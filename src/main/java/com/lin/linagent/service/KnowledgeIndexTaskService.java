package com.lin.linagent.service;

import com.lin.linagent.domain.dto.KnowledgeIndexTaskVO;
import com.lin.linagent.domain.dto.KnowledgeUploadResultVO;
import com.lin.linagent.exception.BusinessException;
import com.lin.linagent.exception.ErrorCode;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 知识库索引任务服务
 */
@Service
@Slf4j
public class KnowledgeIndexTaskService {

    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    private final Map<String, KnowledgeIndexTaskVO> taskStore = new ConcurrentHashMap<>();

    private final ExecutorService taskExecutor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable);
        thread.setName("knowledge-index-worker");
        thread.setDaemon(true);
        return thread;
    });

    @Resource
    private KnowledgeBaseAdminService knowledgeBaseAdminService;

    @Resource
    private AdminLogService adminLogService;

    /**
     * 保存文档并创建异步索引任务
     * @param request 保存请求
     * @return 上传结果
     */
    public KnowledgeUploadResultVO submitUploadTask(com.lin.linagent.domain.dto.KnowledgeDocumentSaveRequest request) {
        KnowledgeBaseAdminService.SavedKnowledgeDocumentContext context = knowledgeBaseAdminService.saveDocumentForAsyncIndex(request);
        KnowledgeUploadResultVO result = new KnowledgeUploadResultVO();
        result.setDocument(context.document());
        if (!Boolean.TRUE.equals(request.getRebuildIndex())) {
            return result;
        }
        KnowledgeIndexTaskVO task = createTask(context.fileName());
        result.setTask(copyTask(task));
        CompletableFuture.runAsync(() -> runTask(task.getTaskId(), context), taskExecutor);
        return result;
    }

    /**
     * 查询索引任务状态
     * @param taskId 任务ID
     * @return 任务快照
     */
    public KnowledgeIndexTaskVO getTask(String taskId) {
        String normalizedTaskId = StringUtils.trimToEmpty(taskId);
        if (StringUtils.isBlank(normalizedTaskId)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "任务ID不能为空");
        }
        KnowledgeIndexTaskVO task = taskStore.get(normalizedTaskId);
        if (task == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "索引任务不存在或已过期");
        }
        return copyTask(task);
    }

    @PreDestroy
    public void shutdown() {
        taskExecutor.shutdown();
    }

    private KnowledgeIndexTaskVO createTask(String fileName) {
        String now = now();
        KnowledgeIndexTaskVO task = new KnowledgeIndexTaskVO();
        task.setTaskId(java.util.UUID.randomUUID().toString());
        task.setFileName(StringUtils.trimToEmpty(fileName));
        task.setStatus("PENDING");
        task.setStage("QUEUED");
        task.setProgress(5);
        task.setMessage("文档已上传，正在排队同步索引");
        task.setCreatedAt(now);
        taskStore.put(task.getTaskId(), task);
        return task;
    }

    private void runTask(String taskId, KnowledgeBaseAdminService.SavedKnowledgeDocumentContext context) {
        updateTask(taskId, task -> {
            task.setStatus("RUNNING");
            task.setStage("STARTED");
            task.setProgress(10);
            task.setStartedAt(now());
            task.setMessage("开始同步知识库索引");
        });
        try {
            knowledgeBaseAdminService.syncDocumentIndexes(context, (progress, stage, message) ->
                    updateTask(taskId, task -> {
                        task.setStatus("RUNNING");
                        task.setStage(StringUtils.defaultIfBlank(stage, "RUNNING"));
                        task.setProgress(Math.max(10, Math.min(100, progress)));
                        task.setMessage(StringUtils.defaultIfBlank(message, "正在同步知识库索引"));
                    }));
            updateTask(taskId, task -> {
                task.setStatus("SUCCEEDED");
                task.setStage("COMPLETED");
                task.setProgress(100);
                task.setMessage("知识库文档已上传，索引同步完成");
                task.setFinishedAt(now());
            });
        } catch (Exception e) {
            log.error("知识库异步索引任务执行失败，taskId={}, fileName={}", taskId, context.fileName(), e);
            adminLogService.error("knowledge", "知识库异步索引失败", "taskId=" + taskId + ", fileName=" + context.fileName() + ", message=" + StringUtils.defaultIfBlank(e.getMessage(), e.getClass().getSimpleName()));
            updateTask(taskId, task -> {
                task.setStatus("FAILED");
                task.setStage("FAILED");
                task.setFinishedAt(now());
                task.setMessage("文档已保存，但索引同步失败：" + StringUtils.defaultIfBlank(e.getMessage(), "请稍后重试重建知识库索引"));
                int currentProgress = task.getProgress() == null ? 10 : task.getProgress();
                task.setProgress(Math.max(10, Math.min(99, currentProgress)));
            });
        }
    }

    private void updateTask(String taskId, java.util.function.Consumer<KnowledgeIndexTaskVO> updater) {
        taskStore.computeIfPresent(taskId, (key, previous) -> {
            KnowledgeIndexTaskVO next = copyTask(previous);
            updater.accept(next);
            return next;
        });
    }

    private KnowledgeIndexTaskVO copyTask(KnowledgeIndexTaskVO source) {
        if (source == null) {
            return null;
        }
        KnowledgeIndexTaskVO target = new KnowledgeIndexTaskVO();
        target.setTaskId(source.getTaskId());
        target.setFileName(source.getFileName());
        target.setStatus(source.getStatus());
        target.setStage(source.getStage());
        target.setProgress(source.getProgress());
        target.setMessage(source.getMessage());
        target.setCreatedAt(source.getCreatedAt());
        target.setStartedAt(source.getStartedAt());
        target.setFinishedAt(source.getFinishedAt());
        return target;
    }

    private String now() {
        return DATE_FORMAT.format(new Date());
    }
}
