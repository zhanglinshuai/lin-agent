package com.lin.linagent.domain.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 保存知识库文档请求
 */
@Data
public class KnowledgeDocumentSaveRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 文件名
     */
    private String fileName;

    /**
     * 文档标题
     */
    private String title;

    /**
     * 文档内容
     */
    private String content;

    /**
     * 保存后是否立即重建索引
     */
    private Boolean rebuildIndex;
}
