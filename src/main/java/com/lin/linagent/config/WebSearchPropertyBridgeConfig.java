package com.lin.linagent.config;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.PriorityOrdered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySource;
import org.springframework.lang.NonNull;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 将 WebSearch 相关常量桥接为 Spring 配置，避免搜索工具继续兜底到 DuckDuckGo。
 */
@Configuration
public class WebSearchPropertyBridgeConfig implements BeanFactoryPostProcessor, EnvironmentAware, PriorityOrdered {

    private static final String PROPERTY_SOURCE_NAME = "webSearchConstantPropertySource";

    private ConfigurableEnvironment environment;

    @Override
    public void setEnvironment(@NonNull org.springframework.core.env.Environment environment) {
        if (environment instanceof ConfigurableEnvironment configurableEnvironment) {
            this.environment = configurableEnvironment;
        }
    }

    @Override
    public void postProcessBeanFactory(@NonNull ConfigurableListableBeanFactory beanFactory) throws BeansException {
        ConfigurableEnvironment configurableEnvironment = this.environment;
        if (configurableEnvironment == null) {
            return;
        }

        Map<String, Object> properties = new LinkedHashMap<>();

        registerAliases(properties, WebSearchApiConstants.METASO_API_KEY,
                "metasoApiKey",
                "METASO_API_KEY",
                "metaso.api-key",
                "metaso.apiKey",
                "metaso.api_key",
                "lin.search.metaso-api-key",
                "search.metaso.apiKey",
                "search.metaso-api-key");

        registerAliases(properties, WebSearchApiConstants.TAVILY_SEARCH_API_KEY,
                "TAVILY_SEARCH_API_KEY",
                "tavilySearchApiKey",
                "tavily.api-key",
                "tavily.apiKey",
                "tavily.api_key",
                "tavily.search.api-key",
                "tavily.search.apiKey",
                "lin.search.tavily-api-key",
                "search.tavily.apiKey",
                "search.tavily-api-key",
                "spring.ai.tavily.api-key",
                "spring.ai.tavily.search.api-key");

        registerAliases(properties, "false",
                "duckduckgo.enabled",
                "duckDuckGo.enabled",
                "search.duckduckgo.enabled",
                "search.duckDuckGo.enabled",
                "web-search.duckduckgo.enabled",
                "webSearch.duckduckgo.enabled");

        registerAliases(properties, "tavily,metaso",
                "search.web.providers",
                "search.web.provider-order",
                "web-search.providers",
                "web-search.provider-order",
                "webSearch.providers",
                "webSearch.providerOrder");

        if (properties.isEmpty()) {
            return;
        }

        MutablePropertySources propertySources = configurableEnvironment.getPropertySources();
        PropertySource<?> existing = propertySources.get(PROPERTY_SOURCE_NAME);
        if (existing != null) {
            propertySources.remove(PROPERTY_SOURCE_NAME);
        }
        propertySources.addFirst(new MapPropertySource(PROPERTY_SOURCE_NAME, properties));

        properties.forEach((key, value) -> {
            if (System.getProperty(key) == null) {
                System.setProperty(key, String.valueOf(value));
            }
        });
    }

    private void registerAliases(Map<String, Object> target, String value, String... keys) {
        for (String key : keys) {
            target.putIfAbsent(key, value);
        }
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
