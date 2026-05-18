package com.xbk.lattice.compiler.service;

import com.xbk.lattice.compiler.graph.ArticleReviewEnvelope;
import com.xbk.lattice.compiler.domain.AnalyzedConcept;
import com.xbk.lattice.compiler.domain.MergedConcept;
import com.xbk.lattice.compiler.domain.RawSource;
import com.xbk.lattice.compiler.domain.SourceBatch;
import com.xbk.lattice.governance.repo.RepoSnapshotService;
import com.xbk.lattice.infra.persistence.ArticleRecord;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * 编译节点能力服务
 *
 * 职责：为 Graph 编排提供编译节点能力
 *
 * @author xiexu
 */
@Service
@Slf4j
public class CompilePipelineService {

    private final SourceIngestSupport sourceIngestSupport;

    private final ArticleCompileSupport articleCompileSupport;

    private final ArticlePersistSupport articlePersistSupport;

    /**
     * 创建编译节点能力服务。
     *
     * @param sourceIngestSupport 源数据支撑服务
     * @param articleCompileSupport 文章编译支撑服务
     * @param articlePersistSupport 文章落库支撑服务
     */
    @Autowired
    public CompilePipelineService(
            SourceIngestSupport sourceIngestSupport,
            ArticleCompileSupport articleCompileSupport,
            ArticlePersistSupport articlePersistSupport
    ) {
        this.sourceIngestSupport = sourceIngestSupport;
        this.articleCompileSupport = articleCompileSupport;
        this.articlePersistSupport = articlePersistSupport;
    }

    /**
     * 注入整库快照服务。
     *
     * @param repoSnapshotService 整库快照服务
     */
    @Autowired(required = false)
    void setRepoSnapshotService(RepoSnapshotService repoSnapshotService) {
        articlePersistSupport.setRepoSnapshotService(repoSnapshotService);
    }

    /**
     * 注入事实证据卡生成服务。
     *
     * @param factCardGenerationService 事实证据卡生成服务
     */
    @Autowired(required = false)
    void setFactCardGenerationService(FactCardGenerationService factCardGenerationService) {
        sourceIngestSupport.setFactCardGenerationService(factCardGenerationService);
    }

    /**
     * 摄入源目录。
     *
     * @param sourceDir 源目录
     * @return 原始源文件集合
     * @throws IOException IO 异常
     */
    public List<RawSource> ingest(Path sourceDir) throws IOException {
        return sourceIngestSupport.ingest(sourceDir);
    }

    /**
     * 对源文件集合进行分组。
     *
     * @param rawSources 原始源文件集合
     * @return 分组结果
     */
    public Map<String, List<RawSource>> groupSources(List<RawSource> rawSources) {
        return sourceIngestSupport.groupSources(rawSources);
    }

    /**
     * 对分组结果执行分批。
     *
     * @param groupedSources 分组结果
     * @return 分批结果
     */
    public Map<String, List<SourceBatch>> splitBatches(Map<String, List<RawSource>> groupedSources) {
        return sourceIngestSupport.splitBatches(groupedSources);
    }

    /**
     * 分析全部批次。
     *
     * @param sourceBatches 分批结果
     * @param sourceDir 源目录
     * @return 分析结果
     */
    public List<AnalyzedConcept> analyzeBatches(
            Map<String, List<SourceBatch>> sourceBatches,
            Path sourceDir
    ) {
        return sourceIngestSupport.analyzeBatches(sourceBatches, sourceDir);
    }

    /**
     * 合并分析结果。
     *
     * @param analyzedConcepts 分析结果
     * @return 合并概念结果
     */
    public List<MergedConcept> mergeConcepts(List<AnalyzedConcept> analyzedConcepts) {
        return sourceIngestSupport.mergeConcepts(analyzedConcepts);
    }

    /**
     * 暂存待提交概念到 WAL。
     *
     * @param jobId 作业标识
     * @param mergedConcepts 合并概念
     */
    public void stageWal(String jobId, List<MergedConcept> mergedConcepts) {
        sourceIngestSupport.stageWal(jobId, mergedConcepts);
    }

    /**
     * 编译新文章草稿。
     *
     * @param mergedConcepts 合并概念
     * @param sourceDir 源目录
     * @return 草稿文章集合
     */
    public List<ArticleRecord> compileDraftArticles(List<MergedConcept> mergedConcepts, Path sourceDir) {
        return articleCompileSupport.compileDraftArticles(mergedConcepts, sourceDir);
    }

