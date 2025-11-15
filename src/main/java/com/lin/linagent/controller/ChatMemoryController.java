package com.lin.linagent.controller;

import com.lin.linagent.domain.ChatMemory;
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
    public List<ChatMemory> getUserChatMemoryList(String username){
        if(username==null){
            throw new BusinessException(ErrorCode.PARAMS_ERROR,"请用户先登录");
        }
        List<ChatMemory> userConversationList = chatMemoryService.getUserConversationList(username);
        if(userConversationList==null || userConversationList.isEmpty()){
            throw new BusinessException(ErrorCode.PARAMS_ERROR,"用户未创建会话");
        }
        return userConversationList;
    }
}
