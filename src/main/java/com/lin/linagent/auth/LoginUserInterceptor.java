package com.lin.linagent.auth;

import com.lin.linagent.domain.User;
import com.lin.linagent.exception.BusinessException;
import com.lin.linagent.exception.ErrorCode;
import com.lin.linagent.support.OperationLogHelper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 登录校验拦截器
 */
@Component
public class LoginUserInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (request.getAttribute(OperationLogHelper.REQUEST_START_TIME_ATTRIBUTE) == null) {
            request.setAttribute(OperationLogHelper.REQUEST_START_TIME_ATTRIBUTE, System.currentTimeMillis());
        }
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }
        boolean requireAdmin = hasRequireAdmin(handlerMethod);
        boolean requireLogin = requireAdmin || hasRequireLogin(handlerMethod);
        if (!requireLogin) {
            return true;
        }
        LoginUserInfo loginUserInfo = AuthHelper.getNullableLoginUser(request);
        if (loginUserInfo == null || loginUserInfo.getId() == null || loginUserInfo.getId().trim().isEmpty()) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "请先登录后再继续操作");
        }
        if (requireAdmin) {
            Integer userRole = loginUserInfo.getUserRole();
            if (userRole == null || !userRole.equals(User.USER_ROLE_ADMIN)) {
                throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "仅管理员可访问该接口");
            }
        }
        request.setAttribute(AuthConstant.LOGIN_USER_ATTRIBUTE, loginUserInfo);
        return true;
    }

    private boolean hasRequireLogin(HandlerMethod handlerMethod) {
        return handlerMethod.getMethodAnnotation(RequireLogin.class) != null
                || handlerMethod.getBeanType().getAnnotation(RequireLogin.class) != null;
    }

    private boolean hasRequireAdmin(HandlerMethod handlerMethod) {
        return handlerMethod.getMethodAnnotation(RequireAdmin.class) != null
                || handlerMethod.getBeanType().getAnnotation(RequireAdmin.class) != null;
    }
}
