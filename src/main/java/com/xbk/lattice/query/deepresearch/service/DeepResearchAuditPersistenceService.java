package com.xbk.lattice.query.deepresearch.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xbk.lattice.infra.persistence.DeepResearchAnswerProjectionJdbcRepository;
import com.xbk.lattice.infra.persistence.DeepResearchAnswerProjectionRecord;
import com.xbk.lattice.infra.persistence.DeepResearchEvidenceAnchorJdbcRepository;
import com.xbk.lattice.infra.persistence.DeepResearchEvidenceAnchorRecord;
import com.xbk.lattice.infra.persistence.DeepResearchEvidenceAnchorValidationJdbcRepository;
import com.xbk.lattice.infra.persistence.DeepResearchEvidenceAnchorValidationRecord;
import com.xbk.lattice.infra.persistence.DeepResearchFindingJdbcRepository;
import com.xbk.lattice.infra.persistence.DeepResearchFindingRecord;
import com.xbk.lattice.infra.persistence.DeepResearchRunJdbcRepository;
import com.xbk.lattice.infra.persistence.DeepResearchRunRecord;
import com.xbk.lattice.infra.persistence.DeepResearchTaskHitJdbcRepository;
import com.xbk.lattice.infra.persistence.DeepResearchTaskHitRecord;
import com.xbk.lattice.infra.persistence.DeepResearchTaskJdbcRepository;
import com.xbk.lattice.infra.persistence.DeepResearchTaskRecord;
import com.xbk.lattice.query.citation.CitationCheckReport;
import com.xbk.lattice.query.citation.QueryAnswerAuditPersistenceService;
import com.xbk.lattice.query.citation.QueryAnswerAuditSnapshot;
import com.xbk.lattice.query.deepresearch.domain.DeepResearchAuditSnapshot;
import com.xbk.lattice.query.deepresearch.domain.EvidenceCard;
import com.xbk.lattice.query.deepresearch.domain.EvidenceLedger;
import com.xbk.lattice.query.deepresearch.domain.LayeredResearchPlan;
import com.xbk.lattice.query.deepresearch.domain.ResearchLayer;
import com.xbk.lattice.query.deepresearch.domain.ResearchTask;
import com.xbk.lattice.query.deepresearch.domain.ResearchTaskHit;
import com.xbk.lattice.query.domain.AnswerOutcome;
import com.xbk.lattice.query.domain.GenerationMode;
import com.xbk.lattice.query.evidence.domain.AnswerProjection;
import com.xbk.lattice.query.evidence.domain.AnswerProjectionBundle;
import com.xbk.lattice.query.evidence.domain.EvidenceAnchor;
import com.xbk.lattice.query.evidence.domain.EvidenceAnchorValidationStatus;
import com.xbk.lattice.query.evidence.domain.FactFinding;
import com.xbk.lattice.query.evidence.domain.ProjectionCitationFormat;
import com.xbk.lattice.query.evidence.domain.ProjectionStatus;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Deep Research 审计持久化服务
 *
 * 职责：按 v2.6 顺序把 run、任务、证据与最终答案审计一次性写入 Deep Research 审计表
 *
 * @author xiexu
 */
@Service
@Profile("jdbc")
public class DeepResearchAuditPersistenceService {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().findAndRegisterModules();

    private final DeepResearchRunJdbcRepository deepResearchRunJdbcRepository;

    private final DeepResearchTaskJdbcRepository deepResearchTaskJdbcRepository;

    private final DeepResearchTaskHitJdbcRepository deepResearchTaskHitJdbcRepository;

    private final DeepResearchFindingJdbcRepository deepResearchFindingJdbcRepository;

    private final DeepResearchEvidenceAnchorJdbcRepository deepResearchEvidenceAnchorJdbcRepository;

    private final DeepResearchEvidenceAnchorValidationJdbcRepository deepResearchEvidenceAnchorValidationJdbcRepository;

