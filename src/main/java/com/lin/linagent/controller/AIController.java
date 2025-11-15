package com.lin.linagent.controller;

import com.lin.linagent.agent.model.LinManus;
import com.lin.linagent.app.EmotionApp;
import jakarta.annotation.Resource;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;

import java.io.IOException;

@RequestMapping("/ai")
@RestController
public class AIController {
    @Resource
    private EmotionApp emotionApp;

    @Resource
    private ToolCallback[] allTools;

    @Resource
    private ChatModel dashscopeChatModel;
    @GetMapping("/emotion/chat/sync")
    public String doChatWithEmotionAppSync(String message,String chatId){
        return emotionApp.doChat(message,chatId);
    }
    @GetMapping(value = "/emotion/chat/sse",produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> doChatWithEmotionAppSSE(String message,String chatId,String userId){
        return emotionApp.doChatByStream(message,chatId,userId);
    }
    @GetMapping("/emotion/chat/sse/emitter")
    public SseEmitter doChatWithEmotionAppSseEmitter(String message, String chatId,String userId) {
        // 创建一个超时时间较长的 SseEmitter
        SseEmitter emitter = new SseEmitter(180000L); // 3分钟超时
        // 获取 Flux 数据流并直接订阅
        emotionApp.doChatByStream(message, chatId,userId)
                .subscribe(
                        // 处理每条消息
                        chunk -> {
                            try {
                                emitter.send(chunk);
                            } catch (IOException e) {
                                emitter.completeWithError(e);
                            }
                        },
                        // 处理错误
                        emitter::completeWithError,
                        // 处理完成
                        emitter::complete
                );
        // 返回emitter
        return emitter;
    }
    @GetMapping("/manus/chat")
    public SseEmitter doChatWithManus(String message){
        LinManus linManus = new LinManus(allTools, dashscopeChatModel);
        return linManus.runStream(message);
    }
}
