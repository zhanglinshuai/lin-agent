package com.lin.linagent.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lin.linagent.domain.User;
import com.lin.linagent.domain.dto.AdminConversationVO;
import com.lin.linagent.domain.dto.AdminDashboardVO;
import com.lin.linagent.domain.dto.AdminUserVO;
import com.lin.linagent.mapper.ChatMemoryMapper;
import com.lin.linagent.mapper.UserMapper;
import com.lin.linagent.service.ConversationInfoService;
import com.lin.linagent.exception.BusinessException;
import com.lin.linagent.exception.ErrorCode;
import jakarta.annotation.Resource;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * 管理后台服务
 */
@Service
public class AdminConsoleService {

    @Resource
    @Qualifier("mysqlJdbcTemplate")
    private JdbcTemplate mysqlJdbcTemplate;

    @Resource
    private UserMapper userMapper;

    @Resource
    private ChatMemoryMapper chatMemoryMapper;

    @Resource
    private ConversationInfoService conversationInfoService;

    @Resource
    private KnowledgeBaseAdminService knowledgeBaseAdminService;

    @Resource
    private AdminLogService adminLogService;

    @Resource
    private AssistantStreamSessionService assistantStreamSessionService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 构建管理后台概览
     * @return 概览
     */
    public AdminDashboardVO buildDashboard() {
        AdminDashboardVO vo = new AdminDashboardVO();
        vo.setUserCount(safeLong(userMapper.countActiveUsers()));
        vo.setConversationCount(conversationInfoService.countConversationTotal());
        vo.setMessageCount(safeLong(chatMemoryMapper.countMessageTotal()));
        vo.setKnowledgeFileCount(knowledgeBaseAdminService.getKnowledgeFileCount());
        vo.setKnowledgeSectionCount(knowledgeBaseAdminService.getKnowledgeSectionCount());
        vo.setVectorRowCount(knowledgeBaseAdminService.getVectorRowCount());
        vo.setElasticDocCount(knowledgeBaseAdminService.getElasticDocCount());
        vo.setActiveStreamCount(assistantStreamSessionService.getActiveSessionCount());
        vo.setLogCount(adminLogService.getLogCount());
        vo.setErrorLogCount(adminLogService.getErrorCount());
        vo.setLastRebuildTime(knowledgeBaseAdminService.getLastRebuildTime());
        return vo;
    }

    /**
     * 安全读取 long 值
     * @param value 原值
     * @return 结果
     */
    private long safeLong(Long value) {
        return value == null ? 0L : value;
    }

    /**
     * 查询 long 值
     * @param sql SQL
     * @return 结果
     */
    private long queryLong(String sql) {
        Long value = mysqlJdbcTemplate.queryForObject(sql, Long.class);
        return value == null ? 0L : value;
    }

    /**
     * 查询用户列表
     * @param keyword 关键词
     * @param userRole 角色筛选
     * @param includeDeleted 是否包含已删除
     * @param limit 返回条数
     * @return 用户列表
     */
    public List<AdminUserVO> listUsers(String keyword, Integer userRole, Boolean includeDeleted, Boolean deleted, Integer limit) {
        conversationInfoService.countConversationTotal();
        int safeLimit = Math.max(1, Math.min(limit == null ? 80 : limit, 300));
        String normalizedKeyword = StringUtils.trimToEmpty(keyword);
        StringBuilder sql = new StringBuilder("""
                SELECT u.id,
                       u.userName,
                       u.userPhone,
                       u.userRole,
                       u.isDelete,
                       u.createTime,
                       u.updateTime,
                       (SELECT COUNT(*) FROM conversation_info ci WHERE ci.user_id = u.id) AS conversationCount,
                       (SELECT COUNT(*) FROM chat_memory c2 WHERE c2.user_id = u.id) AS messageCount
                FROM user u
                WHERE 1 = 1
                """);
        List<Object> params = new ArrayList<>();
        if (deleted != null) {
            sql.append(" AND u.isDelete = ?");
            params.add(Boolean.TRUE.equals(deleted) ? 1 : 0);
        } else if (!Boolean.TRUE.equals(includeDeleted)) {
            sql.append(" AND u.isDelete = 0");
        }
        if (StringUtils.isNotBlank(normalizedKeyword)) {
            sql.append(" AND (u.userName LIKE ? OR u.userPhone LIKE ?)");
            String like = "%" + normalizedKeyword + "%";
            params.add(like);
            params.add(like);
        }
        if (userRole != null && (userRole.equals(User.USER_ROLE_USER) || userRole.equals(User.USER_ROLE_ADMIN))) {
            sql.append(" AND u.userRole = ?");
            params.add(userRole);
        }
        sql.append(" ORDER BY u.updateTime DESC, u.createTime DESC LIMIT ?");
        params.add(safeLimit);
        return mysqlJdbcTemplate.query(sql.toString(), params.toArray(), (rs, rowNum) -> {
            AdminUserVO vo = new AdminUserVO();
            vo.setId(rs.getString("id"));
            vo.setUserName(rs.getString("userName"));
            vo.setUserPhone(rs.getString("userPhone"));
            vo.setUserRole(rs.getInt("userRole"));
            vo.setDeleted(rs.getInt("isDelete") == 1);
            vo.setCreateTime(toDate(rs.getTimestamp("createTime")));
            vo.setUpdateTime(toDate(rs.getTimestamp("updateTime")));
            vo.setConversationCount(rs.getLong("conversationCount"));
            vo.setMessageCount(rs.getLong("messageCount"));
            return vo;
        });
    }

