package com.xbk.lattice.api.admin;

import com.xbk.lattice.admin.service.AdminUploadWorkspaceService;
import com.xbk.lattice.compiler.service.ChunkRebuildResult;
import com.xbk.lattice.compiler.service.ChunkRebuildService;
import com.xbk.lattice.compiler.service.CompileJobService;
import com.xbk.lattice.infra.persistence.CompileJobRecord;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 管理侧编译控制器
 *
 * 职责：暴露 compile job 提交、上传编译、作业查询与重试接口
 *
 * @author xiexu
 */
@RestController
@Profile("jdbc")
public class AdminCompileController {

    private final CompileJobService compileJobService;

    private final AdminUploadWorkspaceService adminUploadWorkspaceService;

    private final ChunkRebuildService chunkRebuildService;

    /**
     * 创建管理侧编译控制器。
     *
     * @param compileJobService 编译作业服务
     * @param adminUploadWorkspaceService 上传暂存服务
     * @param chunkRebuildService chunk 全量重建服务
     */
    public AdminCompileController(
            CompileJobService compileJobService,
            AdminUploadWorkspaceService adminUploadWorkspaceService,
            ChunkRebuildService chunkRebuildService
    ) {
        this.compileJobService = compileJobService;
        this.adminUploadWorkspaceService = adminUploadWorkspaceService;
        this.chunkRebuildService = chunkRebuildService;
    }

    /**
     * 提交目录型编译作业。
     *
     * @param compileJobRequest 编译作业请求
     * @return 编译作业响应
     */
    @PostMapping("/api/v1/admin/compile/jobs")
    public AdminCompileJobResponse submit(@RequestBody AdminCompileJobRequest compileJobRequest) {
        CompileJobRecord compileJobRecord = compileJobService.submit(
                compileJobRequest.getSourceDir(),
                compileJobRequest.isIncremental(),
                compileJobRequest.isAsync(),
                compileJobRequest.getOrchestrationMode()
        );
        return toResponse(compileJobRecord);
    }

    /**
     * 上传源文件并触发编译。
     *
     * @param files 上传文件
     * @param incremental 是否增量编译
     * @param async 是否异步执行
     * @param orchestrationMode 编排模式
     * @return 编译作业响应
     * @throws IOException IO 异常
     */
    @PostMapping(path = "/api/v1/admin/compile/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public AdminCompileJobResponse uploadAndCompile(
            @RequestParam("files") MultipartFile[] files,
            @RequestParam(defaultValue = "false") boolean incremental,
            @RequestParam(defaultValue = "true") boolean async,
            @RequestParam(required = false) String orchestrationMode
    ) throws IOException {
        Path workspaceDir = adminUploadWorkspaceService.save(files);
        CompileJobRecord compileJobRecord = compileJobService.submit(
                workspaceDir.toString(),
                incremental,
                async,
                orchestrationMode
        );
        return toResponse(compileJobRecord);
    }

    /**
     * 返回全部作业列表。
     *
     * @return 作业列表
     */
    @GetMapping("/api/v1/admin/jobs")
    public AdminCompileJobListResponse listJobs() {
        List<CompileJobRecord> compileJobRecords = compileJobService.listJobs();
        List<AdminCompileJobResponse> items = new ArrayList<AdminCompileJobResponse>();
        for (CompileJobRecord compileJobRecord : compileJobRecords) {
            items.add(toResponse(compileJobRecord));
        }
        return new AdminCompileJobListResponse(items.size(), items);
    }

    /**
     * 返回单个作业详情。
     *
     * @param jobId 作业标识
     * @return 作业详情
     */
    @GetMapping("/api/v1/admin/jobs/{jobId}")
    public AdminCompileJobResponse getJob(@PathVariable String jobId) {
        return toResponse(compileJobService.getJob(jobId));
    }

    /**
     * 重试失败作业。
     *
     * @param jobId 作业标识
     * @return 更新后的作业
     */
    @PostMapping("/api/v1/admin/jobs/{jobId}/retry")
    public AdminCompileJobResponse retry(@PathVariable String jobId) {
        return toResponse(compileJobService.retry(jobId));
    }

    /**
     * 触发 article/source chunks 的后台全量重建。
     *
     * @return 重建结果
     */
    @PostMapping("/api/v1/admin/compile/rebuild-chunks")
    public ChunkRebuildResult rebuildChunks() {
        return chunkRebuildService.rebuildAll();
    }

    /**
     * 将作业记录映射为管理侧响应。
     *
     * @param compileJobRecord 作业记录
     * @return 管理侧响应
     */
    private AdminCompileJobResponse toResponse(CompileJobRecord compileJobRecord) {
        return new AdminCompileJobResponse(
                compileJobRecord.getJobId(),
                compileJobRecord.getSourceDir(),
                adminUploadWorkspaceService.listRelativeFileNames(compileJobRecord.getSourceDir()),
                compileJobRecord.isIncremental(),
                compileJobRecord.getOrchestrationMode(),
                compileJobRecord.getStatus(),
                compileJobRecord.getPersistedCount(),
                compileJobRecord.getErrorMessage(),
                compileJobRecord.getAttemptCount(),
                compileJobRecord.getRequestedAt() == null ? null : compileJobRecord.getRequestedAt().toString(),
                compileJobRecord.getStartedAt() == null ? null : compileJobRecord.getStartedAt().toString(),
                compileJobRecord.getFinishedAt() == null ? null : compileJobRecord.getFinishedAt().toString()
        );
    }
}