    private final DeepResearchAnswerProjectionJdbcRepository deepResearchAnswerProjectionJdbcRepository;

    private final QueryAnswerAuditPersistenceService queryAnswerAuditPersistenceService;

    /**
     * 创建 Deep Research 审计持久化服务。
     *
     * @param deepResearchRunJdbcRepository 运行主表仓储
     * @param deepResearchTaskJdbcRepository 任务仓储
     * @param deepResearchTaskHitJdbcRepository 任务命中仓储
     * @param deepResearchFindingJdbcRepository finding 仓储
     * @param deepResearchEvidenceAnchorJdbcRepository 锚点仓储
     * @param deepResearchEvidenceAnchorValidationJdbcRepository 锚点校验仓储
     * @param deepResearchAnswerProjectionJdbcRepository 答案投影仓储
     * @param queryAnswerAuditPersistenceService Query 答案审计仓储服务
     */
    public DeepResearchAuditPersistenceService(
            DeepResearchRunJdbcRepository deepResearchRunJdbcRepository,
            DeepResearchTaskJdbcRepository deepResearchTaskJdbcRepository,
            DeepResearchTaskHitJdbcRepository deepResearchTaskHitJdbcRepository,
            DeepResearchFindingJdbcRepository deepResearchFindingJdbcRepository,
            DeepResearchEvidenceAnchorJdbcRepository deepResearchEvidenceAnchorJdbcRepository,
            DeepResearchEvidenceAnchorValidationJdbcRepository deepResearchEvidenceAnchorValidationJdbcRepository,
            DeepResearchAnswerProjectionJdbcRepository deepResearchAnswerProjectionJdbcRepository,
            QueryAnswerAuditPersistenceService queryAnswerAuditPersistenceService
    ) {
        this.deepResearchRunJdbcRepository = deepResearchRunJdbcRepository;
        this.deepResearchTaskJdbcRepository = deepResearchTaskJdbcRepository;
        this.deepResearchTaskHitJdbcRepository = deepResearchTaskHitJdbcRepository;
        this.deepResearchFindingJdbcRepository = deepResearchFindingJdbcRepository;
        this.deepResearchEvidenceAnchorJdbcRepository = deepResearchEvidenceAnchorJdbcRepository;
        this.deepResearchEvidenceAnchorValidationJdbcRepository = deepResearchEvidenceAnchorValidationJdbcRepository;
        this.deepResearchAnswerProjectionJdbcRepository = deepResearchAnswerProjectionJdbcRepository;
        this.queryAnswerAuditPersistenceService = queryAnswerAuditPersistenceService;
    }

