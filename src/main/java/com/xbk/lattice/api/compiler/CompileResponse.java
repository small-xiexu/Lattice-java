package com.xbk.lattice.api.compiler;

/**
 * 编译响应
 *
 * 职责：表示最小编译接口的返回结果
 *
 * @author xiexu
 */
public class CompileResponse {

    private final int persistedCount;

    /**
     * 创建编译响应。
     *
     * @param persistedCount 落盘数量
     */
    public CompileResponse(int persistedCount) {
        this.persistedCount = persistedCount;
    }

    /**
     * 获取落盘数量。
     *
     * @return 落盘数量
     */
    public int getPersistedCount() {
        return persistedCount;
    }
}
