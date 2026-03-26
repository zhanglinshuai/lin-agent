package com.lin.linagent.tools;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.apache.commons.lang3.StringUtils;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 联网搜索工具
 */
@Slf4j
public class WebSearchTool {

    private static final String TAVILY_SEARCH_API_URL = "https://api.tavily.com/search";
    private static final String METASO_SEARCH_API_URL = "https://metaso.cn/api/v1/search";
    private static final String DUCKDUCKGO_HTML_SEARCH_URL = "https://html.duckduckgo.com/html/";
    private static final String DEFAULT_PROVIDER_ORDER = "tavily,duckduckgo,metaso";
    private static final String DEFAULT_USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/135.0 Safari/537.36";
    private static final int MAX_RESULT_SIZE = 8;
    private static final Gson GSON = new Gson();
    private static final MediaType JSON_MEDIA_TYPE = MediaType.get("application/json; charset=utf-8");
    private static final OkHttpClient HTTP_CLIENT = new OkHttpClient.Builder()
            .connectTimeout(Duration.ofSeconds(10))
            .readTimeout(Duration.ofSeconds(20))
            .writeTimeout(Duration.ofSeconds(20))
            .build();

    private final String metasoApiKey;
    private final String tavilyApiKey;
    private final List<String> providerOrder;

    public WebSearchTool(String metasoApiKey) {
        this(metasoApiKey, System.getenv("TAVILY_API_KEY"), DEFAULT_PROVIDER_ORDER);
    }

    public WebSearchTool(String metasoApiKey, String tavilyApiKey, String providerOrder) {
        this.metasoApiKey = StringUtils.trimToEmpty(metasoApiKey);
        this.tavilyApiKey = StringUtils.trimToEmpty(tavilyApiKey);
        this.providerOrder = parseProviderOrder(providerOrder);
    }

    @Tool(description = "联网搜索最新或外部信息。优先使用更高质量的搜索提供方，适合查询背景资料、推荐信息、地点攻略、实时动态、补充事实等场景。")
    public String searchWeb(@ToolParam(description = "Search query keyword") String query){
        if (StringUtils.isBlank(query)) {
            return "搜索失败：查询内容不能为空。";
        }
        String originalQuery = StringUtils.normalizeSpace(query);
        List<String> candidateQueries = buildCandidateQueries(originalQuery);
        List<String> attemptLogs = new ArrayList<>();
        for (String candidateQuery : candidateQueries) {
            for (String provider : providerOrder) {
                SearchResult searchResult = searchByProvider(provider, candidateQuery);
                attemptLogs.add(buildAttemptLog(searchResult));
                if (searchResult.hasResults()) {
                    log.info("联网搜索命中，provider={}, query={}", searchResult.getProvider(), candidateQuery);
                    return formatSearchResult(originalQuery, candidateQuery, searchResult);
                }
            }
        }
        log.warn("联网搜索未命中，originalQuery={}, attempts={}", originalQuery, attemptLogs);
        return buildNoResultMessage(originalQuery, candidateQueries, attemptLogs);
    }

    /**
     * 依次调用不同搜索提供方
     * @param provider 搜索提供方
     * @param query 查询词
     * @return 搜索结果
     */
    private SearchResult searchByProvider(String provider, String query) {
        return switch (StringUtils.trimToEmpty(provider).toLowerCase()) {
            case "tavily" -> searchByTavily(query);
            case "duckduckgo" -> searchByDuckDuckGo(query);
            case "metaso" -> searchByMetaso(query);
            default -> SearchResult.skip(provider, query, "未识别的搜索提供方");
        };
    }

