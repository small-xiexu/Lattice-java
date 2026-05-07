package com.xbk.lattice.compiler.service;

import com.xbk.lattice.infra.persistence.FactCardRecord;

import java.util.List;

/**
 * 事实证据卡修复结果
 *
 * 职责：承载结构修复后的证据卡、修复动作与复核结果
 *
 * @author xiexu
 */
public class FactCardFixResult {

    private final FactCardRecord factCardRecord;

    private final List<String> actions;

    private final FactCardReviewResult reviewResult;

    /**
     * 创建事实证据卡修复结果。
     *
     * @param factCardRecord 修复后的事实证据卡
     * @param actions 修复动作
     * @param reviewResult 复核结果
     */
    public FactCardFixResult(
            FactCardRecord factCardRecord,
            List<String> actions,
            FactCardReviewResult reviewResult
    ) {
        this.factCardRecord = factCardRecord;
        this.actions = actions == null ? List.of() : List.copyOf(actions);
        this.reviewResult = reviewResult;
    }

    /**
     * 获取修复后的事实证据卡。
     *
     * @return 事实证据卡
     */
    public FactCardRecord getFactCardRecord() {
        return factCardRecord;
    }

    /**
     * 获取修复动作。
     *
     * @return 修复动作
     */
    public List<String> getActions() {
        return actions;
    }

    /**
     * 获取复核结果。
     *
     * @return 复核结果
     */
    public FactCardReviewResult getReviewResult() {
        return reviewResult;
    }
}
