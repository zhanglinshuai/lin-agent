package com.lin.linagent.agent.model;

import lombok.Data;

/**
 * 智能体执行进度事件
 */
@Data
public class AgentProgressEvent {

    /**
     * 事件类型：thinking、result、final、error
     */
    private String type;

    /**
     * 步骤编号
     */
    private Integer step;

    /**
     * 事件内容
     */
    private String content;
}
