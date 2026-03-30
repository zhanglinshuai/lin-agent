package com.lin.linagent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lin.linagent.domain.ConversationInfo;
import com.lin.linagent.domain.dto.AdminConversationVO;
import com.lin.linagent.domain.dto.ConversationSummary;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 会话信息 Mapper
 */
@Mapper
public interface ConversationInfoMapper extends BaseMapper<ConversationInfo> {

    /**
     * 统计会话总数
     * @return 数量
     */
    Long countConversationTotal();

    /**
     * 查询用户会话摘要
     * @param userId 用户id
     * @param mode 模式
     * @param keyword 关键词
     * @return 摘要列表
     */
    List<ConversationSummary> selectUserConversationSummaries(@Param("userId") String userId,
                                                              @Param("mode") String mode,
                                                              @Param("keyword") String keyword);

    /**
     * 查询后台会话列表
     * @param keyword 关键词
     * @param mode 模式
     * @param pinned 是否置顶
     * @param limit 限制
     * @return 列表
     */
    List<AdminConversationVO> selectAdminConversationList(@Param("keyword") String keyword,
                                                          @Param("mode") String mode,
                                                          @Param("pinned") Boolean pinned,
                                                          @Param("limit") Integer limit);
}
