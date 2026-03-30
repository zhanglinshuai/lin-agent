package com.lin.linagent.domain.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * PDF 导出请求
 */
@Data
public class PdfExportRequest implements Serializable {

    private static final long serialVersionUID = 7130457989043566117L;

    /**
     * 文件名（可选，默认 export.pdf）
     */
    private String fileName;

    /**
     * 标题
     */
    private String title;

    /**
     * 副标题
     */
    private String subtitle;

    /**
     * 正文（支持普通文本和 markdown 原文）
     */
    private String content;
}
