package com.lin.linagent.invoke;

import com.lin.linagent.contant.CommonVariables;
import dev.langchain4j.community.model.dashscope.QwenChatModel;

/**
 * 使用LangChain4j调用灵积大模型
 */
public class LangChainInvokeAgent {
    public static void main(String[] args) {
        QwenChatModel chatModel = QwenChatModel.builder()
                .apiKey(CommonVariables.API_KEY)
                .modelName("qwen-max")
                .build();
        String answer = chatModel.chat("你擅长干什么？");
        System.out.println("LangChain4j请求成功");
    }
}
