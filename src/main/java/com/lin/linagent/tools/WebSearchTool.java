package com.lin.linagent.tools;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
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
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 联网搜索工具
 */
@Slf4j
public class WebSearchTool {

    /**
     * 当前线程的搜索进度回调，用于把已命中的来源实时推给前端
     */
    private static final ThreadLocal<Consumer<String>> SEARCH_PROGRESS_CONSUMER = new ThreadLocal<>();
    /**
     * 当前线程的对话上下文，用于按本轮真实意图重写来源摘要
     */
    private static final ThreadLocal<String> SEARCH_REWRITE_CONTEXT = new ThreadLocal<>();

    private static final String TAVILY_SEARCH_API_URL = "https://api.tavily.com/search";
    private static final String METASO_SEARCH_API_URL = "https://metaso.cn/api/v1/search";
    private static final String DUCKDUCKGO_HTML_SEARCH_URL = "https://html.duckduckgo.com/html/";
    private static final String DEFAULT_PROVIDER_ORDER = "tavily,metaso";
    private static final String DEFAULT_USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/135.0 Safari/537.36";
    private static final int DEFAULT_QUERY_REWRITE_ROUNDS = 2;
    private static final int DEFAULT_QUERY_REWRITE_CANDIDATES_PER_ROUND = 2;
    private static final int MAX_QUERY_REWRITE_CANDIDATES_PER_ROUND = 4;
    private static final int MAX_RESULT_SIZE = 8;
    private static final int MIN_SOURCE_LIST_SIZE = 5;
    private static final int MAX_SNIPPET_LENGTH = 240;
    private static final int HARD_REJECT_TOTAL_SCORE = -220;
    private static final int MIN_PREFERRED_RELEVANCE_SCORE = 70;
    private static final int MIN_PREFERRED_TOTAL_SCORE = 20;
    private static final int MIN_DISPLAYABLE_TOTAL_SCORE = -60;
    private static final long PRIMARY_RECENT_WINDOW_DAYS = 400L;
    private static final long SECONDARY_RECENT_WINDOW_DAYS = 800L;
    private static final String SEARCH_META_MARKER_START = "[[SEARCH_META]]";
    private static final String SEARCH_META_MARKER_END = "[[/SEARCH_META]]";
    private static final String SEARCH_SOURCE_MARKER_START = "[[SEARCH_SOURCE]]";
    private static final String SEARCH_SOURCE_MARKER_END = "[[/SEARCH_SOURCE]]";
    private static final List<String> QUERY_STOP_WORDS = List.of(
            "我", "我们", "你", "你们", "请问", "想问", "帮我", "麻烦", "一下", "一下子", "推荐", "一些", "几个",
            "有没有", "适合", "可以", "怎么", "如何", "哪里", "哪儿", "最近", "现在", "周末"
    );
    private static final List<String> TRAVEL_INTENT_WORDS = List.of(
            "旅游", "旅行", "攻略", "景点", "游玩", "打卡", "住宿", "酒店", "美食", "门票", "自由行"
    );
    private static final List<String> ROUTE_INTENT_WORDS = List.of(
            "路线", "交通", "高铁", "动车", "火车", "机票", "航班", "出发", "抵达", "到达", "怎么去"
    );
    private static final List<String> TRUSTED_TRAVEL_DOMAINS = List.of(
            "ctrip.com", "trip.com", "qunar.com", "mafengwo.cn", "ly.com", "fliggy.com", "meituan.com", "dianping.com"
    );
    private static final List<String> TRUSTED_NEWS_DOMAINS = List.of(
            "news.qq.com", "xinhuanet.com", "people.com.cn", "cctv.com", "thepaper.cn", "ifeng.com", "sina.com.cn", "sohu.com", "163.com"
    );
    private static final List<String> LOW_QUALITY_DOMAIN_HINTS = List.of(
            "instagram.com", "vimeo.com", "fanyi.taobao.com", "search-results-page", "dict_pangu", "download=1"
    );
    private static final Pattern ORIGIN_DESTINATION_PATTERN = Pattern.compile("从([\\p{IsHan}A-Za-z0-9]{2,12})到([\\p{IsHan}A-Za-z0-9]{2,12})");
    private static final Pattern GO_DESTINATION_PATTERN = Pattern.compile("(?:去|到|前往|飞往|抵达)([\\p{IsHan}A-Za-z0-9]{2,12})");
    private static final Pattern DEPARTURE_PATTERN = Pattern.compile("([\\p{IsHan}A-Za-z0-9]{2,12})出发");
    private static final Pattern DATE_PATTERN = Pattern.compile("((?:19|20)\\d{2})[-/.年](\\d{1,2})[-/.月](\\d{1,2})");
    private static final Pattern YEAR_MONTH_PATTERN = Pattern.compile("((?:19|20)\\d{2})[-/.年](\\d{1,2})(?:月)?");
    private static final Pattern YEAR_PATTERN = Pattern.compile("(?<!\\d)((?:19|20)\\d{2})(?!\\d)");
    private static final Pattern SEARCH_STATUS_CODE_PATTERN = Pattern.compile("状态码\\s*(\\d{3})");
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
    private final int queryRewriteRounds;
    private final int queryRewriteCandidatesPerRound;
    private final Supplier<ChatModel> chatModelSupplier;

    public WebSearchTool(String metasoApiKey) {
        this(
                metasoApiKey,
                System.getenv("TAVILY_API_KEY"),
                DEFAULT_PROVIDER_ORDER,
                DEFAULT_QUERY_REWRITE_ROUNDS,
                DEFAULT_QUERY_REWRITE_CANDIDATES_PER_ROUND,
                null
        );
    }

    public WebSearchTool(String metasoApiKey, String tavilyApiKey, String providerOrder) {
        this(
                metasoApiKey,
                tavilyApiKey,
                providerOrder,
                DEFAULT_QUERY_REWRITE_ROUNDS,
                DEFAULT_QUERY_REWRITE_CANDIDATES_PER_ROUND,
                null
        );
    }

    public WebSearchTool(String metasoApiKey, String tavilyApiKey, String providerOrder, Supplier<ChatModel> chatModelSupplier) {
        this(
                metasoApiKey,
                tavilyApiKey,
                providerOrder,
                DEFAULT_QUERY_REWRITE_ROUNDS,
                DEFAULT_QUERY_REWRITE_CANDIDATES_PER_ROUND,
                chatModelSupplier
        );
    }

    public WebSearchTool(
            String metasoApiKey,
            String tavilyApiKey,
            String providerOrder,
            int queryRewriteRounds,
            int queryRewriteCandidatesPerRound,
            Supplier<ChatModel> chatModelSupplier
    ) {
        this.metasoApiKey = StringUtils.trimToEmpty(metasoApiKey);
        this.tavilyApiKey = StringUtils.trimToEmpty(tavilyApiKey);
        this.providerOrder = parseProviderOrder(providerOrder);
        this.queryRewriteRounds = Math.max(0, queryRewriteRounds);
        this.queryRewriteCandidatesPerRound = Math.max(
                0,
                Math.min(queryRewriteCandidatesPerRound, MAX_QUERY_REWRITE_CANDIDATES_PER_ROUND)
        );
        this.chatModelSupplier = chatModelSupplier;
    }

    @Tool(description = "联网搜索最新或外部信息。优先使用更高质量的搜索提供方，适合查询背景资料、推荐信息、地点攻略、实时动态、补充事实等场景。")
    public String searchWeb(@ToolParam(description = "Search query keyword") String query){
        if (StringUtils.isBlank(query)) {
            return "搜索失败：查询内容不能为空。";
        }
        String originalQuery = StringUtils.trimToEmpty(query);
        List<SearchAttempt> attemptLogs = new ArrayList<>();
        LinkedHashSet<String> triedQueries = new LinkedHashSet<>();
        List<String> initialQueries = buildInitialCandidateQueries(originalQuery);
        log.info("联网搜索初始候选，originalQuery={}, candidates={}", originalQuery, initialQueries);
        for (String initialQuery : initialQueries) {
            if (!triedQueries.add(initialQuery)) {
                continue;
            }
            SearchOutcome initialOutcome = searchAcrossProviders(originalQuery, initialQuery, attemptLogs);
            if (initialOutcome != null) {
                String renderedResult = initialOutcome.getRenderedResult();
                log.info(
                        "联网搜索最终回复结果，originalQuery={}, actualQuery={}, provider={}, result={}",
                        originalQuery,
                        initialOutcome.getActualQuery(),
                        initialOutcome.getSearchResult() == null ? "" : initialOutcome.getSearchResult().getProvider(),
                        renderedResult
                );
                return renderedResult;
            }
        }

        for (int round = 1; round <= queryRewriteRounds; round++) {
            List<String> rewrittenQueries = rewriteSearchQueries(originalQuery, triedQueries, round);
            if (rewrittenQueries.isEmpty()) {
                log.info("联网搜索第{}轮改写未生成新候选，停止继续遍历，originalQuery={}", round, originalQuery);
                break;
            }
            boolean hasNewQuery = false;
            for (String rewrittenQuery : rewrittenQueries) {
                if (!triedQueries.add(rewrittenQuery)) {
                    continue;
                }
                hasNewQuery = true;
                SearchOutcome rewrittenOutcome = searchAcrossProviders(originalQuery, rewrittenQuery, attemptLogs);
                if (rewrittenOutcome != null) {
                    String renderedResult = rewrittenOutcome.getRenderedResult();
                    log.info(
                            "联网搜索最终回复结果，originalQuery={}, actualQuery={}, provider={}, result={}",
                            originalQuery,
                            rewrittenOutcome.getActualQuery(),
                            rewrittenOutcome.getSearchResult() == null ? "" : rewrittenOutcome.getSearchResult().getProvider(),
                            renderedResult
                    );
                    return renderedResult;
                }
            }
            if (!hasNewQuery) {
                log.info("联网搜索第{}轮改写全部与已尝试搜索词重复，停止继续遍历，originalQuery={}", round, originalQuery);
                break;
            }
        }

        log.warn(
                "联网搜索未命中，originalQuery={}, triedQueries={}, attempts={}",
                originalQuery,
                triedQueries,
                attemptLogs.stream().map(this::buildAttemptLog).toList()
        );
        String renderedResult = buildNoResultMessage(originalQuery, attemptLogs);
        log.info("联网搜索最终回复结果，originalQuery={}, actualQuery={}, provider={}, result={}", originalQuery, "", "", renderedResult);
        return renderedResult;
    }

