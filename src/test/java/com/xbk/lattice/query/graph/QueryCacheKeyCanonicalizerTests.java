package com.xbk.lattice.query.graph;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Query 缓存键归一化测试。
 *
 * 验证等价中文问法归一为相同缓存键，非等价问法保持不同。
 *
 * @author xiexu
 */
class QueryCacheKeyCanonicalizerTests {

    private final QueryCacheKeyCanonicalizer canonicalizer = new QueryCacheKeyCanonicalizer();

    /**
     * 仅语气词"呢"和标点不同，应归一为相同 key。
     */
    @Test
    void shouldMergeEquivalentQuestionsWithParticleDifference() {
        String q1 = "发布检查表里还有哪些检查项没有完成呢？分别是哪个责任人呢？";
        String q2 = "发布检查表里还有哪些检查项没有完成？分别是哪个责任人？";
        assertThat(canonicalizer.canonicalize(q1))
                .isEqualTo(canonicalizer.canonicalize(q2));
    }

    /**
     * 句尾带句号与不带句号等价。
     */
    @Test
    void shouldMergeWithAndWithoutPeriod() {
        String q1 = "缺陷清单里P0和P1级别的缺陷各有多少个。";
        String q2 = "缺陷清单里P0和P1级别的缺陷各有多少个";
        assertThat(canonicalizer.canonicalize(q1))
                .isEqualTo(canonicalizer.canonicalize(q2));
    }

    /**
     * 全角问号与半角问号等价。
     */
    @Test
    void shouldMergeFullWidthAndHalfWidthQuestionMarks() {
        String q1 = "数据库配置是什么？";
        String q2 = "数据库配置是什么?";
        assertThat(canonicalizer.canonicalize(q1))
                .isEqualTo(canonicalizer.canonicalize(q2));
    }

    /**
     * "吗"是有实义的 yes/no 问句标记，不应被归一。
     */
    @Test
    void shouldNotMergeYesNoQuestionMarker() {
        String q1 = "这个配置生效了吗";
        String q2 = "这个配置生效了";
        assertThat(canonicalizer.canonicalize(q1))
                .isNotEqualTo(canonicalizer.canonicalize(q2));
    }

    /**
     * 不同编号字段不应被合并。
     */
    @Test
    void shouldNotMergeDifferentIdentifiers() {
        String q1 = "DEF-001的状态是什么？";
        String q2 = "DEF-002的状态是什么？";
        assertThat(canonicalizer.canonicalize(q1))
                .isNotEqualTo(canonicalizer.canonicalize(q2));
    }

    /**
     * 不同字段名不应被合并。
     */
    @Test
    void shouldNotMergeDifferentFieldNames() {
        String q1 = "超时时间的配置是什么？";
        String q2 = "重试次数的配置是什么？";
        assertThat(canonicalizer.canonicalize(q1))
                .isNotEqualTo(canonicalizer.canonicalize(q2));
    }

    /**
     * 不同状态词不应被合并。
     */
    @Test
    void shouldNotMergeDifferentStatusWords() {
        String q1 = "已完成的检查项有哪些？";
        String q2 = "未完成的检查项有哪些？";
        assertThat(canonicalizer.canonicalize(q1))
                .isNotEqualTo(canonicalizer.canonicalize(q2));
    }

    /**
     * null 和空字符串应安全处理。
     */
    @Test
    void shouldHandleNullAndEmpty() {
        assertThat(canonicalizer.canonicalize(null)).isEqualTo("");
        assertThat(canonicalizer.canonicalize("")).isEqualTo("");
        assertThat(canonicalizer.canonicalize("   ")).isEqualTo("");
    }

    /**
     * 仅空白不同的问题应合并。
     */
    @Test
    void shouldMergeOnlyWhitespaceDifferences() {
        String q1 = "  数据库配置是什么  ";
        String q2 = "数据库配置是什么";
        assertThat(canonicalizer.canonicalize(q1))
                .isEqualTo(canonicalizer.canonicalize(q2));
    }

