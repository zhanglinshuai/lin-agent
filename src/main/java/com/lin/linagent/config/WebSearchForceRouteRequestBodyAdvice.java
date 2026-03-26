package com.lin.linagent.config;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.core.MethodParameter;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.util.ReflectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.servlet.mvc.method.annotation.RequestBodyAdviceAdapter;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * 当请求开启联网搜索时，强制优先经过 webSearch 工具类，避免后续逻辑绕过工具直接访问搜索提供方。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice
public class WebSearchForceRouteRequestBodyAdvice extends RequestBodyAdviceAdapter implements ApplicationContextAware {

    public static final String WEB_SEARCH_RESULT_ATTRIBUTE = "forcedWebSearchResult";

    private ApplicationContext applicationContext;

    @Override
    public void setApplicationContext(@NonNull ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

    @Override
    public boolean supports(@NonNull MethodParameter methodParameter,
                            @NonNull java.lang.reflect.Type targetType,
                            @NonNull Class<? extends HttpMessageConverter<?>> converterType) {
        return true;
    }

    @Override
    public Object afterBodyRead(@NonNull Object body,
                                @NonNull HttpInputMessage inputMessage,
                                @NonNull MethodParameter parameter,
                                @NonNull java.lang.reflect.Type targetType,
                                @NonNull Class<? extends HttpMessageConverter<?>> converterType) {
        if (!WebSearchRequestReflectionSupport.isWebSearchEnabled(body)) {
            return body;
        }

        if (isCurrentRequestWebSearchEndpoint()) {
            return body;
        }

        String query = WebSearchRequestReflectionSupport.extractUserQuery(body);
        if (!StringUtils.hasText(query)) {
            return body;
        }

        Object webSearchBean = resolveWebSearchBean();
        if (webSearchBean == null) {
            return body;
        }

        String searchResult = invokeWebSearch(webSearchBean, query);
        if (!StringUtils.hasText(searchResult)) {
            return body;
        }

        WebSearchRequestReflectionSupport.disableWebSearchFlags(body);
        WebSearchRequestReflectionSupport.injectSearchResult(body, searchResult);
        RequestAttributes requestAttributes = RequestContextHolder.getRequestAttributes();
        if (requestAttributes != null) {
            requestAttributes.setAttribute(WEB_SEARCH_RESULT_ATTRIBUTE, searchResult, RequestAttributes.SCOPE_REQUEST);
        }

        return body;
    }

    private boolean isCurrentRequestWebSearchEndpoint() {
        RequestAttributes requestAttributes = RequestContextHolder.getRequestAttributes();
        if (!(requestAttributes instanceof ServletRequestAttributes servletRequestAttributes)) {
            return false;
        }

        HttpServletRequest request = servletRequestAttributes.getRequest();
        String uri = request.getRequestURI();
        if (!StringUtils.hasText(uri)) {
            return false;
        }

        String normalizedUri = uri.toLowerCase();
        return normalizedUri.contains("websearch") || normalizedUri.contains("web-search");
    }

    private Object resolveWebSearchBean() {
        if (applicationContext == null) {
            return null;
        }

        List<Object> candidates = new ArrayList<>();
        for (String beanName : applicationContext.getBeanDefinitionNames()) {
            Class<?> beanType = applicationContext.getType(beanName);
            String beanTypeName = beanType == null ? "" : beanType.getName().toLowerCase();
            if (!beanName.toLowerCase().contains("websearch") && !beanTypeName.contains("websearch")) {
                continue;
            }

            Object bean = applicationContext.getBean(beanName);
            if (bean == null || bean == this) {
                continue;
            }

            Class<?> beanClass = bean.getClass();
            String className = beanClass.getName().toLowerCase();
            if (!className.contains("linagent")
                    || !className.contains(".tools.")
                    || className.contains("requestbodyadvice")
                    || className.contains(".config.")) {
                continue;
            }

            if (hasInvokableSearchMethod(bean)) {
                candidates.add(bean);
            }
        }

        return candidates.stream()
                .sorted(Comparator.comparingInt(this::scoreBean).reversed())
                .findFirst()
                .orElse(null);
    }

    private boolean hasInvokableSearchMethod(Object bean) {
        List<Method> methods = new ArrayList<>();
        ReflectionUtils.doWithMethods(bean.getClass(), methods::add);
        return methods.stream().anyMatch(method -> scoreMethod(method) > 0);
    }

    private int scoreBean(Object bean) {
        String className = bean.getClass().getName().toLowerCase();
        int score = 0;
        if (className.contains(".tools.")) {
            score += 10;
        }
        if (className.contains("websearch")) {
            score += 10;
        }
        if (className.contains("tool")) {
            score += 6;
        }
        return score;
    }

    private String invokeWebSearch(Object webSearchBean, String query) {
        List<Method> methods = new ArrayList<>();
        ReflectionUtils.doWithMethods(webSearchBean.getClass(), methods::add);

        methods.sort(Comparator.comparingInt(this::scoreMethod).reversed());
        for (Method method : methods) {
            if (scoreMethod(method) <= 0) {
                continue;
            }

            try {
                ReflectionUtils.makeAccessible(method);
                Object result = ReflectionUtils.invokeMethod(method, webSearchBean, buildArguments(method, query));
                String text = flattenResult(result);
                if (StringUtils.hasText(text)) {
                    return text;
                }
            } catch (Exception ignored) {
                // 跳过不匹配的方法，继续尝试下一个候选方法。
            }
        }

        return null;
    }

    private int scoreMethod(Method method) {
        String methodName = method.getName().toLowerCase();
        int score = 0;

        if (methodName.contains("websearch")) {
            score += 10;
        }
        if (methodName.equals("search") || methodName.endsWith("search")) {
            score += 8;
        }
        if (methodName.contains("web") && methodName.contains("search")) {
            score += 6;
        }
        if (method.getParameterCount() >= 1) {
            score += 2;
        }

        return score;
    }

    private Object[] buildArguments(Method method, String query) {
        Class<?>[] parameterTypes = method.getParameterTypes();
        Object[] arguments = new Object[parameterTypes.length];

        for (int i = 0; i < parameterTypes.length; i++) {
            Class<?> parameterType = parameterTypes[i];
            if (parameterType == String.class) {
                arguments[i] = query;
                continue;
            }
            if (parameterType == int.class || parameterType == Integer.class) {
                arguments[i] = 5;
                continue;
            }
            if (parameterType == long.class || parameterType == Long.class) {
                arguments[i] = 5L;
                continue;
            }
            if (parameterType == boolean.class || parameterType == Boolean.class) {
                arguments[i] = Boolean.FALSE;
                continue;
            }
            if (List.class.isAssignableFrom(parameterType)) {
                arguments[i] = List.of();
                continue;
            }
            if (Map.class.isAssignableFrom(parameterType)) {
                arguments[i] = Map.of();
                continue;
            }
            if (parameterType.isEnum()) {
                Object[] constants = parameterType.getEnumConstants();
                arguments[i] = constants != null && constants.length > 0 ? constants[0] : null;
                continue;
            }

            arguments[i] = null;
        }

        return arguments;
    }

    private String flattenResult(Object result) {
        if (result == null) {
            return null;
        }

        if (result instanceof String text) {
            return text;
        }

        if (result instanceof Iterable<?> iterable) {
            StringBuilder builder = new StringBuilder();
            for (Object item : iterable) {
                if (item == null) {
                    continue;
                }
                if (builder.length() > 0) {
                    builder.append(System.lineSeparator()).append(System.lineSeparator());
                }
                builder.append(item);
            }
            return builder.toString();
        }

        return String.valueOf(result);
    }
}
