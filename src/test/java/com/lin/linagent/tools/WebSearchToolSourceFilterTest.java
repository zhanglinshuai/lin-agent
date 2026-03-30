package com.lin.linagent.tools;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class WebSearchToolSourceFilterTest {

    @Test
    void sanitizeSearchItemsShouldPrioritizeRelevanceBeforeDomainWeight() throws Exception {
        List<?> result = sanitizeSearchItems(
                "新乡情侣打卡地推荐",
                newSearchItem("新乡市人民政府官网", "https://www.xinxiang.gov.cn/index.html", "政务公开与便民服务信息", null, 1),
                newSearchItem("新乡情侣打卡地推荐", "https://guide.example.com/newxiang-date", "整理多个适合情侣约会打卡的地点和路线", null, 2),
                newSearchItem("新乡旅游攻略", "https://www.ctrip.com/newxiang-guide", "景点门票与游玩建议", null, 3)
        );

        assertEquals(3, result.size());
        assertEquals("新乡情侣打卡地推荐", getTitle(result.get(0)));
    }

    @Test
    void sanitizeSearchItemsShouldKeepUsefulSourcesAndRemoveNoise() throws Exception {
        List<?> result = sanitizeSearchItems(
                "周末新乡情侣打卡地推荐",
                newSearchItem("新乡官方推荐情侣打卡地", "https://www.xinxiang.gov.cn/visit", "官方整理多个适合周末情侣打卡的地点", null, 1),
                newSearchItem("新乡情侣打卡攻略", "https://www.meituan.com/newxiang/romantic", "推荐多个适合情侣周末打卡的地点", null, 2),
                newSearchItem("新乡周末约会拍照地点", "https://guide.example.com/newxiang-date", "适合情侣打卡和散步", null, 3),
                newSearchItem("周末散步拍照地分享", "https://blog.example.com/slow-walk", "新乡适合情侣打卡和轻松散步", null, 4),
                newSearchItem("情侣散步灵感合集", "https://life.example.com/romantic-walk", "适合周末出门拍照，新乡也可以这样安排", null, 5),
                newSearchItem("站内搜索", "https://noise.example.com/search-results-page?q=新乡", "search results", null, 6)
        );

        assertEquals(5, result.size());
        assertFalse(result.stream().map(this::getUrl).anyMatch(url -> url.contains("search-results-page")));
    }

    private List<?> sanitizeSearchItems(String query, Object... items) throws Exception {
        Method method = WebSearchTool.class.getDeclaredMethod("sanitizeSearchItems", String.class, List.class);
        method.setAccessible(true);
        List<Object> candidates = new ArrayList<>();
        for (Object item : items) {
            candidates.add(item);
        }
        return (List<?>) method.invoke(null, query, candidates);
    }

    private Object newSearchItem(String title, String url, String snippet, Long publishedAtEpochMillis, Integer originalOrder) throws Exception {
        Class<?> searchItemClass = Class.forName("com.lin.linagent.tools.WebSearchTool$SearchItem");
        Constructor<?> constructor = searchItemClass.getDeclaredConstructor(String.class, String.class, String.class, Long.class, Integer.class);
        constructor.setAccessible(true);
        return constructor.newInstance(title, url, snippet, publishedAtEpochMillis, originalOrder);
    }

    private String getTitle(Object item) {
        return invokeStringGetter(item, "getTitle");
    }

    private String getUrl(Object item) {
        return invokeStringGetter(item, "getUrl");
    }

    private String invokeStringGetter(Object item, String methodName) {
        try {
            Method method = item.getClass().getDeclaredMethod(methodName);
            method.setAccessible(true);
            Object value = method.invoke(item);
            return value == null ? "" : String.valueOf(value);
        } catch (Exception e) {
            throw new IllegalStateException("读取搜索结果字段失败: " + methodName, e);
        }
    }
}
