package com.xbk.lattice.query.graph;

import java.util.regex.Pattern;

/**
 * Query 缓存键归一化工具。
 *
 * 职责：将语义等价但标点/语气词不同的中文问法归一为相同缓存键，
 * 避免同一问题的不同书写形式命中不同缓存条目。
 *
 * @author xiexu
 */
public final class QueryCacheKeyCanonicalizer {

    /** 句尾/句中无实义标点，归一为空格以保留词边界。 */
    private static final Pattern NON_SEMANTIC_PUNCTUATION = Pattern.compile("[？?！!。；;，,：:…]+");

    /**
     * 无实义中文句尾语气词。
     *
     * 不包含"吗"（yes/no 问句标记，有实义）。
     * 匹配条件：语气词后必须跟随空白或行尾（避免误删复合词中的同形字）。
     */
    private static final Pattern SENTENCE_FINAL_PARTICLE = Pattern.compile("[呢吧啊呀嘛哦哟咯啦](?=\\s|$)");

    private static final Pattern WHITESPACE_COLLAPSE = Pattern.compile("\\s{2,}");

    /**
     * 归一化 query 缓存键。
     *
     * @param question 用户原始问题
     * @return 归一化后的缓存键；null 或 blank 返回空字符串
     */
    public String canonicalize(String question) {
        if (question == null || question.isBlank()) {
            return "";
        }
        String normalized = question.trim();
        normalized = NON_SEMANTIC_PUNCTUATION.matcher(normalized).replaceAll(" ");
        normalized = SENTENCE_FINAL_PARTICLE.matcher(normalized).replaceAll("");
        normalized = WHITESPACE_COLLAPSE.matcher(normalized).replaceAll(" ");
        return normalized.trim();
    }

    /**
     * 安全解析缓存键。
     *
     * 优先使用已设置的 {@code canonicalCacheKey}，为空时用 {@code fallbackText} 现场归一化。
     * 仍为空时返回空字符串，调用方应跳过 cache get/put 操作。
     *
     * @param canonicalCacheKey 已设置的标准化缓存键，可为 null
     * @param fallbackText 回退文本，可为 null
     * @return 非空缓存键；所有来源均为空时返回空字符串
     */
    public static String resolveSafe(String canonicalCacheKey, String fallbackText) {
        if (canonicalCacheKey != null && !canonicalCacheKey.isBlank()) {
            return canonicalCacheKey;
        }
        if (fallbackText != null && !fallbackText.isBlank()) {
            return new QueryCacheKeyCanonicalizer().canonicalize(fallbackText);
        }
        return "";
    }
}
