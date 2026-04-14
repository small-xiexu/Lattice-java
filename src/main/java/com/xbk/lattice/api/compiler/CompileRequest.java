package com.xbk.lattice.api.compiler;

/**
 * 编译请求
 *
 * 职责：承载最小编译接口的请求参数
 *
 * @author xiexu
 */
public class CompileRequest {

    private String sourceDir;

    /**
     * 获取源目录。
     *
     * @return 源目录
     */
    public String getSourceDir() {
        return sourceDir;
    }

    /**
     * 设置源目录。
     *
     * @param sourceDir 源目录
     */
    public void setSourceDir(String sourceDir) {
        this.sourceDir = sourceDir;
    }
}
