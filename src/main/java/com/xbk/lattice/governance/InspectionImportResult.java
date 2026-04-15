package com.xbk.lattice.governance;

import java.util.List;

/**
 * Inspection 答案导入结果
 *
 * 职责：表示人工答案导回后的处理结果
 *
 * @author xiexu
 */
public class InspectionImportResult {

    private final int importedCount;

    private final List<String> resolvedIds;

    /**
     * 创建导入结果。
     *
     * @param importedCount 导入成功数量
     * @param resolvedIds 已解决的问题标识
     */
    public InspectionImportResult(int importedCount, List<String> resolvedIds) {
        this.importedCount = importedCount;
        this.resolvedIds = resolvedIds;
    }

    public int getImportedCount() {
        return importedCount;
    }

    public List<String> getResolvedIds() {
        return resolvedIds;
    }
}
