package com.lin.linagent.agent.graph.model;

import lombok.Data;

/**
 * 判断是否还需要调用工具
 */
@Data
public class JudgeCallTools {
    private String need_call;
    private String next_node;
    private String reason;

    @Override
    public String toString() {
        return "judgeCallTools{" +
                "needCall=" + need_call +
                ", nextNode='" + next_node + '\'' +
                ", reason='" + reason + '\'' +
                '}';
    }
}
