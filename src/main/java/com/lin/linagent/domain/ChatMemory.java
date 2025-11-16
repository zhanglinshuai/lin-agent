package com.lin.linagent.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.util.Date;

import lombok.Data;

/**
 * 存储消息
 * @TableName chat_memory
 */
@TableName(value ="chat_memory")
@Data
public class ChatMemory {
    /**
     * 
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 会话id
     */
    @TableField("conversation_id")
    private String conversationId;

    /**
     * 消息内容
     */
    @TableField("content")
    private String content;

    /**
     * 消息类型
     */
    @TableField("message_type")
    private String messageType;

    /**
     * 创建时间
     */
    @TableField("create_time")
    private Date createTime;

    /**
     * 当前会话的用户id
     */
    @TableField("user_id")
    private String userId;

    /**
     * 元数据
     */
    @TableField("metadata")
    private String metadata;
}