package com.lin.linagent.agent.graph.node;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.google.gson.Gson;
import com.lin.linagent.agent.graph.model.QuestionClassification;
import jakarta.annotation.Resource;
import org.springframework.ai.chat.client.ChatClient;

import java.util.ArrayList;
import java.util.Map;

/**
 * 意图识别节点
 */
public class ClassifyIntentNode implements NodeAction {

    @Resource
    private final ChatClient chatClient;

    public ClassifyIntentNode(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
    }

    @Override
    public Map<String, Object> apply(OverAllState state) throws Exception {
        ArrayList<String> inputs = state.value("input", new ArrayList<String>());
        if (inputs.isEmpty()) {
            inputs.add(state.value("original_input", ""));
        }
        String ClassifyIntentPrompt = """
                你是本流程中的【意图识别节点】，任务是根据用户输入，精准判断用户的真实意图。
                
                【输入】
                - 用户输入：{user_input}
                
                【你的职责】
                从用户输入中识别其最可能的意图标签，只能从以下选项中选择：
                1. WEB_SEARCH       —— 用户需要实时信息、事实查找、外部知识、问“谁/什么/何时/哪里/最新”等
                2. FILE_OPERATION   —— 用户涉及读取、写入、上传、修改文件
                3. TERMINATE        —— 用户明确要求停止、结束、退出
                4. OTHER            —— 以上都不符合，或无法明确判断的情况
                
                【判断原则】
                - 必须基于用户输入的字面信息判断，不做过度推理或联想。
                - 若出现多种可能，以最强证据的一项为准，并在 description 中说明原因。
                - 不允许输出未在列表中的意图。
                
                【输出要求】
                你必须只输出一个 JSON 对象，格式如下：
                
                {
                    "input": "{user_input}",
                    "intent": "意图标签，必须是上述四个之一",
                    "confidence": 0.00,
                    "description": "简要说明判断原因"
                }
                
                严格遵守 JSON 格式，不得输出任何多余文本。
                """;
        /**
         * TODO 优化用户输入
         *
         */
        String response = chatClient.prompt()
                .user(String.valueOf(inputs))
                .system(ClassifyIntentPrompt)
                .call()
                .content();
        QuestionClassification questionClassification = parseClassification(response);
        //根据分类确定下一个节点
        String node = "";
        if ("WEB_SEARCH".equals(questionClassification.getIntent())) {
            node = "web_search";
        } else if ("FILE_OPERATION".equals(questionClassification.getIntent())) {
            node = "file_operation";
        } else {
            node = "terminate";
        }
        return Map.of(
                "classification", questionClassification,
                "next_node", node
        );
    }

    /**
     * 将response解析为QuestionClassification
     *
     * @param response
     */
    private QuestionClassification parseClassification(String response) {
        Gson gson = new Gson();
        return gson.fromJson(response, QuestionClassification.class);
    }
}
