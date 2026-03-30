package com.lin.linagent.service;

import cn.hutool.core.io.resource.ClassPathResource;
import lombok.Data;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 内容安全校验服务
 */
@Service
public class ContentSafetyService {

    /**
     * 需要重点拦截的高风险词库
     */
    private static final List<String> HIGH_RISK_WORD_FILES = List.of(
            "static/banner_words/暴恐词库.txt",
            "static/banner_words/涉枪涉爆.txt",
            "static/banner_words/色情词库.txt",
            "static/banner_words/非法网址.txt"
    );

    /**
     * 情感场景下需要优先干预的危机表达
     */
    private static final List<String> CRISIS_KEYWORDS = List.of(
            "不想活", "不想活了", "活不下去", "想死", "想自杀", "自杀", "轻生", "结束生命",
            "了结自己", "伤害自己", "自残", "割腕", "跳楼", "服毒", "离开这个世界"
    );

    private volatile Set<String> blockedKeywordsCache;

    /**
     * 校验用户输入
     * @param message 用户输入
     * @return 安全判断结果
     */
    public SafetyDecision inspectUserMessage(String message) {
        String normalizedMessage = StringUtils.normalizeSpace(StringUtils.defaultString(message));
        if (StringUtils.isBlank(normalizedMessage)) {
            return SafetyDecision.pass();
        }
        for (String keyword : CRISIS_KEYWORDS) {
            if (normalizedMessage.contains(keyword)) {
                return SafetyDecision.crisis("""
                        我注意到你这句话里出现了比较强烈的危险信号，我先不继续普通问答。

                        现在更重要的是先确保你身边有人能及时接住你：
                        1. 立刻联系你最信任的家人、朋友、室友或老师，直接告诉对方你现在状态很危险，不要一个人扛。
                        2. 如果你已经有伤害自己的打算或工具，请马上把自己带到有人的地方，并让身边的人陪着你。
                        3. 如果风险已经很紧急，请直接拨打当地急救电话，或尽快前往最近的医院急诊 / 心理危机干预门诊。

                        如果你愿意，你现在可以只回复我两类信息中的一个：
                        - “我身边有人”
                        - “我现在一个人”
                        """);
            }
        }
        for (String keyword : getBlockedKeywords()) {
            if (keyword.length() < 2) {
                continue;
            }
            if (normalizedMessage.contains(keyword)) {
                return SafetyDecision.blocked("当前请求涉及高风险敏感内容，系统已触发安全保护，暂不继续处理这类内容。");
            }
        }
        return SafetyDecision.pass();
    }

    /**
     * 读取高风险词库，首次使用时缓存
     * @return 词集合
     */
    private Set<String> getBlockedKeywords() {
        if (blockedKeywordsCache != null) {
            return blockedKeywordsCache;
        }
        synchronized (this) {
            if (blockedKeywordsCache != null) {
                return blockedKeywordsCache;
            }
            Set<String> keywords = new LinkedHashSet<>();
            for (String filePath : HIGH_RISK_WORD_FILES) {
                ClassPathResource resource = new ClassPathResource(filePath);
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(resource.getStream(), StandardCharsets.UTF_8))) {
                    reader.lines()
                            .map(String::trim)
                            .filter(StringUtils::isNotBlank)
                            .forEach(keywords::add);
                } catch (Exception ignored) {
                }
            }
            blockedKeywordsCache = keywords;
            return blockedKeywordsCache;
        }
    }

    /**
     * 安全判断结果
     */
    @Data
    public static class SafetyDecision {
        private boolean pass;
        private boolean blocked;
        private boolean crisis;
        private String userMessage;
        private String reason;

        public static SafetyDecision pass() {
            SafetyDecision decision = new SafetyDecision();
            decision.setPass(true);
            decision.setReason("");
            return decision;
        }

        public static SafetyDecision blocked(String userMessage) {
            SafetyDecision decision = new SafetyDecision();
            decision.setPass(false);
            decision.setBlocked(true);
            decision.setUserMessage(userMessage);
            decision.setReason("命中高风险敏感内容保护策略");
            return decision;
        }

        public static SafetyDecision crisis(String userMessage) {
            SafetyDecision decision = new SafetyDecision();
            decision.setPass(false);
            decision.setCrisis(true);
            decision.setUserMessage(userMessage);
            decision.setReason("命中情绪危机干预策略");
            return decision;
        }
    }
}
