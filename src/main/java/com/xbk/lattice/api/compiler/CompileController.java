package com.xbk.lattice.api.compiler;

import com.xbk.lattice.compiler.service.CompileApplicationFacade;
import com.xbk.lattice.compiler.service.CompileResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
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

    private final CompileApplicationFacade compileApplicationFacade;

    /**
     * 创建编译控制器。
     *
     * @param compileApplicationFacade 编译应用门面
     */
    public CompileController(CompileApplicationFacade compileApplicationFacade) {
        this.compileApplicationFacade = compileApplicationFacade;
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
        log.info("Compile request received sourceDir: {}", sourceDir);
        CompileResult compileResult = compileApplicationFacade.compile(sourceDir, compileRequest.isIncremental(), null);
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
        CompileResult compileResult = compileApplicationFacade.retry(compileRetryRequest.getJobId());
        return new CompileResponse(compileResult.getPersistedCount(), compileResult.getJobId());
    }
}
