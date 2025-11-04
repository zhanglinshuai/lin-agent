package com.lin.linagent.rag;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.markdown.MarkdownDocumentReader;
import org.springframework.ai.reader.markdown.config.MarkdownDocumentReaderConfig;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * 自定义文档抽取、转换、加载(ETL过程）
 * @author zhanglinshuai
 */
@Slf4j
@Component
public class CustomDocumentETL {

    private final ResourcePatternResolver resourcePatternResolver;

    public CustomDocumentETL(ResourcePatternResolver resourcePatternResolver) {
        this.resourcePatternResolver = resourcePatternResolver;
    }
    public List<Document> loadMarkDownDocuments(){
        List<Document> allDocuments = new ArrayList<>();
        try {
            //抽取文档
            Resource[] resources = resourcePatternResolver.getResources("classpath:static/document/*.md");
            for (Resource resource : resources) {
                String filename = resource.getFilename();
                MarkdownDocumentReaderConfig config = MarkdownDocumentReaderConfig.builder()
                        .withHorizontalRuleCreateDocument(true)
                        .withIncludeCodeBlock(false)
                        .withIncludeBlockquote(false)
                        .withAdditionalMetadata("filename", filename)
                        .build();
                //加载文档
                MarkdownDocumentReader markdownDocumentReader = new MarkdownDocumentReader(resource, config);
                allDocuments.addAll(markdownDocumentReader.read());
            }

        } catch (IOException e) {
            log.error("文档加载失败");
            throw new RuntimeException(e);
        }
        return allDocuments;
    }
}
