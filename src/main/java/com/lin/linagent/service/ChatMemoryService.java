package com.lin.linagent.service;

import com.lin.linagent.domain.ChatMemory;
import com.lin.linagent.domain.dto.ConversationSummary;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

/**
* @author zhanglinshuai
* @description 针对表【chat_memory(存储消息)】的数据库操作Service
* @createDate 2025-11-15 15:39:48
*/
public interface ChatMemoryService extends IService<ChatMemory> {

    /**
     * 创建会话
     * @param username 用户名
     * @param conversationId 会话id
     * @return 是否成功
     */
    boolean createConversation(String username, String conversationId);


    /**
     * 获取用户的会话列表
     * @param username
     * @return
     */
    List<ChatMemory> getUserConversationList(String username, String mode);

    /**
     * 获取用户会话摘要列表
     * @param username 用户名
     * @return 会话摘要列表
     */
    List<ConversationSummary> getUserConversationSummaries(String username, String mode, String keyword);

    /**
     * 获取用户的指定会话内容
     * @param conversationId
     * @return
     */
    List<ChatMemory> getUserConversation(String conversationId);

    /**
     * 获取当前登录用户的指定会话内容
     * @param conversationId 会话id
     * @param userId 用户id
     * @return 会话内容
     */
    List<ChatMemory> getUserConversation(String conversationId, String userId);

    /**
     * 重命名用户会话
     * @param username 用户名
     * @param conversationId 会话id
     * @param title 新标题
     * @return 是否成功
     */
    boolean renameConversation(String username, String conversationId, String title);

    /**
     * 删除用户会话
     * @param username 用户名
     * @param conversationId 会话id
     * @return 是否成功
     */
    boolean deleteConversation(String username, String conversationId);

    /**
     * 设置会话置顶状态
     * @param username 用户名
     * @param conversationId 会话id
     * @param pinned 是否置顶
     * @return 是否成功
     */
    boolean pinConversation(String username, String conversationId, boolean pinned);
}
