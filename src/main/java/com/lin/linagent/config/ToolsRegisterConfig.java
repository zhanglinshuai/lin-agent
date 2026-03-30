package com.lin.linagent.config;

import com.lin.linagent.contant.CommonVariables;
import com.lin.linagent.tools.FileOperationTool;
import com.lin.linagent.tools.TerminateTool;
import com.lin.linagent.tools.WebSearchTool;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.StaticToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

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
            ObjectProvider<ChatModel> dashscopeChatModelProvider,
            @Value("${lin.search.metaso-api-key:}") String metasoApiKey,
            @Value("${lin.search.tavily-api-key:}") String tavilyApiKey,
            @Value("${lin.search.provider-order:tavily,metaso}") String providerOrder,
            @Value("${lin.search.query-rewrite-rounds:2}") int queryRewriteRounds,
            @Value("${lin.search.query-rewrite-candidates-per-round:2}") int queryRewriteCandidatesPerRound
    ) {
        return new WebSearchTool(
                StringUtils.defaultIfBlank(metasoApiKey, CommonVariables.SEARCH_API_KEY),
                StringUtils.defaultIfBlank(tavilyApiKey, CommonVariables.TAVILY_SEARCH_API_KEY),
                StringUtils.defaultIfBlank(providerOrder, CommonVariables.SEARCH_PROVIDER_ORDER),
                queryRewriteRounds,
                queryRewriteCandidatesPerRound,
                dashscopeChatModelProvider::getIfAvailable
        );
    }

    @Bean
    public TerminateTool terminateTool() {
        return new TerminateTool();
    }

    private ToolCallback[] localTools(FileOperationTool fileOperationTool, WebSearchTool webSearchTool, TerminateTool terminateTool){
        return ToolCallbacks.from(
                fileOperationTool,
                webSearchTool,
                terminateTool
        );
    }

    private ToolCallback[] mcpTools(ObjectProvider<SyncMcpToolCallbackProvider> syncMcpToolCallbackProvider) {
        SyncMcpToolCallbackProvider provider = syncMcpToolCallbackProvider.getIfAvailable();
        if (provider == null) {
            return new ToolCallback[0];
        }
        return provider.getToolCallbacks();
    }

    @Bean
    public ToolCallback[] allTools(FileOperationTool fileOperationTool, WebSearchTool webSearchTool, TerminateTool terminateTool, ObjectProvider<SyncMcpToolCallbackProvider> syncMcpToolCallbackProvider){
        List<ToolCallback> callbacks = new ArrayList<>(Arrays.asList(localTools(fileOperationTool, webSearchTool, terminateTool)));
        callbacks.addAll(Arrays.asList(mcpTools(syncMcpToolCallbackProvider)));
        return callbacks.toArray(new ToolCallback[0]);
    }

    @Bean
    public ToolCallbackProvider toolCallbackProvider(@Qualifier("allTools") ToolCallback[] allTools) {
        return new StaticToolCallbackProvider(allTools);
    }
}
