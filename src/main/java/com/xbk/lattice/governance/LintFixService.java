package com.xbk.lattice.governance;

import com.xbk.lattice.compiler.service.LatticePrompts;
import com.xbk.lattice.compiler.service.LlmGateway;
import com.xbk.lattice.infra.persistence.ArticleJdbcRepository;
import com.xbk.lattice.infra.persistence.ArticleRecord;
import com.xbk.lattice.infra.persistence.ArticleSnapshotJdbcRepository;
import com.xbk.lattice.infra.persistence.ArticleSnapshotRecord;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Lint 自动修复服务
 *
 * 职责：对可自动修复的 lint 问题执行最小 LLM 修复闭环
 *
 * @author xiexu
 */
@Service
@Profile("jdbc")
public class LintFixService {

    private final ArticleJdbcRepository articleJdbcRepository;

    private final ArticleSnapshotJdbcRepository articleSnapshotJdbcRepository;

    private final LlmGateway llmGateway;

    /**
     * 创建 Lint 自动修复服务。
     *
     * @param articleJdbcRepository 文章仓储
     * @param articleSnapshotJdbcRepository 快照仓储
     * @param llmGateway LLM 网关
     */
    public LintFixService(
            ArticleJdbcRepository articleJdbcRepository,
            ArticleSnapshotJdbcRepository articleSnapshotJdbcRepository,
            LlmGateway llmGateway
    ) {
        this.articleJdbcRepository = articleJdbcRepository;
        this.articleSnapshotJdbcRepository = articleSnapshotJdbcRepository;
        this.llmGateway = llmGateway;
    }

    /**
     * 修复全部可自动修复问题。
     *
     * @param report lint 报告
     * @return 修复结果
     */
    public LintFixResult fix(LintReport report) {
        return fix(report, null);
    }

    /**
     * 只修复指定 targetId 的可自动修复问题。
     *
     * @param report lint 报告
     * @param issueTargetIds 目标概念标识
     * @return 修复结果
     */
    @Transactional(rollbackFor = Exception.class)
    public LintFixResult fix(LintReport report, List<String> issueTargetIds) {
        if (report == null || report.getIssues() == null || report.getIssues().isEmpty()) {
            return new LintFixResult(0, 0, List.of());
        }
        Set<String> targetFilter = issueTargetIds == null ? null : new LinkedHashSet<String>(issueTargetIds);
        Map<String, List<LintIssue>> issuesByTarget = new LinkedHashMap<String, List<LintIssue>>();
        int skipped = 0;
        List<String> errors = new ArrayList<String>();

        for (LintIssue issue : report.getIssues()) {
            if (!issue.isFixable()) {
                skipped++;
                continue;
            }
            if (targetFilter != null && !targetFilter.contains(issue.getTargetId())) {
                skipped++;
                continue;
            }
            issuesByTarget.computeIfAbsent(issue.getTargetId(), ignored -> new ArrayList<LintIssue>()).add(issue);
        }

        int fixed = 0;
        for (Map.Entry<String, List<LintIssue>> entry : issuesByTarget.entrySet()) {
            Optional<ArticleRecord> optionalArticleRecord = articleJdbcRepository.findByConceptId(entry.getKey());
            if (optionalArticleRecord.isEmpty()) {
                skipped++;
                continue;
            }
            ArticleRecord articleRecord = optionalArticleRecord.orElseThrow();
            try {
                String fixedContent = llmGateway.compile(
                        "lint-fix",
                        LatticePrompts.SYSTEM_LINT_FIX,
                        buildPrompt(articleRecord, entry.getValue())
                );
                ArticleRecord updatedRecord = new ArticleRecord(
                        articleRecord.getConceptId(),
                        articleRecord.getTitle(),
                        fixedContent,
                        articleRecord.getLifecycle(),
                        articleRecord.getCompiledAt(),
                        articleRecord.getSourcePaths(),
                        articleRecord.getMetadataJson(),
                        articleRecord.getSummary(),
                        articleRecord.getReferentialKeywords(),
                        articleRecord.getDependsOn(),
                        articleRecord.getRelated(),
                        articleRecord.getConfidence(),
                        "needs_review"
                );
                articleJdbcRepository.upsert(updatedRecord);
                articleSnapshotJdbcRepository.save(new ArticleSnapshotRecord(
                        -1L,
                        updatedRecord.getConceptId(),
                        updatedRecord.getTitle(),
                        updatedRecord.getContent(),
                        updatedRecord.getLifecycle(),
                        updatedRecord.getCompiledAt(),
                        updatedRecord.getSourcePaths(),
                        updatedRecord.getMetadataJson(),
                        updatedRecord.getSummary(),
                        updatedRecord.getReferentialKeywords(),
                        updatedRecord.getDependsOn(),
                        updatedRecord.getRelated(),
                        updatedRecord.getConfidence(),
                        updatedRecord.getReviewStatus(),
                        "lint_fix",
                        OffsetDateTime.now()
                ));
                fixed++;
            }
            catch (Exception ex) {
                errors.add(entry.getKey());
            }
        }
        return new LintFixResult(fixed, skipped, errors);
    }

    private String buildPrompt(ArticleRecord articleRecord, List<LintIssue> issues) {
        StringBuilder builder = new StringBuilder();
        builder.append("概念ID：").append(articleRecord.getConceptId()).append("\n");
        builder.append("当前文章：\n").append(articleRecord.getContent()).append("\n\n");
        builder.append("待修复问题：\n");
        for (LintIssue issue : issues) {
            builder.append("- ").append(issue.getMessage()).append("\n");
            if (issue.getFixSuggestion() != null && !issue.getFixSuggestion().isBlank()) {
                builder.append("  建议：").append(issue.getFixSuggestion()).append("\n");
            }
        }
        return builder.toString();
    }
}
