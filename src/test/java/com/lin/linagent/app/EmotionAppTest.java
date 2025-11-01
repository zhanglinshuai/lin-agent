package com.lin.linagent.app;

import jakarta.annotation.Resource;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.UUID;
@SpringBootTest()
class EmotionAppTest {

    @Resource
    private EmotionApp emotionApp;

    /**
     * 多轮对话功能test
     */
    @Test
    void doChat() {
        String chatId = UUID.randomUUID().toString();
        //第一轮
        String message = "明天面试，紧张得睡不着";
        String answer = emotionApp.doChat(message,chatId);
        Assertions.assertNotNull(answer);
        //第二轮
        message = "最近提不起精神";
        answer = emotionApp.doChat(message,chatId);
        Assertions.assertNotNull(answer);
        //第三轮
        message = "和朋友吵架了，可能是我的错";
        answer = emotionApp.doChat(message,chatId);
        Assertions.assertNotNull(answer);
        //第四轮
        message = "我明天要什么？我刚给你说过，请告诉我";
        answer = emotionApp.doChat(message,chatId);
        Assertions.assertNotNull(answer);
    }
}