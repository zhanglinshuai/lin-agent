package com.lin.linagent.agent.graph.node;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.lin.linagent.agent.graph.model.ToolResult;
import com.lin.linagent.tools.TerminateTool;

import java.util.Map;

/**
 * 任务结束节点
 */
public class TerminateNode implements NodeAction {
    @Override
    public Map<String, Object> apply(OverAllState state) throws Exception {
        TerminateTool terminateTool = new TerminateTool();
        boolean doneTerminate = terminateTool.doTerminate();
        ToolResult toolResult = new ToolResult();
        toolResult.setToolName("terminate");
        toolResult.setToolResult(doneTerminate);
        return Map.of(
                "next_node","null",
                "tool_result",toolResult,
                "TerminateResult",doneTerminate
        );
    }
}
