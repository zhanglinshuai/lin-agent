package com.lin.linagent.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lin.linagent.auth.AuthHelper;
import com.lin.linagent.auth.LoginUserInfo;
import com.lin.linagent.domain.AdminLogEntry;
import com.lin.linagent.exception.BusinessException;
import com.lin.linagent.exception.ErrorCode;
import com.lin.linagent.mapper.AdminLogEntryMapper;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * 管理后台运行日志服务
 */
@Slf4j
@Service
public class AdminLogService {

    private static final int MAX_LOG_SIZE = 300;

    private static final int MAX_LEVEL_LENGTH = 16;

    private static final int MAX_CATEGORY_LENGTH = 64;

    private static final int MAX_SUMMARY_LENGTH = 255;

    private static final int MAX_CREATED_LABEL_LENGTH = 32;

    private static final String SYSTEM_OPERATOR_ID = "system";

    private static final String SYSTEM_OPERATOR_NAME = "系统";

    private static final String ANONYMOUS_OPERATOR_ID = "anonymous";

    private static final String ANONYMOUS_OPERATOR_NAME = "匿名访客";

    private final Deque<AdminLogEntry> entries = new ConcurrentLinkedDeque<>();

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Resource
    private AdminLogEntryMapper adminLogEntryMapper;

    /**
     * 记录信息日志
     * @param category 分类
     * @param summary 摘要
     * @param detail 详情
     */
    public void info(String category, String summary, String detail) {
        append("INFO", category, summary, detail);
    }

    /**
     * 记录警告日志
     * @param category 分类
     * @param summary 摘要
     * @param detail 详情
     */
    public void warn(String category, String summary, String detail) {
        append("WARN", category, summary, detail);
    }

    /**
     * 记录错误日志
     * @param category 分类
     * @param summary 摘要
     * @param detail 详情
     */
    public void error(String category, String summary, String detail) {
        append("ERROR", category, summary, detail);
    }

    /**
     * 查询最近日志
     * @param limit 返回条数
     * @return 日志列表
     */
    public List<AdminLogEntry> listRecent(int limit) {
        return listRecent(limit, null, null, null);
    }

    /**
     * 查询最近日志（支持筛选）
     * @param limit 返回条数
     * @param level 日志级别
     * @param category 日志分类
     * @param keyword 关键词
     * @return 日志列表
     */
    public List<AdminLogEntry> listRecent(int limit, String level, String category, String keyword) {
        int safeLimit = normalizeLimit(limit);
        String normalizedLevel = StringUtils.trimToEmpty(level).toUpperCase();
        String normalizedCategory = StringUtils.trimToEmpty(category);
        String normalizedKeyword = StringUtils.trimToEmpty(keyword);
        try {
            QueryWrapper<AdminLogEntry> queryWrapper = new QueryWrapper<>();
            if (StringUtils.isNotBlank(normalizedLevel)) {
                queryWrapper.eq("level", normalizedLevel);
            }
            if (StringUtils.isNotBlank(normalizedCategory)) {
                queryWrapper.like("category", normalizedCategory);
            }
            if (StringUtils.isNotBlank(normalizedKeyword)) {
                queryWrapper.and(wrapper -> wrapper.like("summary", normalizedKeyword)
                        .or()
                        .like("detail", normalizedKeyword));
            }
            queryWrapper.orderByDesc("created_at")
                    .orderByDesc("id")
                    .last("LIMIT " + safeLimit);
            List<AdminLogEntry> dbEntries = adminLogEntryMapper.selectList(queryWrapper);
            populateDisplayFields(dbEntries);
            refreshMemoryCache(dbEntries);
            return dbEntries;
        } catch (Exception e) {
            log.error("查询管理日志失败，改用内存缓存返回", e);
            return listRecentFromMemory(safeLimit, normalizedLevel, normalizedCategory, normalizedKeyword);
        }
    }

    /**
     * 获取日志总数
     * @return 总数
     */
    public int getLogCount() {
        try {
            Long count = adminLogEntryMapper.selectCount(new QueryWrapper<>());
            return count == null ? 0 : count.intValue();
        } catch (Exception e) {
            log.error("统计管理日志总数失败，改用内存缓存统计", e);
            return entries.size();
        }
    }

