package com.xbk.lattice.compiler.service;

import com.xbk.lattice.infra.persistence.CompileJobRecord;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 编译应用门面
 *
 * 职责：统一承接同步编译与同步重试入口，负责参数校验、默认模式选择与结果收口
 *
 * @author xiexu
 */
@Service
public class CompileApplicationFacade {

    private final CompileJobService compileJobService;

    /**
     * 创建编译应用门面。
     *
     * @param compileJobService 编译作业服务
     */
    public CompileApplicationFacade(CompileJobService compileJobService) {
        this.compileJobService = compileJobService;
    }

    /**
     * 执行同步编译。
     *
     * @param sourceDir 源目录
     * @param incremental 是否增量编译
     * @param orchestrationMode 编排模式
     * @return 编译结果
     */
    public CompileResult compile(Path sourceDir, boolean incremental, String orchestrationMode) {
        validateSourceDir(sourceDir);
        CompileJobRecord compileJobRecord = compileJobService.submit(
                sourceDir.toString(),
                incremental,
                false,
                orchestrationMode
        );
        return new CompileResult(compileJobRecord.getPersistedCount(), compileJobRecord.getJobId());
    }

    /**
     * 同步重试指定作业。
     *
     * @param jobId 作业标识
     * @return 编译结果
     */
    public CompileResult retry(String jobId) {
        CompileJobRecord compileJobRecord = compileJobService.retryNow(jobId);
        return new CompileResult(compileJobRecord.getPersistedCount(), compileJobRecord.getJobId());
    }

    private void validateSourceDir(Path sourceDir) {
        if (!Files.exists(sourceDir) || !Files.isDirectory(sourceDir)) {
            throw new IllegalArgumentException("sourceDir 不存在或不是目录");
        }
    }
}
