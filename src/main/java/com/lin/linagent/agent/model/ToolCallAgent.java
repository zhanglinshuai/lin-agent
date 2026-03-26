package com.lin.linagent.agent.model;

import cn.hutool.core.collection.CollUtil;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionResult;
import org.springframework.ai.tool.ToolCallback;

import java.util.List;
import java.util.ArrayList;
import java.util.stream.Collectors;

/**
 * 处理工具调用的agent
 * 实现 think和act
 */
@Data
@Slf4j
@EqualsAndHashCode(callSuper = true)
public class ToolCallAgent extends ReActAgent{
    private static final Gson GSON = new Gson();
    /**
     * 可用的工具
     */
    private final ToolCallback[] availableTools;
    /**
     * 工具调用信息的响应
     */
    private ChatResponse toolCallChatResponse;
    /**
     * 工具调用管理者
     */
    private final ToolCallingManager toolCallingManager;
    /**
     * 禁止内置的工具调用机制，自己维护上下文
     */
    private final ChatOptions chatOptions;

    public ToolCallAgent(ToolCallback[] availableTools){
        super();
        this.availableTools = availableTools;
        this.toolCallingManager = ToolCallingManager.builder().build();
        this.chatOptions = DashScopeChatOptions.builder().withInternalToolExecutionEnabled(false).build();
    }

    /**
     * 处理当前状态并决定下一步行动
     * @return 是否需要执行
     */
    @Override
    public boolean think() {
        if(getNextStepPrompt()!=null && !getNextStepPrompt().isEmpty()){
            UserMessage userMessage = new UserMessage(getNextStepPrompt());
            getMessageList().add(userMessage);
        }
        List<Message> messageList = getMessageList();
        Prompt prompt = new Prompt(messageList,chatOptions);
        try{
            //获取带有工具选项的响应
            ChatResponse chatResponse = getChatClient()
                    .prompt(prompt)
                    .system(getSystemPrompt())
                    .toolCallbacks(availableTools)
                    .call()
                    .chatResponse();
            //记录响应，用于Act
            this.toolCallChatResponse = chatResponse;
            AssistantMessage assistantMessage = chatResponse.getResult().getOutput();
            //输出提示信息
            String result = assistantMessage.getText();
            List<AssistantMessage.ToolCall> toolCalls = assistantMessage.getToolCalls();
            log.info(getName()+"的思考:"+result);
            if(toolCalls.isEmpty()){
                //不调用工具时，记录助手消息
                getMessageList().add(assistantMessage);
                emitProgressChunked("final", result);
                setState(AgentState.FINISHED);
                return false;
            }else {
                //需要调用工具时，只向前端暴露“为什么调用、调用哪个工具”
                emitProgress("thinking", buildToolCallThinking(toolCalls));
                return true;
            }
        }catch (Exception e){
                log.info(getName()+"的思考过程遇到了问题"+e.getMessage());
                getMessageList().add(
                        new AssistantMessage("处理时遇到了问题"+e.getMessage()));
            emitProgress("error", "处理时遇到了问题" + e.getMessage());
            return false;
        }
    }

    @Override
    public String act() {
        if(!toolCallChatResponse.hasToolCalls()){
            return "没有工具调用";
        }
        //调用工具
        Prompt prompt = new Prompt(getMessageList(),chatOptions);
        ToolExecutionResult toolExecutionResult = toolCallingManager.executeToolCalls(prompt, toolCallChatResponse);
        //记录消息上下文， 包含助手消息和工具调用返回的结果
        setMessageList(toolExecutionResult.conversationHistory());
        ToolResponseMessage toolResponseMessage = (ToolResponseMessage) CollUtil.getLast(toolExecutionResult.conversationHistory());
        String results = toolResponseMessage.getResponses().stream()
                .map(response -> "工具" + response.name() + "完成了他的任务，结果:" + response.responseData())
                .collect(Collectors.joining("\n"));
        String visibleToolResults = formatToolResults(toolResponseMessage);
        if (!visibleToolResults.isEmpty()) {
            emitProgress("result", visibleToolResults);
        }
        boolean terminateToolCalled = toolResponseMessage.getResponses().stream().anyMatch(response -> "doTerminate".equals(response.name()));
        if(terminateToolCalled){
            setState(AgentState.FINISHED);
            emitProgress("final", "这次任务已经处理完成。");
        }
        log.info(results);
        //将助手返回的消息直接展示在前台
        return toolCallChatResponse.getResult().getOutput().getText();
    }

