package com.xbk.lattice.query.service;

import com.xbk.lattice.query.domain.AnswerOutcome;
import com.xbk.lattice.query.domain.GenerationMode;
import com.xbk.lattice.query.domain.ModelExecutionStatus;
import com.xbk.lattice.query.domain.QueryAnswerPayload;

import java.util.List;
import java.util.Set;

/**
 * 答案生成支撑契约
 *
 * 职责：声明分层支撑类之间互相调用的 package-private 能力
 *
 * @author xiexu
 */
abstract class AnswerGenerationSupportContract {

    /**
     * 匹配 fallback 命中的对比选项。
     *
     * @param fallbackHit fallback 命中
     * @param leftOption 左选项
     * @param rightOption 右选项
     * @return 命中的选项
     */
    abstract String matchComparisonOption(QueryArticleHit fallbackHit, String leftOption, String rightOption);

    /**
     * 选择选项专属 fallback 片段。
     *
     * @param queryArticleHit 查询命中
     * @param option 对比选项
     * @param fallbackTokens fallback token
     * @return 片段
     */
    abstract String selectOptionSpecificFallbackSnippet(
            QueryArticleHit queryArticleHit,
            String option,
            List<String> fallbackTokens
    );

    /**
     * 选择单条贴题 fallback 片段。
     *
     * @param question 用户问题
     * @param queryArticleHit 查询命中
     * @param queryTokens 查询 token
     * @return 片段
     */
    abstract String selectQuestionFocusedFallbackSnippet(
            String question,
            QueryArticleHit queryArticleHit,
            List<String> queryTokens
    );

    /**
     * 提取 metadata description。
     *
     * @param metadataJson metadata JSON
     * @return description
     */
    abstract String extractDescription(String metadataJson);

    /**
     * 提取证据路径。
     *
     * @param snippets 证据片段
     * @return 路径列表
     */
    abstract List<String> extractEvidencePaths(List<String> snippets);

    /**
     * 计算贴题 fallback 行分值。
     *
     * @param question 用户问题
     * @param rawLine 原始行
     * @param normalizedLine 归一化行
     * @param queryTokens 查询 token
     * @return 分值
     */
    abstract int scoreQuestionFocusedFallbackLine(
            String question,
            String rawLine,
            String normalizedLine,
            List<String> queryTokens
    );

    /**
     * 判断候选行是否覆盖显式标识。
     *
     * @param normalizedLine 归一化行
     * @param question 用户问题
     * @return 覆盖返回 true
     */
    abstract boolean containsRequestedExactIdentifier(String normalizedLine, String question);

    /**
     * 追加聚合结论行。
     *
     * @param question 用户问题
     * @param match 候选事实
     * @param conclusionLines 结论行
     * @param selectedSemanticKeys 已选语义键
     */
    abstract void appendAggregatedConclusionLine(
            String question,
            EvidenceLineMatch match,
            List<String> conclusionLines,
            Set<String> selectedSemanticKeys
    );

    /**
     * 选择多条贴题 fallback 片段。
     *
     * @param question 用户问题
     * @param queryArticleHit 查询命中
     * @param queryTokens 查询 token
     * @param limit 条数上限
     * @return 片段列表
     */
    abstract List<String> selectQuestionFocusedFallbackSnippets(
            String question,
            QueryArticleHit queryArticleHit,
            List<String> queryTokens,
            int limit
    );

    /**
     * 选择 path 契约候选行。
     *
     * @param queryArticleHit 查询命中
     * @return 候选行
     */
    abstract List<String> selectPathContractCandidateLines(QueryArticleHit queryArticleHit);

    /**
     * 生成聚合证据语义键。
     *
     * @param question 用户问题
     * @param snippet 候选片段
     * @return 语义键
     */
    abstract String aggregatedEvidenceSemanticKey(String question, String snippet);

    /**
     * 提取机器标识符。
     *
     * @param snippet 候选片段
     * @return 标识符列表
     */
    abstract List<String> extractMachineIdentifiers(String snippet);

    /**
     * 选择匹配正文行。
     *
     * @param content 正文
     * @param queryTokens 查询 token
     * @return 匹配行
     */
    abstract List<String> selectMatchedLines(String content, List<String> queryTokens);

    /**
     * 提取查询 token。
     *
     * @param question 用户问题
     * @return token 列表
     */
    abstract List<String> extractQueryTokens(String question);

    /**
     * 判断单条证据是否可直接回答。
     *
     * @param question 用户问题
     * @param queryArticleHit 查询命中
     * @return 可直接回答返回 true
     */
    abstract boolean isDirectFallbackAnswerable(String question, QueryArticleHit queryArticleHit);

    /**
     * 构造确定性 fallback 载荷。
     *
     * @param question 用户问题
     * @param queryArticleHits 查询命中
     * @param preferredOutcome 期望语义
     * @param generationMode 生成模式
     * @param modelExecutionStatus 模型执行状态
     * @param fallbackReason fallback 原因
     * @return 答案载荷
     */
    abstract QueryAnswerPayload buildEvidencePayload(
            String question,
            List<QueryArticleHit> queryArticleHits,
            AnswerOutcome preferredOutcome,
            GenerationMode generationMode,
            ModelExecutionStatus modelExecutionStatus,
            String fallbackReason
    );

    /**
     * 提取可复用问题锚点。
     *
     * @param question 用户问题
     * @return 锚点列表
     */
    abstract List<String> extractReusableQuestionAnchors(String question);

    /**
     * 统计命中的可复用锚点。
     *
     * @param normalizedMarkdown 归一化答案
     * @param reusableAnchors 可复用锚点
     * @return 命中数量
     */
    abstract int countMatchedReusableAnchors(String normalizedMarkdown, List<String> reusableAnchors);
}
