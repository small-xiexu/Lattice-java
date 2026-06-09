package com.xbk.lattice.api.admin;

import com.xbk.lattice.admin.service.AdminCompileArticleReviewQueueService;
import com.xbk.lattice.admin.service.CompileArticleReviewQueueActionRequest;
import com.xbk.lattice.admin.service.CompileArticleReviewQueueActionResult;
import com.xbk.lattice.infra.persistence.CompileArticleReviewQueueRecord;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

/**
 * 管理侧编译人工确认队列控制器
 *
 * 职责：暴露 needs_human_review 编译草稿列表、详情、发布与驳回接口
 *
 * @author xiexu
 */
@RestController
@RequestMapping("/api/v1/admin/compile/review-queue")
public class AdminCompileReviewQueueController {

    private final AdminCompileArticleReviewQueueService adminCompileArticleReviewQueueService;

    /**
     * 创建管理侧编译人工确认队列控制器。
     *
     * @param adminCompileArticleReviewQueueService 管理侧队列服务
     */
    public AdminCompileReviewQueueController(
            AdminCompileArticleReviewQueueService adminCompileArticleReviewQueueService
    ) {
        this.adminCompileArticleReviewQueueService = adminCompileArticleReviewQueueService;
    }

    /**
     * 查询人工确认队列。
     *
     * @param status 队列状态
     * @param limit 返回上限
     * @return 队列列表
     */
    @GetMapping
    public AdminCompileReviewQueueListResponse list(
            @RequestParam(required = false, defaultValue = "needs_human_review") String status,
            @RequestParam(required = false, defaultValue = "50") int limit
    ) {
        List<CompileArticleReviewQueueRecord> queueRecords = adminCompileArticleReviewQueueService.list(status, limit);
        List<AdminCompileReviewQueueItemResponse> items = new ArrayList<AdminCompileReviewQueueItemResponse>();
        for (CompileArticleReviewQueueRecord queueRecord : queueRecords) {
            items.add(toItemResponse(queueRecord));
        }
        return new AdminCompileReviewQueueListResponse(items.size(), items);
    }

    /**
     * 查询人工确认队列详情。
     *
     * @param id 队列主键
     * @return 队列详情
     */
    @GetMapping("/{id:\\d+}")
    public AdminCompileReviewQueueItemResponse get(@PathVariable long id) {
        return toItemResponse(adminCompileArticleReviewQueueService.get(id));
    }

    /**
     * 人工确认发布草稿。
     *
     * @param id 队列主键
     * @param request 动作请求
     * @return 动作响应
     */
    @PostMapping("/{id:\\d+}/approve")
    public AdminCompileReviewQueueActionResponse approve(
            @PathVariable long id,
            @RequestBody(required = false) AdminCompileReviewQueueActionRequest request
    ) {
        CompileArticleReviewQueueActionResult result = adminCompileArticleReviewQueueService.approve(
                id,
                toServiceRequest(request)
        );
        return toActionResponse(result);
    }

    /**
     * 人工驳回草稿。
     *
     * @param id 队列主键
     * @param request 动作请求
     * @return 动作响应
     */
    @PostMapping("/{id:\\d+}/reject")
    public AdminCompileReviewQueueActionResponse reject(
            @PathVariable long id,
            @RequestBody(required = false) AdminCompileReviewQueueActionRequest request
    ) {
        CompileArticleReviewQueueActionResult result = adminCompileArticleReviewQueueService.reject(
                id,
                toServiceRequest(request)
        );
        return toActionResponse(result);
    }

    private CompileArticleReviewQueueActionRequest toServiceRequest(
            AdminCompileReviewQueueActionRequest request
    ) {
        AdminCompileReviewQueueActionRequest safeRequest = request == null
                ? new AdminCompileReviewQueueActionRequest()
                : request;
        return new CompileArticleReviewQueueActionRequest(
                safeRequest.getReviewedBy(),
                safeRequest.getComment(),
                safeRequest.getExpectedReviewStatus()
        );
    }

    private AdminCompileReviewQueueActionResponse toActionResponse(
            CompileArticleReviewQueueActionResult result
    ) {
        return new AdminCompileReviewQueueActionResponse(
                toItemResponse(result.getQueueRecord()),
                result.getPreviousReviewStatus(),
                result.getAuditId()
        );
    }

    private AdminCompileReviewQueueItemResponse toItemResponse(
            CompileArticleReviewQueueRecord queueRecord
    ) {
        String createdAt = queueRecord.getCreatedAt() == null ? null : queueRecord.getCreatedAt().toString();
        String updatedAt = queueRecord.getUpdatedAt() == null ? null : queueRecord.getUpdatedAt().toString();
        String reviewedAt = queueRecord.getReviewedAt() == null ? null : queueRecord.getReviewedAt().toString();
        return new AdminCompileReviewQueueItemResponse(
                queueRecord.getId(),
                queueRecord.getJobId(),
                queueRecord.getSourceId(),
                queueRecord.getSourceCode(),
                queueRecord.getConceptId(),
                queueRecord.getArticleKey(),
                queueRecord.getTitle(),
                queueRecord.getContent(),
                queueRecord.getMetadataJson(),
                queueRecord.getReviewStatus(),
                queueRecord.getReviewRoute(),
                queueRecord.getReviewerModel(),
                queueRecord.getReviewIssuesJson(),
                queueRecord.getFixAttemptCount(),
                queueRecord.getMaxFixRounds(),
                queueRecord.getSourcePaths(),
                createdAt,
                updatedAt,
                queueRecord.getReviewedBy(),
                reviewedAt,
                queueRecord.getReviewComment(),
                queueRecord.getPublishedArticleKey()
        );
    }
}
