package com.lin.linagent.rag.etl;

import com.lin.linagent.rag.etl.transformer.MyCustomKeyWordEnricher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.markdown.MarkdownDocumentReader;
import org.springframework.ai.reader.markdown.config.MarkdownDocumentReaderConfig;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * 自定义文档抽取、转换、加载(ETL过程）
 * @author zhanglinshuai
 */
@Slf4j
@Component
public class CustomDocumentETL {

    @jakarta.annotation.Resource
    private MyCustomKeyWordEnricher myCustomKeyWordEnricher;

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
                String type = filename.substring(0,2);
                HashMap<String, Object> additionalMetadata = new HashMap<>();
                additionalMetadata.put("type", type);
                additionalMetadata.put("filename", filename);

                MarkdownDocumentReaderConfig config = MarkdownDocumentReaderConfig.builder()
                        .withHorizontalRuleCreateDocument(true)
                        .withIncludeCodeBlock(false)
                        .withIncludeBlockquote(false)
                        .withAdditionalMetadata(additionalMetadata)
                        .build();
                MarkdownDocumentReader markdownDocumentReader = new MarkdownDocumentReader(resource, config);
                //提取关键词后的文档（转换文档）
                List<Document> enrichKeyWordDocuments = myCustomKeyWordEnricher.enrichKeyWordToDocument(markdownDocumentReader.read());
                //加载文档
                allDocuments.addAll(enrichKeyWordDocuments);
            }

        } catch (IOException e) {
            log.error("文档加载失败");
            throw new RuntimeException(e);
        }
        return allDocuments;
    }
}
