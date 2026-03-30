package com.lin.linagent.domain.dto;

import lombok.Data;

/**
 * 会话置顶请求
 */
@Data
public class ConversationPinRequest {

    /**
     * 用户名
     */
    private String username;

    /**
     * 会话id
     */
    private String conversationId;

    /**
     * 是否置顶
     */
    private Boolean pinned;
}
