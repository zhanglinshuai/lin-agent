package com.lin.linagent.domain.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 管理后台概览
 */
@Data
public class AdminDashboardVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long userCount;

    private Long conversationCount;

    private Long messageCount;

    private Integer knowledgeFileCount;

    private Integer knowledgeSectionCount;

    private Integer vectorRowCount;

    private Long elasticDocCount;

    private Integer activeStreamCount;

    private Integer logCount;

    private Long errorLogCount;

    private String lastRebuildTime;
}
