package com.lin.linagent.auth;

import com.lin.linagent.domain.User;
import com.lin.linagent.exception.BusinessException;
import com.lin.linagent.exception.ErrorCode;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.apache.commons.lang3.StringUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

/**
 * 登录态工具类
 */
public final class AuthHelper {

    /**
     * 登录令牌签名密钥，优先读环境变量
     */
    private static final String AUTH_TOKEN_SECRET = System.getenv().getOrDefault("LIN_AUTH_TOKEN_SECRET", "lin-agent-auth-secret");

    private AuthHelper() {
    }

    /**
     * 写入登录态
     * @param request 请求
     * @param userId 用户id
     * @param userName 用户名
     */
    public static void login(HttpServletRequest request, HttpServletResponse response, String userId, String userName, Integer userRole) {
        if (request == null) {
            return;
        }
        LoginUserInfo loginUserInfo = new LoginUserInfo();
        loginUserInfo.setId(userId);
        loginUserInfo.setUserName(userName);
        loginUserInfo.setUserRole(userRole == null ? User.USER_ROLE_USER : userRole);
        writeLoginState(request, loginUserInfo);
        writeAuthTokenCookie(response, loginUserInfo);
    }

    /**
     * 读取登录用户，未登录则抛异常
     * @param request 请求
     * @return 登录用户
     */
    public static LoginUserInfo getLoginUser(HttpServletRequest request) {
        LoginUserInfo loginUserInfo = getNullableLoginUser(request);
        if (loginUserInfo == null) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "请先登录");
        }
        return loginUserInfo;
    }

    /**
     * 读取管理员登录用户，非管理员抛异常
     * @param request 请求
     * @return 登录用户
     */
    public static LoginUserInfo getAdminLoginUser(HttpServletRequest request) {
        LoginUserInfo loginUserInfo = getLoginUser(request);
        Integer userRole = loginUserInfo.getUserRole();
        if (userRole == null || !userRole.equals(User.USER_ROLE_ADMIN)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "仅管理员可访问该接口");
        }
        return loginUserInfo;
    }

    /**
     * 读取登录用户，允许为空
     * @param request 请求
     * @return 登录用户
     */
    public static LoginUserInfo getNullableLoginUser(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        Object requestAttribute = request.getAttribute(AuthConstant.LOGIN_USER_ATTRIBUTE);
        if (requestAttribute instanceof LoginUserInfo loginUserInfo) {
            return loginUserInfo;
        }
        HttpSession session = request.getSession(false);
        if (session != null) {
            Object sessionAttribute = session.getAttribute(AuthConstant.USER_LOGIN_STATE);
            if (sessionAttribute instanceof LoginUserInfo loginUserInfo) {
                request.setAttribute(AuthConstant.LOGIN_USER_ATTRIBUTE, loginUserInfo);
                return loginUserInfo;
            }
        }
        LoginUserInfo cookieLoginUserInfo = readLoginFromCookie(request);
        if (cookieLoginUserInfo != null) {
            writeLoginState(request, cookieLoginUserInfo);
            return cookieLoginUserInfo;
        }
        return null;
    }

    /**
     * 清理登录态
     * @param request 请求
     */
    public static void logout(HttpServletRequest request, HttpServletResponse response) {
        if (request == null) {
            return;
        }
        request.removeAttribute(AuthConstant.LOGIN_USER_ATTRIBUTE);
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        clearAuthTokenCookie(response);
    }

    /**
     * 写入登录状态到 request + session
     * @param request 请求
     * @param loginUserInfo 登录信息
     */
    private static void writeLoginState(HttpServletRequest request, LoginUserInfo loginUserInfo) {
        if (request == null || loginUserInfo == null) {
            return;
        }
        HttpSession session = request.getSession(true);
        session.setAttribute(AuthConstant.USER_LOGIN_STATE, loginUserInfo);
        request.setAttribute(AuthConstant.LOGIN_USER_ATTRIBUTE, loginUserInfo);
    }

    /**
     * 从 cookie 恢复登录信息
     * @param request 请求
     * @return 登录信息
     */
    private static LoginUserInfo readLoginFromCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null || cookies.length == 0) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (cookie == null || !AuthConstant.AUTH_TOKEN_COOKIE.equals(cookie.getName())) {
                continue;
            }
            return parseAuthToken(cookie.getValue());
        }
        return null;
    }

    /**
     * 写入持久化登录 cookie
     * @param response 响应
     * @param loginUserInfo 登录信息
     */
    private static void writeAuthTokenCookie(HttpServletResponse response, LoginUserInfo loginUserInfo) {
        if (response == null || loginUserInfo == null) {
            return;
        }
        String token = buildAuthToken(loginUserInfo);
        if (StringUtils.isBlank(token)) {
            return;
        }
        Cookie cookie = new Cookie(AuthConstant.AUTH_TOKEN_COOKIE, token);
        cookie.setHttpOnly(true);
        cookie.setPath("/");
        cookie.setMaxAge(AuthConstant.AUTH_TOKEN_TTL_SECONDS);
        response.addCookie(cookie);
    }

    /**
     * 清理持久化登录 cookie
     * @param response 响应
     */
    private static void clearAuthTokenCookie(HttpServletResponse response) {
        if (response == null) {
            return;
        }
        Cookie cookie = new Cookie(AuthConstant.AUTH_TOKEN_COOKIE, "");
        cookie.setHttpOnly(true);
        cookie.setPath("/");
        cookie.setMaxAge(0);
        response.addCookie(cookie);
    }

    /**
     * 构建签名令牌
     * @param loginUserInfo 登录信息
     * @return 令牌字符串
     */
    private static String buildAuthToken(LoginUserInfo loginUserInfo) {
        if (loginUserInfo == null || StringUtils.isBlank(loginUserInfo.getId())) {
            return "";
        }
        long expiresAtMillis = System.currentTimeMillis() + AuthConstant.AUTH_TOKEN_TTL_SECONDS * 1000L;
        String fieldUserId = encodeField(loginUserInfo.getId());
        String fieldUserName = encodeField(StringUtils.defaultString(loginUserInfo.getUserName()));
        String fieldUserRole = encodeField(String.valueOf(loginUserInfo.getUserRole() == null ? User.USER_ROLE_USER : loginUserInfo.getUserRole()));
        String fieldExpire = encodeField(String.valueOf(expiresAtMillis));
        String payload = fieldUserId + "." + fieldUserName + "." + fieldUserRole + "." + fieldExpire;
        String signature = sign(payload);
        if (StringUtils.isBlank(signature)) {
            return "";
        }
        return payload + "." + signature;
    }

    /**
     * 解析并校验签名令牌
     * @param token 令牌
     * @return 登录信息
     */
    private static LoginUserInfo parseAuthToken(String token) {
        if (StringUtils.isBlank(token)) {
            return null;
        }
        String[] parts = token.split("\\.");
        if (parts.length != 5) {
            return null;
        }
        String payload = parts[0] + "." + parts[1] + "." + parts[2] + "." + parts[3];
        String expectedSign = sign(payload);
        if (StringUtils.isBlank(expectedSign)) {
            return null;
        }
        byte[] actualSignBytes = parts[4].getBytes(StandardCharsets.UTF_8);
        byte[] expectedSignBytes = expectedSign.getBytes(StandardCharsets.UTF_8);
        if (!MessageDigest.isEqual(actualSignBytes, expectedSignBytes)) {
            return null;
        }
        String userId = decodeField(parts[0]);
        String userName = decodeField(parts[1]);
        String userRoleText = decodeField(parts[2]);
        String expiresAtText = decodeField(parts[3]);
        if (StringUtils.isBlank(userId) || StringUtils.isBlank(expiresAtText)) {
            return null;
        }
        long expiresAtMillis;
        int userRole;
        try {
            expiresAtMillis = Long.parseLong(expiresAtText);
            userRole = Integer.parseInt(StringUtils.defaultIfBlank(userRoleText, String.valueOf(User.USER_ROLE_USER)));
        } catch (Exception e) {
            return null;
        }
        if (System.currentTimeMillis() > expiresAtMillis) {
            return null;
        }
        LoginUserInfo loginUserInfo = new LoginUserInfo();
        loginUserInfo.setId(userId);
        loginUserInfo.setUserName(userName);
        loginUserInfo.setUserRole(userRole);
        return loginUserInfo;
    }

    /**
     * 对字段做 base64-url 编码
     * @param value 原值
     * @return 编码值
     */
    private static String encodeField(String value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(StringUtils.defaultString(value).getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 对字段做 base64-url 解码
     * @param encoded 编码值
     * @return 原值
     */
    private static String decodeField(String encoded) {
        try {
            byte[] bytes = Base64.getUrlDecoder().decode(StringUtils.defaultString(encoded));
            return new String(bytes, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * HMAC-SHA256 签名
     * @param payload 载荷
     * @return 签名
     */
    private static String sign(String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKeySpec = new SecretKeySpec(AUTH_TOKEN_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(secretKeySpec);
            byte[] signBytes = mac.doFinal(StringUtils.defaultString(payload).getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(signBytes);
        } catch (Exception e) {
            return "";
        }
    }
}
