package com.xbk.lattice.query.citation;

import com.xbk.lattice.infra.persistence.QueryAnswerAuditJdbcRepository;
import com.xbk.lattice.infra.persistence.QueryAnswerAuditRecord;
import com.xbk.lattice.infra.persistence.QueryAnswerCitationJdbcRepository;
import com.xbk.lattice.infra.persistence.QueryAnswerCitationRecord;
import com.xbk.lattice.infra.persistence.QueryAnswerClaimJdbcRepository;
import com.xbk.lattice.infra.persistence.QueryAnswerClaimRecord;
import com.xbk.lattice.query.domain.AnswerOutcome;
import com.xbk.lattice.query.domain.GenerationMode;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 查询答案审计持久化服务
 *
 * 职责：把最终答案、claim 与引用核验结果一次性写入审计表
 *
 * @author xiexu
 */
@Service
@Profile("jdbc")
public class QueryAnswerAuditPersistenceService {

    private final QueryAnswerAuditJdbcRepository queryAnswerAuditJdbcRepository;

    private final QueryAnswerClaimJdbcRepository queryAnswerClaimJdbcRepository;

    private final QueryAnswerCitationJdbcRepository queryAnswerCitationJdbcRepository;

    /**
     * 创建查询答案审计持久化服务。
     *
     * @param queryAnswerAuditJdbcRepository 审计主表仓储
     * @param queryAnswerClaimJdbcRepository claim 仓储
     * @param queryAnswerCitationJdbcRepository 引用仓储
     */
    public QueryAnswerAuditPersistenceService(
            QueryAnswerAuditJdbcRepository queryAnswerAuditJdbcRepository,
            QueryAnswerClaimJdbcRepository queryAnswerClaimJdbcRepository,
            QueryAnswerCitationJdbcRepository queryAnswerCitationJdbcRepository
    ) {
        this.queryAnswerAuditJdbcRepository = queryAnswerAuditJdbcRepository;
        this.queryAnswerClaimJdbcRepository = queryAnswerClaimJdbcRepository;
        this.queryAnswerCitationJdbcRepository = queryAnswerCitationJdbcRepository;
    }

    /**
     * 写入最终答案审计。
     *
     * @param queryId 查询标识
     * @param answerVersion 答案版本号
     * @param question 问题
     * @param answerMarkdown 答案 Markdown
     * @param answerOutcome 答案语义
     * @param generationMode 生成模式
     * @param reviewStatus 审查状态
     * @param cacheable 是否可缓存
     * @param routeType 路由类型
     * @param report Citation 检查报告
     * @return 审计快照
     */
    @Transactional(rollbackFor = Exception.class)
    public QueryAnswerAuditSnapshot persist(
            String queryId,
            int answerVersion,
            String question,
            String answerMarkdown,
            AnswerOutcome answerOutcome,
            GenerationMode generationMode,
            String reviewStatus,
            boolean cacheable,
            String routeType,
            CitationCheckReport report
    ) {
        return persist(
                queryId,
                answerVersion,
                question,
                answerMarkdown,
                answerOutcome,
                generationMode,
                reviewStatus,
                cacheable,
                routeType,
                report,
                null
        );
    }

