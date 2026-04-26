package com.xbk.lattice.query.evidence.domain;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 事实发现
 *
 * 职责：表示 researcher 从原始证据中抽取出的结构化事实槽位
 *
 * @author xiexu
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FactFinding {

    private String findingId;

    private String factKey;

    private String subject;

    private String predicate;

    private String valueText;

    private FactValueType valueType;

    private String unit;

    private String qualifier;

    private String claimText;

    private double confidence;

    private FindingSupportLevel supportLevel;

    private List<String> anchorIds = new ArrayList<String>();

    /**
     * 构造冻结后的 factKey。
     *
     * @return 由主体、谓词和限定语拼出的事实键
     */
    public String expectedFactKey() {
        if (isBlank(subject) || isBlank(predicate) || isBlank(qualifier)) {
            return "";
        }
        return subject.trim() + "." + predicate.trim() + "." + qualifier.trim();
    }

    /**
     * 判断当前 factKey 是否满足冻结公式。
     *
     * @return 仅当 factKey 与公式完全一致时返回 true
     */
    public boolean matchesFrozenFactKey() {
        return !expectedFactKey().isBlank() && expectedFactKey().equals(factKey);
    }

    /**
     * 返回用于 run 内 merge/conflict 判定的二级身份键。
     *
     * @return `(factKey, valueText, unit)` 的规范化串
     */
    public String mergeIdentity() {
        String normalizedFactKey = factKey == null ? "" : factKey.trim();
        String normalizedValueText = valueText == null ? "" : valueText.trim();
        String normalizedUnit = unit == null ? "" : unit.trim();
        return normalizedFactKey + "|" + normalizedValueText + "|" + normalizedUnit;
    }

    /**
     * 判断当前 finding 是否具备最小可入账条件。
     *
     * @return 仅当 factKey 公式匹配且存在 anchor 时返回 true
     */
    public boolean canEnterLedger() {
        return matchesFrozenFactKey() && anchorIds != null && !anchorIds.isEmpty();
    }

    /**
     * 判断给定文本是否为空白。
     *
     * @param value 待判断文本
     * @return 为空白时返回 true
     */
    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
