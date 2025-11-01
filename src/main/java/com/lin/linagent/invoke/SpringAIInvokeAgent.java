package com.lin.linagent.invoke;

import jakarta.annotation.Resource;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * 使用spring ai alibaba调用灵积大模型
 *  通过ChatModel对象调用大模型，适合简单的对话场景
 * @author zhanglinshuai
 */
@Component
public class SpringAIInvokeAgent implements CommandLineRunner {
    @Resource
    private ChatModel dashscopeChatModel;

    @Override
    public void run(String... args) throws Exception {
        AssistantMessage assistantMessage = dashscopeChatModel.call(new Prompt("你好，你会做什么?"))
                .getResult()
                .getOutput();
        System.out.println("Spring AI 调用成功");
    }
}
