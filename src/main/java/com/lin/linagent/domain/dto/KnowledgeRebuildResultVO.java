package com.lin.linagent.domain.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 知识库重建结果
 */
@Data
public class KnowledgeRebuildResultVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Integer knowledgeFileCount;

    private Integer knowledgeSectionCount;

    private Integer vectorRowCount;

    private Long elasticDocCount;

    private String rebuiltAt;
}