    /**
     * 持久化一次 Deep Research 运行快照。
     *
     * @param queryId 查询标识
     * @param question 查询问题
     * @param routeReason 路由原因
     * @param plan 研究计划
     * @param evidenceLedger 证据账本
     * @param answerMarkdown 最终答案 Markdown
     * @param citationCheckReport Citation 核验报告
     * @param answerProjectionBundle 答案投影包
     * @param llmCallCount LLM 调用数
     * @param partialAnswer 是否部分答案
     * @param hasConflicts 是否存在冲突
     * @return 审计快照
     */
    @Transactional(rollbackFor = Exception.class)
    public DeepResearchAuditSnapshot persist(
            String queryId,
            String question,
            String routeReason,
            LayeredResearchPlan plan,
            EvidenceLedger evidenceLedger,
            String answerMarkdown,
            CitationCheckReport citationCheckReport,
            AnswerProjectionBundle answerProjectionBundle,
            int llmCallCount,
            boolean partialAnswer,
            boolean hasConflicts
    ) {
        List<EvidenceCard> evidenceCards = evidenceLedger == null ? List.of() : evidenceLedger.getCards();
        AnchorCanonicalization anchorCanonicalization = canonicalizeAnchors(evidenceCards, evidenceLedger);
        Long runId = deepResearchRunJdbcRepository.insert(new DeepResearchRunRecord(
                queryId,
                question,
                routeReason,
                writeJson(plan),
                plan == null ? 0 : plan.layerCount(),
                plan == null ? 0 : plan.taskCount(),
                llmCallCount,
                citationCheckReport == null ? 0.0D : citationCheckReport.getCoverageRate(),
                partialAnswer,
                hasConflicts,
                null
        ));
        persistTasks(runId, plan, evidenceCards);
        persistTaskHits(runId, evidenceCards);
        persistFindingsAndAnchors(runId, evidenceCards, anchorCanonicalization);
        QueryAnswerAuditSnapshot answerAuditSnapshot = queryAnswerAuditPersistenceService.persist(
                queryId,
                1,
                question,
                answerMarkdown,
                partialAnswer ? AnswerOutcome.PARTIAL_ANSWER : AnswerOutcome.SUCCESS,
                GenerationMode.LLM,
                null,
                false,
                "deep_research",
                citationCheckReport,
                runId
        );
        persistAnswerProjections(runId, answerAuditSnapshot, answerProjectionBundle, anchorCanonicalization);
        if (answerAuditSnapshot != null && answerAuditSnapshot.getAuditId() != null) {
            deepResearchRunJdbcRepository.bindFinalAnswerAudit(runId, answerAuditSnapshot.getAuditId());
        }
        return new DeepResearchAuditSnapshot(runId, evidenceCards.size());
    }

    /**
     * 持久化任务主表。
     *
     * @param runId run 主键
     * @param plan 研究计划
     * @param evidenceCards 证据卡列表
     */
    private void persistTasks(Long runId, LayeredResearchPlan plan, List<EvidenceCard> evidenceCards) {
        if (plan == null || plan.getLayers() == null || deepResearchTaskJdbcRepository == null) {
            return;
        }
        Map<String, EvidenceCard> cardsByTaskId = new LinkedHashMap<String, EvidenceCard>();
        for (EvidenceCard evidenceCard : evidenceCards) {
            if (evidenceCard != null && !isBlank(evidenceCard.getTaskId())) {
                cardsByTaskId.put(evidenceCard.getTaskId(), evidenceCard);
            }
        }
        for (ResearchLayer researchLayer : plan.getLayers()) {
            if (researchLayer == null || researchLayer.getTasks() == null) {
                continue;
            }
            for (ResearchTask researchTask : researchLayer.getTasks()) {
                if (researchTask == null || isBlank(researchTask.getTaskId())) {
                    continue;
                }
                EvidenceCard evidenceCard = cardsByTaskId.get(researchTask.getTaskId());
                deepResearchTaskJdbcRepository.insert(new DeepResearchTaskRecord(
                        runId,
                        researchTask.getTaskId(),
                        researchLayer.getLayerIndex(),
                        resolveTaskType(researchTask),
                        normalize(researchTask.getQuestion()),
                        resolveExpectedFactSchemaJson(researchTask),
                        writeJson(researchTask.getPreferredUpstreamTaskIds()),
                        resolveTaskStatus(evidenceCard),
                        0,
                        isTimedOut(evidenceCard),
                        resolveTaskErrorReason(evidenceCard),
                        null,
                        null
                ));
            }
        }
    }

