package com.lin.linagent.embedding;

import com.knuddels.jtokkit.api.EncodingType;
import org.springframework.ai.embedding.BatchingStrategy;
import org.springframework.ai.embedding.TokenCountBatchingStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 自定义批处理策略
 */
@Configuration
public class EmbeddingConfig {

    @Bean
    public BatchingStrategy customTokenCountBatchingStrategy() {
        return new TokenCountBatchingStrategy(
                EncodingType.CL100K_BASE,
                2500,
                0.2
        );
    }

}
