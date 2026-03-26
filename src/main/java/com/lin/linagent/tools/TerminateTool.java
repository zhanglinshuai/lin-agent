package com.lin.linagent.tools;

import org.springframework.ai.tool.annotation.Tool;

public class TerminateTool {

    @Tool(description = """
            结束当前任务。只有在你已经给出最终答复、并且确认后续不再需要任何工具或额外步骤时才调用。
            """)
    public boolean doTerminate() {
        return true;
    }
}
