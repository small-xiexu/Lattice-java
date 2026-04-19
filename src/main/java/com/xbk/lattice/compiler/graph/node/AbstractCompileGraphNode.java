package com.xbk.lattice.compiler.graph.node;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.xbk.lattice.compiler.graph.ArticleReviewEnvelope;
import com.xbk.lattice.compiler.graph.CompileGraphState;
import com.xbk.lattice.compiler.graph.CompileGraphStateMapper;
import com.xbk.lattice.compiler.graph.CompileWorkingSetStore;
import com.xbk.lattice.compiler.graph.ReviewPartition;
import com.xbk.lattice.compiler.domain.MergedConcept;
import com.xbk.lattice.infra.persistence.ArticleRecord;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 编译图节点抽象基类
 *
 * 职责：统一封装图状态映射与工作集读写辅助逻辑
 *
 * @author xiexu
 */
abstract class AbstractCompileGraphNode {

    protected final CompileGraphStateMapper compileGraphStateMapper;

    protected final CompileWorkingSetStore compileWorkingSetStore;

    /**
     * 创建仅依赖状态映射的节点基类。
     *
     * @param compileGraphStateMapper 编译图状态映射器
     */
    protected AbstractCompileGraphNode(CompileGraphStateMapper compileGraphStateMapper) {
        this(compileGraphStateMapper, null);
    }

    /**
     * 创建带工作集访问能力的节点基类。
     *
     * @param compileGraphStateMapper 编译图状态映射器
     * @param compileWorkingSetStore 编译工作集存储
     */
    protected AbstractCompileGraphNode(
            CompileGraphStateMapper compileGraphStateMapper,
            CompileWorkingSetStore compileWorkingSetStore
    ) {
        this.compileGraphStateMapper = compileGraphStateMapper;
        this.compileWorkingSetStore = compileWorkingSetStore;
    }

    /**
     * 从图状态构建强类型状态。
     *
     * @param overAllState 图状态
     * @return 强类型状态
     */
    protected CompileGraphState state(OverAllState overAllState) {
        return compileGraphStateMapper.fromMap(overAllState.data());
    }

    /**
     * 把强类型状态回写为图状态增量。
     *
     * @param state 强类型状态
     * @return 图状态增量
     */
    protected Map<String, Object> delta(CompileGraphState state) {
        return compileGraphStateMapper.toDeltaMap(state);
    }

    /**
     * 读取草稿文章集合。
     *
     * @param ref 工作集引用
     * @return 草稿文章集合
     */
    protected List<ArticleRecord> loadDraftArticles(String ref) {
        if (ref == null) {
            return new ArrayList<ArticleRecord>();
        }
        return workingSetStore().loadDraftArticles(ref);
    }

    /**
     * 读取审查后文章集合。
     *
     * @param ref 工作集引用
     * @return 审查后文章集合
     */
    protected List<ArticleReviewEnvelope> loadReviewedArticles(String ref) {
        if (ref == null) {
            return new ArrayList<ArticleReviewEnvelope>();
        }
        return workingSetStore().loadReviewedArticles(ref);
    }

    /**
     * 读取审查分区结果。
     *
     * @param ref 工作集引用
     * @return 审查分区结果
     */
    protected ReviewPartition loadReviewPartition(String ref) {
        if (ref == null) {
            return new ReviewPartition();
        }
        return workingSetStore().loadReviewPartition(ref);
    }

    /**
     * 读取已接受文章集合。
     *
     * @param ref 工作集引用
     * @return 已接受文章集合
     */
    protected List<ArticleReviewEnvelope> loadAcceptedArticles(String ref) {
        if (ref == null) {
            return new ArrayList<ArticleReviewEnvelope>();
        }
        return workingSetStore().loadAcceptedArticles(ref);
    }

    /**
     * 读取待人工复核文章集合。
     *
     * @param ref 工作集引用
     * @return 待人工复核文章集合
     */
    protected List<ArticleReviewEnvelope> loadNeedsHumanReviewArticles(String ref) {
        if (ref == null) {
            return new ArrayList<ArticleReviewEnvelope>();
        }
        return workingSetStore().loadNeedsHumanReviewArticles(ref);
    }

    /**
     * 保存已接受文章集合。
     *
     * @param jobId 作业标识
     * @param acceptedArticles 已接受文章集合
     * @return 工作集引用
     */
    protected String saveAcceptedArticles(String jobId, List<ArticleReviewEnvelope> acceptedArticles) {
        if (acceptedArticles.isEmpty()) {
            return null;
        }
        return workingSetStore().saveAcceptedArticles(jobId, acceptedArticles);
    }

