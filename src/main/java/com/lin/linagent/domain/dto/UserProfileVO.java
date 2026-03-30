package com.lin.linagent.domain.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 脱敏后的用户资料
 */
@Data
public class UserProfileVO implements Serializable {

    private static final long serialVersionUID = 2947239524526283247L;

    private String id;

    private String userName;

    private String userPhone;

    private String userAvatar;

    /**
     * 用户角色：0-普通用户，1-管理员
     */
    private Integer userRole;

    private Date createTime;

    private Date updateTime;
}
