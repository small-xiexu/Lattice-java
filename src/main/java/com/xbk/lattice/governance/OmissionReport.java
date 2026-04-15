package com.xbk.lattice.governance;

import java.util.List;

/**
 * 遗漏报告
 *
 * 职责：汇总未被任何文章引用的源文件清单
 *
 * @author xiexu
 */
public class OmissionReport {

    private final int totalSourceFileCount;

    private final List<String> items;

    /**
     * 创建遗漏报告。
     *
     * @param totalSourceFileCount 源文件总数
     * @param items 未覆盖源文件路径列表
     */
    public OmissionReport(int totalSourceFileCount, List<String> items) {
        this.totalSourceFileCount = totalSourceFileCount;
        this.items = items;
    }

    /**
     * 获取源文件总数。
     *
     * @return 源文件总数
     */
    public int getTotalSourceFileCount() {
        return totalSourceFileCount;
    }

    /**
     * 获取遗漏源文件数量。
     *
     * @return 遗漏源文件数量
     */
    public int getOmittedSourceFileCount() {
        return items == null ? 0 : items.size();
    }

    /**
     * 获取遗漏源文件清单。
     *
     * @return 遗漏源文件清单
     */
    public List<String> getItems() {
        return items;
    }
}
