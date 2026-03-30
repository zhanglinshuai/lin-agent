package com.lin.linagent.domain.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 知识库文档对象
 */
@Data
public class KnowledgeDocumentVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private String fileName;

    private String title;

    private String content;

    private Long size;

    private String updateTime;

    private Integer sectionCount;
}
