package com.xbk.lattice.compiler.service;

import java.util.Locale;

/**
 * 编译编排模式常量
 *
 * 职责：统一定义编译执行可选的 orchestration mode
 *
 * @author xiexu
 */
public final class CompileOrchestrationModes {

    public static final String SERVICE = "service";

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
        if (orchestrationMode == null) {
            return SERVICE;
        }
        String trimmedValue = orchestrationMode.trim();
        if (trimmedValue.isEmpty()) {
            return SERVICE;
        }
        String normalizedValue = trimmedValue.toLowerCase(Locale.ROOT);
        if (STATE_GRAPH.equals(normalizedValue)) {
            return STATE_GRAPH;
        }
        return SERVICE;
    }
}
