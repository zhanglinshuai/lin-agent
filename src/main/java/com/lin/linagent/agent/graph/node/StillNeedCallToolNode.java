package com.lin.linagent.agent.graph.node;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.google.gson.Gson;
import com.lin.linagent.agent.graph.model.JudgeCallTools;
import com.lin.linagent.agent.graph.model.QuestionClassification;
import com.lin.linagent.agent.graph.model.ToolResult;
import com.lin.linagent.contant.CommonVariables;
import jakarta.annotation.Resource;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.tool.ToolCallback;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 是否还需调用工具节点
 * TODO 根据用户输入判断是否还需要调用工具
 */
public class StillNeedCallToolNode implements NodeAction {

    /**
     * 可用的工具
     */

    private final ToolCallback[] availableTools;

    private final ChatClient chatClient;
    /**
     * 维护一个输入上下文
     */
    private final List<String> addInput = new ArrayList<>();
    public StillNeedCallToolNode(ToolCallback[] availableTools, ChatClient chatClient) {
        this.availableTools = availableTools;
        this.chatClient = chatClient;
    }

    @Override
    public Map<String, Object> apply(OverAllState state) throws Exception {
        List<String> input = state.value("input", addInput);
        String originalInput = state.value("original_input", "");
        if(!input.contains(originalInput)){
            input.add(originalInput);
        }
        ToolResult toolResult = state.value("tool_result", new ToolResult());
        QuestionClassification questionClassification = state.value("classification", new QuestionClassification());
        String intent = questionClassification.getIntent();
        List<Message>  messageList = new ArrayList<>();
        messageList.add(new UserMessage(String.valueOf(input)));
        messageList.add(new UserMessage(intent));
        Object result = toolResult.getToolResult();
        messageList.add(new UserMessage(result.toString()));
        Prompt prompt = new Prompt(messageList);

        String judgePrompt = """
                你是本流程中的【工具调用判断节点（ToolCallDecisionNode）】，任务是根据用户输入、当前状态和上一轮工具执行结果，判断是否需要继续调用工具。
                
                【输入】
                - 用户最新输入：{input}
                - 上一步模型生成的意图分类：{classification}
                - 上一次工具调用结果：{tool_result}
                - 当前可用工具列表：{availableTools}
                
                【判断规则】
                1. 如果用户的需求已经被满足（如已经得到答案），则：
                   - need_call = false
                   - next_node = "terminate"
                2. 如果意图分类显示需要调用工具（如搜索、文件操作等），且尚未调用工具，则：
                   - need_call = true
                   - next_node = "classify_intent"
                3. 如果工具已经调用过，但结果仍无法满足用户需求，则：
                   - need_call = true
                   - next_node = "classify_intent"
                4. 如果用户请求不涉及工具操作（如纯聊天、咨询、问政策），则：
                   - need_call = false
                   - next_node = "terminate"
                5. 如果用户明确提出“继续”“再查查”“不够详细”等，说明需要继续调用工具，则：
                   - need_call = true
                   - next_node = "classify_intent"
                6. 如果用户请求的功能在 availableTools 中不存在，则：
                   - need_call = false
                   - next_node = "terminate"
                
                【输出要求】
                请严格只输出一个 JSON 对象，格式如下：
                
                {
                  "need_call": true/false,
                  "next_node": "classify_intent 或 terminate",
                  "reason": "简要说明判断原因"
                }
                
                特别注意：
                - 严格根据规则判断，不要进行额外解读
                - 不输出模型思考过程
                - 不臆造工具，只能选择 availableTools 中真实存在的工具
                """;
        //获取带有工具选项的响应
        String response = chatClient.prompt(prompt)
                .system(judgePrompt)
                .toolCallbacks(availableTools)
                .call()
                .content();
        JudgeCallTools judgeCallTools = parseJudgeCallTools(response);
        String needCall = judgeCallTools.getNeed_call();
        String reason = judgeCallTools.getReason();
        String nextNode = "";
        if(needCall.equals("true")){
            //需要调用工具
            nextNode = judgeCallTools.getNext_node();
            messageList.add(new UserMessage(judgeCallTools.toString()));
            addInput.add(reason);
        }else {
            nextNode = judgeCallTools.getNext_node();
        }
        return Map.of(
                "next_node",nextNode,
                "input",addInput
        );
    }

    private JudgeCallTools parseJudgeCallTools(String response) {
        Gson gson = new Gson();
        return gson.fromJson(response,JudgeCallTools.class);
    }
}
