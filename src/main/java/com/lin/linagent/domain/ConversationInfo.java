package com.lin.linagent.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/**
 * 会话信息
 */
@Data
@TableName("conversation_info")
public class ConversationInfo {

    /**
     * 主键
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 会话id
     */
    @TableField("conversation_id")
    private String conversationId;

    /**
     * 用户id
     */
    @TableField("user_id")
    private String userId;

    /**
     * 会话标题
     */
    @TableField("title")
    private String title;

    /**
     * 会话模式
     */
    @TableField("mode")
    private String mode;

    /**
     * 会话标签
     */
    @TableField("tag")
    private String tag;

    /**
     * 是否置顶
     */
    @TableField("pinned")
    private Boolean pinned;

    /**
     * 会话摘要
     */
    @TableField("summary")
    private String summary;

    /**
     * 最近消息
     */
    @TableField("last_message")
    private String lastMessage;

    /**
     * 消息数量
     */
    @TableField("message_count")
    private Integer messageCount;

    /**
     * 创建时间
     */
    @TableField("create_time")
    private Date createTime;

    /**
     * 最近消息时间
     */
    @TableField("last_time")
    private Date lastTime;

    /**
     * 更新时间
     */
    @TableField("update_time")
    private Date updateTime;
}
