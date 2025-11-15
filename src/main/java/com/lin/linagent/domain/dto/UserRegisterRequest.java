package com.lin.linagent.domain.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 用户注册参数
 * @author zhanglinshuai
 */
@Data
public class UserRegisterRequest implements Serializable {

    private static final long serialVersionUID = 6749873730429881410L;


    private String username;

    private String userPassword;

    private String checkPassword;
}
