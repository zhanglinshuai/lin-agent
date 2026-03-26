package com.lin.linagent.agent.graph.agent;

import com.alibaba.cloud.ai.graph.*;
import com.alibaba.cloud.ai.graph.action.AsyncCommandAction;
import com.alibaba.cloud.ai.graph.action.AsyncEdgeAction;
import com.alibaba.cloud.ai.graph.action.AsyncNodeAction;
import com.alibaba.cloud.ai.graph.checkpoint.config.SaverConfig;
import com.alibaba.cloud.ai.graph.checkpoint.savers.MemorySaver;
import com.alibaba.cloud.ai.graph.exception.GraphStateException;
import com.alibaba.cloud.ai.graph.state.strategy.AppendStrategy;
import com.alibaba.cloud.ai.graph.state.strategy.ReplaceStrategy;
import com.lin.linagent.agent.graph.node.*;
import jakarta.annotation.Resource;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 通用提问智能体
 * @author zhanglinshuai
 */
@Component
public class QuestionAnswerAgent {

    @Resource
    private ChatModel dashscopeChatModel;
    @Resource
    private ToolCallback[] availableTools;

    /**
     * 创建提问智能体
     * @param
     * @return
     */
    public CompiledGraph createQuestionAnswerGraph() throws GraphStateException {
        ChatClient.Builder builder = ChatClient.builder(dashscopeChatModel);
        ChatClient chatClient = builder.build();
        //定义状态工厂
        KeyStrategyFactory keyStrategyFactory = ()->{
            HashMap<String, KeyStrategy> strategies = new HashMap<>();
            strategies.put("original_input",new ReplaceStrategy());//用户原始输入
            strategies.put("input",new AppendStrategy());//用户输入
            strategies.put("classification",new ReplaceStrategy());//意图识别
            strategies.put("search_result",new ReplaceStrategy());//搜索工具调用的结果
            strategies.put("optimized_search_result",new ReplaceStrategy());//优化后的搜索结果
            strategies.put("file_result",new ReplaceStrategy());//写入文件调用的结果
            strategies.put("tool_result",new ReplaceStrategy());//工具调用的结果
            strategies.put("TerminateResult",new ReplaceStrategy());//终止任务调用的结果
            strategies.put("need_call",new ReplaceStrategy());//是否需要调用工具
            strategies.put("next_node",new ReplaceStrategy());//下一个节点
            return strategies;
        };
        //构建状态图
        StateGraph graph = new StateGraph("提问智能体", keyStrategyFactory)
                //添加基本节点
                .addNode("classify_intent", AsyncNodeAction.node_async(new ClassifyIntentNode(builder)))
                .addNode("web_search",AsyncNodeAction.node_async(new webSearchNode()))
                .addNode("optimize_search_result",AsyncNodeAction.node_async(new OptimizeSearchResultsNode(builder)))
                .addNode("file_operation",AsyncNodeAction.node_async(new FileOperationNode(builder)))
                .addNode("terminate",AsyncNodeAction.node_async(new TerminateNode()))
                .addNode("still_need_call",AsyncNodeAction.node_async(new StillNeedCallToolNode(availableTools,chatClient)));
        //添加基本边
        graph.addEdge(StateGraph.START,"classify_intent");
        graph.addEdge("web_search","optimize_search_result");
        graph.addEdge("optimize_search_result","still_need_call");
        graph.addEdge("file_operation","still_need_call");
        graph.addEdge("terminate",StateGraph.END);
        //添加条件边
        graph.addConditionalEdges("classify_intent", AsyncEdgeAction.edge_async(
                state -> {
                    return (String) state.value("next_node").orElse("terminate");
                }),
                Map.of(
                        "web_search","web_search",
                        "file_operation","file_operation",
                        "terminate","terminate"
                )
        );
        graph.addConditionalEdges("still_need_call", AsyncEdgeAction.edge_async(
                state -> {
                    return (String) state.value("next_node","terminate");
                }),
                Map.of(
                        "classify_intent","classify_intent",
                        "terminate","terminate"
                ));
        //配置持久化
        MemorySaver memorySaver = new MemorySaver();
        CompileConfig compileConfig = CompileConfig.builder()
                .saverConfig(SaverConfig.builder()
                        .register(memorySaver)
                        .build())
                .build();

        return graph.compile(compileConfig);
    }
}