    /**
     * Tavily 搜索
     * @param query 查询词
     * @return 搜索结果
     */
    private SearchResult searchByTavily(String query) {
        if (StringUtils.isBlank(tavilyApiKey)) {
            return SearchResult.skip("tavily", query, "未配置 Tavily API Key");
        }
        JsonObject requestJson = new JsonObject();
        requestJson.addProperty("query", query);
        requestJson.addProperty("topic", inferTavilyTopic(query));
        requestJson.addProperty("search_depth", "basic");
        requestJson.addProperty("include_answer", true);
        requestJson.addProperty("include_raw_content", false);
        requestJson.addProperty("max_results", MAX_RESULT_SIZE);
        RequestBody body = RequestBody.create(GSON.toJson(requestJson), JSON_MEDIA_TYPE);
        Request request = new Request.Builder()
                .url(TAVILY_SEARCH_API_URL)
                .header("Authorization", "Bearer " + tavilyApiKey)
                .header("Accept", "application/json")
                .post(body)
                .build();
        try (Response response = HTTP_CLIENT.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                return SearchResult.fail("tavily", query, "接口调用异常，状态码 " + response.code());
            }
            JsonObject jsonObject = JsonParser.parseString(response.body().string()).getAsJsonObject();
            JsonArray results = jsonObject.getAsJsonArray("results");
            List<SearchItem> items = new ArrayList<>();
            if (results != null) {
                for (int i = 0; i < results.size(); i++) {
                    JsonObject item = results.get(i).getAsJsonObject();
                    items.add(new SearchItem(
                            getJsonString(item, "title", "未命名结果"),
                            getJsonString(item, "url", ""),
                            getJsonString(item, "content", "")
                    ));
                }
            }
            return SearchResult.success("tavily", query, items, getJsonString(jsonObject, "answer", ""));
        } catch (Exception e) {
            return SearchResult.fail("tavily", query, e.getMessage());
        }
    }

    /**
     * DuckDuckGo HTML 搜索
     * @param query 查询词
     * @return 搜索结果
     */
    private SearchResult searchByDuckDuckGo(String query) {
        try {
            Connection connection = Jsoup.connect(DUCKDUCKGO_HTML_SEARCH_URL)
                    .userAgent(DEFAULT_USER_AGENT)
                    .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
                    .timeout(15000)
                    .data("q", query)
                    .method(Connection.Method.POST);
            Document document = connection.post();
            List<SearchItem> items = new ArrayList<>();
            for (Element resultElement : document.select(".result")) {
                Element titleLink = resultElement.selectFirst("a.result__a");
                if (titleLink == null) {
                    continue;
                }
                String title = titleLink.text();
                String url = decodeDuckDuckGoUrl(titleLink.attr("href"));
                Element snippetElement = resultElement.selectFirst(".result__snippet");
                String snippet = snippetElement == null ? "" : snippetElement.text();
                if (StringUtils.isAnyBlank(title, url)) {
                    continue;
                }
                items.add(new SearchItem(title, url, snippet));
                if (items.size() >= MAX_RESULT_SIZE) {
                    break;
                }
            }
            return SearchResult.success("duckduckgo", query, items, "");
        } catch (Exception e) {
            return SearchResult.fail("duckduckgo", query, e.getMessage());
        }
    }

    /**
     * Metaso 搜索，作为兜底提供方
     * @param query 查询词
     * @return 搜索结果
     */
    private SearchResult searchByMetaso(String query) {
        if (StringUtils.isBlank(metasoApiKey)) {
            return SearchResult.skip("metaso", query, "未配置 Metaso API Key");
        }
        JsonObject requestJson = new JsonObject();
        requestJson.addProperty("q", query);
        requestJson.addProperty("scope", "webpage");
        requestJson.addProperty("includeSummary", true);
        requestJson.addProperty("size", MAX_RESULT_SIZE);
        requestJson.addProperty("includeRawContent", false);
        requestJson.addProperty("conciseSnippet", false);
        RequestBody body = RequestBody.create(GSON.toJson(requestJson), JSON_MEDIA_TYPE);
        Request request = new Request.Builder()
                .url(METASO_SEARCH_API_URL)
                .header("Authorization", "Bearer " + metasoApiKey)
                .header("Accept", "application/json")
                .post(body)
                .build();
        try (Response response = HTTP_CLIENT.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                return SearchResult.fail("metaso", query, "接口调用异常，状态码 " + response.code());
            }
            JsonObject jsonObject = JsonParser.parseString(response.body().string()).getAsJsonObject();
            JsonArray webpages = jsonObject.getAsJsonArray("webpages");
            List<SearchItem> items = new ArrayList<>();
            if (webpages != null) {
                for (int i = 0; i < webpages.size(); i++) {
                    JsonObject item = webpages.get(i).getAsJsonObject();
                    items.add(new SearchItem(
                            getJsonString(item, "title", "未命名结果"),
                            getJsonString(item, "url", ""),
                            getJsonString(item, "summary", getJsonString(item, "snippet", ""))
                    ));
                }
            }
            return SearchResult.success("metaso", query, items, "");
        } catch (IOException e) {
            return SearchResult.fail("metaso", query, e.getMessage());
        }
    }

    /**
     * 构造候选查询词，提升召回率
     * @param query 原始查询词
     * @return 候选查询词列表
     */
    private List<String> buildCandidateQueries(String query) {
        Set<String> candidates = new LinkedHashSet<>();
        String normalized = StringUtils.normalizeSpace(query);
        candidates.add(normalized);
        String strippedQuoteQuery = normalized.replaceAll("[“”\"'‘’]+", "").trim();
        if (StringUtils.isNotBlank(strippedQuoteQuery)) {
            candidates.add(strippedQuoteQuery);
        }
        String strippedYearQuery = strippedQuoteQuery
                .replaceAll("(?<!\\d)(19|20)\\d{2}(?!\\d)", " ")
                .replaceAll("\\s{2,}", " ")
                .trim();
        if (StringUtils.isNotBlank(strippedYearQuery) && strippedYearQuery.length() >= 2) {
            candidates.add(strippedYearQuery);
        }
        String compactQuery = strippedYearQuery
                .replaceAll("[，。、《》：:（）()【】\\[\\]！？?；;]+", " ")
                .replaceAll("\\s{2,}", " ")
                .trim();
        if (StringUtils.isNotBlank(compactQuery) && compactQuery.length() >= 2) {
            candidates.add(compactQuery);
        }
        return new ArrayList<>(candidates);
    }

    /**
     * 解析搜索提供方顺序
     * @param providerOrder 提供方顺序字符串
     * @return 标准化结果
     */
    private List<String> parseProviderOrder(String providerOrder) {
        List<String> providers = new ArrayList<>();
        String rawProviderOrder = StringUtils.defaultIfBlank(providerOrder, DEFAULT_PROVIDER_ORDER);
        for (String provider : rawProviderOrder.split(",")) {
            String normalizedProvider = StringUtils.trimToEmpty(provider).toLowerCase();
            if (StringUtils.isBlank(normalizedProvider)) {
                continue;
            }
            if (!providers.contains(normalizedProvider)) {
                providers.add(normalizedProvider);
            }
        }
        if (providers.isEmpty()) {
            providers.add("tavily");
            providers.add("duckduckgo");
            providers.add("metaso");
        }
        return providers;
    }

    /**
     * 格式化搜索结果
     * @param originalQuery 原始查询
     * @param actualQuery 实际查询
     * @param searchResult 搜索结果
     * @return 可读文本
     */
    private String formatSearchResult(String originalQuery, String actualQuery, SearchResult searchResult) {
        StringBuilder sb = new StringBuilder();
        sb.append("搜索词：").append(originalQuery).append("\n");
        if (!StringUtils.equals(originalQuery, actualQuery)) {
            sb.append("实际查询：").append(actualQuery).append("\n");
        }
        sb.append("搜索提供方：").append(getProviderDisplayName(searchResult.getProvider())).append("\n");
        if (StringUtils.isNotBlank(searchResult.getSummary())) {
            sb.append("搜索摘要：").append(searchResult.getSummary()).append("\n");
        }
        List<SearchItem> items = searchResult.getItems();
        for (int i = 0; i < items.size(); i++) {
            SearchItem item = items.get(i);
            sb.append("\n结果 ").append(i + 1).append("：\n")
                    .append("标题：").append(item.getTitle()).append("\n")
                    .append("链接：").append(item.getUrl()).append("\n")
                    .append("摘要：").append(StringUtils.defaultIfBlank(item.getSnippet(), "暂无摘要")).append("\n");
        }
        return sb.toString().trim();
    }

    /**
     * 构造无结果提示
     * @param originalQuery 原始查询
     * @param candidateQueries 候选查询
     * @param attemptLogs 尝试记录
     * @return 文本结果
     */
    private String buildNoResultMessage(String originalQuery, List<String> candidateQueries, List<String> attemptLogs) {
        StringBuilder sb = new StringBuilder();
        sb.append("没有搜索到和“").append(originalQuery).append("”相关的结果。");
        if (candidateQueries.size() > 1) {
            sb.append("\n已尝试改写查询：").append(String.join("；", candidateQueries));
        }
        if (!attemptLogs.isEmpty()) {
            sb.append("\n已尝试搜索提供方：").append(String.join("；", attemptLogs));
        }
        return sb.toString();
    }

    /**
     * 生成单次尝试日志
     * @param searchResult 搜索结果
     * @return 日志文本
     */
    private String buildAttemptLog(SearchResult searchResult) {
        String providerName = getProviderDisplayName(searchResult.getProvider());
        if (searchResult.hasResults()) {
            return providerName + "（命中 " + searchResult.getItems().size() + " 条）";
        }
        if (searchResult.isSkipped()) {
            return providerName + "（跳过：" + searchResult.getMessage() + "）";
        }
        return providerName + "（无结果或失败：" + StringUtils.defaultIfBlank(searchResult.getMessage(), "未返回可用内容") + "）";
    }

    /**
     * 推断 Tavily 查询主题
     * @param query 查询词
     * @return 主题
     */
    private String inferTavilyTopic(String query) {
        if (StringUtils.containsAny(query, "最新", "今日", "今天", "本周", "新闻", "动态")) {
            return "news";
        }
        return "general";
    }

    /**
     * 解析 DuckDuckGo 重定向链接
     * @param rawUrl 原始链接
     * @return 真实链接
     */
    private String decodeDuckDuckGoUrl(String rawUrl) {
        if (StringUtils.isBlank(rawUrl)) {
            return "";
        }
        String normalizedUrl = rawUrl.startsWith("//") ? "https:" + rawUrl : rawUrl;
        HttpUrl httpUrl = HttpUrl.parse(normalizedUrl);
        if (httpUrl != null) {
            String uddg = httpUrl.queryParameter("uddg");
            if (StringUtils.isNotBlank(uddg)) {
                return URLDecoder.decode(uddg, StandardCharsets.UTF_8);
            }
        }
        return normalizedUrl;
    }

    /**
     * 读取 Json 字段
     * @param jsonObject Json 对象
     * @param fieldName 字段名
     * @param defaultValue 默认值
     * @return 字段值
     */
    private String getJsonString(JsonObject jsonObject, String fieldName, String defaultValue) {
        if (jsonObject == null || !jsonObject.has(fieldName) || jsonObject.get(fieldName).isJsonNull()) {
            return defaultValue;
        }
        try {
            return jsonObject.get(fieldName).getAsString();
        } catch (Exception e) {
            return defaultValue;
        }
    }

    /**
     * 获取搜索提供方显示名称
     * @param provider 提供方标识
     * @return 中文名称
     */
    private String getProviderDisplayName(String provider) {
        return switch (StringUtils.trimToEmpty(provider).toLowerCase()) {
            case "tavily" -> "Tavily";
            case "duckduckgo" -> "DuckDuckGo";
            case "metaso" -> "Metaso";
            default -> StringUtils.defaultIfBlank(provider, "未知搜索源");
        };
    }

    @Data
    private static class SearchResult {
        /**
         * 搜索提供方
         */
        private String provider;
        /**
         * 实际查询词
         */
        private String query;
        /**
         * 搜索结果
         */
        private List<SearchItem> items = new ArrayList<>();
        /**
         * 结果摘要
         */
        private String summary;
        /**
         * 错误或提示信息
         */
        private String message;
        /**
         * 是否跳过
         */
        private boolean skipped;

        public boolean hasResults() {
            return items != null && !items.isEmpty();
        }

        public static SearchResult success(String provider, String query, List<SearchItem> items, String summary) {
            SearchResult searchResult = new SearchResult();
            searchResult.setProvider(provider);
            searchResult.setQuery(query);
            searchResult.setItems(items == null ? new ArrayList<>() : items);
            searchResult.setSummary(StringUtils.defaultString(summary));
            return searchResult;
        }

        public static SearchResult fail(String provider, String query, String message) {
            SearchResult searchResult = new SearchResult();
            searchResult.setProvider(provider);
            searchResult.setQuery(query);
            searchResult.setMessage(StringUtils.defaultIfBlank(message, "请求失败"));
            return searchResult;
        }

        public static SearchResult skip(String provider, String query, String message) {
            SearchResult searchResult = fail(provider, query, message);
            searchResult.setSkipped(true);
            return searchResult;
        }
    }

    @Data
    private static class SearchItem {
        /**
         * 标题
         */
        private final String title;
        /**
         * 链接
         */
        private final String url;
        /**
         * 摘要
         */
        private final String snippet;
    }
}
