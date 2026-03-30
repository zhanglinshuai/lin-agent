package com.lin.linagent.support;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lin.linagent.auth.AuthHelper;
import com.lin.linagent.auth.LoginUserInfo;
import com.lin.linagent.common.BaseResponse;
import com.lin.linagent.exception.BusinessException;
import com.lin.linagent.service.AdminLogService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.validation.BindingResult;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 操作日志辅助类
 */
@Slf4j
@Component
public class OperationLogHelper {

    public static final String REQUEST_START_TIME_ATTRIBUTE = "lin.agent.operation.startTime";

    private static final int MAX_STRING_LENGTH = 240;

    private static final int MAX_COLLECTION_SIZE = 10;

    private static final int MAX_MAP_SIZE = 20;

    private static final int MAX_DEPTH = 3;

    private static final String MASK_TEXT = "***";

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Resource
    private AdminLogService adminLogService;

    /**
     * 写操作日志
     * @param level 级别
     * @param category 分类
     * @param summary 摘要
     * @param detail 详情
     */
    public void write(String level, String category, String summary, String detail) {
        String normalizedLevel = StringUtils.defaultIfBlank(level, "INFO").toUpperCase();
        if ("ERROR".equals(normalizedLevel)) {
            adminLogService.error(category, summary, detail);
            return;
        }
        if ("WARN".equals(normalizedLevel)) {
            adminLogService.warn(category, summary, detail);
            return;
        }
        adminLogService.info(category, summary, detail);
    }

    /**
     * 解析接口日志分类
     * @param request 请求
     * @return 分类
     */
    public String resolveApiCategory(HttpServletRequest request) {
        String path = request == null ? "" : StringUtils.defaultString(request.getRequestURI());
        if (path.startsWith("/admin")) {
            return "api-admin";
        }
        if (path.startsWith("/user")) {
            return "api-user";
        }
        if (path.startsWith("/ai")) {
            return "api-ai";
        }
        if (path.startsWith("/chat_memory")) {
            return "api-chat-memory";
        }
        return "api";
    }

    /**
     * 是否应跳过接口日志
     * @param request 请求
     * @return 是否跳过
     */
    public boolean shouldSkipApiLog(HttpServletRequest request) {
        return request != null && "GET".equalsIgnoreCase(request.getMethod());
    }

    /**
     * 构建接口摘要
     * @param request 请求
     * @param actionText 动作文本
     * @param fallbackPath 兜底路径
     * @return 摘要
     */
    public String buildApiSummary(HttpServletRequest request, String actionText, String fallbackPath) {
        String method = request == null ? "UNKNOWN" : StringUtils.defaultIfBlank(request.getMethod(), "UNKNOWN");
        String path = request == null ? StringUtils.defaultIfBlank(fallbackPath, "unknown") : StringUtils.defaultIfBlank(request.getRequestURI(), StringUtils.defaultIfBlank(fallbackPath, "unknown"));
        return StringUtils.defaultIfBlank(actionText, "接口调用") + ": " + method + " " + path;
    }

    /**
     * 构建定时任务摘要
     * @param taskName 任务名
     * @param success 是否成功
     * @return 摘要
     */
    public String buildSchedulerSummary(String taskName, boolean success) {
        return (success ? "定时任务执行成功: " : "定时任务执行失败: ") + StringUtils.defaultIfBlank(taskName, "unknown");
    }

