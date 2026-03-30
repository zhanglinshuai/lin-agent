package com.lin.linagent.domain.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 会话重命名请求
 */
@Data
public class ConversationRenameRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = -5664536083171799017L;

    /**
     * 用户名
     */
    private String username;

    /**
     * 会话id
     */
    private String conversationId;

    /**
     * 新标题
     */
    private String title;
}