    /**
     * 更新用户角色
     * @param userId 用户id
     * @param userRole 目标角色
     * @return 是否成功
     */
    public boolean updateUserRole(String userId, Integer userRole) {
        String normalizedUserId = StringUtils.trimToEmpty(userId);
        if (StringUtils.isBlank(normalizedUserId)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户id不能为空");
        }
        if (userRole == null || (!userRole.equals(User.USER_ROLE_USER) && !userRole.equals(User.USER_ROLE_ADMIN))) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户角色不合法");
        }
        int updated = mysqlJdbcTemplate.update("UPDATE user SET userRole = ?, updateTime = NOW() WHERE id = ?", userRole, normalizedUserId);
        if (updated <= 0) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "用户不存在");
        }
        adminLogService.info("admin-user", "更新用户角色", "userId=" + normalizedUserId + ", userRole=" + userRole);
        return true;
    }

    /**
     * 更新用户删除状态
     * @param operatorUserId 操作人id
     * @param targetUserId 目标用户id
     * @param deleted 是否删除
     * @return 是否成功
     */
    public boolean updateUserDeleteState(String operatorUserId, String targetUserId, boolean deleted) {
        String normalizedTargetUserId = StringUtils.trimToEmpty(targetUserId);
        if (StringUtils.isBlank(normalizedTargetUserId)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户id不能为空");
        }
        if (deleted && StringUtils.equals(StringUtils.trimToEmpty(operatorUserId), normalizedTargetUserId)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "不能删除当前登录管理员");
        }
        int deleteValue = deleted ? 1 : 0;
        int updated = mysqlJdbcTemplate.update("UPDATE user SET isDelete = ?, updateTime = NOW() WHERE id = ?", deleteValue, normalizedTargetUserId);
        if (updated <= 0) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "用户不存在");
        }
        if (deleted) {
            adminLogService.warn("admin-user", "禁用用户", "userId=" + normalizedTargetUserId);
        } else {
            adminLogService.info("admin-user", "恢复用户", "userId=" + normalizedTargetUserId);
        }
        return true;
    }

    /**
     * 查询会话列表
     * @param keyword 关键词
     * @param limit 返回条数
     * @return 会话列表
     */
    public List<AdminConversationVO> listConversations(String keyword, String mode, Boolean pinned, Integer limit) {
        return conversationInfoService.listAdminConversations(keyword, mode, pinned, limit);
    }

    /**
     * 删除会话
     * @param conversationId 会话id
     * @param userId 用户id（可选）
     * @return 是否成功
     */
    public boolean deleteConversation(String conversationId, String userId) {
        String normalizedConversationId = StringUtils.trimToEmpty(conversationId);
        if (StringUtils.isBlank(normalizedConversationId)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "会话id不能为空");
        }
        String normalizedUserId = StringUtils.trimToEmpty(userId);
        int deleted;
        if (StringUtils.isBlank(normalizedUserId)) {
            deleted = mysqlJdbcTemplate.update("DELETE FROM chat_memory WHERE conversation_id = ?", normalizedConversationId);
        } else {
            deleted = mysqlJdbcTemplate.update("DELETE FROM chat_memory WHERE conversation_id = ? AND user_id = ?", normalizedConversationId, normalizedUserId);
        }
        if (deleted <= 0) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "会话不存在");
        }
        conversationInfoService.deleteConversationForAdmin(normalizedConversationId, normalizedUserId);
        adminLogService.warn("admin-conversation", "管理员删除会话", "conversationId=" + normalizedConversationId + ", userId=" + normalizedUserId);
        return true;
    }

    /**
     * 日期转换
     * @param timestamp 时间戳
     * @return 日期
     */
    private Date toDate(Timestamp timestamp) {
        return timestamp == null ? null : new Date(timestamp.getTime());
    }

    /**
     * 安全截断文本
     * @param text 文本
     * @param maxLen 最大长度
     * @return 截断文本
     */
    private String safeText(String text, int maxLen) {
        String normalized = StringUtils.trimToEmpty(text).replaceAll("\\s+", " ");
        if (normalized.length() <= maxLen) {
            return normalized;
        }
        return normalized.substring(0, maxLen) + "...";
    }
}
