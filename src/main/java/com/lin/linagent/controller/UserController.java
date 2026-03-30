package com.lin.linagent.controller;

import com.lin.linagent.auth.AuthHelper;
import com.lin.linagent.auth.RequireLogin;
import com.lin.linagent.common.BaseResponse;
import com.lin.linagent.common.ResultUtils;
import com.lin.linagent.domain.User;
import com.lin.linagent.domain.dto.UserLoginRequest;
import com.lin.linagent.domain.dto.UserLoginVO;
import com.lin.linagent.domain.dto.UserProfileUpdateRequest;
import com.lin.linagent.domain.dto.UserProfileVO;
import com.lin.linagent.domain.dto.UserRegisterRequest;
import com.lin.linagent.exception.BusinessException;
import com.lin.linagent.exception.ErrorCode;
import com.lin.linagent.service.UserService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
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
    public BaseResponse<UserLoginVO> userLogin(@RequestBody UserLoginRequest userLoginRequest, HttpServletRequest request, HttpServletResponse response) {
        if(userLoginRequest == null){
            throw new BusinessException(ErrorCode.PARAMS_ERROR,"登录请求参数为空");
        }
        String username = userLoginRequest.getUserName();
        String password = userLoginRequest.getUserPassword();
        if(username==null || password==null){
            throw new BusinessException(ErrorCode.PARAMS_ERROR,"账号或密码为空");
        }
        String userId = userService.userLogin(username, password);
        User user = userService.getUserInfo(userId);
        AuthHelper.login(request, response, userId, user.getUserName(), user.getUserRole());
        UserLoginVO userLoginVO = new UserLoginVO();
        userLoginVO.setUserId(userId);
        userLoginVO.setUserName(user.getUserName());
        userLoginVO.setUserRole(user.getUserRole());
        return ResultUtils.success(userLoginVO);
    }


    @GetMapping("/getUserInfo")
    @RequireLogin
    public BaseResponse<UserProfileVO> getUserInfo(String userId, HttpServletRequest request){
        String currentUserId = AuthHelper.getLoginUser(request).getId();
        User user = userService.getUserInfo(currentUserId);
        if (user == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR,"用户不存在");
        }
        return ResultUtils.success(toUserProfileVO(user));
    }


    @PostMapping("/updateUserInfo")
    @RequireLogin
    public BaseResponse<UserProfileVO> updateUserInfo(@RequestBody UserProfileUpdateRequest request, HttpServletRequest httpServletRequest){
        if(request==null){
            throw new BusinessException(ErrorCode.PARAMS_ERROR,"新用户信息为空");
        }
        request.setId(AuthHelper.getLoginUser(httpServletRequest).getId());
        User newUser = userService.updateUserInfo(request);
        return ResultUtils.success(toUserProfileVO(newUser));
    }
    @PostMapping("/uploadAvatar")
    @RequireLogin
    public BaseResponse<String> uploadAvatar(MultipartFile file, String userId, HttpServletRequest request){
        String currentUserId = AuthHelper.getLoginUser(request).getId();
        if(file.isEmpty()){
            throw new BusinessException(ErrorCode.PARAMS_ERROR,"上传文件不存在");
        }
        String avatarUrl = userService.uploadAvatar(file, currentUserId);
        return ResultUtils.success(avatarUrl);
    }

    @PostMapping("/exit")
    @RequireLogin
    public void logout(HttpServletRequest request, HttpServletResponse response){
        if (request==null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR,"请求错误");
        }
        AuthHelper.logout(request, response);
    }

    private UserProfileVO toUserProfileVO(User user) {
        UserProfileVO userProfileVO = new UserProfileVO();
        userProfileVO.setId(user.getId());
        userProfileVO.setUserName(user.getUserName());
        userProfileVO.setUserPhone(user.getUserPhone());
        userProfileVO.setUserAvatar(user.getUserAvatar());
        userProfileVO.setUserRole(user.getUserRole());
        userProfileVO.setCreateTime(user.getCreateTime());
        userProfileVO.setUpdateTime(user.getUpdateTime());
        return userProfileVO;
    }
}
