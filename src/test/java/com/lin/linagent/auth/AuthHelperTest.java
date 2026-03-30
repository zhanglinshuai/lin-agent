package com.lin.linagent.auth;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * AuthHelper 测试
 */
class AuthHelperTest {

    @Test
    void shouldRestoreLoginFromCookieWhenSessionMissing() {
        MockHttpServletRequest loginRequest = new MockHttpServletRequest();
        MockHttpServletResponse loginResponse = new MockHttpServletResponse();
        AuthHelper.login(loginRequest, loginResponse, "user-1", "测试用户", 0);

        Cookie authCookie = loginResponse.getCookie(AuthConstant.AUTH_TOKEN_COOKIE);
        Assertions.assertNotNull(authCookie);

        MockHttpServletRequest nextRequest = new MockHttpServletRequest();
        nextRequest.setCookies(authCookie);

        LoginUserInfo loginUserInfo = AuthHelper.getNullableLoginUser(nextRequest);

        Assertions.assertNotNull(loginUserInfo);
        Assertions.assertEquals("user-1", loginUserInfo.getId());
        Assertions.assertEquals("测试用户", loginUserInfo.getUserName());
        Assertions.assertNotNull(nextRequest.getSession(false));
        Assertions.assertSame(loginUserInfo, nextRequest.getAttribute(AuthConstant.LOGIN_USER_ATTRIBUTE));
    }
}
