package com.lin.linagent.rag;

import jakarta.annotation.Resource;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.BatchingStrategy;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.util.CollectionUtils;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;

/**
 * 自定义向量存储器
 */
@Configuration
public class CustomPgVectorStore {
    @Resource
    private CustomDocumentETL customDocumentETL;

    @Resource
    private VectorStore pgVectorVectorStore;

    @Resource
    private BatchingStrategy customTokenCountBatchingStrategy;

    @Bean
    public VectorStore EmotionVectorStore() {
        DataSource dataSource = new DriverManagerDataSource(
                "jdbc:postgresql://localhost:5432/postgres",
                "postgres", "123456"
        );
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        //加载文档
        List<Document> documents = customDocumentETL.loadMarkDownDocuments();
        String sql = "SELECT count(*) FROM vector_store WHERE content = ?";
        //不加载内容重复的文档
        if(!CollectionUtils.isEmpty(documents)){
            Iterator<Document> iterator = documents.iterator();
            while(iterator.hasNext()){
                Document document = iterator.next();
                String text = document.getText();
                Integer count = jdbcTemplate.queryForObject(sql, Integer.class, text);
                if(count!=null && count>0){
                    iterator.remove();
                }
            }
        }
        if(!CollectionUtils.isEmpty(documents)){
            List<List<Document>> batch = customTokenCountBatchingStrategy.batch(documents);
            for(List<Document> batchDocuments : batch){
                pgVectorVectorStore.add(batchDocuments);
            }
        }
        return pgVectorVectorStore;
    }
}
