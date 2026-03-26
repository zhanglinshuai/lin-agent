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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
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
    
    @Resource
    private UserMapper userMapper;
    
    @Resource
    private ChatMemoryMapper chatMemoryMapper;
    
    

    @Override
    public List<ChatMemory> getUserConversationList(String username, String mode) {
        User user = getUserByUsername(username);
        List<ChatMemory> chatMemoryList = listUserChatMemories(user.getId());
        if (chatMemoryList.isEmpty()){
            return new ArrayList<>();
        }
        Map<String, List<ChatMemory>> grouped = groupConversation(chatMemoryList);
        List<ChatMemory> result = new ArrayList<>();
        for (List<ChatMemory> conversation : grouped.values()) {
            if (!matchConversationMode(conversation, mode)) {
                continue;
            }
            ChatMemory latest = conversation.stream()
                    .max(Comparator.comparing(ChatMemory::getCreateTime, Comparator.nullsLast(Date::compareTo)))
                    .orElse(null);
            if (latest != null) {
                result.add(latest);
            }
        }
        result.sort(Comparator.comparing(ChatMemory::getCreateTime, Comparator.nullsLast(Date::compareTo)).reversed());
        return result;
    }

    @Override
    public List<ConversationSummary> getUserConversationSummaries(String username, String mode) {
        User user = getUserByUsername(username);
        List<ChatMemory> chatMemoryList = listUserChatMemories(user.getId());
        if(chatMemoryList == null || chatMemoryList.isEmpty()){
            return new ArrayList<>();
        }
        Map<String, List<ChatMemory>> grouped = groupConversation(chatMemoryList);
        List<ConversationSummary> result = new ArrayList<>();
        for (Map.Entry<String, List<ChatMemory>> entry : grouped.entrySet()) {
            List<ChatMemory> conversation = entry.getValue();
            if (!matchConversationMode(conversation, mode)) {
                continue;
            }
            conversation.sort(Comparator.comparing(ChatMemory::getCreateTime));
            ChatMemory latest = conversation.get(conversation.size() - 1);
            ConversationSummary summary = new ConversationSummary();
            summary.setConversationId(entry.getKey());
            summary.setTitle(extractTitle(conversation));
            summary.setTag(extractTag(conversation));
            summary.setMode(extractMode(conversation));
            summary.setLastMessage(safeText(latest.getContent(), 90));
            summary.setSummary(extractSummary(conversation));
            summary.setLastTime(latest.getCreateTime());
            summary.setMessageCount(conversation.size());
            result.add(summary);
        }
        result.sort(Comparator.comparing(ConversationSummary::getLastTime, Comparator.nullsLast(Date::compareTo)).reversed());
        return result;
    }

    @Override
    public List<ChatMemory> getUserConversation(String conversationId) {
        if(conversationId==null||conversationId.length()==0){
            throw new BusinessException(ErrorCode.PARAMS_ERROR,"会话不存在");
        }
        //根据conversationId找出会话
        QueryWrapper<ChatMemory> chatMemoryQueryWrapper = new QueryWrapper<>();
        chatMemoryQueryWrapper.eq("conversation_id",conversationId);
        chatMemoryQueryWrapper.orderByAsc("create_time");
        List<ChatMemory> chatMemoryList = chatMemoryMapper.selectList(chatMemoryQueryWrapper);
        if (chatMemoryList.isEmpty()){
            return new ArrayList<>();
        }
        return chatMemoryList;
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
            ObjectMapper objectMapper = new ObjectMapper();
            Map<String, Object> map = objectMapper.readValue(metadata, new TypeReference<Map<String, Object>>() {});
            Object value = map.get(key);
            return value == null ? null : String.valueOf(value);
        } catch (Exception e) {
            return null;
        }
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

}