    /**
     * 执行增量规划。
     *
     * @param mergedConcepts 合并概念
     * @return 增量规划结果
     */
    public IncrementalCompilePlanResult planIncrementalGraphChanges(List<MergedConcept> mergedConcepts) {
        return sourceIngestSupport.planIncrementalGraphChanges(mergedConcepts);
    }

    /**
     * 生成增强文章草稿。
     *
     * @param enhancementConcepts 增强映射
     * @return 增强文章草稿
     */
    public List<ArticleRecord> enhanceExistingArticles(Map<String, List<MergedConcept>> enhancementConcepts) {
        return sourceIngestSupport.enhanceExistingArticles(enhancementConcepts);
    }

    /**
     * 审查草稿文章。
     *
     * @param draftArticles 草稿文章集合
     * @return 审查结果集合
     */
    public List<ArticleReviewEnvelope> reviewDraftArticles(List<ArticleRecord> draftArticles) {
        return articleCompileSupport.reviewDraftArticles(draftArticles);
    }

    /**
     * 对审查失败文章执行修复。
     *
     * @param reviewedArticles 审查后文章集合
     * @return 修复后的文章集合
     */
    public List<ArticleReviewEnvelope> fixReviewedArticles(List<ArticleReviewEnvelope> reviewedArticles) {
        return articleCompileSupport.fixReviewedArticles(reviewedArticles);
    }

    /**
     * 正式落库文章。
     *
     * @param jobId 作业标识
     * @param reviewedArticles 审查后文章集合
     * @return 已落库文章数
     */
    public int persistArticles(String jobId, List<ArticleReviewEnvelope> reviewedArticles) {
        return articlePersistSupport.persistArticles(jobId, reviewedArticles);
    }

    /**
     * 重建文章分块。
     *
     * @param reviewedArticles 已落库文章集合
     */
    public void rebuildArticleChunks(List<ArticleReviewEnvelope> reviewedArticles) {
        articlePersistSupport.rebuildArticleChunks(reviewedArticles);
    }

    /**
     * 刷新文章向量索引。
     *
     * @param reviewedArticles 已落库文章集合
     */
    public void refreshVectorIndex(List<ArticleReviewEnvelope> reviewedArticles) {
        articlePersistSupport.refreshVectorIndex(reviewedArticles);
    }

    /**
     * 刷新合成产物。
     */
    public void generateGraphSynthesisArtifacts() {
        articlePersistSupport.generateGraphSynthesisArtifacts();
    }

    /**
     * 摄取整库快照。
     *
     * @param triggerEvent 触发事件
     * @param sourceDir 源目录
     * @param persistedCount 已落库数量
     */
    public void captureRepoSnapshot(String triggerEvent, Path sourceDir, int persistedCount) {
        articlePersistSupport.captureRepoSnapshot(triggerEvent, sourceDir, persistedCount);
    }

    /**
     * 落盘源文件预览。
     *
     * @param rawSources 原始源文件集合
     */
    public void persistSourceFiles(List<RawSource> rawSources) {
        sourceIngestSupport.persistSourceFiles(rawSources);
    }

    /**
     * 落盘源文件分块。
     *
     * @param rawSources 原始源文件集合
     */
    public void persistSourceFileChunks(List<RawSource> rawSources) {
        sourceIngestSupport.persistSourceFileChunks(rawSources);
    }

    /**
     * 汇总文章最终落库形态。
     *
     * @param reviewEnvelope 审查包裹对象
     * @return 最终文章记录
     */
    public ArticleRecord finalizeArticleForPersist(ArticleReviewEnvelope reviewEnvelope) {
        return articlePersistSupport.finalizeArticleForPersist(reviewEnvelope);
    }

    /**
     * 返回编译角色当前路由。
     *
     * @return 编译角色路由
     */
    public String currentCompileRoute() {
        return articleCompileSupport.currentCompileRoute();
    }

    /**
     * 返回审查角色当前路由。
     *
     * @return 审查角色路由
     */
    public String currentReviewRoute() {
        return articleCompileSupport.currentReviewRoute();
    }

    /**
     * 返回修复角色当前路由。
     *
     * @return 修复角色路由
     */
    public String currentFixRoute() {
        return articleCompileSupport.currentFixRoute();
    }
}
