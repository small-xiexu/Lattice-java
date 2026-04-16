package com.xbk.lattice.governance;

import com.xbk.lattice.infra.persistence.ArticleJdbcRepository;
import com.xbk.lattice.infra.persistence.ArticleRecord;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Lint 服务
 *
 * 职责：提供 B7 首批可落地的最小 6 维治理检查
 *
 * @author xiexu
 */
@Service
@Profile("jdbc")
public class LintService {

    private static final List<String> CHECKED_DIMENSIONS = List.of(
            "consistency",
            "gaps",
            "freshness",
            "propagation",
            "grounding",
            "referential"
    );

    private final ArticleJdbcRepository articleJdbcRepository;

    /**
     * 创建 Lint 服务。
     *
     * @param articleJdbcRepository 文章仓储
     */
    public LintService(ArticleJdbcRepository articleJdbcRepository) {
        this.articleJdbcRepository = articleJdbcRepository;
    }

    /**
     * 执行最小治理检查。
     *
     * @return Lint 报告
     */
    public LintReport lint() {
        List<ArticleRecord> articleRecords = articleJdbcRepository.findAll();
        List<LintIssue> issues = new ArrayList<LintIssue>();
        Set<String> conceptIds = new LinkedHashSet<String>();
        Map<String, Integer> titleCounts = new LinkedHashMap<String, Integer>();

        for (ArticleRecord articleRecord : articleRecords) {
            conceptIds.add(articleRecord.getConceptId());
            Integer count = titleCounts.get(articleRecord.getTitle());
            titleCounts.put(articleRecord.getTitle(), count == null ? 1 : count + 1);
        }

        for (ArticleRecord articleRecord : articleRecords) {
            if (titleCounts.get(articleRecord.getTitle()) != null && titleCounts.get(articleRecord.getTitle()) > 1) {
                issues.add(new LintIssue(
                        "consistency",
                        articleRecord.getConceptId(),
                        "标题重复，可能存在概念重叠",
                        true,
                        "与重复标题的文章合并，或为其中一篇修改标题"
                ));
            }
            if (articleRecord.getSummary() == null || articleRecord.getSummary().trim().isEmpty()) {
                issues.add(new LintIssue(
                        "gaps",
                        articleRecord.getConceptId(),
                        "缺少 summary",
                        true,
                        "根据文章正文首段生成 2-3 句摘要，写入 YAML frontmatter"
                ));
            }
            if (!"active".equalsIgnoreCase(articleRecord.getLifecycle())) {
                issues.add(new LintIssue("freshness", articleRecord.getConceptId(), "生命周期不是 active"));
            }
            if (articleRecord.getSourcePaths() == null || articleRecord.getSourcePaths().isEmpty()) {
                issues.add(new LintIssue("grounding", articleRecord.getConceptId(), "缺少 source_paths"));
            }
            if (articleRecord.getReferentialKeywords() == null || articleRecord.getReferentialKeywords().isEmpty()) {
                issues.add(new LintIssue(
                        "referential",
                        articleRecord.getConceptId(),
                        "缺少 referential_keywords",
                        true,
                        "从文章正文提取业务码、端口、枚举值等明确性知识，写入 YAML frontmatter"
                ));
            }
            if ("needs_human_review".equalsIgnoreCase(articleRecord.getReviewStatus())) {
                issues.add(new LintIssue("consistency", articleRecord.getConceptId(), "文章仍处于 needs_human_review"));
            }
            for (String dependency : articleRecord.getDependsOn()) {
                if (!conceptIds.contains(dependency)) {
                    issues.add(new LintIssue("propagation", articleRecord.getConceptId(), "依赖概念缺失: " + dependency));
                }
            }
        }
        return new LintReport(CHECKED_DIMENSIONS, issues);
    }
}