    /**
     * 持久化每个任务的检索命中。
     *
     * @param runId run 主键
     * @param evidenceCards 证据卡列表
     */
    private void persistTaskHits(Long runId, List<EvidenceCard> evidenceCards) {
        if (deepResearchTaskHitJdbcRepository == null || evidenceCards == null || evidenceCards.isEmpty()) {
            return;
        }
        for (EvidenceCard evidenceCard : evidenceCards) {
            if (evidenceCard == null || evidenceCard.getTaskHits() == null || evidenceCard.getTaskHits().isEmpty()) {
                continue;
            }
            for (ResearchTaskHit taskHit : evidenceCard.getTaskHits()) {
                if (taskHit == null) {
                    continue;
                }
                deepResearchTaskHitJdbcRepository.insert(new DeepResearchTaskHitRecord(
                        runId,
                        evidenceCard.getTaskId(),
                        taskHit.getHitOrdinal(),
                        taskHit.getChannel(),
                        taskHit.getEvidenceType(),
                        taskHit.getSourceId(),
                        taskHit.getArticleKey(),
                        taskHit.getConceptId(),
                        taskHit.getTitle(),
                        taskHit.getChunkId(),
                        taskHit.getPath(),
                        taskHit.getOriginalScore(),
                        taskHit.getRrfScore(),
                        taskHit.getFusedScore(),
                        normalize(taskHit.getContentExcerpt())
                ));
            }
        }
    }

    /**
     * 持久化 findings、anchors 与 anchor validations。
     *
     * @param runId run 主键
     * @param evidenceCards 证据卡列表
     * @param evidenceLedger 证据账本
     */
    private void persistFindingsAndAnchors(
            Long runId,
            List<EvidenceCard> evidenceCards,
            AnchorCanonicalization anchorCanonicalization
    ) {
        persistFindings(runId, evidenceCards, anchorCanonicalization);
        persistAnchors(runId, anchorCanonicalization);
    }

    /**
     * 持久化 finding 主表。
     *
     * @param runId run 主键
     * @param evidenceCards 证据卡列表
     */
    private void persistFindings(
            Long runId,
            List<EvidenceCard> evidenceCards,
            AnchorCanonicalization anchorCanonicalization
    ) {
        if (deepResearchFindingJdbcRepository == null) {
            return;
        }
        for (EvidenceCard evidenceCard : evidenceCards) {
            if (evidenceCard == null) {
                continue;
            }
            for (FactFinding factFinding : deduplicateFactFindings(resolveFactFindings(evidenceCard))) {
                List<String> canonicalAnchorIds = canonicalizeAnchorIds(
                        factFinding.getAnchorIds(),
                        anchorCanonicalization
                );
                deepResearchFindingJdbcRepository.insert(new DeepResearchFindingRecord(
                        runId,
                        factFinding.getFindingId(),
                        evidenceCard.getTaskId(),
                        factFinding.getFactKey(),
                        factFinding.getSubject(),
                        factFinding.getPredicate(),
                        factFinding.getValueText(),
                        factFinding.getValueType() == null ? null : factFinding.getValueType().name(),
                        factFinding.getUnit(),
                        factFinding.getQualifier(),
                        factFinding.getClaimText(),
                        factFinding.getSupportLevel() == null ? null : factFinding.getSupportLevel().name(),
                        factFinding.getConfidence(),
                        writeJson(canonicalAnchorIds)
                ));
            }
        }
    }

    /**
     * 对同一 task 内重复 finding 做 mergeIdentity 级合并，避免落库撞唯一约束。
     *
     * @param factFindings 原始 finding 列表
     * @return 去重后的 finding 列表
     */
    private List<FactFinding> deduplicateFactFindings(List<FactFinding> factFindings) {
        if (factFindings == null || factFindings.isEmpty()) {
            return List.of();
        }
        Map<String, FactFinding> findingsByMergeIdentity = new LinkedHashMap<String, FactFinding>();
        for (FactFinding factFinding : factFindings) {
            if (factFinding == null) {
                continue;
            }
            String mergeIdentity = factFinding.mergeIdentity();
            FactFinding existingFinding = findingsByMergeIdentity.get(mergeIdentity);
            if (existingFinding == null) {
                findingsByMergeIdentity.put(mergeIdentity, copyFactFinding(factFinding));
                continue;
            }
            mergeAnchorIds(existingFinding, factFinding);
            if (factFinding.getConfidence() > existingFinding.getConfidence()) {
                existingFinding.setConfidence(factFinding.getConfidence());
            }
        }
        return List.copyOf(findingsByMergeIdentity.values());
    }

