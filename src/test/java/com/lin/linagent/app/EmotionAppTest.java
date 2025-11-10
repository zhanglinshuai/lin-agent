package com.lin.linagent.app;

import com.alibaba.dashscope.exception.NoApiKeyException;
import com.alibaba.dashscope.exception.UploadFileException;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;
import java.util.List;
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

    @Test
    void getEmotionReport() {
        String chatId = UUID.randomUUID().toString();
        String message = "明天面试，紧张得睡不着";
        EmotionApp.EmotionReport emotionReport = emotionApp.getEmotionReport(message, chatId);
        Assertions.assertNotNull(emotionReport);
    }

    @Test
    void multiImage() throws NoApiKeyException, UploadFileException {
        List<String>  imgUrls = new ArrayList<>();
        imgUrls.add("https://dashscope.oss-cn-beijing.aliyuncs.com/images/dog_and_girl.jpeg");
        imgUrls.add("https://dashscope.oss-cn-beijing.aliyuncs.com/images/tiger.png");
        imgUrls.add( "https://dashscope.oss-cn-beijing.aliyuncs.com/images/rabbit.png");
        String userPrompt = "这些图片描述了什么？";
        Object result = emotionApp.MultiImage(imgUrls,userPrompt);
        Assertions.assertNotNull(result);
    }

    @Test
    void bannerWord(){
        String chatId = UUID.randomUUID().toString();
        String message = "明天面试，紧张得睡不着";
        String answer = emotionApp.doChat(message, chatId);
        Assertions.assertNotNull(answer);
    }

    @Test
    void doChatWithRag() {
        String message = "明天面试，紧张得睡不着？";
        String chatId = UUID.randomUUID().toString();
        String answer = emotionApp.doChatWithRag(message, chatId);
        Assertions.assertNotNull(answer);
    }

    @Test
    void getResultsThroughMultiRecall() {
        String message = "明天面试，紧张得睡不着？";
        String chatId = UUID.randomUUID().toString();
        String resultsThroughMultiRecall = emotionApp.getResultsThroughMultiRecall(message, chatId);
        Assertions.assertNotNull(resultsThroughMultiRecall);
    }

    @Test
    void doChatWithTools() {
        String chatId = UUID.randomUUID().toString();
        String message = "推荐新乡周末适合情侣约会打卡的地方";
        String answer = emotionApp.doChatWithTools(message, chatId);
        System.out.println(answer);
    }

    @Test
    void doChatWithMcp() {
        String chatId = UUID.randomUUID().toString();
        String message = "推荐新乡周末适合情侣约会打卡的地方";
        String answer = emotionApp.doChatWithMcp(message, chatId);
        System.out.println(answer);
    }
}