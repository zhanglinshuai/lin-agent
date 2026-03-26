package com.lin.linagent.agent.graph.node;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.lin.linagent.agent.graph.model.QuestionClassification;
import com.lin.linagent.agent.graph.model.ToolResult;
import com.lin.linagent.contant.CommonVariables;
import com.lin.linagent.tools.WebSearchTool;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

/**
 * webSearch工具调用节点
 */
@Slf4j
public class webSearchNode implements NodeAction {
    @Override
    public Map<String, Object> apply(OverAllState state) throws Exception {
        QuestionClassification questionClassification = state.value("classification")
                .map(v -> (QuestionClassification) v)
                .orElse(new QuestionClassification());
        String input = questionClassification.getInput();
        //调用web搜索工具
        WebSearchTool webSearchTool = new WebSearchTool(
                CommonVariables.SEARCH_API_KEY,
                CommonVariables.TAVILY_SEARCH_API_KEY,
                CommonVariables.SEARCH_PROVIDER_ORDER
        );
        String webSearchResult = webSearchTool.searchWeb(input);
        ToolResult toolResult = new ToolResult();
        toolResult.setToolName("webSearch");
        toolResult.setToolResult(webSearchResult);
        return Map.of(
                "tool_result",toolResult,
                "next_node","optimize_search_result",
                "search_result",webSearchResult
        );
    }
}
