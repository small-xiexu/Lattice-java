package com.xbk.lattice.compiler.service;

import java.io.IOException;

/**
 * 编译编排器
 *
 * 职责：抽象不同 orchestration mode 下的编译执行入口
 *
 * @author xiexu
 */
public interface CompileOrchestrator {

    /**
     * 返回当前编排器模式标识。
     *
     * @return 模式标识
     */
    String getMode();

    /**
     * 执行编译。
     *
     * @param executionRequest 执行请求
     * @return 编译结果
     * @throws IOException IO 异常
     */
    CompileResult execute(CompileExecutionRequest executionRequest) throws IOException;
}
