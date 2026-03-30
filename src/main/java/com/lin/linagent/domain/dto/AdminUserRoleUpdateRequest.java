package com.lin.linagent.domain.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 管理后台更新用户角色请求
 */
@Data
public class AdminUserRoleUpdateRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    private String userId;

    /**
     * 用户角色：0-普通用户，1-管理员
     */
    private Integer userRole;
}
