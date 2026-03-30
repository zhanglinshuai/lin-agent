package com.lin.linagent.domain.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 知识库上传结果
 */
@Data
public class KnowledgeUploadResultVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private KnowledgeDocumentVO document;

    private KnowledgeIndexTaskVO task;
}
