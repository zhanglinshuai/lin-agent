package com.lin.linagent.controller;

import com.lin.linagent.domain.ChatMemory;
import com.lin.linagent.domain.dto.ConversationSummary;
import com.lin.linagent.exception.BusinessException;
import com.lin.linagent.exception.ErrorCode;
import com.lin.linagent.service.ChatMemoryService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

@RequestMapping("/chat_memory")
@RestController
public class ChatMemoryController {
    @Resource
    private ChatMemoryService chatMemoryService;
    /**
     * 根据用户名获取用户id然后获取用户的消息列表
     * @param username
     * @return
     */
    @GetMapping("/getChatMemoryList")
    public List<ChatMemory> getUserChatMemoryList(String username, String mode){
        if(username==null){
            throw new BusinessException(ErrorCode.PARAMS_ERROR,"请用户先登录");
        }
        List<ChatMemory> userConversationList = chatMemoryService.getUserConversationList(username, mode);
        if(userConversationList==null || userConversationList.isEmpty()){
            throw new BusinessException(ErrorCode.PARAMS_ERROR,"用户未创建会话");
        }
        return userConversationList;
    }

    /**
     * 获取用户会话摘要列表
     * @param username 用户名
     * @return 会话摘要列表
     */
    @GetMapping("/getConversationSummaries")
    public List<ConversationSummary> getConversationSummaries(String username, String mode){
        if(username == null){
            throw new BusinessException(ErrorCode.PARAMS_ERROR,"请用户先登录");
        }
        return chatMemoryService.getUserConversationSummaries(username, mode);
    }

    /**
     * 获取属于conversationId下的所有对话
     * @param conversationId
     * @return
     */
    @GetMapping("/getConversation")
    public List<ChatMemory> getUserConversation(String conversationId){
        if(conversationId==null){
            throw new BusinessException(ErrorCode.PARAMS_ERROR,"会话不存在");
        }
        return chatMemoryService.getUserConversation(conversationId);
    }

}
