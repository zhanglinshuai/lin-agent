package com.lin.linagent.controller;

import com.lin.linagent.auth.AuthHelper;
import com.lin.linagent.auth.LoginUserInfo;
import com.lin.linagent.auth.RequireLogin;
import com.lin.linagent.common.BaseResponse;
import com.lin.linagent.common.ResultUtils;
import com.lin.linagent.domain.ChatMemory;
import com.lin.linagent.domain.dto.ConversationCreateRequest;
import com.lin.linagent.domain.dto.ConversationDeleteRequest;
import com.lin.linagent.domain.dto.ConversationPinRequest;
import com.lin.linagent.domain.dto.ConversationRenameRequest;
import com.lin.linagent.domain.dto.ConversationSummary;
import com.lin.linagent.exception.BusinessException;
import com.lin.linagent.exception.ErrorCode;
import com.lin.linagent.service.ChatMemoryService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

@RequestMapping("/chat_memory")
@RestController
@RequireLogin
public class ChatMemoryController {
    @Resource
    private ChatMemoryService chatMemoryService;

    /**
     * 创建会话
     * @param request 创建请求
     * @param httpServletRequest 请求
     * @return 是否成功
     */
    @PostMapping("/createConversation")
    public BaseResponse<Boolean> createConversation(@RequestBody ConversationCreateRequest request, HttpServletRequest httpServletRequest) {
        if (request == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "创建会话请求不能为空");
        }
        LoginUserInfo loginUser = AuthHelper.getLoginUser(httpServletRequest);
        return ResultUtils.success(chatMemoryService.createConversation(
                loginUser.getUserName(),
                request.getConversationId()
        ));
    }

    /**
     * 根据用户名获取用户id然后获取用户的消息列表
     * @param username
     * @return
     */
    @GetMapping("/getChatMemoryList")
    public List<ChatMemory> getUserChatMemoryList(String username, String mode, HttpServletRequest request){
        LoginUserInfo loginUser = AuthHelper.getLoginUser(request);
        List<ChatMemory> userConversationList = chatMemoryService.getUserConversationList(loginUser.getUserName(), mode);
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
    public List<ConversationSummary> getConversationSummaries(String username, String mode, String keyword, HttpServletRequest request){
        LoginUserInfo loginUser = AuthHelper.getLoginUser(request);
        return chatMemoryService.getUserConversationSummaries(loginUser.getUserName(), mode, keyword);
    }

    /**
     * 获取属于conversationId下的所有对话
     * @param conversationId
     * @return
     */
    @GetMapping("/getConversation")
    public List<ChatMemory> getUserConversation(String conversationId, HttpServletRequest request){
        if(conversationId==null){
            throw new BusinessException(ErrorCode.PARAMS_ERROR,"会话不存在");
        }
        LoginUserInfo loginUser = AuthHelper.getLoginUser(request);
        return chatMemoryService.getUserConversation(conversationId, loginUser.getId());
    }

    /**
     * 重命名会话
     * @param request 重命名请求
     * @return 是否成功
     */
    @PostMapping("/renameConversation")
    public BaseResponse<Boolean> renameConversation(@RequestBody ConversationRenameRequest request, HttpServletRequest httpServletRequest) {
        if (request == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "重命名请求不能为空");
        }
        LoginUserInfo loginUser = AuthHelper.getLoginUser(httpServletRequest);
        return ResultUtils.success(chatMemoryService.renameConversation(
                loginUser.getUserName(),
                request.getConversationId(),
                request.getTitle()
        ));
    }

    /**
     * 删除会话
     * @param request 删除请求
     * @return 是否成功
     */
    @PostMapping("/deleteConversation")
    public BaseResponse<Boolean> deleteConversation(@RequestBody ConversationDeleteRequest request, HttpServletRequest httpServletRequest) {
        if (request == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "删除请求不能为空");
        }
        LoginUserInfo loginUser = AuthHelper.getLoginUser(httpServletRequest);
        return ResultUtils.success(chatMemoryService.deleteConversation(
                loginUser.getUserName(),
                request.getConversationId()
        ));
    }

    /**
     * 设置会话置顶状态
     * @param request 置顶请求
     * @return 是否成功
     */
    @PostMapping("/pinConversation")
    public BaseResponse<Boolean> pinConversation(@RequestBody ConversationPinRequest request, HttpServletRequest httpServletRequest) {
        if (request == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "置顶请求不能为空");
        }
        LoginUserInfo loginUser = AuthHelper.getLoginUser(httpServletRequest);
        return ResultUtils.success(chatMemoryService.pinConversation(
                loginUser.getUserName(),
                request.getConversationId(),
                Boolean.TRUE.equals(request.getPinned())
        ));
    }

}
