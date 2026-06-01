package com.xbk.lattice.query.deepresearch.domain;

import com.xbk.lattice.query.evidence.domain.*;
import com.xbk.lattice.query.deepresearch.validator.DeepResearchAnchorValidator;
import lombok.Getter;

import java.util.*;

/**
 * 证据账本。
 *
 * <p>维护 Deep Research 的事实索引、锚点索引与冲突探测结果——所有集合和 Map 通过
 * addCard/addFactFinding/addAnchor/addProjectionCandidate/markCoverage 等业务方法维护，
 * 外部不得直接替换集合/Map 引用。
 *
 * @author xiexu
 */
@Getter
public class EvidenceLedger {

    private static final DeepResearchAnchorValidator ANCHOR_VALIDATOR = new DeepResearchAnchorValidator();
    private static final double MIN_FINDING_CONFIDENCE = 0.55D;
    private static final double MIN_PROJECTABLE_ANCHOR_SCORE = 0.55D;

    /** 证据卡列表。通过 addCard() 追加。 */
    private List<EvidenceCard> cards = new ArrayList<EvidenceCard>();
    /** 按任务 ID 索引的证据卡。key=taskId。 */
    private Map<String, EvidenceCard> cardsByTaskId = new LinkedHashMap<String, EvidenceCard>();
    /** 按 factKey 索引的 finding 列表。key=factKey，value=属于该 factKey 的 finding 列表。 */
    private Map<String, List<FactFinding>> findingsByFactKey = new LinkedHashMap<String, List<FactFinding>>();
    /** 按 anchorId 索引的锚点。key=anchorId。 */
    private Map<String, EvidenceAnchor> anchorsById = new LinkedHashMap<String, EvidenceAnchor>();
    /** 投影候选列表。通过 addProjectionCandidate() 追加（仅 verified=true 的候选）。 */
    private List<ProjectionCandidate> projectionCandidates = new ArrayList<ProjectionCandidate>();
    /** 冲突索引。key=factKey，value=冲突值签名列表。同一 factKey 下有多个不同 value+unit 时触发。 */
    private Map<String, List<String>> conflicts = new LinkedHashMap<String, List<String>>();
    /** 互补事实索引。key=factKey，value=互补 factKey 列表。同一 subject 的不同 factKey 互相标记为互补。 */
    private Map<String, List<String>> complements = new LinkedHashMap<String, List<String>>();
    /** 覆盖状态。key=factKey，value=是否已被最终投影覆盖。通过 markCoverage/registerMustResolveFactKeys 维护。 */
    private Map<String, Boolean> coverageState = new LinkedHashMap<String, Boolean>();

    public void addCard(EvidenceCard card) {
        if (card != null) {
            cards.add(card);
            if (!isBlank(card.getTaskId())) cardsByTaskId.put(card.getTaskId(), card);
            addAnchors(resolveCardAnchors(card));
            addFactFindings(resolveCardFactFindings(card));
            addProjectionCandidates(buildProjectionCandidates(resolveCardFactFindings(card)));
        }
    }
    public void addCards(List<EvidenceCard> evidenceCards) {
        if (evidenceCards != null && !evidenceCards.isEmpty())
            for (EvidenceCard c : evidenceCards) addCard(c);
    }
    public int cardCount() { return cards.size(); }

