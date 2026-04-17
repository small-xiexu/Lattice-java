package com.xbk.lattice.compiler.service;

/**
 * 编译编排模式常量
 *
 * 职责：统一定义编译执行可选的 orchestration mode
 *
 * @author xiexu
 */
public final class CompileOrchestrationModes {

    public static final String STATE_GRAPH = "state_graph";

    private CompileOrchestrationModes() {
    }

    /**
     * 规范化编排模式。
     *
     * @param orchestrationMode 原始模式
     * @return 规范化结果
     */
    public static String normalize(String orchestrationMode) {
        return STATE_GRAPH;
    }
}
