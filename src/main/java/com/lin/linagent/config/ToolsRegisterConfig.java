package com.lin.linagent.config;

import com.lin.linagent.contant.CommonVariables;
import com.lin.linagent.tools.FileOperationTool;
import com.lin.linagent.tools.WebSearchTool;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 工具注册配置类
 */
@Configuration
public class ToolsRegisterConfig {
    @Bean
    public ToolCallback[] allTools(){
        return ToolCallbacks.from(
                new FileOperationTool(),
                new WebSearchTool(CommonVariables.SEARCH_API_KEY)
        );
    }
}
