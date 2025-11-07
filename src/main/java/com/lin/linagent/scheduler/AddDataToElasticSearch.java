package com.lin.linagent.scheduler;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import co.elastic.clients.elasticsearch.core.bulk.BulkOperation;
import com.lin.linagent.contant.CommonVariables;
import com.lin.linagent.elasticsearch.entity.KnowledgeDoc;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 定时向ElasticSearch定时添加数据的方法
 */
@Slf4j
@Component
public class AddDataToElasticSearch {
    @Resource
    private final ElasticsearchClient client;

    public AddDataToElasticSearch(ElasticsearchClient client) {
        this.client = client;
    }
    /**
     * 将markdown格式的文件转换为document格式
     * @return
     */
    public List<KnowledgeDoc> loadMarkDownToDocument(){
        List<KnowledgeDoc> knowledgeDocs = new ArrayList<>();
        String path = "src/main/resources/static/document";
        Path folder = Paths.get(path);
        try {
            DirectoryStream<Path> paths = Files.newDirectoryStream(folder,"*.md");
            for(Path markDown : paths){
                Path fileName = markDown.getFileName();
                String emotion = fileName.toString().substring(0, 2);
                List<String> lines = Files.readAllLines(markDown);
                StringBuilder currentContent = new StringBuilder();
                String currentTitle = null;
                for(String line : lines){
                    if(line.trim().startsWith("####")){
                        //说明是四级标题
                        String title = line.trim().substring(4).trim();
                        if(currentTitle != null){
                            knowledgeDocs.add(extracted(currentTitle,currentContent,emotion));
                        }
                        //开启新段落
                        currentTitle = title;
                        currentContent = new StringBuilder();
                    }else {
                        if(currentTitle!=null){
                            currentContent.append(line).append("\n");
                        }
                    }
                }
                //最后一段
                if(currentTitle!=null){
                    knowledgeDocs.add(extracted(currentTitle,currentContent,emotion));
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return knowledgeDocs;
    }

    /**
     * 转换为Knowledge
     * @param currentTitle
     * @param currentContent
     * @return
     */
    private static KnowledgeDoc extracted(String currentTitle, StringBuilder currentContent,String emotion) {
        //保存文档
        KnowledgeDoc knowledgeDoc = new KnowledgeDoc();
        knowledgeDoc.setId(UUID.randomUUID().toString());
        knowledgeDoc.setTitle(currentTitle);
        knowledgeDoc.setContent(currentContent.toString());
        knowledgeDoc.setEmotion(emotion);
        return knowledgeDoc;
    }


    /**
     * 将Knowledge增量导入到Elasticsearch
     */
    @Scheduled(cron = "0 0 23 * * 0")
    public void addDataToElasticSearch() throws IOException {
        List<BulkOperation> operations = new ArrayList<>();
        List<KnowledgeDoc> knowledgeDocs = loadMarkDownToDocument();
        for(KnowledgeDoc doc : knowledgeDocs){
            operations.add(BulkOperation.of(b-> b
                    .update(idx-> idx
                            .index("knowledge_docs")
                            .id(doc.getId())
                            .action(a-> a
                                    .doc(doc)
                                    //不存在就插入
                                    .docAsUpsert(true))
                    )
            ));
            if(operations.size()>= CommonVariables.BITCH_ELASTIC){
                BulkResponse response = client.bulk(BulkRequest.of(b -> b.operations(operations)));
                if(response.errors()){
                    log.error("文档索引失败！");
                    response.items().forEach(item -> {
                        if(item.error()!=null){
                            log.error("文档{} 失败:{}",item.id(),item.error().reason());
                        }
                    });
                }else {
                    log.info("成功同步增量{}条文档",knowledgeDocs.size());
                }
                //清空 bulk请求
                operations.clear();
            }
        }

    }

}