    /**
     * 构造首轮搜索候选，优先尝试更适合检索的精炼搜索词
     * @param originalQuery 用户原始问题
     * @return 首轮候选列表
     */
    private List<String> buildInitialCandidateQueries(String originalQuery) {
        LinkedHashSet<String> candidates = new LinkedHashSet<>();
        String preferredQuery = rewritePrimarySearchQuery(originalQuery);
        collectRewrittenQuery(preferredQuery, candidates);
        collectRewrittenQuery(buildGoalFocusedSearchQuery(originalQuery), candidates);
        collectRewrittenQuery(buildHeuristicSearchQuery(originalQuery), candidates);
        collectRewrittenQuery(originalQuery, candidates);
        return new ArrayList<>(candidates);
    }

    /**
     * 生成首轮优先尝试的独立搜索词
     * @param originalQuery 用户原始问题
     * @return 改写后的独立搜索词
     */
    private String rewritePrimarySearchQuery(String originalQuery) {
        ChatModel chatModel = chatModelSupplier == null ? null : chatModelSupplier.get();
        if (chatModel == null) {
            return "";
        }
        String rewriteContext = StringUtils.defaultIfBlank(SEARCH_REWRITE_CONTEXT.get(), originalQuery);
        String prompt = """
                你是联网搜索词改写助手。
                请根据当前问题，生成 1 条最适合拿去搜索引擎检索的独立搜索词。
                
                要求：
                1. 保留用户真正想查的主题、对象、地点、时间范围、筛选条件和任务目标。
                2. 删除寒暄、口语、铺垫和无关修饰，让搜索词更像关键词组合。
                3. 如果原问题已经适合检索，就返回它的精炼版，不要发散，不要补充用户没提到的新事实。
                4. 不要解释，不要列表，不要 JSON，不要引号，只返回 1 条搜索词。
                
                当前对话真正关心的问题：%s
                用户原始问题：%s
                """.formatted(rewriteContext, originalQuery);
        try {
            String response = ChatClient.builder(chatModel)
                    .build()
                    .prompt()
                    .user(prompt)
                    .call()
                    .content();
            return normalizeSearchQuery(cleanJson(response));
        } catch (Exception e) {
            log.warn("联网搜索首轮独立搜索词改写失败，回退启发式候选，originalQuery={}, error={}", originalQuery, e.getMessage());
            return "";
        }
    }

    /**
     * 当模型改写不可用时，用启发式规则压缩自然语言搜索词
     * @param originalQuery 用户原始问题
     * @return 更适合检索的关键词组合
     */
    private String buildHeuristicSearchQuery(String originalQuery) {
        String normalized = StringUtils.normalizeSpace(StringUtils.defaultString(originalQuery))
                .replace('，', ' ')
                .replace('。', ' ')
                .replace('？', ' ')
                .replace('！', ' ')
                .replace(',', ' ')
                .replace('.', ' ')
                .replace('?', ' ')
                .replace('!', ' ');
        if (StringUtils.isBlank(normalized)) {
            return "";
        }
        String simplified = normalized
                .replaceAll("(我想问一下|我想问|请问一下|请问|麻烦你|麻烦帮我|帮我看看|帮我|给我|我想|想让你|能不能|可以帮我|推荐几个|推荐一些|推荐一下|推荐下|有没有|想知道)", " ")
                .replaceAll("(适合|适不适合|适不适合去|带女朋友|带对象|一起去|去哪里|去哪儿|去玩|周末|最近|现在|一下|一下子)", " ")
                .replaceAll("\\s{2,}", " ")
                .trim();
        if (StringUtils.isBlank(simplified)) {
            simplified = normalized;
        }
        String goalFocusedQuery = buildGoalFocusedSearchQuery(simplified);
        if (StringUtils.isNotBlank(goalFocusedQuery)) {
            return goalFocusedQuery;
        }
        List<String> intentTokens = new ArrayList<>();
        for (String candidateToken : List.of(
                "最新", "新闻", "动态", "攻略", "推荐", "景点", "打卡", "打卡地", "约会", "情侣",
                "美食", "住宿", "酒店", "路线", "时间", "门票", "价格", "政策", "官网", "教程",
                "下载", "评测", "配置", "对比", "报名", "地址", "营业时间", "开放时间"
        )) {
            if (StringUtils.contains(simplified, candidateToken)) {
                intentTokens.add(candidateToken);
            }
        }
        String locationToken = extractLocationToken(simplified);
        LinkedHashSet<String> keywordParts = new LinkedHashSet<>();
        if (StringUtils.isNotBlank(locationToken)) {
            keywordParts.add(locationToken);
        }
        keywordParts.addAll(intentTokens);
        if (keywordParts.size() >= 2) {
            return String.join(" ", keywordParts);
        }
        return normalizeSearchQuery(simplified);
    }

    /**
     * 基于查询主体和任务目标生成更聚焦的搜索词
     * @param originalQuery 用户原始问题
     * @return 搜索词
     */
    private String buildGoalFocusedSearchQuery(String originalQuery) {
        QueryProfile queryProfile = buildQueryProfile(originalQuery);
        if (queryProfile.getCoreTerms().isEmpty()) {
            return "";
        }
        LinkedHashSet<String> parts = new LinkedHashSet<>();
        if (StringUtils.isNotBlank(queryProfile.getDestination())) {
            parts.add(queryProfile.getDestination());
        }
        if (queryProfile.isTravelIntent()) {
            parts.add("旅游攻略");
        }
        if (queryProfile.isRouteIntent()) {
            parts.add("路线");
            parts.add("交通");
        }
        for (String term : queryProfile.getCoreTerms()) {
            if (parts.size() >= 4) {
                break;
            }
            if (StringUtils.isBlank(term)) {
                continue;
            }
            if (StringUtils.equals(term, queryProfile.getDestination())) {
                continue;
            }
            parts.add(term);
        }
        if (StringUtils.isNotBlank(queryProfile.getOrigin()) && !StringUtils.equals(queryProfile.getOrigin(), queryProfile.getDestination())) {
            parts.add(queryProfile.getOrigin() + "出发");
        }
        if (parts.isEmpty()) {
            return "";
        }
        return normalizeSearchQuery(String.join(" ", parts));
    }

    /**
     * 提取中文场景下常见的地点或对象词
     * @param text 文本
     * @return 地点或对象
     */
    private static String extractLocationToken(String text) {
        Matcher matcher = Pattern.compile("(?:去|在|到|来|逛|玩|搜|查)([\\p{IsHan}A-Za-z0-9]{2,12}?)(?:约会|旅游|玩|吃|打卡|景点|攻略|推荐|附近|哪里|哪儿|怎么样)").matcher(StringUtils.defaultString(text));
        if (matcher.find()) {
            return matcher.group(1);
        }
        matcher = Pattern.compile("([\\p{IsHan}A-Za-z0-9]{2,12})(?:景点|攻略|美食|约会|情侣|打卡)").matcher(StringUtils.defaultString(text));
        if (matcher.find()) {
            return matcher.group(1);
        }
        return "";
    }

    /**
     * 按提供方顺序搜索指定查询词
     * @param originalQuery 原始查询词
     * @param actualQuery 当前尝试的查询词
     * @param attemptLogs 尝试日志
     * @return 命中结果；若未命中则返回 null
     */
    private SearchOutcome searchAcrossProviders(String originalQuery, String actualQuery, List<SearchAttempt> attemptLogs) {
        for (String provider : providerOrder) {
            SearchResult searchResult = searchByProvider(provider, actualQuery);
            attemptLogs.add(buildSearchAttempt(actualQuery, searchResult));
            if (!searchResult.hasResults()) {
                continue;
            }
            SearchResult rewrittenSearchResult = rewriteSearchResult(originalQuery, actualQuery, searchResult);
            log.info(
                    "联网搜索命中，provider={}, originalQuery={}, actualQuery={}",
                    searchResult.getProvider(),
                    originalQuery,
                    actualQuery
            );
            emitProgressiveSearchResults(originalQuery, actualQuery, rewrittenSearchResult);
            return new SearchOutcome(
                    actualQuery,
                    rewrittenSearchResult,
                    formatSearchResult(originalQuery, actualQuery, rewrittenSearchResult)
            );
        }
        return null;
    }

