package com.xbk.lattice.compiler.graph;

import com.xbk.lattice.compiler.service.CompileOrchestrationModes;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * 编译图条件路由
 *
 * 职责：集中计算编译图在关键节点后的条件分支目标
 *
 * @author xiexu
 */
@Component
@Profile("jdbc")
public class CompileGraphConditions {

    /**
     * 计算 merge_concepts 之后的路由。
     *
     * @param state 编译图状态
     * @return 条件路由键
     */
    public String routeAfterMerge(CompileGraphState state) {
        if ("incremental".equalsIgnoreCase(state.getCompileMode())) {
            return "plan_changes";
        }
        return "compile_new_articles";
    }

    /**
     * 计算 plan_changes 之后的路由。
     *
     * @param state 编译图状态
     * @return 条件路由键
     */
    public String routeAfterPlanChanges(CompileGraphState state) {
        if (state.isNothingToDo()) {
            return "finalize_job";
        }
        if (state.isHasEnhancements()) {
            return "enhance_existing_articles";
        }
        return "compile_new_articles";
    }

    /**
     * 构建默认编排模式。
     *
     * @return 默认编排模式
     */
    public String defaultMode() {
        return CompileOrchestrationModes.STATE_GRAPH;
    }
}
