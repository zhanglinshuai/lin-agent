package com.lin.linagent.domain.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 会话删除请求
 */
@Data
public class ConversationDeleteRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = -6887564753322847610L;

    /**
     * 用户名
     */
    private String username;

    /**
     * 会话id
     */
    private String conversationId;
}
