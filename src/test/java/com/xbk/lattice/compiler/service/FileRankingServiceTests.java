package com.xbk.lattice.compiler.service;

import com.xbk.lattice.compiler.config.CompilerProperties;
import com.xbk.lattice.compiler.domain.RawSource;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * FileRankingService 测试
 *
 * 职责：验证关键文件优先级排序与自定义规则覆盖行为
 *
 * @author xiexu
 */
class FileRankingServiceTests {

    /**
     * 验证 overview/readme/claude/architecture 会排在普通大文件之前。
     */
    @Test
    void shouldRankKeyFilesAheadOfOrdinaryLargeFiles() {
        FileRankingService fileRankingService = new FileRankingService(new CompilerProperties());
        List<RawSource> ranked = fileRankingService.rank(List.of(
                RawSource.text("docs/huge-spec.md", "x".repeat(120_000), "md", 120_000L),
                RawSource.text("docs/architecture.md", "architecture", "md", 12L),
                RawSource.text("README.md", "readme", "md", 6L),
                RawSource.text("CLAUDE.md", "claude", "md", 6L),
                RawSource.text("overview.md", "overview", "md", 8L)
        ));

        List<String> rankedPaths = ranked.stream().map(RawSource::getRelativePath).toList();
        assertThat(rankedPaths.get(0)).isEqualTo("overview.md");
        assertThat(rankedPaths.indexOf("README.md")).isLessThan(rankedPaths.indexOf("docs/huge-spec.md"));
        assertThat(rankedPaths.indexOf("CLAUDE.md")).isLessThan(rankedPaths.indexOf("docs/huge-spec.md"));
        assertThat(rankedPaths.indexOf("docs/architecture.md")).isLessThan(rankedPaths.indexOf("docs/huge-spec.md"));
        assertThat(rankedPaths.get(rankedPaths.size() - 1)).isEqualTo("docs/huge-spec.md");
    }

    /**
     * 验证自定义规则会覆盖默认优先级。
     */
    @Test
    void shouldOverrideDefaultScoresWithCustomRules() {
        CompilerProperties compilerProperties = new CompilerProperties();
        CompilerProperties.FileRankingRule fileRankingRule = new CompilerProperties.FileRankingRule();
        fileRankingRule.setPattern("notes.*");
        fileRankingRule.setScore(120);
        CompilerProperties.FileRanking fileRanking = new CompilerProperties.FileRanking();
        fileRanking.setRules(List.of(fileRankingRule));
        compilerProperties.setFileRanking(fileRanking);
        FileRankingService fileRankingService = new FileRankingService(compilerProperties);

        List<RawSource> ranked = fileRankingService.rank(List.of(
                RawSource.text("overview.md", "overview", "md", 8L),
                RawSource.text("notes.md", "notes", "md", 5L)
        ));

        assertThat(ranked)
                .extracting(RawSource::getRelativePath)
                .containsExactly("notes.md", "overview.md");
    }
}
