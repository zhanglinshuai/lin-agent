package com.lin.linagent.service;

import com.lin.linagent.domain.dto.PdfExportRequest;

/**
 * PDF 导出服务
 */
public interface PdfExportService {

    /**
     * 生成 PDF 二进制
     * @param request 导出请求
     * @return PDF 字节
     */
    byte[] generatePdf(PdfExportRequest request);
}
