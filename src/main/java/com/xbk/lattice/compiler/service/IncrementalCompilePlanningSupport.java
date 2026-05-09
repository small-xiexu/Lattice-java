package com.xbk.lattice.compiler.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xbk.lattice.compiler.config.CompilerProperties;
import com.xbk.lattice.compiler.domain.AnalyzedConcept;
import com.xbk.lattice.compiler.domain.ConceptSection;
import com.xbk.lattice.compiler.domain.MergedConcept;
import com.xbk.lattice.compiler.domain.RawSource;
import com.xbk.lattice.compiler.domain.SourceBatch;
import com.xbk.lattice.compiler.node.AnalyzeNode;
import com.xbk.lattice.compiler.node.BatchSplitNode;
import com.xbk.lattice.compiler.node.CompileArticleNode;
import com.xbk.lattice.compiler.node.CrossGroupMergeNode;
import com.xbk.lattice.compiler.node.GroupNode;
import com.xbk.lattice.compiler.node.IngestNode;
import com.xbk.lattice.compiler.prompt.LatticePrompts;
import com.xbk.lattice.compiler.prompt.SchemaAwarePrompts;
import com.xbk.lattice.documentparse.application.DocumentParseApplicationService;
import com.xbk.lattice.infra.persistence.ArticleChunkJdbcRepository;
import com.xbk.lattice.infra.persistence.ArticleJdbcRepository;
import com.xbk.lattice.infra.persistence.ArticleRecord;
import com.xbk.lattice.infra.persistence.SourceFileChunkJdbcRepository;
import com.xbk.lattice.infra.persistence.SourceFileJdbcRepository;
import com.xbk.lattice.infra.persistence.SourceFileRecord;
import com.xbk.lattice.governance.DependencyGraphService;
import com.xbk.lattice.governance.PropagationItem;
import com.xbk.lattice.governance.PropagationReport;
import com.xbk.lattice.governance.PropagationService;
import com.xbk.lattice.governance.repo.RepoSnapshotService;
import com.xbk.lattice.query.service.ArticleChunkVectorIndexService;
import com.xbk.lattice.query.service.ArticleVectorIndexService;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 增量编译规划支持。
 *
 * 职责：基于现有文章、概念和传播关系生成新增与增强计划。
 *
 * @author xiexu
 */
@Slf4j
abstract class IncrementalCompilePlanningSupport extends IncrementalCompileWritebackSupport {

    /**
     * 创建增量编译服务。
     *
     * @param compilerProperties 编译配置
     * @param llmGateway LLM 网关
     * @param articleReviewerGateway 文章审查网关
     * @param reviewFixService 审查修复服务
     * @param synthesisArtifactsService 合成产物服务
     * @param articleJdbcRepository 文章仓储
     * @param articleChunkJdbcRepository 文章 chunk 仓储
     * @param sourceFileJdbcRepository 源文件仓储
     * @param sourceFileChunkJdbcRepository 源文件 chunk 仓储
     * @param articleVectorIndexService 文章向量索引服务
     * @param articleChunkVectorIndexService 文章分块向量索引服务
     * @param documentParseApplicationService 文档解析应用服务
     */
    protected IncrementalCompilePlanningSupport(
            CompilerProperties compilerProperties,
            LlmGateway llmGateway,
            ArticleReviewerGateway articleReviewerGateway,
            ReviewFixService reviewFixService,
            SynthesisArtifactsService synthesisArtifactsService,
            ArticleJdbcRepository articleJdbcRepository,
            ArticleChunkJdbcRepository articleChunkJdbcRepository,
            SourceFileJdbcRepository sourceFileJdbcRepository,
            SourceFileChunkJdbcRepository sourceFileChunkJdbcRepository,
            ArticleVectorIndexService articleVectorIndexService,
            ArticleChunkVectorIndexService articleChunkVectorIndexService,
            DocumentParseApplicationService documentParseApplicationService
    ) {
        super(
                compilerProperties,
                llmGateway,
                articleReviewerGateway,
                reviewFixService,
                synthesisArtifactsService,
                articleJdbcRepository,
                articleChunkJdbcRepository,
                sourceFileJdbcRepository,
                sourceFileChunkJdbcRepository,
                articleVectorIndexService,
                articleChunkVectorIndexService,
                documentParseApplicationService
        );
    }