    /**
     * 提取参数
     * @param parameterNames 参数名
     * @param args 参数值
     * @return 参数摘要
     */
    public Map<String, Object> extractParameters(String[] parameterNames, Object[] args) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (args == null || args.length == 0) {
            return result;
        }
        for (int i = 0; i < args.length; i++) {
            Object arg = args[i];
            if (shouldIgnoreValue(arg)) {
                continue;
            }
            String key = parameterNames != null && i < parameterNames.length ? parameterNames[i] : "arg" + i;
            result.put(key, sanitizeValue(key, arg, 0));
        }
        return result;
    }

    /**
     * 构建接口详情
     * @param request 请求
     * @param handlerName 处理器名
     * @param parameters 参数
     * @param result 返回值
     * @param throwable 异常
     * @param durationMs 耗时
     * @return 详情
     */
    public String buildApiDetail(HttpServletRequest request,
                                 String handlerName,
                                 Map<String, Object> parameters,
                                 Object result,
                                 Throwable throwable,
                                 long durationMs) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("handler", StringUtils.defaultIfBlank(handlerName, "unknown"));
        if (request != null) {
            detail.put("httpMethod", request.getMethod());
            detail.put("path", request.getRequestURI());
            detail.put("queryString", truncate(StringUtils.defaultString(request.getQueryString())));
            detail.put("remoteAddr", request.getRemoteAddr());
            LoginUserInfo loginUserInfo = AuthHelper.getNullableLoginUser(request);
            if (loginUserInfo != null) {
                detail.put("operatorId", loginUserInfo.getId());
                detail.put("operatorName", loginUserInfo.getUserName());
                detail.put("operatorRole", loginUserInfo.getUserRole());
            }
        }
        if (parameters != null && !parameters.isEmpty()) {
            detail.put("parameters", parameters);
        }
        if (durationMs >= 0) {
            detail.put("durationMs", durationMs);
        }
        if (result != null) {
            detail.put("result", describeResult(result));
        }
        if (throwable != null) {
            detail.put("exceptionType", throwable.getClass().getSimpleName());
            detail.put("exceptionMessage", truncate(throwable.getMessage()));
        }
        return toJson(detail);
    }

    /**
     * 构建定时任务详情
     * @param taskName 任务名
     * @param parameters 参数
     * @param result 返回值
     * @param throwable 异常
     * @param durationMs 耗时
     * @return 详情
     */
    public String buildSchedulerDetail(String taskName,
                                       Map<String, Object> parameters,
                                       Object result,
                                       Throwable throwable,
                                       long durationMs) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("taskName", StringUtils.defaultIfBlank(taskName, "unknown"));
        if (parameters != null && !parameters.isEmpty()) {
            detail.put("parameters", parameters);
        }
        detail.put("durationMs", durationMs);
        if (result != null) {
            detail.put("result", describeResult(result));
        }
        if (throwable != null) {
            detail.put("exceptionType", throwable.getClass().getSimpleName());
            detail.put("exceptionMessage", truncate(throwable.getMessage()));
        }
        return toJson(detail);
    }

    /**
     * 判断响应是否成功
     * @param result 返回值
     * @return 是否成功
     */
    public boolean isSuccessResponse(Object result) {
        if (!(result instanceof BaseResponse<?> baseResponse)) {
            return true;
        }
        return baseResponse.getCode() == 0;
    }

    /**
     * 解析响应日志级别
     * @param result 返回值
     * @return 级别
     */
    public String resolveResponseLevel(Object result) {
        if (!(result instanceof BaseResponse<?> baseResponse)) {
            return "INFO";
        }
        if (baseResponse.getCode() == 0) {
            return "INFO";
        }
        return baseResponse.getCode() >= 50000 ? "ERROR" : "WARN";
    }

    /**
     * 解析异常日志级别
     * @param throwable 异常
     * @return 级别
     */
    public String resolveExceptionLevel(Throwable throwable) {
        if (throwable instanceof BusinessException businessException) {
            return businessException.getCode() >= 50000 ? "ERROR" : "WARN";
        }
        return "ERROR";
    }

    /**
     * 提取请求耗时
     * @param request 请求
     * @return 耗时
     */
    public long resolveDuration(HttpServletRequest request) {
        if (request == null) {
            return -1L;
        }
        Object startTime = request.getAttribute(REQUEST_START_TIME_ATTRIBUTE);
        if (startTime instanceof Long time) {
            return Math.max(0L, System.currentTimeMillis() - time);
        }
        return -1L;
    }

    /**
     * 判断是否应忽略
     * @param value 参数值
     * @return 是否忽略
     */
    private boolean shouldIgnoreValue(Object value) {
        return value == null
                || value instanceof HttpServletRequest
                || value instanceof jakarta.servlet.http.HttpServletResponse
                || value instanceof SseEmitter
                || value instanceof BindingResult;
    }

    /**
     * 脱敏并裁剪参数
     * @param key 字段名
     * @param value 值
     * @param depth 深度
     * @return 结果
     */
    private Object sanitizeValue(String key, Object value, int depth) {
        if (value == null) {
            return null;
        }
        if (isSensitiveKey(key)) {
            return MASK_TEXT;
        }
        if (depth >= MAX_DEPTH) {
            return truncate(String.valueOf(value));
        }
        if (value instanceof MultipartFile file) {
            Map<String, Object> fileSummary = new LinkedHashMap<>();
            fileSummary.put("fileName", truncate(file.getOriginalFilename()));
            fileSummary.put("size", file.getSize());
            fileSummary.put("contentType", truncate(file.getContentType()));
            return fileSummary;
        }
        if (value instanceof CharSequence) {
            return truncate(String.valueOf(value));
        }
        if (value instanceof Number || value instanceof Boolean || value instanceof Enum<?>) {
            return value;
        }
        if (value instanceof Throwable throwable) {
            return throwable.getClass().getSimpleName() + ": " + truncate(throwable.getMessage());
        }
        if (value instanceof Collection<?> collection) {
            List<Object> list = new ArrayList<>();
            int index = 0;
            for (Object item : collection) {
                if (index >= MAX_COLLECTION_SIZE) {
                    list.add("...(" + collection.size() + " items)");
                    break;
                }
                list.add(sanitizeValue(key, item, depth + 1));
                index++;
            }
            return list;
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            int index = 0;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (index >= MAX_MAP_SIZE) {
                    result.put("truncated", "...(" + map.size() + " entries)");
                    break;
                }
                String itemKey = String.valueOf(entry.getKey());
                result.put(itemKey, sanitizeValue(itemKey, entry.getValue(), depth + 1));
                index++;
            }
            return result;
        }
        if (value.getClass().isArray()) {
            int length = Array.getLength(value);
            List<Object> list = new ArrayList<>();
            for (int i = 0; i < Math.min(length, MAX_COLLECTION_SIZE); i++) {
                list.add(sanitizeValue(key, Array.get(value, i), depth + 1));
            }
            if (length > MAX_COLLECTION_SIZE) {
                list.add("...(" + length + " items)");
            }
            return list;
        }
        try {
            Map<String, Object> beanMap = objectMapper.convertValue(value, new TypeReference<Map<String, Object>>() {
            });
            if (beanMap != null && !beanMap.isEmpty()) {
                return sanitizeValue(key, beanMap, depth + 1);
            }
        } catch (IllegalArgumentException e) {
            log.debug("参数转日志摘要失败，使用字符串兜底，type={}", value.getClass().getName(), e);
        }
        return truncate(String.valueOf(value));
    }

    /**
     * 描述返回结果
     * @param result 返回值
     * @return 摘要
     */
    private Object describeResult(Object result) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("type", result.getClass().getSimpleName());
        if (result instanceof BaseResponse<?> baseResponse) {
            summary.put("code", baseResponse.getCode());
            summary.put("message", truncate(baseResponse.getMessage()));
            summary.put("dataType", baseResponse.getData() == null ? null : baseResponse.getData().getClass().getSimpleName());
            if (baseResponse.getData() instanceof Collection<?> collection) {
                summary.put("dataSize", collection.size());
            }
            return summary;
        }
        if (result instanceof ResponseEntity<?> responseEntity) {
            summary.put("status", responseEntity.getStatusCode().value());
            summary.put("bodyType", responseEntity.getBody() == null ? null : responseEntity.getBody().getClass().getSimpleName());
            if (responseEntity.getBody() instanceof byte[] bytes) {
                summary.put("bodyLength", bytes.length);
            }
            return summary;
        }
        if (result instanceof Collection<?> collection) {
            summary.put("size", collection.size());
            return summary;
        }
        if (result.getClass().isArray()) {
            summary.put("length", Array.getLength(result));
            return summary;
        }
        return summary;
    }

    /**
     * 是否敏感字段
     * @param key 字段名
     * @return 是否敏感
     */
    private boolean isSensitiveKey(String key) {
        String normalizedKey = StringUtils.trimToEmpty(key).toLowerCase();
        return normalizedKey.contains("password")
                || normalizedKey.contains("passwd")
                || normalizedKey.contains("secret")
                || normalizedKey.contains("token")
                || normalizedKey.contains("cookie")
                || normalizedKey.contains("authorization")
                || normalizedKey.contains("credential")
                || normalizedKey.contains("verificationcode");
    }

    /**
     * 截断文本
     * @param text 文本
     * @return 截断结果
     */
    private String truncate(String text) {
        String normalized = StringUtils.defaultString(text).replaceAll("\\s+", " ").trim();
        if (normalized.length() <= MAX_STRING_LENGTH) {
            return normalized;
        }
        return normalized.substring(0, MAX_STRING_LENGTH) + "...";
    }

    /**
     * 转为 JSON 文本
     * @param value 对象
     * @return JSON
     */
    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            log.warn("操作日志详情序列化失败", e);
            return String.valueOf(value);
        }
    }
}
