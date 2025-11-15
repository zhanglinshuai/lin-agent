package com.lin.linagent.domain;

import com.baomidou.mybatisplus.annotation.IdType;
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
    private String conversation_id;

    /**
     * 消息内容
     */
    private String content;

    /**
     * 消息类型
     */
    private String message_type;

    /**
     * 创建时间
     */
    private Date create_time;

    /**
     * 当前会话的用户id
     */
    private String user_id;
}