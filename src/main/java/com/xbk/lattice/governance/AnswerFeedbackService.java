package com.xbk.lattice.governance;

import com.xbk.lattice.infra.persistence.AnswerFeedbackAuditJdbcRepository;
import com.xbk.lattice.infra.persistence.AnswerFeedbackAuditRecord;
import com.xbk.lattice.infra.persistence.AnswerFeedbackJdbcRepository;
import com.xbk.lattice.infra.persistence.AnswerFeedbackRecord;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 答案反馈服务
 *
 * 职责：管理问答结果反馈创建、列表读取与处理审计
 *
 * @author xiexu
 */
@Service
public class AnswerFeedbackService {

    public static final String STATUS_PENDING = "PENDING";

    public static final String STATUS_RESOLVED = "RESOLVED";

    public static final String STATUS_DISMISSED = "DISMISSED";

    private static final Set<String> SUPPORTED_FEEDBACK_TYPES = Set.of(
            "reliable",
            "answer_problem",
            "source_conflict",
            "needs_manual_confirmation"
    );

    private static final int MAX_TEXT_LENGTH = 4000;

    private final AnswerFeedbackJdbcRepository answerFeedbackJdbcRepository;

    private final AnswerFeedbackAuditJdbcRepository answerFeedbackAuditJdbcRepository;

    /**
     * 创建答案反馈服务。
     *
     * @param answerFeedbackJdbcRepository 答案反馈仓储
     * @param answerFeedbackAuditJdbcRepository 答案反馈审计仓储
     */
    public AnswerFeedbackService(
            AnswerFeedbackJdbcRepository answerFeedbackJdbcRepository,
            AnswerFeedbackAuditJdbcRepository answerFeedbackAuditJdbcRepository
    ) {
        this.answerFeedbackJdbcRepository = answerFeedbackJdbcRepository;
        this.answerFeedbackAuditJdbcRepository = answerFeedbackAuditJdbcRepository;
    }

    /**
     * 创建答案反馈。
     *
     * @param request 答案反馈请求
     * @return 保存后的答案反馈
     */
    @Transactional(rollbackFor = Exception.class)
    public AnswerFeedbackRecord create(AnswerFeedbackRequest request) {
        OffsetDateTime now = OffsetDateTime.now();
        String feedbackType = normalizeFeedbackType(request == null ? null : request.getFeedbackType());
        AnswerFeedbackRecord feedbackRecord = new AnswerFeedbackRecord(
                0L,
                normalizeNullable(request == null ? null : request.getQueryId()),
                normalizeText(request == null ? null : request.getQuestion()),
                normalizeText(request == null ? null : request.getAnswerSummary()),
                feedbackType,
                normalizeText(request == null ? null : request.getComment()),
                normalizeDistinct(request == null ? List.of() : request.getArticleKeys()),
                normalizeDistinct(request == null ? List.of() : request.getSourcePaths()),
                normalizeActor(request == null ? null : request.getReportedBy()),
                STATUS_PENDING,
                "",
                null,
                null,
                now,
                now,
                "{}"
        );
        AnswerFeedbackRecord savedRecord = answerFeedbackJdbcRepository.save(feedbackRecord);
        answerFeedbackAuditJdbcRepository.save(new AnswerFeedbackAuditRecord(
                0L,
                savedRecord.getId(),
                "CREATE",
                null,
                STATUS_PENDING,
                savedRecord.getComment(),
                savedRecord.getReportedBy(),
                now,
                "{}"
        ));
        return savedRecord;
    }

    /**
     * 查询答案反馈列表。
     *
     * @param status 可选状态
     * @param limit 返回上限
     * @return 答案反馈列表
     */
    public List<AnswerFeedbackRecord> list(String status, int limit) {
        return answerFeedbackJdbcRepository.findAll(normalizeStatusFilter(status), limit);
    }

