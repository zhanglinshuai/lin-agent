package com.lin.linagent.agent.model;

/**
 * 智能体执行状态枚举
 */
public enum AgentState {

    /**
     * 空闲状态
     */
    IDLE,
    /**
     * 运行中状态
     */
    RUNNING,
    /**
     * 结束状态
     */
    FINISHED,
    /**
     * 错误状态
     */
    ERROR
}
