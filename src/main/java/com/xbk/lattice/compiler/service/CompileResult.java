package com.xbk.lattice.compiler.service;

/**
 * 编译结果
 *
 * 职责：表示最小编译链路的落盘结果
 *
 * @author xiexu
 */
public class CompileResult {

    private final int persistedCount;

    /**
     * 创建编译结果。
     *
     * @param persistedCount 落盘文章数
     */
    public CompileResult(int persistedCount) {
        this.persistedCount = persistedCount;
    }

    /**
     * 获取落盘文章数。
     *
     * @return 落盘文章数
     */
    public int getPersistedCount() {
        return persistedCount;
    }
}
