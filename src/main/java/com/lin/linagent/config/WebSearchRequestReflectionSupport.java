package com.lin.linagent.config;

import org.springframework.util.CollectionUtils;
import org.springframework.util.ReflectionUtils;
import org.springframework.util.StringUtils;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 通过反射提取并修改联网搜索请求，尽量减少对现有 DTO 结构的侵入。
 */
public final class WebSearchRequestReflectionSupport {

    private static final List<String> WEB_SEARCH_FLAG_NAMES = List.of(
            "enableWebSearch",
            "webSearchEnabled",
            "webSearch",
            "internetSearch",
            "networkSearch",
            "onlineSearch",
            "searchWeb",
            "useWebSearch",
            "enableNetworkSearch"
    );

    private static final List<String> QUERY_FIELD_NAMES = List.of(
            "message",
            "content",
            "prompt",
            "query",
            "question",
            "text",
            "input",
            "userInput"
    );

    private static final List<String> SEARCH_RESULT_FIELD_NAMES = List.of(
            "webSearchResult",
            "networkSearchResult",
            "searchResult",
            "searchContext",
            "extraContext",
            "referenceContent",
            "knowledgeContext",
            "ragContext",
            "context"
    );

    private static final List<String> MESSAGE_LIST_FIELD_NAMES = List.of(
            "messages",
            "history",
            "conversation",
            "chatMessages"
    );

    private WebSearchRequestReflectionSupport() {
    }

    public static boolean isWebSearchEnabled(Object body) {
        if (body == null) {
            return false;
        }

        for (String fieldName : WEB_SEARCH_FLAG_NAMES) {
            Object value = readProperty(body, fieldName);
            if (value instanceof Boolean flag && flag) {
                return true;
            }
            if (value instanceof String text && Boolean.parseBoolean(text)) {
                return true;
            }
        }

        return false;
    }

    public static void disableWebSearchFlags(Object body) {
        if (body == null) {
            return;
        }

        for (String fieldName : WEB_SEARCH_FLAG_NAMES) {
            writeProperty(body, fieldName, false);
        }
    }

    public static String extractUserQuery(Object body) {
        if (body == null) {
            return null;
        }

        for (String fieldName : QUERY_FIELD_NAMES) {
            Object value = readProperty(body, fieldName);
            if (value instanceof String text && StringUtils.hasText(text)) {
                return text;
            }
        }

        for (String fieldName : MESSAGE_LIST_FIELD_NAMES) {
            Object value = readProperty(body, fieldName);
            String text = extractQueryFromMessageContainer(value);
            if (StringUtils.hasText(text)) {
                return text;
            }
        }

        return null;
    }

    public static boolean injectSearchResult(Object body, String searchResult) {
        if (body == null || !StringUtils.hasText(searchResult)) {
            return false;
        }

        for (String fieldName : SEARCH_RESULT_FIELD_NAMES) {
            if (writeProperty(body, fieldName, searchResult)) {
                return true;
            }
        }

        for (String fieldName : MESSAGE_LIST_FIELD_NAMES) {
            Object value = readProperty(body, fieldName);
            if (injectIntoMessageContainer(value, searchResult)) {
                return true;
            }
        }

        for (String fieldName : QUERY_FIELD_NAMES) {
            Object value = readProperty(body, fieldName);
            if (value instanceof String text && StringUtils.hasText(text)) {
                String merged = buildMergedPrompt(searchResult, text);
                if (writeProperty(body, fieldName, merged)) {
                    return true;
                }
            }
        }

        return false;
    }

    private static String extractQueryFromMessageContainer(Object value) {
        if (value instanceof Collection<?> collection && !CollectionUtils.isEmpty(collection)) {
            List<?> items = new ArrayList<>(collection);
            for (int i = items.size() - 1; i >= 0; i--) {
                String text = extractMessageText(items.get(i));
                if (StringUtils.hasText(text)) {
                    return text;
                }
            }
        }
        return null;
    }

    private static boolean injectIntoMessageContainer(Object value, String searchResult) {
        if (!(value instanceof List<?> rawList) || rawList.isEmpty()) {
            return false;
        }

        @SuppressWarnings("unchecked")
        List<Object> messages = (List<Object>) rawList;

        for (int i = messages.size() - 1; i >= 0; i--) {
            Object message = messages.get(i);
            if (message instanceof Map<?, ?> rawMap) {
                @SuppressWarnings("unchecked")
                Map<Object, Object> messageMap = (Map<Object, Object>) rawMap;
                Object content = messageMap.get("content");
                if (content instanceof String text && StringUtils.hasText(text)) {
                    Map<Object, Object> copied = new LinkedHashMap<>(messageMap);
                    copied.put("content", buildMergedPrompt(searchResult, text));
                    messages.set(i, copied);
                    return true;
                }
            }

            Object content = readProperty(message, "content");
            if (content instanceof String text && StringUtils.hasText(text)) {
                return writeProperty(message, "content", buildMergedPrompt(searchResult, text));
            }
        }

        return false;
    }

