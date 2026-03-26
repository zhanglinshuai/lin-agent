package com.lin.linagent.agent.graph.agent;

import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.alibaba.cloud.ai.graph.NodeOutput;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.exception.GraphStateException;
import com.alibaba.cloud.ai.graph.state.StateSnapshot;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import reactor.core.publisher.Flux;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
@Slf4j
@SpringBootTest
class QuestionAnswerAgentTest {


    @Resource
    private QuestionAnswerAgent questionAnswerAgent;

    @Test
    void createQuestionAnswerGraph() throws GraphStateException {
        Map<String,Object> initialState = Map.of(
                "original_input","帮我生成从新乡到南京的旅游规划"
        );
        RunnableConfig config = RunnableConfig.builder()
                .threadId("customer_123")
                .build();
        CompiledGraph graph = questionAnswerAgent.createQuestionAnswerGraph();
        Flux<NodeOutput> stream = graph.stream(initialState,config);
        stream.doOnNext(output->log.info("节点输出{}",output))
                .doOnError(error->log.error("执行错误:{}",error.getMessage()))
                .doOnComplete(()->log.info("流完成"))
                .blockLast();
    }
}