package com.lin.linagent.app;

import com.lin.linagent.advisor.MyLoggerAdvisor;
import com.lin.linagent.advisor.MyReTwoAdvisor;
import com.lin.linagent.chatMemory.CustomMysqlChatMemoryRepositoryDialect;
import com.lin.linagent.contant.CommonVariables;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.memory.InMemoryChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.memory.repository.jdbc.JdbcChatMemoryRepository;
import org.springframework.ai.chat.memory.repository.jdbc.MysqlChatMemoryRepositoryDialect;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.util.List;

/**
 * 情感大师应用功能
 *
 * @author zhanglinshuai
 */
@Component
@Slf4j
public class EmotionApp {


    private final ChatClient chatClient;
    /**
     * ai支持多轮对话能力
     * 初始化ChatModel，具备多轮对话能力
     *
     * @param dashscopeChatModel
     */
    public EmotionApp(ChatModel dashscopeChatModel) {
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
                .defaultSystem(CommonVariables.SYSTEM_PROMPT)
                .defaultAdvisors(
                        MessageChatMemoryAdvisor.builder(chatMemory).build(),
                        //日志Advisor
                        new MyLoggerAdvisor(),
                        //自定义的Re-reading advisor
                        new MyReTwoAdvisor()
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
        log.info("content:{}", answer);
        return answer;
    }

    record EmotionReport(String title, List<String> suggestions) {}

    /**
     *  指定ai生成情感报告
     * @param message
     * @param chatId
     * @return
     */
    public EmotionReport getEmotionReport(String message,String chatId){
        EmotionReport emotionReport = chatClient
                .prompt()
                .system(CommonVariables.SYSTEM_PROMPT + "每次对话后生成情感报告，标题为{问题}的情感报告，内容为建议列表")
                .user(message)
                .advisors(sepc -> sepc.param(ChatMemory.CONVERSATION_ID, chatId))
                .call()
                .entity(EmotionReport.class);
        log.info("emotionReport:{}",emotionReport);
        return emotionReport;
    }


}
