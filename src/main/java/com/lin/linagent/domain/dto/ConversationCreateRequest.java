package com.lin.linagent.domain.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 创建会话请求
 */
@Data
public class ConversationCreateRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 会话id
     */
    private String conversationId;

    /**
     * 会话标题
     */
    private String title;
}