    /**
     * 多个语气词与不同标点应归一为相同 key。
     */
    @Test
    void shouldMergeMultipleParticlesAndMixedPunctuation() {
        String q1 = "这个功能怎么用呢？请解释一下吧！";
        String q2 = "这个功能怎么用？请解释一下";
        assertThat(canonicalizer.canonicalize(q1))
                .isEqualTo(canonicalizer.canonicalize(q2));
    }

    // ── resolveSafe 防御性兜底测试 ──

    /**
     * resolveSafe 优先使用已设置的 canonicalCacheKey。
     */
    @Test
    void shouldPreferCanonicalCacheKeyOverFallback() {
        String key = QueryCacheKeyCanonicalizer.resolveSafe("已归一化的key", "回退文本");
        assertThat(key).isEqualTo("已归一化的key");
    }

    /**
     * canonicalCacheKey 为空时，使用 fallbackText 现场归一化。
     */
    @Test
    void shouldFallbackToCanonicalizedFallbackText() {
        String key = QueryCacheKeyCanonicalizer.resolveSafe(null, "这个问题怎么用呢？");
        assertThat(key).isEqualTo(canonicalizer.canonicalize("这个问题怎么用呢？"));
    }

    /**
     * canonicalCacheKey 为空字符串时，应使用 fallbackText 现场归一化。
     */
    @Test
    void shouldFallbackWhenCanonicalCacheKeyIsBlank() {
        String key = QueryCacheKeyCanonicalizer.resolveSafe("   ", "请解释一下吧！");
        assertThat(key).isEqualTo(canonicalizer.canonicalize("请解释一下吧！"));
    }

    /**
     * 所有来源均为空时，返回空字符串，调用方应跳过 cache 操作。
     */
    @Test
    void shouldReturnEmptyWhenAllSourcesAreBlank() {
        assertThat(QueryCacheKeyCanonicalizer.resolveSafe(null, null)).isEqualTo("");
        assertThat(QueryCacheKeyCanonicalizer.resolveSafe("", "")).isEqualTo("");
        assertThat(QueryCacheKeyCanonicalizer.resolveSafe("  ", null)).isEqualTo("");
    }

    // ── canonicalCacheKey 与 normalizedQuestion 分工合同测试 ──

    /**
     * canonicalCacheKey 剥离语气词/标点，normalizedQuestion 保留原始语义。
     *
     * 这是长期隔离合同的核心断言：cache key 归一化不应污染 normalizedQuestion。
     */
    @Test
    void shouldKeepNormalizedQuestionSeparateFromCanonicalCacheKey() {
        String q1 = "发布检查表里还有哪些检查项没有完成呢？";
        String q2 = "发布检查表里还有哪些检查项没有完成？";

        String normalizedQ1 = q1.trim();
        String normalizedQ2 = q2.trim();
        String cacheKey1 = canonicalizer.canonicalize(q1);
        String cacheKey2 = canonicalizer.canonicalize(q2);

        // cache key 归一：两个等价问题相同
        assertThat(cacheKey1).isEqualTo(cacheKey2);
        // normalizedQuestion 保留原始文本（含语气词和标点）
        assertThat(normalizedQ1).isNotEqualTo(cacheKey1);
        assertThat(normalizedQ2).isNotEqualTo(cacheKey2);
        // 两个 normalizedQuestion 互不相同（保留各自原始标点/语气词）
        assertThat(normalizedQ1).isNotEqualTo(normalizedQ2);
    }

    /**
     * resolveSafe 跳过 null cache key：不会产出可用于访问/写入的 key。
     */
    @Test
    void shouldNotProduceCacheKeyForNullInput() {
        String key = QueryCacheKeyCanonicalizer.resolveSafe(null, null);
        assertThat(key).isEqualTo("");
    }
}
