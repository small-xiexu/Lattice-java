package com.xbk.lattice.api.admin;

import com.xbk.lattice.admin.service.AdminUploadWorkspaceService;
import com.xbk.lattice.source.domain.SourceSyncRunDetail;
import com.xbk.lattice.source.service.SourceUploadService;
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
import java.util.List;

/**
 * 统一上传与资料源同步控制器。
 *
 * 职责：暴露 uploads、source-runs 轮询与 WAIT_CONFIRM 人工确认接口
 *
 * @author xiexu
 */
@RestController
@RequestMapping("/api/v1/admin")
public class AdminUploadController {

    private final AdminUploadWorkspaceService adminUploadWorkspaceService;

    private final SourceUploadService sourceUploadService;

    /**
     * 创建统一上传控制器。
     *
     * @param adminUploadWorkspaceService 上传暂存服务
     * @param sourceUploadService 统一上传服务
     */
    public AdminUploadController(
            AdminUploadWorkspaceService adminUploadWorkspaceService,
            SourceUploadService sourceUploadService
    ) {
        this.adminUploadWorkspaceService = adminUploadWorkspaceService;
        this.sourceUploadService = sourceUploadService;
    }

    /**
     * 接收统一上传请求。
     *
     * @param files 上传文件
     * @param sourceId 可选的目标资料源主键
     * @return 同步运行详情
     * @throws IOException IO 异常
     */
    @PostMapping(path = "/uploads", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public SourceSyncRunDetail upload(
            @RequestParam("files") MultipartFile[] files,
            @RequestParam(required = false) Long sourceId
    ) throws IOException {
        Path stagingDir = adminUploadWorkspaceService.save(files);
        return sourceUploadService.acceptUpload(stagingDir, sourceId);
    }

    /**
     * 查询单次同步运行详情。
     *
     * @param runId 运行主键
     * @return 同步运行详情
     */
    @GetMapping("/source-runs/{runId}")
    public SourceSyncRunDetail getRun(@PathVariable Long runId) {
        return sourceUploadService.getRunDetail(runId);
    }

    /**
     * 查询最近同步运行列表。
     *
     * @param limit 返回数量
     * @return 最近同步运行
     */
    @GetMapping("/source-runs")
    public List<SourceSyncRunDetail> listRecentRuns(@RequestParam(defaultValue = "10") Integer limit) {
        int resolvedLimit = limit == null ? 10 : Math.max(1, Math.min(limit.intValue(), 50));
        return sourceUploadService.listRecentRunDetails(resolvedLimit);
    }

    /**
     * 对 WAIT_CONFIRM 运行执行人工确认。
     *
     * @param runId 运行主键
     * @param request 人工确认请求
     * @return 同步运行详情
     */
    @PostMapping("/source-runs/{runId}/confirm")
    public SourceSyncRunDetail confirmRun(
            @PathVariable Long runId,
            @RequestBody AdminSourceRunConfirmRequest request
    ) {
        return sourceUploadService.confirmRun(runId, request.getDecision(), request.getSourceId());
    }

    /**
     * 重试失败的上传型同步运行。
     *
     * @param runId 运行主键
     * @return 重试后的同步运行详情
     */
    @PostMapping("/source-runs/{runId}/retry")
    public SourceSyncRunDetail retryRun(@PathVariable Long runId) {
        return sourceUploadService.retryRun(runId);
    }

    /**
     * 查询资料源同步历史。
     *
     * @param sourceId 资料源主键
     * @return 同步历史
     */
    @GetMapping("/sources/{sourceId}/runs")
    public List<SourceSyncRunDetail> listRuns(@PathVariable Long sourceId) {
        return sourceUploadService.listRunDetails(sourceId);
    }

    /**
     * 查询资料源下的单次同步详情。
     *
     * @param sourceId 资料源主键
     * @param runId 运行主键
     * @return 同步运行详情
     */
    @GetMapping("/sources/{sourceId}/runs/{runId}")
    public SourceSyncRunDetail getSourceRun(
            @PathVariable Long sourceId,
            @PathVariable Long runId
    ) {
        SourceSyncRunDetail detail = sourceUploadService.getRunDetail(runId);
        if (detail.getSourceId() == null || !sourceId.equals(detail.getSourceId())) {
            throw new IllegalArgumentException("run does not belong to source: " + runId);
        }
        return detail;
    }
}
