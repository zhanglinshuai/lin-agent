package com.lin.linagent.domain;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/**
 * 知识库文档元数据
 */
@Data
@TableName("knowledge_document_meta")
public class KnowledgeDocumentMeta {

    /**
     * 文件名
     */
    @TableId("file_name")
    private String fileName;

    /**
     * 标题
     */
    @TableField("title")
    private String title;

    /**
     * 文件大小
     */
    @TableField("file_size")
    private Long fileSize;

    /**
     * 片段数量
     */
    @TableField("section_count")
    private Integer sectionCount;

    /**
     * 文档更新时间
     */
    @TableField("update_time")
    private String updateTime;

    /**
     * 最近同步时间
     */
    @TableField("synced_at")
    private Date syncedAt;
}
