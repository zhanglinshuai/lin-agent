package com.lin.linagent.app;

import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversation;
import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversationParam;
import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversationResult;
import com.alibaba.dashscope.common.MultiModalMessage;
import com.alibaba.dashscope.common.Role;
import com.alibaba.dashscope.exception.NoApiKeyException;
import com.alibaba.dashscope.exception.UploadFileException;
import com.lin.linagent.advisor.MyLoggerAdvisor;
import com.lin.linagent.chatMemory.CustomJdbcChatMemoryRepository;
import com.lin.linagent.chatMemory.dialect.CustomMysqlJdbcChatMemoryRepositoryDialect;
import com.lin.linagent.contant.CommonVariables;
import com.lin.linagent.domain.dto.EmotionReportVO;
import com.lin.linagent.multirecall.MultiRecall;
import com.lin.linagent.multirecall.RecallResultMerger;
import com.lin.linagent.service.ConversationInfoService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.ai.rag.generation.augmentation.ContextualQueryAugmenter;
import org.springframework.ai.rag.preretrieval.query.transformation.RewriteQueryTransformer;
import org.springframework.ai.rag.retrieval.search.VectorStoreDocumentRetriever;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * 情感大师应用功能
 *
 * @author zhanglinshuai
 */
@Component
@Slf4j
public class EmotionApp {
    @Resource
    private VectorStore EmotionVectorStore;

    private final ChatClient chatClient;

    private final CustomJdbcChatMemoryRepository customChatMemoryRepository;

    private final ChatMemory emotionChatMemory;

    private final String emotionSystemPrompt;

    @Resource
    private ResourcePatternResolver resourcePatternResolver;

    @Resource
    private ChatModel dashscopeChatModel;
    /**
     * 多路召回
     */
    @Resource
    private MultiRecall multiRecall;
    /**
     * 召回后进行合并重排序
     */
    @Resource
    private RecallResultMerger recallResultMerger;

    @Resource
    private ToolCallback[] allTools;

    @Resource
    private ToolCallbackProvider toolCallbackProvider;

    /**
     * ai支持多轮对话能力
     * 初始化ChatModel，具备多轮对话能力
     * 将消息存储到mysql当中
     *
     * @param dashscopeChatModel
     */
    public EmotionApp(ChatModel dashscopeChatModel, MultiRecall multiRecall, RecallResultMerger recallResultMerger, ConversationInfoService conversationInfoService) {
        this.resourcePatternResolver = new PathMatchingResourcePatternResolver();
        this.multiRecall = multiRecall;
        this.recallResultMerger = recallResultMerger;
        org.springframework.core.io.Resource resource = resourcePatternResolver.getResource("templates/systemPrompt.md");

        DataSource dataSource = new DriverManagerDataSource(
                "jdbc:mysql://localhost:3306/chat_agent",
                "root",
                "123456"
        );
        CustomJdbcChatMemoryRepository chatMemoryRepository = CustomJdbcChatMemoryRepository.builder()
                .jdbcTemplate(new JdbcTemplate(dataSource))
                .dialect(new CustomMysqlJdbcChatMemoryRepositoryDialect())
                .conversationSyncHandler(conversationInfoService::syncConversation)
                .build();
        this.customChatMemoryRepository = chatMemoryRepository;
        this.emotionSystemPrompt = readEmotionSystemPrompt(resource);
        ChatMemory chatMemory = MessageWindowChatMemory.builder()
                .chatMemoryRepository(chatMemoryRepository)
                .maxMessages(10)
                .build();
        this.emotionChatMemory = chatMemory;
        chatClient = ChatClient.builder(dashscopeChatModel)
                .defaultSystem(resource)
                .defaultAdvisors(
                        MessageChatMemoryAdvisor.builder(chatMemory).build(),
//                        //违禁词校验Advisor
//                        new MyBannerWordAdvisor(),

                        //日志Advisor
                        new MyLoggerAdvisor()

                        //自定义的Re-reading advisor
//                        new MyReTwoAdvisor()
                )
                .build();
    }

