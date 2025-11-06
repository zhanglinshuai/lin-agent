package com.lin.linagent.multirecall;

import org.springframework.ai.document.Document;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * 召回策略
 */
public interface RecallStrategy {

    String getName();

    CompletableFuture<List<Document>> recallAsync(String query);
}