    /**
     * 获取错误日志数
     * @return 错误数
     */
    public long getErrorCount() {
        try {
            QueryWrapper<AdminLogEntry> queryWrapper = new QueryWrapper<>();
            queryWrapper.eq("level", "ERROR");
            Long count = adminLogEntryMapper.selectCount(queryWrapper);
            return count == null ? 0L : count;
        } catch (Exception e) {
            log.error("统计错误日志数失败，改用内存缓存统计", e);
            return entries.stream().filter(entry -> "ERROR".equalsIgnoreCase(entry.getLevel())).count();
        }
    }

    /**
     * 清空日志
     */
    public void clear() {
        try {
            adminLogEntryMapper.delete(new QueryWrapper<>());
            entries.clear();
        } catch (Exception e) {
            log.error("清空管理日志失败", e);
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "清空运行日志失败");
        }
    }

    /**
     * 追加日志
     * @param level 级别
     * @param category 分类
     * @param summary 摘要
     * @param detail 详情
     */
    private void append(String level, String category, String summary, String detail) {
        if (shouldSkipCurrentRequestLog()) {
            return;
        }
        AdminLogEntry entry = buildEntry(level, category, summary, detail);
        appendToMemory(entry);
        try {
            adminLogEntryMapper.insert(entry);
        } catch (Exception e) {
            log.error("写入管理日志表失败，category={}, summary={}", entry.getCategory(), entry.getSummary(), e);
        }
    }

    /**
     * 构建日志实体
     * @param level 级别
     * @param category 分类
     * @param summary 摘要
     * @param detail 详情
     * @return 日志实体
     */
    private AdminLogEntry buildEntry(String level, String category, String summary, String detail) {
        OperatorSnapshot operatorSnapshot = resolveOperatorSnapshot();
        AdminLogEntry entry = new AdminLogEntry();
        entry.setId(UUID.randomUUID().toString());
        entry.setLevel(limitLength(StringUtils.defaultIfBlank(level, "INFO").toUpperCase(), MAX_LEVEL_LENGTH));
        entry.setCategory(limitLength(StringUtils.defaultIfBlank(category, "system"), MAX_CATEGORY_LENGTH));
        entry.setSummary(limitLength(StringUtils.defaultIfBlank(summary, "未命名日志"), MAX_SUMMARY_LENGTH));
        entry.setDetail(mergeDetail(StringUtils.defaultString(detail), operatorSnapshot));
        entry.setCreatedAt(new Date());
        entry.setCreatedAtLabel(limitLength(formatDate(entry.getCreatedAt()), MAX_CREATED_LABEL_LENGTH));
        entry.setOperatorId(operatorSnapshot.operatorId());
        entry.setOperatorName(operatorSnapshot.operatorName());
        entry.setDisplayDetail(resolveDisplayDetail(entry.getDetail()));
        return entry;
    }

    /**
     * 使用内存缓存兜底查询
     * @param limit 条数
     * @param level 级别
     * @param category 分类
     * @param keyword 关键词
     * @return 日志列表
     */
    private List<AdminLogEntry> listRecentFromMemory(int limit, String level, String category, String keyword) {
        List<AdminLogEntry> result = new ArrayList<>();
        int index = 0;
        for (AdminLogEntry entry : entries) {
            if (!matchFilter(entry, level, category, keyword)) {
                continue;
            }
            if (index >= limit) {
                break;
            }
            result.add(entry);
            index++;
        }
        return result;
    }

    /**
     * 刷新内存缓存
     * @param latestEntries 最新日志
     */
    private void refreshMemoryCache(List<AdminLogEntry> latestEntries) {
        entries.clear();
        if (latestEntries == null || latestEntries.isEmpty()) {
            return;
        }
        for (AdminLogEntry entry : latestEntries) {
            if (entry != null) {
                entries.addLast(entry);
            }
        }
    }

    /**
     * 补充展示字段
     * @param latestEntries 日志列表
     */
    private void populateDisplayFields(List<AdminLogEntry> latestEntries) {
        if (latestEntries == null || latestEntries.isEmpty()) {
            return;
        }
        for (AdminLogEntry entry : latestEntries) {
            if (entry == null) {
                continue;
            }
            if (StringUtils.isBlank(entry.getCreatedAtLabel()) && entry.getCreatedAt() != null) {
                entry.setCreatedAtLabel(limitLength(formatDate(entry.getCreatedAt()), MAX_CREATED_LABEL_LENGTH));
            }
            entry.setOperatorId(resolveOperatorId(entry.getDetail()));
            entry.setOperatorName(resolveOperatorName(entry.getDetail(), entry.getOperatorId()));
            entry.setDisplayDetail(resolveDisplayDetail(entry.getDetail()));
        }
    }

    /**
     * 追加到内存缓存
     * @param entry 日志
     */
    private void appendToMemory(AdminLogEntry entry) {
        if (entry == null) {
            return;
        }
        entries.addFirst(entry);
        while (entries.size() > MAX_LOG_SIZE) {
            entries.pollLast();
        }
    }

    /**
     * 判断日志是否命中筛选条件
     * @param entry 日志
     * @param level 级别
     * @param category 分类
     * @param keyword 关键词
     * @return 是否命中
     */
    private boolean matchFilter(AdminLogEntry entry, String level, String category, String keyword) {
        if (entry == null) {
            return false;
        }
        String normalizedCategory = StringUtils.trimToEmpty(category).toLowerCase();
        String normalizedKeyword = StringUtils.trimToEmpty(keyword).toLowerCase();
        if (StringUtils.isNotBlank(level) && !level.equalsIgnoreCase(StringUtils.defaultString(entry.getLevel()))) {
            return false;
        }
        if (StringUtils.isNotBlank(normalizedCategory) && !StringUtils.defaultString(entry.getCategory()).toLowerCase().contains(normalizedCategory)) {
            return false;
        }
        if (StringUtils.isBlank(normalizedKeyword)) {
            return true;
        }
        String text = (StringUtils.defaultString(entry.getSummary()) + " " + StringUtils.defaultString(entry.getDetail())).toLowerCase();
        return text.contains(normalizedKeyword);
    }

    /**
     * 规整条数限制
     * @param limit 原始条数
     * @return 安全条数
     */
    private int normalizeLimit(int limit) {
        return Math.max(1, Math.min(limit, MAX_LOG_SIZE));
    }

    /**
     * 限制文本长度
     * @param text 文本
     * @param maxLength 最大长度
     * @return 结果
     */
    private String limitLength(String text, int maxLength) {
        String normalized = StringUtils.defaultString(text);
        if (normalized.length() <= maxLength) {
            return normalized;
        }
        return normalized.substring(0, maxLength);
    }

    /**
     * 格式化时间
     * @param date 时间
     * @return 文本
     */
    private String formatDate(Date date) {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(date);
    }

    /**
     * 当前请求是否应跳过日志
     * @return 是否跳过
     */
    private boolean shouldSkipCurrentRequestLog() {
        HttpServletRequest request = resolveCurrentRequest();
        return request != null && "GET".equalsIgnoreCase(request.getMethod());
    }

    /**
     * 合并日志详情和操作人
     * @param detail 原始详情
     * @param operatorSnapshot 操作人
     * @return 合并后的详情
     */
    private String mergeDetail(String detail, OperatorSnapshot operatorSnapshot) {
        Map<String, Object> detailMap = parseDetailMap(detail);
        if (detailMap == null) {
            detailMap = new LinkedHashMap<>();
            if (StringUtils.isNotBlank(detail)) {
                detailMap.put("detailText", detail);
            }
        }
        if (StringUtils.isNotBlank(operatorSnapshot.operatorId())) {
            detailMap.putIfAbsent("operatorId", operatorSnapshot.operatorId());
        }
        if (StringUtils.isNotBlank(operatorSnapshot.operatorName())) {
            detailMap.putIfAbsent("operatorName", operatorSnapshot.operatorName());
        }
        if (StringUtils.isNotBlank(operatorSnapshot.operatorRole())) {
            detailMap.putIfAbsent("operatorRole", operatorSnapshot.operatorRole());
        }
        return toJson(detailMap);
    }

    /**
     * 解析详情中的操作人id
     * @param detail 详情
     * @return 操作人id
     */
    private String resolveOperatorId(String detail) {
        Map<String, Object> detailMap = parseDetailMap(detail);
        if (detailMap != null) {
            String operatorId = StringUtils.trimToEmpty(String.valueOf(detailMap.getOrDefault("operatorId", "")));
            if (StringUtils.isNotBlank(operatorId)) {
                return operatorId;
            }
        }
        return extractLegacyOperator(detail);
    }

    /**
     * 解析详情中的操作人名称
     * @param detail 详情
     * @param operatorId 操作人id
     * @return 操作人名称
     */
    private String resolveOperatorName(String detail, String operatorId) {
        Map<String, Object> detailMap = parseDetailMap(detail);
        if (detailMap != null) {
            String operatorName = StringUtils.trimToEmpty(String.valueOf(detailMap.getOrDefault("operatorName", "")));
            if (StringUtils.isNotBlank(operatorName)) {
                return operatorName;
            }
        }
        String legacyOperator = extractLegacyOperator(detail);
        if (StringUtils.isNotBlank(legacyOperator)) {
            return legacyOperator;
        }
        if (StringUtils.equalsIgnoreCase(operatorId, SYSTEM_OPERATOR_ID)) {
            return SYSTEM_OPERATOR_NAME;
        }
        if (StringUtils.equalsIgnoreCase(operatorId, ANONYMOUS_OPERATOR_ID)) {
            return ANONYMOUS_OPERATOR_NAME;
        }
        return "";
    }

    /**
     * 解析页面展示详情
     * @param detail 原始详情
     * @return 展示详情
     */
    private String resolveDisplayDetail(String detail) {
        Map<String, Object> detailMap = parseDetailMap(detail);
        if (detailMap == null) {
            return StringUtils.defaultString(detail);
        }
        String detailText = StringUtils.trimToEmpty(String.valueOf(detailMap.getOrDefault("detailText", "")));
        if (StringUtils.isNotBlank(detailText)) {
            return detailText;
        }
        return StringUtils.defaultString(detail);
    }

    /**
     * 解析详情为 map
     * @param detail 详情
     * @return map
     */
    private Map<String, Object> parseDetailMap(String detail) {
        if (StringUtils.isBlank(detail)) {
            return null;
        }
        String trimmedDetail = detail.trim();
        if (!trimmedDetail.startsWith("{") || !trimmedDetail.endsWith("}")) {
            return null;
        }
        try {
            return objectMapper.readValue(trimmedDetail, new TypeReference<Map<String, Object>>() {
            });
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 兼容旧格式 operator=xxx 文本
     * @param detail 详情
     * @return 操作人
     */
    private String extractLegacyOperator(String detail) {
        String text = StringUtils.defaultString(detail);
        int start = text.indexOf("operator=");
        if (start < 0) {
            return "";
        }
        String operator = text.substring(start + "operator=".length());
        int commaIndex = operator.indexOf(",");
        if (commaIndex >= 0) {
            operator = operator.substring(0, commaIndex);
        }
        return StringUtils.trimToEmpty(operator);
    }

    /**
     * 转为 json
     * @param detailMap map
     * @return json
     */
    private String toJson(Map<String, Object> detailMap) {
        try {
            return objectMapper.writeValueAsString(detailMap);
        } catch (Exception e) {
            log.warn("管理日志详情序列化失败", e);
            return String.valueOf(detailMap);
        }
    }

    /**
     * 解析操作人
     * @return 操作人快照
     */
    private OperatorSnapshot resolveOperatorSnapshot() {
        HttpServletRequest request = resolveCurrentRequest();
        if (request == null) {
            return new OperatorSnapshot(SYSTEM_OPERATOR_ID, SYSTEM_OPERATOR_NAME, "");
        }
        LoginUserInfo loginUserInfo = AuthHelper.getNullableLoginUser(request);
        if (loginUserInfo == null) {
            return new OperatorSnapshot(ANONYMOUS_OPERATOR_ID, ANONYMOUS_OPERATOR_NAME, "");
        }
        String operatorId = StringUtils.defaultIfBlank(loginUserInfo.getId(), ANONYMOUS_OPERATOR_ID);
        String operatorName = StringUtils.defaultIfBlank(loginUserInfo.getUserName(), operatorId);
        String operatorRole = loginUserInfo.getUserRole() == null ? "" : String.valueOf(loginUserInfo.getUserRole());
        return new OperatorSnapshot(operatorId, operatorName, operatorRole);
    }

    /**
     * 获取当前请求
     * @return 请求
     */
    private HttpServletRequest resolveCurrentRequest() {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes)) {
            return null;
        }
        return attributes.getRequest();
    }

    /**
     * 操作人快照
     * @param operatorId 操作人id
     * @param operatorName 操作人名称
     * @param operatorRole 操作人角色
     */
    private record OperatorSnapshot(String operatorId, String operatorName, String operatorRole) {
    }
}
