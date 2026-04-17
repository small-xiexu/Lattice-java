package com.xbk.lattice.compiler.agent;

/**
 * FixerAgent
 *
 * 职责：根据审查问题修复单篇草稿
 *
 * @author xiexu
 */
public interface FixerAgent {

    /**
     * 执行单篇草稿修复。
     *
     * @param fixTask 修复输入任务
     * @return 修复输出结果
     */
    FixerResult fix(FixTask fixTask);
}
