package com.lin.linagent.domain.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.Data;

import java.io.Serializable;
@Data
public class UserLoginRequest implements Serializable {

    private static final long serialVersionUID = 5594387118782670358L;

    @JsonAlias({"username"})
    private String userName;
    private String userPassword;

}
