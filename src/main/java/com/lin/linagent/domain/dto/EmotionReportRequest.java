package com.lin.linagent.domain.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 情感报告请求
 */
@Data
public class EmotionReportRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 会话id
     */
    private String conversationId;

    /**
     * 用户消息
     */
    private String userMessage;

    /**
     * 助手答复
     */
    private String assistantMessage;
}
