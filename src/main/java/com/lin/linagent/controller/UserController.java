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
import org.springframework.web.bind.annotation.*;

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
}
