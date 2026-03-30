package com.lin.linagent.domain.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 用户资料更新请求
 */
@Data
public class UserProfileUpdateRequest implements Serializable {

    private static final long serialVersionUID = -8602090490533245204L;

    private String id;

    private String userName;

    private String userPhone;
}
