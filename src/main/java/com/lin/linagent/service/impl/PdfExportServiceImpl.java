package com.lin.linagent.service.impl;

import com.itextpdf.text.BaseColor;
import com.itextpdf.text.Document;
import com.itextpdf.text.DocumentException;
import com.itextpdf.text.Element;
import com.itextpdf.text.Font;
import com.itextpdf.text.PageSize;
import com.itextpdf.text.Paragraph;
import com.itextpdf.text.pdf.BaseFont;
import com.itextpdf.text.pdf.PdfWriter;
import com.lin.linagent.domain.dto.PdfExportRequest;
import com.lin.linagent.exception.BusinessException;
import com.lin.linagent.exception.ErrorCode;
import com.lin.linagent.service.PdfExportService;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * PDF 导出服务实现
 */
@Service
public class PdfExportServiceImpl implements PdfExportService {

    @Override
    public byte[] generatePdf(PdfExportRequest request) {
        if (request == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "PDF 导出请求不能为空");
        }
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            Document document = new Document(PageSize.A4, 48, 48, 56, 50);
            PdfWriter.getInstance(document, outputStream);
            document.open();

            BaseFont baseFont = BaseFont.createFont("STSong-Light", "UniGB-UCS2-H", BaseFont.NOT_EMBEDDED);
            Font titleFont = new Font(baseFont, 20, Font.BOLD, new BaseColor(17, 24, 39));
            Font subTitleFont = new Font(baseFont, 11, Font.NORMAL, new BaseColor(71, 85, 105));
            Font bodyFont = new Font(baseFont, 12, Font.NORMAL, new BaseColor(31, 41, 55));
            Font metaFont = new Font(baseFont, 10, Font.NORMAL, new BaseColor(100, 116, 139));

            String title = StringUtils.defaultIfBlank(request.getTitle(), "导出文档");
            Paragraph titleParagraph = new Paragraph(title, titleFont);
            titleParagraph.setAlignment(Element.ALIGN_LEFT);
            titleParagraph.setSpacingAfter(8);
            document.add(titleParagraph);

            if (StringUtils.isNotBlank(request.getSubtitle())) {
                Paragraph subTitleParagraph = new Paragraph(request.getSubtitle(), subTitleFont);
                subTitleParagraph.setAlignment(Element.ALIGN_LEFT);
                subTitleParagraph.setSpacingAfter(16);
                document.add(subTitleParagraph);
            } else {
                Paragraph spacer = new Paragraph(" ", bodyFont);
                spacer.setSpacingAfter(8);
                document.add(spacer);
            }

            String content = StringUtils.defaultIfBlank(request.getContent(), "暂无导出内容");
            String[] lines = content.replace("\r\n", "\n").replace('\r', '\n').split("\n");
            for (String line : lines) {
                Paragraph bodyParagraph = new Paragraph(StringUtils.defaultIfBlank(line, " "), bodyFont);
                bodyParagraph.setLeading(20f);
                bodyParagraph.setSpacingAfter(2);
                document.add(bodyParagraph);
            }

            Paragraph footer = new Paragraph(
                    "导出时间：" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")),
                    metaFont
            );
            footer.setSpacingBefore(16);
            document.add(footer);

            document.close();
            return outputStream.toByteArray();
        } catch (DocumentException | java.io.IOException e) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "PDF 生成失败：" + e.getMessage());
        }
    }
}
