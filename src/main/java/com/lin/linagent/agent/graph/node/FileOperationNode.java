package com.lin.linagent.agent.graph.node;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.lin.linagent.agent.graph.model.ToolResult;
import com.lin.linagent.tools.FileOperationTool;
import jakarta.annotation.Resource;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;

import java.util.Map;

/**
 * 写入文件节点
 */
public class FileOperationNode implements NodeAction {
    @Resource
    private final ChatClient chatClient;

    public FileOperationNode(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
    }
    @Override
    public Map<String, Object> apply(OverAllState state) throws Exception {
        String fileInput = state.value("search_result", "");
        String fileNamePrompt = """
                你是一名专业的文件标题生成器。你的任务是从用户输入的正文内容中提取核心主题，并生成一个简洁、清晰、专业的文件标题。
                
                【标题要求】
                1. 标题长度建议在 8～20 个字之间（中文）或 3～10 个词（英文）。
                2. 必须高度概括内容主旨，不得流水账。
                3. 不包含过度修饰词（例如：非常、特别、详细等）。
                4. 不要使用句号、感叹号等标点。
                5. 避免太宽泛（如“总结”），也避免太细碎。
                6. 输出格式固定为纯文本标题，不要解释。
                
                【生成规则】
                - 如果输入内容包含关键主题（如“调研”“报表”“需求文档”），优先加在标题前缀。
                - 如果内容包含时间、地点、对象等信息，应自动提取并合并到标题中。
                - 如果用户输入内容很短（如一句话），根据语义自动补全为最合理的标题。
                - 如内容杂乱，需自动提炼统一主题。
                
                请只输出最终标题。
                """;
        ChatResponse chatResponse = chatClient.prompt()
                .user(fileInput)
                .system(fileNamePrompt)
                .call()
                .chatResponse();
        FileOperationTool fileOperationTool = new FileOperationTool();
        fileOperationTool.writeFile(chatResponse.getResult().getOutput().getText(),fileInput);
        ToolResult toolResult = new ToolResult();
        toolResult.setToolName("fileOperation");
        toolResult.setToolResult(true);
        return Map.of(
                "next_node","still_need_call",
                "tool_result",toolResult,
                "file_result",true
        );
    }
}