    /**
     * 保存待人工复核文章集合。
     *
     * @param jobId 作业标识
     * @param needsHumanReviewArticles 待人工复核文章集合
     * @return 工作集引用
     */
    protected String saveNeedsHumanReviewArticles(
            String jobId,
            List<ArticleReviewEnvelope> needsHumanReviewArticles
    ) {
        if (needsHumanReviewArticles.isEmpty()) {
            return null;
        }
        return workingSetStore().saveNeedsHumanReviewArticles(jobId, needsHumanReviewArticles);
    }

    /**
     * 合并审查尝试元信息。
     *
     * @param baseReviewedArticles 基线审查结果
     * @param reviewedArticles 本轮审查结果
     */
    protected void mergeAttemptMetadata(
            List<ArticleReviewEnvelope> baseReviewedArticles,
            List<ArticleReviewEnvelope> reviewedArticles
    ) {
        if (baseReviewedArticles.isEmpty()) {
            return;
        }
        Map<String, ArticleReviewEnvelope> baseArticleMap = new LinkedHashMap<String, ArticleReviewEnvelope>();
        for (ArticleReviewEnvelope baseReviewedArticle : baseReviewedArticles) {
            baseArticleMap.put(baseReviewedArticle.getArticle().getConceptId(), baseReviewedArticle);
        }
        for (ArticleReviewEnvelope reviewedArticle : reviewedArticles) {
            ArticleReviewEnvelope baseReviewedArticle = baseArticleMap.get(reviewedArticle.getArticle().getConceptId());
            if (baseReviewedArticle == null) {
                continue;
            }
            reviewedArticle.setReviewAttemptCount(baseReviewedArticle.getReviewAttemptCount() + 1);
            reviewedArticle.setFixAttemptCount(baseReviewedArticle.getFixAttemptCount());
            reviewedArticle.setFixed(baseReviewedArticle.isFixed());
        }
    }

    /**
     * 合并审查包裹集合。
     *
     * @param baseReviewedArticles 基线集合
     * @param currentReviewedArticles 当前集合
     * @return 合并后的集合
     */
    protected List<ArticleReviewEnvelope> mergeReviewEnvelopes(
            List<ArticleReviewEnvelope> baseReviewedArticles,
            List<ArticleReviewEnvelope> currentReviewedArticles
    ) {
        Map<String, ArticleReviewEnvelope> reviewedArticleMap = new LinkedHashMap<String, ArticleReviewEnvelope>();
        for (ArticleReviewEnvelope baseReviewedArticle : baseReviewedArticles) {
            reviewedArticleMap.put(baseReviewedArticle.getArticle().getConceptId(), baseReviewedArticle);
        }
        for (ArticleReviewEnvelope currentReviewedArticle : currentReviewedArticles) {
            reviewedArticleMap.put(currentReviewedArticle.getArticle().getConceptId(), currentReviewedArticle);
        }
        return new ArrayList<ArticleReviewEnvelope>(reviewedArticleMap.values());
    }

    /**
     * 提取最终落库文章标识。
     *
     * @param reviewedArticles 审查包裹集合
     * @return 文章标识集合
     */
    protected List<String> extractArticleIds(List<ArticleReviewEnvelope> reviewedArticles) {
        List<String> persistedArticleIds = new ArrayList<String>();
        for (ArticleReviewEnvelope reviewedArticle : reviewedArticles) {
            persistedArticleIds.add(reviewedArticle.getArticle().getConceptId());
        }
        return persistedArticleIds;
    }

    /**
     * 解析当前节点需要编译的概念集合。
     *
     * @param state 编译图状态
     * @return 待编译概念集合
     */
    protected List<MergedConcept> resolveConceptsToCompile(CompileGraphState state) {
        if ("incremental".equalsIgnoreCase(state.getCompileMode())) {
            if (state.getConceptsToCreateRef() == null) {
                return new ArrayList<MergedConcept>();
            }
            return workingSetStore().loadConceptsToCreate(state.getConceptsToCreateRef());
        }
        return workingSetStore().loadMergedConcepts(state.getMergedConceptsRef());
    }

    /**
     * 获取工作集存储。
     *
     * @return 工作集存储
     */
    protected CompileWorkingSetStore workingSetStore() {
        if (compileWorkingSetStore == null) {
            throw new IllegalStateException("compileWorkingSetStore is required for this node");
        }
        return compileWorkingSetStore;
    }
}
