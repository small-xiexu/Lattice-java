package com.xbk.lattice.governance;

import com.xbk.lattice.infra.persistence.ArticleJdbcRepository;
import com.xbk.lattice.infra.persistence.ArticleRecord;
import com.xbk.lattice.infra.persistence.SourceFileJdbcRepository;
import com.xbk.lattice.infra.persistence.SourceFileRecord;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * 覆盖率跟踪服务
 *
 * 职责：基于文章 source_paths 统计当前知识库的源文件覆盖率
 *
 * @author xiexu
 */
@Service
@Profile("jdbc")
public class CoverageTrackingService {

    private final SourceFileJdbcRepository sourceFileJdbcRepository;

    private final ArticleJdbcRepository articleJdbcRepository;

    /**
     * 创建覆盖率跟踪服务。
     *
     * @param sourceFileJdbcRepository 源文件仓储
     * @param articleJdbcRepository 文章仓储
     */
    public CoverageTrackingService(
            SourceFileJdbcRepository sourceFileJdbcRepository,
            ArticleJdbcRepository articleJdbcRepository
    ) {
        this.sourceFileJdbcRepository = sourceFileJdbcRepository;
        this.articleJdbcRepository = articleJdbcRepository;
    }

    /**
     * 生成覆盖率报告。
     *
     * @return 覆盖率报告
     */
    public CoverageReport measure() {
        Set<String> knownSourcePaths = collectKnownSourcePaths(sourceFileJdbcRepository.findAll());
        Set<String> coveredSourcePaths = collectCoveredSourcePaths(articleJdbcRepository.findAll(), knownSourcePaths);
        int totalSourceFileCount = knownSourcePaths.size();
        int coveredSourceFileCount = coveredSourcePaths.size();
        int uncoveredSourceFileCount = totalSourceFileCount - coveredSourceFileCount;
        double coverageRatio = totalSourceFileCount == 0
                ? 0D
                : (double) coveredSourceFileCount / (double) totalSourceFileCount;

        return new CoverageReport(
                totalSourceFileCount,
                coveredSourceFileCount,
                uncoveredSourceFileCount,
                coverageRatio,
                new ArrayList<String>(coveredSourcePaths)
        );
    }

    /**
     * 汇总已知源文件路径。
     *
     * @param sourceFileRecords 源文件记录列表
     * @return 已知源文件路径集合
     */
    private Set<String> collectKnownSourcePaths(List<SourceFileRecord> sourceFileRecords) {
        Set<String> knownSourcePaths = new TreeSet<String>();
        for (SourceFileRecord sourceFileRecord : sourceFileRecords) {
            String normalizedSourcePath = normalizeSourcePath(sourceFileRecord.getFilePath());
            if (!normalizedSourcePath.isEmpty()) {
                knownSourcePaths.add(normalizedSourcePath);
            }
        }
        return knownSourcePaths;
    }

    /**
     * 汇总已被文章引用的源文件路径。
     *
     * @param articleRecords 文章记录列表
     * @param knownSourcePaths 已知源文件路径集合
     * @return 已覆盖源文件路径集合
     */
    private Set<String> collectCoveredSourcePaths(
            List<ArticleRecord> articleRecords,
            Set<String> knownSourcePaths
    ) {
        Set<String> coveredSourcePaths = new TreeSet<String>();
        for (ArticleRecord articleRecord : articleRecords) {
            for (String sourcePath : articleRecord.getSourcePaths()) {
                String normalizedSourcePath = normalizeSourcePath(sourcePath);
                if (knownSourcePaths.contains(normalizedSourcePath)) {
                    coveredSourcePaths.add(normalizedSourcePath);
                }
            }
        }
        return coveredSourcePaths;
    }

    /**
     * 规范化源文件路径。
     *
     * @param sourcePath 原始源文件路径
     * @return 规范化后的源文件路径
     */
    private String normalizeSourcePath(String sourcePath) {
        if (sourcePath == null) {
            return "";
        }

        String normalizedSourcePath = sourcePath.trim().replace('\\', '/');
        int fragmentSeparator = normalizedSourcePath.indexOf('#');
        if (fragmentSeparator >= 0) {
            normalizedSourcePath = normalizedSourcePath.substring(0, fragmentSeparator);
        }
        return normalizedSourcePath.trim();
    }
}
