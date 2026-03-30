package com.lin.linagent.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.bulk.BulkOperation;
import co.elastic.clients.elasticsearch.core.bulk.DeleteOperation;
import com.lin.linagent.domain.dto.KnowledgeDocumentSaveRequest;
import com.lin.linagent.domain.dto.KnowledgeDocumentVO;
import com.lin.linagent.domain.dto.KnowledgeRebuildResultVO;
import com.lin.linagent.elasticsearch.ElasticSearchInitializer;
import com.lin.linagent.elasticsearch.entity.KnowledgeDoc;
import com.lin.linagent.exception.BusinessException;
import com.lin.linagent.exception.ErrorCode;
import com.lin.linagent.rag.etl.CustomDocumentETL;
import com.lin.linagent.scheduler.AddDataToElasticSearch;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.BatchingStrategy;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 知识库后台管理服务
 */
@Service
@Slf4j
public class KnowledgeBaseAdminService {

    private static final String KNOWLEDGE_INDEX = "knowledge_docs";

    /**
     * DashScope embedding 单次最多支持 25 条文本
     */
    private static final int MAX_EMBEDDING_BATCH_SIZE = 25;

    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    private final Path knowledgeRoot = Paths.get(System.getProperty("user.dir"), "src", "main", "resources", "static", "document");

    @Resource
    private CustomDocumentETL customDocumentETL;

    @Resource
    private VectorStore pgVectorVectorStore;

    @Resource
    private BatchingStrategy customTokenCountBatchingStrategy;

    @Resource
    @Qualifier("pgJdbcTemplate")
    private JdbcTemplate pgJdbcTemplate;

    @Resource
    private ElasticsearchClient elasticsearchClient;

    @Resource
    private ElasticSearchInitializer elasticSearchInitializer;

    @Resource
    private AddDataToElasticSearch addDataToElasticSearch;

    @Resource
    private AdminLogService adminLogService;

    @Resource
    private KnowledgeDocumentMetaService knowledgeDocumentMetaService;

    private volatile String lastRebuildTime = "";

    private volatile String vectorStoreFileNameColumn = "";

    private final Object knowledgeIndexMonitor = new Object();

    /**
     * 已保存文档上下文，便于同步或异步索引时复用。
     */
    public record SavedKnowledgeDocumentContext(Path path,
                                               boolean existedBeforeSave,
                                               String previousContent,
                                               String fileName,
                                               String currentContent,
                                               KnowledgeDocumentVO document) {
    }

    @FunctionalInterface
    public interface KnowledgeIndexProgressListener {
        void onProgress(int progress, String stage, String message);

        static KnowledgeIndexProgressListener noop() {
            return (progress, stage, message) -> {
            };
        }
    }

