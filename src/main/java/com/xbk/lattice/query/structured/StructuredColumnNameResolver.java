package com.xbk.lattice.query.structured;

import com.xbk.lattice.infra.persistence.StructuredTableCellRecord;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 结构化列名解析器
 *
 * 职责：用通用表头归一化规则匹配用户问题中的字段名与实际列名
 *
 * @author xiexu
 */
public class StructuredColumnNameResolver {

    /**
     * 判断实际列名是否匹配用户请求的字段名。
     *
     * @param actualColumnName 实际列名
     * @param requestedColumnName 用户请求字段名
     * @return 是否匹配
     */
    public boolean matches(String actualColumnName, String requestedColumnName) {
        if (!StringUtils.hasText(actualColumnName) || !StringUtils.hasText(requestedColumnName)) {
            return false;
        }
        String actualNormalized = normalize(actualColumnName);
        String requestedNormalized = normalize(requestedColumnName);
        if (actualNormalized.equals(requestedNormalized)) {
            return true;
        }
        String actualCompact = compact(actualNormalized);
        String requestedCompact = compact(requestedNormalized);
        if (actualCompact.equals(requestedCompact)) {
            return true;
        }
        if (isMeaningfulContainment(actualCompact, requestedCompact)) {
            return true;
        }
        Set<String> actualTokens = semanticTokens(actualNormalized);
        Set<String> requestedTokens = semanticTokens(requestedNormalized);
        for (String token : requestedTokens) {
            if (actualTokens.contains(token)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 在单元格集合中查找匹配字段名的单元格。
     *
     * @param cellRecords 单元格集合
     * @param requestedColumnName 用户请求字段名
     * @return 命中的单元格
     */
    public StructuredTableCellRecord findCell(
            List<StructuredTableCellRecord> cellRecords,
            String requestedColumnName
    ) {
        if (cellRecords == null || !StringUtils.hasText(requestedColumnName)) {
            return null;
        }
        for (StructuredTableCellRecord cellRecord : cellRecords) {
            if (cellRecord != null && matches(cellRecord.getColumnName(), requestedColumnName)) {
                return cellRecord;
            }
        }
        return null;
    }

    /**
     * 判断一行是否覆盖全部投影字段。
     *
     * @param cellRecords 单元格集合
     * @param projections 投影字段
     * @return 是否覆盖
     */
    public boolean hasProjectionCells(
            List<StructuredTableCellRecord> cellRecords,
            List<String> projections
    ) {
        if (projections == null || projections.isEmpty()) {
            return true;
        }
        for (String projection : projections) {
            if (findCell(cellRecords, projection) == null) {
                return false;
            }
        }
        return true;
    }

    private String normalize(String value) {
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private String compact(String value) {
        return value.replaceAll("[\\s_\\-.]+", "");
    }

    private boolean isMeaningfulContainment(String left, String right) {
        if (left.length() < 3 || right.length() < 3) {
            return false;
        }
        return left.contains(right) || right.contains(left);
    }

    private Set<String> semanticTokens(String value) {
        Set<String> tokens = new LinkedHashSet<String>();
        for (String token : splitAsciiTokens(value)) {
            addCanonicalToken(tokens, token);
        }
        addCjkCanonicalTokens(tokens, value);
        return tokens;
    }

    private List<String> splitAsciiTokens(String value) {
        List<String> tokens = new ArrayList<String>();
        for (String token : value.split("[^A-Za-z0-9]+")) {
            if (token.length() >= 2) {
                tokens.add(token);
            }
        }
        return tokens;
    }

    private void addCjkCanonicalTokens(Set<String> tokens, String value) {
        if (value.contains("名称") || value.contains("名字") || value.contains("标题")) {
            tokens.add("name");
            tokens.add("title");
        }
        if (value.contains("备注") || value.contains("注释") || value.contains("说明") || value.contains("描述")) {
            tokens.add("remark");
            tokens.add("note");
            tokens.add("comment");
            tokens.add("description");
        }
        if (value.contains("状态")) {
            tokens.add("status");
        }
        if (value.contains("类别") || value.contains("分类")) {
            tokens.add("category");
            tokens.add("type");
        }
    }

    private void addCanonicalToken(Set<String> tokens, String token) {
        String normalizedToken = token.toLowerCase(Locale.ROOT);
        if ("name".equals(normalizedToken) || "title".equals(normalizedToken)) {
            tokens.add("name");
            tokens.add("title");
        }
        if ("remark".equals(normalizedToken)
                || "remarks".equals(normalizedToken)
                || "note".equals(normalizedToken)
                || "notes".equals(normalizedToken)
                || "comment".equals(normalizedToken)
                || "comments".equals(normalizedToken)
                || "description".equals(normalizedToken)
                || "desc".equals(normalizedToken)) {
            tokens.add("remark");
            tokens.add("note");
            tokens.add("comment");
            tokens.add("description");
        }
        if ("status".equals(normalizedToken) || "state".equals(normalizedToken)) {
            tokens.add("status");
            tokens.add("state");
        }
        if ("category".equals(normalizedToken) || "type".equals(normalizedToken) || "kind".equals(normalizedToken)) {
            tokens.add("category");
            tokens.add("type");
            tokens.add("kind");
        }
    }
}