    /**
     * 复制 finding，避免修改原始 EvidenceCard 上的运行时对象。
     *
     * @param factFinding 原始 finding
     * @return 可安全修改的副本
     */
    private FactFinding copyFactFinding(FactFinding factFinding) {
        FactFinding copiedFinding = new FactFinding();
        copiedFinding.setFindingId(factFinding.getFindingId());
        copiedFinding.setFactKey(factFinding.getFactKey());
        copiedFinding.setSubject(factFinding.getSubject());
        copiedFinding.setPredicate(factFinding.getPredicate());
        copiedFinding.setValueText(factFinding.getValueText());
        copiedFinding.setValueType(factFinding.getValueType());
        copiedFinding.setUnit(factFinding.getUnit());
        copiedFinding.setQualifier(factFinding.getQualifier());
        copiedFinding.setClaimText(factFinding.getClaimText());
        copiedFinding.setConfidence(factFinding.getConfidence());
        copiedFinding.setSupportLevel(factFinding.getSupportLevel());
        copiedFinding.setAnchorIds(factFinding.getAnchorIds() == null
                ? List.of()
                : List.copyOf(factFinding.getAnchorIds()));
        return copiedFinding;
    }

    /**
     * 合并重复 finding 的 anchorIds。
     *
     * @param target 目标 finding
     * @param source 源 finding
     */
    private void mergeAnchorIds(FactFinding target, FactFinding source) {
        Map<String, String> mergedAnchorIds = new LinkedHashMap<String, String>();
        if (target.getAnchorIds() != null) {
            for (String anchorId : target.getAnchorIds()) {
                if (!isBlank(anchorId)) {
                    mergedAnchorIds.put(anchorId, anchorId);
                }
            }
        }
        if (source.getAnchorIds() != null) {
            for (String anchorId : source.getAnchorIds()) {
                if (!isBlank(anchorId)) {
                    mergedAnchorIds.put(anchorId, anchorId);
                }
            }
        }
        target.setAnchorIds(List.copyOf(mergedAnchorIds.values()));
    }

    /**
     * 持久化锚点主表与初始校验轨迹。
     *
     * @param runId run 主键
     * @param evidenceCards 证据卡列表
     * @param evidenceLedger 证据账本
     */
    private void persistAnchors(Long runId, AnchorCanonicalization anchorCanonicalization) {
        if (anchorCanonicalization == null || anchorCanonicalization.getCanonicalAnchorsById().isEmpty()) {
            return;
        }
        for (EvidenceAnchor evidenceAnchor : anchorCanonicalization.getCanonicalAnchorsById().values()) {
            String taskId = anchorCanonicalization.getTaskIdByCanonicalAnchorId().get(evidenceAnchor.getAnchorId());
            if (isBlank(taskId)) {
                continue;
            }
            if (deepResearchEvidenceAnchorJdbcRepository != null) {
                deepResearchEvidenceAnchorJdbcRepository.insert(new DeepResearchEvidenceAnchorRecord(
                        runId,
                        evidenceAnchor.getAnchorId(),
                        taskId,
                        evidenceAnchor.getSourceType() == null ? null : evidenceAnchor.getSourceType().name(),
                        evidenceAnchor.getSourceId(),
                        evidenceAnchor.getChunkId(),
                        evidenceAnchor.getPath(),
                        evidenceAnchor.getLineStart(),
                        evidenceAnchor.getLineEnd(),
                        normalize(evidenceAnchor.getQuoteText()),
                        Double.valueOf(evidenceAnchor.getRetrievalScore()),
                        evidenceAnchor.getContentHash()
                ));
            }
            if (deepResearchEvidenceAnchorValidationJdbcRepository != null) {
                deepResearchEvidenceAnchorValidationJdbcRepository.insert(new DeepResearchEvidenceAnchorValidationRecord(
                        runId,
                        evidenceAnchor.getAnchorId(),
                        0,
                        resolveAnchorValidationStatus(evidenceAnchor),
                        "STRUCTURE_RULE",
                        "initial_snapshot",
                        normalize(evidenceAnchor.getQuoteText())
                ));
            }
        }
    }

