package com.xbk.lattice.compiler.service;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 编译编排器注册表
 *
 * 职责：按 orchestration mode 路由到具体编排器
 *
 * @author xiexu
 */
@Service
@Profile("jdbc")
public class CompileOrchestratorRegistry {

    private final Map<String, CompileOrchestrator> compileOrchestratorMap;

    /**
     * 创建编译编排器注册表。
     *
     * @param compileOrchestrators 已注册编排器
     */
    public CompileOrchestratorRegistry(List<CompileOrchestrator> compileOrchestrators) {
        this.compileOrchestratorMap = new LinkedHashMap<String, CompileOrchestrator>();
        for (CompileOrchestrator compileOrchestrator : compileOrchestrators) {
            compileOrchestratorMap.put(compileOrchestrator.getMode(), compileOrchestrator);
        }
    }

    /**
     * 执行编译。
     *
     * @param orchestrationMode 编排模式
     * @param sourceDir 源目录
     * @param incremental 是否增量编译
     * @return 编译结果
     * @throws IOException IO 异常
     */
    public CompileResult execute(String orchestrationMode, Path sourceDir, boolean incremental) throws IOException {
        String normalizedMode = CompileOrchestrationModes.normalize(orchestrationMode);
        CompileOrchestrator compileOrchestrator = compileOrchestratorMap.get(normalizedMode);
        if (compileOrchestrator == null) {
            throw new IllegalArgumentException("unsupported orchestration mode: " + orchestrationMode);
        }
        return compileOrchestrator.execute(sourceDir, incremental);
    }
}
