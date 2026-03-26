package com.lin.linagent.app;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.lin.linagent.agent.model.AgentProgressEvent;
import com.lin.linagent.agent.model.LinManus;
import com.lin.linagent.domain.ChatMemory;
import com.lin.linagent.tools.FileOperationTool;
import com.lin.linagent.tools.TerminateTool;
import com.lin.linagent.tools.WebSearchTool;
import com.lin.linagent.service.ChatMemoryService;
import jakarta.annotation.Resource;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * 统一助手应用
 * 负责在情感陪伴和任务执行之间做路由
 */
@Component
@Slf4j
public class UnifiedAssistantApp {

    private static final String MODE_AUTO = "auto";
    private static final String MODE_EMOTION = "emotion";
    private static final String MODE_AGENT = "agent";
    private static final String MODE_MANUS = "manus";

    private static final String LABEL_EMOTION = "情感陪伴";
    private static final String LABEL_AGENT = "任务执行";
    private static final String LABEL_MANUS = "超级智能体";

    private static final String TAG_EMOTION_SUPPORT = "emotion_support";
    private static final String TAG_RELATIONSHIP_GUIDANCE = "relationship_guidance";
    private static final String TAG_STRESS_RELIEF = "stress_relief";
    private static final String TAG_TASK_PLANNING = "task_planning";
    private static final String TAG_INFORMATION_LOOKUP = "information_lookup";
    private static final String TAG_CONTENT_ORGANIZING = "content_organizing";
    private static final String TAG_GENERAL_ASSISTANCE = "general_assistance";

    private final Gson gson = new Gson();

    @Resource
    private EmotionApp emotionApp;

    @Resource
    private ChatModel dashscopeChatModel;

    @Resource
    private ToolCallback[] allTools;

    @Resource
    private ChatMemoryService chatMemoryService;

    @Resource
    private FileOperationTool fileOperationTool;

    @Resource
    private WebSearchTool webSearchTool;

    @Resource
    private TerminateTool terminateTool;

    /**
     * 统一流式对话入口
     *
     * @param message 用户消息
     * @param chatId 会话id
     * @param userId 用户id
     * @param mode 模式：auto / emotion / agent
     * @return 统一的流式事件
     */
    public Flux<String> doChatByStream(String message, String chatId, String userId, String mode, boolean allowFileTool, boolean allowWebSearchTool, List<String> uploadedFiles) {
        String normalizedMode = normalizeMode(mode);
        if (MODE_EMOTION.equals(normalizedMode)) {
            return buildEmotionFlux(message, chatId, userId, buildDecision(
                    MODE_EMOTION,
                    inferTagByMode(message, MODE_EMOTION),
                    "用户选择了情感陪伴模式"
            ));
        }
        if (MODE_AGENT.equals(normalizedMode)) {
            return buildAgentFlux(message, chatId, userId, buildDecision(
                    MODE_AGENT,
                    inferTagByMode(message, MODE_AGENT),
                    "用户选择了任务执行模式"
            ), allowFileTool, allowWebSearchTool, uploadedFiles);
        }
        return Mono.fromCallable(() -> classifyMode(message))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMapMany(decision -> {
                    if (MODE_AGENT.equals(decision.getMode())) {
                        return buildAgentFlux(message, chatId, userId, decision, allowFileTool, allowWebSearchTool, uploadedFiles);
                    }
                    return buildEmotionFlux(message, chatId, userId, decision);
                });
    }

    /**
     * 超级智能体流式对话
     * @param message 用户消息
     * @param chatId 会话id
     * @param userId 用户id
     * @return 流式事件
     */
    public Flux<String> doManusChatByStream(String message, String chatId, String userId) {
        RouteDecision decision = buildDecision(MODE_MANUS, TAG_GENERAL_ASSISTANCE, "用户进入超级智能体模式");
        return Flux.create(sink -> {
            sink.next(buildEvent("route", MODE_MANUS, LABEL_MANUS, decision.getTag(), "", decision.getReason()));
            sink.next(buildEvent("thinking", MODE_MANUS, LABEL_MANUS, decision.getTag(), "我先拆解你的目标，再逐步完成这次任务。", decision.getReason()));
            Mono.fromRunnable(() -> runManusWithProgress(message, chatId, userId, decision, sink))
                    .subscribeOn(Schedulers.boundedElastic())
                    .subscribe();
        }, FluxSink.OverflowStrategy.BUFFER);
    }

