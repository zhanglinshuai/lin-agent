package com.lin.linagent.agent.model;

import cn.hutool.core.collection.CollUtil;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.lin.linagent.tools.WebSearchTool;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import static com.lin.linagent.contant.CommonVariables.FINAL_ANSWER_GUIDANCE;

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
     * 终止工具触发后，强制模型直接输出最终答复
     */
    private static final String FORCE_FINAL_ANSWER_PROMPT = """
            需要的信息已经准备完毕。
            现在不要再调用任何工具，也不要重复展示工具过程。
            请直接面向用户输出完整、清晰的最终答复。
            在不改变统一 Markdown 结构的前提下，宁可写得更充分一点，也不要只给很短的骨架。
            """ + FINAL_ANSWER_GUIDANCE;
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
    /**
     * 是否强制下一轮直接输出最终答复
     */
    private boolean forceFinalAnswerWithoutTools;

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
            if (forceFinalAnswerWithoutTools) {
                // 终止工具触发后，最终答复改为真正的流式生成
                String finalAnswer = generateFinalAnswerByStream(prompt);
                getMessageList().add(new AssistantMessage(finalAnswer));
                forceFinalAnswerWithoutTools = false;
                setState(AgentState.FINISHED);
                return false;
            }
            ChatResponse chatResponse;
            // 常规轮次允许模型继续调用工具
            chatResponse = getChatClient()
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
                forceFinalAnswerWithoutTools = false;
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
        AtomicBoolean searchProgressEmitted = new AtomicBoolean(false);
        ToolExecutionResult toolExecutionResult;
        try {
            WebSearchTool.setRewriteContext(extractSearchRewriteContext());
            WebSearchTool.setProgressConsumer(content -> {
                if (content == null || content.isBlank()) {
                    return;
                }
                searchProgressEmitted.set(true);
                emitProgress("result", content);
            });
            toolExecutionResult = toolCallingManager.executeToolCalls(prompt, toolCallChatResponse);
        } finally {
            WebSearchTool.clearProgressConsumer();
            WebSearchTool.clearRewriteContext();
        }
        //记录消息上下文， 包含助手消息和工具调用返回的结果
        setMessageList(toolExecutionResult.conversationHistory());
        ToolResponseMessage toolResponseMessage = (ToolResponseMessage) CollUtil.getLast(toolExecutionResult.conversationHistory());
        String results = toolResponseMessage.getResponses().stream()
                .map(response -> "工具" + response.name() + "完成了他的任务，结果:" + response.responseData())
                .collect(Collectors.joining("\n"));
        String visibleToolResults = formatToolResults(toolResponseMessage, searchProgressEmitted.get());
        if (!visibleToolResults.isEmpty()) {
            emitProgressChunked("result", visibleToolResults);
        }
        boolean terminateToolCalled = toolResponseMessage.getResponses().stream().anyMatch(response -> "doTerminate".equals(response.name()));
        if(terminateToolCalled){
            // 终止工具只表示“可以收束”，真正的最终答复留给下一轮模型生成
            forceFinalAnswerWithoutTools = true;
            setNextStepPrompt(FORCE_FINAL_ANSWER_PROMPT);
            emitProgress("thinking", (searchProgressEmitted.get() || !visibleToolResults.isEmpty()) ? "关键信息已经补齐了，我接着为你整理成更清楚的答复。" : "我正在把刚才的信息整理成更清楚的答复。");
        }
        log.info(results);
        //将助手返回的消息直接展示在前台
        return toolCallChatResponse.getResult().getOutput().getText();
    }

    /**
     * 在不再允许调用工具时，直接流式生成最终答复
     * @param prompt 当前上下文提示
     * @return 完整最终答复
     */
    private String generateFinalAnswerByStream(Prompt prompt) {
        StringBuilder finalAnswerBuilder = new StringBuilder();
        for (String chunk : getChatClient()
                .prompt(prompt)
                .system(getSystemPrompt())
                .stream()
                .content()
                .toIterable()) {
            if (chunk == null || chunk.isEmpty()) {
                continue;
            }
            finalAnswerBuilder.append(chunk);
            emitProgress("final", chunk);
        }
        return finalAnswerBuilder.toString();
    }

    /**
     * 生成工具调用前的简短说明
     * @param toolCalls 工具调用列表
     * @return 提示文本
     */
    private String buildToolCallThinking(List<AssistantMessage.ToolCall> toolCalls) {
        List<String> actions = toolCalls.stream()
                .map(toolCall -> inferUserFacingAction(toolCall.name()))
                .distinct()
                .filter(action -> action != null && !action.isBlank())
                .toList();
        if (actions.isEmpty()) {
            return "我先补齐这一步需要的关键信息，再继续为你整理。";
        }
        if (actions.size() == 1) {
            return actions.get(0) + "。";
        }
        if (actions.size() == 2) {
            return actions.get(0) + "，再" + removeLeadingClause(actions.get(1)) + "。";
        }
        return actions.get(0) + "，再" + removeLeadingClause(actions.get(1)) + "，然后" + removeLeadingClause(actions.get(2)) + "。";
    }

    /**
     * 格式化工具结果，供最终答复展示
     * @param toolResponseMessage 工具响应
     * @return 工具结果文本
     */
    private String formatToolResults(ToolResponseMessage toolResponseMessage, boolean skipSearchResult) {
        List<String> blocks = new ArrayList<>();
        int index = 1;
        for (ToolResponseMessage.ToolResponse response : toolResponseMessage.getResponses()) {
            if (response == null || response.name() == null) {
                continue;
            }
            if ("doTerminate".equals(response.name())) {
                continue;
            }
            if (skipSearchResult && "searchWeb".equals(response.name())) {
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
     * 提取当前工具轮次最接近用户真实意图的上下文
     * @return 对话上下文
     */
    private String extractSearchRewriteContext() {
        for (Message message : getMessageList()) {
            if (!(message instanceof UserMessage userMessage)) {
                continue;
            }
            String text = StringUtils.defaultString(userMessage.getText()).trim();
            if (StringUtils.isBlank(text)) {
                continue;
            }
            return text;
        }
        return "";
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
     * 将工具动作转换成面向用户的下一步说明
     * @param toolName 工具名称
     * @return 用户可读文案
     */
    private String inferUserFacingAction(String toolName) {
        if (toolName == null) {
            return "我先把关键背景补齐，再继续为你整理";
        }
        return switch (toolName) {
            case "searchWeb" -> "我先帮你补充一下这部分相关资料，再把重点整理给你";
            case "readFile" -> "我先看看你提供的材料内容，再继续往下整理";
            case "writeFile" -> "我先把整理结果落成你可以直接使用的内容";
            case "listFiles" -> "我先确认一下你这边现有的材料";
            case "doTerminate" -> "";
            default -> "我先把关键背景补齐，再继续为你整理";
        };
    }

    /**
     * 去掉重复的主语前缀，避免多动作拼接时语句生硬
     * @param text 原始文案
     * @return 去前缀后的文案
     */
    private String removeLeadingClause(String text) {
        if (text == null) {
            return "";
        }
        return text.replaceFirst("^我先", "");
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
