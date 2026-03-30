package com.lin.linagent.auth;

/**
 * 登录鉴权相关常量
 */
public interface AuthConstant {

    /**
     * session 中保存登录用户信息的键
     */
    String USER_LOGIN_STATE = "user_login_state";

    /**
     * request 中保存登录用户信息的键
     */
    String LOGIN_USER_ATTRIBUTE = "login_user_attribute";

    /**
     * 持久化登录令牌 cookie 名称
     */
    String AUTH_TOKEN_COOKIE = "lin_auth_token";

    /**
     * 持久化登录令牌有效期（秒），默认 7 天
     */
    int AUTH_TOKEN_TTL_SECONDS = 7 * 24 * 60 * 60;
}
