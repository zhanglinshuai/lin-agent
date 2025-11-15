package com.lin.linagent.service;

import com.lin.linagent.domain.User;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

/**
* @author zhanglinshuai
* @description 针对表【user】的数据库操作Service
* @createDate 2025-11-14 23:46:12
*/

public interface UserService extends IService<User> {

    /**
     * 用户注册
     * @param username
     * @param password
     * @param checkPassword
     * @return
     */
    String userRegister(String username, String password,String checkPassword);

    /**
     * 用户登录
     * @param username
     * @param password
     * @return
     */
    String userLogin(String username,String password);


}
