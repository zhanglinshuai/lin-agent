package com.lin.linagent.scheduler;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import co.elastic.clients.elasticsearch.core.bulk.BulkOperation;
import com.lin.linagent.contant.CommonVariables;
import com.lin.linagent.elasticsearch.entity.KnowledgeDoc;
import com.lin.linagent.rag.etl.CustomDocumentETL;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.document.Document;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;

/**
 * 定时向ElasticSearch定时添加数据的方法
 */
@Slf4j
@Component
public class AddDataToElasticSearch {

    private static final Path KNOWLEDGE_ROOT = Paths.get(System.getProperty("user.dir"), "src", "main", "resources", "static", "document");

    @Resource
    private final ElasticsearchClient client;

    @Resource
    private CustomDocumentETL customDocumentETL;

    public AddDataToElasticSearch(ElasticsearchClient client) {
        this.client = client;
    }

    /**
     * 将markdown格式的文件转换为document格式
     * @return
     */
    public List<KnowledgeDoc> loadMarkDownToDocument() {
        List<KnowledgeDoc> knowledgeDocs = new ArrayList<>();
        if (!Files.exists(KNOWLEDGE_ROOT)) {
            return knowledgeDocs;
        }
        try (var stream = Files.list(KNOWLEDGE_ROOT)) {
            List<Path> fileList = stream
                    .filter(path -> Files.isRegularFile(path) && path.getFileName().toString().toLowerCase().endsWith(".md"))
                    .sorted()
                    .toList();
            for (Path path : fileList) {
                knowledgeDocs.addAll(loadSingleMarkDownToDocument(path.getFileName().toString(), Files.readString(path, StandardCharsets.UTF_8)));
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return knowledgeDocs;
    }

    /**
     * 将单个 markdown 文档转换为 Elasticsearch 文档
     * @param fileName 文件名
     * @param content 文档内容
     * @return 文档列表
     */
    public List<KnowledgeDoc> loadSingleMarkDownToDocument(String fileName, String content) {
        List<Document> sections = customDocumentETL.loadSingleMarkDownSections(fileName, content);
        return buildKnowledgeDocsFromSections(fileName, sections);
    }

    /**
     * 将已切分的文档片段转换为 Elasticsearch 文档。
     * @param fileName 文件名
     * @param sections 已切分片段
     * @return 文档列表
     */
    public List<KnowledgeDoc> buildKnowledgeDocsFromSections(String fileName, List<Document> sections) {
        List<KnowledgeDoc> knowledgeDocs = new ArrayList<>();
        if (sections == null || sections.isEmpty()) {
            return knowledgeDocs;
        }
        String emotion = resolveEmotion(fileName);
        for (Document section : sections) {
            if (section == null || StringUtils.isBlank(section.getText())) {
                continue;
            }
            KnowledgeDoc knowledgeDoc = new KnowledgeDoc();
            String title = StringUtils.trimToEmpty(String.valueOf(section.getMetadata().getOrDefault("title", "")));
            int sectionIndex = resolveSectionIndex(section.getMetadata().get("sectionIndex"), knowledgeDocs.size());
            String chunkId = StringUtils.trimToEmpty(String.valueOf(section.getMetadata().getOrDefault("chunkId", "")));
            if (StringUtils.isBlank(chunkId)) {
                chunkId = buildChunkId(fileName, title, sectionIndex);
            }
            knowledgeDoc.setId(chunkId);
            knowledgeDoc.setFileName(StringUtils.trimToEmpty(fileName));
            knowledgeDoc.setTitle(StringUtils.defaultIfBlank(title, buildFallbackTitle(fileName)));
            knowledgeDoc.setContent(section.getText());
            knowledgeDoc.setEmotion(emotion);
            knowledgeDocs.add(knowledgeDoc);
        }
        return knowledgeDocs;
    }

    /**
     * 兼容旧版四级标题分片生成的 Elasticsearch 文档 id
     * @param fileName 文件名
     * @param content 文档内容
     * @return 旧版 id 列表
     */
    public List<String> loadLegacyDocumentIds(String fileName, String content) {
        List<String> ids = new ArrayList<>();
        if (StringUtils.isAnyBlank(fileName, content)) {
            return ids;
        }
        LinkedHashSet<String> titleSet = new LinkedHashSet<>();
        String[] lines = content.split("\\r?\\n");
        for (String line : lines) {
            String trimmedLine = StringUtils.trimToEmpty(line);
            if (!trimmedLine.startsWith("####")) {
                continue;
            }
            String title = StringUtils.trimToEmpty(trimmedLine.substring(4));
            if (StringUtils.isBlank(title) || !titleSet.add(title)) {
                continue;
            }
            ids.add(UUID.nameUUIDFromBytes((fileName + "::" + title).getBytes(StandardCharsets.UTF_8)).toString());
        }
        return ids;
    }


    /**
     * 将Knowledge增量导入到Elasticsearch
     */
    @Scheduled(cron = "0 0 23 * * 0")
    public void addDataToElasticSearch() throws IOException {
        List<BulkOperation> operations = new ArrayList<>();
        List<KnowledgeDoc> knowledgeDocs = loadMarkDownToDocument();
        for (KnowledgeDoc doc : knowledgeDocs) {
            operations.add(BulkOperation.of(b -> b
                    .update(idx -> idx
                            .index("knowledge_docs")
                            .id(doc.getId())
                            .action(a -> a
                                    .doc(doc)
                                    //不存在就插入
                                    .docAsUpsert(true))
                    )
            ));
            if (operations.size() >= CommonVariables.BITCH_ELASTIC) {
                BulkResponse response = client.bulk(BulkRequest.of(b -> b.operations(operations)));
                if (response.errors()) {
                    log.error("文档索引失败！");
                    response.items().forEach(item -> {
                        if (item.error() != null) {
                            log.error("文档{} 失败:{}", item.id(), item.error().reason());
                        }
                    });
                } else {
                    log.info("成功同步增量{}条文档", knowledgeDocs.size());
                }
                //清空 bulk请求
                operations.clear();
            }
        }
        if (!operations.isEmpty()) {
            BulkResponse response = client.bulk(BulkRequest.of(b -> b.operations(operations)));
            if (response.errors()) {
                log.error("文档索引失败！");
                response.items().forEach(item -> {
                    if (item.error() != null) {
                        log.error("文档{} 失败:{}", item.id(), item.error().reason());
                    }
                });
            } else {
                log.info("成功同步增量{}条文档", knowledgeDocs.size());
            }
        }

    }

    private String resolveEmotion(String fileName) {
        String normalizedFileName = StringUtils.trimToEmpty(fileName);
        if (normalizedFileName.length() >= 2) {
            return normalizedFileName.substring(0, 2);
        }
        return normalizedFileName;
    }

    private int resolveSectionIndex(Object value, int defaultValue) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (Exception e) {
            return defaultValue;
        }
    }

    private String buildChunkId(String fileName, String title, int sectionIndex) {
        String raw = StringUtils.trimToEmpty(fileName) + "::" + StringUtils.defaultString(title) + "::" + sectionIndex;
        return UUID.nameUUIDFromBytes(raw.getBytes(StandardCharsets.UTF_8)).toString();
    }

    private String buildFallbackTitle(String fileName) {
        return StringUtils.trimToEmpty(fileName).replaceFirst("(?i)\\.md$", "");
    }

}
