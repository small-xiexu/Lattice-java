package com.xbk.lattice.compiler.service;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Service 编排器
 *
 * 职责：复用现有 service 链路完成 full / incremental compile
 *
 * @author xiexu
 */
@Service
@Profile("jdbc")
public class ServiceCompileOrchestrator implements CompileOrchestrator {

    private final CompilePipelineService compilePipelineService;

    /**
     * 创建 service 编排器。
     *
     * @param compilePipelineService 编译链路服务
     */
    public ServiceCompileOrchestrator(CompilePipelineService compilePipelineService) {
        this.compilePipelineService = compilePipelineService;
    }

    /**
     * 返回当前编排器模式标识。
     *
     * @return 模式标识
     */
    @Override
    public String getMode() {
        return CompileOrchestrationModes.SERVICE;
    }

    /**
     * 执行编译。
     *
     * @param sourceDir 源目录
     * @param incremental 是否增量编译
     * @return 编译结果
     * @throws IOException IO 异常
     */
    @Override
    public CompileResult execute(Path sourceDir, boolean incremental) throws IOException {
        if (incremental) {
            return compilePipelineService.incrementalCompile(sourceDir);
        }
        return compilePipelineService.compile(sourceDir);
    }
}
