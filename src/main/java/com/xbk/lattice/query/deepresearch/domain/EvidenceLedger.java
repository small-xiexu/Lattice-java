package com.xbk.lattice.query.deepresearch.domain;

import com.xbk.lattice.query.evidence.domain.EvidenceAnchor;
import com.xbk.lattice.query.evidence.domain.FactFinding;
import com.xbk.lattice.query.evidence.domain.FindingSupportLevel;
import com.xbk.lattice.query.evidence.domain.AnswerProjection;
import com.xbk.lattice.query.evidence.domain.ProjectionCitationFormat;
import com.xbk.lattice.query.evidence.domain.ProjectionCandidate;
import com.xbk.lattice.query.evidence.domain.ProjectionStatus;
import com.xbk.lattice.query.deepresearch.validator.DeepResearchAnchorValidator;
import lombok.Data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 证据账本
 *
 * 职责：维护 Deep Research 的事实索引、锚点索引与冲突探测结果
 *
 * @author xiexu
 */
@Data
public class EvidenceLedger {

    private static final DeepResearchAnchorValidator ANCHOR_VALIDATOR = new DeepResearchAnchorValidator();

    private static final double MIN_FINDING_CONFIDENCE = 0.55D;

    private static final double MIN_PROJECTABLE_ANCHOR_SCORE = 0.55D;

    private List<EvidenceCard> cards = new ArrayList<EvidenceCard>();

    private Map<String, EvidenceCard> cardsByTaskId = new LinkedHashMap<String, EvidenceCard>();

    private Map<String, List<FactFinding>> findingsByFactKey = new LinkedHashMap<String, List<FactFinding>>();

    private Map<String, EvidenceAnchor> anchorsById = new LinkedHashMap<String, EvidenceAnchor>();

    private List<ProjectionCandidate> projectionCandidates = new ArrayList<ProjectionCandidate>();

    private Map<String, List<String>> conflicts = new LinkedHashMap<String, List<String>>();

    private Map<String, List<String>> complements = new LinkedHashMap<String, List<String>>();

    private Map<String, Boolean> coverageState = new LinkedHashMap<String, Boolean>();

    /**
     * 追加证据卡。
     *
     * @param card 证据卡
     */
    public void addCard(EvidenceCard card) {
        if (card != null) {
            cards.add(card);
            if (!isBlank(card.getTaskId())) {
                cardsByTaskId.put(card.getTaskId(), card);
            }
            List<EvidenceAnchor> resolvedAnchors = resolveCardAnchors(card);
            List<FactFinding> resolvedFactFindings = resolveCardFactFindings(card);
            addAnchors(resolvedAnchors);
            addFactFindings(resolvedFactFindings);
            addProjectionCandidates(buildProjectionCandidates(resolvedFactFindings));
        }
    }

    /**
     * 追加证据卡列表。
     *
     * @param evidenceCards 证据卡列表
     */
    public void addCards(List<EvidenceCard> evidenceCards) {
        if (evidenceCards == null || evidenceCards.isEmpty()) {
            return;
        }
        for (EvidenceCard evidenceCard : evidenceCards) {
            addCard(evidenceCard);
        }
    }

    /**
     * 返回证据卡数量。
     *
     * @return 证据卡数量
     */
    public int cardCount() {
        return cards.size();
    }

    /**
     * 返回 factFinding 总数。
     *
     * @return factFinding 总数
     */
    public int findingCount() {
        int findingCount = 0;
        for (List<FactFinding> factFindings : findingsByFactKey.values()) {
            findingCount += factFindings.size();
        }
        return findingCount;
    }

