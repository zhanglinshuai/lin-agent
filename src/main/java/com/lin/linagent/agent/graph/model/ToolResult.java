package com.lin.linagent.agent.graph.model;

import lombok.Data;

@Data
public class ToolResult {
    private String toolName;
    private Object toolResult;

    @Override
    public String toString() {
        return "ToolResult{" +
                "toolName='" + toolName + '\'' +
                ", toolResult='" + toolResult + '\'' +
                '}';
    }
}
