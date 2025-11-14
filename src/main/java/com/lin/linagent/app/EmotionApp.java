package com.lin.linagent.app;

import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversation;
import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversationParam;
import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversationResult;
import com.alibaba.dashscope.common.MultiModalMessage;
import com.alibaba.dashscope.common.Role;
import com.alibaba.dashscope.exception.NoApiKeyException;
import com.alibaba.dashscope.exception.UploadFileException;
import com.lin.linagent.advisor.MyLoggerAdvisor;
import com.lin.linagent.chatMemory.CustomMysqlChatMemoryRepositoryDialect;
import com.lin.linagent.contant.CommonVariables;
import com.lin.linagent.multirecall.MultiRecall;
import com.lin.linagent.multirecall.RecallResultMerger;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.memory.repository.jdbc.JdbcChatMemoryRepository;
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

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
    private ToolCallbackProvider  toolCallbackProvider;

    /**
     * ai支持多轮对话能力
     * 初始化ChatModel，具备多轮对话能力
     * 将消息存储到mysql当中
     *
     * @param dashscopeChatModel
     */
    public EmotionApp(ChatModel dashscopeChatModel,MultiRecall multiRecall,RecallResultMerger recallResultMerger) {
        this.resourcePatternResolver = new PathMatchingResourcePatternResolver();
        this.multiRecall = multiRecall;
        this.recallResultMerger = recallResultMerger;
        org.springframework.core.io.Resource resource = resourcePatternResolver.getResource("templates/systemPrompt.md");

        DataSource dataSource = new DriverManagerDataSource(
                "jdbc:mysql://localhost:3306/chat_agent",
                "root",
                "123456"
        );
        ChatMemoryRepository chatMemoryRepository = JdbcChatMemoryRepository.builder()
                .jdbcTemplate(new JdbcTemplate(dataSource))
                .dialect(new CustomMysqlChatMemoryRepositoryDialect())
                .build();
        ChatMemory chatMemory = MessageWindowChatMemory.builder()
                .chatMemoryRepository(chatMemoryRepository)
                .maxMessages(10)
                .build();
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

    record EmotionReport(String title, List<String> suggestions) {
    }

    /**
     * 指定ai生成情感报告(结构化输出)
     *
     * @param message
     * @param chatId
     * @return
     */
    public EmotionReport getEmotionReport(String message, String chatId) {
        EmotionReport emotionReport = chatClient
                .prompt()
                .system(CommonVariables.SYSTEM_PROMPT + "每次对话后生成情感报告，标题为{问题}的情感报告，内容为建议列表")
                .user(message)
                .advisors(sepc -> sepc.param(ChatMemory.CONVERSATION_ID, chatId))
                .call()
                .entity(EmotionReport.class);
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
            query.append("文档").append(i+1).append(":").append(finalDocuments.get(i).getText()).append("\n");
        }
        query.append("\n问题:").append(message).append("\n");
        ChatResponse chatResponse = chatClient
                .prompt()
                .advisors(advisorSpec -> advisorSpec.param(ChatMemory.CONVERSATION_ID, chatId))
                .user(query.toString())
                .call()
                .chatResponse();
        return  chatResponse.getResult().getOutput().getText();
    }

    /**
     * 使用ai工具对话
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

    public Flux<String> doChatByStream(String message,String chatId){
        return chatClient
                .prompt()
                .user(message)
                .advisors(advisorSpec -> advisorSpec.param(ChatMemory.CONVERSATION_ID,chatId))
                .stream()
                .content();
    }


}
