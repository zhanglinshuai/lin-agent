package com.lin.linagent.advisor;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClientMessageAggregator;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.*;
import reactor.core.publisher.Flux;

/**
 * 自定义日志拦截器
 * @author zhanglinshuai
 */
@Slf4j
public class MyLoggerAdvisor implements CallAdvisor, StreamAdvisor {


    private void logRequest(ChatClientRequest request) {
        log.info("用户输入：{}",request.prompt().getUserMessage().getText());
    }

    private void logResponse(ChatClientResponse response) {
        log.info("模型输出：{}",response.chatResponse().getResult().getOutput().getText());
    }
    @Override
    public ChatClientResponse adviseCall(ChatClientRequest chatClientRequest, CallAdvisorChain callAdvisorChain) {
        logRequest(chatClientRequest);
        String userPrompt = chatClientRequest.prompt().getUserMessage().getText();

        ChatClientResponse chatClientResponse = callAdvisorChain.nextCall(chatClientRequest);

        logResponse(chatClientResponse);

        return chatClientResponse;
    }

    @Override
    public Flux<ChatClientResponse> adviseStream(ChatClientRequest chatClientRequest, StreamAdvisorChain streamAdvisorChain) {
        logRequest(chatClientRequest);

        Flux<ChatClientResponse> chatClientResponse = streamAdvisorChain.nextStream(chatClientRequest);


        return new ChatClientMessageAggregator().aggregateChatClientResponse(chatClientResponse,this::logResponse);
    }

    @Override
    public String getName() {
        return this.getClass().getSimpleName();
    }

    @Override
    public int getOrder() {
        return 1;
    }
}
