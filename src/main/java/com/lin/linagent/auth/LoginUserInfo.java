package com.lin.linagent.auth;

import lombok.Data;

import java.io.Serializable;

/**
 * 当前登录用户的会话信息
 */
@Data
public class LoginUserInfo implements Serializable {

    private static final long serialVersionUID = -1521365634208474298L;

    private String id;

    private String userName;

    /**
     * 用户角色：0-普通用户，1-管理员
     */
    private Integer userRole;
}