    /**
     * 持久化最终答案 projection 白名单。
     *
     * @param runId run 主键
     * @param answerAuditSnapshot 答案审计快照
     * @param answerProjectionBundle 答案投影包
     * @param evidenceLedger 证据账本
     */
    private void persistAnswerProjections(
            Long runId,
            QueryAnswerAuditSnapshot answerAuditSnapshot,
            AnswerProjectionBundle answerProjectionBundle,
            AnchorCanonicalization anchorCanonicalization
    ) {
        if (deepResearchAnswerProjectionJdbcRepository == null
                || answerAuditSnapshot == null
                || answerAuditSnapshot.getAuditId() == null
                || answerProjectionBundle == null
                || answerProjectionBundle.getProjections() == null
                || anchorCanonicalization == null
                || anchorCanonicalization.getCanonicalAnchorsById().isEmpty()) {
            return;
        }
        for (AnswerProjection answerProjection : answerProjectionBundle.getProjections()) {
            if (!isPersistableProjection(answerProjection)) {
                continue;
            }
            String canonicalAnchorId = anchorCanonicalization.getCanonicalAnchorIdByAnchorId()
                    .getOrDefault(answerProjection.getAnchorId(), answerProjection.getAnchorId());
            if (!anchorCanonicalization.getCanonicalAnchorsById().containsKey(canonicalAnchorId)) {
                continue;
            }
            deepResearchAnswerProjectionJdbcRepository.insert(new DeepResearchAnswerProjectionRecord(
                    runId,
                    answerAuditSnapshot.getAuditId(),
                    answerProjection.getProjectionOrdinal(),
                    canonicalAnchorId,
                    answerProjection.getCitationLiteral(),
                    answerProjection.getSourceType().name(),
                    answerProjection.getTargetKey(),
                    answerProjection.getStatus().name(),
                    answerProjection.getRepairRound(),
                    answerProjection.getRepairedFromProjectionOrdinal()
            ));
        }
    }

    /**
     * 根据当前 task 生成占位事实槽位 JSON。
     *
     * @param researchTask 研究任务
     * @return 事实槽位 JSON
     */
    private String resolveExpectedFactSchemaJson(ResearchTask researchTask) {
        if (researchTask != null
                && researchTask.getExpectedFactSchema() != null
                && !researchTask.getExpectedFactSchema().isEmpty()) {
            return writeJson(researchTask.getExpectedFactSchema());
        }
        if (researchTask == null || isBlank(researchTask.getExpectedOutput())) {
            return "[]";
        }
        return writeJson(List.of(researchTask.getExpectedOutput()));
    }

    /**
     * 解析 task 类型。
     *
     * @param researchTask 研究任务
     * @return task 类型
     */
    private String resolveTaskType(ResearchTask researchTask) {
        if (researchTask != null && researchTask.getTaskType() != null) {
            return researchTask.getTaskType().name();
        }
        if (researchTask == null || isBlank(researchTask.getRetrievalFocus())) {
            return "RESEARCH";
        }
        return researchTask.getRetrievalFocus().trim().toUpperCase(Locale.ROOT);
    }

    /**
     * 解析任务状态。
     *
     * @param evidenceCard 证据卡
     * @return 任务状态
     */
    private String resolveTaskStatus(EvidenceCard evidenceCard) {
        if (evidenceCard == null) {
            return "FAILED";
        }
        if (hasFactFindings(evidenceCard)) {
            return "SUCCEEDED";
        }
        if (isTimedOut(evidenceCard) || !evidenceCard.getGaps().isEmpty() || !evidenceCard.getSelectedArticleKeys().isEmpty()) {
            return "PARTIAL";
        }
        return "FAILED";
    }

