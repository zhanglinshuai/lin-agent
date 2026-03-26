package com.lin.linagent.agent.graph.model;

import lombok.Data;

/**
 * 问题意图分类结构
 */
@Data
public class QuestionClassification {
    private String input;
    private String intent;
    private float confidence;
    private String description;

    @Override
    public String toString() {
        return "QuestionClassification{" +
                "input='" + input + '\'' +
                ", intent='" + intent + '\'' +
                ", confidence=" + confidence +
                ", description='" + description + '\'' +
                '}';
    }
}
