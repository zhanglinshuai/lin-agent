package com.lin.linagent.domain.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 知识库索引任务状态
 */
@Data
public class KnowledgeIndexTaskVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private String taskId;

    private String fileName;

    private String status;

    private String stage;

    private Integer progress;

    private String message;

    private String createdAt;

    private String startedAt;

    private String finishedAt;
}
