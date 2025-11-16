package com.lin.linagent.service;

import com.lin.linagent.domain.ChatMemory;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

/**
* @author zhanglinshuai
* @description 针对表【chat_memory(存储消息)】的数据库操作Service
* @createDate 2025-11-15 15:39:48
*/
public interface ChatMemoryService extends IService<ChatMemory> {


    /**
     * 获取用户的会话列表
     * @param username
     * @return
     */
    List<ChatMemory> getUserConversationList(String username);

    /**
     * 获取用户的指定会话内容
     * @param conversationId
     * @return
     */
    List<ChatMemory> getUserConversation(String conversationId);
}
