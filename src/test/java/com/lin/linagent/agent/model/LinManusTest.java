package com.lin.linagent.agent.model;

import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class LinManusTest {

    @Resource
    private  LinManus linManus;


    @Test
    void run(){
        String userPrompt = """
                我的另一半在河南省新乡市红旗区，请帮我找到5公里内适合约会地点，并结合一些网络图片，生成约会计划。
                """;
        String ans = linManus.run(userPrompt);
        System.out.println(ans);
    }

}