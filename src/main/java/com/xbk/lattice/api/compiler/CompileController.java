package com.xbk.lattice.api.compiler;

import com.xbk.lattice.compiler.service.CompilePipelineService;
import com.xbk.lattice.compiler.service.CompileResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 编译控制器
 *
 * 职责：暴露最小编译入口 API
 *
 * @author xiexu
 */
@RestController
@Slf4j
@Profile("jdbc")
@RequestMapping("/api/v1/compile")
public class CompileController {

    private final CompilePipelineService compilePipelineService;

    /**
     * 创建编译控制器。
     *
     * @param compilePipelineService 编译链路服务
     */
    public CompileController(CompilePipelineService compilePipelineService) {
        this.compilePipelineService = compilePipelineService;
    }

    /**
     * 触发最小编译链路。
     *
     * @param compileRequest 编译请求
     * @return 编译响应
     * @throws IOException IO 异常
     */
    @PostMapping
    public CompileResponse compile(@RequestBody CompileRequest compileRequest) throws IOException {
        Path sourceDir = Path.of(compileRequest.getSourceDir());
        validateSourceDir(sourceDir);
        log.info("Compile request received sourceDir: {}", sourceDir);
        CompileResult compileResult = compileRequest.isIncremental()
                ? compilePipelineService.incrementalCompile(sourceDir)
                : compilePipelineService.compile(sourceDir);
        return new CompileResponse(compileResult.getPersistedCount(), compileResult.getJobId());
    }

    /**
     * 基于 jobId 重试未完成的编译提交。
     *
     * @param compileRetryRequest 编译重试请求
     * @return 编译响应
     */
    @PostMapping("/retry")
    public CompileResponse retry(@RequestBody CompileRetryRequest compileRetryRequest) {
        CompileResult compileResult = compilePipelineService.retry(compileRetryRequest.getJobId());
        return new CompileResponse(compileResult.getPersistedCount(), compileResult.getJobId());
    }

    /**
     * 校验编译源目录。
     *
     * @param sourceDir 源目录
     */
    private void validateSourceDir(Path sourceDir) {
        if (!Files.exists(sourceDir) || !Files.isDirectory(sourceDir)) {
            throw new IllegalArgumentException("sourceDir 不存在或不是目录");
        }
    }
}
