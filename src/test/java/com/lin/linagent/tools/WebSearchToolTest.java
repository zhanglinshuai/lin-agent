package com.lin.linagent.tools;

import com.lin.linagent.contant.CommonVariables;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
@SpringBootTest
class WebSearchToolTest {

    @Test
    void searchWeb() {
        WebSearchTool webSearchTool = new WebSearchTool(CommonVariables.SEARCH_API_KEY);
        String query = "周末我想带女朋友去新乡约会，推荐几个适合情侣打卡的小中打卡地？";
        String result = webSearchTool.searchWeb(query);
        System.out.println(result);
    }
}