    /**
     * 读取情感系统提示词文本
     * @param resource 提示词资源
     * @return 提示词文本
     */
    private String readEmotionSystemPrompt(org.springframework.core.io.Resource resource) {
        try {
            String prompt = resource.getContentAsString(StandardCharsets.UTF_8);
            return StringUtils.defaultIfBlank(prompt, CommonVariables.SYSTEM_PROMPT);
        } catch (Exception e) {
            log.warn("读取情感系统提示词失败，回退默认提示词: {}", e.getMessage());
            return CommonVariables.SYSTEM_PROMPT;
        }
    }

    /**
     * ai对话功能
     *
     * @param message
     * @param chatId
     * @return
     */
    public String doChat(String message, String chatId) {
        ChatResponse response = chatClient
                .prompt()
                .user(message)
                .advisors(sepc -> sepc.param(ChatMemory.CONVERSATION_ID, chatId))
                .call()
                .chatResponse();
        String answer = response.getResult().getOutput().getText();
        return answer;
    }

    /**
     * 指定ai生成情感报告(结构化输出)
     *
     * @param message
     * @param chatId
     * @return
     */
    public EmotionReportVO getEmotionReport(String message, String chatId) {
        // 情感报告是一次性结构化生成，不应写入会话记忆，避免污染对话区
        EmotionReportVO emotionReport = ChatClient.builder(dashscopeChatModel)
                .build()
                .prompt()
                .system("""
                        %s
                        
                        请基于当前用户的表达生成一份结构化情感报告。
                        要求：
                        1. title：写成自然、具体的报告标题。
                        2. snapshot：用 2-3 句话概括用户当前的情绪状态和核心处境。
                        3. keyPoints：提炼 3 条以内最值得关注的问题点。
                        4. suggestions：给出 3-5 条温和、具体、可执行的建议。
                        5. actions：给出 2-4 条用户接下来就能做的行动。
                        6. closingMessage：用一小段收尾鼓励语结束。
                        7. 只输出结构化结果，不要额外解释字段。
                        """.formatted(CommonVariables.SYSTEM_PROMPT))
                .user(message)
                .call()
                .entity(EmotionReportVO.class);
        if (emotionReport != null) {
            emotionReport.setGeneratedAt(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()));
        }
        return emotionReport;
    }

    /**
     * dashScope多模态图片识别功能
     *
     * @param imgUrls
     * @param userPrompt
     * @return
     * @throws NoApiKeyException
     * @throws UploadFileException
     */
    public Object MultiImage(List<String> imgUrls, String userPrompt) throws NoApiKeyException, UploadFileException {
        List<Map<String, Object>> list = new ArrayList<>();
        for (String imgUrl : imgUrls) {
            list.add(Collections.singletonMap("image", imgUrl));
        }
        list.add(Collections.singletonMap("text", userPrompt));
        MultiModalConversation conv = new MultiModalConversation();
        MultiModalMessage userMessage = MultiModalMessage.builder().role(Role.USER.getValue())
                .content(list)
                .build();
        MultiModalConversationParam param = MultiModalConversationParam.builder()
                .apiKey(CommonVariables.API_KEY)
                // 此处以qwen-vl-plus为例，可按需更换模型名称。模型列表：https://help.aliyun.com/zh/model-studio/getting-started/models
                .model(CommonVariables.MODEL_IMAGE_NAME)
                .message(userMessage)
                .build();
        MultiModalConversationResult result = conv.call(param);

        return result.getOutput().getChoices().get(0).getMessage().getContent().get(0).get("text");
    }

    /**
     * 使用rag知识库进行对话
     * 使用QuestionAnswerAdvisor
     *
     * @param message
     * @param chatId
     * @return
     */
    public String doChatWithRag(String message, String chatId) {
        /**
         * 构建重写查询器
         */
        Query query = new Query(message);
        RewriteQueryTransformer queryTransformer = RewriteQueryTransformer.builder()
                .chatClientBuilder(ChatClient.builder(dashscopeChatModel))
                .build();
        String ReWriterQuery = queryTransformer.transform(query).text();
        log.info("query:{}", ReWriterQuery);
        /**
         * 构建检索增强生成Advisor
         */
        RetrievalAugmentationAdvisor ragAdvisor = RetrievalAugmentationAdvisor
                .builder()
                //构建查询转换器（查询重写）
                .queryTransformers(queryTransformer)
                //构建查询增强（空上下文查询增强）
                .queryAugmenter(ContextualQueryAugmenter.builder()
                        .allowEmptyContext(true)
                        .build())
                //文档检索器
                .documentRetriever(VectorStoreDocumentRetriever.builder()
                        //相似度搜索
                        .similarityThreshold(0.8)
                        .vectorStore(EmotionVectorStore)
                        .build()
                ).build();
        ChatResponse chatResponse = chatClient
                .prompt()
                .advisors(ragAdvisor)
                .user(ReWriterQuery)
                .advisors(advisorSpec -> advisorSpec.param(ChatMemory.CONVERSATION_ID, chatId))
                .call()
                .chatResponse();
        return chatResponse.getResult().getOutput().getText();
    }

    /**
     * 通过多路召回的方式得到结果
     *
     * @param message
     * @param chatId
     * @return
     */
    public String getResultsThroughMultiRecall(String message, String chatId) {
        StringBuilder query = new StringBuilder();
        //先进行多路召回
        List<Document> multiRecallDocuments = multiRecall.recall(message);
        List<Document> finalDocuments = recallResultMerger.mergeAndRank(multiRecallDocuments);
        for (int i = 0; i < finalDocuments.size(); i++) {
            query.append("文档").append(i + 1).append(":").append(finalDocuments.get(i).getText()).append("\n");
        }
        query.append("\n问题:").append(message).append("\n");
        ChatResponse chatResponse = chatClient
                .prompt()
                .advisors(advisorSpec -> advisorSpec.param(ChatMemory.CONVERSATION_ID, chatId))
                .user(query.toString())
                .call()
                .chatResponse();
        return chatResponse.getResult().getOutput().getText();
    }

    /**
     * 使用ai工具对话
     *
     * @param message
     * @param chatId
     * @return
     */
    public String doChatWithTools(String message, String chatId) {
        ChatResponse chatResponse = chatClient
                .prompt()
                .user(message)
                .advisors(advisorSpec -> advisorSpec.param(ChatMemory.CONVERSATION_ID, chatId))
                .toolCallbacks(allTools)
                .call()
                .chatResponse();
        return chatResponse.getResult().getOutput().getText();
    }

    /**
     * 使用mcp与ai对话
     *
     * @param message
     * @param chatId
     * @return
     */
    public String doChatWithMcp(String message, String chatId) {
        ChatResponse chatResponse = chatClient.prompt()
                .user(message)
                .advisors(advisorSpec -> {
                    advisorSpec.param(ChatMemory.CONVERSATION_ID, chatId);
                })
                .toolCallbacks(toolCallbackProvider)
                .call()
                .chatResponse();
        return chatResponse.getResult().getOutput().getText();
    }

    /**
     * 流式返回结果
     *
     * @param message
     * @param chatId
     * @param userId
     * @return
     */
    public Flux<String> doChatByStream(String message, String chatId, String userId) {
        return doChatByStream(message, chatId, userId, "emotion_support");
    }

    /**
     * 流式返回结果
     *
     * @param message 消息
     * @param chatId 会话id
     * @param userId 用户id
     * @param tag 对话标签
     * @return 流式结果
     */
    public Flux<String> doChatByStream(String message, String chatId, String userId, String tag) {
        UserMessage userMessage = buildEmotionUserMessage(message, chatId, userId, tag);
        return chatClient
                .prompt()
                .messages(userMessage)
                .advisors(advisorSpec ->
                        advisorSpec.param(ChatMemory.CONVERSATION_ID, chatId)
                )
                .stream()
                .content();

    }

    /**
     * 带知识库上下文的情感流式回答
     * @param message 用户消息
     * @param chatId 会话id
     * @param userId 用户id
     * @param tag 对话标签
     * @param knowledgePromptContext 知识库上下文
     * @return 流式结果
     */
    public Flux<String> doChatByStreamWithKnowledgeSupport(String message, String chatId, String userId, String tag, String knowledgePromptContext) {
        UserMessage userMessage = buildEmotionUserMessage(message, chatId, userId, tag);
        return buildKnowledgeGroundedChatClient()
                .prompt()
                .system(buildKnowledgeGroundedEmotionPrompt(knowledgePromptContext))
                .messages(userMessage)
                .advisors(advisorSpec ->
                        advisorSpec.param(ChatMemory.CONVERSATION_ID, chatId)
                )
                .stream()
                .content();
    }

    /**
     * 构建情感消息对象
     * @param message 用户消息
     * @param chatId 会话id
     * @param userId 用户id
     * @param tag 对话标签
     * @return 用户消息
     */
    private UserMessage buildEmotionUserMessage(String message, String chatId, String userId, String tag) {
        UserMessage userMessage = new UserMessage(message);
        Map<String, Object> metadata = userMessage.getMetadata();
        metadata.put("userId", userId);
        metadata.put("mode", "emotion");
        metadata.put("tag", tag);
        String title = getOrCreateTitle(chatId, message);
        metadata.put("title", title);
        return userMessage;
    }

    /**
     * 构建带知识库上下文的情感对话客户端
     * @return ChatClient
     */
    private ChatClient buildKnowledgeGroundedChatClient() {
        return ChatClient.builder(dashscopeChatModel)
                .defaultAdvisors(
                        MessageChatMemoryAdvisor.builder(emotionChatMemory).build(),
                        new MyLoggerAdvisor()
                )
                .build();
    }

    /**
     * 构建带知识库上下文的情感提示词
     * @param knowledgePromptContext 知识库上下文
     * @return 提示词
     */
    private String buildKnowledgeGroundedEmotionPrompt(String knowledgePromptContext) {
        if (StringUtils.isBlank(knowledgePromptContext)) {
            return emotionSystemPrompt;
        }
        return emotionSystemPrompt + "\n\n" + knowledgePromptContext + """
                
                补充要求：
                1. 优先参考上面的知识库资料，结合用户当前的真实处境给出温和、具体、可执行的回应。
                2. 如果知识库资料不足以直接覆盖用户的问题，要明确说明不足，并继续基于情感支持能力给出稳妥建议。
                3. 不要生硬复述资料标题或片段，要把资料内容自然融入回复。
                """;
    }

    private Optional<String> findExistingTitle(String conversationId) {
        List<Message> messages = this.customChatMemoryRepository.findByConversationId(conversationId);
        for (Message m : messages) {
            if (m instanceof UserMessage um) {
                Object t = um.getMetadata().get("title");
                if (t instanceof String s && !s.isEmpty()) {
                    return Optional.of(s);
                }
            }
        }
        return Optional.empty();
    }

    private String getOrCreateTitle(String conversationId, String userText) {
        Optional<String> existing = findExistingTitle(conversationId);
        if (existing.isPresent()) {
            return existing.get();
        }
        String byModel = generateTitleWithModel(userText);
        if (byModel != null && !byModel.isEmpty()) {
            return byModel;
        }
        return generateTitle(userText);
    }

    private String generateTitle(String text) {
        if (text == null) {
            return "新对话";
        }
        String trimmed = text.trim();
        if (trimmed.isEmpty()) {
            return "新对话";
        }
        int limit = Math.min(trimmed.length(), 20);
        return trimmed.substring(0, limit);
    }

    private String generateTitleWithModel(String text) {
        if (text == null || text.trim().isEmpty()) {
            return null;
        }
        try {
            String content = ChatClient.builder(dashscopeChatModel)
                    .build()
                    .prompt()
                    .system("你是标题生成器。基于用户输入生成一个简洁的会话标题。只返回标题本身，不要解释，不要标点，不要引号，不要换行。不超过12个中文字符或6个英文词。")
                    .user(text)
                    .call()
                    .content();
            if (content == null) {
                return null;
            }
            String t = content.trim();
            int nl = t.indexOf('\n');
            if (nl >= 0) {
                t = t.substring(0, nl);
            }
            t = t.replace("\"", "").replace("“", "").replace("”", "");
            if (t.length() > 20) {
                t = t.substring(0, 20);
            }
            return t;
        } catch (Exception e) {
            return null;
        }
    }
}
