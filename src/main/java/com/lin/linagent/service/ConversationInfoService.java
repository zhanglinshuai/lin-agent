package com.lin.linagent.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.lin.linagent.domain.ConversationInfo;
import com.lin.linagent.domain.ChatMemory;
import com.lin.linagent.domain.dto.AdminConversationVO;
import com.lin.linagent.domain.dto.ConversationSummary;

import java.util.List;

/**
 * 会话信息服务
 */
public interface ConversationInfoService extends IService<ConversationInfo> {

    /**
     * 创建会话
     * @param userId 用户id
     * @param conversationId 会话id
     * @param title 标题
     */
    void createConversation(String userId, String conversationId, String title);

    /**
     * 从消息表同步会话信息
     * @param userId 用户id
     * @param conversationId 会话id
     */
    void syncConversation(String userId, String conversationId);

    /**
     * 重命名会话
     * @param userId 用户id
     * @param conversationId 会话id
     * @param title 标题
     */
    void renameConversation(String userId, String conversationId, String title);

    /**
     * 置顶会话
     * @param userId 用户id
     * @param conversationId 会话id
     * @param pinned 是否置顶
     */
    void pinConversation(String userId, String conversationId, boolean pinned);

    /**
     * 删除会话
     * @param userId 用户id
     * @param conversationId 会话id
     */
    void deleteConversation(String userId, String conversationId);

    /**
     * 后台删除会话
     * @param conversationId 会话id
     * @param userId 用户id
     */
    void deleteConversationForAdmin(String conversationId, String userId);

    /**
     * 查询用户会话摘要
     * @param userId 用户id
     * @param mode 模式
     * @param keyword 关键词
     * @return 摘要列表
     */
    List<ConversationSummary> listConversationSummaries(String userId, String mode, String keyword);

    /**
     * 查询后台会话列表
     * @param keyword 关键词
     * @param mode 模式
     * @param pinned 是否置顶
     * @param limit 限制
     * @return 列表
     */
    List<AdminConversationVO> listAdminConversations(String keyword, String mode, Boolean pinned, Integer limit);

    /**
     * 统计会话总数
     * @return 数量
     */
    long countConversationTotal();

    /**
     * 查询用户会话列表的最新消息
     * @param userId 用户id
     * @param mode 模式
     * @return 最新消息列表
     */
    List<ChatMemory> listUserConversationList(String userId, String mode);
}
