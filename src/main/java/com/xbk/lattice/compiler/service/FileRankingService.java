package com.xbk.lattice.compiler.service;

import com.xbk.lattice.compiler.config.CompilerProperties;
import com.xbk.lattice.compiler.model.RawSource;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * 文件优先级排序服务
 *
 * 职责：为编译批次挑出高价值文件，优先让关键上下文进入前序批次
 *
 * @author xiexu
 */
public class FileRankingService {

    private final CompilerProperties compilerProperties;

    /**
     * 创建文件优先级排序服务。
     *
     * @param compilerProperties 编译配置
     */
    public FileRankingService(CompilerProperties compilerProperties) {
        this.compilerProperties = compilerProperties;
    }

    /**
     * 对源文件集合做优先级排序。
     *
     * @param sources 原始源文件集合
     * @return 排序后的新列表
     */
    public List<RawSource> rank(List<RawSource> sources) {
        List<RankedSource> rankedSources = new ArrayList<RankedSource>();
        for (RawSource source : sources) {
            rankedSources.add(new RankedSource(source, resolveScore(source)));
        }
        rankedSources.sort(Comparator
                .comparingInt(RankedSource::getScore).reversed()
                .thenComparing(rankedSource -> rankedSource.getSource().getRelativePath(), String.CASE_INSENSITIVE_ORDER)
                .thenComparing(rankedSource -> rankedSource.getSource().getRelativePath()));
        List<RawSource> orderedSources = new ArrayList<RawSource>();
        for (RankedSource rankedSource : rankedSources) {
            orderedSources.add(rankedSource.getSource());
        }
        return orderedSources;
    }

    private int resolveScore(RawSource source) {
        String fileName = extractFileName(source.getRelativePath()).toLowerCase(Locale.ROOT);
        CompilerProperties.FileRanking fileRanking = compilerProperties == null
                ? null
                : compilerProperties.getFileRanking();
        if (fileRanking != null && fileRanking.getRules() != null) {
            for (CompilerProperties.FileRankingRule rule : fileRanking.getRules()) {
                if (rule != null && matches(rule.getPattern(), fileName)) {
                    return rule.getScore();
                }
            }
        }
        return defaultScore(fileName, source.getContent());
    }

    private int defaultScore(String fileName, String content) {
        if (fileName.startsWith("overview.")) {
            return 100;
        }
        if (fileName.startsWith("readme.") || "claude.md".equals(fileName)) {
            return 95;
        }
        if (fileName.startsWith("architecture.")) {
            return 85;
        }
        if (fileName.startsWith("domain.")) {
            return 80;
        }
        if (fileName.startsWith("design.")) {
            return 75;
        }
        if (fileName.startsWith("api.")) {
            return 70;
        }
        if (fileName.startsWith("config.")) {
            return 60;
        }
        int lengthPenalty = content == null ? 0 : content.length() / 10000;
        return Math.max(10, 50 - lengthPenalty);
    }

    private boolean matches(String pattern, String fileName) {
        if (isBlank(pattern)) {
            return false;
        }
        String regex = pattern.toLowerCase(Locale.ROOT)
                .replace(".", "\\.")
                .replace("*", ".*");
        return fileName.matches(regex);
    }

    private String extractFileName(String relativePath) {
        if (isBlank(relativePath)) {
            return "";
        }
        int slashIndex = relativePath.lastIndexOf('/');
        if (slashIndex < 0) {
            return relativePath;
        }
        return relativePath.substring(slashIndex + 1);
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static final class RankedSource {

        private final RawSource source;

        private final int score;

        private RankedSource(RawSource source, int score) {
            this.source = source;
            this.score = score;
        }

        private RawSource getSource() {
            return source;
        }

        private int getScore() {
            return score;
        }
    }
}
