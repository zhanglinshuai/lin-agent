package com.lin.linagent.service;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.lin.linagent.multirecall.MultiRecall;
import com.lin.linagent.multirecall.RecallResultMerger;
import jakarta.annotation.Resource;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * 知识库检索服务
 */
@Service
@Slf4j
public class KnowledgeBaseService {

    private static final String KB_META_MARKER_START = "[[KB_META]]";
    private static final String KB_META_MARKER_END = "[[/KB_META]]";
    private static final String KB_SOURCE_MARKER_START = "[[KB_SOURCE]]";
    private static final String KB_SOURCE_MARKER_END = "[[/KB_SOURCE]]";
    private static final Gson GSON = new Gson();

    @Resource
    private MultiRecall multiRecall;

    @Resource
    private RecallResultMerger recallResultMerger;

    @Resource
    private AdminLogService adminLogService;

    /**
     * 为当前问题准备知识库上下文
     * @param query 用户问题
     * @return 知识库支持结果
     */
    public KnowledgeSupport prepareKnowledgeSupport(String query) {
        String normalizedQuery = StringUtils.normalizeSpace(StringUtils.defaultString(query));
        if (StringUtils.isBlank(normalizedQuery)) {
            log.info("知识库检索跳过：查询词为空");
            adminLogService.warn("knowledge", "知识库检索跳过", "query为空");
            return KnowledgeSupport.empty("知识库开关已开启，但当前问题为空，未执行检索。");
        }
        log.info("知识库开始检索，query={}", normalizedQuery);
        try {
            List<Document> recalledDocuments = multiRecall.recall(normalizedQuery);
            List<Document> mergedDocuments = recallResultMerger.mergeAndRank(recalledDocuments);
            List<KnowledgeReference> references = mergedDocuments.stream()
                    .map(this::toKnowledgeReference)
                    .filter(reference -> reference != null && StringUtils.isNotBlank(reference.getSnippet()))
                    .sorted(Comparator.comparingDouble(KnowledgeReference::getScore).reversed())
                    .limit(4)
                    .toList();
            if (references.isEmpty()) {
                log.info("知识库检索未命中，query={}, recallCount={}, mergedCount={}",
                        normalizedQuery, recalledDocuments.size(), mergedDocuments.size());
                adminLogService.info("knowledge", "知识库检索未命中", "query=" + normalizedQuery + ", recallCount=" + recalledDocuments.size() + ", mergedCount=" + mergedDocuments.size());
                return KnowledgeSupport.empty("已尝试检索知识库，但本轮没有命中可直接参考的资料。");
            }
            log.info("知识库检索命中，query={}, recallCount={}, mergedCount={}, hitCount={}, hits={}",
                    normalizedQuery,
                    recalledDocuments.size(),
                    mergedDocuments.size(),
                    references.size(),
                    buildHitLogSummary(references));
            adminLogService.info("knowledge", "知识库检索命中", "query=" + normalizedQuery + ", hitCount=" + references.size());
            return KnowledgeSupport.matched(
                    references.size(),
                    buildKnowledgePromptContext(references),
                    buildKnowledgeEvidence(normalizedQuery, references)
            );
        } catch (Exception e) {
            log.warn("知识库检索失败，query={}, error={}", normalizedQuery, e.getMessage(), e);
            adminLogService.error("knowledge", "知识库检索失败", "query=" + normalizedQuery + ", error=" + e.getMessage());
            return KnowledgeSupport.empty("已尝试检索知识库，但本轮检索失败，后续回答将只基于当前对话内容。");
        }
    }

    /**
     * 构建知识库提示上下文
     * @param references 命中来源
     * @return 提示上下文
     */
    private String buildKnowledgePromptContext(List<KnowledgeReference> references) {
        StringBuilder builder = new StringBuilder("已检索到以下知识库资料，请优先结合这些资料回答：\n");
        int index = 1;
        for (KnowledgeReference reference : references) {
            builder.append("资料").append(index++).append("：\n")
                    .append("标题：").append(StringUtils.defaultIfBlank(reference.getTitle(), "未命名资料")).append("\n")
                    .append("来源：").append(StringUtils.defaultIfBlank(reference.getSourceLabel(), "知识库")).append("\n")
                    .append("内容：").append(StringUtils.defaultString(reference.getSnippet())).append("\n\n");
        }
        builder.append("如果资料不足以直接回答，请明确指出不足点，不要把资料里没有的信息说得过于确定。");
        return builder.toString().trim();
    }

