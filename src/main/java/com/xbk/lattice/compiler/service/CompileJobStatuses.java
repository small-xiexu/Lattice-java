package com.xbk.lattice.compiler.service;

/**
 * 编译作业状态常量
 *
 * 职责：统一定义后台编译作业的生命周期状态
 *
 * @author xiexu
 */
public final class CompileJobStatuses {

    public static final String QUEUED = "QUEUED";

    public static final String RUNNING = "RUNNING";

    public static final String SUCCEEDED = "SUCCEEDED";

    public static final String FAILED = "FAILED";

    private CompileJobStatuses() {
    }
}