    /**
     * 生成下一轮待遍历的搜索词候选
     * @param originalQuery 原始搜索词
     * @param triedQueries 已尝试搜索词
     * @param round 第几轮改写
     * @return 新候选列表
     */
    private List<String> rewriteSearchQueries(String originalQuery, LinkedHashSet<String> triedQueries, int round) {
        if (queryRewriteRounds <= 0 || queryRewriteCandidatesPerRound <= 0) {
            return List.of();
        }
        ChatModel chatModel = chatModelSupplier == null ? null : chatModelSupplier.get();
        if (chatModel == null) {
            return List.of();
        }
        String rewriteContext = StringUtils.defaultIfBlank(SEARCH_REWRITE_CONTEXT.get(), originalQuery);
        String prompt = """
                你是联网搜索词改写助手。
                你的任务是为同一个搜索目标生成新的搜索词，用于下一轮搜索引擎遍历。
                
                改写要求：
                1. 所有候选都必须与当前问题保持同一个检索意图，不能跑题，不能编造新事实。
                2. 保留实体、地点、时间、筛选条件和任务目标，删除口语、寒暄和无效铺垫。
                3. 候选之间要有明显差异，可以从“更短关键词版、对象前置版、任务前置版、同义改写版、保留地点时间限定版”这些方向变化。
                4. 不要输出与已尝试搜索词重复或近似重复的候选。
                5. 每条尽量精炼，优先提高搜索引擎命中率，不要写成长段自然语言。
                6. 只输出 JSON 数组，不要解释，不要 Markdown，不要代码块。
                
                输出示例：
                ["候选词1","候选词2"]
                
                当前对话真正关心的问题：%s
                原始搜索词：%s
                已尝试搜索词：%s
                当前是第 %d 轮改写，请输出最多 %d 条新的候选搜索词。
                """.formatted(rewriteContext, originalQuery, triedQueries, round, queryRewriteCandidatesPerRound);
        try {
            String response = ChatClient.builder(chatModel)
                    .build()
                    .prompt()
                    .user(prompt)
                    .call()
                    .content();
            List<String> candidates = parseRewrittenQueries(response);
            log.info("联网搜索第{}轮改写候选，originalQuery={}, candidates={}", round, originalQuery, candidates);
            return candidates;
        } catch (Exception e) {
            log.warn("联网搜索第{}轮改写失败，跳过本轮，originalQuery={}, error={}", round, originalQuery, e.getMessage());
            return List.of();
        }
    }

    /**
     * 注册当前线程的搜索进度回调
     * @param consumer 进度回调
     */
    public static void setProgressConsumer(Consumer<String> consumer) {
        if (consumer == null) {
            SEARCH_PROGRESS_CONSUMER.remove();
            return;
        }
        SEARCH_PROGRESS_CONSUMER.set(consumer);
    }

    /**
     * 清理当前线程的搜索进度回调
     */
    public static void clearProgressConsumer() {
        SEARCH_PROGRESS_CONSUMER.remove();
    }

    /**
     * 注册当前线程的摘要重写上下文
     * @param context 当前对话上下文
     */
    public static void setRewriteContext(String context) {
        if (StringUtils.isBlank(context)) {
            SEARCH_REWRITE_CONTEXT.remove();
            return;
        }
        SEARCH_REWRITE_CONTEXT.set(context.trim());
    }

