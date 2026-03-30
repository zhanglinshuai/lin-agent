package com.lin.linagent.app;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import com.lin.linagent.agent.model.AgentProgressEvent;
import com.lin.linagent.agent.model.LinManus;
import com.lin.linagent.domain.ChatMemory;
import com.lin.linagent.service.ContentSafetyService;
import com.lin.linagent.service.ConversationInfoService;
import com.lin.linagent.service.KnowledgeBaseService;
import com.lin.linagent.tools.FileOperationTool;
import com.lin.linagent.tools.TerminateTool;
import com.lin.linagent.tools.WebSearchTool;
import com.lin.linagent.service.ChatMemoryService;
import jakarta.annotation.Resource;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.lin.linagent.contant.CommonVariables.FINAL_ANSWER_GUIDANCE;

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
    private static final int MAX_CONTEXT_MESSAGE_COUNT = 40;
    private static final int MAX_DECISION_CONTEXT_MESSAGE_COUNT = 12;
    private static final int MAX_SEARCH_QUERY_CONTEXT_MESSAGE_COUNT = 8;
    private static final int ASSISTANT_METADATA_MAX_BYTES = 60 * 1024;
    private static final int ASSISTANT_REASON_MAX_BYTES = 1024;
    private static final int ASSISTANT_SOURCE_PROMPT_MAX_BYTES = 2048;
    private static final int ASSISTANT_THINKING_MAX_BYTES = 12 * 1024;
    private static final int ASSISTANT_RESULT_MAX_BYTES = 20 * 1024;
    private static final int ASSISTANT_RESULT_FALLBACK_MAX_BYTES = 12 * 1024;
    private static final int ASSISTANT_THINKING_FALLBACK_MAX_BYTES = 6 * 1024;
    private static final int CONVERSATION_TITLE_MAX_LENGTH = 18;
    private static final int TITLE_SOURCE_SNIPPET_MAX_LENGTH = 220;
    private static final String METADATA_TRUNCATION_SUFFIX = "\n\n[内容过长，已截断保存]";

    private final Gson gson = new Gson();

    @Resource
    private EmotionApp emotionApp;

    @Resource
    private ChatModel dashscopeChatModel;

    @Resource
    private ToolCallback[] allTools;

    @Resource
    private ObjectProvider<SyncMcpToolCallbackProvider> syncMcpToolCallbackProvider;

    @Resource
    private ChatMemoryService chatMemoryService;

    @Resource
    private FileOperationTool fileOperationTool;

    @Resource
    private WebSearchTool webSearchTool;

    @Resource
    private TerminateTool terminateTool;

    @Resource
    private KnowledgeBaseService knowledgeBaseService;

    @Resource
    private ContentSafetyService contentSafetyService;

    @Resource
    private ConversationInfoService conversationInfoService;

    /**
     * 统一流式对话入口
     *
     * @param message 用户消息
     * @param chatId 会话id
     * @param userId 用户id
     * @param mode 模式：auto / emotion / agent
     * @return 统一的流式事件
     */
    public Flux<String> doChatByStream(String message, String chatId, String userId, String mode, boolean allowFileTool, boolean allowWebSearchTool, boolean allowKnowledgeBase, List<String> uploadedFiles) {
        ContentSafetyService.SafetyDecision safetyDecision = contentSafetyService.inspectUserMessage(message);
        if (!safetyDecision.isPass()) {
            return buildSafetyFlux(safetyDecision);
        }
        String normalizedMode = normalizeMode(mode);
        List<Message> conversationContext = loadConversationContextMessages(chatId, userId, MAX_CONTEXT_MESSAGE_COUNT);
        if (MODE_EMOTION.equals(normalizedMode)) {
            return buildEmotionFlux(message, chatId, userId, buildDecision(
                    MODE_EMOTION,
                    inferTagByMode(message, MODE_EMOTION),
                    "用户选择了情感陪伴模式"
            ), allowKnowledgeBase);
        }
        if (MODE_AGENT.equals(normalizedMode)) {
            RouteDecision decision = buildDecision(
                    MODE_AGENT,
                    inferTagByMode(message, MODE_AGENT),
                    "用户选择了任务执行模式"
            );
            if (allowWebSearchTool) {
                return buildWebSearchFlux(message, chatId, userId, decision, allowFileTool, uploadedFiles, conversationContext);
            }
            return buildAgentFlux(message, chatId, userId, decision, allowFileTool, allowWebSearchTool, allowKnowledgeBase, uploadedFiles, conversationContext);
        }
        List<Message> decisionContext = sliceRecentMessages(conversationContext, MAX_DECISION_CONTEXT_MESSAGE_COUNT);
        return Mono.fromCallable(() -> classifyMode(message, decisionContext))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMapMany(decision -> {
                    if (MODE_AGENT.equals(decision.getMode())) {
                        if (allowWebSearchTool) {
                            return buildWebSearchFlux(message, chatId, userId, decision, allowFileTool, uploadedFiles, conversationContext);
                        }
                        return buildAgentFlux(message, chatId, userId, decision, allowFileTool, allowWebSearchTool, allowKnowledgeBase, uploadedFiles, conversationContext);
                    }
                    return buildEmotionFlux(message, chatId, userId, decision, allowKnowledgeBase);
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
        ContentSafetyService.SafetyDecision safetyDecision = contentSafetyService.inspectUserMessage(message);
        if (!safetyDecision.isPass()) {
            return buildSafetyFlux(safetyDecision);
        }
        RouteDecision decision = buildDecision(MODE_MANUS, TAG_GENERAL_ASSISTANCE, "用户进入超级智能体模式");
        List<Message> conversationContext = loadConversationContextMessages(chatId, userId, MAX_CONTEXT_MESSAGE_COUNT);
        return Flux.create(sink -> {
            sink.next(buildEvent("route", MODE_MANUS, LABEL_MANUS, decision.getTag(), "", decision.getReason()));
            sink.next(buildEvent("thinking", MODE_MANUS, LABEL_MANUS, decision.getTag(), "我先把你的目标拆成几个清晰步骤，再一步步帮你推进。", decision.getReason()));
            Mono.fromRunnable(() -> runManusWithProgress(message, chatId, userId, decision, sink, conversationContext))
                    .subscribeOn(Schedulers.boundedElastic())
                    .subscribe();
        }, FluxSink.OverflowStrategy.BUFFER);
    }

    private Flux<String> buildEmotionFlux(String message, String chatId, String userId, RouteDecision decision, boolean allowKnowledgeBase) {
        if (!allowKnowledgeBase) {
            log.info("统一助手情感分支未启用知识库，直接调用AI。chatId={}, userId={}, tag={}", chatId, userId, decision.getTag());
            return buildPlainEmotionFlux(message, chatId, userId, decision);
        }
        String initialThinking = "我先看看知识库里有没有更贴近你这类处境的资料，再陪你把最在意的部分慢慢理清。";
        log.info("统一助手情感分支启用知识库优先策略。chatId={}, userId={}, tag={}", chatId, userId, decision.getTag());
        return Mono.fromCallable(() -> knowledgeBaseService.prepareKnowledgeSupport(message))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMapMany(knowledgeSupport -> {
                    StringBuilder answerBuilder = new StringBuilder();
                    long startedAt = System.currentTimeMillis();
                    ConversationRenderState renderState = createRenderState(message, decision.getReason(), initialThinking);
                    renderState.setKnowledgeBaseEnabled(true);
                    renderState.setKnowledgeMatchedCount(knowledgeSupport.getMatchCount());
                    log.info("统一助手情感分支知识库检索完成。chatId={}, userId={}, tag={}, matchedCount={}, fallbackToAi={}",
                            chatId, userId, decision.getTag(), knowledgeSupport.getMatchCount(), !knowledgeSupport.isMatched());
                    String evidenceContent = StringUtils.defaultString(knowledgeSupport.getEvidenceContent());
                    if (StringUtils.isNotBlank(evidenceContent)) {
                        renderState.setResultContent(evidenceContent);
                    }
                    Flux<String> resultFlux = StringUtils.isBlank(knowledgeSupport.getEvidenceContent())
                            ? Flux.empty()
                            : Flux.just(buildEvent("result", MODE_EMOTION, LABEL_EMOTION, decision.getTag(), knowledgeSupport.getEvidenceContent(), decision.getReason()));
                    Flux<String> answerFlux = knowledgeSupport.isMatched()
                            ? emotionApp.doChatByStreamWithKnowledgeSupport(message, chatId, userId, decision.getTag(), knowledgeSupport.getPromptContext())
                            : emotionApp.doChatByStream(message, chatId, userId, decision.getTag());
                    String finalThinking = knowledgeSupport.isMatched()
                            ? initialThinking
                            : "我先查了一下知识库里的相关内容，暂时没有命中更合适的片段，我先基于你现在的情况陪你分析。";
                    renderState.setThinkingContent(finalThinking);
                    return Flux.concat(
                            Flux.just(buildEvent("route", MODE_EMOTION, LABEL_EMOTION, decision.getTag(), "", decision.getReason())),
                            Flux.just(buildEvent("thinking", MODE_EMOTION, LABEL_EMOTION, decision.getTag(), finalThinking, decision.getReason())),
                            resultFlux,
                            answerFlux.doOnNext(answerBuilder::append)
                                    .map(chunk -> buildEvent("final", MODE_EMOTION, LABEL_EMOTION, decision.getTag(), chunk, decision.getReason())),
                            Mono.fromRunnable(() -> {
                                        renderState.setFinalContent(answerBuilder.toString());
                                        renderState.setElapsedSeconds(calculateElapsedSeconds(startedAt));
                                        enrichLatestAssistantMetadata(chatId, userId, MODE_EMOTION, decision.getTag(), renderState, message, answerBuilder.toString());
                                    })
                                    .thenMany(Flux.empty()),
                            Flux.just(buildEvent("done", MODE_EMOTION, LABEL_EMOTION, decision.getTag(), "", decision.getReason()))
                    );
                });
    }

    private Flux<String> buildPlainEmotionFlux(String message, String chatId, String userId, RouteDecision decision) {
        String initialThinking = "我先接住你现在的感受，再陪你把最在意的部分慢慢理清。";
        StringBuilder answerBuilder = new StringBuilder();
        long startedAt = System.currentTimeMillis();
        ConversationRenderState renderState = createRenderState(message, decision.getReason(), initialThinking);
        renderState.setKnowledgeBaseEnabled(false);
        renderState.setKnowledgeMatchedCount(0);
        return Flux.concat(
                Flux.just(buildEvent("route", MODE_EMOTION, LABEL_EMOTION, decision.getTag(), "", decision.getReason())),
                Flux.just(buildEvent("thinking", MODE_EMOTION, LABEL_EMOTION, decision.getTag(), initialThinking, decision.getReason())),
                emotionApp.doChatByStream(message, chatId, userId, decision.getTag())
                        .doOnNext(answerBuilder::append)
                        .map(chunk -> buildEvent("final", MODE_EMOTION, LABEL_EMOTION, decision.getTag(), chunk, decision.getReason())),
                Mono.fromRunnable(() -> {
                            renderState.setFinalContent(answerBuilder.toString());
                            renderState.setElapsedSeconds(calculateElapsedSeconds(startedAt));
                            enrichLatestAssistantMetadata(chatId, userId, MODE_EMOTION, decision.getTag(), renderState, message, answerBuilder.toString());
                        })
                        .thenMany(Flux.empty()),
                Flux.just(buildEvent("done", MODE_EMOTION, LABEL_EMOTION, decision.getTag(), "", decision.getReason()))
        );
    }

    /**
     * 联网搜索优先模式
     * 只要前端显式开启联网搜索，就先稳定执行搜索，再基于搜索结果回答。
     */
    private Flux<String> buildWebSearchFlux(String message, String chatId, String userId, RouteDecision decision, boolean allowFileTool, List<String> uploadedFiles, List<Message> conversationContext) {
        String initialThinking = "我先帮你补充一下相关资料，再把重点整理成清晰可用的答复。";
        return Flux.create(sink -> {
            sink.next(buildEvent("route", MODE_AGENT, LABEL_AGENT, decision.getTag(), "", decision.getReason()));
            sink.next(buildEvent("thinking", MODE_AGENT, LABEL_AGENT, decision.getTag(), initialThinking, decision.getReason()));
            Mono.fromRunnable(() -> runWebSearchWithProgress(
                            message,
                            chatId,
                            userId,
                            decision,
                            initialThinking,
                            uploadedFiles,
                            conversationContext,
                            sink,
                            allowFileTool))
                    .subscribeOn(Schedulers.boundedElastic())
                    .subscribe();
        }, FluxSink.OverflowStrategy.BUFFER);
    }

    private Flux<String> buildAgentFlux(String message, String chatId, String userId, RouteDecision decision, boolean allowFileTool, boolean allowWebSearchTool, boolean allowKnowledgeBase, List<String> uploadedFiles, List<Message> conversationContext) {
        if (allowKnowledgeBase) {
            log.info("统一助手非情感分支跳过知识库检索。chatId={}, userId={}, tag={}", chatId, userId, decision.getTag());
        }
        List<Message> decisionContext = sliceRecentMessages(conversationContext, MAX_DECISION_CONTEXT_MESSAGE_COUNT);
        return Mono.fromCallable(() -> decideToolUsage(message, allowFileTool, allowWebSearchTool, decisionContext))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMapMany(plan -> {
                    if (!plan.getNeedTool()) {
                        return buildDirectAnswerFlux(message, chatId, userId, decision, plan, allowFileTool, allowWebSearchTool, allowKnowledgeBase, uploadedFiles, conversationContext);
                    }
                    return Flux.create(sink -> {
                        sink.next(buildEvent("route", MODE_AGENT, LABEL_AGENT, decision.getTag(), "", decision.getReason()));
                        sink.next(buildEvent("thinking", MODE_AGENT, LABEL_AGENT, decision.getTag(), plan.getThinking(), decision.getReason()));
                        Mono.fromRunnable(() -> runAgentWithProgress(
                                        message,
                                        buildAgentInput(message, uploadedFiles, allowWebSearchTool),
                                        chatId,
                                        userId,
                                        decision,
                                        plan.getThinking(),
                                        uploadedFiles,
                                        conversationContext,
                                        sink,
                                        allowFileTool,
                                        allowWebSearchTool))
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
    private Flux<String> buildDirectAnswerFlux(String message, String chatId, String userId, RouteDecision decision, ToolUsagePlan plan, boolean allowFileTool, boolean allowWebSearchTool, boolean allowKnowledgeBase, List<String> uploadedFiles, List<Message> conversationContext) {
        StringBuilder answerBuilder = new StringBuilder();
        long startedAt = System.currentTimeMillis();
        ConversationRenderState renderState = createRenderState(message, decision.getReason(), plan.getThinking());
        renderState.setWebSearchEnabled(allowWebSearchTool);
        renderState.setKnowledgeBaseEnabled(false);
        renderState.setKnowledgeMatchedCount(0);
        Flux<String> resultFlux = Flux.empty();
        Flux<String> answerFlux = directAnswerWithoutToolStream(
                message,
                allowFileTool,
                allowWebSearchTool,
                conversationContext,
                ""
        )
                .doOnNext(answerBuilder::append)
                .map(chunk -> buildEvent("final", MODE_AGENT, LABEL_AGENT, decision.getTag(), chunk, decision.getReason()));
        return Flux.concat(
                Flux.just(buildEvent("route", MODE_AGENT, LABEL_AGENT, decision.getTag(), "", decision.getReason())),
                Flux.just(buildEvent("thinking", MODE_AGENT, LABEL_AGENT, decision.getTag(), plan.getThinking(), decision.getReason())),
                resultFlux,
                answerFlux,
                Mono.fromRunnable(() -> {
                            renderState.setFinalContent(answerBuilder.toString());
                            renderState.setElapsedSeconds(calculateElapsedSeconds(startedAt));
                            saveAgentConversation(chatId, userId, message, answerBuilder.toString(), decision.getTag(), uploadedFiles, renderState);
                        })
                        .thenMany(Flux.just(buildEvent("done", MODE_AGENT, LABEL_AGENT, decision.getTag(), "", decision.getReason())))
        );
    }

    private RouteDecision classifyMode(String message, List<Message> conversationContext) {
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
                .messages(appendCurrentUserMessage(conversationContext, message))
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

    /**
     * 加载会话上下文消息，供统一助手在下一轮继续参考
     * @param chatId 会话id
     * @param maxMessages 最大消息数
     * @return 最近历史消息
     */
    private List<Message> loadConversationContextMessages(String chatId, String userId, int maxMessages) {
        if (StringUtils.isBlank(chatId) || maxMessages <= 0) {
            return List.of();
        }
        List<ChatMemory> conversation = chatMemoryService.getUserConversation(chatId, userId);
        if (conversation == null || conversation.isEmpty()) {
            return List.of();
        }
        int fromIndex = Math.max(0, conversation.size() - maxMessages);
        List<Message> messages = new ArrayList<>();
        for (ChatMemory chatMemory : conversation.subList(fromIndex, conversation.size())) {
            if (chatMemory == null || StringUtils.isBlank(chatMemory.getContent())) {
                continue;
            }
            if ("USER".equalsIgnoreCase(chatMemory.getMessageType())) {
                messages.add(new UserMessage(chatMemory.getContent()));
                continue;
            }
            if ("ASSISTANT".equalsIgnoreCase(chatMemory.getMessageType())) {
                messages.add(new AssistantMessage(chatMemory.getContent()));
            }
        }
        return messages;
    }

    /**
     * 对历史消息做最近窗口裁剪，避免决策链路读取过多上下文
     * @param messages 原始消息
     * @param maxMessages 最大消息数
     * @return 裁剪后的消息
     */
    private List<Message> sliceRecentMessages(List<Message> messages, int maxMessages) {
        if (messages == null || messages.isEmpty() || maxMessages <= 0) {
            return List.of();
        }
        int fromIndex = Math.max(0, messages.size() - maxMessages);
        return new ArrayList<>(messages.subList(fromIndex, messages.size()));
    }

    /**
     * 在历史上下文后追加当前用户消息
     * @param conversationContext 历史上下文
     * @param message 当前用户消息
     * @return 送给模型的消息列表
     */
    private List<Message> appendCurrentUserMessage(List<Message> conversationContext, String message) {
        List<Message> messages = new ArrayList<>();
        if (conversationContext != null && !conversationContext.isEmpty()) {
            messages.addAll(conversationContext);
        }
        messages.add(new UserMessage(StringUtils.defaultString(message)));
        return messages;
    }

    /**
     * 将当前问题改写为适合联网搜索的独立查询词
     * @param message 当前用户消息
     * @param conversationContext 最近上下文
     * @return 改写后的查询词
     */
    private String buildStandaloneSearchQuery(String message, List<Message> conversationContext) {
        if (StringUtils.isBlank(message)) {
            return "";
        }
        String prompt = """
                你是搜索词改写器。
                请结合最近对话上下文，把用户当前这句话改写成适合联网搜索的一句独立查询词。
                
                要求：
                1. 保留用户真正想查的主题、对象、时间范围、筛选条件和任务目标。
                2. 如果当前问题已经完整，直接返回原问题的规范化版本。
                3. 不要解释，不要列表，不要 JSON，不要引号，只返回一条搜索词。
                4. 不要发散扩写，不要加入用户没提到的新结论。
                """;
        try {
            String response = ChatClient.builder(dashscopeChatModel)
                    .build()
                    .prompt()
                    .system(prompt)
                    .messages(appendCurrentUserMessage(conversationContext, message))
                    .call()
                    .content();
            String searchQuery = StringUtils.normalizeSpace(cleanJson(response));
            return StringUtils.defaultIfBlank(searchQuery, StringUtils.normalizeSpace(message));
        } catch (Exception e) {
            log.warn("搜索词改写失败，回退用户原问题: {}", e.getMessage());
            return StringUtils.normalizeSpace(message);
        }
    }

    /**
     * 执行联网搜索并把结果以进度事件形式回传给前端
     * @param searchQuery 搜索查询词
     * @param message 用户当前消息
     * @param conversationContext 最近上下文
     * @param sink SSE 输出
     * @param decision 路由结果
     * @param renderState 前端回放状态
     * @return 搜索结果
     */
    private String executeWebSearch(String searchQuery, String message, List<Message> conversationContext, FluxSink<String> sink, RouteDecision decision, ConversationRenderState renderState) {
        String rewriteContext = buildSearchRewriteContext(message, conversationContext);
        try {
            WebSearchTool.setRewriteContext(rewriteContext);
            WebSearchTool.setProgressConsumer(content -> {
                if (StringUtils.isBlank(content)) {
                    return;
                }
                renderState.setResultContent(appendStreamChunk(renderState.getResultContent(), content));
                sink.next(buildEvent("result", MODE_AGENT, LABEL_AGENT, decision.getTag(), content, decision.getReason()));
            });
            return webSearchTool.searchWeb(StringUtils.defaultIfBlank(searchQuery, message));
        } finally {
            WebSearchTool.clearProgressConsumer();
            WebSearchTool.clearRewriteContext();
        }
    }

    /**
     * 构造供搜索摘要重写使用的上下文
     * @param message 当前消息
     * @param conversationContext 最近上下文
     * @return 上下文字符串
     */
    private String buildSearchRewriteContext(String message, List<Message> conversationContext) {
        StringBuilder builder = new StringBuilder();
        if (conversationContext != null && !conversationContext.isEmpty()) {
            int fromIndex = Math.max(0, conversationContext.size() - MAX_SEARCH_QUERY_CONTEXT_MESSAGE_COUNT);
            for (Message historyMessage : conversationContext.subList(fromIndex, conversationContext.size())) {
                if (historyMessage instanceof UserMessage userMessage) {
                    builder.append("用户：").append(StringUtils.defaultString(userMessage.getText())).append("\n");
                } else if (historyMessage instanceof AssistantMessage assistantMessage) {
                    builder.append("助手：").append(StringUtils.defaultString(assistantMessage.getText())).append("\n");
                }
            }
        }
        builder.append("当前用户问题：").append(StringUtils.defaultString(message));
        return builder.toString().trim();
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
    private ToolUsagePlan decideToolUsage(String message, boolean allowFileTool, boolean allowWebSearchTool, List<Message> conversationContext) {
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
                  "thinking": "写 1-2 句面向用户的话，说明你接下来会先补哪部分信息、或者会先从哪个重点开始梳理。不要写工具名，不要写调用工具、系统、路由、提示词、模型、JSON、参数等开发者口吻。"
                }
                
                thinking 的表达要求：
                1. 语气要像正在和用户解释你的下一步，而不是写给开发者看的过程说明。
                2. 如果 needTool 为 true，可以说“我先帮你查一下这部分最新信息”或“我先看看你提供的材料”，但不要说“我要调用某个工具”。
                3. 如果 needTool 为 false，可以说“我先把问题拆开，抓住重点再回答你”，不要写内部判断过程。
                4. 不要写工具结果，不要写最终答复内容，只说明当前这一步准备怎么推进。
                """.formatted(allowWebSearchTool ? "可用" : "不可用", allowFileTool ? "可用" : "不可用");
        String response = ChatClient.builder(dashscopeChatModel)
                .build()
                .prompt()
                .system(prompt)
                .messages(appendCurrentUserMessage(conversationContext, message))
                .call()
                .content();
        try {
            ToolUsagePlan plan = gson.fromJson(cleanJson(response), ToolUsagePlan.class);
            if (plan == null) {
                return buildDefaultPlan();
            }
            plan.setThinking(normalizeUserFacingThinking(plan.getThinking(), plan.getNeedTool()));
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
        plan.setThinking(normalizeUserFacingThinking("", false));
        return plan;
    }

    /**
     * 先执行联网搜索，再基于搜索结果给出最终答复
     */
    private void runWebSearchWithProgress(String message, String chatId, String userId, RouteDecision decision, String initialThinking, List<String> uploadedFiles, List<Message> conversationContext, FluxSink<String> sink, boolean allowFileTool) {
        long startedAt = System.currentTimeMillis();
        ConversationRenderState renderState = createRenderState(message, decision.getReason(), initialThinking);
        renderState.setWebSearchEnabled(true);
        try {
            String searchQuery = buildStandaloneSearchQuery(message, conversationContext);
            String searchResult = executeWebSearch(searchQuery, message, conversationContext, sink, decision, renderState);
            if (StringUtils.isBlank(renderState.getResultContent()) && StringUtils.isNotBlank(searchResult)) {
                renderState.setResultContent(searchResult);
                sink.next(buildEvent("result", MODE_AGENT, LABEL_AGENT, decision.getTag(), searchResult, decision.getReason()));
            }
            if (allowFileTool && uploadedFiles != null && !uploadedFiles.isEmpty()) {
                String searchAugmentedAgentInput = buildSearchAugmentedAgentInput(message, uploadedFiles, searchQuery, searchResult);
                runAgentWithProgress(
                        message,
                        searchAugmentedAgentInput,
                        chatId,
                        userId,
                        decision,
                        initialThinking,
                        uploadedFiles,
                        conversationContext,
                        sink,
                        true,
                        false
                );
                return;
            }
            StringBuilder finalAnswerBuilder = new StringBuilder();
            for (String chunk : directAnswerWithSearchResultStream(message, searchQuery, searchResult, conversationContext).toIterable()) {
                if (chunk == null || chunk.isEmpty()) {
                    continue;
                }
                finalAnswerBuilder.append(chunk);
                renderState.setFinalContent(appendStreamChunk(renderState.getFinalContent(), chunk));
                sink.next(buildEvent("final", MODE_AGENT, LABEL_AGENT, decision.getTag(), chunk, decision.getReason()));
            }
            String finalAnswer = StringUtils.defaultIfBlank(finalAnswerBuilder.toString(), "我已经帮你补充了联网资料，但当前还没有生成可展示的最终答复。");
            renderState.setElapsedSeconds(calculateElapsedSeconds(startedAt));
            saveAgentConversation(chatId, userId, message, finalAnswer, decision.getTag(), uploadedFiles, renderState);
            sink.next(buildEvent("done", MODE_AGENT, LABEL_AGENT, decision.getTag(), "", decision.getReason()));
            sink.complete();
        } catch (Exception e) {
            log.error("联网搜索优先链路执行失败", e);
            sink.next(buildEvent("error", MODE_AGENT, LABEL_AGENT, decision.getTag(), "执行出错：" + e.getMessage(), decision.getReason()));
            sink.complete();
        }
    }

    /**
     * 无工具直接回答
     * @param message 用户消息
     * @return 最终答复
     */
    private String directAnswerWithoutTool(String message, boolean allowFileTool, boolean allowWebSearchTool, List<Message> conversationContext, String knowledgePromptContext) {
        String prompt = buildDirectAnswerPrompt(allowFileTool, allowWebSearchTool, knowledgePromptContext);
        return ChatClient.builder(dashscopeChatModel)
                .build()
                .prompt()
                .system(prompt)
                .messages(appendCurrentUserMessage(conversationContext, message))
                .call()
                .content();
    }

    /**
     * 生成无工具场景下的回答提示词
     * @param allowFileTool 文件工具开关
     * @param allowWebSearchTool 联网搜索开关
     * @return 提示词
     */
    private String buildDirectAnswerPrompt(boolean allowFileTool, boolean allowWebSearchTool, String knowledgePromptContext) {
        String basePrompt = """
                你是智能协同助理。
                当前问题不需要调用外部工具，请直接给出高质量最终答复。
                
                要求：
                1. 回答要像在直接帮助用户解决眼前的问题，而不是在写说明文档。
                2. 先给用户最需要的判断、结论或建议，再展开解释。
                3. 如果是规划类问题，请把建议整理成用户可以直接照着做的步骤或清单。
                4. 如果是分析类问题，请先提炼重点，再给用户真正用得上的建议。
                5. 不要泛泛而谈，要尽量结合当前问题里的真实语境、约束和用户处境来回答。
                6. 如果存在多种思路，请明确说明你更推荐哪一种，以及为什么更推荐。
                7. 不要提到底层工具、路由、系统流程。
                8. 在不改变统一 Markdown 结构的前提下，回答默认要写得更充分一点，不要只给很短的骨架。
                9. 当前联网搜索：%s；当前文件工具：%s。
                10. 如果用户的问题明显依赖最新外部信息，但联网搜索未开启，请明确说明当前未开启联网搜索，无法核验最新信息，并提示用户开启后再试。
                11. 如果用户需要读取、整理、保存文件，但文件工具未开启或没有可用文件，请明确说明需要先上传文件后再处理。
                """.formatted(allowWebSearchTool ? "已开启" : "未开启", allowFileTool ? "已开启" : "未开启");
        if (StringUtils.isNotBlank(knowledgePromptContext)) {
            basePrompt += "\n\n" + knowledgePromptContext + "\n\n补充要求：\n1. 优先基于上面的知识库资料组织回答。\n2. 如果资料不足，也要明确指出不足点，不要把没有依据的内容说得过于确定。";
        }
        return basePrompt + FINAL_ANSWER_GUIDANCE;
    }

    /**
     * 生成基于联网搜索结果的最终答复提示词
     * @param searchQuery 搜索查询词
     * @param searchResult 搜索结果
     * @return 提示词
     */
    private String buildSearchGroundedAnswerPrompt(String searchQuery, String searchResult) {
        return """
                你是智能协同助理。
                当前已经执行过联网搜索，请直接基于下面这些联网资料回答用户问题。
                
                搜索查询词：
                %s
                
                联网搜索资料：
                %s
                
                要求：
                1. 优先基于上面的资料整理结论、推荐、对比和限制条件，不要跳过这些资料直接凭记忆作答。
                2. 如果资料里已经足够支撑回答，就直接形成最终答复；如果资料不足，也要明确指出不足点。
                3. 不要复述搜索过程，不要写“我调用了搜索工具”之类的话。
                """.formatted(StringUtils.defaultIfBlank(searchQuery, "未生成搜索词"), StringUtils.defaultString(searchResult))
                + FINAL_ANSWER_GUIDANCE;
    }

    /**
     * 无工具时，直接流式生成最终答复
     * @param message 用户消息
     * @return 最终答复流
     */
    private Flux<String> directAnswerWithoutToolStream(String message, boolean allowFileTool, boolean allowWebSearchTool, List<Message> conversationContext, String knowledgePromptContext) {
        String prompt = buildDirectAnswerPrompt(allowFileTool, allowWebSearchTool, knowledgePromptContext);
        return ChatClient.builder(dashscopeChatModel)
                .build()
                .prompt()
                .system(prompt)
                .messages(appendCurrentUserMessage(conversationContext, message))
                .stream()
                .content();
    }

    /**
     * 基于联网搜索结果直接生成最终答复
     * @param message 用户消息
     * @param searchQuery 搜索查询词
     * @param searchResult 搜索结果
     * @param conversationContext 历史上下文
     * @return 最终答复流
     */
    private Flux<String> directAnswerWithSearchResultStream(String message, String searchQuery, String searchResult, List<Message> conversationContext) {
        String prompt = buildSearchGroundedAnswerPrompt(searchQuery, searchResult);
        return ChatClient.builder(dashscopeChatModel)
                .build()
                .prompt()
                .system(prompt)
                .messages(appendCurrentUserMessage(conversationContext, message))
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
    private void runAgentWithProgress(String originalMessage, String agentInput, String chatId, String userId, RouteDecision decision, String initialThinking, List<String> uploadedFiles, List<Message> conversationContext, FluxSink<String> sink, boolean allowFileTool, boolean allowWebSearchTool) {
        LinManus linManus = new LinManus(buildAvailableTools(allowFileTool, allowWebSearchTool), dashscopeChatModel);
        linManus.setMessageList(new ArrayList<>(conversationContext));
        StringBuilder finalAnswerBuilder = new StringBuilder();
        long startedAt = System.currentTimeMillis();
        ConversationRenderState renderState = createRenderState(originalMessage, decision.getReason(), initialThinking);
        renderState.setWebSearchEnabled(allowWebSearchTool);
        try {
            linManus.runWithProgress(agentInput, event -> {
                String eventType = normalizeAgentEventType(event);
                String content = StringUtils.defaultString(event.getContent());
                if (content.isEmpty()) {
                    return;
                }
                if ("thinking".equals(eventType)) {
                    renderState.setThinkingContent(appendDisplayBlock(renderState.getThinkingContent(), content));
                } else if ("result".equals(eventType)) {
                    renderState.setResultContent(appendStreamChunk(renderState.getResultContent(), content));
                } else if ("final".equals(eventType)) {
                    finalAnswerBuilder.append(content);
                    renderState.setFinalContent(appendStreamChunk(renderState.getFinalContent(), content));
                }
                sink.next(buildEvent(eventType, MODE_AGENT, LABEL_AGENT, decision.getTag(), content, decision.getReason()));
            });
            String finalAnswer = resolveAgentFinalAnswer(finalAnswerBuilder, linManus);
            if (finalAnswerBuilder.length() == 0 && StringUtils.isNotBlank(finalAnswer)) {
                sink.next(buildEvent("final", MODE_AGENT, LABEL_AGENT, decision.getTag(), finalAnswer, decision.getReason()));
            }
            if (StringUtils.isBlank(renderState.getFinalContent())) {
                renderState.setFinalContent(finalAnswer);
            }
            renderState.setElapsedSeconds(calculateElapsedSeconds(startedAt));
            saveAgentConversation(chatId, userId, originalMessage, finalAnswer, decision.getTag(), uploadedFiles, renderState);
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
    private void runManusWithProgress(String message, String chatId, String userId, RouteDecision decision, FluxSink<String> sink, List<Message> conversationContext) {
        LinManus linManus = new LinManus(allTools, dashscopeChatModel);
        linManus.setMessageList(new ArrayList<>(conversationContext));
        StringBuilder finalAnswerBuilder = new StringBuilder();
        long startedAt = System.currentTimeMillis();
        ConversationRenderState renderState = createRenderState(message, decision.getReason(), "我先把你的目标拆成几个清晰步骤，再一步步帮你推进。");
        try {
            linManus.runWithProgress(message, event -> {
                String eventType = normalizeAgentEventType(event);
                String content = StringUtils.defaultString(event.getContent());
                if (content.isEmpty()) {
                    return;
                }
                if ("thinking".equals(eventType)) {
                    renderState.setThinkingContent(appendDisplayBlock(renderState.getThinkingContent(), content));
                } else if ("result".equals(eventType)) {
                    renderState.setResultContent(appendStreamChunk(renderState.getResultContent(), content));
                } else if ("final".equals(eventType)) {
                    finalAnswerBuilder.append(content);
                    renderState.setFinalContent(appendStreamChunk(renderState.getFinalContent(), content));
                }
                sink.next(buildEvent(eventType, MODE_MANUS, LABEL_MANUS, decision.getTag(), content, decision.getReason()));
            });
            String finalAnswer = resolveAgentFinalAnswer(finalAnswerBuilder, linManus);
            if (finalAnswerBuilder.length() == 0 && StringUtils.isNotBlank(finalAnswer)) {
                sink.next(buildEvent("final", MODE_MANUS, LABEL_MANUS, decision.getTag(), finalAnswer, decision.getReason()));
            }
            if (StringUtils.isBlank(renderState.getFinalContent())) {
                renderState.setFinalContent(finalAnswer);
            }
            renderState.setElapsedSeconds(calculateElapsedSeconds(startedAt));
            saveConversation(chatId, userId, message, finalAnswer, MODE_MANUS, decision.getTag(), List.of(), renderState);
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
        List<ToolCallback> callbacks = new ArrayList<>();
        if (allowFileTool && allowWebSearchTool) {
            callbacks.addAll(java.util.Arrays.asList(ToolCallbacks.from(fileOperationTool, webSearchTool, terminateTool)));
        } else if (allowFileTool) {
            callbacks.addAll(java.util.Arrays.asList(ToolCallbacks.from(fileOperationTool, terminateTool)));
        } else if (allowWebSearchTool) {
            callbacks.addAll(java.util.Arrays.asList(ToolCallbacks.from(webSearchTool, terminateTool)));
        } else {
            callbacks.addAll(java.util.Arrays.asList(ToolCallbacks.from(terminateTool)));
        }
        SyncMcpToolCallbackProvider provider = syncMcpToolCallbackProvider.getIfAvailable();
        if (provider != null) {
            ToolCallback[] mcpTools = provider.getToolCallbacks();
            if (mcpTools.length > 0) {
                callbacks.addAll(java.util.Arrays.asList(mcpTools));
            }
        }
        return callbacks.toArray(new ToolCallback[0]);
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
     * 构造已补充联网搜索结果后的智能体输入
     * @param message 用户消息
     * @param uploadedFiles 已上传文件
     * @param searchQuery 搜索查询词
     * @param searchResult 搜索结果
     * @return 增强后的输入
     */
    private String buildSearchAugmentedAgentInput(String message, List<String> uploadedFiles, String searchQuery, String searchResult) {
        StringBuilder builder = new StringBuilder("用户原始需求：").append(StringUtils.defaultString(message));
        builder.append("\n当前已完成联网搜索，请优先基于下面的资料继续处理用户请求。");
        if (StringUtils.isNotBlank(searchQuery)) {
            builder.append("\n搜索查询词：").append(searchQuery);
        }
        if (StringUtils.isNotBlank(searchResult)) {
            builder.append("\n联网搜索资料：\n").append(searchResult);
        }
        if (uploadedFiles != null && !uploadedFiles.isEmpty()) {
            builder.append("\n当前可用文件：").append(String.join("、", uploadedFiles))
                    .append("\n如果确实需要读取或整理文件，再继续使用文件工具；否则请直接整理成最终答复。");
        } else {
            builder.append("\n如果这些资料已经足够，请直接整理成最终答复。");
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
    private void saveAgentConversation(String chatId, String userId, String userMessage, String assistantMessage, String tag, List<String> uploadedFiles, ConversationRenderState renderState) {
        saveConversation(chatId, userId, userMessage, assistantMessage, MODE_AGENT, tag, uploadedFiles, renderState);
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
    private void saveConversation(String chatId, String userId, String userMessage, String assistantMessage, String mode, String tag, List<String> uploadedFiles, ConversationRenderState renderState) {
        if (StringUtils.isAnyBlank(chatId, userId, userMessage)) {
            return;
        }
        Date now = new Date();
        String title = resolveConversationTitle(chatId, userId, userMessage, assistantMessage);
        ChatMemory userChatMemory = new ChatMemory();
        userChatMemory.setConversationId(chatId);
        userChatMemory.setContent(userMessage);
        userChatMemory.setMessageType("USER");
        userChatMemory.setCreateTime(now);
        userChatMemory.setUserId(userId);
        userChatMemory.setMetadata(buildUserMetadata(userId, title, mode, tag, uploadedFiles));

        ChatMemory assistantChatMemory = new ChatMemory();
        assistantChatMemory.setConversationId(chatId);
        assistantChatMemory.setContent(StringUtils.defaultString(assistantMessage));
        assistantChatMemory.setMessageType("ASSISTANT");
        assistantChatMemory.setCreateTime(new Date(now.getTime() + 1));
        assistantChatMemory.setUserId(userId);
        assistantChatMemory.setMetadata(buildAssistantMetadata(userId, title, mode, tag, renderState));

        chatMemoryService.saveBatch(java.util.List.of(userChatMemory, assistantChatMemory));
        conversationInfoService.syncConversation(userId, chatId);
    }

    /**
     * 创建用于前端回放的渲染状态
     * @param sourcePrompt 原始问题
     * @param reason 路由原因
     * @param thinkingContent 初始思考
     * @return 渲染状态
     */
    private ConversationRenderState createRenderState(String sourcePrompt, String reason, String thinkingContent) {
        ConversationRenderState renderState = new ConversationRenderState();
        renderState.setSourcePrompt(StringUtils.defaultString(sourcePrompt));
        renderState.setReason(StringUtils.defaultString(reason));
        renderState.setThinkingContent(StringUtils.defaultString(thinkingContent));
        return renderState;
    }

    /**
     * 计算执行用时（秒）
     * @param startedAt 开始时间
     * @return 秒数
     */
    private long calculateElapsedSeconds(long startedAt) {
        long elapsedMillis = Math.max(0L, System.currentTimeMillis() - startedAt);
        return Math.max(1L, (elapsedMillis + 999L) / 1000L);
    }

    /**
     * 拼接展示块文本
     * @param current 当前内容
     * @param next 新内容
     * @return 拼接结果
     */
    private String appendDisplayBlock(String current, String next) {
        if (StringUtils.isBlank(next)) {
            return StringUtils.defaultString(current);
        }
        if (StringUtils.isBlank(current)) {
            return next;
        }
        return current + "\n\n" + next;
    }

    /**
     * 拼接流式分片
     * @param current 当前内容
     * @param next 新分片
     * @return 拼接结果
     */
    private String appendStreamChunk(String current, String next) {
        return StringUtils.defaultString(current) + StringUtils.defaultString(next);
    }

    /**
     * 将思考过程文案标准化为面向用户的自然表达
     * @param thinking 原始文案
     * @param needTool 是否需要补充外部信息或文件内容
     * @return 标准化后的文案
     */
    private String normalizeUserFacingThinking(String thinking, boolean needTool) {
        String text = StringUtils.normalizeSpace(StringUtils.defaultString(thinking));
        if (StringUtils.isBlank(text)) {
            return needTool ? "我先补齐这一步需要的关键信息，再把重点整理清楚告诉你。" : "我先把你的问题拆开，抓住重点后再继续回答你。";
        }
        if (containsDeveloperFacingWords(text)) {
            return needTool ? "我先补齐这一步需要的关键信息，再把重点整理清楚告诉你。" : "我先把你的问题拆开，抓住重点后再继续回答你。";
        }
        return text;
    }

    /**
     * 判断文案里是否混入明显的开发者口吻
     * @param text 文案
     * @return 是否命中
     */
    private boolean containsDeveloperFacingWords(String text) {
        return StringUtils.containsAny(text,
                "工具", "调用", "路由", "提示词", "system", "prompt", "模型", "JSON", "needTool", "参数", "接口", "developer", "系统");
    }

    /**
     * 构建用户消息元数据
     * @param userId 用户id
     * @param title 标题
     * @param mode 模式
     * @param tag 标签
     * @param uploadedFiles 已上传文件
     * @return JSON 元数据
     */
    private String buildUserMetadata(String userId, String title, String mode, String tag, List<String> uploadedFiles) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("userId", userId);
        metadata.put("title", title);
        metadata.put("mode", mode);
        metadata.put("tag", StringUtils.defaultIfBlank(tag, TAG_GENERAL_ASSISTANCE));
        if (uploadedFiles != null && !uploadedFiles.isEmpty()) {
            metadata.put("uploadedFiles", uploadedFiles);
        }
        return gson.toJson(metadata);
    }

    /**
     * 构建助手消息元数据
     * @param userId 用户id
     * @param title 标题
     * @param mode 模式
     * @param tag 标签
     * @param renderState 渲染状态
     * @return JSON 元数据
     */
    private String buildAssistantMetadata(String userId, String title, String mode, String tag, ConversationRenderState renderState) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("userId", userId);
        metadata.put("title", title);
        metadata.put("mode", mode);
        metadata.put("tag", StringUtils.defaultIfBlank(tag, TAG_GENERAL_ASSISTANCE));
        if (renderState == null) {
            return gson.toJson(metadata);
        }
        String thinkingContent = trimMetadataText(renderState.getThinkingContent(), ASSISTANT_THINKING_MAX_BYTES);
        if (StringUtils.isNotBlank(thinkingContent)) {
            metadata.put("thinkingContent", thinkingContent);
        }
        String resultContent = trimMetadataText(renderState.getResultContent(), ASSISTANT_RESULT_MAX_BYTES);
        if (StringUtils.isNotBlank(resultContent)) {
            metadata.put("resultContent", resultContent);
        }
        String sourcePrompt = trimMetadataText(renderState.getSourcePrompt(), ASSISTANT_SOURCE_PROMPT_MAX_BYTES);
        if (StringUtils.isNotBlank(sourcePrompt)) {
            metadata.put("sourcePrompt", sourcePrompt);
        }
        String reason = trimMetadataText(renderState.getReason(), ASSISTANT_REASON_MAX_BYTES);
        if (StringUtils.isNotBlank(reason)) {
            metadata.put("reason", reason);
        }
        if (renderState.getWebSearchEnabled() != null) {
            metadata.put("webSearchEnabled", renderState.getWebSearchEnabled());
        }
        if (renderState.getKnowledgeBaseEnabled() != null) {
            metadata.put("knowledgeBaseEnabled", renderState.getKnowledgeBaseEnabled());
        }
        if (renderState.getKnowledgeMatchedCount() != null) {
            metadata.put("knowledgeMatchedCount", renderState.getKnowledgeMatchedCount());
        }
        if (renderState.getElapsedSeconds() != null && renderState.getElapsedSeconds() > 0) {
            metadata.put("elapsedSeconds", renderState.getElapsedSeconds());
        }
        String metadataJson = gson.toJson(metadata);
        if (getUtf8Length(metadataJson) <= ASSISTANT_METADATA_MAX_BYTES) {
            return metadataJson;
        }
        metadata.remove("sourcePrompt");
        metadataJson = gson.toJson(metadata);
        if (getUtf8Length(metadataJson) <= ASSISTANT_METADATA_MAX_BYTES) {
            return metadataJson;
        }
        if (metadata.containsKey("resultContent")) {
            metadata.put("resultContent", trimMetadataText(String.valueOf(metadata.get("resultContent")), ASSISTANT_RESULT_FALLBACK_MAX_BYTES));
        }
        metadataJson = gson.toJson(metadata);
        if (getUtf8Length(metadataJson) <= ASSISTANT_METADATA_MAX_BYTES) {
            return metadataJson;
        }
        if (metadata.containsKey("thinkingContent")) {
            metadata.put("thinkingContent", trimMetadataText(String.valueOf(metadata.get("thinkingContent")), ASSISTANT_THINKING_FALLBACK_MAX_BYTES));
        }
        metadata.remove("reason");
        return gson.toJson(metadata);
    }

    /**
     * 按 UTF-8 字节数裁剪元数据文本，避免数据库 text 字段被撑爆
     * @param text 原始文本
     * @param maxBytes 最大字节数
     * @return 裁剪后的文本
     */
    private String trimMetadataText(String text, int maxBytes) {
        String normalized = StringUtils.defaultString(text).trim();
        if (StringUtils.isBlank(normalized) || maxBytes <= 0) {
            return "";
        }
        if (getUtf8Length(normalized) <= maxBytes) {
            return normalized;
        }
        int suffixBytes = getUtf8Length(METADATA_TRUNCATION_SUFFIX);
        if (suffixBytes >= maxBytes) {
            return "";
        }
        int allowedBytes = maxBytes - suffixBytes;
        int low = 0;
        int high = normalized.length();
        while (low < high) {
            int mid = (low + high + 1) / 2;
            String candidate = normalized.substring(0, mid);
            if (getUtf8Length(candidate) <= allowedBytes) {
                low = mid;
            } else {
                high = mid - 1;
            }
        }
        return normalized.substring(0, low).trim() + METADATA_TRUNCATION_SUFFIX;
    }

    /**
     * 计算字符串的 UTF-8 字节长度
     * @param text 文本
     * @return 字节长度
     */
    private int getUtf8Length(String text) {
        return StringUtils.defaultString(text).getBytes(StandardCharsets.UTF_8).length;
    }

    /**
     * 解析当前会话标题
     * 首次对话时基于首轮问答生成标题；后续轮次复用已有标题
     * @param conversationId 会话id
     * @param userMessage 用户消息
     * @param assistantMessage 助手首轮答复
     * @return 标题
     */
    private String resolveConversationTitle(String conversationId, String userId, String userMessage, String assistantMessage) {
        String existingTitle = findExistingConversationTitle(conversationId, userId);
        if (StringUtils.isNotBlank(existingTitle)) {
            return existingTitle;
        }
        String generatedTitle = generateTitleFromFirstDialogue(userMessage, assistantMessage);
        if (StringUtils.isBlank(generatedTitle)) {
            generatedTitle = generateTitle(userMessage);
        }
        return sanitizeConversationTitle(generatedTitle);
    }

    /**
     * 查找已存在的会话标题
     * @param conversationId 会话id
     * @return 已有标题
     */
    private String findExistingConversationTitle(String conversationId, String userId) {
        if (StringUtils.isBlank(conversationId)) {
            return "";
        }
        List<ChatMemory> conversation = chatMemoryService.getUserConversation(conversationId, userId);
        if (conversation == null || conversation.isEmpty()) {
            return "";
        }
        for (ChatMemory chatMemory : conversation) {
            String title = extractMetadataText(chatMemory.getMetadata(), "title");
            if (StringUtils.isNotBlank(title)) {
                return sanitizeConversationTitle(title);
            }
        }
        return "";
    }

    /**
     * 基于当前会话第一次问答生成标题
     * @param userMessage 用户首条消息
     * @param assistantMessage 助手首轮答复
     * @return 标题
     */
    private String generateTitleFromFirstDialogue(String userMessage, String assistantMessage) {
        if (StringUtils.isAllBlank(userMessage, assistantMessage)) {
            return "";
        }
        try {
            String prompt = """
                    你是会话标题生成器。
                    请根据当前会话的第一次问答，总结一个自然、具体、简洁的标题。
                    
                    要求：
                    1. 只返回标题本身，不要解释，不要引号，不要换行，不要列表。
                    2. 标题要概括用户真正想解决的主题，不要写成“问题咨询”“继续聊聊”“一些建议”这种空泛表述。
                    3. 表达要像真实聊天列表里的标题，自然一些，不要套模板。
                    4. 标题长度控制在 %d 个中文字符以内。
                    
                    用户第一次提问：
                    %s
                    
                    助手第一次答复摘要：
                    %s
                    """.formatted(
                    CONVERSATION_TITLE_MAX_LENGTH,
                    trimTitleSourceText(userMessage),
                    trimTitleSourceText(assistantMessage)
            );
            String response = ChatClient.builder(dashscopeChatModel)
                    .build()
                    .prompt()
                    .user(prompt)
                    .call()
                    .content();
            return sanitizeConversationTitle(cleanJson(response));
        } catch (Exception e) {
            log.warn("会话标题生成失败，回退截断标题: {}", e.getMessage());
            return "";
        }
    }

    /**
     * 提取元数据中的文本字段
     * @param metadataJson 元数据 JSON
     * @param key 字段名
     * @return 字段值
     */
    private String extractMetadataText(String metadataJson, String key) {
        if (StringUtils.isAnyBlank(metadataJson, key)) {
            return "";
        }
        try {
            JsonObject jsonObject = JsonParser.parseString(metadataJson).getAsJsonObject();
            if (!jsonObject.has(key) || jsonObject.get(key).isJsonNull()) {
                return "";
            }
            return StringUtils.trimToEmpty(jsonObject.get(key).getAsString());
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * 补写情感分支助手消息元数据，确保刷新页面后仍能回放思考区域
     * @param chatId 会话id
     * @param userId 用户id
     * @param mode 模式
     * @param tag 标签
     * @param renderState 渲染状态
     * @param userMessage 用户消息
     * @param assistantMessage 助手答复
     */
    private void enrichLatestAssistantMetadata(String chatId, String userId, String mode, String tag, ConversationRenderState renderState, String userMessage, String assistantMessage) {
        if (StringUtils.isAnyBlank(chatId, userId) || renderState == null) {
            return;
        }
        QueryWrapper<ChatMemory> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("conversation_id", chatId);
        queryWrapper.eq("user_id", userId);
        queryWrapper.orderByDesc("create_time");
        queryWrapper.orderByDesc("id");
        List<ChatMemory> conversationRows = chatMemoryService.list(queryWrapper);
        if (conversationRows == null || conversationRows.isEmpty()) {
            return;
        }
        ChatMemory latestAssistant = null;
        String title = "";
        for (ChatMemory row : conversationRows) {
            if (StringUtils.isBlank(title)) {
                title = extractMetadataText(row.getMetadata(), "title");
            }
            if (latestAssistant == null && StringUtils.equalsIgnoreCase("ASSISTANT", row.getMessageType())) {
                latestAssistant = row;
            }
            if (latestAssistant != null && StringUtils.isNotBlank(title)) {
                break;
            }
        }
        if (latestAssistant == null) {
            return;
        }
        String effectiveTitle = StringUtils.defaultIfBlank(title, resolveConversationTitle(chatId, userId, userMessage, assistantMessage));
        latestAssistant.setMetadata(buildAssistantMetadata(userId, effectiveTitle, mode, tag, renderState));
        chatMemoryService.updateById(latestAssistant);
    }

    /**
     * 裁剪标题生成时使用的上下文片段
     * @param text 原始文本
     * @return 裁剪后的文本
     */
    private String trimTitleSourceText(String text) {
        String normalized = StringUtils.normalizeSpace(StringUtils.defaultString(text));
        if (StringUtils.isBlank(normalized)) {
            return "";
        }
        if (normalized.length() <= TITLE_SOURCE_SNIPPET_MAX_LENGTH) {
            return normalized;
        }
        return normalized.substring(0, TITLE_SOURCE_SNIPPET_MAX_LENGTH).trim() + "…";
    }

    /**
     * 兜底生成会话标题
     * @param message 用户消息
     * @return 标题
     */
    private String generateTitle(String message) {
        if (StringUtils.isBlank(message)) {
            return "新对话";
        }
        String normalized = message.replaceAll("\\s+", " ").trim();
        if (normalized.length() <= CONVERSATION_TITLE_MAX_LENGTH) {
            return normalized;
        }
        return normalized.substring(0, CONVERSATION_TITLE_MAX_LENGTH);
    }

    /**
     * 规范化最终标题，确保适合会话列表展示
     * @param rawTitle 原始标题
     * @return 清洗后的标题
     */
    private String sanitizeConversationTitle(String rawTitle) {
        String title = StringUtils.defaultString(rawTitle).trim();
        if (StringUtils.isBlank(title)) {
            return "新对话";
        }
        title = cleanJson(title)
                .replaceAll("[\\r\\n\\t]+", " ")
                .replaceFirst("^(标题|会话标题)\\s*[:：]\\s*", "")
                .replaceAll("\\s{2,}", " ")
                .trim();
        title = StringUtils.strip(title, "\"'“”‘’[]【】");
        title = title.replaceAll("[。！？；;：:，,]+$", "").trim();
        if (StringUtils.isBlank(title)) {
            return "新对话";
        }
        if (title.length() <= CONVERSATION_TITLE_MAX_LENGTH) {
            return title;
        }
        return title.substring(0, CONVERSATION_TITLE_MAX_LENGTH).trim();
    }

    /**
     * 构建安全保护返回流
     * @param safetyDecision 安全判断结果
     * @return 流式事件
     */
    private Flux<String> buildSafetyFlux(ContentSafetyService.SafetyDecision safetyDecision) {
        String tag = safetyDecision.isCrisis() ? TAG_STRESS_RELIEF : TAG_GENERAL_ASSISTANCE;
        String thinking = safetyDecision.isCrisis()
                ? "我先优先处理当前的安全风险，再给你最稳妥的建议。"
                : "当前内容触发了安全保护，我先把可继续的边界说明清楚。";
        String reason = StringUtils.defaultIfBlank(safetyDecision.getReason(), "命中安全保护策略");
        return Flux.just(
                buildEvent("route", MODE_EMOTION, LABEL_EMOTION, tag, "", reason),
                buildEvent("thinking", MODE_EMOTION, LABEL_EMOTION, tag, thinking, reason),
                buildEvent("final", MODE_EMOTION, LABEL_EMOTION, tag, StringUtils.defaultString(safetyDecision.getUserMessage()), reason),
                buildEvent("done", MODE_EMOTION, LABEL_EMOTION, tag, "", reason)
        );
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

    @Data
    private static class ConversationRenderState {
        private String thinkingContent;
        private String resultContent;
        private String finalContent;
        private String reason;
        private String sourcePrompt;
        private Boolean webSearchEnabled;
        private Boolean knowledgeBaseEnabled;
        private Integer knowledgeMatchedCount;
        private Long elapsedSeconds;
    }
}