    /**
     * 规划增量编译的增强与新建动作。
     *
     * @param mergedConcepts 增量概念
     * @param existingArticles 已有文章
     * @return 增量计划
     */
    protected IncrementalPlan planIncrementalChanges(List<MergedConcept> mergedConcepts, List<ArticleRecord> existingArticles) {
        return buildRuleBasedPlan(mergedConcepts, existingArticles);
    }
    /**
     * 构建规则主导的增量计划。
     *
     * @param mergedConcepts 增量概念
     * @param existingArticles 已有文章
     * @return 增量计划
     */
    protected IncrementalPlan buildRuleBasedPlan(List<MergedConcept> mergedConcepts, List<ArticleRecord> existingArticles) {
        Map<String, List<MergedConcept>> directHitConcepts = resolveDirectHitConcepts(mergedConcepts, existingArticles);
        List<EnhancementPlan> enhancements = new ArrayList<EnhancementPlan>();
        for (Map.Entry<String, List<MergedConcept>> entry : directHitConcepts.entrySet()) {
            enhancements.add(buildEnhancementPlan(entry.getKey(), entry.getValue()));
        }
        enhancements.addAll(buildPropagationPlans(directHitConcepts, existingArticles));
        return new IncrementalPlan(enhancements, buildNewArticlePlans(mergedConcepts, existingArticles));
    }
    /**
     * 解析直命中文章集合。
     *
     * @param mergedConcepts 增量概念
     * @param existingArticles 已有文章
     * @return 直命中文章到增量概念的映射
     */
    protected Map<String, List<MergedConcept>> resolveDirectHitConcepts(
            List<MergedConcept> mergedConcepts,
            List<ArticleRecord> existingArticles
    ) {
        Map<String, List<MergedConcept>> result = new LinkedHashMap<String, List<MergedConcept>>();
        for (ArticleRecord existingArticle : existingArticles) {
            for (MergedConcept mergedConcept : mergedConcepts) {
                if (!isDirectHit(existingArticle, mergedConcept)) {
                    continue;
                }
                addMatchedConcept(result, existingArticle.getConceptId(), mergedConcept);
            }
        }
        return result;
    }
    /**
     * 判断文章是否被本次变更直接命中。
     *
     * @param existingArticle 已有文章
     * @param mergedConcept 增量概念
     * @return 是否直命中
     */
    protected boolean isDirectHit(ArticleRecord existingArticle, MergedConcept mergedConcept) {
        if (normalizeConceptId(existingArticle.getConceptId()).equals(normalizeConceptId(mergedConcept.getConceptId()))) {
            return true;
        }
        return hasSourceIntersection(existingArticle.getSourcePaths(), mergedConcept.getSourcePaths());
    }
    /**
     * 判断两组来源路径是否存在交集。
     *
     * @param leftSourcePaths 左侧来源路径
     * @param rightSourcePaths 右侧来源路径
     * @return 是否存在交集
     */
    protected boolean hasSourceIntersection(List<String> leftSourcePaths, List<String> rightSourcePaths) {
        Set<String> normalizedPaths = new LinkedHashSet<String>();
        if (leftSourcePaths == null || rightSourcePaths == null) {
            return false;
        }
        for (String sourcePath : leftSourcePaths) {
            if (sourcePath == null || sourcePath.isBlank()) {
                continue;
            }
            normalizedPaths.add(sourcePath.trim());
        }
        for (String sourcePath : rightSourcePaths) {
            if (sourcePath == null || sourcePath.isBlank()) {
                continue;
            }
            if (normalizedPaths.contains(sourcePath.trim())) {
                return true;
            }
        }
        return false;
    }
    /**
     * 构建传播增强计划。
     *
     * @param directHitConcepts 直命中文章集合
     * @param existingArticles 已有文章
     * @return 下游增强计划列表
     */
    protected List<EnhancementPlan> buildPropagationPlans(
            Map<String, List<MergedConcept>> directHitConcepts,
            List<ArticleRecord> existingArticles
    ) {
        List<EnhancementPlan> propagationPlans = new ArrayList<EnhancementPlan>();
        if (directHitConcepts.isEmpty()) {
            return propagationPlans;
        }
        PropagationService propagationService = new PropagationService(
                new DependencyGraphService(articleJdbcRepository),
                articleJdbcRepository
        );
        Map<String, ArticleRecord> existingArticleMap = indexExistingArticles(existingArticles);
        Set<String> emittedPlanKeys = new LinkedHashSet<String>();
        for (Map.Entry<String, List<MergedConcept>> entry : directHitConcepts.entrySet()) {
            ArticleRecord rootArticle = existingArticleMap.get(normalizeConceptId(entry.getKey()));
            if (rootArticle == null) {
                continue;
            }
            String rootArticleId = resolveArticleId(rootArticle);
            PropagationReport propagationReport = propagationService.analyzeImpact(rootArticleId, "incremental compile");
            List<String> sourceRefs = collectSourceRefs(entry.getValue());
            if (sourceRefs.isEmpty()) {
                continue;
            }
            for (PropagationItem propagationItem : propagationReport.getItems()) {
                String targetArticleId = propagationItem.getConceptId();
                if (targetArticleId == null || targetArticleId.isBlank()) {
                    continue;
                }
                if (!shouldPropagateIncrementalChange(propagationItem)) {
                    continue;
                }
                String emittedPlanKey = normalizeConceptId(entry.getKey()) + "->" + normalizeConceptId(targetArticleId);
                if (!emittedPlanKeys.add(emittedPlanKey)) {
                    continue;
                }
                propagationPlans.add(new EnhancementPlan(
                        targetArticleId,
                        buildEnhancementSummary(entry.getValue()),
                        sourceRefs
                ));
            }
        }
        return propagationPlans;
    }
    /**
     * 判断当前传播影响项是否值得纳入增量编译传播。
     *
     * @param propagationItem 传播影响项
     * @return 仅当存在事实依赖边时返回 true
     */
    protected boolean shouldPropagateIncrementalChange(PropagationItem propagationItem) {
        if (propagationItem == null || propagationItem.getTriggers() == null) {
            return false;
        }
        for (String trigger : propagationItem.getTriggers()) {
            String normalizedTrigger = normalizeConceptId(trigger);
            if ("depends_on".equals(normalizedTrigger) || "wiki_link".equals(normalizedTrigger)) {
                return true;
            }
        }
        return false;
    }
    /**
     * 构建新建文章计划。
     *
     * @param mergedConcepts 增量概念
     * @param existingArticles 已有文章
     * @return 新建文章计划列表
     */
    protected List<NewArticlePlan> buildNewArticlePlans(List<MergedConcept> mergedConcepts, List<ArticleRecord> existingArticles) {
        Set<String> existingArticleIds = new LinkedHashSet<String>();
        for (ArticleRecord existingArticle : existingArticles) {
            existingArticleIds.add(normalizeConceptId(existingArticle.getConceptId()));
        }
        Set<String> plannedConceptIds = new LinkedHashSet<String>();
        List<NewArticlePlan> newArticles = new ArrayList<NewArticlePlan>();
        for (MergedConcept mergedConcept : mergedConcepts) {
            String normalizedConceptId = normalizeConceptId(mergedConcept.getConceptId());
            if (existingArticleIds.contains(normalizedConceptId) || !plannedConceptIds.add(normalizedConceptId)) {
                continue;
            }
            newArticles.add(new NewArticlePlan(
                    mergedConcept.getConceptId(),
                    mergedConcept.getTitle(),
                    mergedConcept.getDescription(),
                    mergedConcept.getSourcePaths()
            ));
        }
        return newArticles;
    }
    /**
     * 构建单条增强计划。
     *
     * @param targetArticleId 目标文章标识
     * @param matchedConcepts 命中的增量概念
     * @return 增强计划
     */
    protected EnhancementPlan buildEnhancementPlan(String targetArticleId, List<MergedConcept> matchedConcepts) {
        return new EnhancementPlan(
                targetArticleId,
                buildEnhancementSummary(matchedConcepts),
                collectSourceRefs(matchedConcepts)
        );
    }
    /**
     * 构建增强摘要。
     *
     * @param matchedConcepts 命中的增量概念
     * @return 摘要文本
     */
    protected String buildEnhancementSummary(List<MergedConcept> matchedConcepts) {
        List<String> summaries = new ArrayList<String>();
        for (MergedConcept matchedConcept : matchedConcepts) {
            if (matchedConcept.getDescription() == null || matchedConcept.getDescription().isBlank()) {
                continue;
            }
            summaries.add(matchedConcept.getDescription().trim());
        }
        return String.join("；", summaries);
    }
    /**
     * 收集增量概念对应的来源路径。
     *
     * @param matchedConcepts 命中的增量概念
     * @return 去重后的来源路径
     */
    protected List<String> collectSourceRefs(List<MergedConcept> matchedConcepts) {
        LinkedHashSet<String> sourceRefs = new LinkedHashSet<String>();
        for (MergedConcept matchedConcept : matchedConcepts) {
            sourceRefs.addAll(normalizeTextList(matchedConcept.getSourcePaths()));
        }
        return new ArrayList<String>(sourceRefs);
    }
    /**
     * 解析增强计划对应的概念集合。
     *
     * @param enhancements 增强计划
     * @param mergedConcepts 增量概念
     * @param existingArticles 已有文章
     * @return 按目标文章分组后的概念集合
     */
    protected Map<String, List<MergedConcept>> resolveEnhancementConcepts(
            List<EnhancementPlan> enhancements,
            List<MergedConcept> mergedConcepts,
            List<ArticleRecord> existingArticles
    ) {
        Set<String> existingArticleIds = new LinkedHashSet<String>();
        for (ArticleRecord existingArticle : existingArticles) {
            existingArticleIds.add(normalizeConceptId(existingArticle.getConceptId()));
        }

        Map<String, List<MergedConcept>> result = new LinkedHashMap<String, List<MergedConcept>>();
        for (EnhancementPlan enhancementPlan : enhancements) {
            if (!existingArticleIds.contains(normalizeConceptId(enhancementPlan.getTargetArticleId()))) {
                continue;
            }
            List<MergedConcept> matchedConcepts = matchConceptsForEnhancement(enhancementPlan, mergedConcepts);
            if (matchedConcepts.isEmpty()) {
                continue;
            }
            for (MergedConcept matchedConcept : matchedConcepts) {
                addMatchedConcept(result, enhancementPlan.getTargetArticleId(), matchedConcept);
            }
        }
        return result;
    }
    /**
     * 为增强计划匹配具体概念。
     *
     * @param enhancementPlan 增强计划
     * @param mergedConcepts 增量概念
     * @return 命中的概念
     */
    protected List<MergedConcept> matchConceptsForEnhancement(
            EnhancementPlan enhancementPlan,
            List<MergedConcept> mergedConcepts
    ) {
        List<MergedConcept> matchedConcepts = new ArrayList<MergedConcept>();
        Set<String> sourceRefs = new LinkedHashSet<String>(normalizeTextList(enhancementPlan.getSourceRefs()));
        for (MergedConcept mergedConcept : mergedConcepts) {
            boolean matchedBySource = false;
            for (String sourcePath : mergedConcept.getSourcePaths()) {
                if (sourceRefs.contains(sourcePath)) {
                    matchedBySource = true;
                    break;
                }
            }
            if (matchedBySource) {
                matchedConcepts.add(mergedConcept);
            }
        }
        if (!matchedConcepts.isEmpty()) {
            return matchedConcepts;
        }
        for (MergedConcept mergedConcept : mergedConcepts) {
            if (normalizeConceptId(mergedConcept.getConceptId()).equals(normalizeConceptId(enhancementPlan.getTargetArticleId()))) {
                matchedConcepts.add(mergedConcept);
            }
        }
        return matchedConcepts;
    }
    /**
     * 解析需要新建文章的概念集合。
     *
     * @param newArticles 新建计划
     * @param mergedConcepts 增量概念
     * @return 待新建概念
     */
    protected List<MergedConcept> resolveConceptsToCreate(List<NewArticlePlan> newArticles, List<MergedConcept> mergedConcepts) {
        List<MergedConcept> conceptsToCreate = new ArrayList<MergedConcept>();
        Set<String> conceptIdsToCreate = new LinkedHashSet<String>();
        for (NewArticlePlan newArticle : newArticles) {
            MergedConcept matchedConcept = findMergedConceptById(mergedConcepts, newArticle.getId());
            if (matchedConcept == null) {
                continue;
            }
            if (conceptIdsToCreate.add(normalizeConceptId(matchedConcept.getConceptId()))) {
                conceptsToCreate.add(matchedConcept);
            }
        }
        return conceptsToCreate;
    }
    /**
     * 向命中映射中追加概念，并按概念标识去重。
     *
     * @param matchedConcepts 目标映射
     * @param targetArticleId 目标文章标识
     * @param mergedConcept 增量概念
     */
    protected void addMatchedConcept(
            Map<String, List<MergedConcept>> matchedConcepts,
            String targetArticleId,
            MergedConcept mergedConcept
    ) {
        List<MergedConcept> concepts = matchedConcepts.computeIfAbsent(
                targetArticleId,
                key -> new ArrayList<MergedConcept>()
        );
        for (MergedConcept existingConcept : concepts) {
            if (normalizeConceptId(existingConcept.getConceptId()).equals(normalizeConceptId(mergedConcept.getConceptId()))) {
                return;
            }
        }
        concepts.add(mergedConcept);
    }
    /**
     * 建立已有文章索引。
     *
     * @param existingArticles 已有文章
     * @return 按概念标识归一化后的文章映射
     */
    protected Map<String, ArticleRecord> indexExistingArticles(List<ArticleRecord> existingArticles) {
        Map<String, ArticleRecord> existingArticleMap = new LinkedHashMap<String, ArticleRecord>();
        for (ArticleRecord existingArticle : existingArticles) {
            existingArticleMap.put(normalizeConceptId(existingArticle.getConceptId()), existingArticle);
        }
        return existingArticleMap;
    }
    /**
     * 解析传播分析使用的根文章标识。
     *
     * @param articleRecord 根文章
     * @return articleKey 或 conceptId
     */
    protected String resolveArticleId(ArticleRecord articleRecord) {
        if (articleRecord.getArticleKey() != null && !articleRecord.getArticleKey().isBlank()) {
            return articleRecord.getArticleKey();
        }
        return articleRecord.getConceptId();
    }
    /**
     * 按概念标识查找增量概念。
     *
     * @param mergedConcepts 概念集合
     * @param conceptId 概念标识
     * @return 概念；不存在时返回 null
     */
    protected MergedConcept findMergedConceptById(List<MergedConcept> mergedConcepts, String conceptId) {
        String normalizedConceptId = normalizeConceptId(conceptId);
        for (MergedConcept mergedConcept : mergedConcepts) {
            if (normalizeConceptId(mergedConcept.getConceptId()).equals(normalizedConceptId)) {
                return mergedConcept;
            }
        }
        return null;
    }
}