    /**
     * 解析任务失败原因。
     *
     * @param evidenceCard 证据卡
     * @return 失败原因
     */
    private String resolveTaskErrorReason(EvidenceCard evidenceCard) {
        if (evidenceCard == null || evidenceCard.getGaps() == null || evidenceCard.getGaps().isEmpty()) {
            return null;
        }
        return String.join(",", evidenceCard.getGaps());
    }

    /**
     * 判断任务是否超时。
     *
     * @param evidenceCard 证据卡
     * @return 是否超时
     */
    private boolean isTimedOut(EvidenceCard evidenceCard) {
        return evidenceCard != null && evidenceCard.getGaps() != null && evidenceCard.getGaps().contains("overall_timeout");
    }

    /**
     * 解析 anchor 当前校验状态。
     *
     * @param evidenceAnchor 锚点
     * @return 校验状态
     */
    private String resolveAnchorValidationStatus(EvidenceAnchor evidenceAnchor) {
        EvidenceAnchorValidationStatus validationStatus = evidenceAnchor == null ? null : evidenceAnchor.getValidationStatus();
        if (validationStatus == null) {
            return EvidenceAnchorValidationStatus.RAW.name();
        }
        return validationStatus.name();
    }

    /**
     * 建立 anchor 到 task 的映射。
     *
     * @param evidenceCards 证据卡列表
     * @return anchor 对应的 task 映射
     */
    private Map<String, String> buildTaskIdByAnchorId(List<EvidenceCard> evidenceCards) {
        Map<String, String> taskIdByAnchorId = new LinkedHashMap<String, String>();
        for (EvidenceCard evidenceCard : evidenceCards) {
            if (evidenceCard == null) {
                continue;
            }
            if (evidenceCard.getEvidenceAnchors() != null && !evidenceCard.getEvidenceAnchors().isEmpty()) {
                for (EvidenceAnchor evidenceAnchor : evidenceCard.getEvidenceAnchors()) {
                    if (evidenceAnchor != null && !isBlank(evidenceAnchor.getAnchorId())) {
                        taskIdByAnchorId.putIfAbsent(evidenceAnchor.getAnchorId(), evidenceCard.getTaskId());
                    }
                }
            }
        }
        return taskIdByAnchorId;
    }

    /**
     * 对同一 run 内重复 content hash 的 anchor 做 canonical 归一化。
     *
     * @param evidenceCards 证据卡列表
     * @param evidenceLedger 证据账本
     * @return canonical 归一化结果
     */
    private AnchorCanonicalization canonicalizeAnchors(List<EvidenceCard> evidenceCards, EvidenceLedger evidenceLedger) {
        AnchorCanonicalization anchorCanonicalization = new AnchorCanonicalization();
        if (evidenceLedger == null || evidenceLedger.getAnchorsById() == null || evidenceLedger.getAnchorsById().isEmpty()) {
            return anchorCanonicalization;
        }
        Map<String, String> rawTaskIdByAnchorId = buildTaskIdByAnchorId(evidenceCards);
        Map<String, String> canonicalAnchorIdByContentHash = new LinkedHashMap<String, String>();
        for (EvidenceAnchor evidenceAnchor : evidenceLedger.getAnchorsById().values()) {
            if (evidenceAnchor == null || isBlank(evidenceAnchor.getAnchorId())) {
                continue;
            }
            String canonicalAnchorId = canonicalAnchorIdByContentHash.computeIfAbsent(
                    normalize(evidenceAnchor.getContentHash()),
                    ignored -> evidenceAnchor.getAnchorId()
            );
            anchorCanonicalization.getCanonicalAnchorIdByAnchorId().put(evidenceAnchor.getAnchorId(), canonicalAnchorId);
            if (canonicalAnchorId.equals(evidenceAnchor.getAnchorId())) {
                anchorCanonicalization.getCanonicalAnchorsById().put(canonicalAnchorId, evidenceAnchor);
            }
            String taskId = rawTaskIdByAnchorId.get(evidenceAnchor.getAnchorId());
            if (!isBlank(taskId)) {
                anchorCanonicalization.getTaskIdByCanonicalAnchorId().putIfAbsent(canonicalAnchorId, taskId);
            }
        }
        return anchorCanonicalization;
    }

