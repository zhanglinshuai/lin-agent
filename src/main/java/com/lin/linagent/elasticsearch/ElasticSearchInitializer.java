package com.lin.linagent.elasticsearch;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.indices.CreateIndexRequest;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * 初始化Elasticsearch
 */
@Component
public class ElasticSearchInitializer {

    private final ElasticsearchClient elasticsearchClient;

    private volatile boolean knowledgeDocIndexReady = false;

    public ElasticSearchInitializer(ElasticsearchClient elasticsearchClient) {
        this.elasticsearchClient = elasticsearchClient;
    }

    @PostConstruct
    public void init() throws IOException {
        ensureKnowledgeDocIndex();
    }

    /**
     * 确保知识库索引存在
     * @throws IOException 异常
     */
    public void ensureKnowledgeDocIndex() throws IOException {
        if (knowledgeDocIndexReady) {
            return;
        }
        if (!elasticsearchClient.indices().exists(e -> e.index("knowledge_docs")).value()) {
            elasticsearchClient.indices().create(CreateIndexRequest.of(c -> c
                    .index("knowledge_docs")
                    .mappings(m -> m
                            .properties("type",p->p.text(t->t))
                            .properties("title", p -> p.text(t -> t.analyzer("ik_max_word")))
                            .properties("content", p -> p.text(t -> t.analyzer("ik_max_word")))
                    )
            ));
        }
        knowledgeDocIndexReady = true;
    }
}
