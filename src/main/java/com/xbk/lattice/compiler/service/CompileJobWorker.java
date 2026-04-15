package com.xbk.lattice.compiler.service;

import com.xbk.lattice.compiler.config.CompileJobProperties;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 编译作业 worker
 *
 * 职责：轮询并执行排队中的后台编译作业
 *
 * @author xiexu
 */
@Component
@Profile("jdbc")
public class CompileJobWorker {

    private final CompileJobService compileJobService;

    private final CompileJobProperties compileJobProperties;

    /**
     * 创建编译作业 worker。
     *
     * @param compileJobService 编译作业服务
     * @param compileJobProperties 编译作业配置
     */
    public CompileJobWorker(
            CompileJobService compileJobService,
            CompileJobProperties compileJobProperties
    ) {
        this.compileJobService = compileJobService;
        this.compileJobProperties = compileJobProperties;
    }

    /**
     * 轮询执行排队中的作业。
     */
    @Scheduled(fixedDelayString = "${lattice.compiler.jobs.poll-delay-ms:1000}")
    public void pollAndExecute() {
        if (!compileJobProperties.isWorkerEnabled()) {
            return;
        }
        compileJobService.processNextQueuedJob();
    }
}
