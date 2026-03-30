package com.lin.linagent.domain.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 登录返回信息
 */
@Data
public class UserLoginVO implements Serializable {

    private static final long serialVersionUID = -1171770933724345200L;

    private String userId;

    private String userName;

    /**
     * 用户角色：0-普通用户，1-管理员
     */
    private Integer userRole;
}