    /**
     * 把原始 anchorIds 映射成 canonical anchorIds。
     *
     * @param anchorIds 原始 anchorIds
     * @param anchorCanonicalization canonical 结果
     * @return 去重后的 canonical anchorIds
     */
    private List<String> canonicalizeAnchorIds(
            List<String> anchorIds,
            AnchorCanonicalization anchorCanonicalization
    ) {
        if (anchorIds == null || anchorIds.isEmpty() || anchorCanonicalization == null) {
            return List.of();
        }
        Map<String, String> canonicalAnchorIds = new LinkedHashMap<String, String>();
        for (String anchorId : anchorIds) {
            if (isBlank(anchorId)) {
                continue;
            }
            String canonicalAnchorId = anchorCanonicalization.getCanonicalAnchorIdByAnchorId()
                    .getOrDefault(anchorId, anchorId);
            canonicalAnchorIds.put(canonicalAnchorId, canonicalAnchorId);
        }
        return List.copyOf(canonicalAnchorIds.values());
    }

    /**
     * 解析证据卡中的结构化 finding。
     *
     * @param evidenceCard 证据卡
     * @return 结构化 finding 列表
     */
    private List<FactFinding> resolveFactFindings(EvidenceCard evidenceCard) {
        if (evidenceCard == null) {
            return List.of();
        }
        if (evidenceCard.getFactFindings() != null && !evidenceCard.getFactFindings().isEmpty()) {
            return evidenceCard.getFactFindings();
        }
        return List.of();
    }

    /**
     * 判断证据卡是否已有可持久化 finding。
     *
     * @param evidenceCard 证据卡
     * @return 是否存在 finding
     */
    private boolean hasFactFindings(EvidenceCard evidenceCard) {
        return !resolveFactFindings(evidenceCard).isEmpty();
    }

    /**
     * 判断 projection 是否允许写入最终白名单。
     *
     * @param answerProjection 答案投影
     * @return 是否允许写入
     */
    private boolean isPersistableProjection(AnswerProjection answerProjection) {
        if (answerProjection == null
                || answerProjection.getSourceType() == null
                || answerProjection.getStatus() == null
                || isBlank(answerProjection.getAnchorId())
                || isBlank(answerProjection.getCitationLiteral())
                || isBlank(answerProjection.getTargetKey())) {
            return false;
        }
        return answerProjection.getSourceType() == ProjectionCitationFormat.ARTICLE
                || answerProjection.getSourceType() == ProjectionCitationFormat.SOURCE_FILE;
    }

    private String writeJson(Object value) {
        try {
            return OBJECT_MAPPER.writeValueAsString(value);
        }
        catch (Exception exception) {
            return "{}";
        }
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    /**
     * Deep Research anchor canonical 归一化结果。
     */
    private static final class AnchorCanonicalization {

        private final Map<String, String> canonicalAnchorIdByAnchorId = new LinkedHashMap<String, String>();

        private final Map<String, EvidenceAnchor> canonicalAnchorsById = new LinkedHashMap<String, EvidenceAnchor>();

        private final Map<String, String> taskIdByCanonicalAnchorId = new LinkedHashMap<String, String>();

        private Map<String, String> getCanonicalAnchorIdByAnchorId() {
            return canonicalAnchorIdByAnchorId;
        }

        private Map<String, EvidenceAnchor> getCanonicalAnchorsById() {
            return canonicalAnchorsById;
        }

        private Map<String, String> getTaskIdByCanonicalAnchorId() {
            return taskIdByCanonicalAnchorId;
        }
    }
}
