package com.xbk.lattice.governance;

import java.util.ArrayList;
import java.util.List;

/**
 * 传播报告
 *
 * 职责：汇总某次纠错会影响到的下游文章
 *
 * @author xiexu
 */
public class PropagationReport {

    private final String rootConceptId;

    private final String correctionSummary;

    private final List<PropagationItem> items;

    /**
     * 创建传播报告。
     *
     * @param rootConceptId 被纠错的根概念
     * @param correctionSummary 纠错摘要
     * @param items 下游影响项
     */
    public PropagationReport(String rootConceptId, String correctionSummary, List<PropagationItem> items) {
        this.rootConceptId = rootConceptId;
        this.correctionSummary = correctionSummary;
        this.items = items;
    }

    public String getRootConceptId() {
        return rootConceptId;
    }

    public String getCorrectionSummary() {
        return correctionSummary;
    }

    public List<PropagationItem> getItems() {
        return items;
    }

    public int getImpactedCount() {
        return items.size();
    }

    public List<String> getImpactedConceptIds() {
        List<String> conceptIds = new ArrayList<String>();
        for (PropagationItem item : items) {
            conceptIds.add(item.getConceptId());
        }
        return conceptIds;
    }
}
