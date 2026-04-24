package com.xbk.lattice.compiler.graph;

import com.xbk.lattice.compiler.ast.domain.AstGraphExtractReport;
import com.xbk.lattice.compiler.domain.AnalyzedConcept;
import com.xbk.lattice.compiler.domain.MergedConcept;
import com.xbk.lattice.compiler.domain.RawSource;
import com.xbk.lattice.compiler.domain.SourceBatch;
import com.xbk.lattice.infra.persistence.ArticleRecord;

import java.util.List;
import java.util.Map;

/**
 * 编译工作集存储
 *
 * 职责：外置存放跨图节点传递的大对象载荷
 *
 * @author xiexu
 */
public interface CompileWorkingSetStore {

    /**
     * 保存原始源文件集合。
     *
     * @param jobId 作业标识
     * @param rawSources 原始源文件集合
     * @return 引用标识
     */
    String saveRawSources(String jobId, List<RawSource> rawSources);

    /**
     * 读取原始源文件集合。
     *
     * @param ref 引用标识
     * @return 原始源文件集合
     */
    List<RawSource> loadRawSources(String ref);

    /**
     * 保存分组结果。
     *
     * @param jobId 作业标识
     * @param groupedSources 分组结果
     * @return 引用标识
     */
    String saveGroupedSources(String jobId, Map<String, List<RawSource>> groupedSources);

    /**
     * 读取分组结果。
     *
     * @param ref 引用标识
     * @return 分组结果
     */
    Map<String, List<RawSource>> loadGroupedSources(String ref);

    /**
     * 保存分批结果。
     *
     * @param jobId 作业标识
     * @param sourceBatches 分批结果
     * @return 引用标识
     */
    String saveSourceBatches(String jobId, Map<String, List<SourceBatch>> sourceBatches);

    /**
     * 读取分批结果。
     *
     * @param ref 引用标识
     * @return 分批结果
     */
    Map<String, List<SourceBatch>> loadSourceBatches(String ref);

    /**
     * 保存分析结果。
     *
     * @param jobId 作业标识
     * @param analyzedConcepts 分析结果
     * @return 引用标识
     */
    String saveAnalyzedConcepts(String jobId, List<AnalyzedConcept> analyzedConcepts);

    /**
     * 读取分析结果。
     *
     * @param ref 引用标识
     * @return 分析结果
     */
    List<AnalyzedConcept> loadAnalyzedConcepts(String ref);

    /**
     * 保存合并概念结果。
     *
     * @param jobId 作业标识
     * @param mergedConcepts 合并概念结果
     * @return 引用标识
     */
    String saveMergedConcepts(String jobId, List<MergedConcept> mergedConcepts);

    /**
     * 读取合并概念结果。
     *
     * @param ref 引用标识
     * @return 合并概念结果
     */
    List<MergedConcept> loadMergedConcepts(String ref);

    /**
     * 保存增强映射。
     *
     * @param jobId 作业标识
     * @param enhancementConcepts 增强映射
     * @return 引用标识
     */
    String saveEnhancementConcepts(String jobId, Map<String, List<MergedConcept>> enhancementConcepts);

    /**
     * 读取增强映射。
     *
     * @param ref 引用标识
     * @return 增强映射
     */
    Map<String, List<MergedConcept>> loadEnhancementConcepts(String ref);

    /**
     * 保存待新建概念集合。
     *
     * @param jobId 作业标识
     * @param conceptsToCreate 待新建概念集合
     * @return 引用标识
     */
    String saveConceptsToCreate(String jobId, List<MergedConcept> conceptsToCreate);

    /**
     * 读取待新建概念集合。
     *
     * @param ref 引用标识
     * @return 待新建概念集合
     */
    List<MergedConcept> loadConceptsToCreate(String ref);

    /**
     * 保存草稿文章集合。
     *
     * @param jobId 作业标识
     * @param draftArticles 草稿文章集合
     * @return 引用标识
     */
    String saveDraftArticles(String jobId, List<ArticleRecord> draftArticles);

    /**
     * 读取草稿文章集合。
     *
     * @param ref 引用标识
     * @return 草稿文章集合
     */
    List<ArticleRecord> loadDraftArticles(String ref);

    /**
     * 保存审查后文章集合。
     *
     * @param jobId 作业标识
     * @param reviewedArticles 审查后文章集合
     * @return 引用标识
     */
    String saveReviewedArticles(String jobId, List<ArticleReviewEnvelope> reviewedArticles);

    /**
     * 读取审查后文章集合。
     *
     * @param ref 引用标识
     * @return 审查后文章集合
     */
    List<ArticleReviewEnvelope> loadReviewedArticles(String ref);

    /**
     * 保存审查分区结果。
     *
     * @param jobId 作业标识
     * @param reviewPartition 审查分区
     * @return 引用标识
     */
    String saveReviewPartition(String jobId, ReviewPartition reviewPartition);

    /**
     * 读取审查分区结果。
     *
     * @param ref 引用标识
     * @return 审查分区
     */
    ReviewPartition loadReviewPartition(String ref);

    /**
     * 保存已冻结可落库文章集合。
     *
     * @param jobId 作业标识
     * @param acceptedArticles 可落库文章集合
     * @return 引用标识
     */
    String saveAcceptedArticles(String jobId, List<ArticleReviewEnvelope> acceptedArticles);

    /**
     * 读取已冻结可落库文章集合。
     *
     * @param ref 引用标识
     * @return 可落库文章集合
     */
    List<ArticleReviewEnvelope> loadAcceptedArticles(String ref);

    /**
     * 保存待人工复核文章集合。
     *
     * @param jobId 作业标识
     * @param needsHumanReviewArticles 待人工复核文章集合
     * @return 引用标识
     */
    String saveNeedsHumanReviewArticles(String jobId, List<ArticleReviewEnvelope> needsHumanReviewArticles);

    /**
     * 读取待人工复核文章集合。
     *
     * @param ref 引用标识
     * @return 待人工复核文章集合
     */
    List<ArticleReviewEnvelope> loadNeedsHumanReviewArticles(String ref);

    /**
     * 保存 AST 图谱抽取报告。
     *
     * @param jobId 作业标识
     * @param astGraphExtractReport 抽取报告
     * @return 引用标识
     */
    String saveAstExtractReport(String jobId, AstGraphExtractReport astGraphExtractReport);

    /**
     * 读取 AST 图谱抽取报告。
     *
     * @param ref 引用标识
     * @return 抽取报告
     */
    AstGraphExtractReport loadAstExtractReport(String ref);

    /**
     * 按作业清理工作集。
     *
     * @param jobId 作业标识
     */
    void deleteByJobId(String jobId);
}
