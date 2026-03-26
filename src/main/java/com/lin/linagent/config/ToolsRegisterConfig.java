package com.lin.linagent.config;

import com.lin.linagent.contant.CommonVariables;
import com.lin.linagent.tools.FileOperationTool;
import com.lin.linagent.tools.TerminateTool;
import com.lin.linagent.tools.WebSearchTool;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.StaticToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 工具注册配置类
 */
@Configuration
public class ToolsRegisterConfig {

    @Bean
    public FileOperationTool fileOperationTool() {
        return new FileOperationTool();
    }

    @Bean
    public WebSearchTool webSearchTool(
            @Value("${lin.search.metaso-api-key:}") String metasoApiKey,
            @Value("${lin.search.tavily-api-key:}") String tavilyApiKey,
            @Value("${lin.search.provider-order:tavily,duckduckgo,metaso}") String providerOrder
    ) {
        return new WebSearchTool(
                StringUtils.defaultIfBlank(metasoApiKey, CommonVariables.SEARCH_API_KEY),
                StringUtils.defaultIfBlank(tavilyApiKey, CommonVariables.TAVILY_SEARCH_API_KEY),
                StringUtils.defaultIfBlank(providerOrder, CommonVariables.SEARCH_PROVIDER_ORDER)
        );
    }

    @Bean
    public TerminateTool terminateTool() {
        return new TerminateTool();
    }

    @Bean
    public ToolCallback[] allTools(FileOperationTool fileOperationTool, WebSearchTool webSearchTool, TerminateTool terminateTool){
        return ToolCallbacks.from(
                fileOperationTool,
                webSearchTool,
                terminateTool
        );
    }

    @Bean
    public ToolCallbackProvider toolCallbackProvider(ToolCallback[] allTools) {
        return new StaticToolCallbackProvider(allTools);
    }
}
