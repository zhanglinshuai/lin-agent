package com.lin.linagent.advisor;

import cn.hutool.core.io.resource.ClassPathResource;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 自定义违禁词Advisor
 * @author zhanglinshuai
 */
public class MyBannerWordAdvisor implements CallAdvisor, StreamAdvisor {

    private static final List<String> DEFAULT_BANNER_WORD_LIST = List.of(
            "static/banner_words/COVID-19词库.txt",
            "static/banner_words/GFW补充词库.txt",
            "static/banner_words/其他词库.txt",
            "static/banner_words/反动词库.txt",
            "static/banner_words/广告类型.txt",
            "static/banner_words/政治类型.txt",
            "static/banner_words/新思想启蒙.txt",
            "static/banner_words/暴恐词库.txt",
            "static/banner_words/民生词库.txt",
            "static/banner_words/涉枪涉爆.txt",
            "static/banner_words/网易前端过滤敏感词库.txt",
            "static/banner_words/色情类型.txt",
            "static/banner_words/色情词库.txt",
            "static/banner_words/补充词库.txt",
            "static/banner_words/贪腐词库.txt",
            "static/banner_words/零时-Tencent.txt",
            "static/banner_words/非法网址.txt"
    );

    /**
     * 加载违禁词列表
     * @param path
     * @return
     */
    private List<String> loadBannerWords(List<String> path){
        List<String> words = new ArrayList<>();
        for(String pathStr : path){
            ClassPathResource pathResource = new ClassPathResource(pathStr);
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(pathResource.getStream(), StandardCharsets.UTF_8)
            );
            List<String> list = reader.lines()
                    .filter(StringUtils::hasText)
                    .map(String::trim)
                    .toList();
            words.addAll(list);
        }


        return words;
    }



    @Override
    public ChatClientResponse adviseCall(ChatClientRequest chatClientRequest, CallAdvisorChain callAdvisorChain) {
        //调用ai前进行校验
        String userText = chatClientRequest.prompt().getUserMessage().getText();
        List<String> banner_word =  loadBannerWords(DEFAULT_BANNER_WORD_LIST);
        for(String word : banner_word){
            if(userText.contains(word)){
                System.out.println("用户包含违禁词"+word);
            }
        }
        return callAdvisorChain.nextCall(chatClientRequest);
    }

    @Override
    public Flux<ChatClientResponse> adviseStream(ChatClientRequest chatClientRequest, StreamAdvisorChain streamAdvisorChain) {
        return null;
    }

    @Override
    public String getName() {
        return this.getClass().getSimpleName();
    }

    @Override
    public int getOrder() {
        return 0;
    }
}
