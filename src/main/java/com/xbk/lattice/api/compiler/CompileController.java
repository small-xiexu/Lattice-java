package com.xbk.lattice.api.compiler;

import com.xbk.lattice.compiler.service.CompilePipelineService;
import com.xbk.lattice.compiler.service.CompileResult;
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
        CompileResult compileResult = compilePipelineService.compile(sourceDir);
        return new CompileResponse(compileResult.getPersistedCount());
    }
}
