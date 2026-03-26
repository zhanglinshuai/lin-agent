package com.lin.linagent.domain.dto;

import lombok.Data;

import java.util.Date;

/**
 * 会话摘要
 */
@Data
public class ConversationSummary {

    /**
     * 会话id
     */
    private String conversationId;

    /**
     * 会话标题
     */
    private String title;

    /**
     * 会话标签
     */
    private String tag;

    /**
     * 会话模式
     */
    private String mode;

    /**
     * 会话摘要
     */
    private String summary;

    /**
     * 最新消息
     */
    private String lastMessage;

    /**
     * 最新时间
     */
    private Date lastTime;

    /**
     * 消息数量
     */
    private Integer messageCount;
}