    private Flux<String> buildEmotionFlux(String message, String chatId, String userId, RouteDecision decision) {
        return Flux.concat(
                Flux.just(buildEvent("route", MODE_EMOTION, LABEL_EMOTION, decision.getTag(), "", decision.getReason())),
                Flux.just(buildEvent("thinking", MODE_EMOTION, LABEL_EMOTION, decision.getTag(), "我先理解一下你的情绪和处境。", decision.getReason())),
                emotionApp.doChatByStream(message, chatId, userId, decision.getTag())
                        .map(chunk -> buildEvent("final", MODE_EMOTION, LABEL_EMOTION, decision.getTag(), chunk, decision.getReason())),
                Flux.just(buildEvent("done", MODE_EMOTION, LABEL_EMOTION, decision.getTag(), "", decision.getReason()))
        );
    }

    private Flux<String> buildAgentFlux(String message, String chatId, String userId, RouteDecision decision, boolean allowFileTool, boolean allowWebSearchTool, List<String> uploadedFiles) {
        return Mono.fromCallable(() -> decideToolUsage(message, allowFileTool, allowWebSearchTool))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMapMany(plan -> {
                    if (!plan.getNeedTool()) {
                        return buildDirectAnswerFlux(message, chatId, userId, decision, plan, allowFileTool, allowWebSearchTool);
                    }
                    return Flux.create(sink -> {
                        sink.next(buildEvent("route", MODE_AGENT, LABEL_AGENT, decision.getTag(), "", decision.getReason()));
                        sink.next(buildEvent("thinking", MODE_AGENT, LABEL_AGENT, decision.getTag(), plan.getThinking(), decision.getReason()));
                        Mono.fromRunnable(() -> runAgentWithProgress(buildAgentInput(message, uploadedFiles, allowWebSearchTool), chatId, userId, decision, sink, allowFileTool, allowWebSearchTool))
                                .subscribeOn(Schedulers.boundedElastic())
                                .subscribe();
                    }, FluxSink.OverflowStrategy.BUFFER);
                });
    }

    /**
     * 直接回答模式
     * @param message 用户消息
     * @param chatId 会话id
     * @param userId 用户id
     * @param decision 路由决策
     * @param plan 工具计划
     * @return 流式结果
     */
    private Flux<String> buildDirectAnswerFlux(String message, String chatId, String userId, RouteDecision decision, ToolUsagePlan plan, boolean allowFileTool, boolean allowWebSearchTool) {
        StringBuilder answerBuilder = new StringBuilder();
        Flux<String> answerFlux = directAnswerWithoutToolStream(message, allowFileTool, allowWebSearchTool)
                .doOnNext(answerBuilder::append)
                .map(chunk -> buildEvent("final", MODE_AGENT, LABEL_AGENT, decision.getTag(), chunk, decision.getReason()));
        return Flux.concat(
                Flux.just(buildEvent("route", MODE_AGENT, LABEL_AGENT, decision.getTag(), "", decision.getReason())),
                Flux.just(buildEvent("thinking", MODE_AGENT, LABEL_AGENT, decision.getTag(), plan.getThinking(), decision.getReason())),
                answerFlux,
                Mono.fromRunnable(() -> saveAgentConversation(chatId, userId, message, answerBuilder.toString(), decision.getTag()))
                        .thenMany(Flux.just(buildEvent("done", MODE_AGENT, LABEL_AGENT, decision.getTag(), "", decision.getReason())))
        );
    }

