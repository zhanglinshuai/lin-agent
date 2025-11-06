package com.lin.linagent.multirecall;

import co.elastic.clients.elasticsearch.ElasticsearchClient;


import co.elastic.clients.elasticsearch._types.query_dsl.MultiMatchQuery;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import com.lin.linagent.contant.CommonVariables;
import com.lin.linagent.elasticsearch.entity.KnowledgeDoc;
import org.springframework.ai.document.Document;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * 自定义 elasticsearch召回策略
 */
@Component
public class ElasticSearchRecallStrategy implements RecallStrategy {

    private final ElasticsearchClient client;


    public ElasticSearchRecallStrategy(ElasticsearchClient client) {
        this.client = client;
    }


    @Override
    public String getName() {
        return "BM25_ES";
    }

    @Override
    public CompletableFuture<List<Document>> recallAsync(String query) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                SearchResponse<KnowledgeDoc> response = client.search(s -> s
                                .index("knowledge_docs")
                                .size(CommonVariables.EACH_CHANNEL_SIZE)
                                .query(q -> q.multiMatch(MultiMatchQuery.of(mm -> mm
                                        .fields("title", "content")
                                        .query(query)
                                ))),
                        KnowledgeDoc.class);

                return response.hits().hits().stream()
                        .map(this::toDoc)
                        .collect(Collectors.toList());
            } catch (IOException e) {
                e.printStackTrace();
                return List.of();
            }
        });
    }
    private Document toDoc(Hit<KnowledgeDoc> hit) {
        KnowledgeDoc kd = hit.source();
        assert kd != null;
        assert hit.score() != null;
        return new Document(kd.getId(), kd.getContent(), Map.of(
                "source", "es",
                "title", kd.getTitle(),
                "score", hit.score(),
                "type", kd.getEmotion()
        ));
    }


}
