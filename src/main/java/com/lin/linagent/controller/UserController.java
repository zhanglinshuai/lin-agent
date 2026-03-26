package com.lin.linagent.controller;

import com.lin.linagent.common.BaseResponse;
import com.lin.linagent.common.ResultUtils;
import com.lin.linagent.domain.User;
import com.lin.linagent.domain.dto.UserLoginRequest;
import com.lin.linagent.domain.dto.UserRegisterRequest;
import com.lin.linagent.exception.BusinessException;
import com.lin.linagent.exception.ErrorCode;
import com.lin.linagent.service.UserService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.jsoup.Connection;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * 用户相关操作
 */
@RestController
@RequestMapping("/user")
public class UserController {

    @Resource
    private UserService userService;

    @PostMapping("/register")
    public BaseResponse<String> userRegister(@RequestBody UserRegisterRequest  userRegisterRequest) {
        if(userRegisterRequest == null){
            throw new BusinessException(ErrorCode.PARAMS_ERROR,"注册请求参数为空");
        }
        String userName = userRegisterRequest.getUsername();
        String password = userRegisterRequest.getUserPassword();
        String checkPassword = userRegisterRequest.getCheckPassword();
        if(userName==null || password==null || checkPassword==null){
            throw new BusinessException(ErrorCode.PARAMS_ERROR,"登录请求参数为空");
        }
        String userId = userService.userRegister(userName, password, checkPassword);
        return ResultUtils.success(userId);
    }
    @PostMapping("/login")
    public BaseResponse<String> userLogin(@RequestBody UserLoginRequest userLoginRequest) {
        if(userLoginRequest == null){
            throw new BusinessException(ErrorCode.PARAMS_ERROR,"登录请求参数为空");
        }
        String username = userLoginRequest.getUserName();
        String password = userLoginRequest.getUserPassword();
        if(username==null || password==null){
            throw new BusinessException(ErrorCode.PARAMS_ERROR,"账号或密码为空");
        }
        String userId = userService.userLogin(username, password);
        return ResultUtils.success(userId);
    }


    @GetMapping("/getUserInfo")
    public BaseResponse<User> getUserInfo(String userId){
        if(userId==null){
            throw new BusinessException(ErrorCode.PARAMS_ERROR,"用户不存在");
        }
        User user = userService.getUserInfo(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR,"用户不存在");
        }
        return ResultUtils.success(user);
    }


    @PostMapping("/updateUserInfo")
    public BaseResponse<User> updateUserInfo(User user){
        if(user==null){
            throw new BusinessException(ErrorCode.PARAMS_ERROR,"新用户信息为空");
        }
        User newUser = userService.updateUserInfo(user);
        return ResultUtils.success(newUser);
    }
    @PostMapping("/uploadAvatar")
    public BaseResponse<String> uploadAvatar(MultipartFile file, String userId){
        if(userId==null){
            throw new BusinessException(ErrorCode.PARAMS_ERROR,"用户不存在");
        }
        if(file.isEmpty()){
            throw new BusinessException(ErrorCode.PARAMS_ERROR,"上传文件不存在");
        }
        String avatarUrl = userService.uploadAvatar(file, userId);
        return ResultUtils.success(avatarUrl);
    }

    @PostMapping("/exit")
    public void logout(HttpServletRequest request){
        if (request==null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR,"请求错误");
        }
        //session失效
        request.getSession().invalidate();
    }
}