    /**
     * 生成工具调用前的简短说明
     * @param toolCalls 工具调用列表
     * @return 提示文本
     */
    private String buildToolCallThinking(List<AssistantMessage.ToolCall> toolCalls) {
        List<String> toolNames = toolCalls.stream()
                .map(toolCall -> formatToolDisplayName(toolCall.name()))
                .distinct()
                .toList();
        List<String> reasons = toolCalls.stream()
                .map(toolCall -> inferToolPurpose(toolCall.name()))
                .distinct()
                .toList();
        String reasonText = String.join("、", reasons);
        String toolText = String.join("、", toolNames);
        if (reasonText.isEmpty()) {
            reasonText = "补充完成任务所需的信息";
        }
        if (toolText.isEmpty()) {
            toolText = "相关工具";
        }
        return "为了" + reasonText + "，我将调用" + toolText + "。";
    }

    /**
     * 格式化工具结果，供最终答复展示
     * @param toolResponseMessage 工具响应
     * @return 工具结果文本
     */
    private String formatToolResults(ToolResponseMessage toolResponseMessage) {
        List<String> blocks = new ArrayList<>();
        int index = 1;
        for (ToolResponseMessage.ToolResponse response : toolResponseMessage.getResponses()) {
            if (response == null || response.name() == null) {
                continue;
            }
            if ("doTerminate".equals(response.name())) {
                continue;
            }
            String responseData = response.responseData() == null ? "" : String.valueOf(response.responseData()).trim();
            if (responseData.isEmpty()) {
                continue;
            }
            blocks.add("### 工具" + index + "：" + formatToolDisplayName(response.name()) + "\n" + normalizeToolResponseData(responseData));
            index++;
        }
        if (blocks.isEmpty()) {
            return "";
        }
        return String.join("\n\n", blocks);
    }

    /**
     * 将工具返回值清洗为可阅读文本
     * @param raw 原始返回
     * @return 清洗后文本
     */
    private String normalizeToolResponseData(String raw) {
        if (raw == null) {
            return "";
        }
        String text = decodeQuotedText(raw.trim());
        text = text.replace("\\r\\n", "\n")
                .replace("\\n", "\n")
                .replace("\\t", "    ")
                .replace("\\\"", "\"")
                .replace("\\\\", "\\");
        text = decodeQuotedText(text);
        if (text.startsWith("\"") && text.endsWith("\"") && text.contains("\n") && text.length() > 1) {
            text = text.substring(1, text.length() - 1);
        }
        text = text.replaceAll("(^|\\n)工具执行结果(?=【工具\\d+：)", "$1## 工具执行结果\n");
        text = text.replaceAll("(^|\\n)\"(?=(文件名：|文件路径：|文件内容：|#|##\\s*最终答复))", "$1");
        text = text.replaceAll("\"\\s*(?=##\\s*最终答复)", "\n");
        text = text.replaceAll("\\n{3,}", "\n\n");
        return text.trim();
    }

    /**
     * 解析可能被 JSON 包裹的字符串
     * @param text 原始文本
     * @return 解码结果
     */
    private String decodeQuotedText(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        if (!(text.startsWith("\"") && text.endsWith("\""))) {
            return text;
        }
        try {
            String decoded = GSON.fromJson(text, String.class);
            return decoded == null ? text : decoded;
        } catch (JsonSyntaxException e) {
            return text;
        }
    }

    /**
     * 识别调用工具的目的
     * @param toolName 工具名称
     * @return 调用目的
     */
    private String inferToolPurpose(String toolName) {
        if (toolName == null) {
            return "补充信息";
        }
        return switch (toolName) {
            case "searchWeb" -> "补充外部资料";
            case "readFile" -> "读取现有文件";
            case "writeFile" -> "保存整理结果";
            case "listFiles" -> "确认可用文件";
            case "doTerminate" -> "结束当前任务";
            default -> "补充完成任务所需的信息";
        };
    }

    /**
     * 生成更适合展示的工具名称
     * @param toolName 工具名称
     * @return 展示名称
     */
    private String formatToolDisplayName(String toolName) {
        if (toolName == null) {
            return "工具";
        }
        return switch (toolName) {
            case "searchWeb" -> "联网搜索工具";
            case "readFile" -> "文件读取工具";
            case "writeFile" -> "文件写入工具";
            case "listFiles" -> "文件列表工具";
            case "doTerminate" -> "结束任务工具";
            default -> toolName + " 工具";
        };
    }

}
