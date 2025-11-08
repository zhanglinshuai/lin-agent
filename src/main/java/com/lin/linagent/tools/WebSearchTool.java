package com.lin.linagent.tools;

import cn.hutool.http.HttpUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 联网搜索工具
 */
public class WebSearchTool {

    private static final String SEARCH_API_URL = "https://www.searchapi.io/api/v1/search?engine=google";

    private final String apiKey;
    public WebSearchTool(String apiKey) {
        this.apiKey = apiKey;
    }
    @Tool(description = "Search for information from google Search Engine")
    public String searchWeb(@ToolParam(description = "Search query keyword") String query){
        Map<String,Object> paramMap = new HashMap<>();
        paramMap.put("q",query);
        paramMap.put("api_key",apiKey);
        String response = HttpUtil.get(SEARCH_API_URL, paramMap);
        //取出返回结果前5条
        JSONObject jsonObject = JSONUtil.parseObj(response);
        JSONArray organicResults = jsonObject.getJSONArray("organic_results");
        List<Object> objects = organicResults.subList(0,5);
        return objects.stream().map(obj -> {
            JSONObject tempJSONObject = (JSONObject) obj;
            return tempJSONObject.toString();
        }).collect(Collectors.joining(","));
    }
}
