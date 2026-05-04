package com.xbk.lattice.query.service;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.Locale;

/**
 * 查询意图分类器
 *
 * 职责：用本地规则识别查询更偏代码、配置、故障排查、架构解释还是通用知识
 *
 * @author xiexu
 */
@Service
@Profile("jdbc")
public class QueryIntentClassifier {

    /**
     * 识别查询意图。
     *
     * @param question 查询问题
     * @return 查询意图
     */
    public QueryIntent classify(String question) {
        if (question == null || question.isBlank()) {
            return QueryIntent.GENERAL;
        }
        String normalizedQuestion = question.toLowerCase(Locale.ROOT);
        if (looksLikeExactLookupQuestion(normalizedQuestion)) {
            return QueryIntent.CONFIGURATION;
        }
        if (looksLikeArchitectureQuestion(normalizedQuestion)) {
            return QueryIntent.ARCHITECTURE;
        }
        if (containsAny(normalizedQuestion, ".java", "class", "method", "controller", "service", "repository",
                "调用链", "类名", "方法", "接口实现", "源码", "代码")) {
            return QueryIntent.CODE_STRUCTURE;
        }
        if (containsAny(normalizedQuestion, ".yaml", ".yml", ".properties", "config", "配置", "参数", "阈值",
                "开关", "timeout", "retry", "endpoint", "url", "key")) {
            return QueryIntent.CONFIGURATION;
        }
        if (containsAny(normalizedQuestion, "error", "exception", "failed", "fail", "失败", "异常", "报错",
                "排查", "为什么不", "无法", "超时")) {
            return QueryIntent.TROUBLESHOOTING;
        }
        return QueryIntent.GENERAL;
    }

    /**
     * 判断问题是否更像精确查值/精确结论题。
     *
     * @param normalizedQuestion 归一化问题
     * @return 精确查值题返回 true
     */
    private boolean looksLikeExactLookupQuestion(String normalizedQuestion) {
        return containsAny(
                normalizedQuestion,
                "命中数",
                "接口路径",
                "路径是什么",
                "哪个接口",
                "对应哪个接口",
                "第几批",
                "哪一批",
                "分别是什么",
                "分别是多少",
                "结论是什么",
                "状态是什么",
                "是否生效",
                "是否启用",
                "是否一致",
                "关键差异"
        );
    }

    /**
     * 判断问题是否更偏架构解释。
     *
     * @param normalizedQuestion 归一化问题
     * @return 架构题返回 true
     */
    private boolean looksLikeArchitectureQuestion(String normalizedQuestion) {
        return containsAny(
                normalizedQuestion,
                "architecture",
                "design",
                "adr",
                "架构",
                "设计",
                "方案",
                "取舍",
                "为什么要",
                "优缺点",
                "对比",
                "同步",
                "异步"
        );
    }

    /**
     * 判断文本是否包含任一片段。
     *
     * @param text 文本
     * @param fragments 片段
     * @return 是否包含
     */
    private boolean containsAny(String text, String... fragments) {
        for (String fragment : fragments) {
            if (text.contains(fragment)) {
                return true;
            }
        }
        return false;
    }
}