    /**
     * 追加结构化 finding。
     *
     * @param factFinding 结构化 finding
     */
    public void addFactFinding(FactFinding factFinding) {
        if (factFinding == null) {
            return;
        }
        if (!factFinding.canEnterLedger()) {
            throw new IllegalArgumentException("finding 缺少冻结 factKey 或 anchorIds，禁止写入 ledger");
        }
        if (!passesQualityGate(factFinding)) {
            return;
        }
        String factKey = factFinding.getFactKey();
        List<FactFinding> factFindings = findingsByFactKey.computeIfAbsent(
                factKey,
                key -> new ArrayList<FactFinding>()
        );
        FactFinding existingFinding = findByMergeIdentity(factFindings, factFinding.mergeIdentity());
        if (existingFinding != null) {
            mergeAnchorIds(existingFinding, factFinding);
            if (factFinding.getConfidence() > existingFinding.getConfidence()) {
                existingFinding.setConfidence(factFinding.getConfidence());
            }
            return;
        }
        factFindings.add(factFinding);
        registerConflict(factKey, factFindings);
        registerComplements(factFinding);
    }

    /**
     * 追加结构化 finding 列表。
     *
     * @param factFindings finding 列表
     */
    public void addFactFindings(List<FactFinding> factFindings) {
        if (factFindings == null || factFindings.isEmpty()) {
            return;
        }
        for (FactFinding factFinding : factFindings) {
            addFactFinding(factFinding);
        }
    }

    /**
     * 注册证据锚点。
     *
     * @param evidenceAnchor 证据锚点
     */
    public void addAnchor(EvidenceAnchor evidenceAnchor) {
        if (evidenceAnchor == null || isBlank(evidenceAnchor.getAnchorId()) || !evidenceAnchor.hasReusableIdentity()) {
            return;
        }
        EvidenceAnchor normalizedAnchor = ANCHOR_VALIDATOR.validateAndNormalize(evidenceAnchor);
        anchorsById.putIfAbsent(normalizedAnchor.getAnchorId(), normalizedAnchor);
    }

    /**
     * 注册证据锚点列表。
     *
     * @param evidenceAnchors 证据锚点列表
     */
    public void addAnchors(List<EvidenceAnchor> evidenceAnchors) {
        if (evidenceAnchors == null || evidenceAnchors.isEmpty()) {
            return;
        }
        for (EvidenceAnchor evidenceAnchor : evidenceAnchors) {
            addAnchor(evidenceAnchor);
        }
    }

    /**
     * 注册投影候选。
     *
     * @param projectionCandidate 投影候选
     */
    public void addProjectionCandidate(ProjectionCandidate projectionCandidate) {
        if (projectionCandidate != null
                && projectionCandidate.isVerified()
                && !hasProjectionCandidate(projectionCandidate)) {
            projectionCandidates.add(projectionCandidate);
        }
    }

