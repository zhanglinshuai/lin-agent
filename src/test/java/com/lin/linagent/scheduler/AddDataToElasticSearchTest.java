package com.lin.linagent.scheduler;

import com.lin.linagent.elasticsearch.entity.KnowledgeDoc;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
@SpringBootTest
class AddDataToElasticSearchTest {

    @Resource
    private AddDataToElasticSearch addDataToElasticSearch;


    @Test
    void loadMarkDownToDocument() {
        try {
            addDataToElasticSearch.addDataToElasticSearch();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void addDataToElasticSearch() {
    }
}