    /**
     * 写入最终答案审计。
     *
     * @param queryId 查询标识
     * @param answerVersion 答案版本号
     * @param question 问题
     * @param answerMarkdown 答案 Markdown
     * @param answerOutcome 答案语义
     * @param generationMode 生成模式
     * @param reviewStatus 审查状态
     * @param cacheable 是否可缓存
     * @param routeType 路由类型
     * @param report Citation 检查报告
     * @param deepResearchRunId 所属 Deep Research run 主键
     * @return 审计快照
     */
    public QueryAnswerAuditSnapshot persist(
            String queryId,
            int answerVersion,
            String question,
            String answerMarkdown,
            AnswerOutcome answerOutcome,
            GenerationMode generationMode,
            String reviewStatus,
            boolean cacheable,
            String routeType,
            CitationCheckReport report,
            Long deepResearchRunId
    ) {
        CitationCheckReport effectiveReport = report == null
                ? new CitationCheckReport(answerMarkdown, List.of(), List.of(), 0, 0, 0, true, 0.0D, 0, 0, 0, 0)
                : report;
        Long auditId = queryAnswerAuditJdbcRepository.insert(new QueryAnswerAuditRecord(
                queryId,
                answerVersion,
                question,
                answerMarkdown,
                answerOutcome == null ? null : answerOutcome.name(),
                generationMode == null ? null : generationMode.name(),
                reviewStatus,
                effectiveReport.getCoverageRate(),
                effectiveReport.getUnsupportedClaimCount(),
                effectiveReport.getVerifiedCount(),
                effectiveReport.getDemotedCount(),
                effectiveReport.getSkippedCount(),
                cacheable,
                routeType,
                "{}",
                deepResearchRunId
        ));
        Map<Integer, Long> claimIdsByIndex = new LinkedHashMap<Integer, Long>();
        for (ClaimSegment claimSegment : effectiveReport.getClaimSegments()) {
            String claimStatus = resolveClaimStatus(claimSegment, effectiveReport);
            Long claimId = queryAnswerClaimJdbcRepository.insert(new QueryAnswerClaimRecord(
                    auditId,
                    claimSegment.getClaimIndex(),
                    claimSegment.getClaimText(),
                    claimStatus,
                    claimSegment.getCitations().size()
            ));
            claimIdsByIndex.put(Integer.valueOf(claimSegment.getClaimIndex()), claimId);
        }
        for (ClaimSegment claimSegment : effectiveReport.getClaimSegments()) {
            Long claimId = claimIdsByIndex.get(Integer.valueOf(claimSegment.getClaimIndex()));
            for (Citation citation : claimSegment.getCitations()) {
                CitationValidationResult validationResult = resolveValidationResult(effectiveReport, citation.getOrdinal());
                queryAnswerCitationJdbcRepository.insert(new QueryAnswerCitationRecord(
                        auditId,
                        claimId,
                        citation.getOrdinal(),
                        citation.getLiteral(),
                        citation.getSourceType().name(),
                        citation.getTargetKey(),
                        validationResult == null ? CitationValidationStatus.DEMOTED.name() : validationResult.getStatus().name(),
                        validationResult == null ? CitationValidationSource.RULE.name() : validationResult.getValidatedBy().name(),
                        validationResult == null ? 0.0D : validationResult.getOverlapScore(),
                        validationResult == null ? "" : validationResult.getMatchedExcerpt(),
                        validationResult == null ? "missing_validation_result" : validationResult.getReason()
                ));
            }
        }
        return new QueryAnswerAuditSnapshot(auditId, answerVersion, effectiveReport);
    }

    private CitationValidationResult resolveValidationResult(CitationCheckReport report, int ordinal) {
        for (CitationValidationResult result : report.getResults()) {
            if (result.getOrdinal() == ordinal) {
                return result;
            }
        }
        return null;
    }

    private String resolveClaimStatus(ClaimSegment claimSegment, CitationCheckReport report) {
        if (claimSegment.getCitations().isEmpty()) {
            return ClaimStatus.NO_CITATION.name();
        }
        boolean verified = false;
        boolean covered = false;
        for (Citation citation : claimSegment.getCitations()) {
            CitationValidationResult validationResult = resolveValidationResult(report, citation.getOrdinal());
            if (validationResult == null) {
                continue;
            }
            if (validationResult.isVerified()) {
                verified = true;
                covered = true;
            }
            else if (validationResult.isSkipped()) {
                covered = true;
            }
        }
        if (verified) {
            return ClaimStatus.VERIFIED.name();
        }
        if (covered) {
            return ClaimStatus.PARTIAL.name();
        }
        return ClaimStatus.UNSUPPORTED.name();
    }
}
