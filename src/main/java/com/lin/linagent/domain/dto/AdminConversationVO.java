package com.lin.linagent.domain.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 管理后台会话摘要
 */
@Data
public class AdminConversationVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private String conversationId;

    private String userId;

    private String userName;

    private String title;

    private String mode;

    private String tag;

    private Boolean pinned;

    private String summary;

    private String lastMessage;

    private Date createTime;

    private Date lastTime;

    private Long messageCount;
}
