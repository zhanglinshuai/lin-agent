package com.lin.linagent.rag.etl.transformer;

import com.lin.linagent.contant.CommonVariables;
import jakarta.annotation.Resource;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.document.Document;
import org.springframework.ai.model.transformer.KeywordMetadataEnricher;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 自定义提取关键词器（中文）
 */
@Component
public class ChineseEnricherKeyWord extends KeywordMetadataEnricher {

    private final ChatModel dashscopechatModel;

    private final int keywordCount = CommonVariables.ENRICHER_KEY_WORD_COUNT;

    public ChineseEnricherKeyWord(ChatModel dashscopechatModel) {
        super(dashscopechatModel,CommonVariables.ENRICHER_KEY_WORD_COUNT);
        this.dashscopechatModel = dashscopechatModel;
        Assert.notNull(dashscopechatModel, "ChatModel must not be null");
    }
    @Override
    public List<Document> apply(List<Document> documents) {
        for(Document document : documents) {
            PromptTemplate template = new PromptTemplate(String.format("请用中文从{content_str}中提取 %s 个最重要的关键词，只返回中文关键词，用逗号分隔",CommonVariables.ENRICHER_KEY_WORD_COUNT));
            Prompt prompt = template.create(Map.of("content_str", document.getText()));
            String keywords = dashscopechatModel.call(prompt).getResult().getOutput().getText();
            document.getMetadata().putAll(Map.of("excerpt_keywords", keywords));
        }

        return documents;
    }


}