    /**
     * 查询答案反馈详情。
     *
     * @param id 反馈主键
     * @return 答案反馈详情
     */
    public AnswerFeedbackRecord get(long id) {
        return answerFeedbackJdbcRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("answer feedback not found: " + id));
    }

    /**
     * 查询答案反馈审计历史。
     *
     * @param id 反馈主键
     * @return 审计历史
     */
    public List<AnswerFeedbackAuditRecord> listAudits(long id) {
        get(id);
        return answerFeedbackAuditJdbcRepository.findByFeedbackId(id);
    }

    /**
     * 标记答案反馈已处理。
     *
     * @param id 反馈主键
     * @param request 处理请求
     * @return 更新后的答案反馈
     */
    @Transactional(rollbackFor = Exception.class)
    public AnswerFeedbackRecord resolve(long id, AnswerFeedbackHandleRequest request) {
        return updateStatus(id, STATUS_RESOLVED, "RESOLVE", request);
    }

    /**
     * 标记答案反馈已忽略。
     *
     * @param id 反馈主键
     * @param request 处理请求
     * @return 更新后的答案反馈
     */
    @Transactional(rollbackFor = Exception.class)
    public AnswerFeedbackRecord dismiss(long id, AnswerFeedbackHandleRequest request) {
        return updateStatus(id, STATUS_DISMISSED, "DISMISS", request);
    }

    /**
     * 统计待处理反馈数量。
     *
     * @return 待处理反馈数量
     */
    public int countPending() {
        return answerFeedbackJdbcRepository.countByStatus(STATUS_PENDING);
    }

    /**
     * 更新答案反馈状态。
     *
     * @param id 反馈主键
     * @param nextStatus 目标状态
     * @param action 审计动作
     * @param request 处理请求
     * @return 更新后的答案反馈
     */
    private AnswerFeedbackRecord updateStatus(
            long id,
            String nextStatus,
            String action,
            AnswerFeedbackHandleRequest request
    ) {
        AnswerFeedbackRecord currentRecord = get(id);
        if (!STATUS_PENDING.equalsIgnoreCase(currentRecord.getStatus())) {
            throw new IllegalStateException("answer feedback already handled: " + currentRecord.getStatus());
        }
        OffsetDateTime now = OffsetDateTime.now();
        String handledBy = normalizeActor(request == null ? null : request.getHandledBy());
        String comment = normalizeText(request == null ? null : request.getComment());
        AnswerFeedbackRecord updatedRecord = answerFeedbackJdbcRepository.updateStatus(
                id,
                nextStatus,
                comment,
                handledBy,
                now
        );
        answerFeedbackAuditJdbcRepository.save(new AnswerFeedbackAuditRecord(
                0L,
                id,
                action,
                currentRecord.getStatus(),
                nextStatus,
                comment,
                handledBy,
                now,
                "{}"
        ));
        return updatedRecord;
    }

    /**
     * 归一反馈类型。
     *
     * @param feedbackType 反馈类型
     * @return 归一后的反馈类型
     */
    private String normalizeFeedbackType(String feedbackType) {
        String normalized = normalizeNullable(feedbackType);
        if (normalized == null) {
            return "answer_problem";
        }
        String lowerCaseFeedbackType = normalized.toLowerCase(Locale.ROOT);
        if (!SUPPORTED_FEEDBACK_TYPES.contains(lowerCaseFeedbackType)) {
            return "answer_problem";
        }
        return lowerCaseFeedbackType;
    }

    /**
     * 归一状态筛选。
     *
     * @param status 状态筛选
     * @return 归一后的状态
     */
    private String normalizeStatusFilter(String status) {
        String normalized = normalizeNullable(status);
        if (normalized == null || "ALL".equalsIgnoreCase(normalized)) {
            return null;
        }
        String upperCaseStatus = normalized.toUpperCase(Locale.ROOT);
        if (!STATUS_PENDING.equals(upperCaseStatus)
                && !STATUS_RESOLVED.equals(upperCaseStatus)
                && !STATUS_DISMISSED.equals(upperCaseStatus)) {
            return null;
        }
        return upperCaseStatus;
    }

    /**
     * 归一操作者。
     *
     * @param actor 操作者
     * @return 归一后的操作者
     */
    private String normalizeActor(String actor) {
        String normalized = normalizeNullable(actor);
        if (normalized == null) {
            return "anonymous";
        }
        return normalized;
    }

    /**
     * 归一文本。
     *
     * @param value 原始文本
     * @return 归一后的文本
     */
    private String normalizeText(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String normalized = value.trim();
        if (normalized.length() <= MAX_TEXT_LENGTH) {
            return normalized;
        }
        return normalized.substring(0, MAX_TEXT_LENGTH);
    }

    /**
     * 归一可空字符串。
     *
     * @param value 原始字符串
     * @return 归一后的字符串
     */
    private String normalizeNullable(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    /**
     * 归一去重字符串列表。
     *
     * @param values 原始列表
     * @return 去重后的列表
     */
    private List<String> normalizeDistinct(List<String> values) {
        LinkedHashSet<String> uniqueValues = new LinkedHashSet<String>();
        for (String value : values) {
            String normalized = normalizeNullable(value);
            if (normalized == null) {
                continue;
            }
            uniqueValues.add(normalized);
        }
        return new ArrayList<String>(uniqueValues);
    }
}