    public int findingCount() {
        int n = 0;
        for (List<FactFinding> ffs : findingsByFactKey.values()) n += ffs.size();
        return n;
    }
    public void addFactFinding(FactFinding f) {
        if (f == null) return;
        if (!f.canEnterLedger()) throw new IllegalArgumentException("finding 缺少冻结 factKey 或 anchorIds，禁止写入 ledger");
        if (!passesQualityGate(f)) return;
        String fk = f.getFactKey();
        List<FactFinding> list = findingsByFactKey.computeIfAbsent(fk, k -> new ArrayList<>());
        FactFinding exist = findByMergeIdentity(list, f.mergeIdentity());
        if (exist != null) { mergeAnchorIds(exist, f); if (f.getConfidence() > exist.getConfidence()) exist.setConfidence(f.getConfidence()); return; }
        list.add(f); registerConflict(fk, list); registerComplements(f);
    }
    public void addFactFindings(List<FactFinding> ffs) {
        if (ffs != null && !ffs.isEmpty()) for (FactFinding f : ffs) addFactFinding(f);
    }
    public void addAnchor(EvidenceAnchor a) {
        if (a == null || isBlank(a.getAnchorId()) || !a.hasReusableIdentity()) return;
        anchorsById.putIfAbsent(ANCHOR_VALIDATOR.validateAndNormalize(a).getAnchorId(), ANCHOR_VALIDATOR.validateAndNormalize(a));
    }
    public void addAnchors(List<EvidenceAnchor> as) {
        if (as != null && !as.isEmpty()) for (EvidenceAnchor a : as) addAnchor(a);
    }
    public void addProjectionCandidate(ProjectionCandidate pc) {
        if (pc != null && pc.isVerified() && !hasProjectionCandidate(pc)) projectionCandidates.add(pc);
    }
    public void addProjectionCandidates(List<ProjectionCandidate> pcs) {
        if (pcs != null && !pcs.isEmpty()) for (ProjectionCandidate pc : pcs) addProjectionCandidate(pc);
    }
    public void markCoverage(String fk, boolean covered) { if (!isBlank(fk)) coverageState.put(fk, covered); }
    public void registerMustResolveFactKeys(List<String> fks) {
        if (fks != null && !fks.isEmpty()) for (String fk : fks) if (!isBlank(fk)) coverageState.putIfAbsent(fk.trim(), false);
    }
    public void refreshCoverageState(List<AnswerProjection> aps) {
        if (coverageState.isEmpty()) return;
        for (String fk : new ArrayList<>(coverageState.keySet())) coverageState.put(fk, false);
        if (aps == null || aps.isEmpty()) return;
        for (AnswerProjection ap : aps) {
            if (!isActiveProjection(ap)) continue;
            for (String fk : findFactKeysByAnchorId(ap.getAnchorId()))
                if (coverageState.containsKey(fk)) coverageState.put(fk, true);
        }
    }
    public boolean hasConflicts() { return !conflicts.isEmpty(); }

