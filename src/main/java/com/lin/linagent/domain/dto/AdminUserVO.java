package com.lin.linagent.domain.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 管理后台用户信息
 */
@Data
public class AdminUserVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private String id;

    private String userName;

    private String userPhone;

    private Integer userRole;

    private Boolean deleted;

    private Date createTime;

    private Date updateTime;

    private Long conversationCount;

    private Long messageCount;
}
