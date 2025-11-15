package com.lin.linagent.domain.dto;

import lombok.Data;

import java.io.Serializable;
@Data
public class UserLoginRequest implements Serializable {

    private static final long serialVersionUID = 5594387118782670358L;

    private String userName;
    private String userPassword;

}