    /**
     * 列出知识库文档
     * @return 文档列表
     */
    public List<KnowledgeDocumentVO> listDocuments() {
        ensureKnowledgeRoot();
        try (var stream = Files.list(knowledgeRoot)) {
            return stream
                    .filter(path -> Files.isRegularFile(path) && path.getFileName().toString().toLowerCase().endsWith(".md"))
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .map(this::toDocumentSummary)
                    .toList();
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "读取知识库文档失败");
        }
    }

    /**
     * 读取知识库文档详情
     * @param fileName 文件名
     * @return 文档详情
     */
    public KnowledgeDocumentVO getDocument(String fileName) {
        Path path = resolveDocumentPath(fileName);
        if (!Files.exists(path)) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "知识库文档不存在");
        }
        try {
            KnowledgeDocumentVO vo = toDocumentSummary(path);
            vo.setContent(Files.readString(path, StandardCharsets.UTF_8));
            return vo;
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "读取知识库文档内容失败");
        }
    }

    /**
     * 保存知识库文档
     * @param request 保存请求
     * @return 保存后的文档
     */
    public KnowledgeDocumentVO saveDocument(KnowledgeDocumentSaveRequest request) {
        SavedKnowledgeDocumentContext context = null;
        try {
            context = persistDocument(request);
            if (Boolean.TRUE.equals(request.getRebuildIndex())) {
                replaceDocumentIndexes(context.fileName(), context.previousContent(), context.currentContent(), KnowledgeIndexProgressListener.noop());
                adminLogService.info("knowledge", "保存知识库文档并同步索引", "fileName=" + context.fileName() + ", sectionCount=" + context.document().getSectionCount());
            } else {
                adminLogService.info("knowledge", "保存知识库文档", "fileName=" + context.fileName());
            }
            knowledgeDocumentMetaService.syncDocumentMeta(context.document());
            return context.document();
        } catch (BusinessException e) {
            if (context != null) {
                rollbackSavedDocument(context.path(), context.existedBeforeSave(), context.previousContent());
                log.error("保存知识库文档失败，fileName={}", context.fileName(), e);
                adminLogService.error("knowledge", "保存知识库文档失败", "fileName=" + context.fileName() + ", message=" + StringUtils.defaultIfBlank(e.getMessage(), e.getClass().getSimpleName()));
            }
            throw e;
        } catch (Exception e) {
            SavedKnowledgeDocumentContext rollbackContext = context != null ? context : extractContext(e);
            if (rollbackContext != null) {
                rollbackSavedDocument(rollbackContext.path(), rollbackContext.existedBeforeSave(), rollbackContext.previousContent());
            }
            String fileName = rollbackContext == null ? "" : rollbackContext.fileName();
            log.error("保存知识库文档失败，fileName={}", fileName, e);
            adminLogService.error("knowledge", "保存知识库文档失败", "fileName=" + fileName + ", message=" + StringUtils.defaultIfBlank(e.getMessage(), e.getClass().getSimpleName()));
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "保存知识库文档失败：" + StringUtils.defaultIfBlank(e.getMessage(), "请检查向量库或检索服务状态"));
        }
    }

    /**
     * 保存文档并准备异步索引
     * @param request 保存请求
     * @return 保存后的上下文
     */
    public SavedKnowledgeDocumentContext saveDocumentForAsyncIndex(KnowledgeDocumentSaveRequest request) {
        SavedKnowledgeDocumentContext context = null;
        try {
            context = persistDocument(request);
            knowledgeDocumentMetaService.syncDocumentMeta(context.document());
            adminLogService.info("knowledge", "保存知识库文档并加入索引队列", "fileName=" + context.fileName() + ", sectionCount=" + context.document().getSectionCount());
            return context;
        } catch (BusinessException e) {
            if (context != null) {
                rollbackSavedDocument(context.path(), context.existedBeforeSave(), context.previousContent());
                log.error("保存知识库文档并创建异步索引任务失败，fileName={}", context.fileName(), e);
                adminLogService.error("knowledge", "保存知识库文档失败", "fileName=" + context.fileName() + ", message=" + StringUtils.defaultIfBlank(e.getMessage(), e.getClass().getSimpleName()));
            }
            throw e;
        } catch (Exception e) {
            SavedKnowledgeDocumentContext rollbackContext = context != null ? context : extractContext(e);
            if (rollbackContext != null) {
                rollbackSavedDocument(rollbackContext.path(), rollbackContext.existedBeforeSave(), rollbackContext.previousContent());
            }
            String fileName = rollbackContext == null ? "" : rollbackContext.fileName();
            log.error("保存知识库文档并创建异步索引任务失败，fileName={}", fileName, e);
            adminLogService.error("knowledge", "保存知识库文档失败", "fileName=" + fileName + ", message=" + StringUtils.defaultIfBlank(e.getMessage(), e.getClass().getSimpleName()));
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "保存知识库文档失败：" + StringUtils.defaultIfBlank(e.getMessage(), "请检查知识库目录或配置"));
        }
    }

    /**
     * 同步单篇文档索引，通常由异步任务调用
     * @param context 已保存文档上下文
     * @param progressListener 进度监听
     */
    public void syncDocumentIndexes(SavedKnowledgeDocumentContext context, KnowledgeIndexProgressListener progressListener) {
        if (context == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "索引上下文不能为空");
        }
        ensureCurrentDocumentVersion(context);
        KnowledgeIndexProgressListener listener = progressListener == null ? KnowledgeIndexProgressListener.noop() : progressListener;
        replaceDocumentIndexes(context.fileName(), context.previousContent(), context.currentContent(), listener);
        adminLogService.info("knowledge", "知识库文档异步索引完成", "fileName=" + context.fileName() + ", sectionCount=" + context.document().getSectionCount());
    }

    /**
     * 删除知识库文档
     * @param fileName 文件名
     * @param rebuildIndex 是否重建索引
     * @return 是否删除成功
     */
    public boolean deleteDocument(String fileName, boolean rebuildIndex) {
        Path path = resolveDocumentPath(fileName);
        try {
            if (!Files.exists(path)) {
                throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "知识库文档不存在");
            }
            String content = Files.readString(path, StandardCharsets.UTF_8);
            boolean deleted = Files.deleteIfExists(path);
            if (!deleted) {
                throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "知识库文档不存在");
            }
            removeDocumentIndexes(fileName, content);
            knowledgeDocumentMetaService.removeByFileName(fileName);
            adminLogService.warn("knowledge", rebuildIndex ? "删除知识库文档并清理索引" : "删除知识库文档", "fileName=" + fileName);
            return true;
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "删除知识库文档失败");
        }
    }

    /**
     * 重建知识库索引
     * @return 重建结果
     */
    public synchronized KnowledgeRebuildResultVO rebuildIndexes() {
        synchronized (knowledgeIndexMonitor) {
            try {
                ensureKnowledgeRoot();
                List<Document> vectorDocuments = customDocumentETL.loadMarkDownDocuments();
                List<KnowledgeDocumentVO> documentList = listDocuments();
                pgJdbcTemplate.execute("DELETE FROM vector_store");
                addVectorDocuments(vectorDocuments);

                elasticSearchInitializer.ensureKnowledgeDocIndex();
                elasticsearchClient.deleteByQuery(request -> request.index(KNOWLEDGE_INDEX).query(query -> query.matchAll(match -> match)));
                List<KnowledgeDoc> knowledgeDocs = addDataToElasticSearch.loadMarkDownToDocument();
                addElasticDocuments(knowledgeDocs);
                knowledgeDocumentMetaService.replaceAllDocumentMeta(documentList);
                updateLastRebuildTime();
                adminLogService.info("knowledge", "重建知识库索引完成", "vectorCount=" + vectorDocuments.size() + ", elasticCount=" + knowledgeDocs.size());
                return buildRebuildResult();
            } catch (BusinessException e) {
                throw e;
            } catch (Exception e) {
                log.error("重建知识库索引失败", e);
                adminLogService.error("knowledge", "重建知识库索引失败", e.getMessage());
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, "重建知识库索引失败：" + e.getMessage());
            }
        }
    }

    /**
     * 获取知识库文件数量
     * @return 数量
     */
    public int getKnowledgeFileCount() {
        ensureKnowledgeDocumentMetaCache();
        return knowledgeDocumentMetaService.countDocumentTotal();
    }

    /**
     * 获取知识库片段数量
     * @return 数量
     */
    public int getKnowledgeSectionCount() {
        ensureKnowledgeDocumentMetaCache();
        return knowledgeDocumentMetaService.countSectionTotal();
    }

    /**
     * 获取向量库记录数
     * @return 数量
     */
    public int getVectorRowCount() {
        Integer count = pgJdbcTemplate.queryForObject("select count(*) from vector_store", Integer.class);
        return count == null ? 0 : count;
    }

    /**
     * 获取 ES 记录数
     * @return 数量
     */
    public long getElasticDocCount() {
        try {
            return elasticsearchClient.count(request -> request.index(KNOWLEDGE_INDEX)).count();
        } catch (Exception e) {
            return 0L;
        }
    }

    /**
     * 获取最近一次重建时间
     * @return 时间
     */
    public String getLastRebuildTime() {
        return lastRebuildTime;
    }

    /**
     * 构建重建结果
     * @return 结果
     */
    public KnowledgeRebuildResultVO buildRebuildResult() {
        KnowledgeRebuildResultVO result = new KnowledgeRebuildResultVO();
        result.setKnowledgeFileCount(getKnowledgeFileCount());
        result.setKnowledgeSectionCount(getKnowledgeSectionCount());
        result.setVectorRowCount(getVectorRowCount());
        result.setElasticDocCount(getElasticDocCount());
        result.setRebuiltAt(StringUtils.defaultIfBlank(lastRebuildTime, DATE_FORMAT.format(new Date())));
        return result;
    }

    /**
     * 转换文档摘要
     * @param path 路径
     * @return 摘要对象
     */
    private KnowledgeDocumentVO toDocumentSummary(Path path) {
        if (path == null || !Files.exists(path)) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "知识库文档不存在");
        }
        try {
            String content = Files.readString(path, StandardCharsets.UTF_8);
            return buildDocumentVO(path, content, false);
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "读取知识库文档摘要失败：" + StringUtils.defaultIfBlank(e.getMessage(), "文件读取异常"));
        }
    }

    /**
     * 解析文档标题
     * @param fileName 文件名
     * @param content 内容
     * @return 标题
     */
    private String resolveDocumentTitle(String fileName, String content) {
        String[] lines = StringUtils.defaultString(content).split("\\r?\\n");
        for (String line : lines) {
            String trimmed = StringUtils.trimToEmpty(line);
            if (trimmed.startsWith("# ")) {
                return trimmed.substring(2).trim();
            }
        }
        return fileName.replace(".md", "");
    }

    /**
     * 规范化知识库文件名
     * @param fileName 文件名
     * @param title 标题
     * @return 文件名
     */
    private String normalizeDocumentFileName(String fileName, String title) {
        String candidate = StringUtils.defaultIfBlank(StringUtils.trimToEmpty(fileName), StringUtils.trimToEmpty(title));
        if (StringUtils.isBlank(candidate)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "文件名不能为空");
        }
        String normalized = candidate.replaceAll("[\\\\/:*?\"<>|]", "_");
        if (!normalized.toLowerCase().endsWith(".md")) {
            normalized += ".md";
        }
        return normalized;
    }

    /**
     * 解析安全路径
     * @param fileName 文件名
     * @return 路径
     */
    private Path resolveDocumentPath(String fileName) {
        if (StringUtils.isBlank(fileName)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "文件名不能为空");
        }
        ensureKnowledgeRoot();
        Path resolved = knowledgeRoot.resolve(fileName.trim()).normalize();
        if (!resolved.startsWith(knowledgeRoot)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "不允许访问知识库目录之外的文件");
        }
        return resolved;
    }

    /**
     * 确保知识库目录存在
     */
    private void ensureKnowledgeRoot() {
        try {
            Files.createDirectories(knowledgeRoot);
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "创建知识库目录失败");
        }
    }

    /**
     * 确保知识库元数据缓存可用
     */
    private void ensureKnowledgeDocumentMetaCache() {
        int metaCount = knowledgeDocumentMetaService.countDocumentTotal();
        if (metaCount > 0) {
            return;
        }
        List<KnowledgeDocumentVO> documentList = listDocuments();
        if (documentList.isEmpty()) {
            return;
        }
        knowledgeDocumentMetaService.replaceAllDocumentMeta(documentList);
    }

    private SavedKnowledgeDocumentContext persistDocument(KnowledgeDocumentSaveRequest request) {
        if (request == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "保存请求不能为空");
        }
        String fileName = normalizeDocumentFileName(request.getFileName(), request.getTitle());
        String title = StringUtils.defaultIfBlank(StringUtils.trimToEmpty(request.getTitle()), fileName.replace(".md", ""));
        String rawContent = StringUtils.trimToEmpty(request.getContent());
        if (StringUtils.isBlank(rawContent)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "知识库内容不能为空");
        }
        Path path = resolveDocumentPath(fileName);
        boolean existedBeforeSave = Files.exists(path);
        String previousContent = "";
        try {
            if (existedBeforeSave) {
                previousContent = Files.readString(path, StandardCharsets.UTF_8);
            }
            String currentContent = buildDocumentContent(title, rawContent);
            ensureKnowledgeRoot();
            Files.writeString(path, currentContent, StandardCharsets.UTF_8);
            KnowledgeDocumentVO documentVO = buildDocumentVO(path, currentContent, true);
            return new SavedKnowledgeDocumentContext(path, existedBeforeSave, previousContent, fileName, currentContent, documentVO);
        } catch (IOException e) {
            throw new KnowledgeDocumentPersistException("读取或写入知识库文档失败", e,
                    new SavedKnowledgeDocumentContext(path, existedBeforeSave, previousContent, fileName, "", null));
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new KnowledgeDocumentPersistException("保存知识库文档失败", e,
                    new SavedKnowledgeDocumentContext(path, existedBeforeSave, previousContent, fileName, "", null));
        }
    }

    private String buildDocumentContent(String title, String content) {
        if (StringUtils.startsWith(content, "# ")) {
            return content;
        }
        return "# " + title + "\n\n" + content;
    }

    private KnowledgeDocumentVO buildDocumentVO(Path path, String content, boolean includeContent) throws IOException {
        KnowledgeDocumentVO vo = new KnowledgeDocumentVO();
        vo.setFileName(path.getFileName().toString());
        vo.setTitle(resolveDocumentTitle(path.getFileName().toString(), content));
        vo.setSize(Files.size(path));
        vo.setUpdateTime(DATE_FORMAT.format(new Date(Files.getLastModifiedTime(path).toMillis())));
        vo.setSectionCount(customDocumentETL.countSections(path.getFileName().toString(), content));
        if (includeContent) {
            vo.setContent(content);
        }
        return vo;
    }

    private SavedKnowledgeDocumentContext extractContext(Exception exception) {
        if (exception instanceof KnowledgeDocumentPersistException persistException) {
            return persistException.getContext();
        }
        return null;
    }

    private void ensureCurrentDocumentVersion(SavedKnowledgeDocumentContext context) {
        Path path = resolveDocumentPath(context.fileName());
        if (!Files.exists(path)) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "文档已被删除，本次索引任务已跳过");
        }
        try {
            String latestContent = Files.readString(path, StandardCharsets.UTF_8);
            if (!StringUtils.equals(latestContent, context.currentContent())) {
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "文档内容已更新，本次旧任务已跳过，请以最新结果为准");
            }
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "读取最新知识库文档失败");
        }
    }

    private void replaceDocumentIndexes(String fileName, String previousContent, String currentContent, KnowledgeIndexProgressListener progressListener) {
        synchronized (knowledgeIndexMonitor) {
            KnowledgeIndexProgressListener listener = progressListener == null ? KnowledgeIndexProgressListener.noop() : progressListener;
            listener.onProgress(12, "CLEANUP", "正在清理旧向量索引");
            deleteVectorDocuments(fileName);
            listener.onProgress(34, "PARSE", "正在解析文档并切分知识片段");
            List<Document> sectionDocuments = customDocumentETL.loadSingleMarkDownSections(fileName, currentContent);
            listener.onProgress(52, "VECTOR_PREPARE", "正在为分片生成向量索引数据");
            List<Document> vectorDocuments = customDocumentETL.enrichVectorDocuments(sectionDocuments);
            listener.onProgress(72, "VECTOR", "正在写入向量索引");
            addVectorDocuments(vectorDocuments);
            listener.onProgress(90, "ELASTIC", "正在将分片同步到 ES 搜索引擎");
            syncElasticDocumentIndexes(fileName, previousContent, currentContent, sectionDocuments);
            updateLastRebuildTime();
            listener.onProgress(100, "COMPLETED", "知识库索引同步完成");
        }
    }

    private void removeDocumentIndexes(String fileName, String content) {
        deleteVectorDocuments(fileName);
        if (StringUtils.isNotBlank(content)) {
            clearElasticDocumentIndexes(fileName, content);
        }
        updateLastRebuildTime();
    }

    private void addVectorDocuments(List<Document> vectorDocuments) {
        if (vectorDocuments == null || vectorDocuments.isEmpty()) {
            return;
        }
        List<Document> normalizedDocuments = new ArrayList<>();
        for (Document vectorDocument : vectorDocuments) {
            Document normalizedDocument = normalizeVectorDocumentMetadata(vectorDocument);
            if (normalizedDocument != null) {
                normalizedDocuments.add(normalizedDocument);
            }
        }
        Map<String, List<Document>> documentGroup = new LinkedHashMap<>();
        for (Document normalizedDocument : normalizedDocuments) {
            String fileName = pickVectorFileName(normalizedDocument.getMetadata());
            if (StringUtils.isBlank(fileName)) {
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, "向量分片缺少 fileName 字段");
            }
            documentGroup.computeIfAbsent(fileName, key -> new ArrayList<>()).add(normalizedDocument);
        }
        for (Map.Entry<String, List<Document>> entry : documentGroup.entrySet()) {
            List<List<Document>> batchedDocuments = customTokenCountBatchingStrategy.batch(entry.getValue());
            for (List<Document> batch : batchedDocuments) {
                if (batch == null || batch.isEmpty()) {
                    continue;
                }
                // 这里继续按 25 条拆分，避免 embedding 接口触发单次输入上限
                for (int start = 0; start < batch.size(); start += MAX_EMBEDDING_BATCH_SIZE) {
                    int end = Math.min(start + MAX_EMBEDDING_BATCH_SIZE, batch.size());
                    List<Document> currentBatch = new ArrayList<>(batch.subList(start, end));
                    pgVectorVectorStore.add(currentBatch);
                    fillVectorStoreFileName(currentBatch, entry.getKey());
                }
            }
        }
    }

    private void addElasticDocuments(List<KnowledgeDoc> knowledgeDocs) throws IOException {
        if (knowledgeDocs == null || knowledgeDocs.isEmpty()) {
            return;
        }
        elasticSearchInitializer.ensureKnowledgeDocIndex();
        List<BulkOperation> operations = new ArrayList<>();
        for (KnowledgeDoc knowledgeDoc : knowledgeDocs) {
            operations.add(BulkOperation.of(operation -> operation
                    .index(index -> index
                            .index(KNOWLEDGE_INDEX)
                            .id(knowledgeDoc.getId())
                            .document(knowledgeDoc))));
        }
        verifyBulkResponse(elasticsearchClient.bulk(BulkRequest.of(request -> request.operations(operations))), "写入知识库 ES 文档失败");
        refreshElasticKnowledgeIndex();
    }

    private void deleteVectorDocuments(String fileName) {
        if (StringUtils.isBlank(fileName)) {
            return;
        }
        String columnName = quoteIdentifier(resolveVectorStoreFileNameColumn());
        pgJdbcTemplate.update("DELETE FROM vector_store WHERE " + columnName + " = ?", fileName);
    }

    private void deleteElasticDocuments(String fileName, String content) throws IOException {
        if (StringUtils.isBlank(fileName) || StringUtils.isBlank(content)) {
            return;
        }
        elasticSearchInitializer.ensureKnowledgeDocIndex();
        Set<String> documentIds = new LinkedHashSet<>();
        List<KnowledgeDoc> knowledgeDocs = addDataToElasticSearch.loadSingleMarkDownToDocument(fileName, content);
        for (KnowledgeDoc knowledgeDoc : knowledgeDocs) {
            if (knowledgeDoc != null && StringUtils.isNotBlank(knowledgeDoc.getId())) {
                documentIds.add(knowledgeDoc.getId());
            }
        }
        documentIds.addAll(addDataToElasticSearch.loadLegacyDocumentIds(fileName, content));
        if (!documentIds.isEmpty()) {
            List<BulkOperation> deleteOperations = new ArrayList<>();
            for (String documentId : documentIds) {
                deleteOperations.add(BulkOperation.of(operation -> operation
                        .delete(DeleteOperation.of(delete -> delete
                                .index(KNOWLEDGE_INDEX)
                                .id(documentId)))));
            }
            verifyBulkResponse(elasticsearchClient.bulk(BulkRequest.of(request -> request.operations(deleteOperations))), "删除知识库 ES 文档失败");
            refreshElasticKnowledgeIndex();
        }
    }

    private void syncElasticDocumentIndexes(String fileName, String previousContent, String currentContent, List<Document> sectionDocuments) {
        try {
            List<KnowledgeDoc> knowledgeDocs = (sectionDocuments == null || sectionDocuments.isEmpty())
                    ? addDataToElasticSearch.loadSingleMarkDownToDocument(fileName, currentContent)
                    : addDataToElasticSearch.buildKnowledgeDocsFromSections(fileName, sectionDocuments);
            applyElasticDocumentChanges(fileName, previousContent, knowledgeDocs);
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "同步知识库 ES 文档失败：" + StringUtils.defaultIfBlank(e.getMessage(), "Elasticsearch 写入异常"));
        }
    }

    private void clearElasticDocumentIndexes(String fileName, String content) {
        try {
            deleteElasticDocuments(fileName, content);
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "清理知识库 ES 文档失败：" + StringUtils.defaultIfBlank(e.getMessage(), "Elasticsearch 写入异常"));
        }
    }

    private void rollbackSavedDocument(Path path, boolean existedBeforeSave, String previousContent) {
        try {
            if (existedBeforeSave) {
                Files.writeString(path, StringUtils.defaultString(previousContent), StandardCharsets.UTF_8);
            } else {
                Files.deleteIfExists(path);
            }
        } catch (Exception rollbackException) {
            log.error("保存知识库文档失败后回滚文件失败，path={}", path, rollbackException);
        }
    }

    private Document normalizeVectorDocumentMetadata(Document document) {
        if (document == null || StringUtils.isBlank(document.getText())) {
            return null;
        }
        Map<String, Object> metadata = new LinkedHashMap<>();
        if (document.getMetadata() != null && !document.getMetadata().isEmpty()) {
            metadata.putAll(document.getMetadata());
        }
        String fileName = pickVectorFileName(metadata);
        if (StringUtils.isNotBlank(fileName)) {
            metadata.put("fileName", fileName);
            metadata.put("filename", fileName);
            metadata.put("source", fileName);
        }
        return new Document(document.getId(), document.getText(), metadata);
    }

    private String pickVectorFileName(Map<String, Object> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return "";
        }
        Object fileName = metadata.get("fileName");
        if (fileName != null && StringUtils.isNotBlank(String.valueOf(fileName))) {
            return String.valueOf(fileName).trim();
        }
        Object filename = metadata.get("filename");
        if (filename != null && StringUtils.isNotBlank(String.valueOf(filename))) {
            return String.valueOf(filename).trim();
        }
        Object source = metadata.get("source");
        if (source != null && StringUtils.isNotBlank(String.valueOf(source))) {
            return String.valueOf(source).trim();
        }
        return "";
    }

    private void fillVectorStoreFileName(List<Document> documents, String fileName) {
        if (documents == null || documents.isEmpty() || StringUtils.isBlank(fileName)) {
            return;
        }
        String columnName = quoteIdentifier(resolveVectorStoreFileNameColumn());
        List<String> idList = documents.stream()
                .map(Document::getId)
                .filter(StringUtils::isNotBlank)
                .toList();
        if (idList.isEmpty()) {
            return;
        }
        StringBuilder placeholders = new StringBuilder();
        List<Object> args = new ArrayList<>();
        args.add(fileName);
        for (String id : idList) {
            if (placeholders.length() > 0) {
                placeholders.append(",");
            }
            placeholders.append("?");
            args.add(id);
        }
        pgJdbcTemplate.update("UPDATE vector_store SET " + columnName + " = ? WHERE id IN (" + placeholders + ")", args.toArray());
    }

    private String resolveVectorStoreFileNameColumn() {
        if (StringUtils.isNotBlank(vectorStoreFileNameColumn)) {
            return vectorStoreFileNameColumn;
        }
        List<String> columnList = pgJdbcTemplate.queryForList("""
                SELECT column_name
                FROM information_schema.columns
                WHERE table_schema = 'public'
                  AND table_name = 'vector_store'
                  AND column_name IN ('fileName', 'filename', 'file_name')
                """, String.class);
        if (columnList == null || columnList.isEmpty()) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "vector_store 表缺少 fileName 字段");
        }
        vectorStoreFileNameColumn = columnList.get(0);
        return vectorStoreFileNameColumn;
    }

    private String quoteIdentifier(String columnName) {
        if (StringUtils.isBlank(columnName)) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "vector_store 的 fileName 字段名无效");
        }
        return "\"" + columnName.replace("\"", "") + "\"";
    }

    private void updateLastRebuildTime() {
        lastRebuildTime = DATE_FORMAT.format(new Date());
    }

    /**
     * 主动刷新 ES 索引，让后台总览和搜索结果尽快看到最新分片。
     */
    private void refreshElasticKnowledgeIndex() {
        try {
            elasticsearchClient.indices().refresh(request -> request.index(KNOWLEDGE_INDEX));
        } catch (Exception e) {
            log.warn("刷新知识库 ES 索引失败，index={}, message={}", KNOWLEDGE_INDEX, e.getMessage());
        }
    }

    /**
     * 将删除旧分片与写入新分片合并成一次 bulk，减少 ES 往返次数。
     */
    private void applyElasticDocumentChanges(String fileName, String previousContent, List<KnowledgeDoc> currentKnowledgeDocs) throws IOException {
        elasticSearchInitializer.ensureKnowledgeDocIndex();
        List<KnowledgeDoc> normalizedKnowledgeDocs = currentKnowledgeDocs == null ? List.of() : currentKnowledgeDocs;
        Set<String> currentDocumentIds = new LinkedHashSet<>();
        for (KnowledgeDoc knowledgeDoc : normalizedKnowledgeDocs) {
            if (knowledgeDoc != null && StringUtils.isNotBlank(knowledgeDoc.getId())) {
                currentDocumentIds.add(knowledgeDoc.getId());
            }
        }
        Set<String> staleDocumentIds = collectStaleElasticDocumentIds(fileName, previousContent, currentDocumentIds);
        if (staleDocumentIds.isEmpty() && normalizedKnowledgeDocs.isEmpty()) {
            return;
        }
        List<BulkOperation> operations = new ArrayList<>();
        for (String staleDocumentId : staleDocumentIds) {
            operations.add(BulkOperation.of(operation -> operation
                    .delete(DeleteOperation.of(delete -> delete
                            .index(KNOWLEDGE_INDEX)
                            .id(staleDocumentId)))));
        }
        for (KnowledgeDoc knowledgeDoc : normalizedKnowledgeDocs) {
            if (knowledgeDoc == null || StringUtils.isBlank(knowledgeDoc.getId())) {
                continue;
            }
            operations.add(BulkOperation.of(operation -> operation
                    .index(index -> index
                            .index(KNOWLEDGE_INDEX)
                            .id(knowledgeDoc.getId())
                            .document(knowledgeDoc))));
        }
        verifyBulkResponse(elasticsearchClient.bulk(BulkRequest.of(request -> request.operations(operations))), "同步知识库 ES 文档失败");
        refreshElasticKnowledgeIndex();
    }

    /**
     * 只删除旧内容中已经不存在的分片，避免整份文档先删后加。
     */
    private Set<String> collectStaleElasticDocumentIds(String fileName, String previousContent, Set<String> currentDocumentIds) {
        Set<String> staleDocumentIds = new LinkedHashSet<>();
        if (StringUtils.isBlank(fileName) || StringUtils.isBlank(previousContent)) {
            return staleDocumentIds;
        }
        List<KnowledgeDoc> previousKnowledgeDocs = addDataToElasticSearch.loadSingleMarkDownToDocument(fileName, previousContent);
        for (KnowledgeDoc previousKnowledgeDoc : previousKnowledgeDocs) {
            if (previousKnowledgeDoc == null || StringUtils.isBlank(previousKnowledgeDoc.getId())) {
                continue;
            }
            if (currentDocumentIds == null || !currentDocumentIds.contains(previousKnowledgeDoc.getId())) {
                staleDocumentIds.add(previousKnowledgeDoc.getId());
            }
        }
        staleDocumentIds.addAll(addDataToElasticSearch.loadLegacyDocumentIds(fileName, previousContent));
        if (currentDocumentIds != null && !currentDocumentIds.isEmpty()) {
            staleDocumentIds.removeAll(currentDocumentIds);
        }
        return staleDocumentIds;
    }

    /**
     * 检查 ES bulk 响应，确保成功状态不会掩盖部分失败。
     */
    private void verifyBulkResponse(co.elastic.clients.elasticsearch.core.BulkResponse response, String defaultMessage) {
        if (response == null || !response.errors()) {
            return;
        }
        StringBuilder errorBuilder = new StringBuilder();
        response.items().forEach(item -> {
            if (item != null && item.error() != null) {
                if (errorBuilder.length() > 0) {
                    errorBuilder.append(" | ");
                }
                errorBuilder.append(item.id()).append(":").append(item.error().reason());
            }
        });
        throw new BusinessException(ErrorCode.SYSTEM_ERROR, defaultMessage + "：" + StringUtils.defaultIfBlank(errorBuilder.toString(), "bulk 请求返回错误"));
    }

    private static final class KnowledgeDocumentPersistException extends RuntimeException {
        private final SavedKnowledgeDocumentContext context;

        private KnowledgeDocumentPersistException(String message, Throwable cause, SavedKnowledgeDocumentContext context) {
            super(message, cause);
            this.context = context;
        }

        private SavedKnowledgeDocumentContext getContext() {
            return context;
        }
    }
}
