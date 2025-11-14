package com.lin.linagent.agent.model;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;

import java.util.ArrayList;
import java.util.List;

/**
 * 基础代理类，管理代理状态和执行流程
 */
@Data
@Slf4j
public abstract class BaseAgent {
    //名称
    private String name;
    //提示
    private String systemPrompt;
    private String nextStepPrompt;
    //状态
    private AgentState state = AgentState.IDLE;
    //最大执行次数
    private int maxSteps = 10;
    //当前执行次数
    private int currentStep = 0;
    //LLM
    private ChatClient chatClient;
    //维护上下文
    private List<Message> messageList = new ArrayList<>();


    /**
     * TODO
     * 1. 将上下文存储到数据库当中
     * 2.
     */


    /**
     * 运行代理
     *
     * @param userPrompt
     * @return
     */
    public String run(String userPrompt) {
        if (this.state != AgentState.IDLE) {
            throw new RuntimeException("智能体不处于空闲中:" + this.state);
        }
        if (StringUtils.isBlank(userPrompt)) {
            throw new RuntimeException("用户提示词为空");
        }
        state = AgentState.RUNNING;
        //将消息保存到上下文
        messageList.add(new UserMessage(userPrompt));
        //保存结果
        List<String> results = new ArrayList<>();
        try {
            for (int i = 0; i < maxSteps && state != AgentState.FINISHED; i++) {
                currentStep = i + 1;
                log.info("执行了" + currentStep + "最多可执行" + maxSteps);
                String stepResult = step();
                String result = "Step:" + currentStep + ":" + stepResult;
                results.add(result);
            }
            if (currentStep >= maxSteps) {
                state = AgentState.FINISHED;
                results.add("超过了最大不熟(" + maxSteps + ")");
            }
            return String.join(",", results);
        } catch (Exception e) {
            state = AgentState.ERROR;
            log.error("智能体执行错误", e);
            return "执行错误" + e.getMessage();
        } finally {
            this.cleanup();
        }
    }

    /**
     * 执行单步
     *
     * @return
     */
    public abstract String step();

    /**
     * 清理资源
     */
    protected void cleanup(){

    }
}