    /**
     * 构建前端可视化展示用的知识库命中信息
     * @param query 查询词
     * @param references 命中来源
     * @return 结构化内容
     */
    private String buildKnowledgeEvidence(String query, List<KnowledgeReference> references) {
        JsonObject meta = new JsonObject();
        meta.addProperty("provider", "知识库");
        meta.addProperty("query", query);
        meta.addProperty("summary", "已命中 " + references.size() + " 条知识库资料");
        StringBuilder builder = new StringBuilder();
        builder.append(KB_META_MARKER_START).append(GSON.toJson(meta)).append(KB_META_MARKER_END);
        for (KnowledgeReference reference : references) {
            JsonObject source = new JsonObject();
            source.addProperty("title", reference.getTitle());
            source.addProperty("snippet", reference.getSnippet());
            source.addProperty("url", "");
            builder.append(KB_SOURCE_MARKER_START).append(GSON.toJson(source)).append(KB_SOURCE_MARKER_END);
        }
        return builder.toString();
    }

    /**
     * 构建命中日志摘要
     * @param references 命中来源
     * @return 日志文本
     */
    private String buildHitLogSummary(List<KnowledgeReference> references) {
        if (references == null || references.isEmpty()) {
            return "[]";
        }
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < references.size(); i++) {
            KnowledgeReference reference = references.get(i);
            if (i > 0) {
                builder.append(" | ");
            }
            builder.append("#").append(i + 1)
                    .append("{title=").append(StringUtils.defaultIfBlank(reference.getTitle(), "知识库片段"))
                    .append(", source=").append(StringUtils.defaultIfBlank(reference.getSourceLabel(), "知识库"))
                    .append(", score=").append(String.format(java.util.Locale.ROOT, "%.4f", reference.getScore()))
                    .append(", snippet=").append(trimText(reference.getSnippet(), 120))
                    .append("}");
        }
        return builder.toString();
    }

    /**
     * 文档转成知识库来源
     * @param document 文档
     * @return 来源对象
     */
    private KnowledgeReference toKnowledgeReference(Document document) {
        if (document == null) {
            return null;
        }
        Map<String, Object> metadata = document.getMetadata();
        String title = pickFirstText(metadata, "title", "filename");
        String source = pickFirstText(metadata, "filename", "source", "type");
        String snippet = trimText(document.getText(), 180);
        if (StringUtils.isBlank(title)) {
            title = trimText(snippet, 20);
        }
        if (StringUtils.isBlank(snippet)) {
            return null;
        }
        KnowledgeReference reference = new KnowledgeReference();
        reference.setTitle(StringUtils.defaultIfBlank(title, "知识库片段"));
        reference.setSourceLabel(StringUtils.defaultIfBlank(source, "知识库"));
        reference.setSnippet(snippet);
        Object scoreValue = metadata.get("score");
        double score = 0D;
        if (scoreValue instanceof Number numberValue) {
            score = numberValue.doubleValue();
        }
        reference.setScore(score);
        return reference;
    }

    /**
     * 从 metadata 中按顺序读取文本字段
     * @param metadata 元数据
     * @param keys 候选字段
     * @return 文本
     */
    private String pickFirstText(Map<String, Object> metadata, String... keys) {
        if (metadata == null || keys == null) {
            return "";
        }
        for (String key : keys) {
            Object value = metadata.get(key);
            if (value == null) {
                continue;
            }
            String text = String.valueOf(value).trim();
            if (StringUtils.isNotBlank(text)) {
                return text;
            }
        }
        return "";
    }

    /**
     * 安全裁剪文本
     * @param text 原始文本
     * @param maxLen 最大长度
     * @return 裁剪结果
     */
    private String trimText(String text, int maxLen) {
        String normalized = StringUtils.normalizeSpace(StringUtils.defaultString(text));
        if (StringUtils.isBlank(normalized) || maxLen <= 0) {
            return "";
        }
        if (normalized.length() <= maxLen) {
            return normalized;
        }
        return normalized.substring(0, maxLen).trim() + "...";
    }

    /**
     * 知识库支持结果
     */
    @Data
    public static class KnowledgeSupport {
        private boolean matched;
        private int matchCount;
        private String promptContext;
        private String evidenceContent;

        public static KnowledgeSupport matched(int matchCount, String promptContext, String evidenceContent) {
            KnowledgeSupport support = new KnowledgeSupport();
            support.setMatched(true);
            support.setMatchCount(matchCount);
            support.setPromptContext(promptContext);
            support.setEvidenceContent(evidenceContent);
            return support;
        }

        public static KnowledgeSupport empty(String evidenceContent) {
            KnowledgeSupport support = new KnowledgeSupport();
            support.setMatched(false);
            support.setMatchCount(0);
            support.setPromptContext("");
            support.setEvidenceContent(StringUtils.defaultString(evidenceContent));
            return support;
        }
    }

    /**
     * 知识库来源条目
     */
    @Data
    private static class KnowledgeReference {
        private String title;
        private String sourceLabel;
        private String snippet;
        private double score;
    }
}