    private RouteDecision classifyMode(String message) {
        if (StringUtils.isBlank(message)) {
            return buildDecision(MODE_EMOTION, TAG_EMOTION_SUPPORT, "消息为空，默认走情感陪伴模式");
        }
        String classifyPrompt = """
                你是统一助手中的【路由决策器】。
                你的任务是根据用户输入，在以下两个模式中选择最合适的一种：
                
                1. emotion：适合情绪倾诉、恋爱烦恼、关系困扰、心理支持、安慰、共情、陪伴、压力表达等场景
                2. agent：适合查询信息、制定计划、联网搜索、写文档、生成文件、执行任务、整理资料、调用工具等场景
                
                输出要求：
                - 只能输出一个 JSON 对象
                - 格式固定如下：
                {
                  "mode": "emotion 或 agent",
                  "tag": "必须从以下标签中选择一个：emotion_support、relationship_guidance、stress_relief、task_planning、information_lookup、content_organizing、general_assistance",
                  "reason": "一句简短中文理由"
                }
                - 不要输出任何额外说明
                """;
        String response = ChatClient.builder(dashscopeChatModel)
                .build()
                .prompt()
                .system(classifyPrompt)
                .user(message)
                .call()
                .content();
        try {
            RouteDecision decision = gson.fromJson(cleanJson(response), RouteDecision.class);
            if (decision == null || StringUtils.isBlank(decision.getMode())) {
                return buildDecision(MODE_EMOTION, TAG_EMOTION_SUPPORT, "模型未返回有效路由，默认走情感陪伴模式");
            }
            decision.setMode(normalizeMode(decision.getMode()));
            if (!MODE_AGENT.equals(decision.getMode())) {
                decision.setMode(MODE_EMOTION);
            }
            decision.setTag(normalizeTag(decision.getTag(), decision.getMode(), message));
            if (StringUtils.isBlank(decision.getReason())) {
                decision.setReason(MODE_AGENT.equals(decision.getMode()) ? "问题更偏任务执行" : "问题更偏情感表达");
            }
            return decision;
        } catch (JsonSyntaxException e) {
            log.warn("统一助手路由解析失败: {}", response);
            return buildDecision(MODE_EMOTION, TAG_EMOTION_SUPPORT, "路由解析失败，默认走情感陪伴模式");
        }
    }

    private String normalizeMode(String mode) {
        if (StringUtils.isBlank(mode)) {
            return MODE_AUTO;
        }
        String normalizedMode = mode.trim().toLowerCase();
        if (MODE_EMOTION.equals(normalizedMode) || MODE_AGENT.equals(normalizedMode)) {
            return normalizedMode;
        }
        return MODE_AUTO;
    }

    private RouteDecision buildDecision(String mode, String tag, String reason) {
        RouteDecision decision = new RouteDecision();
        decision.setMode(mode);
        decision.setTag(tag);
        decision.setReason(reason);
        return decision;
    }

    private String buildEvent(String type, String mode, String label, String tag, String content, String reason) {
        UnifiedAssistantEvent event = new UnifiedAssistantEvent();
        event.setType(type);
        event.setMode(mode);
        event.setLabel(label);
        event.setTag(tag);
        event.setContent(content);
        event.setReason(reason);
        return gson.toJson(event);
    }

