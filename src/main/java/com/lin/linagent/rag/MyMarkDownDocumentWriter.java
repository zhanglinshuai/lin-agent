package com.lin.linagent.rag;

import jakarta.annotation.Resource;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * 自定义文本文档加载器
 */
@Configuration
public class MyMarkDownDocumentWriter {
    @Resource
    private MyMarkDownDocumentReader myMarkDownDocumentReader;

    @Resource
    private VectorStore pgVectorVectorStore;

    @Bean
    public VectorStore EmotionVectorStore(EmbeddingModel dashscopeEmbeddingModel) {
        //加载文档
        List<Document> documents = myMarkDownDocumentReader.loadMarkDownDocuments();
        pgVectorVectorStore.add(documents);
        return pgVectorVectorStore;
    }
}
