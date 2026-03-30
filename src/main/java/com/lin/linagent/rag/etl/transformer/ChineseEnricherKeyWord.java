package com.lin.linagent.rag.etl.transformer;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.lin.linagent.contant.CommonVariables;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.document.Document;
import org.springframework.ai.model.transformer.KeywordMetadataEnricher;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 自定义提取关键词器（中文）
 */
@Slf4j
@Component
public class ChineseEnricherKeyWord extends KeywordMetadataEnricher {

    /**
     * 单批最大分片数，避免单次提示词过大
     */
    private static final int MAX_BATCH_SIZE = 6;

    /**
     * 单个分片送给关键词抽取时的最大长度
     */
    private static final int MAX_CONTENT_LENGTH = 1200;

    /**
     * 单批累计文本长度上限
     */
    private static final int MAX_BATCH_CONTENT_LENGTH = 4800;

    private final ChatModel dashscopechatModel;

    private final int keywordCount = CommonVariables.ENRICHER_KEY_WORD_COUNT;

    /**
     * 相同文本直接复用关键词，减少重复调用
     */
    private final Map<String, String> keywordCache = new ConcurrentHashMap<>();

    public ChineseEnricherKeyWord(ChatModel dashscopechatModel) {
        super(dashscopechatModel, CommonVariables.ENRICHER_KEY_WORD_COUNT);
        this.dashscopechatModel = dashscopechatModel;
        Assert.notNull(dashscopechatModel, "ChatModel must not be null");
    }

    @Override
    public List<Document> apply(List<Document> documents) {
        if (documents == null || documents.isEmpty()) {
            return documents;
        }
        List<KeywordTask> pendingTasks = new ArrayList<>();
        for (Document document : documents) {
            if (document == null || StringUtils.isBlank(document.getText())) {
                continue;
            }
            String normalizedContent = normalizeContent(document.getText());
            String cacheKey = buildCacheKey(normalizedContent);
            String cachedKeywords = keywordCache.get(cacheKey);
            if (StringUtils.isNotBlank(cachedKeywords)) {
                document.getMetadata().put("excerpt_keywords", cachedKeywords);
                continue;
            }
            pendingTasks.add(new KeywordTask(document, cacheKey, normalizedContent));
        }
        for (List<KeywordTask> batchTasks : buildBatches(pendingTasks)) {
            applyBatchKeywords(batchTasks);
        }
        return documents;
    }

    /**
     * 批量抽取关键词，减少大模型调用次数。
     * @param batchTasks 当前批次任务
     */
    private void applyBatchKeywords(List<KeywordTask> batchTasks) {
        if (batchTasks == null || batchTasks.isEmpty()) {
            return;
        }
        try {
            Map<Integer, String> keywordMap = requestBatchKeywords(batchTasks);
            for (int i = 0; i < batchTasks.size(); i++) {
                KeywordTask task = batchTasks.get(i);
                String keywords = StringUtils.trimToEmpty(keywordMap.get(i));
                if (StringUtils.isBlank(keywords)) {
                    keywords = requestSingleKeywords(task.promptContent());
                }
                writeKeywords(task, keywords);
            }
        } catch (Exception e) {
            log.warn("批量抽取关键词失败，降级为单条抽取。原因：{}", e.getMessage());
            for (KeywordTask task : batchTasks) {
                writeKeywords(task, requestSingleKeywords(task.promptContent()));
            }
        }
    }

    /**
     * 一次请求多个分片的关键词。
     * @param batchTasks 批次任务
     * @return 关键词映射
     */
    private Map<Integer, String> requestBatchKeywords(List<KeywordTask> batchTasks) {
        JsonArray payloadArray = new JsonArray();
        for (int i = 0; i < batchTasks.size(); i++) {
            JsonObject item = new JsonObject();
            item.addProperty("index", i);
            item.addProperty("content", batchTasks.get(i).promptContent());
            payloadArray.add(item);
        }
        String promptText = String.format("""
                你是中文文档关键词提取器。
                请从输入数组中的每一段文本中提取 %s 个最重要的中文关键词。
                返回结果必须是 JSON 数组，每个元素格式固定为：
                {"index":0,"keywords":"关键词1,关键词2,关键词3"}
                只返回 JSON，不要输出 markdown 代码块，不要补充解释。
                输入内容：
                %s
                """, keywordCount, payloadArray);
        Prompt prompt = new Prompt(promptText);
        String resultText = dashscopechatModel.call(prompt).getResult().getOutput().getText();
        return parseBatchKeywords(resultText);
    }