    /**
     * 清理当前线程的摘要重写上下文
     */
    public static void clearRewriteContext() {
        SEARCH_REWRITE_CONTEXT.remove();
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
        requestJson.addProperty("search_depth", "advanced");
        requestJson.addProperty("include_answer", true);
        requestJson.addProperty("include_raw_content", false);
        requestJson.addProperty("auto_parameters", true);
        requestJson.addProperty("max_results", MAX_RESULT_SIZE);
        requestJson.addProperty("time_range", "year");
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
                            normalizeSnippet(getJsonString(item, "content", "")),
                            extractPublishedAt(item, "published_date", "publishedDate", "date", "last_updated"),
                            i
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
                    .timeout(7000)
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
                String snippet = snippetElement == null ? "" : normalizeSnippet(snippetElement.text());
                if (StringUtils.isAnyBlank(title, url)) {
                    continue;
                }
                items.add(new SearchItem(title, url, snippet, inferPublishedAt(title, snippet, url), items.size()));
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
            int businessCode = getJsonInt(jsonObject, "code", 0);
            String businessMessage = getJsonString(jsonObject, "message", "");
            JsonArray webpages = jsonObject.getAsJsonArray("webpages");
            if ((businessCode != 0 && businessCode != 200) && StringUtils.isNotBlank(businessMessage)) {
                return SearchResult.fail("metaso", query, "接口返回异常：" + businessMessage);
            }
            List<SearchItem> items = new ArrayList<>();
            if (webpages != null) {
                for (int i = 0; i < webpages.size(); i++) {
                    JsonObject item = webpages.get(i).getAsJsonObject();
                    items.add(new SearchItem(
                            getJsonString(item, "title", "未命名结果"),
                            getJsonString(item, "url", ""),
                            normalizeSnippet(getJsonString(item, "summary", getJsonString(item, "snippet", ""))),
                            extractPublishedAt(item, "published_time", "publishedTime", "publishTime", "date", "updated_at"),
                            i
                    ));
                }
            }
            if (items.isEmpty() && StringUtils.isNotBlank(businessMessage) && StringUtils.contains(businessMessage, "错误")) {
                return SearchResult.fail("metaso", query, "接口返回异常：" + businessMessage);
            }
            return SearchResult.success("metaso", query, items, "");
        } catch (IOException e) {
            return SearchResult.fail("metaso", query, e.getMessage());
        }
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
        sb.append(buildSearchMetaMarker(originalQuery, actualQuery, searchResult)).append("\n");
        List<SearchItem> items = searchResult.getItems();
        for (int i = 0; i < items.size(); i++) {
            SearchItem item = items.get(i);
            sb.append("\n结果 ").append(i + 1).append("：\n")
                    .append("标题：").append(item.getTitle()).append("\n")
                    .append("链接：").append(item.getUrl()).append("\n")
                    .append("摘要：").append(StringUtils.defaultIfBlank(item.getSnippet(), "暂无摘要")).append("\n")
                    .append(buildSearchSourceMarker(item, i + 1)).append("\n");
        }
        return sb.toString().trim();
    }

    /**
     * 将搜索结果按来源逐条推送，避免前端等到整段文本拼完才渲染
     * @param originalQuery 原始查询
     * @param actualQuery 实际查询
     * @param searchResult 搜索结果
     */
    private void emitProgressiveSearchResults(String originalQuery, String actualQuery, SearchResult searchResult) {
        Consumer<String> consumer = SEARCH_PROGRESS_CONSUMER.get();
        if (consumer == null || searchResult == null || !searchResult.hasResults()) {
            return;
        }
        String header = buildProgressHeader(originalQuery, actualQuery, searchResult);
        if (StringUtils.isNotBlank(header)) {
            consumer.accept(header + "\n\n");
        }
        List<SearchItem> items = searchResult.getItems();
        for (int i = 0; i < items.size(); i++) {
            String itemBlock = buildProgressItem(items.get(i), i + 1);
            if (StringUtils.isNotBlank(itemBlock)) {
                consumer.accept(itemBlock + "\n\n");
            }
        }
    }

    /**
     * 构造实时搜索头信息
     * @param originalQuery 原始查询
     * @param actualQuery 实际查询
     * @param searchResult 搜索结果
     * @return 展示文本
     */
    private String buildProgressHeader(String originalQuery, String actualQuery, SearchResult searchResult) {
        StringBuilder sb = new StringBuilder();
        sb.append("搜索词：").append(originalQuery).append("\n");
        if (!StringUtils.equals(originalQuery, actualQuery)) {
            sb.append("实际查询：").append(actualQuery).append("\n");
        }
        sb.append("搜索提供方：").append(getProviderDisplayName(searchResult.getProvider())).append("\n")
                .append(buildSearchMetaMarker(originalQuery, actualQuery, searchResult));
        return sb.toString().trim();
    }

    /**
     * 构造单条来源的实时展示文本
     * @param item 搜索项
     * @param index 序号
     * @return 展示文本
     */
    private String buildProgressItem(SearchItem item, int index) {
        if (item == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("结果 ").append(index).append("：\n")
                .append("标题：").append(StringUtils.defaultIfBlank(item.getTitle(), "未命名结果")).append("\n")
                .append("链接：").append(StringUtils.defaultString(item.getUrl())).append("\n")
                .append("摘要：").append(StringUtils.defaultIfBlank(item.getSnippet(), "暂无摘要")).append("\n")
                .append(buildSearchSourceMarker(item, index));
        return sb.toString().trim();
    }

    /**
     * 构造无结果提示
     * @param originalQuery 原始查询
     * @param attemptLogs 尝试记录
     * @return 文本结果
     */
    private String buildNoResultMessage(String originalQuery, List<SearchAttempt> attemptLogs) {
        if (attemptLogs == null || attemptLogs.isEmpty()) {
            return "这次联网搜索暂时没有拿到可用结果，你可以稍后重试。";
        }
        long failedCount = attemptLogs.stream().filter(SearchAttempt::isFailed).count();
        long emptyResultCount = attemptLogs.stream().filter(SearchAttempt::isEmptyResult).count();
        long skippedCount = attemptLogs.stream().filter(SearchAttempt::isSkipped).count();
        LinkedHashSet<String> triedQueries = new LinkedHashSet<>();
        for (SearchAttempt attempt : attemptLogs) {
            if (StringUtils.isNotBlank(attempt.getQuery())) {
                triedQueries.add(attempt.getQuery());
            }
        }
        boolean showQuery = triedQueries.size() > 1;
        StringBuilder sb = new StringBuilder();
        sb.append(buildNoResultHeadline(originalQuery, failedCount, emptyResultCount, skippedCount, attemptLogs.size()));
        if (showQuery) {
            sb.append("\n本次已自动尝试 ").append(triedQueries.size()).append(" 组搜索词，但都没有拿到稳定结果。");
        }
        sb.append("\n\n已尝试的搜索源：");
        for (SearchAttempt attempt : attemptLogs) {
            sb.append("\n- ").append(buildAttemptUserLine(attempt, showQuery));
        }
        sb.append("\n\n建议稍后重试，或把问题改成更短、更明确的关键词后再试一次。");
        return sb.toString();
    }

    /**
     * 生成单次尝试信息
     * @param searchResult 搜索结果
     * @return 尝试信息
     */
    private SearchAttempt buildSearchAttempt(String actualQuery, SearchResult searchResult) {
        if (searchResult == null) {
            return new SearchAttempt(
                    "未知搜索源",
                    StringUtils.defaultString(actualQuery),
                    "搜索服务没有返回可解析的结果",
                    "未返回搜索结果对象",
                    false,
                    true,
                    false
            );
        }
        String providerName = getProviderDisplayName(searchResult.getProvider());
        String queryLabel = StringUtils.defaultIfBlank(searchResult.getQuery(), actualQuery);
        if (searchResult.hasResults()) {
            return new SearchAttempt(
                    providerName,
                    queryLabel,
                    "命中 " + searchResult.getItems().size() + " 条",
                    "",
                    false,
                    false,
                    false
            );
        }
        if (searchResult.isSkipped()) {
            return new SearchAttempt(
                    providerName,
                    queryLabel,
                    translateSkipMessage(searchResult.getMessage()),
                    StringUtils.defaultString(searchResult.getMessage()),
                    true,
                    false,
                    false
            );
        }
        String technicalMessage = StringUtils.defaultString(searchResult.getMessage());
        if (StringUtils.isBlank(technicalMessage)) {
            technicalMessage = "未返回可用内容";
            return new SearchAttempt(
                    providerName,
                    queryLabel,
                    "服务返回为空，暂时没有可展示的结果",
                    technicalMessage,
                    false,
                    false,
                    true
            );
        }
        return new SearchAttempt(
                providerName,
                queryLabel,
                translateFailureMessage(technicalMessage),
                technicalMessage,
                false,
                true,
                false
        );
    }

    /**
     * 生成排查用的尝试日志
     * @param attempt 尝试信息
     * @return 日志文本
     */
    private String buildAttemptLog(SearchAttempt attempt) {
        if (attempt == null) {
            return "未知搜索源（无尝试信息）";
        }
        StringBuilder builder = new StringBuilder();
        builder.append(StringUtils.defaultIfBlank(attempt.getProvider(), "未知搜索源"))
                .append("（查询词：").append(StringUtils.defaultString(attempt.getQuery())).append("；")
                .append(StringUtils.defaultIfBlank(attempt.getUserMessage(), "未返回可用内容"));
        if (StringUtils.isNotBlank(attempt.getTechnicalMessage())
                && !StringUtils.equals(attempt.getUserMessage(), attempt.getTechnicalMessage())) {
            builder.append("；原始原因：").append(attempt.getTechnicalMessage());
        }
        builder.append("）");
        return builder.toString();
    }

    /**
     * 生成用户可读的无结果标题
     * @param originalQuery 原始查询
     * @param failedCount 失败次数
     * @param emptyResultCount 空结果次数
     * @param skippedCount 跳过次数
     * @param totalCount 总尝试次数
     * @return 标题文本
     */
    private String buildNoResultHeadline(String originalQuery, long failedCount, long emptyResultCount, long skippedCount, int totalCount) {
        if (skippedCount >= totalCount) {
            return "当前没有可用的联网搜索源，暂时无法完成这次搜索。";
        }
        if (failedCount > 0 && emptyResultCount == 0) {
            return "这次联网搜索暂时没能拿到可用结果，主要是搜索服务超时、报错或返回异常，不代表和“"
                    + originalQuery + "”相关的信息一定不存在。";
        }
        if (failedCount > 0) {
            return "这次联网搜索暂时没拿到稳定结果，部分搜索源出现了超时或异常，其余搜索源也没有返回可展示内容。";
        }
        return "这次联网搜索没有找到和“" + originalQuery + "”直接相关的可用结果。";
    }

    /**
     * 生成用户可读的单条尝试说明
     * @param attempt 尝试信息
     * @param showQuery 是否展示查询词
     * @return 展示文本
     */
    private String buildAttemptUserLine(SearchAttempt attempt, boolean showQuery) {
        if (attempt == null) {
            return "未知搜索源：暂时没有拿到可用结果";
        }
        StringBuilder builder = new StringBuilder();
        builder.append(StringUtils.defaultIfBlank(attempt.getProvider(), "未知搜索源"));
        if (showQuery) {
            builder.append("（查询：").append(StringUtils.defaultString(attempt.getQuery())).append("）");
        }
        builder.append("：").append(StringUtils.defaultIfBlank(attempt.getUserMessage(), "暂时没有拿到可用结果"));
        String suffix = buildAttemptUserSuffix(attempt.getTechnicalMessage());
        if (StringUtils.isNotBlank(suffix)) {
            builder.append("（").append(suffix).append("）");
        }
        return builder.toString();
    }

    /**
     * 将跳过原因转成更易读的文案
     * @param rawMessage 原始原因
     * @return 用户文案
     */
    private String translateSkipMessage(String rawMessage) {
        if (StringUtils.contains(rawMessage, "未配置")) {
            return "当前未启用这个搜索源";
        }
        return "这个搜索源本轮没有参与检索";
    }

    /**
     * 将失败原因转成更易读的文案
     * @param rawMessage 原始原因
     * @return 用户文案
     */
    private String translateFailureMessage(String rawMessage) {
        String normalized = StringUtils.defaultString(rawMessage);
        if (StringUtils.containsIgnoreCase(normalized, "timed out") || StringUtils.contains(normalized, "超时")) {
            return "连接搜索服务超时";
        }
        if (StringUtils.contains(normalized, "状态码 400")) {
            return "搜索服务暂时没有接受这次请求";
        }
        if (StringUtils.contains(normalized, "状态码 401") || StringUtils.contains(normalized, "状态码 403")) {
            return "搜索服务鉴权失败，暂时无法使用";
        }
        if (StringUtils.contains(normalized, "状态码 404")) {
            return "搜索服务地址暂时不可用";
        }
        if (StringUtils.contains(normalized, "状态码 429")) {
            return "搜索服务当前请求过多，暂时被限流";
        }
        if (StringUtils.contains(normalized, "状态码 500")
                || StringUtils.contains(normalized, "状态码 502")
                || StringUtils.contains(normalized, "状态码 503")
                || StringUtils.contains(normalized, "状态码 504")) {
            return "搜索服务暂时异常";
        }
        if (StringUtils.containsIgnoreCase(normalized, "connect")
                || StringUtils.containsIgnoreCase(normalized, "connection")
                || StringUtils.containsIgnoreCase(normalized, "refused")
                || StringUtils.containsIgnoreCase(normalized, "reset")) {
            return "连接搜索服务失败";
        }
        if (StringUtils.containsIgnoreCase(normalized, "unknownhost")
                || StringUtils.containsIgnoreCase(normalized, "nodename")
                || StringUtils.containsIgnoreCase(normalized, "dns")) {
            return "搜索服务域名解析失败";
        }
        if (StringUtils.contains(normalized, "未返回可用内容")) {
            return "服务返回为空，暂时没有可展示的结果";
        }
        return "搜索服务暂时不可用";
    }

    /**
     * 为用户补充适量的技术后缀
     * @param rawMessage 原始原因
     * @return 补充信息
     */
    private String buildAttemptUserSuffix(String rawMessage) {
        String normalized = StringUtils.defaultString(rawMessage);
        Matcher statusCodeMatcher = SEARCH_STATUS_CODE_PATTERN.matcher(normalized);
        if (statusCodeMatcher.find()) {
            return "状态码 " + statusCodeMatcher.group(1);
        }
        return "";
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
     * 读取 Json 整数字段
     * @param jsonObject Json 对象
     * @param fieldName 字段名
     * @param defaultValue 默认值
     * @return 字段值
     */
    private int getJsonInt(JsonObject jsonObject, String fieldName, int defaultValue) {
        if (jsonObject == null || !jsonObject.has(fieldName) || jsonObject.get(fieldName).isJsonNull()) {
            return defaultValue;
        }
        try {
            return jsonObject.get(fieldName).getAsInt();
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

    /**
     * 构造搜索元信息标记，供前端稳定解析
     * @param originalQuery 原始搜索词
     * @param actualQuery 实际查询词
     * @param searchResult 搜索结果
     * @return 标记文本
     */
    private String buildSearchMetaMarker(String originalQuery, String actualQuery, SearchResult searchResult) {
        JsonObject object = new JsonObject();
        object.addProperty("query", StringUtils.defaultString(originalQuery));
        object.addProperty("actualQuery", StringUtils.defaultString(actualQuery));
        object.addProperty("provider", getProviderDisplayName(searchResult == null ? "" : searchResult.getProvider()));
        return SEARCH_META_MARKER_START + GSON.toJson(object) + SEARCH_META_MARKER_END;
    }

    /**
     * 构造单条来源标记，供前端稳定解析
     * @param item 搜索项
     * @param index 来源序号
     * @return 标记文本
     */
    private String buildSearchSourceMarker(SearchItem item, int index) {
        JsonObject object = new JsonObject();
        object.addProperty("index", index);
        object.addProperty("title", StringUtils.defaultString(item == null ? "" : item.getTitle()));
        object.addProperty("url", StringUtils.defaultString(item == null ? "" : item.getUrl()));
        object.addProperty("summary", StringUtils.defaultString(item == null ? "" : item.getSnippet()));
        return SEARCH_SOURCE_MARKER_START + GSON.toJson(object) + SEARCH_SOURCE_MARKER_END;
    }

    private static List<SearchItem> sanitizeSearchItems(String query, List<SearchItem> items) {
        List<SearchItem> normalizedItems = new ArrayList<>();
        if (items == null || items.isEmpty()) {
            return normalizedItems;
        }
        QueryProfile queryProfile = buildQueryProfile(query);
        Set<String> dedupeKeys = new LinkedHashSet<>();
        for (SearchItem item : items) {
            SearchItem normalizedItem = normalizeSearchItem(item);
            if (normalizedItem == null) {
                continue;
            }
            String dedupeKey = (StringUtils.defaultString(normalizedItem.getUrl()) + "|" + StringUtils.defaultString(normalizedItem.getTitle())).toLowerCase();
            if (!dedupeKeys.add(dedupeKey)) {
                continue;
            }
            normalizedItems.add(normalizedItem);
        }
        List<ScoredSearchItem> scoredItems = new ArrayList<>();
        for (SearchItem normalizedItem : normalizedItems) {
            int relevanceScore = calculateSearchItemRelevanceScore(normalizedItem, queryProfile);
            int score = calculateSearchItemScore(normalizedItem, queryProfile);
            if (score <= HARD_REJECT_TOTAL_SCORE && relevanceScore <= 0) {
                continue;
            }
            scoredItems.add(new ScoredSearchItem(normalizedItem, relevanceScore, score));
        }
        scoredItems.sort(buildSearchComparator());
        int minimumResultSize = Math.min(MAX_RESULT_SIZE, Math.min(scoredItems.size(), MIN_SOURCE_LIST_SIZE));
        List<SearchItem> result = new ArrayList<>();
        List<ScoredSearchItem> fallbackItems = new ArrayList<>();
        for (ScoredSearchItem scoredItem : scoredItems) {
            if (result.size() >= MAX_RESULT_SIZE) {
                break;
            }
            if (isPreferredSearchItem(scoredItem, queryProfile)) {
                result.add(scoredItem.getItem());
                continue;
            }
            fallbackItems.add(scoredItem);
        }
        for (ScoredSearchItem scoredItem : fallbackItems) {
            if (result.size() >= MAX_RESULT_SIZE) {
                break;
            }
            if (result.size() < minimumResultSize || isDisplayableFallbackSearchItem(scoredItem)) {
                result.add(scoredItem.getItem());
            }
        }
        return result;
    }

    private static SearchItem normalizeSearchItem(SearchItem item) {
        if (item == null) {
            return null;
        }
        String title = StringUtils.trimToEmpty(item.getTitle());
        String url = StringUtils.trimToEmpty(item.getUrl());
        String snippet = normalizeSnippet(item.getSnippet());
        Long publishedAtEpochMillis = item.getPublishedAtEpochMillis();
        if (publishedAtEpochMillis == null) {
            publishedAtEpochMillis = inferPublishedAt(title, snippet, url);
        }
        if (StringUtils.isAllBlank(title, url, snippet)) {
            return null;
        }
        if (looksLikeFailedSearchSource(title) || looksLikeFailedSearchSource(snippet)) {
            return null;
        }
        if (looksLikeLowQualitySearchSource(title, url, snippet)) {
            return null;
        }
        return new SearchItem(title, url, snippet, publishedAtEpochMillis, item.getOriginalOrder());
    }

    private static Comparator<ScoredSearchItem> buildSearchComparator() {
        long now = System.currentTimeMillis();
        return Comparator
                .comparingInt((ScoredSearchItem item) -> item == null ? Integer.MIN_VALUE : item.getRelevanceScore())
                .reversed()
                .thenComparing(Comparator.comparingInt((ScoredSearchItem item) -> item == null ? Integer.MIN_VALUE : item.getScore()).reversed())
                .thenComparing(Comparator.comparingInt((ScoredSearchItem item) -> freshnessTier(item == null || item.getItem() == null ? null : item.getItem().getPublishedAtEpochMillis(), now)).reversed())
                .thenComparing(Comparator.comparingLong((ScoredSearchItem item) -> item == null || item.getItem() == null ? Long.MIN_VALUE : safePublishedAt(item.getItem().getPublishedAtEpochMillis())).reversed())
                .thenComparingInt(item -> item == null || item.getItem() == null || item.getItem().getOriginalOrder() == null ? Integer.MAX_VALUE : item.getItem().getOriginalOrder());
    }

    /**
     * 判断是否应进入优先展示区
     * @param scoredItem 带分数的搜索项
     * @param queryProfile 查询画像
     * @return 是否优先展示
     */
    private static boolean isPreferredSearchItem(ScoredSearchItem scoredItem, QueryProfile queryProfile) {
        if (scoredItem == null || scoredItem.getItem() == null) {
            return false;
        }
        if (scoredItem.getRelevanceScore() >= MIN_PREFERRED_RELEVANCE_SCORE) {
            return true;
        }
        if (scoredItem.getScore() >= MIN_PREFERRED_TOTAL_SCORE) {
            return true;
        }
        if (queryProfile == null || StringUtils.isBlank(queryProfile.getDestination())) {
            return false;
        }
        SearchItem item = scoredItem.getItem();
        String combined = String.join(
                " ",
                StringUtils.defaultString(item.getTitle()),
                StringUtils.defaultString(item.getSnippet()),
                StringUtils.defaultString(item.getUrl())
        );
        return StringUtils.contains(combined, queryProfile.getDestination());
    }

    /**
     * 判断补位来源是否仍值得继续展示
     * @param scoredItem 带分数的搜索项
     * @return 是否可展示
     */
    private static boolean isDisplayableFallbackSearchItem(ScoredSearchItem scoredItem) {
        if (scoredItem == null) {
            return false;
        }
        return scoredItem.getRelevanceScore() > 0 || scoredItem.getScore() >= MIN_DISPLAYABLE_TOTAL_SCORE;
    }

    private static int freshnessTier(Long publishedAtEpochMillis, long now) {
        if (publishedAtEpochMillis == null || publishedAtEpochMillis <= 0) {
            return 0;
        }
        long ageDays = Math.max(0L, (now - publishedAtEpochMillis) / 86_400_000L);
        if (ageDays <= PRIMARY_RECENT_WINDOW_DAYS) {
            return 3;
        }
        if (ageDays <= SECONDARY_RECENT_WINDOW_DAYS) {
            return 2;
        }
        return 1;
    }

    private static long safePublishedAt(Long publishedAtEpochMillis) {
        return publishedAtEpochMillis == null ? 0L : publishedAtEpochMillis;
    }

    /**
     * 构造查询画像，用于结果质量评分
     * @param query 查询词
     * @return 查询画像
     */
    private static QueryProfile buildQueryProfile(String query) {
        String normalized = StringUtils.normalizeSpace(StringUtils.defaultString(query));
        String origin = "";
        String destination = "";

        Matcher routeMatcher = ORIGIN_DESTINATION_PATTERN.matcher(normalized);
        if (routeMatcher.find()) {
            origin = normalizeEntity(routeMatcher.group(1));
            destination = normalizeEntity(routeMatcher.group(2));
        }

        if (StringUtils.isBlank(origin)) {
            Matcher departureMatcher = DEPARTURE_PATTERN.matcher(normalized);
            if (departureMatcher.find()) {
                origin = normalizeEntity(departureMatcher.group(1));
            }
        }

        if (StringUtils.isBlank(destination)) {
            Matcher destinationMatcher = GO_DESTINATION_PATTERN.matcher(normalized);
            if (destinationMatcher.find()) {
                destination = normalizeEntity(destinationMatcher.group(1));
            }
        }

        boolean travelIntent = containsAnyPhrase(normalized, TRAVEL_INTENT_WORDS);
        boolean routeIntent = containsAnyPhrase(normalized, ROUTE_INTENT_WORDS);
        List<String> coreTerms = extractCoreTerms(normalized);

        if (StringUtils.isBlank(destination)) {
            destination = normalizeEntity(extractLocationToken(normalized));
        }

        if (StringUtils.isBlank(destination) && travelIntent && !coreTerms.isEmpty()) {
            for (String term : coreTerms) {
                if (term.length() <= 8 && !isIntentWord(term)) {
                    destination = term;
                    break;
                }
            }
        }
        if (StringUtils.isBlank(origin) && coreTerms.size() >= 2 && routeIntent) {
            origin = coreTerms.get(0);
            if (StringUtils.isBlank(destination)) {
                destination = coreTerms.get(1);
            }
        }

        return new QueryProfile(normalized, origin, destination, travelIntent, routeIntent, coreTerms);
    }

    /**
     * 提取查询中的核心词
     * @param query 查询词
     * @return 核心词列表
     */
    private static List<String> extractCoreTerms(String query) {
        LinkedHashSet<String> terms = new LinkedHashSet<>();
        String normalized = StringUtils.normalizeSpace(StringUtils.defaultString(query))
                .replaceAll("[，。！？,.!?；;：:()/\\\\\\[\\]【】]+", " ");
        String locationToken = normalizeEntity(extractLocationToken(normalized));
        if (StringUtils.isNotBlank(locationToken)) {
            terms.add(locationToken);
        }
        for (String keyword : TRAVEL_INTENT_WORDS) {
            if (StringUtils.contains(normalized, keyword)) {
                terms.add(keyword);
            }
        }
        for (String keyword : ROUTE_INTENT_WORDS) {
            if (StringUtils.contains(normalized, keyword)) {
                terms.add(keyword);
            }
        }
        for (String token : normalized.split("\\s+")) {
            String term = normalizeEntity(token);
            if (StringUtils.isBlank(term) || isStopWord(term)) {
                continue;
            }
            if (term.length() > 8) {
                continue;
            }
            if (term.length() <= 1 && !term.matches("\\d+")) {
                continue;
            }
            terms.add(term);
        }
        return new ArrayList<>(terms);
    }

    /**
     * 统一规范化实体词
     * @param raw 原始词
     * @return 规范化结果
     */
    private static String normalizeEntity(String raw) {
        String term = StringUtils.defaultString(raw).trim();
        if (StringUtils.isBlank(term)) {
            return "";
        }
        term = term
                .replaceAll("(出发|抵达|到达|旅游|旅行|攻略|路线|交通|景点|住宿|酒店|打卡|游玩|约会|情侣)$", "")
                .replaceAll("\\s{2,}", " ")
                .trim();
        return term;
    }

    /**
     * 判断是否为停用词
     * @param term 词项
     * @return 是否停用
     */
    private static boolean isStopWord(String term) {
        for (String stopWord : QUERY_STOP_WORDS) {
            if (StringUtils.equals(term, stopWord)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 判断词项是否属于意图词
     * @param term 词项
     * @return 是否意图词
     */
    private static boolean isIntentWord(String term) {
        return containsAnyPhrase(term, TRAVEL_INTENT_WORDS) || containsAnyPhrase(term, ROUTE_INTENT_WORDS);
    }

    /**
     * 判断文本是否包含任一短语
     * @param text 文本
     * @param phrases 短语列表
     * @return 是否包含
     */
    private static boolean containsAnyPhrase(String text, List<String> phrases) {
        if (StringUtils.isBlank(text) || phrases == null || phrases.isEmpty()) {
            return false;
        }
        for (String phrase : phrases) {
            if (StringUtils.contains(text, phrase)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 计算来源质量分
     * @param item 搜索项
     * @param queryProfile 查询画像
     * @return 分数
     */
    private static int calculateSearchItemScore(SearchItem item, QueryProfile queryProfile) {
        if (item == null) {
            return Integer.MIN_VALUE;
        }
        return calculateSearchItemRelevanceScore(item, queryProfile)
                + calculateSearchItemQualityAdjustment(item, queryProfile);
    }

    /**
     * 计算与当前查询的相关性分
     * @param item 搜索项
     * @param queryProfile 查询画像
     * @return 相关性分
     */
    private static int calculateSearchItemRelevanceScore(SearchItem item, QueryProfile queryProfile) {
        if (item == null) {
            return Integer.MIN_VALUE;
        }
        String title = StringUtils.defaultString(item.getTitle());
        String snippet = StringUtils.defaultString(item.getSnippet());
        String url = StringUtils.defaultString(item.getUrl());
        String combined = title + " " + snippet + " " + url;

        int score = 0;

        boolean destinationMatched = StringUtils.isBlank(queryProfile.getDestination()) || StringUtils.contains(combined, queryProfile.getDestination());
        boolean originMatched = StringUtils.isBlank(queryProfile.getOrigin()) || StringUtils.contains(combined, queryProfile.getOrigin());

        if (StringUtils.isNotBlank(queryProfile.getDestination())) {
            if (StringUtils.contains(title, queryProfile.getDestination())) {
                score += 120;
            } else if (StringUtils.contains(snippet, queryProfile.getDestination()) || StringUtils.contains(url, queryProfile.getDestination())) {
                score += 70;
            } else if (queryProfile.isTravelIntent() || queryProfile.isRouteIntent()) {
                score -= 90;
            }
        }

        if (StringUtils.isNotBlank(queryProfile.getOrigin())) {
            if (StringUtils.contains(title, queryProfile.getOrigin()) || StringUtils.contains(snippet, queryProfile.getOrigin())) {
                score += 12;
            }
            if ((queryProfile.isTravelIntent() || queryProfile.isRouteIntent()) && originMatched && !destinationMatched) {
                score -= 36;
            }
        }

        for (String term : queryProfile.getCoreTerms()) {
            if (isIntentWord(term)) {
                if (StringUtils.contains(title, term)) {
                    score += 12;
                    continue;
                }
                if (StringUtils.contains(snippet, term)) {
                    score += 6;
                    continue;
                }
                if (StringUtils.contains(url, term)) {
                    score += 2;
                }
                continue;
            }
            if (StringUtils.contains(title, term)) {
                score += 28;
                continue;
            }
            if (StringUtils.contains(snippet, term)) {
                score += 14;
                continue;
            }
            if (StringUtils.contains(url, term)) {
                score += 6;
            }
        }

        if (StringUtils.length(queryProfile.getNormalizedQuery()) >= 4 && StringUtils.length(queryProfile.getNormalizedQuery()) <= 24) {
            if (StringUtils.contains(title, queryProfile.getNormalizedQuery())) {
                score += 48;
            } else if (StringUtils.contains(snippet, queryProfile.getNormalizedQuery())) {
                score += 24;
            }
        }

        if (queryProfile.isTravelIntent() && containsAnyPhrase(combined, TRAVEL_INTENT_WORDS)) {
            score += 24;
        }
        if (queryProfile.isRouteIntent() && containsAnyPhrase(combined, ROUTE_INTENT_WORDS)) {
            score += 24;
        }

        return score;
    }

    /**
     * 计算质量和噪声修正分
     * @param item 搜索项
     * @param queryProfile 查询画像
     * @return 修正分
     */
    private static int calculateSearchItemQualityAdjustment(SearchItem item, QueryProfile queryProfile) {
        if (item == null) {
            return Integer.MIN_VALUE;
        }
        String title = StringUtils.defaultString(item.getTitle());
        String snippet = StringUtils.defaultString(item.getSnippet());
        String url = StringUtils.defaultString(item.getUrl());
        int score = calculateDomainQualityScore(url, title, snippet);

        if (queryProfile != null && queryProfile.isTravelIntent() && looksLikeAggregatedTravelPage(title, snippet, queryProfile.getDestination())) {
            score -= 110;
        }

        if (looksLikeSearchResultPage(url, title, snippet)) {
            score -= 90;
        }

        return score;
    }

    /**
     * 计算域名质量分
     * @param url 链接
     * @param title 标题
     * @param snippet 摘要
     * @return 分数
     */
    private static int calculateDomainQualityScore(String url, String title, String snippet) {
        String host = extractHost(url);
        String combined = String.join(" ", StringUtils.defaultString(host), StringUtils.defaultString(title), StringUtils.defaultString(snippet)).toLowerCase();
        int score = 0;
        if (StringUtils.contains(host, ".gov.cn") || StringUtils.contains(host, "gov.cn")) {
            score += 80;
        }
        if (StringUtils.contains(host, ".edu.cn")) {
            score += 30;
        }
        if (StringUtils.containsAny(title, "官网", "官方")) {
            score += 36;
        }
        for (String domain : TRUSTED_TRAVEL_DOMAINS) {
            if (StringUtils.contains(host, domain)) {
                score += 36;
                break;
            }
        }
        for (String domain : TRUSTED_NEWS_DOMAINS) {
            if (StringUtils.contains(host, domain)) {
                score += 18;
                break;
            }
        }
        for (String hint : LOW_QUALITY_DOMAIN_HINTS) {
            if (StringUtils.contains(combined, hint)) {
                score -= 120;
                break;
            }
        }
        if (StringUtils.containsAny(host, "douyin.com", "xiaohongshu.com", "instagram.com", "vimeo.com")) {
            score -= 40;
        }
        return score;
    }

    /**
     * 提取 host
     * @param url 链接
     * @return host
     */
    private static String extractHost(String url) {
        HttpUrl httpUrl = HttpUrl.parse(StringUtils.defaultString(url));
        if (httpUrl == null || StringUtils.isBlank(httpUrl.host())) {
            return "";
        }
        return httpUrl.host().toLowerCase();
    }

    /**
     * 判断是否像站内搜索页或聚合页
     * @param url 链接
     * @param title 标题
     * @param snippet 摘要
     * @return 是否为低质量搜索页
     */
    private static boolean looksLikeSearchResultPage(String url, String title, String snippet) {
        String combined = String.join(" ", StringUtils.defaultString(url), StringUtils.defaultString(title), StringUtils.defaultString(snippet)).toLowerCase();
        return combined.contains("/search")
                || combined.contains("search-results-page")
                || combined.contains("browse likes")
                || combined.contains("people who like")
                || combined.contains("your cart is empty");
    }

    /**
     * 判断是否像多个目的地堆叠的攻略聚合页
     * @param title 标题
     * @param snippet 摘要
     * @param destination 当前目的地
     * @return 是否聚合页
     */
    private static boolean looksLikeAggregatedTravelPage(String title, String snippet, String destination) {
        String text = String.join(" ", StringUtils.defaultString(title), StringUtils.defaultString(snippet));
        Matcher matcher = Pattern.compile("([\\p{IsHan}A-Za-z]{2,8})旅游攻略").matcher(text);
        LinkedHashSet<String> destinations = new LinkedHashSet<>();
        while (matcher.find()) {
            String place = normalizeEntity(matcher.group(1));
            if (StringUtils.isBlank(place)) {
                continue;
            }
            if (StringUtils.isNotBlank(destination) && StringUtils.equals(place, destination)) {
                continue;
            }
            destinations.add(place);
            if (destinations.size() >= 3) {
                return true;
            }
        }
        return false;
    }

    private static Long extractPublishedAt(JsonObject jsonObject, String... fieldNames) {
        if (jsonObject == null || fieldNames == null) {
            return null;
        }
        for (String fieldName : fieldNames) {
            String rawValue = getStaticJsonString(jsonObject, fieldName);
            Long parsed = parsePublishedAt(rawValue);
            if (parsed != null) {
                return parsed;
            }
        }
        return null;
    }

    private static String getStaticJsonString(JsonObject jsonObject, String fieldName) {
        if (jsonObject == null || StringUtils.isBlank(fieldName) || !jsonObject.has(fieldName) || jsonObject.get(fieldName).isJsonNull()) {
            return "";
        }
        try {
            return jsonObject.get(fieldName).getAsString();
        } catch (Exception e) {
            return "";
        }
    }

    private static Long inferPublishedAt(String title, String snippet, String url) {
        String combined = String.join(" ", StringUtils.defaultString(title), StringUtils.defaultString(snippet), StringUtils.defaultString(url));
        Matcher fullDateMatcher = DATE_PATTERN.matcher(combined);
        if (fullDateMatcher.find()) {
            return parsePublishedAt(fullDateMatcher.group());
        }
        Matcher yearMonthMatcher = YEAR_MONTH_PATTERN.matcher(combined);
        if (yearMonthMatcher.find()) {
            return parsePublishedAt(yearMonthMatcher.group());
        }
        Matcher yearMatcher = YEAR_PATTERN.matcher(combined);
        if (yearMatcher.find()) {
            return parsePublishedAt(yearMatcher.group(1));
        }
        return null;
    }

    private static Long parsePublishedAt(String rawValue) {
        String value = StringUtils.trimToEmpty(rawValue);
        if (StringUtils.isBlank(value)) {
            return null;
        }
        try {
            return Instant.parse(value).toEpochMilli();
        } catch (Exception ignored) {
        }
        try {
            return OffsetDateTime.parse(value).toInstant().toEpochMilli();
        } catch (Exception ignored) {
        }
        try {
            return LocalDateTime.parse(value).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        } catch (Exception ignored) {
        }
        try {
            return LocalDate.parse(value).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
        } catch (Exception ignored) {
        }
        Matcher fullDateMatcher = DATE_PATTERN.matcher(value);
        if (fullDateMatcher.find()) {
            try {
                int year = Integer.parseInt(fullDateMatcher.group(1));
                int month = Integer.parseInt(fullDateMatcher.group(2));
                int day = Integer.parseInt(fullDateMatcher.group(3));
                return LocalDate.of(year, month, day).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
            } catch (Exception ignored) {
            }
        }
        Matcher yearMonthMatcher = YEAR_MONTH_PATTERN.matcher(value);
        if (yearMonthMatcher.find()) {
            try {
                int year = Integer.parseInt(yearMonthMatcher.group(1));
                int month = Integer.parseInt(yearMonthMatcher.group(2));
                return LocalDate.of(year, month, 1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
            } catch (Exception ignored) {
            }
        }
        Matcher yearMatcher = YEAR_PATTERN.matcher(value);
        if (yearMatcher.find()) {
            try {
                int year = Integer.parseInt(yearMatcher.group(1));
                return LocalDate.of(year, 1, 1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    private static boolean looksLikeFailedSearchSource(String text) {
        String normalized = StringUtils.normalizeSpace(StringUtils.defaultString(text));
        if (StringUtils.isBlank(normalized)) {
            return false;
        }
        String lowerCaseText = normalized.toLowerCase();
        return normalized.startsWith("搜索失败")
                || normalized.startsWith("请求失败")
                || normalized.startsWith("接口调用异常")
                || normalized.startsWith("无结果或失败")
                || normalized.startsWith("未返回可用内容")
                || normalized.startsWith("未配置")
                || normalized.startsWith("跳过：")
                || normalized.startsWith("跳过:")
                || normalized.startsWith("解析失败")
                || normalized.startsWith("调用失败")
                || lowerCaseText.startsWith("error:")
                || lowerCaseText.startsWith("request failed")
                || lowerCaseText.startsWith("search failed");
    }

    /**
     * 过滤明显噪声来源
     * @param title 标题
     * @param url 链接
     * @param snippet 摘要
     * @return 是否低质量
     */
    private static boolean looksLikeLowQualitySearchSource(String title, String url, String snippet) {
        String combined = String.join(" ", StringUtils.defaultString(title), StringUtils.defaultString(url), StringUtils.defaultString(snippet)).toLowerCase();
        return combined.contains("dict_pangu")
                || combined.contains("download=1")
                || combined.contains("browse likes")
                || combined.contains("people who like")
                || combined.contains("your cart is empty")
                || combined.contains("search-results-page");
    }

    /**
     * 解析模型返回的候选搜索词
     * @param raw 模型原始输出
     * @return 候选列表
     */
    private List<String> parseRewrittenQueries(String raw) {
        LinkedHashSet<String> candidates = new LinkedHashSet<>();
        String cleaned = cleanJson(raw);
        if (StringUtils.isBlank(cleaned)) {
            return new ArrayList<>();
        }
        try {
            JsonElement jsonElement = JsonParser.parseString(cleaned);
            collectRewrittenQueries(jsonElement, candidates);
        } catch (Exception ignored) {
        }
        if (candidates.isEmpty()) {
            for (String line : cleaned.split("\\r?\\n")) {
                collectRewrittenQuery(line, candidates);
            }
        }
        if (candidates.isEmpty()) {
            for (String part : cleaned.split("[；;|]")) {
                collectRewrittenQuery(part, candidates);
            }
        }
        List<String> result = new ArrayList<>(candidates);
        if (result.size() <= queryRewriteCandidatesPerRound) {
            return result;
        }
        return new ArrayList<>(result.subList(0, queryRewriteCandidatesPerRound));
    }

    /**
     * 递归收集 JSON 中的搜索词候选
     * @param jsonElement JSON 节点
     * @param candidates 结果集合
     */
    private void collectRewrittenQueries(JsonElement jsonElement, LinkedHashSet<String> candidates) {
        if (jsonElement == null || jsonElement.isJsonNull() || candidates.size() >= queryRewriteCandidatesPerRound) {
            return;
        }
        if (jsonElement.isJsonPrimitive()) {
            collectRewrittenQuery(jsonElement.getAsString(), candidates);
            return;
        }
        if (jsonElement.isJsonArray()) {
            JsonArray jsonArray = jsonElement.getAsJsonArray();
            for (JsonElement item : jsonArray) {
                collectRewrittenQueries(item, candidates);
                if (candidates.size() >= queryRewriteCandidatesPerRound) {
                    return;
                }
            }
            return;
        }
        if (!jsonElement.isJsonObject()) {
            return;
        }
        JsonObject jsonObject = jsonElement.getAsJsonObject();
        collectRewrittenQuery(getJsonString(jsonObject, "query", ""), candidates);
        collectRewrittenQuery(getJsonString(jsonObject, "searchQuery", ""), candidates);
        collectRewrittenQuery(getJsonString(jsonObject, "keyword", ""), candidates);
        collectRewrittenQuery(getJsonString(jsonObject, "text", ""), candidates);
        collectRewrittenQuery(getJsonString(jsonObject, "value", ""), candidates);
    }

    /**
     * 收集并清洗单条搜索词候选
     * @param rawCandidate 原始候选
     * @param candidates 候选集合
     */
    private void collectRewrittenQuery(String rawCandidate, LinkedHashSet<String> candidates) {
        if (candidates.size() >= queryRewriteCandidatesPerRound) {
            return;
        }
        String normalizedCandidate = normalizeSearchQuery(rawCandidate);
        if (StringUtils.isBlank(normalizedCandidate)) {
            return;
        }
        candidates.add(normalizedCandidate);
    }

    /**
     * 规范化搜索词候选，兼容模型偶发输出的编号、标签和引号
     * @param query 原始搜索词
     * @return 清洗后的搜索词
     */
    private String normalizeSearchQuery(String query) {
        String normalized = StringUtils.defaultString(query).trim();
        if (StringUtils.isBlank(normalized)) {
            return "";
        }
        normalized = normalized
                .replace('\u00A0', ' ')
                .replaceFirst("^[\\-•*]\\s*", "")
                .replaceFirst("^[(（]?\\d+[)）.、:：]\\s*", "")
                .replaceFirst("^(搜索词|查询词|候选词)\\s*[:：]\\s*", "")
                .replaceAll("\\s{2,}", " ")
                .trim();
        normalized = StringUtils.strip(normalized, "\"'“”‘’[]【】");
        normalized = normalized.replaceAll("[。！？；;，,]+$", "").trim();
        if (StringUtils.length(normalized) > 120) {
            normalized = StringUtils.substring(normalized, 0, 120).trim();
        }
        return StringUtils.normalizeSpace(normalized);
    }

    /**
     * 根据当前对话上下文重写搜索来源摘要，只保留真正有用的信息
     * @param originalQuery 原始搜索词
     * @param actualQuery 实际查询词
     * @param searchResult 搜索结果
     * @return 重写后的搜索结果
     */
    private SearchResult rewriteSearchResult(String originalQuery, String actualQuery, SearchResult searchResult) {
        ChatModel chatModel = chatModelSupplier == null ? null : chatModelSupplier.get();
        if (chatModel == null || searchResult == null || !searchResult.hasResults()) {
            return searchResult;
        }
        List<SearchItem> items = searchResult.getItems();
        boolean hasSnippet = items.stream().anyMatch(item -> StringUtils.isNotBlank(item.getSnippet()));
        if (!hasSnippet) {
            return searchResult;
        }
        try {
            JsonArray inputItems = new JsonArray();
            for (int i = 0; i < items.size(); i++) {
                SearchItem item = items.get(i);
                JsonObject object = new JsonObject();
                object.addProperty("index", i + 1);
                object.addProperty("title", StringUtils.defaultString(item.getTitle()));
                object.addProperty("url", StringUtils.defaultString(item.getUrl()));
                object.addProperty("snippet", StringUtils.defaultString(item.getSnippet()));
                inputItems.add(object);
            }
            String rewriteContext = StringUtils.defaultIfBlank(SEARCH_REWRITE_CONTEXT.get(), originalQuery);
            String prompt = """
                    你是搜索来源摘要重写助手。
                    你的任务是根据【当前对话真正关心的问题】，把每条来源里的原始摘要改写成更适合展示给用户的简短总结。
                    
                    重写规则：
                    1. 只保留对当前对话有帮助的信息，删除无关背景、广告口吻、重复铺垫和枝节内容。
                    2. 优先保留结论、数据、条件、限制、适用场景、时间信息和用户可以直接拿来判断的信息。
                    3. 每条只输出 1-2 句简短总结，语言自然，不要超过 90 个汉字或 160 个字符。
                    4. 不要编造原文没有的信息，不要加入你自己的评价，不要写“该来源提到”“这篇文章说”。
                    5. 如果原始摘要几乎没有有效信息，可以压缩成一句最关键的事实；如果完全没信息，就输出空字符串。
                    6. 只输出 JSON 数组，不要输出任何额外说明。
                    
                    输出格式：
                    [
                      {"index": 1, "summary": "重写后的简短总结"},
                      {"index": 2, "summary": "重写后的简短总结"}
                    ]
                    
                    当前对话：%s
                    搜索词：%s
                    实际查询：%s
                    待重写来源：%s
                    """.formatted(rewriteContext, originalQuery, actualQuery, inputItems);
            String response = ChatClient.builder(chatModel)
                    .build()
                    .prompt()
                    .user(prompt)
                    .call()
                    .content();
            JsonArray summaries = JsonParser.parseString(cleanJson(response)).getAsJsonArray();
            List<SearchItem> rewrittenItems = new ArrayList<>();
            for (int i = 0; i < items.size(); i++) {
                SearchItem originalItem = items.get(i);
                String rewrittenSnippet = originalItem.getSnippet();
                for (int j = 0; j < summaries.size(); j++) {
                    JsonObject object = summaries.get(j).getAsJsonObject();
                    int index = object.has("index") ? object.get("index").getAsInt() : -1;
                    if (index != i + 1) {
                        continue;
                    }
                    rewrittenSnippet = normalizeSnippet(getJsonString(object, "summary", rewrittenSnippet));
                    break;
                }
                rewrittenItems.add(new SearchItem(
                        originalItem.getTitle(),
                        originalItem.getUrl(),
                        rewrittenSnippet,
                        originalItem.getPublishedAtEpochMillis(),
                        originalItem.getOriginalOrder()
                ));
            }
            return SearchResult.success(searchResult.getProvider(), searchResult.getQuery(), rewrittenItems, searchResult.getSummary());
        } catch (Exception e) {
            log.warn("搜索来源摘要重写失败，回退原摘要: {}", e.getMessage());
            return searchResult;
        }
    }

    /**
     * 清洗搜索摘要，避免前端直接拿到一整段原始抓取文本
     * @param rawSnippet 原始摘要
     * @return 清洗后的摘要
     */
    private static String normalizeSnippet(String rawSnippet) {
        String snippet = StringUtils.defaultString(rawSnippet)
                .replace('\u00A0', ' ')
                .replaceAll("[\\r\\n\\t]+", " ")
                .replaceAll("\\s{2,}", " ")
                .replaceAll("([。！？.!?；;])(?=\\S)", "$1 ")
                .trim();
        if (StringUtils.isBlank(snippet)) {
            return "";
        }
        snippet = snippet
                .replace("...", "…")
                .replaceAll("…{2,}", "…")
                .replaceAll("([，、；：:])\\1+", "$1");
        if (snippet.length() <= MAX_SNIPPET_LENGTH) {
            return snippet;
        }
        int cutIndex = Math.max(
                Math.max(snippet.lastIndexOf('。', MAX_SNIPPET_LENGTH), snippet.lastIndexOf('；', MAX_SNIPPET_LENGTH)),
                Math.max(snippet.lastIndexOf('！', MAX_SNIPPET_LENGTH), snippet.lastIndexOf('？', MAX_SNIPPET_LENGTH))
        );
        if (cutIndex < 60) {
            cutIndex = MAX_SNIPPET_LENGTH;
        }
        return StringUtils.trim(snippet.substring(0, Math.min(cutIndex + 1, snippet.length()))) + "…";
    }

    /**
     * 清理模型可能包裹的 JSON 代码块
     * @param raw 原始输出
     * @return 清理后的文本
     */
    private String cleanJson(String raw) {
        String text = StringUtils.defaultString(raw).trim();
        if (text.startsWith("```json")) {
            text = text.substring(7).trim();
        } else if (text.startsWith("```")) {
            text = text.substring(3).trim();
        }
        if (text.endsWith("```")) {
            text = text.substring(0, text.length() - 3).trim();
        }
        return text;
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
            searchResult.setItems(sanitizeSearchItems(query, items));
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
        /**
         * 发布时间或最后更新时间
         */
        private final Long publishedAtEpochMillis;
        /**
         * 搜索引擎原始顺序，用于同一时间层内保留相关性
         */
        private final Integer originalOrder;
    }

    @Data
    private static class SearchOutcome {
        /**
         * 命中的实际查询词
         */
        private final String actualQuery;
        /**
         * 命中的搜索结果
         */
        private final SearchResult searchResult;
        /**
         * 可直接返回给上游的渲染文本
         */
        private final String renderedResult;
    }

    @Data
    private static class SearchAttempt {
        /**
         * 搜索提供方显示名
         */
        private final String provider;
        /**
         * 本次尝试使用的查询词
         */
        private final String query;
        /**
         * 给用户展示的简明说明
         */
        private final String userMessage;
        /**
         * 底层原始原因，主要用于日志排查
         */
        private final String technicalMessage;
        /**
         * 是否为跳过状态
         */
        private final boolean skipped;
        /**
         * 是否为调用失败
         */
        private final boolean failed;
        /**
         * 是否为返回空结果
         */
        private final boolean emptyResult;
    }

    @Data
    private static class ScoredSearchItem {
        /**
         * 搜索项
         */
        private final SearchItem item;
        /**
         * 相关性分
         */
        private final int relevanceScore;
        /**
         * 综合分
         */
        private final int score;
    }

    @Data
    private static class QueryProfile {
        /**
         * 规范化查询词
         */
        private final String normalizedQuery;
        /**
         * 出发地
         */
        private final String origin;
        /**
         * 目的地或主体
         */
        private final String destination;
        /**
         * 是否旅游类问题
         */
        private final boolean travelIntent;
        /**
         * 是否路线/交通类问题
         */
        private final boolean routeIntent;
        /**
         * 核心词
         */
        private final List<String> coreTerms;
    }
}
