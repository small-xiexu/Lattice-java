package com.xbk.lattice.api.admin;

import com.xbk.lattice.governance.AnswerFeedbackHandleRequest;
import com.xbk.lattice.governance.AnswerFeedbackRequest;
import com.xbk.lattice.governance.AnswerFeedbackService;
import com.xbk.lattice.infra.persistence.AnswerFeedbackAuditRecord;
import com.xbk.lattice.infra.persistence.AnswerFeedbackRecord;
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
 * 管理侧答案反馈控制器
 *
 * 职责：暴露问答结果反馈创建、列表、详情和处理接口
 *
 * @author xiexu
 */
@RestController
@RequestMapping("/api/v1/admin/query-feedback")
public class AdminQueryFeedbackController {

    private final AnswerFeedbackService answerFeedbackService;

    /**
     * 创建管理侧答案反馈控制器。
     *
     * @param answerFeedbackService 答案反馈服务
     */
    public AdminQueryFeedbackController(AnswerFeedbackService answerFeedbackService) {
        this.answerFeedbackService = answerFeedbackService;
    }

    /**
     * 创建问答结果反馈。
     *
     * @param request 创建请求
     * @return 反馈响应
     */
    @PostMapping
    public AdminQueryFeedbackResponse create(@RequestBody AdminQueryFeedbackCreateRequest request) {
        AnswerFeedbackRecord feedbackRecord = answerFeedbackService.create(toServiceRequest(request));
        return toResponse(feedbackRecord);
    }

    /**
     * 查询问答结果反馈列表。
     *
     * @param status 状态筛选
     * @param limit 返回上限
     * @return 反馈列表
     */
    @GetMapping
    public AdminQueryFeedbackListResponse list(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "50") int limit
    ) {
        List<AnswerFeedbackRecord> feedbackRecords = answerFeedbackService.list(status, limit);
        List<AdminQueryFeedbackResponse> items = new ArrayList<AdminQueryFeedbackResponse>();
        for (AnswerFeedbackRecord feedbackRecord : feedbackRecords) {
            items.add(toResponse(feedbackRecord));
        }
        return new AdminQueryFeedbackListResponse(items.size(), items);
    }

    /**
     * 查询问答结果反馈详情。
     *
     * @param feedbackId 反馈主键
     * @return 反馈详情
     */
    @GetMapping("/{feedbackId}")
    public AdminQueryFeedbackDetailResponse detail(@PathVariable long feedbackId) {
        AnswerFeedbackRecord feedbackRecord = answerFeedbackService.get(feedbackId);
        List<AnswerFeedbackAuditRecord> auditRecords = answerFeedbackService.listAudits(feedbackId);
        List<AdminQueryFeedbackAuditResponse> auditResponses = new ArrayList<AdminQueryFeedbackAuditResponse>();
        for (AnswerFeedbackAuditRecord auditRecord : auditRecords) {
            auditResponses.add(toAuditResponse(auditRecord));
        }
        return new AdminQueryFeedbackDetailResponse(toResponse(feedbackRecord), auditResponses);
    }

    /**
     * 标记反馈已处理。
     *
     * @param feedbackId 反馈主键
     * @param request 处理请求
     * @return 更新后的反馈
     */
    @PostMapping("/{feedbackId}/resolve")
    public AdminQueryFeedbackResponse resolve(
            @PathVariable long feedbackId,
            @RequestBody AdminQueryFeedbackHandleRequest request
    ) {
        AnswerFeedbackRecord feedbackRecord = answerFeedbackService.resolve(feedbackId, toHandleRequest(request));
        return toResponse(feedbackRecord);
    }

    /**
     * 标记反馈已忽略。
     *
     * @param feedbackId 反馈主键
     * @param request 处理请求
     * @return 更新后的反馈
     */
    @PostMapping("/{feedbackId}/dismiss")
    public AdminQueryFeedbackResponse dismiss(
            @PathVariable long feedbackId,
            @RequestBody AdminQueryFeedbackHandleRequest request
    ) {
        AnswerFeedbackRecord feedbackRecord = answerFeedbackService.dismiss(feedbackId, toHandleRequest(request));
        return toResponse(feedbackRecord);
    }

    /**
     * 转换创建请求。
     *
     * @param request API 请求
     * @return 服务请求
     */
    private AnswerFeedbackRequest toServiceRequest(AdminQueryFeedbackCreateRequest request) {
        AdminQueryFeedbackCreateRequest safeRequest = request == null
                ? new AdminQueryFeedbackCreateRequest()
                : request;
        return new AnswerFeedbackRequest(
                safeRequest.getQueryId(),
                safeRequest.getQuestion(),
                safeRequest.getAnswerSummary(),
                safeRequest.getFeedbackType(),
                safeRequest.getComment(),
                safeRequest.getArticleKeys(),
                safeRequest.getSourcePaths(),
                safeRequest.getReportedBy()
        );
    }

    /**
     * 转换处理请求。
     *
     * @param request API 请求
     * @return 服务处理请求
     */
    private AnswerFeedbackHandleRequest toHandleRequest(AdminQueryFeedbackHandleRequest request) {
        AdminQueryFeedbackHandleRequest safeRequest = request == null
                ? new AdminQueryFeedbackHandleRequest()
                : request;
        return new AnswerFeedbackHandleRequest(safeRequest.getHandledBy(), safeRequest.getComment());
    }

    /**
     * 转换反馈响应。
     *
     * @param feedbackRecord 反馈记录
     * @return API 响应
     */
    private AdminQueryFeedbackResponse toResponse(AnswerFeedbackRecord feedbackRecord) {
        return new AdminQueryFeedbackResponse(
                feedbackRecord.getId(),
                feedbackRecord.getQueryId(),
                feedbackRecord.getQuestion(),
                feedbackRecord.getAnswerSummary(),
                feedbackRecord.getFeedbackType(),
                feedbackRecord.getComment(),
                feedbackRecord.getArticleKeys(),
                feedbackRecord.getSourcePaths(),
                feedbackRecord.getReportedBy(),
                feedbackRecord.getStatus(),
                feedbackRecord.getResolutionComment(),
                feedbackRecord.getHandledBy(),
                feedbackRecord.getHandledAt() == null ? null : feedbackRecord.getHandledAt().toString(),
                feedbackRecord.getCreatedAt() == null ? null : feedbackRecord.getCreatedAt().toString(),
                feedbackRecord.getUpdatedAt() == null ? null : feedbackRecord.getUpdatedAt().toString()
        );
    }

    /**
     * 转换审计响应。
     *
     * @param auditRecord 审计记录
     * @return API 响应
     */
    private AdminQueryFeedbackAuditResponse toAuditResponse(AnswerFeedbackAuditRecord auditRecord) {
        return new AdminQueryFeedbackAuditResponse(
                auditRecord.getId(),
                auditRecord.getFeedbackId(),
                auditRecord.getAction(),
                auditRecord.getPreviousStatus(),
                auditRecord.getNextStatus(),
                auditRecord.getComment(),
                auditRecord.getOperatedBy(),
                auditRecord.getOperatedAt() == null ? null : auditRecord.getOperatedAt().toString(),
                auditRecord.getMetadataJson()
        );
    }
}
