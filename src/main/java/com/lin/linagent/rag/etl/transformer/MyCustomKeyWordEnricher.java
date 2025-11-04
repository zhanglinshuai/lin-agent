package com.lin.linagent.rag.etl.transformer;

import jakarta.annotation.Resource;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.document.Document;
import org.springframework.ai.model.transformer.KeywordMetadataEnricher;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 自定义关键词丰富器
 * 自动解析关键词并添加到metadata当中
 */
@Component
public class MyCustomKeyWordEnricher {
    @Resource
    private ChatModel dashscopeChatModel;

    @Bean
    public List<Document> enrichKeyWordToDocument(List<Document> documents){
        ChineseEnricherKeyWord chineseEnricherKeyWord = new ChineseEnricherKeyWord(this.dashscopeChatModel);
        return chineseEnricherKeyWord.apply(documents);
    }
}
