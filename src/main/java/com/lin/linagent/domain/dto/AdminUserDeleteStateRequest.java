package com.lin.linagent.domain.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 管理后台更新用户删除状态请求
 */
@Data
public class AdminUserDeleteStateRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    private String userId;

    /**
     * 是否删除
     */
    private Boolean deleted;
}
