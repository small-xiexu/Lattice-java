package com.xbk.lattice.api.compiler;

/**
 * 编译请求。
 *
 * <p>承载最小编译接口的请求参数，由 Spring MVC 从 JSON 请求体绑定。
 *
 * @author xiexu
 */
public class CompileRequest {

    /**
     * 源目录路径。
     *
     * <p>指定要编译的源文件根目录。编译流程会扫描该目录下的所有文件，
     * 经过解析、review、persist 全流程处理。为空时使用系统默认源目录。</p>
     */
    private String sourceDir;

    /**
     * 是否增量编译。
     *
     * <p>为 true 时只编译自上次编译以来发生变更的文件；为 false 时执行全量编译。
     * 增量编译可以显著减少编译耗时，但全量编译更适合清理历史残留。</p>
     */
    private boolean incremental;

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

    /**
     * 是否启用增量编译。
     *
     * @return 是否增量编译
     */
    public boolean isIncremental() {
        return incremental;
    }

    /**
     * 设置是否启用增量编译。
     *
     * @param incremental 是否增量编译
     */
    public void setIncremental(boolean incremental) {
        this.incremental = incremental;
    }
}
