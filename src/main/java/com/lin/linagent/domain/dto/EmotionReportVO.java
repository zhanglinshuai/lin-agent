package com.lin.linagent.domain.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 情感报告结果
 */
@Data
public class EmotionReportVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 报告标题
     */
    private String title;

    /**
     * 情绪概览
     */
    private String snapshot;

    /**
     * 关注重点
     */
    private List<String> keyPoints;

    /**
     * 建议列表
     */
    private List<String> suggestions;

    /**
     * 下一步行动
     */
    private List<String> actions;

    /**
     * 收尾鼓励
     */
    private String closingMessage;

    /**
     * 生成时间
     */
    private String generatedAt;
}
