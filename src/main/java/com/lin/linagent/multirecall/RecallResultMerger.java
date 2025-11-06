package com.lin.linagent.multirecall;

import com.lin.linagent.contant.CommonVariables;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 *  得到结果后对结果进行合并并且重排序
 */
@Component
public class RecallResultMerger {
    public List<Document> mergeAndRank(List<Document> documents){
        Map<String, Document> unique = documents.stream()
                //document的id为key，document为value，如果冲突保留第一个
                .collect(Collectors.toMap(Document::getId, document -> document, (a, b) -> a));
        return unique.values().stream()
                .sorted(Comparator.comparingDouble(this::score).reversed())
                .limit(CommonVariables.RECALL_MERGED_SIZE)
                .collect(Collectors.toList());
    }

    private double score(Document document){
        return ((Number)document.getMetadata().getOrDefault("score",0.0)).doubleValue();
    }
}
