package com.lin.linagent.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lin.linagent.domain.ChatMemory;
import com.lin.linagent.domain.User;
import com.lin.linagent.domain.dto.ConversationSummary;
import com.lin.linagent.exception.BusinessException;
import com.lin.linagent.exception.ErrorCode;
import com.lin.linagent.mapper.UserMapper;
import com.lin.linagent.service.ChatMemoryService;
import com.lin.linagent.mapper.ChatMemoryMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
* @author zhanglinshuai
* @description 针对表【chat_memory(存储消息)】的数据库操作Service实现
* @createDate 2025-11-15 15:39:48
*/
@Service
public class ChatMemoryServiceImpl extends ServiceImpl<ChatMemoryMapper, ChatMemory>
    implements ChatMemoryService{

    /**
     * 会话标题最大长度
     */
    private static final int CONVERSATION_TITLE_MAX_LENGTH = 18;

    private final ObjectMapper objectMapper = new ObjectMapper();
    
    @Resource
    private UserMapper userMapper;
    
    @Resource
    private ChatMemoryMapper chatMemoryMapper;

    @Resource
    private com.lin.linagent.service.ConversationInfoService conversationInfoService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean createConversation(String username, String conversationId) {
        User user = getUserByUsername(username);
        String normalizedConversationId = normalizeConversationId(conversationId);
        conversationInfoService.createConversation(user.getId(), normalizedConversationId, "新对话");
        return true;
    }

    @Override
    public List<ChatMemory> getUserConversationList(String username, String mode) {
        User user = getUserByUsername(username);
        return conversationInfoService.listUserConversationList(user.getId(), mode);
    }

    @Override
    public List<ConversationSummary> getUserConversationSummaries(String username, String mode, String keyword) {
        User user = getUserByUsername(username);
        return conversationInfoService.listConversationSummaries(user.getId(), mode, keyword);
    }

    @Override
    public List<ChatMemory> getUserConversation(String conversationId) {
        return getUserConversation(conversationId, null);
    }

    @Override
    public List<ChatMemory> getUserConversation(String conversationId, String userId) {
        if(conversationId==null||conversationId.length()==0){
            throw new BusinessException(ErrorCode.PARAMS_ERROR,"会话不存在");
        }
        //根据conversationId找出会话
        QueryWrapper<ChatMemory> chatMemoryQueryWrapper = new QueryWrapper<>();
        chatMemoryQueryWrapper.eq("conversation_id",conversationId);
        if (userId != null && userId.trim().length() > 0) {
            chatMemoryQueryWrapper.eq("user_id", userId.trim());
        }
        chatMemoryQueryWrapper.orderByAsc("create_time");
        List<ChatMemory> chatMemoryList = chatMemoryMapper.selectList(chatMemoryQueryWrapper);
        if (chatMemoryList.isEmpty()){
            return new ArrayList<>();
        }
        return chatMemoryList;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean renameConversation(String username, String conversationId, String title) {
        User user = getUserByUsername(username);
        String normalizedConversationId = normalizeConversationId(conversationId);
        String normalizedTitle = normalizeConversationTitle(title);
        List<ChatMemory> conversation = listOwnedConversation(user.getId(), normalizedConversationId);
        if (conversation.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "会话不存在或无权操作");
        }
        for (ChatMemory chatMemory : conversation) {
            chatMemory.setMetadata(rewriteMetadataTitle(chatMemory.getMetadata(), normalizedTitle));
            chatMemoryMapper.updateById(chatMemory);
        }
        conversationInfoService.renameConversation(user.getId(), normalizedConversationId, normalizedTitle);
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteConversation(String username, String conversationId) {
        User user = getUserByUsername(username);
        String normalizedConversationId = normalizeConversationId(conversationId);
        QueryWrapper<ChatMemory> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("conversation_id", normalizedConversationId);
        queryWrapper.eq("user_id", user.getId());
        Long count = chatMemoryMapper.selectCount(queryWrapper);
        if (count == null || count <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "会话不存在或无权操作");
        }
        boolean deleted = chatMemoryMapper.delete(queryWrapper) > 0;
        if (deleted) {
            conversationInfoService.deleteConversation(user.getId(), normalizedConversationId);
        }
        return deleted;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean pinConversation(String username, String conversationId, boolean pinned) {
        User user = getUserByUsername(username);
        String normalizedConversationId = normalizeConversationId(conversationId);
        List<ChatMemory> conversation = listOwnedConversation(user.getId(), normalizedConversationId);
        if (conversation.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "会话不存在或无权操作");
        }
        for (ChatMemory chatMemory : conversation) {
            chatMemory.setMetadata(rewriteMetadataPinned(chatMemory.getMetadata(), pinned));
            chatMemoryMapper.updateById(chatMemory);
        }
        conversationInfoService.pinConversation(user.getId(), normalizedConversationId, pinned);
        return true;
    }

    /**
     * 根据用户名查询用户
     * @param username 用户名
     * @return 用户信息
     */
    private User getUserByUsername(String username) {
        if(username == null || username.length() == 0){
            throw new BusinessException(ErrorCode.PARAMS_ERROR,"用户名为空");
        }
        if(username.length()>10){
            throw new BusinessException(ErrorCode.PARAMS_ERROR,"用户名不符合规定");
        }
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("userName",username);
        User user = userMapper.selectOne(queryWrapper);
        if(user==null){
            throw new BusinessException(ErrorCode.PARAMS_ERROR,"用户不存在");
        }
        return user;
    }

    /**
     * 查询用户全部会话消息
     * @param userId 用户id
     * @return 消息列表
     */
    private List<ChatMemory> listUserChatMemories(String userId) {
        QueryWrapper<ChatMemory> chatMemoryQueryWrapper = new QueryWrapper<>();
        chatMemoryQueryWrapper.eq("user_id", userId);
        chatMemoryQueryWrapper.orderByDesc("create_time");
        return chatMemoryMapper.selectList(chatMemoryQueryWrapper);
    }

    /**
     * 查询用户拥有的指定会话
     * @param userId 用户id
     * @param conversationId 会话id
     * @return 会话消息
     */
    private List<ChatMemory> listOwnedConversation(String userId, String conversationId) {
        QueryWrapper<ChatMemory> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("conversation_id", conversationId);
        queryWrapper.eq("user_id", userId);
        queryWrapper.orderByAsc("create_time");
        return chatMemoryMapper.selectList(queryWrapper);
    }

    /**
     * 按会话进行分组
     * @param chatMemoryList 消息列表
     * @return 分组结果
     */
    private Map<String, List<ChatMemory>> groupConversation(List<ChatMemory> chatMemoryList) {
        Map<String, List<ChatMemory>> grouped = new LinkedHashMap<>();
        for (ChatMemory chatMemory : chatMemoryList) {
            grouped.computeIfAbsent(chatMemory.getConversationId(), key -> new ArrayList<>()).add(chatMemory);
        }
        return grouped;
    }

    /**
     * 判断会话是否匹配模式
     * @param conversation 会话消息
     * @param mode 模式
     * @return 是否匹配
     */
    private boolean matchConversationMode(List<ChatMemory> conversation, String mode) {
        if (mode == null || mode.trim().isEmpty()) {
            return true;
        }
        for (ChatMemory chatMemory : conversation) {
            String metadataMode = getMetadataValue(chatMemory.getMetadata(), "mode");
            if (mode.equalsIgnoreCase(metadataMode)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 提取会话标题
     * @param conversation 会话消息
     * @return 标题
     */
    private String extractTitle(List<ChatMemory> conversation) {
        for (ChatMemory chatMemory : conversation) {
            String metadataTitle = getMetadataValue(chatMemory.getMetadata(), "title");
            if (metadataTitle != null && metadataTitle.trim().length() > 0) {
                return metadataTitle.trim();
            }
        }
        for (ChatMemory chatMemory : conversation) {
            if ("USER".equalsIgnoreCase(chatMemory.getMessageType())) {
                return safeText(chatMemory.getContent(), 20);
            }
        }
        return "新对话";
    }

    /**
     * 提取会话摘要
     * @param conversation 会话消息
     * @return 摘要
     */
    private String extractSummary(List<ChatMemory> conversation) {
        for (int i = conversation.size() - 1; i >= 0; i--) {
            ChatMemory chatMemory = conversation.get(i);
            if ("ASSISTANT".equalsIgnoreCase(chatMemory.getMessageType())) {
                return safeText(chatMemory.getContent(), 72);
            }
        }
        return safeText(conversation.get(conversation.size() - 1).getContent(), 72);
    }

    /**
     * 提取会话标签
     * @param conversation 会话消息
     * @return 标签
     */
    private String extractTag(List<ChatMemory> conversation) {
        for (ChatMemory chatMemory : conversation) {
            String tag = getMetadataValue(chatMemory.getMetadata(), "tag");
            if (tag != null && tag.trim().length() > 0) {
                return tag.trim();
            }
        }
        return null;
    }

    /**
     * 提取会话模式
     * @param conversation 会话消息
     * @return 会话模式
     */
    private String extractMode(List<ChatMemory> conversation) {
        for (ChatMemory chatMemory : conversation) {
            String mode = getMetadataValue(chatMemory.getMetadata(), "mode");
            if (mode != null && mode.trim().length() > 0) {
                return mode.trim();
            }
        }
        return null;
    }

    /**
     * 提取会话是否置顶
     * @param conversation 会话消息
     * @return 是否置顶
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
     * 读取metadata中的字段
     * @param metadata 元数据
     * @param key 字段
     * @return 值
     */
    private String getMetadataValue(String metadata, String key) {
        if (metadata == null || metadata.trim().length() == 0) {
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
     * 读取metadata中的布尔字段
     * @param metadata 元数据
     * @param key 字段
     * @return 布尔值
     */
    private Boolean getMetadataBoolean(String metadata, String key) {
        if (metadata == null || metadata.trim().length() == 0) {
            return null;
        }
        try {
            Map<String, Object> map = objectMapper.readValue(metadata, new TypeReference<Map<String, Object>>() {});
            Object value = map.get(key);
            if (value instanceof Boolean booleanValue) {
                return booleanValue;
            }
            if (value instanceof String stringValue) {
                return Boolean.parseBoolean(stringValue);
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 判断会话是否命中关键词
     * @param summary 会话摘要
     * @param keyword 关键词
     * @return 是否命中
     */
    private boolean matchConversationKeyword(ConversationSummary summary, String keyword) {
        String normalizedKeyword = keyword == null ? "" : keyword.trim().toLowerCase();
        if (normalizedKeyword.length() == 0) {
            return true;
        }
        return containsKeyword(summary.getTitle(), normalizedKeyword)
                || containsKeyword(summary.getSummary(), normalizedKeyword)
                || containsKeyword(summary.getLastMessage(), normalizedKeyword);
    }

    /**
     * 判断文本是否包含关键词
     * @param text 文本
     * @param keyword 关键词
     * @return 是否包含
     */
    private boolean containsKeyword(String text, String keyword) {
        if (text == null || keyword == null) {
            return false;
        }
        return text.toLowerCase().contains(keyword);
    }

    /**
     * 安全截断文本
     * @param text 文本
     * @param maxLen 最大长度
     * @return 截断后的文本
     */
    private String safeText(String text, int maxLen) {
        if (text == null) {
            return "";
        }
        String normalized = text.replaceAll("\\s+", " ").trim();
        if (normalized.length() <= maxLen) {
            return normalized;
        }
        return normalized.substring(0, maxLen) + "...";
    }

    /**
     * 校验并规范化会话id
     * @param conversationId 原始会话id
     * @return 规范化后的会话id
     */
    private String normalizeConversationId(String conversationId) {
        String normalizedConversationId = conversationId == null ? "" : conversationId.trim();
        if (normalizedConversationId.length() == 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "会话不存在");
        }
        return normalizedConversationId;
    }

    /**
     * 规范化会话标题
     * @param rawTitle 原始标题
     * @return 清洗后的标题
     */
    private String normalizeConversationTitle(String rawTitle) {
        String normalizedTitle = rawTitle == null ? "" : rawTitle.trim();
        normalizedTitle = normalizedTitle
                .replaceAll("[\\r\\n\\t]+", " ")
                .replaceAll("\\s{2,}", " ")
                .replaceFirst("^(标题|会话标题)\\s*[:：]\\s*", "")
                .trim();
        normalizedTitle = normalizedTitle.replaceAll("[。！？；;：:，,]+$", "").trim();
        if (normalizedTitle.length() == 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "会话标题不能为空");
        }
        if (normalizedTitle.length() > CONVERSATION_TITLE_MAX_LENGTH) {
            normalizedTitle = normalizedTitle.substring(0, CONVERSATION_TITLE_MAX_LENGTH).trim();
        }
        if (normalizedTitle.length() == 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "会话标题不能为空");
        }
        return normalizedTitle;
    }

    /**
     * 重写元数据中的标题字段
     * @param metadata 原始元数据
     * @param title 新标题
     * @return 更新后的元数据
     */
    private String rewriteMetadataTitle(String metadata, String title) {
        Map<String, Object> metadataMap = new LinkedHashMap<>();
        if (metadata != null && metadata.trim().length() > 0) {
            try {
                metadataMap.putAll(objectMapper.readValue(metadata, new TypeReference<Map<String, Object>>() {}));
            } catch (Exception ignored) {
            }
        }
        metadataMap.put("title", title);
        try {
            return objectMapper.writeValueAsString(metadataMap);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "更新会话标题失败");
        }
    }

    /**
     * 重写元数据中的置顶字段
     * @param metadata 原始元数据
     * @param pinned 是否置顶
     * @return 更新后的元数据
     */
    private String rewriteMetadataPinned(String metadata, boolean pinned) {
        Map<String, Object> metadataMap = new LinkedHashMap<>();
        if (metadata != null && metadata.trim().length() > 0) {
            try {
                metadataMap.putAll(objectMapper.readValue(metadata, new TypeReference<Map<String, Object>>() {}));
            } catch (Exception ignored) {
            }
        }
        metadataMap.put("pinned", pinned);
        try {
            return objectMapper.writeValueAsString(metadataMap);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "更新会话置顶状态失败");
        }
    }

}




