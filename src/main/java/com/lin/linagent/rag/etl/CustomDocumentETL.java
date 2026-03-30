package com.lin.linagent.rag.etl;

import com.lin.linagent.rag.etl.transformer.MyCustomKeyWordEnricher;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.markdown.MarkdownDocumentReader;
import org.springframework.ai.reader.markdown.config.MarkdownDocumentReaderConfig;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 自定义文档抽取、转换、加载(ETL过程）
 * @author zhanglinshuai
 */
@Slf4j
@Component
public class CustomDocumentETL {

    private static final Path KNOWLEDGE_ROOT = Paths.get(System.getProperty("user.dir"), "src", "main", "resources", "static", "document");

    @jakarta.annotation.Resource
    private MyCustomKeyWordEnricher myCustomKeyWordEnricher;

    public List<Document> loadMarkDownDocuments() {
        List<Document> allDocuments = new ArrayList<>();
        if (!Files.exists(KNOWLEDGE_ROOT)) {
            return allDocuments;
        }
        try (var stream = Files.list(KNOWLEDGE_ROOT)) {
            List<Path> fileList = stream
                    .filter(path -> Files.isRegularFile(path) && path.getFileName().toString().toLowerCase().endsWith(".md"))
                    .sorted()
                    .toList();
            for (Path path : fileList) {
                allDocuments.addAll(loadSingleMarkDownDocument(path.getFileName().toString(), Files.readString(path, StandardCharsets.UTF_8)));
            }
        } catch (IOException e) {
            log.error("文档加载失败");
            throw new RuntimeException(e);
        }
        return allDocuments;
    }

    public List<Document> loadSingleMarkDownSections(String fileName, String content) {
        String normalizedFileName = StringUtils.trimToEmpty(fileName);
        String normalizedContent = StringUtils.defaultString(content);
        if (StringUtils.isAnyBlank(normalizedFileName, normalizedContent)) {
            return new ArrayList<>();
        }

        HashMap<String, Object> additionalMetadata = new HashMap<>();
        additionalMetadata.put("type", resolveType(normalizedFileName));
        additionalMetadata.put("filename", normalizedFileName);
        additionalMetadata.put("fileName", normalizedFileName);
        additionalMetadata.put("source", normalizedFileName);

        MarkdownDocumentReaderConfig config = MarkdownDocumentReaderConfig.builder()
                .withHorizontalRuleCreateDocument(true)
                .withIncludeCodeBlock(false)
                .withIncludeBlockquote(false)
                .withAdditionalMetadata(additionalMetadata)
                .build();
        Resource resource = new ByteArrayResource(normalizedContent.getBytes(StandardCharsets.UTF_8)) {
            @Override
            public String getFilename() {
                return normalizedFileName;
            }
        };
        MarkdownDocumentReader markdownDocumentReader = new MarkdownDocumentReader(resource, config);
        List<Document> parsedDocuments = markdownDocumentReader.get();
        List<Document> normalizedDocuments = new ArrayList<>();
        int sectionIndex = 0;
        for (Document document : parsedDocuments) {
            if (document == null || StringUtils.isBlank(document.getText())) {
                continue;
            }
            Map<String, Object> metadata = new LinkedHashMap<>();
            if (document.getMetadata() != null && !document.getMetadata().isEmpty()) {
                metadata.putAll(document.getMetadata());
            }
            String sectionTitle = StringUtils.trimToEmpty(String.valueOf(metadata.getOrDefault("title", "")));
            if (StringUtils.isBlank(sectionTitle)) {
                sectionTitle = normalizedFileName.replaceFirst("(?i)\\.md$", "");
                metadata.put("title", sectionTitle);
            }
            metadata.put("filename", normalizedFileName);
            metadata.put("fileName", normalizedFileName);
            metadata.put("source", normalizedFileName);
            metadata.put("sectionIndex", sectionIndex);
            String chunkId = buildChunkId(normalizedFileName, sectionTitle, sectionIndex);
            metadata.put("chunkId", chunkId);
            normalizedDocuments.add(new Document(chunkId, document.getText(), metadata));
            sectionIndex++;
        }
        return normalizedDocuments;
    }

    public List<Document> loadSingleMarkDownDocument(String fileName, String content) {
        List<Document> rawDocuments = loadSingleMarkDownSections(fileName, content);
        // 保留关键词抽取能力，但通过批量与缓存降低保存、上传时延。
        return enrichVectorDocuments(rawDocuments);
    }

    /**
     * 基于已切分的文档片段生成向量分片，避免重复解析 markdown。
     * @param sections 已切分片段
     * @return 可写入向量库的文档列表
     */
    public List<Document> enrichVectorDocuments(List<Document> sections) {
        if (sections == null || sections.isEmpty()) {
            return new ArrayList<>();
        }
        List<Document> copiedSections = new ArrayList<>(sections);
        return myCustomKeyWordEnricher.enrichKeyWordToDocument(copiedSections);
    }

    public int countSections(String fileName, String content) {
        return loadSingleMarkDownSections(fileName, content).size();
    }

    private String resolveType(String fileName) {
        if (StringUtils.length(fileName) >= 2) {
            return fileName.substring(0, 2);
        }
        return fileName;
    }

    private String buildChunkId(String fileName, String title, int sectionIndex) {
        String raw = fileName + "::" + StringUtils.defaultString(title) + "::" + sectionIndex;
        return UUID.nameUUIDFromBytes(raw.getBytes(StandardCharsets.UTF_8)).toString();
    }
}