    private String cleanJson(String raw) {
        if (raw == null) {
            return "";
        }
        String text = raw.trim();
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

    private String formatAgentResult(String raw) {
        if (StringUtils.isBlank(raw)) {
            return "任务已完成，但当前没有返回可展示的结果。";
        }
        return raw.replace(",Step:", "\n\nStep:");
    }

    /**
     * 将最终答复拆成多段事件返回，便于前端流式渲染
     * @param answer 最终答复
     * @param mode 模式
     * @param label 标签
     * @param tag 业务标签
     * @param reason 路由原因
     * @return 流式事件
     */
    private Flux<String> streamFinalAnswer(String answer, String mode, String label, String tag, String reason) {
        return Flux.fromIterable(splitForStreaming(answer))
                .map(chunk -> buildEvent("final", mode, label, tag, chunk, reason));
    }

    /**
     * 将长文本拆分成适合流式显示的片段
     * @param content 完整文本
     * @return 分片结果
     */
    private List<String> splitForStreaming(String content) {
        List<String> chunks = new ArrayList<>();
        if (StringUtils.isBlank(content)) {
            return List.of("");
        }
        String[] segments = content.split("(?<=[。！？.!?；;\\n])");
        for (String segment : segments) {
            if (segment == null || segment.isEmpty()) {
                continue;
            }
            String remaining = segment;
            int chunkSize = 48;
            while (remaining.length() > chunkSize) {
                chunks.add(remaining.substring(0, chunkSize));
                remaining = remaining.substring(chunkSize);
            }
            if (!remaining.isEmpty()) {
                chunks.add(remaining);
            }
        }
        if (chunks.isEmpty()) {
            chunks.add(content);
        }
        return chunks;
    }

    /**
     * 工具使用决策
     * @param message 用户消息
     * @return 工具计划
     */
    private ToolUsagePlan decideToolUsage(String message, boolean allowFileTool, boolean allowWebSearchTool) {
        String prompt = """
                你是统一助手中的【工具使用决策器】。
                请先判断当前问题是否真的需要调用工具。
                
                规则：
                1. 如果只需要分析、总结、解释、规划、安抚、整理表达，可以直接回答，不需要工具。
                2. 只有在联网搜索工具可用时，用户需要联网查询背景资料、推荐信息、地点攻略、外部事实、最新动态，才可以调用联网搜索工具。
                3. 只有在文件工具可用时，用户明确要求读取、列出、保存文件，才需要文件工具。
                4. 不要因为问题比较长，就默认调用工具。
                5. 当前联网搜索工具可用状态：%s
                6. 当前文件工具可用状态：%s
                
                只输出 JSON：
                {
                  "needTool": true/false,
                  "thinking": "如果 needTool 为 true，这里只写为什么要调用工具，以及准备调用哪类工具；不要写工具结果"
                }
                """.formatted(allowWebSearchTool ? "可用" : "不可用", allowFileTool ? "可用" : "不可用");
        String response = ChatClient.builder(dashscopeChatModel)
                .build()
                .prompt()
                .system(prompt)
                .user(message)
                .call()
                .content();
        try {
            ToolUsagePlan plan = gson.fromJson(cleanJson(response), ToolUsagePlan.class);
            if (plan == null) {
                return buildDefaultPlan();
            }
            if (StringUtils.isBlank(plan.getThinking())) {
                plan.setThinking(plan.getNeedTool() ? "我需要先调用相关工具补充信息，再给你最终结果。" : "我先根据你给的信息整理思路。");
            }
            return plan;
        } catch (Exception e) {
            return buildDefaultPlan();
        }
    }

    /**
     * 默认工具计划
     * @return 默认计划
     */
    private ToolUsagePlan buildDefaultPlan() {
        ToolUsagePlan plan = new ToolUsagePlan();
        plan.setNeedTool(false);
        plan.setThinking("我先根据你已经提供的信息整理思路。");
        return plan;
    }

    /**
     * 无工具直接回答
     * @param message 用户消息
     * @return 最终答复
     */
    private String directAnswerWithoutTool(String message, boolean allowFileTool, boolean allowWebSearchTool) {
        String prompt = buildDirectAnswerPrompt(allowFileTool, allowWebSearchTool);
        return ChatClient.builder(dashscopeChatModel)
                .build()
                .prompt()
                .system(prompt)
                .user(message)
                .call()
                .content();
    }

    /**
     * 生成无工具场景下的回答提示词
     * @param allowFileTool 文件工具开关
     * @param allowWebSearchTool 联网搜索开关
     * @return 提示词
     */
    private String buildDirectAnswerPrompt(boolean allowFileTool, boolean allowWebSearchTool) {
        return """
                你是智能协同助理。
                当前问题不需要调用外部工具，请直接给出高质量最终答复。
                
                要求：
                1. 优先给出清晰、完整、可执行的回答。
                2. 如果是规划类问题，请用步骤或清单组织答案。
                3. 如果是分析类问题，请先提炼重点，再给建议。
                4. 不要提到底层工具、路由、系统流程。
                5. 当前联网搜索：%s；当前文件工具：%s。
                6. 如果用户的问题明显依赖最新外部信息，但联网搜索未开启，请明确说明当前未开启联网搜索，无法核验最新信息，并提示用户开启后再试。
                7. 如果用户需要读取、整理、保存文件，但文件工具未开启或没有可用文件，请明确说明需要先上传文件后再处理。
                """.formatted(allowWebSearchTool ? "已开启" : "未开启", allowFileTool ? "已开启" : "未开启");
    }

    /**
     * 无工具时，直接流式生成最终答复
     * @param message 用户消息
     * @return 最终答复流
     */
    private Flux<String> directAnswerWithoutToolStream(String message, boolean allowFileTool, boolean allowWebSearchTool) {
        String prompt = buildDirectAnswerPrompt(allowFileTool, allowWebSearchTool);
        return ChatClient.builder(dashscopeChatModel)
                .build()
                .prompt()
                .system(prompt)
                .user(message)
                .stream()
                .content();
    }

    /**
     * 运行智能体并持续输出进度
     * @param message 用户消息
     * @param chatId 会话id
     * @param userId 用户id
     * @param decision 路由决策
     * @param sink 响应流
     */
    private void runAgentWithProgress(String message, String chatId, String userId, RouteDecision decision, FluxSink<String> sink, boolean allowFileTool, boolean allowWebSearchTool) {
        LinManus linManus = new LinManus(buildAvailableTools(allowFileTool, allowWebSearchTool), dashscopeChatModel);
        StringBuilder finalAnswerBuilder = new StringBuilder();
        try {
            linManus.runWithProgress(message, event -> {
                String eventType = normalizeAgentEventType(event);
                String content = StringUtils.defaultString(event.getContent()).trim();
                if (StringUtils.isBlank(content)) {
                    return;
                }
                if ("final".equals(eventType)) {
                    finalAnswerBuilder.append(content);
                }
                sink.next(buildEvent(eventType, MODE_AGENT, LABEL_AGENT, decision.getTag(), content, decision.getReason()));
            });
            String finalAnswer = resolveAgentFinalAnswer(finalAnswerBuilder, linManus);
            if (finalAnswerBuilder.length() == 0 && StringUtils.isNotBlank(finalAnswer)) {
                sink.next(buildEvent("final", MODE_AGENT, LABEL_AGENT, decision.getTag(), finalAnswer, decision.getReason()));
            }
            saveAgentConversation(chatId, userId, message, finalAnswer, decision.getTag());
            sink.next(buildEvent("done", MODE_AGENT, LABEL_AGENT, decision.getTag(), "", decision.getReason()));
            sink.complete();
        } catch (Exception e) {
            log.error("统一助手任务执行失败", e);
            sink.next(buildEvent("error", MODE_AGENT, LABEL_AGENT, decision.getTag(), "执行出错：" + e.getMessage(), decision.getReason()));
            sink.complete();
        }
    }

    /**
     * 运行超级智能体并保存会话
     * @param message 用户消息
     * @param chatId 会话id
     * @param userId 用户id
     * @param decision 路由决策
     * @param sink 流式事件
     */
    private void runManusWithProgress(String message, String chatId, String userId, RouteDecision decision, FluxSink<String> sink) {
        LinManus linManus = new LinManus(allTools, dashscopeChatModel);
        StringBuilder finalAnswerBuilder = new StringBuilder();
        try {
            linManus.runWithProgress(message, event -> {
                String eventType = normalizeAgentEventType(event);
                String content = StringUtils.defaultString(event.getContent()).trim();
                if (StringUtils.isBlank(content)) {
                    return;
                }
                if ("final".equals(eventType)) {
                    finalAnswerBuilder.append(content);
                }
                sink.next(buildEvent(eventType, MODE_MANUS, LABEL_MANUS, decision.getTag(), content, decision.getReason()));
            });
            String finalAnswer = resolveAgentFinalAnswer(finalAnswerBuilder, linManus);
            if (finalAnswerBuilder.length() == 0 && StringUtils.isNotBlank(finalAnswer)) {
                sink.next(buildEvent("final", MODE_MANUS, LABEL_MANUS, decision.getTag(), finalAnswer, decision.getReason()));
            }
            saveConversation(chatId, userId, message, finalAnswer, MODE_MANUS, decision.getTag());
            sink.next(buildEvent("done", MODE_MANUS, LABEL_MANUS, decision.getTag(), "", decision.getReason()));
            sink.complete();
        } catch (Exception e) {
            log.error("超级智能体执行失败", e);
            sink.next(buildEvent("error", MODE_MANUS, LABEL_MANUS, decision.getTag(), "执行出错：" + e.getMessage(), decision.getReason()));
            sink.complete();
        }
    }

    /**
     * 构造智能体可用工具集合
     * @param allowFileTool 是否允许文件工具
     * @return 工具数组
     */
    private ToolCallback[] buildAvailableTools(boolean allowFileTool, boolean allowWebSearchTool) {
        if (allowFileTool && allowWebSearchTool) {
            return ToolCallbacks.from(fileOperationTool, webSearchTool, terminateTool);
        }
        if (allowFileTool) {
            return ToolCallbacks.from(fileOperationTool, terminateTool);
        }
        if (allowWebSearchTool) {
            return ToolCallbacks.from(webSearchTool, terminateTool);
        }
        return ToolCallbacks.from(terminateTool);
    }

    /**
     * 构造传给智能体的上下文输入
     * @param message 用户消息
     * @param uploadedFiles 已上传文件
     * @return 带上下文的输入
     */
    private String buildAgentInput(String message, List<String> uploadedFiles, boolean allowWebSearchTool) {
        boolean hasFiles = uploadedFiles != null && !uploadedFiles.isEmpty();
        if (!hasFiles && !allowWebSearchTool) {
            return message;
        }
        StringBuilder builder = new StringBuilder("用户原始需求：").append(message);
        if (allowWebSearchTool) {
            builder.append("\n当前已开启联网搜索，如问题涉及外部资料、最新信息或需要核验事实，请优先调用联网搜索工具。");
        }
        if (hasFiles) {
            builder.append("\n当前可用文件：").append(String.join("、", uploadedFiles))
                    .append("\n如需使用文件工具，请优先围绕这些文件展开。");
        }
        return builder.toString();
    }

    /**
     * 解析智能体最终答复，避免前端遗漏最后一轮无工具回复
     * @param finalAnswerBuilder 流式累积的最终答复
     * @param linManus 智能体实例
     * @return 最终答复
     */
    private String resolveAgentFinalAnswer(StringBuilder finalAnswerBuilder, LinManus linManus) {
        if (finalAnswerBuilder != null && finalAnswerBuilder.length() > 0) {
            return finalAnswerBuilder.toString();
        }
        String recoveredAnswer = extractLatestAssistantAnswer(linManus);
        if (StringUtils.isNotBlank(recoveredAnswer)) {
            log.info("检测到流式最终答复缺失，已从智能体上下文恢复最终答复");
            return recoveredAnswer;
        }
        return "这次任务已经处理完成。";
    }

    /**
     * 从智能体上下文中提取最后一条真实的助手答复
     * @param linManus 智能体实例
     * @return 助手答复
     */
    private String extractLatestAssistantAnswer(LinManus linManus) {
        if (linManus == null || linManus.getMessageList() == null || linManus.getMessageList().isEmpty()) {
            return "";
        }
        List<Message> messageList = linManus.getMessageList();
        for (int i = messageList.size() - 1; i >= 0; i--) {
            Message message = messageList.get(i);
            if (!(message instanceof AssistantMessage assistantMessage)) {
                continue;
            }
            if (assistantMessage.getToolCalls() != null && !assistantMessage.getToolCalls().isEmpty()) {
                continue;
            }
            String text = StringUtils.trimToEmpty(assistantMessage.getText());
            if (StringUtils.isNotBlank(text)) {
                return text;
            }
        }
        return "";
    }

    /**
     * 标准化智能体事件类型
     * @param event 进度事件
     * @return 标准类型
     */
    private String normalizeAgentEventType(AgentProgressEvent event) {
        if (event == null || StringUtils.isBlank(event.getType())) {
            return "result";
        }
        String type = event.getType().trim().toLowerCase();
        if (java.util.Set.of("thinking", "result", "final", "error").contains(type)) {
            return type;
        }
        return "result";
    }

    /**
     * 标准化标签
     * @param tag 标签
     * @param mode 模式
     * @param message 用户消息
     * @return 标准化后的标签
     */
    private String normalizeTag(String tag, String mode, String message) {
        String normalizedTag = StringUtils.trimToEmpty(tag).toLowerCase();
        if (java.util.Set.of(
                TAG_EMOTION_SUPPORT,
                TAG_RELATIONSHIP_GUIDANCE,
                TAG_STRESS_RELIEF,
                TAG_TASK_PLANNING,
                TAG_INFORMATION_LOOKUP,
                TAG_CONTENT_ORGANIZING,
                TAG_GENERAL_ASSISTANCE
        ).contains(normalizedTag)) {
            return normalizedTag;
        }
        return inferTagByMode(message, mode);
    }

    /**
     * 根据模式和消息推断标签
     * @param message 用户消息
     * @param mode 模式
     * @return 标签
     */
    private String inferTagByMode(String message, String mode) {
        String text = StringUtils.trimToEmpty(message);
        if (MODE_EMOTION.equals(mode)) {
            if (containsAny(text, "对象", "恋爱", "分手", "婚姻", "沟通", "关系", "吵架")) {
                return TAG_RELATIONSHIP_GUIDANCE;
            }
            if (containsAny(text, "压力", "焦虑", "紧张", "崩溃", "累", "烦", "失眠")) {
                return TAG_STRESS_RELIEF;
            }
            return TAG_EMOTION_SUPPORT;
        }
        if (containsAny(text, "搜索", "查询", "最新", "资料", "信息", "是什么", "怎么查")) {
            return TAG_INFORMATION_LOOKUP;
        }
        if (containsAny(text, "计划", "安排", "方案", "步骤", "路线", "日程")) {
            return TAG_TASK_PLANNING;
        }
        if (containsAny(text, "整理", "总结", "提纲", "润色", "写", "文案", "输出")) {
            return TAG_CONTENT_ORGANIZING;
        }
        return TAG_GENERAL_ASSISTANCE;
    }

    /**
     * 判断文本是否包含关键词
     * @param text 文本
     * @param keywords 关键词
     * @return 是否包含
     */
    private boolean containsAny(String text, String... keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 保存任务执行对话
     * @param chatId 会话id
     * @param userId 用户id
     * @param userMessage 用户消息
     * @param assistantMessage 助手消息
     */
    private void saveAgentConversation(String chatId, String userId, String userMessage, String assistantMessage, String tag) {
        saveConversation(chatId, userId, userMessage, assistantMessage, MODE_AGENT, tag);
    }

    /**
     * 保存对话记录
     * @param chatId 会话id
     * @param userId 用户id
     * @param userMessage 用户消息
     * @param assistantMessage 助手消息
     * @param mode 对话模式
     * @param tag 对话标签
     */
    private void saveConversation(String chatId, String userId, String userMessage, String assistantMessage, String mode, String tag) {
        if (StringUtils.isAnyBlank(chatId, userId, userMessage)) {
            return;
        }
        Date now = new Date();
        String title = generateTitle(userMessage);
        ChatMemory userChatMemory = new ChatMemory();
        userChatMemory.setConversationId(chatId);
        userChatMemory.setContent(userMessage);
        userChatMemory.setMessageType("USER");
        userChatMemory.setCreateTime(now);
        userChatMemory.setUserId(userId);
        userChatMemory.setMetadata(gson.toJson(java.util.Map.of(
                "userId", userId,
                "title", title,
                "mode", mode,
                "tag", StringUtils.defaultIfBlank(tag, TAG_GENERAL_ASSISTANCE)
        )));

        ChatMemory assistantChatMemory = new ChatMemory();
        assistantChatMemory.setConversationId(chatId);
        assistantChatMemory.setContent(assistantMessage);
        assistantChatMemory.setMessageType("ASSISTANT");
        assistantChatMemory.setCreateTime(new Date(now.getTime() + 1));
        assistantChatMemory.setUserId(userId);
        assistantChatMemory.setMetadata(gson.toJson(java.util.Map.of(
                "userId", userId,
                "title", title,
                "mode", mode,
                "tag", StringUtils.defaultIfBlank(tag, TAG_GENERAL_ASSISTANCE)
        )));

        chatMemoryService.saveBatch(java.util.List.of(userChatMemory, assistantChatMemory));
    }

    /**
     * 生成会话标题
     * @param message 用户消息
     * @return 标题
     */
    private String generateTitle(String message) {
        if (StringUtils.isBlank(message)) {
            return "新对话";
        }
        String normalized = message.replaceAll("\\s+", " ").trim();
        if (normalized.length() <= 18) {
            return normalized;
        }
        return normalized.substring(0, 18);
    }

    @Data
    private static class RouteDecision {
        private String mode;
        private String tag;
        private String reason;
    }

    @Data
    private static class UnifiedAssistantEvent {
        private String type;
        private String mode;
        private String label;
        private String tag;
        private String content;
        private String reason;
    }

    @Data
    private static class ToolUsagePlan {
        private Boolean needTool;
        private String thinking;

        public Boolean getNeedTool() {
            return needTool != null && needTool;
        }
    }
}
