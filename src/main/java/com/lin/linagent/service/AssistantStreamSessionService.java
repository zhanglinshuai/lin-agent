package com.lin.linagent.service;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.util.Date;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

/**
 * 统一助手流式会话续连服务
 */
@Service
@Slf4j
public class AssistantStreamSessionService {

    private static final long SESSION_TTL_MILLIS = 5 * 60 * 1000L;

    private final Gson gson = new Gson();

    private final Map<String, StreamSession> sessions = new ConcurrentHashMap<>();

    private final AdminLogService adminLogService;

    public AssistantStreamSessionService(AdminLogService adminLogService) {
        this.adminLogService = adminLogService;
    }

    /**
     * 打开或续连流式会话
     * @param requestId 请求id
     * @param userId 用户id
     * @param resume 是否为刷新续连
     * @param streamSupplier 实际执行流
     * @return 事件流
     */
    public Flux<String> openOrResume(String requestId, String userId, boolean resume, Supplier<Flux<String>> streamSupplier) {
        if (StringUtils.isAnyBlank(requestId, userId) || streamSupplier == null) {
            return streamSupplier == null ? Flux.empty() : streamSupplier.get();
        }
        cleanupExpiredSessions();
        String sessionKey = buildSessionKey(requestId, userId);
        StreamSession existing = sessions.get(sessionKey);
        if (existing != null && !existing.isExpired()) {
            existing.touch();
            adminLogService.info("stream", "续连进行中的会话", "requestId=" + requestId + ", userId=" + userId);
            return existing.asFlux();
        }
        StreamSession created = new StreamSession(sessionKey, requestId, userId, resume);
        StreamSession previous = sessions.putIfAbsent(sessionKey, created);
        if (previous != null && !previous.isExpired()) {
            previous.touch();
            adminLogService.info("stream", "命中已有流式会话", "requestId=" + requestId + ", userId=" + userId);
            return previous.asFlux();
        }
        if (previous != null) {
            sessions.put(sessionKey, created);
        }
        startSession(created, streamSupplier);
        adminLogService.info("stream", resume ? "刷新后重建流式会话" : "创建新的流式会话", "requestId=" + requestId + ", userId=" + userId);
        return created.asFlux();
    }

    /**
     * 当前活跃会话数
     * @return 数量
     */
    public int getActiveSessionCount() {
        cleanupExpiredSessions();
        return (int) sessions.values().stream().filter(session -> !session.isCompleted()).count();
    }

    /**
     * 启动实际执行流
     * @param session 会话
     * @param streamSupplier 执行器
     */
    private void startSession(StreamSession session, Supplier<Flux<String>> streamSupplier) {
        Flux<String> stream;
        try {
            stream = streamSupplier.get();
        } catch (Exception e) {
            log.error("创建流式会话失败，requestId={}", session.getRequestId(), e);
            session.emit(buildInternalErrorEvent("创建流式会话失败：" + e.getMessage()));
            session.complete();
            adminLogService.error("stream", "创建流式会话失败", "requestId=" + session.getRequestId() + ", error=" + e.getMessage());
            return;
        }
        stream.subscribe(
                session::emit,
                error -> {
                    log.error("流式会话执行失败，requestId={}", session.getRequestId(), error);
                    session.emit(buildInternalErrorEvent("会话执行失败：" + error.getMessage()));
                    session.complete();
                    adminLogService.error("stream", "流式会话执行失败", "requestId=" + session.getRequestId() + ", error=" + error.getMessage());
                },
                () -> {
                    session.complete();
                    adminLogService.info("stream", "流式会话执行完成", "requestId=" + session.getRequestId() + ", userId=" + session.getUserId());
                }
        );
    }

    /**
     * 清理过期会话
     */
    private void cleanupExpiredSessions() {
        sessions.entrySet().removeIf(entry -> entry.getValue() == null || entry.getValue().isExpired());
    }

    /**
     * 构建内部错误事件
     * @param content 内容
     * @return 事件字符串
     */
    private String buildInternalErrorEvent(String content) {
        JsonObject event = new JsonObject();
        event.addProperty("type", "error");
        event.addProperty("mode", "agent");
        event.addProperty("label", "任务执行");
        event.addProperty("tag", "general_assistance");
        event.addProperty("content", StringUtils.defaultString(content));
        event.addProperty("reason", "会话执行异常");
        return gson.toJson(event);
    }

    /**
     * 构建会话key
     * @param requestId 请求id
     * @param userId 用户id
     * @return key
     */
    private String buildSessionKey(String requestId, String userId) {
        return userId + "::" + requestId;
    }

    /**
     * 流式会话
     */
    private class StreamSession {
        private final String key;
        private final String requestId;
        private final String userId;
        private final Sinks.Many<String> sink = Sinks.many().replay().all();
        private final AtomicLong sequence = new AtomicLong(0);
        private final boolean resumeRestarted;
        private volatile boolean completed;
        private volatile boolean firstEvent = true;
        private volatile long updatedAt = System.currentTimeMillis();
        private volatile long completedAt = 0L;

        StreamSession(String key, String requestId, String userId, boolean resumeRestarted) {
            this.key = key;
            this.requestId = requestId;
            this.userId = userId;
            this.resumeRestarted = resumeRestarted;
        }

        public void emit(String rawEvent) {
            if (StringUtils.isBlank(rawEvent)) {
                return;
            }
            this.touch();
            long eventSeq = sequence.incrementAndGet();
            String decorated = decorateEvent(rawEvent, eventSeq, firstEvent && resumeRestarted);
            firstEvent = false;
            sink.tryEmitNext(decorated);
        }

        public void complete() {
            this.completed = true;
            this.completedAt = System.currentTimeMillis();
            this.touch();
            sink.tryEmitComplete();
        }

        public Flux<String> asFlux() {
            return sink.asFlux().doOnSubscribe(subscription -> this.touch());
        }

        public void touch() {
            this.updatedAt = System.currentTimeMillis();
        }

        public boolean isCompleted() {
            return completed;
        }

        public boolean isExpired() {
            long now = System.currentTimeMillis();
            if (!completed) {
                return now - updatedAt > SESSION_TTL_MILLIS;
            }
            return now - Math.max(completedAt, updatedAt) > SESSION_TTL_MILLIS;
        }

        public String getRequestId() {
            return requestId;
        }

        public String getUserId() {
            return userId;
        }

        /**
         * 给事件补充序号和续连信息
         * @param rawEvent 原始事件
         * @param eventSeq 序号
         * @param appendResumeRestarted 是否标记为续连后重建
         * @return 新事件
         */
        private String decorateEvent(String rawEvent, long eventSeq, boolean appendResumeRestarted) {
            JsonObject event;
            try {
                event = JsonParser.parseString(rawEvent).getAsJsonObject();
            } catch (Exception e) {
                event = new JsonObject();
                event.addProperty("type", "final");
                event.addProperty("content", rawEvent);
            }
            event.addProperty("eventSeq", eventSeq);
            event.addProperty("requestId", requestId);
            event.addProperty("sessionUpdatedAt", new Date(updatedAt).getTime());
            if (appendResumeRestarted) {
                event.addProperty("resumeRestarted", true);
            }
            return gson.toJson(event);
        }
    }
}