    /**
     * 单条抽取关键词，作为批量失败后的兜底。
     * @param content 文本内容
     * @return 关键词结果
     */
    private String requestSingleKeywords(String content) {
        if (StringUtils.isBlank(content)) {
            return "";
        }
        try {
            PromptTemplate template = new PromptTemplate(String.format("请用中文从{content_str}中提取 %s 个最重要的关键词，只返回中文关键词，用逗号分隔", keywordCount));
            Prompt prompt = template.create(Map.of("content_str", content));
            return StringUtils.trimToEmpty(dashscopechatModel.call(prompt).getResult().getOutput().getText());
        } catch (Exception e) {
            log.warn("单条关键词抽取失败：{}", e.getMessage());
            return "";
        }
    }

    /**
     * 解析批量关键词抽取结果。
     * @param resultText 模型返回文本
     * @return 关键词映射
     */
    private Map<Integer, String> parseBatchKeywords(String resultText) {
        Map<Integer, String> keywordMap = new HashMap<>();
        String normalizedResult = StringUtils.trimToEmpty(resultText);
        if (normalizedResult.startsWith("```")) {
            normalizedResult = normalizedResult.replaceFirst("^```(?:json)?\\s*", "").replaceFirst("\\s*```$", "");
        }
        JsonElement root = JsonParser.parseString(normalizedResult);
        if (!root.isJsonArray()) {
            return keywordMap;
        }
        for (JsonElement item : root.getAsJsonArray()) {
            if (!item.isJsonObject()) {
                continue;
            }
            JsonObject jsonObject = item.getAsJsonObject();
            if (!jsonObject.has("index") || !jsonObject.has("keywords")) {
                continue;
            }
            try {
                int index = jsonObject.get("index").getAsInt();
                String keywords = StringUtils.trimToEmpty(jsonObject.get("keywords").getAsString());
                keywordMap.put(index, keywords);
            } catch (Exception ignored) {
            }
        }
        return keywordMap;
    }

    /**
     * 拆分批次，兼顾批次数量和提示词长度。
     * @param pendingTasks 待处理任务
     * @return 批次列表
     */
    private List<List<KeywordTask>> buildBatches(List<KeywordTask> pendingTasks) {
        List<List<KeywordTask>> batches = new ArrayList<>();
        if (pendingTasks == null || pendingTasks.isEmpty()) {
            return batches;
        }
        List<KeywordTask> currentBatch = new ArrayList<>();
        int currentLength = 0;
        for (KeywordTask task : pendingTasks) {
            int nextLength = task.promptContent().length();
            boolean needSplit = !currentBatch.isEmpty()
                    && (currentBatch.size() >= MAX_BATCH_SIZE || currentLength + nextLength > MAX_BATCH_CONTENT_LENGTH);
            if (needSplit) {
                batches.add(currentBatch);
                currentBatch = new ArrayList<>();
                currentLength = 0;
            }
            currentBatch.add(task);
            currentLength += nextLength;
        }
        if (!currentBatch.isEmpty()) {
            batches.add(currentBatch);
        }
        return batches;
    }

    /**
     * 回写关键词并刷新缓存。
     * @param task 当前任务
     * @param keywords 关键词
     */
    private void writeKeywords(KeywordTask task, String keywords) {
        if (task == null || task.document() == null) {
            return;
        }
        String normalizedKeywords = StringUtils.trimToEmpty(keywords);
        task.document().getMetadata().put("excerpt_keywords", normalizedKeywords);
        if (StringUtils.isNotBlank(normalizedKeywords)) {
            keywordCache.put(task.cacheKey(), normalizedKeywords);
        }
    }

    /**
     * 规范化文本，减少提示词无效长度。
     * @param content 原始文本
     * @return 规范化结果
     */
    private String normalizeContent(String content) {
        return StringUtils.normalizeSpace(StringUtils.defaultString(content));
    }

    /**
     * 构建缓存键。
     * @param content 文本内容
     * @return 缓存键
     */
    private String buildCacheKey(String content) {
        return UUID.nameUUIDFromBytes(StringUtils.defaultString(content).getBytes(StandardCharsets.UTF_8)).toString();
    }

    private record KeywordTask(Document document, String cacheKey, String content) {

        private String promptContent() {
            if (content.length() <= MAX_CONTENT_LENGTH) {
                return content;
            }
            return content.substring(0, MAX_CONTENT_LENGTH);
        }
    }
}
