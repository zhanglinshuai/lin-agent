package com.lin.linagent.multirecall;

import com.lin.linagent.contant.CommonVariables;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 自定义pgVector召回策略
 */
@Component
public class pgVectorRecallStrategy implements RecallStrategy {

    private final VectorStore pgVectorVectorStore;

    public pgVectorRecallStrategy(VectorStore pgVectorVectorStore) {
        this.pgVectorVectorStore = pgVectorVectorStore;
    }


    @Override
    public String getName() {
        return "VectorReCall";
    }

    @Override
    public CompletableFuture<List<Document>> recallAsync(String query) {
        SearchRequest searchRequest = SearchRequest
                .builder()
                .query(query)
                .topK(CommonVariables.EACH_CHANNEL_SIZE)
                .build();
        return CompletableFuture.supplyAsync(()->{
            List<Document> documents = pgVectorVectorStore.similaritySearch(searchRequest);
            return documents.stream()
                    .map(d->new Document(d.getId(),d.getText(), Map.of(
                            "source","vector",
                            "score",d.getScore()
                    )))
                    .toList();
        });
    }

}
