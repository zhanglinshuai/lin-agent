package com.lin.linagent.agent.graph.node;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.lin.linagent.agent.graph.model.ToolResult;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import reactor.core.publisher.Flux;

import java.util.Map;

/**
 * 优化搜索结果节点(流式输出）
 *
 * @author zhanglinshuai
 */
@Slf4j
public class OptimizeSearchResultsNode implements NodeAction {
    @Resource
    private final ChatClient chatClient;

    public OptimizeSearchResultsNode(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
    }

    @Override
    public Map<String, Object> apply(OverAllState state) throws Exception {
        String originalInput = state.value("original_input", "");
        String searchResult = state.value("search_result", "");
        String optimizeSearchResultPrompt = """
                你是本流程中的“搜索结果处理节点”。
                
                【输入】
                - 用户输入：{user_input}
                - 搜索返回的唯一长结果：{search_result}
                
                【你的任务】
                从冗长的搜索结果中筛选出与用户需求最相关的信息，对其进行提取、清洗、压缩、重写，并生成高质量的最终回答。
                
                【处理准则】
                1. 相关性筛选
                   - 只保留与用户输入目的密切相关的片段
                   - 忽略广告、导航、页面噪音、与问题无关的段落
                
                2. 内容清洗与浓缩
                   - 将长文本压缩为简洁、逻辑清晰的结构
                   - 不改变事实，但可以提高表达质量
                   - 将多段混乱内容重写成通顺自然语言
                
                3. 最终结果优化
                   - 回答必须完整覆盖用户意图
                   - 不引用搜索过程，不出现“搜索结果显示”“网页内容”等字样
                   - 若信息不足，可补充常识推断，但需标注为推断内容
                
                4. 表达要求
                   - 结构化输出（如：要点、步骤、总结 等）
                   - 语言简洁，不堆砌搜索内容原文
                   - 一次性给出面向用户的最终答案
                
                【输出】
                请根据以上规则，生成面向用户的最终优化回答。
                """;
        StringBuilder sb = new StringBuilder();
        sb.append("用户输入：").append(originalInput).append("\n");
        sb.append("搜索结果：").append(searchResult).append("\n");
        String optimizedSearchResult = chatClient.prompt(sb.toString())
                .system(optimizeSearchResultPrompt)
                .call()
                .content();
        log.info("优化后的搜索结果:{}", optimizedSearchResult);
        ToolResult toolResult = new ToolResult();
        toolResult.setToolName("webSearch");
        toolResult.setToolResult(optimizedSearchResult);
        return Map.of(
                "optimized_search_result",optimizedSearchResult,
                "next_node","still_need_call",
                "tool_result",toolResult
        );
    }
}
