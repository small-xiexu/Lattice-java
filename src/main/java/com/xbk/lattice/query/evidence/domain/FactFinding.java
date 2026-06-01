package com.xbk.lattice.query.evidence.domain;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * 事实发现。
 *
 * <p>表示 researcher 从原始证据中抽取出的结构化事实槽位——通过 factKey 公式匹配和 mergeIdentity 去重。
 *
 * @author xiexu
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class FactFinding {

    /** 发现唯一标识。 */
    private String findingId;
    /** 事实键（由 subject.predicate.qualifier 组成）。 */
    private String factKey;
    /** 主体。 */
    private String subject;
    /** 谓词。 */
    private String predicate;
    /** 事实值文本。 */
    private String valueText;
    /** 事实值类型（NUMBER / BOOLEAN / STRING / ENUM / RANGE）。 */
    private FactValueType valueType;
    /** 单位（valueType=NUMBER 时有值）。 */
    private String unit;
    /** 限定语（如时间范围、条件）。 */
    private String qualifier;
    /** 事实声明文本。可能较长。 */
    private String claimText;
    /** 置信度（0.0-1.0）。 */
    private double confidence;
    /** 支撑级别（DIRECT / INFERRED）。 */
    private FindingSupportLevel supportLevel;
    /** 关联的锚点 ID 列表。 */
    private List<String> anchorIds = new ArrayList<String>();

    public String expectedFactKey() {
        if (isBlank(subject) || isBlank(predicate) || isBlank(qualifier)) return "";
        return subject.trim() + "." + predicate.trim() + "." + qualifier.trim();
    }

    public boolean matchesFrozenFactKey() {
        return !expectedFactKey().isBlank() && expectedFactKey().equals(factKey);
    }

    public String mergeIdentity() {
        String nfk = factKey == null ? "" : factKey.trim();
        String nvt = valueText == null ? "" : valueText.trim();
        String nu = unit == null ? "" : unit.trim();
        return nfk + "|" + nvt + "|" + nu;
    }

    public boolean canEnterLedger() {
        return matchesFrozenFactKey() && anchorIds != null && !anchorIds.isEmpty();
    }

    private boolean isBlank(String value) { return value == null || value.trim().isEmpty(); }
}
