package com.lin.linagent.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lin.linagent.domain.ChatMemory;
import com.lin.linagent.domain.ConversationInfo;
import com.lin.linagent.domain.dto.AdminConversationVO;
import com.lin.linagent.domain.dto.ConversationSummary;
import com.lin.linagent.mapper.ChatMemoryMapper;
import com.lin.linagent.mapper.ConversationInfoMapper;
import com.lin.linagent.service.ConversationInfoService;
import jakarta.annotation.Resource;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 会话信息服务实现
 */
@Service
public class ConversationInfoServiceImpl extends ServiceImpl<ConversationInfoMapper, ConversationInfo>
        implements ConversationInfoService {

    private static final String DEFAULT_TITLE = "新对话";
    private static final int SUMMARY_MAX_LENGTH = 90;
    private static final int SUMMARY_TOPIC_MAX_LENGTH = 16;
    private static final int SUMMARY_CONTENT_MAX_LENGTH = 64;
    private static final Pattern SUMMARY_MARK_PATTERN = Pattern.compile("^(一句话结论|结论|总结)[：:]?\\s*(.*)$");

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Resource
    private ChatMemoryMapper chatMemoryMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void createConversation(String userId, String conversationId, String title) {
        String normalizedUserId = StringUtils.trimToEmpty(userId);
        String normalizedConversationId = StringUtils.trimToEmpty(conversationId);
        if (StringUtils.isAnyBlank(normalizedUserId, normalizedConversationId)) {
            return;
        }
        ConversationInfo existing = findOne(normalizedUserId, normalizedConversationId);
        if (existing != null) {
            existing.setUpdateTime(new Date());
            if (StringUtils.isNotBlank(title)) {
                existing.setTitle(StringUtils.trimToEmpty(title));
            }
            this.updateById(existing);
            return;
        }
        Date now = new Date();
        ConversationInfo conversationInfo = new ConversationInfo();
        conversationInfo.setConversationId(normalizedConversationId);
        conversationInfo.setUserId(normalizedUserId);
        conversationInfo.setTitle(StringUtils.defaultIfBlank(StringUtils.trimToEmpty(title), DEFAULT_TITLE));
        conversationInfo.setPinned(false);
        conversationInfo.setSummary("");
        conversationInfo.setLastMessage("");
        conversationInfo.setMessageCount(0);
        conversationInfo.setCreateTime(now);
        conversationInfo.setLastTime(now);
        conversationInfo.setUpdateTime(now);
        this.save(conversationInfo);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void syncConversation(String userId, String conversationId) {
        String normalizedUserId = StringUtils.trimToEmpty(userId);
        String normalizedConversationId = StringUtils.trimToEmpty(conversationId);
        if (StringUtils.isAnyBlank(normalizedUserId, normalizedConversationId)) {
            return;
        }
        List<ChatMemory> conversation = listConversationMessages(normalizedUserId, normalizedConversationId);
        if (conversation.isEmpty()) {
            deleteConversation(normalizedUserId, normalizedConversationId);
            return;
        }
        conversation.sort(Comparator.comparing(ChatMemory::getCreateTime, Comparator.nullsLast(Date::compareTo)));
        ChatMemory earliest = conversation.get(0);
        ChatMemory latest = conversation.get(conversation.size() - 1);
        ConversationInfo conversationInfo = findOne(normalizedUserId, normalizedConversationId);
        if (conversationInfo == null) {
            conversationInfo = new ConversationInfo();
            conversationInfo.setConversationId(normalizedConversationId);
            conversationInfo.setUserId(normalizedUserId);
            conversationInfo.setCreateTime(earliest.getCreateTime() == null ? new Date() : earliest.getCreateTime());
        }
        conversationInfo.setTitle(extractTitle(conversation));
        conversationInfo.setMode(extractMode(conversation));
        conversationInfo.setTag(extractTag(conversation));
        conversationInfo.setPinned(extractPinned(conversation));
        conversationInfo.setSummary(extractSummary(conversation));
        conversationInfo.setLastMessage(safeText(StringUtils.defaultString(latest.getContent()), 120));
        conversationInfo.setMessageCount(conversation.size());
        conversationInfo.setLastTime(latest.getCreateTime() == null ? new Date() : latest.getCreateTime());
        conversationInfo.setUpdateTime(new Date());
        this.saveOrUpdate(conversationInfo);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void renameConversation(String userId, String conversationId, String title) {
        ConversationInfo conversationInfo = findOne(userId, conversationId);
        if (conversationInfo == null) {
            return;
        }
        conversationInfo.setTitle(StringUtils.defaultIfBlank(StringUtils.trimToEmpty(title), DEFAULT_TITLE));
        conversationInfo.setUpdateTime(new Date());
        this.updateById(conversationInfo);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void pinConversation(String userId, String conversationId, boolean pinned) {
        ConversationInfo conversationInfo = findOne(userId, conversationId);
        if (conversationInfo == null) {
            return;
        }
        conversationInfo.setPinned(pinned);
        conversationInfo.setUpdateTime(new Date());
        this.updateById(conversationInfo);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteConversation(String userId, String conversationId) {
        String normalizedUserId = StringUtils.trimToEmpty(userId);
        String normalizedConversationId = StringUtils.trimToEmpty(conversationId);
        if (StringUtils.isAnyBlank(normalizedUserId, normalizedConversationId)) {
            return;
        }
        QueryWrapper<ConversationInfo> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("user_id", normalizedUserId);
        queryWrapper.eq("conversation_id", normalizedConversationId);
        this.remove(queryWrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteConversationForAdmin(String conversationId, String userId) {
        String normalizedConversationId = StringUtils.trimToEmpty(conversationId);
        if (StringUtils.isBlank(normalizedConversationId)) {
            return;
        }
        QueryWrapper<ConversationInfo> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("conversation_id", normalizedConversationId);
        if (StringUtils.isNotBlank(userId)) {
            queryWrapper.eq("user_id", StringUtils.trimToEmpty(userId));
        }
        this.remove(queryWrapper);
    }

    @Override
    public List<ConversationSummary> listConversationSummaries(String userId, String mode, String keyword) {
        ensureConversationTableInitialized();
        List<ConversationSummary> summaryList = this.baseMapper.selectUserConversationSummaries(
                StringUtils.trimToEmpty(userId),
                StringUtils.trimToEmpty(mode),
                StringUtils.trimToEmpty(keyword)
        );
        if (refreshUserConversationSummariesIfNecessary(StringUtils.trimToEmpty(userId), summaryList)) {
            return this.baseMapper.selectUserConversationSummaries(
                    StringUtils.trimToEmpty(userId),
                    StringUtils.trimToEmpty(mode),
                    StringUtils.trimToEmpty(keyword)
            );
        }
        return summaryList;
    }

    @Override
    public List<AdminConversationVO> listAdminConversations(String keyword, String mode, Boolean pinned, Integer limit) {
        ensureConversationTableInitialized();
        int safeLimit = Math.max(1, Math.min(limit == null ? 120 : limit, 500));
        List<AdminConversationVO> conversationList = this.baseMapper.selectAdminConversationList(
                StringUtils.trimToEmpty(keyword),
                StringUtils.trimToEmpty(mode),
                pinned,
                safeLimit
        );
        if (refreshAdminConversationSummariesIfNecessary(conversationList)) {
            return this.baseMapper.selectAdminConversationList(
                    StringUtils.trimToEmpty(keyword),
                    StringUtils.trimToEmpty(mode),
                    pinned,
                    safeLimit
            );
        }
        return conversationList;
    }

    @Override
    public long countConversationTotal() {
        ensureConversationTableInitialized();
        Long count = this.baseMapper.countConversationTotal();
        return count == null ? 0L : count;
    }

    @Override
    public List<ChatMemory> listUserConversationList(String userId, String mode) {
        ensureConversationTableInitialized();
        List<ConversationSummary> summaryList = this.baseMapper.selectUserConversationSummaries(
                StringUtils.trimToEmpty(userId),
                StringUtils.trimToEmpty(mode),
                ""
        );
        if (summaryList == null || summaryList.isEmpty()) {
            return new ArrayList<>();
        }
        List<ChatMemory> result = new ArrayList<>();
        for (ConversationSummary summary : summaryList) {
            ChatMemory chatMemory = new ChatMemory();
            chatMemory.setConversationId(summary.getConversationId());
            chatMemory.setContent(StringUtils.defaultIfBlank(summary.getLastMessage(), summary.getSummary()));
            chatMemory.setMessageType("ASSISTANT");
            chatMemory.setCreateTime(summary.getLastTime());
            chatMemory.setUserId(StringUtils.trimToEmpty(userId));
            result.add(chatMemory);
        }
        return result;
    }

    /**
     * 确保会话表已经初始化
     */
    private void ensureConversationTableInitialized() {
        long memoryConversationCount = countChatMemoryConversationTotal();
        if (memoryConversationCount <= 0) {
            return;
        }
        if (this.count() >= memoryConversationCount) {
            return;
        }
        List<ChatMemory> chatMemoryList = chatMemoryMapper.selectList(new QueryWrapper<ChatMemory>()
                .orderByAsc("user_id")
                .orderByAsc("conversation_id")
                .orderByAsc("create_time"));
        if (chatMemoryList == null || chatMemoryList.isEmpty()) {
            return;
        }
        Map<String, List<ChatMemory>> grouped = new LinkedHashMap<>();
        for (ChatMemory chatMemory : chatMemoryList) {
            String key = StringUtils.trimToEmpty(chatMemory.getUserId()) + "::" + StringUtils.trimToEmpty(chatMemory.getConversationId());
            grouped.computeIfAbsent(key, ignored -> new ArrayList<>()).add(chatMemory);
        }
        for (List<ChatMemory> conversation : grouped.values()) {
            if (conversation.isEmpty()) {
                continue;
            }
            ChatMemory first = conversation.get(0);
            if (findOne(first.getUserId(), first.getConversationId()) == null) {
                syncConversation(first.getUserId(), first.getConversationId());
            }
        }
    }

    /**
     * 统计消息表中的会话总数
     */
    private long countChatMemoryConversationTotal() {
        Long count = chatMemoryMapper.countConversationTotal();
        return count == null ? 0L : count;
    }

    /**
     * 查询单个会话
     */
    private ConversationInfo findOne(String userId, String conversationId) {
        QueryWrapper<ConversationInfo> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("user_id", StringUtils.trimToEmpty(userId));
        queryWrapper.eq("conversation_id", StringUtils.trimToEmpty(conversationId));
        queryWrapper.last("LIMIT 1");
        return this.getOne(queryWrapper, false);
    }

    /**
     * 查询会话消息
     */
    private List<ChatMemory> listConversationMessages(String userId, String conversationId) {
        QueryWrapper<ChatMemory> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("user_id", StringUtils.trimToEmpty(userId));
        queryWrapper.eq("conversation_id", StringUtils.trimToEmpty(conversationId));
        queryWrapper.orderByAsc("create_time");
        return chatMemoryMapper.selectList(queryWrapper);
    }

    /**
     * 提取会话标题
     */
    private String extractTitle(List<ChatMemory> conversation) {
        for (ChatMemory chatMemory : conversation) {
            String title = getMetadataValue(chatMemory.getMetadata(), "title");
            if (StringUtils.isNotBlank(title)) {
                return title.trim();
            }
        }
        for (ChatMemory chatMemory : conversation) {
            if ("USER".equalsIgnoreCase(chatMemory.getMessageType())) {
                return safeText(chatMemory.getContent(), 20);
            }
        }
        return DEFAULT_TITLE;
    }

    /**
     * 提取会话摘要
     */
    private String extractSummary(List<ChatMemory> conversation) {
        String latestUserMessage = findLatestMessageContent(conversation, "USER");
        for (int i = conversation.size() - 1; i >= 0; i--) {
            ChatMemory chatMemory = conversation.get(i);
            if (!"ASSISTANT".equalsIgnoreCase(chatMemory.getMessageType())) {
                continue;
            }
            String finalContent = getMetadataValue(chatMemory.getMetadata(), "finalContent");
            if (StringUtils.isNotBlank(finalContent)) {
                String summary = buildConversationSummary(finalContent, latestUserMessage);
                if (StringUtils.isNotBlank(summary)) {
                    return summary;
                }
            }
            String resultContent = getMetadataValue(chatMemory.getMetadata(), "resultContent");
            if (StringUtils.isNotBlank(resultContent)) {
                String summary = buildConversationSummary(resultContent, latestUserMessage);
                if (StringUtils.isNotBlank(summary)) {
                    return summary;
                }
            }
            String summary = buildConversationSummary(chatMemory.getContent(), latestUserMessage);
            if (StringUtils.isNotBlank(summary)) {
                return summary;
            }
        }
        if (StringUtils.isNotBlank(latestUserMessage)) {
            String userTopic = extractUserTopic(latestUserMessage);
            if (StringUtils.isNotBlank(userTopic)) {
                return safeText("围绕“" + userTopic + "”继续对话", SUMMARY_MAX_LENGTH);
            }
            return safeText(normalizeSummaryLine(latestUserMessage), SUMMARY_MAX_LENGTH);
        }
        return safeText(normalizeSummaryLine(conversation.get(conversation.size() - 1).getContent()), SUMMARY_MAX_LENGTH);
    }

    /**
     * 提取标签
     */
    private String extractTag(List<ChatMemory> conversation) {
        for (ChatMemory chatMemory : conversation) {
            String tag = getMetadataValue(chatMemory.getMetadata(), "tag");
            if (StringUtils.isNotBlank(tag)) {
                return tag.trim();
            }
        }
        return "";
    }

    /**
     * 提取模式
     */
    private String extractMode(List<ChatMemory> conversation) {
        for (ChatMemory chatMemory : conversation) {
            String mode = getMetadataValue(chatMemory.getMetadata(), "mode");
            if (StringUtils.isNotBlank(mode)) {
                return mode.trim();
            }
        }
        return "";
    }

    /**
     * 提取置顶状态
     */
    private Boolean extractPinned(List<ChatMemory> conversation) {
        for (ChatMemory chatMemory : conversation) {
            Boolean pinned = getMetadataBoolean(chatMemory.getMetadata(), "pinned");
            if (pinned != null) {
                return pinned;
            }
        }
        return false;
    }

    /**
     * 读取元数据文本字段
     */
    private String getMetadataValue(String metadata, String key) {
        if (StringUtils.isAnyBlank(metadata, key)) {
            return null;
        }
        try {
            Map<String, Object> map = objectMapper.readValue(metadata, new TypeReference<Map<String, Object>>() {});
            Object value = map.get(key);
            return value == null ? null : String.valueOf(value);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 读取元数据布尔字段
     */
    private Boolean getMetadataBoolean(String metadata, String key) {
        if (StringUtils.isAnyBlank(metadata, key)) {
            return null;
        }
        try {
            Map<String, Object> map = objectMapper.readValue(metadata, new TypeReference<Map<String, Object>>() {});
            Object value = map.get(key);
            if (value instanceof Boolean bool) {
                return bool;
            }
            if (value instanceof String text) {
                return Boolean.parseBoolean(text);
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 刷新用户侧摘要仍为旧格式的会话
     */
    private boolean refreshUserConversationSummariesIfNecessary(String userId, List<ConversationSummary> summaryList) {
        if (StringUtils.isBlank(userId) || summaryList == null || summaryList.isEmpty()) {
            return false;
        }
        boolean refreshed = false;
        for (ConversationSummary summary : summaryList) {
            if (summary == null || !needRefreshSummary(summary.getSummary(), summary.getLastMessage())) {
                continue;
            }
            syncConversation(userId, summary.getConversationId());
            refreshed = true;
        }
        return refreshed;
    }

    /**
     * 刷新后台列表中仍为旧格式的会话摘要
     */
    private boolean refreshAdminConversationSummariesIfNecessary(List<AdminConversationVO> conversationList) {
        if (conversationList == null || conversationList.isEmpty()) {
            return false;
        }
        boolean refreshed = false;
        for (AdminConversationVO item : conversationList) {
            if (item == null || !needRefreshSummary(item.getSummary(), item.getLastMessage())) {
                continue;
            }
            syncConversation(item.getUserId(), item.getConversationId());
            refreshed = true;
        }
        return refreshed;
    }

    /**
     * 判断摘要是否仍然是旧的回填格式
     */
    private boolean needRefreshSummary(String summary, String lastMessage) {
        String normalizedSummary = StringUtils.trimToEmpty(summary);
        if (StringUtils.isBlank(normalizedSummary)) {
            return true;
        }
        return StringUtils.equals(normalizedSummary, StringUtils.trimToEmpty(lastMessage));
    }

    /**
     * 查找指定类型的最新消息内容
     */
    private String findLatestMessageContent(List<ChatMemory> conversation, String messageType) {
        if (conversation == null || conversation.isEmpty()) {
            return "";
        }
        for (int i = conversation.size() - 1; i >= 0; i--) {
            ChatMemory chatMemory = conversation.get(i);
            if (chatMemory != null && StringUtils.equalsIgnoreCase(messageType, chatMemory.getMessageType())) {
                return StringUtils.defaultString(chatMemory.getContent());
            }
        }
        return "";
    }

    /**
     * 结合用户问题和助手答复生成会话摘要
     */
    private String buildConversationSummary(String assistantText, String userText) {
        String assistantSummary = extractAssistantSummary(assistantText);
        String userTopic = extractUserTopic(userText);
        if (StringUtils.isBlank(assistantSummary)) {
            return StringUtils.isBlank(userTopic) ? "" : safeText("围绕“" + userTopic + "”继续对话", SUMMARY_MAX_LENGTH);
        }
        if (StringUtils.isBlank(userTopic) || assistantSummary.contains(userTopic) || assistantSummary.length() >= SUMMARY_CONTENT_MAX_LENGTH) {
            return safeText(assistantSummary, SUMMARY_MAX_LENGTH);
        }
        return safeText("围绕“" + userTopic + "”：" + assistantSummary, SUMMARY_MAX_LENGTH);
    }

    /**
     * 提炼助手答复中的核心结论
     */
    private String extractAssistantSummary(String text) {
        String normalized = normalizeSummaryText(text);
        if (StringUtils.isBlank(normalized)) {
            return "";
        }
        String explicitSummary = extractExplicitSummary(normalized);
        if (StringUtils.isNotBlank(explicitSummary)) {
            return safeText(explicitSummary, SUMMARY_CONTENT_MAX_LENGTH);
        }
        String[] lines = normalized.split("\\n+");
        for (String line : lines) {
            String candidate = normalizeSummaryCandidate(line);
            if (StringUtils.isBlank(candidate) || isSummaryNoiseLine(candidate)) {
                continue;
            }
            String firstSentence = extractFirstSentence(candidate);
            if (StringUtils.isNotBlank(firstSentence)) {
                return safeText(firstSentence, SUMMARY_CONTENT_MAX_LENGTH);
            }
        }
        return safeText(extractFirstSentence(normalized), SUMMARY_CONTENT_MAX_LENGTH);
    }

    /**
     * 提炼用户问题中的主题
     */
    private String extractUserTopic(String text) {
        String normalized = normalizeSummaryText(text);
        if (StringUtils.isBlank(normalized)) {
            return "";
        }
        String topic = extractFirstSentence(normalized);
        topic = topic.replaceFirst("^(请问|帮我|麻烦|我想|想问一下|请帮我|想了解一下)\\s*", "").trim();
        return safeText(topic, SUMMARY_TOPIC_MAX_LENGTH);
    }

    /**
     * 提取显式写出的结论段落
     */
    private String extractExplicitSummary(String text) {
        String[] lines = text.split("\\n+");
        boolean waitingSummary = false;
        for (String line : lines) {
            String normalizedLine = normalizeSummaryCandidate(line);
            if (StringUtils.isBlank(normalizedLine)) {
                continue;
            }
            java.util.regex.Matcher matcher = SUMMARY_MARK_PATTERN.matcher(normalizedLine);
            if (matcher.matches()) {
                String inlineSummary = StringUtils.trimToEmpty(matcher.group(2));
                if (StringUtils.isNotBlank(inlineSummary)) {
                    return normalizeSummaryCandidate(inlineSummary);
                }
                waitingSummary = true;
                continue;
            }
            if (waitingSummary && !isSummaryNoiseLine(normalizedLine)) {
                return normalizedLine;
            }
        }
        return "";
    }

    /**
     * 规范化摘要候选文本
     */
    private String normalizeSummaryCandidate(String text) {
        String candidate = StringUtils.defaultString(text).trim();
        candidate = candidate.replaceAll("^[>*\\-•]+\\s*", "");
        candidate = candidate.replaceAll("^\\d+[\\.、]\\s*", "");
        candidate = candidate.replaceAll("^(重点|关键信息|建议动作|建议|结果|回答|说明)[：:]\\s*", "");
        candidate = candidate.replaceAll("[：:]$", "");
        return normalizeSummaryLine(candidate);
    }

    /**
     * 判断当前行是否只是结构标题
     */
    private boolean isSummaryNoiseLine(String text) {
        String normalized = StringUtils.trimToEmpty(text);
        if (StringUtils.isBlank(normalized)) {
            return true;
        }
        return "核心内容".equals(normalized)
                || "下一步建议".equals(normalized)
                || "行动建议".equals(normalized)
                || "落地建议".equals(normalized)
                || "风险提示".equals(normalized)
                || "继续聊聊".equals(normalized)
                || "补充说明".equals(normalized);
    }

    /**
     * 提取首个有效句子
     */
    private String extractFirstSentence(String text) {
        String normalized = normalizeSummaryLine(text);
        if (StringUtils.isBlank(normalized)) {
            return "";
        }
        String[] parts = normalized.split("[。！？!?；;]");
        for (String part : parts) {
            String candidate = normalizeSummaryLine(part);
            if (candidate.length() >= 6) {
                return candidate;
            }
        }
        return normalized;
    }

    /**
     * 规范化原始答复文本
     */
    private String normalizeSummaryText(String text) {
        String normalized = StringUtils.defaultString(text)
                .replace("\r\n", "\n")
                .replace('\r', '\n');
        normalized = normalized.replaceAll("(?s)```.*?```", " ");
        normalized = normalized.replaceAll("`([^`]+)`", "$1");
        normalized = normalized.replaceAll("!\\[[^\\]]*\\]\\([^)]*\\)", " ");
        normalized = normalized.replaceAll("\\[([^\\]]+)\\]\\([^)]*\\)", "$1");
        normalized = normalized.replaceAll("(?m)^#{1,6}\\s*", "");
        normalized = normalized.replaceAll("\\*\\*|__|~~", "");
        normalized = normalized.replace('|', ' ');
        normalized = normalized.replaceAll("[ \\t]+", " ");
        normalized = normalized.replaceAll("\\n{3,}", "\n\n");
        return normalized.trim();
    }

    /**
     * 规范化单行摘要文本
     */
    private String normalizeSummaryLine(String text) {
        return StringUtils.defaultString(text)
                .replaceAll("\\s+", " ")
                .replaceAll("^[：:、，,\\-]+", "")
                .trim();
    }

    /**
     * 安全截断文本
     */
    private String safeText(String text, int maxLen) {
        String normalized = StringUtils.defaultString(text).replaceAll("\\s+", " ").trim();
        if (normalized.length() <= maxLen) {
            return normalized;
        }
        return normalized.substring(0, maxLen) + "...";
    }
}
