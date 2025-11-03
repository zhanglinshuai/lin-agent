package com.lin.linagent.rag;

import jakarta.annotation.Resource;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * 自定义文本文档转换器
 */
@Configuration
public class MyMarkDownDocumentWriter {
    @Resource
    private MyMarkDownDocumentReader myMarkDownDocumentReader;
    @Bean
    public VectorStore EmotionVectorStore(EmbeddingModel dashscopeEmbeddingModel) {
        SimpleVectorStore simpleVectorStore = SimpleVectorStore.builder(dashscopeEmbeddingModel)
                .build();
        //加载文档
        List<Document> documents = myMarkDownDocumentReader.loadMarkDownDocuments();
        simpleVectorStore.add(documents);
        return simpleVectorStore;
    }
}
