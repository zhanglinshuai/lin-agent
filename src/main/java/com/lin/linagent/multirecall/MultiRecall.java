package com.lin.linagent.multirecall;

import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * 多路召回，合并
 */
@Component
public class MultiRecall {
    private final List<RecallStrategy> strategies;

    public MultiRecall(List<RecallStrategy> strategies) {
        this.strategies = strategies;
    }
    public List<Document> recall(String query){
        List<CompletableFuture<List<Document>>> futures = strategies.stream().map(s -> s.recallAsync(query)).toList();
        return futures.stream()
                .map(CompletableFuture::join)
                .flatMap(List::stream)
                .collect(Collectors.toList());
    }
}
