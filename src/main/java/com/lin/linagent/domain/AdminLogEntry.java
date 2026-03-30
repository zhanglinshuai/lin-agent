package com.lin.linagent.domain;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/**
 * 管理日志条目
 */
@Data
@TableName("admin_log_entry")
public class AdminLogEntry {

    @TableId("id")
    private String id;

    @TableField("level")
    private String level;

    @TableField("category")
    private String category;

    @TableField("summary")
    private String summary;

    @TableField("detail")
    private String detail;

    @TableField("created_at")
    private Date createdAt;

    @TableField("created_at_label")
    private String createdAtLabel;

    @TableField(exist = false)
    private String operatorId;

    @TableField(exist = false)
    private String operatorName;

    @TableField(exist = false)
    private String displayDetail;
}
