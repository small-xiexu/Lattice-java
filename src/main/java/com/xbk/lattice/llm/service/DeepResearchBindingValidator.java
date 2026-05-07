package com.xbk.lattice.llm.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Deep Research 绑定校验器
 *
 * 职责：在应用启动时校验 deep_research scene 的 role 绑定、模型配置与连接配置是否完整可用
 *
 * @author xiexu
 */
@Component
@ConditionalOnProperty(
        prefix = "lattice.llm",
        name = "deep-research-startup-validation-enabled",
        havingValue = "true",
        matchIfMissing = true
)
@Slf4j
public class DeepResearchBindingValidator implements ApplicationRunner {

    private final ExecutionLlmSnapshotService executionLlmSnapshotService;

    /**
     * 创建 Deep Research 绑定校验器。
     *
     * @param executionLlmSnapshotService 运行时快照服务
     */
    public DeepResearchBindingValidator(ExecutionLlmSnapshotService executionLlmSnapshotService) {
        this.executionLlmSnapshotService = executionLlmSnapshotService;
    }

    /**
     * 启动时执行 Deep Research scene 绑定校验。
     *
     * @param args 启动参数
     */
    @Override
    public void run(ApplicationArguments args) {
        executionLlmSnapshotService.validateSceneBindings(ExecutionLlmSnapshotService.DEEP_RESEARCH_SCENE);
        log.info("Validated deep_research scene bindings successfully");
    }
}