    /**
     * 注册投影候选列表。
     *
     * @param candidates 投影候选列表
     */
    public void addProjectionCandidates(List<ProjectionCandidate> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return;
        }
        for (ProjectionCandidate projectionCandidate : candidates) {
            addProjectionCandidate(projectionCandidate);
        }
    }

    /**
     * 标记事实槽位是否已被最终投影覆盖。
     *
     * @param factKey 事实键
     * @param covered 是否已覆盖
     */
    public void markCoverage(String factKey, boolean covered) {
        if (!isBlank(factKey)) {
            coverageState.put(factKey, Boolean.valueOf(covered));
        }
    }

    /**
     * 注册最终答案必须覆盖的事实槽位。
     *
     * @param factKeys 必须覆盖的事实键
     */
    public void registerMustResolveFactKeys(List<String> factKeys) {
        if (factKeys == null || factKeys.isEmpty()) {
            return;
        }
        for (String factKey : factKeys) {
            if (!isBlank(factKey)) {
                coverageState.putIfAbsent(factKey.trim(), Boolean.FALSE);
            }
        }
    }

    /**
     * 基于 ACTIVE projection 刷新 mustResolve 覆盖状态。
     *
     * @param answerProjections 答案投影列表
     */
    public void refreshCoverageState(List<AnswerProjection> answerProjections) {
        if (coverageState.isEmpty()) {
            return;
        }
        for (String factKey : new ArrayList<String>(coverageState.keySet())) {
            coverageState.put(factKey, Boolean.FALSE);
        }
        if (answerProjections == null || answerProjections.isEmpty()) {
            return;
        }
        for (AnswerProjection answerProjection : answerProjections) {
            if (!isActiveProjection(answerProjection)) {
                continue;
            }
            List<String> factKeys = findFactKeysByAnchorId(answerProjection.getAnchorId());
            for (String factKey : factKeys) {
                if (coverageState.containsKey(factKey)) {
                    coverageState.put(factKey, Boolean.TRUE);
                }
            }
        }
    }

    /**
     * 返回是否存在冲突证据。
     *
     * @return 是否存在冲突证据
     */
    public boolean hasConflicts() {
        if (!conflicts.isEmpty()) {
            return true;
        }
        return false;
    }

    private List<FactFinding> resolveCardFactFindings(EvidenceCard card) {
        if (card != null && card.getFactFindings() != null && !card.getFactFindings().isEmpty()) {
            return card.getFactFindings();
        }
        return List.of();
    }

    private List<EvidenceAnchor> resolveCardAnchors(EvidenceCard card) {
        if (card != null && card.getEvidenceAnchors() != null && !card.getEvidenceAnchors().isEmpty()) {
            return card.getEvidenceAnchors();
        }
        return List.of();
    }

    private List<ProjectionCandidate> buildProjectionCandidates(List<FactFinding> factFindings) {
        List<ProjectionCandidate> candidates = new ArrayList<ProjectionCandidate>();
        if (factFindings == null || factFindings.isEmpty()) {
            return candidates;
        }
        for (FactFinding factFinding : factFindings) {
            if (factFinding == null || factFinding.getAnchorIds() == null || !passesQualityGate(factFinding)) {
                continue;
            }
            for (String anchorId : factFinding.getAnchorIds()) {
                EvidenceAnchor evidenceAnchor = anchorsById.get(anchorId);
                ProjectionCandidate projectionCandidate = buildProjectionCandidate(factFinding, evidenceAnchor);
                if (projectionCandidate != null) {
                    candidates.add(projectionCandidate);
                }
            }
        }
        return candidates;
    }

    private ProjectionCandidate buildProjectionCandidate(FactFinding factFinding, EvidenceAnchor evidenceAnchor) {
        if (factFinding == null
                || evidenceAnchor == null
                || evidenceAnchor.getSourceType() == null
                || evidenceAnchor.getRetrievalScore() < MIN_PROJECTABLE_ANCHOR_SCORE) {
            return null;
        }
        switch (evidenceAnchor.getSourceType()) {
            case ARTICLE:
                return new ProjectionCandidate(
                        projectionCandidateId(factFinding, evidenceAnchor),
                        factFinding.getFactKey(),
                        evidenceAnchor.getAnchorId(),
                        ProjectionCitationFormat.ARTICLE,
                        evidenceAnchor.getSourceId(),
                        0,
                        true,
                        evidenceAnchor.getRetrievalScore()
                );
            case SOURCE_FILE:
                return new ProjectionCandidate(
                        projectionCandidateId(factFinding, evidenceAnchor),
                        factFinding.getFactKey(),
                        evidenceAnchor.getAnchorId(),
                        ProjectionCitationFormat.SOURCE_FILE,
                        evidenceAnchor.getSourceId(),
                        0,
                        true,
                        evidenceAnchor.getRetrievalScore()
                );
            default:
                return null;
        }
    }

    private boolean hasProjectionCandidate(ProjectionCandidate projectionCandidate) {
        for (ProjectionCandidate existingCandidate : projectionCandidates) {
            if (Objects.equals(existingCandidate.getProjectionCandidateId(), projectionCandidate.getProjectionCandidateId())) {
                return true;
            }
        }
        return false;
    }

    private String projectionCandidateId(FactFinding factFinding, EvidenceAnchor evidenceAnchor) {
        return "pc-" + normalize(factFinding.getFactKey()) + "-" + normalize(evidenceAnchor.getAnchorId());
    }

    private FactFinding findByMergeIdentity(List<FactFinding> factFindings, String mergeIdentity) {
        for (FactFinding factFinding : factFindings) {
            if (Objects.equals(factFinding.mergeIdentity(), mergeIdentity)) {
                return factFinding;
            }
        }
        return null;
    }

    private void mergeAnchorIds(FactFinding target, FactFinding source) {
        List<String> mergedAnchorIds = new ArrayList<String>();
        if (target.getAnchorIds() != null) {
            mergedAnchorIds.addAll(target.getAnchorIds());
        }
        for (String anchorId : source.getAnchorIds()) {
            if (!mergedAnchorIds.contains(anchorId)) {
                mergedAnchorIds.add(anchorId);
            }
        }
        target.setAnchorIds(mergedAnchorIds);
    }

    private void registerConflict(String factKey, List<FactFinding> factFindings) {
        List<String> observedValues = new ArrayList<String>();
        for (FactFinding factFinding : factFindings) {
            String valueSignature = normalize(factFinding.getValueText()) + "|" + normalize(factFinding.getUnit());
            if (!observedValues.contains(valueSignature)) {
                observedValues.add(valueSignature);
            }
        }
        if (observedValues.size() > 1) {
            conflicts.put(factKey, observedValues);
            return;
        }
        conflicts.remove(factKey);
    }

    private void registerComplements(FactFinding newFinding) {
        String newFactKey = normalize(newFinding.getFactKey());
        String newSubject = normalize(newFinding.getSubject());
        if (newFactKey.isEmpty() || newSubject.isEmpty()) {
            return;
        }
        for (Map.Entry<String, List<FactFinding>> entry : findingsByFactKey.entrySet()) {
            String existingFactKey = normalize(entry.getKey());
            if (existingFactKey.isEmpty() || existingFactKey.equals(newFactKey)) {
                continue;
            }
            FactFinding existingFinding = entry.getValue().isEmpty() ? null : entry.getValue().get(0);
            if (existingFinding == null || !newSubject.equals(normalize(existingFinding.getSubject()))) {
                continue;
            }
            addComplement(newFactKey, existingFactKey);
            addComplement(existingFactKey, newFactKey);
        }
    }

    private void addComplement(String factKey, String complementFactKey) {
        List<String> complementFactKeys = complements.computeIfAbsent(factKey, key -> new ArrayList<String>());
        if (!complementFactKeys.contains(complementFactKey)) {
            complementFactKeys.add(complementFactKey);
        }
    }

    private boolean passesQualityGate(FactFinding factFinding) {
        if (factFinding == null || isBlank(factFinding.getClaimText())) {
            return false;
        }
        if (factFinding.getConfidence() < MIN_FINDING_CONFIDENCE) {
            return false;
        }
        if (factFinding.getSupportLevel() == FindingSupportLevel.INFERRED && !hasRegisteredAnchor(factFinding)) {
            return false;
        }
        return true;
    }

    private boolean hasRegisteredAnchor(FactFinding factFinding) {
        if (factFinding == null || factFinding.getAnchorIds() == null || factFinding.getAnchorIds().isEmpty()) {
            return false;
        }
        if (anchorsById.isEmpty()) {
            return true;
        }
        for (String anchorId : factFinding.getAnchorIds()) {
            if (anchorsById.containsKey(anchorId)) {
                return true;
            }
        }
        return false;
    }

    private boolean isActiveProjection(AnswerProjection answerProjection) {
        return answerProjection != null
                && answerProjection.getStatus() == ProjectionStatus.ACTIVE
                && !isBlank(answerProjection.getAnchorId());
    }

    private List<String> findFactKeysByAnchorId(String anchorId) {
        List<String> factKeys = new ArrayList<String>();
        if (isBlank(anchorId)) {
            return factKeys;
        }
        for (Map.Entry<String, List<FactFinding>> entry : findingsByFactKey.entrySet()) {
            for (FactFinding factFinding : entry.getValue()) {
                if (factFinding.getAnchorIds() != null && factFinding.getAnchorIds().contains(anchorId)) {
                    factKeys.add(entry.getKey());
                    break;
                }
            }
        }
        return factKeys;
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
