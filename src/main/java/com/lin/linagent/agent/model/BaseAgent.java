package com.lin.linagent.agent.model;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.jsoup.internal.StringUtil;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

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
    //执行过程回调
    private transient Consumer<AgentProgressEvent> progressConsumer;


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

    public SseEmitter runStream(String userPrompt) {
        SseEmitter emitter = new SseEmitter(300000L);
        CompletableFuture.runAsync(() -> {
            try {
                if (this.state != AgentState.IDLE) {
                    emitter.send("错误:" + this.state);
                    emitter.complete();
                    return;
                }
                if (StringUtil.isBlank(userPrompt)) {
                    emitter.send("错误:空提示词");
                    emitter.complete();
                    return;
                }
                state = AgentState.RUNNING;
                messageList.add(new UserMessage(userPrompt));
                try {
                    for (int i = 0; i < maxSteps && state != AgentState.FINISHED; i++) {
                        currentStep = i + 1;
                        String result = step();
                        emitter.send(result + "\n");
                    }
                    if (currentStep >= maxSteps) {
                        state = AgentState.FINISHED;
                        emitter.send("执行结束:" + maxSteps);
                    }
                    emitter.complete();
                } catch (Exception e) {
                    state = AgentState.ERROR;
                    log.error("执行智能体失败", e);
                    try {
                        emitter.send("执行错误:" + e.getMessage());
                        emitter.complete();
                    } catch (Exception ex) {
                        emitter.completeWithError(ex);
                    }
                } finally {
                    this.cleanup();
                }
            } catch (Exception e) {
                emitter.completeWithError(e);
            }
        });
        emitter.onTimeout(() -> {
            this.state = AgentState.ERROR;
            this.cleanup();
        });
        emitter.onCompletion(() -> {
            if (this.state == AgentState.RUNNING) {
                this.state = AgentState.FINISHED;
            }
            this.cleanup();
        });
        return emitter;
    }

    /**
     * 以进度回调方式运行智能体
     *
     * @param userPrompt 用户输入
     * @param consumer 进度事件回调
     */
    public void runWithProgress(String userPrompt, Consumer<AgentProgressEvent> consumer) {
        if (this.state != AgentState.IDLE) {
            throw new RuntimeException("智能体不处于空闲中:" + this.state);
        }
        if (StringUtils.isBlank(userPrompt)) {
            throw new RuntimeException("用户提示词为空");
        }
        this.progressConsumer = consumer;
        state = AgentState.RUNNING;
        messageList.add(new UserMessage(userPrompt));
        try {
            for (int i = 0; i < maxSteps && state != AgentState.FINISHED; i++) {
                currentStep = i + 1;
                step();
            }
            if (currentStep >= maxSteps) {
                state = AgentState.FINISHED;
                emitProgress("error", "执行超过最大步数:" + maxSteps);
            }
        } catch (Exception e) {
            state = AgentState.ERROR;
            log.error("智能体执行错误", e);
            emitProgress("error", "执行错误:" + e.getMessage());
        } finally {
            this.progressConsumer = null;
            this.cleanup();
        }
    }

    /**
     * 发送执行事件
     *
     * @param type 事件类型
     * @param content 事件内容
     */
    protected void emitProgress(String type, String content) {
        if (this.progressConsumer == null || StringUtils.isBlank(content)) {
            return;
        }
        AgentProgressEvent agentProgressEvent = new AgentProgressEvent();
        agentProgressEvent.setType(type);
        agentProgressEvent.setStep(currentStep);
        agentProgressEvent.setContent(content);
        this.progressConsumer.accept(agentProgressEvent);
    }

    /**
     * 将较长内容切分后逐段发送，便于前端流式展示
     *
     * @param type 事件类型
     * @param content 完整内容
     */
    protected void emitProgressChunked(String type, String content) {
        if (StringUtils.isBlank(content)) {
            return;
        }
        for (String chunk : splitForStreaming(content)) {
            emitProgress(type, chunk);
            try {
                TimeUnit.MILLISECONDS.sleep(35);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    /**
     * 将长文本拆成适合流式展示的小段
     *
     * @param content 完整内容
     * @return 分片结果
     */
    private List<String> splitForStreaming(String content) {
        List<String> chunks = new ArrayList<>();
        String normalized = content == null ? "" : content;
        String[] segments = normalized.split("(?<=[。！？.!?；;\\n])");
        for (String segment : segments) {
            if (segment == null || segment.isEmpty()) {
                continue;
            }
            String remaining = segment;
            int chunkSize = 48;
            while (remaining.length() > chunkSize) {
                chunks.add(remaining.substring(0, chunkSize));
                remaining = remaining.substring(chunkSize);
            }
            if (!remaining.isEmpty()) {
                chunks.add(remaining);
            }
        }
        if (chunks.isEmpty()) {
            chunks.add(normalized);
        }
        return chunks;
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
    protected void cleanup() {

    }
}