    private static String extractMessageText(Object message) {
        if (message instanceof String text && StringUtils.hasText(text)) {
            return text;
        }

        if (message instanceof Map<?, ?> messageMap) {
            Object content = messageMap.get("content");
            if (content instanceof String text && StringUtils.hasText(text)) {
                return text;
            }
        }

        Object content = readProperty(message, "content");
        if (content instanceof String text && StringUtils.hasText(text)) {
            return text;
        }

        return null;
    }

    private static String buildMergedPrompt(String searchResult, String originalText) {
        return """
                【联网搜索结果】
                %s

                【用户原始问题】
                %s
                """.formatted(searchResult, originalText);
    }

    private static Object readProperty(Object target, String propertyName) {
        if (target == null || !StringUtils.hasText(propertyName)) {
            return null;
        }

        if (target instanceof Map<?, ?> rawMap) {
            return rawMap.get(propertyName);
        }

        Method getter = findGetter(target.getClass(), propertyName);
        if (getter != null) {
            ReflectionUtils.makeAccessible(getter);
            return ReflectionUtils.invokeMethod(getter, target);
        }

        Field field = findField(target.getClass(), propertyName);
        if (field != null) {
            ReflectionUtils.makeAccessible(field);
            return ReflectionUtils.getField(field, target);
        }

        return null;
    }

    private static boolean writeProperty(Object target, String propertyName, Object value) {
        if (target == null || !StringUtils.hasText(propertyName)) {
            return false;
        }

        if (target instanceof Map<?, ?> rawMap) {
            @SuppressWarnings("unchecked")
            Map<Object, Object> targetMap = (Map<Object, Object>) rawMap;
            targetMap.put(propertyName, value);
            return true;
        }

        Method setter = findSetter(target.getClass(), propertyName, value);
        if (setter != null) {
            ReflectionUtils.makeAccessible(setter);
            ReflectionUtils.invokeMethod(setter, target, adaptValue(value, setter.getParameterTypes()[0]));
            return true;
        }

        Field field = findField(target.getClass(), propertyName);
        if (field != null) {
            ReflectionUtils.makeAccessible(field);
            ReflectionUtils.setField(field, target, adaptValue(value, field.getType()));
            return true;
        }

        return false;
    }

    private static Method findGetter(Class<?> type, String propertyName) {
        String capitalized = capitalize(propertyName);
        Method getter = ReflectionUtils.findMethod(type, "get" + capitalized);
        if (getter != null) {
            return getter;
        }
        return ReflectionUtils.findMethod(type, "is" + capitalized);
    }

    private static Method findSetter(Class<?> type, String propertyName, Object value) {
        String methodName = "set" + capitalize(propertyName);
        Method[] methods = type.getMethods();
        for (Method method : methods) {
            if (!method.getName().equals(methodName) || method.getParameterCount() != 1) {
                continue;
            }

            Class<?> parameterType = method.getParameterTypes()[0];
            if (value == null || isTypeCompatible(parameterType, value.getClass())) {
                return method;
            }
        }
        return null;
    }

    private static Field findField(Class<?> type, String propertyName) {
        Class<?> current = type;
        while (current != null && current != Object.class) {
            Field field = ReflectionUtils.findField(current, propertyName);
            if (field != null) {
                return field;
            }
            current = current.getSuperclass();
        }
        return null;
    }

    private static Object adaptValue(Object value, Class<?> targetType) {
        if (value == null) {
            return null;
        }

        if (targetType.isInstance(value)) {
            return value;
        }

        if ((targetType == boolean.class || targetType == Boolean.class) && value instanceof String text) {
            return Boolean.parseBoolean(text);
        }

        if (targetType == String.class) {
            return String.valueOf(value);
        }

        return value;
    }

    private static boolean isTypeCompatible(Class<?> targetType, Class<?> valueType) {
        if (targetType.isAssignableFrom(valueType)) {
            return true;
        }

        if ((targetType == boolean.class || targetType == Boolean.class)
                && (valueType == Boolean.class || valueType == String.class)) {
            return true;
        }

        return targetType == String.class;
    }

    private static String capitalize(String text) {
        if (!StringUtils.hasText(text)) {
            return text;
        }
        return text.substring(0, 1).toUpperCase(Locale.ROOT) + text.substring(1);
    }
}