    private List<FactFinding> resolveCardFactFindings(EvidenceCard c) {
        return (c != null && c.getFactFindings() != null && !c.getFactFindings().isEmpty()) ? c.getFactFindings() : List.of();
    }
    private List<EvidenceAnchor> resolveCardAnchors(EvidenceCard c) {
        return (c != null && c.getEvidenceAnchors() != null && !c.getEvidenceAnchors().isEmpty()) ? c.getEvidenceAnchors() : List.of();
    }
    private List<ProjectionCandidate> buildProjectionCandidates(List<FactFinding> ffs) {
        List<ProjectionCandidate> cs = new ArrayList<>();
        if (ffs == null || ffs.isEmpty()) return cs;
        for (FactFinding f : ffs) {
            if (f == null || f.getAnchorIds() == null || !passesQualityGate(f)) continue;
            for (String aid : f.getAnchorIds()) {
                ProjectionCandidate pc = buildProjectionCandidate(f, anchorsById.get(aid));
                if (pc != null) cs.add(pc);
            }
        }
        return cs;
    }
    private ProjectionCandidate buildProjectionCandidate(FactFinding f, EvidenceAnchor a) {
        if (f == null || a == null || a.getSourceType() == null || a.getRetrievalScore() < MIN_PROJECTABLE_ANCHOR_SCORE) return null;
        ProjectionCitationFormat fmt;
        switch (a.getSourceType()) {
            case ARTICLE: fmt = ProjectionCitationFormat.ARTICLE; break;
            case SOURCE_FILE: fmt = ProjectionCitationFormat.SOURCE_FILE; break;
            default: return null;
        }
        return new ProjectionCandidate(projectionCandidateId(f, a), f.getFactKey(), a.getAnchorId(), fmt, a.getSourceId(), 0, true, a.getRetrievalScore());
    }
    private boolean hasProjectionCandidate(ProjectionCandidate pc) {
        for (ProjectionCandidate ec : projectionCandidates)
            if (Objects.equals(ec.getProjectionCandidateId(), pc.getProjectionCandidateId())) return true;
        return false;
    }
    private String projectionCandidateId(FactFinding f, EvidenceAnchor a) { return "pc-" + normalize(f.getFactKey()) + "-" + normalize(a.getAnchorId()); }
    private FactFinding findByMergeIdentity(List<FactFinding> list, String mi) { for (FactFinding f : list) if (Objects.equals(f.mergeIdentity(), mi)) return f; return null; }
    private void mergeAnchorIds(FactFinding t, FactFinding s) {
        List<String> m = new ArrayList<>(t.getAnchorIds() != null ? t.getAnchorIds() : List.of());
        for (String aid : s.getAnchorIds()) if (!m.contains(aid)) m.add(aid);
        t.setAnchorIds(m);
    }
    private void registerConflict(String fk, List<FactFinding> list) {
        List<String> vals = new ArrayList<>();
        for (FactFinding f : list) { String sig = normalize(f.getValueText()) + "|" + normalize(f.getUnit()); if (!vals.contains(sig)) vals.add(sig); }
        if (vals.size() > 1) conflicts.put(fk, vals); else conflicts.remove(fk);
    }
    private void registerComplements(FactFinding nf) {
        String nfk = normalize(nf.getFactKey()), ns = normalize(nf.getSubject());
        if (nfk.isEmpty() || ns.isEmpty()) return;
        for (Map.Entry<String, List<FactFinding>> e : findingsByFactKey.entrySet()) {
            String efk = normalize(e.getKey());
            if (efk.isEmpty() || efk.equals(nfk)) continue;
            FactFinding ef = e.getValue().isEmpty() ? null : e.getValue().get(0);
            if (ef != null && ns.equals(normalize(ef.getSubject()))) { addComplement(nfk, efk); addComplement(efk, nfk); }
        }
    }
    private void addComplement(String fk, String cfk) { complements.computeIfAbsent(fk, k -> new ArrayList<>()).add(cfk); }
    private boolean passesQualityGate(FactFinding f) {
        if (f == null || isBlank(f.getClaimText())) return false;
        if (f.getConfidence() < MIN_FINDING_CONFIDENCE) return false;
        if (f.getSupportLevel() == FindingSupportLevel.INFERRED && !hasRegisteredAnchor(f)) return false;
        return true;
    }
    private boolean hasRegisteredAnchor(FactFinding f) {
        if (f == null || f.getAnchorIds() == null || f.getAnchorIds().isEmpty()) return false;
        if (anchorsById.isEmpty()) return true;
        for (String aid : f.getAnchorIds()) if (anchorsById.containsKey(aid)) return true;
        return false;
    }
    private boolean isActiveProjection(AnswerProjection ap) { return ap != null && ap.getStatus() == ProjectionStatus.ACTIVE && !isBlank(ap.getAnchorId()); }
    private List<String> findFactKeysByAnchorId(String aid) {
        List<String> fks = new ArrayList<>();
        if (isBlank(aid)) return fks;
        for (Map.Entry<String, List<FactFinding>> e : findingsByFactKey.entrySet())
            for (FactFinding f : e.getValue()) if (f.getAnchorIds() != null && f.getAnchorIds().contains(aid)) { fks.add(e.getKey()); break; }
        return fks;
    }
    private String normalize(String v) { return v == null ? "" : v.trim(); }
    private boolean isBlank(String v) { return v == null || v.trim().isEmpty(); }
}
