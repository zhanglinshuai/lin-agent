package com.lin.linagent.agent.model;

import com.lin.linagent.advisor.MyLoggerAdvisor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Component;

import static com.lin.linagent.contant.CommonVariables.NEXT_STOP_PROMPT;
import static com.lin.linagent.contant.CommonVariables.SYSTEM_PROMPT_MANUS;

@Component
@Slf4j
public class LinManus extends ToolCallAgent{

    public LinManus(ToolCallback[] allTools, ChatModel dashscopeChatModel) {
        super(allTools);
        this.setName("linManus");
        this.setSystemPrompt(SYSTEM_PROMPT_MANUS);
        this.setNextStepPrompt(NEXT_STOP_PROMPT);
        this.setMaxSteps(20);
        ChatClient chatClient = ChatClient.builder(dashscopeChatModel)
                .build();
        this.setChatClient(chatClient);
    }


}
