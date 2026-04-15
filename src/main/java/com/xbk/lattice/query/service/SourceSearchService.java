package com.xbk.lattice.query.service;

import com.xbk.lattice.infra.persistence.SourceFileChunkJdbcRepository;
import com.xbk.lattice.infra.persistence.SourceFileChunkRecord;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * 源文件检索服务
 *
 * 职责：基于 source_file_chunks 提供源文件层查询能力
 *
 * @author xiexu
 */
@Service
@Profile("jdbc")
public class SourceSearchService {

    private final SourceFileChunkJdbcRepository sourceFileChunkJdbcRepository;

    /**
     * 创建源文件检索服务。
     *
     * @param sourceFileChunkJdbcRepository 源文件分块仓储
     */
    public SourceSearchService(SourceFileChunkJdbcRepository sourceFileChunkJdbcRepository) {
        this.sourceFileChunkJdbcRepository = sourceFileChunkJdbcRepository;
    }

    /**
     * 执行源文件层检索。
     *
     * @param question 查询问题
     * @param limit 返回数量
     * @return 命中列表
     */
    public List<QueryArticleHit> search(String question, int limit) {
        if (sourceFileChunkJdbcRepository == null) {
            return List.of();
        }

        List<String> queryTokens = QueryTokenExtractor.extract(question);
        if (queryTokens.isEmpty()) {
            return List.of();
        }

        List<QueryArticleHit> matchedHits = new ArrayList<QueryArticleHit>();
        for (SourceFileChunkRecord sourceFileChunkRecord : sourceFileChunkJdbcRepository.findAll()) {
            double score = scoreChunk(sourceFileChunkRecord, queryTokens);
            if (score <= 0) {
                continue;
            }
            matchedHits.add(new QueryArticleHit(
                    QueryEvidenceType.SOURCE,
                    sourceFileChunkRecord.getFilePath() + "#" + sourceFileChunkRecord.getChunkIndex(),
                    sourceFileChunkRecord.getFilePath(),
                    sourceFileChunkRecord.getChunkText(),
                    buildMetadataJson(sourceFileChunkRecord),
                    List.of(sourceFileChunkRecord.getFilePath()),
                    score
            ));
        }
        matchedHits.sort(Comparator.comparing(QueryArticleHit::getScore).reversed()
                .thenComparing(QueryArticleHit::getConceptId));
        if (matchedHits.size() <= limit) {
            return matchedHits;
        }
        return matchedHits.subList(0, limit);
    }

    /**
     * 计算分块命中分数。
     *
     * @param sourceFileChunkRecord 源文件分块
     * @param queryTokens 查询 token
     * @return 命中分数
     */
    private double scoreChunk(SourceFileChunkRecord sourceFileChunkRecord, List<String> queryTokens) {
        String filePath = sourceFileChunkRecord.getFilePath().toLowerCase(Locale.ROOT);
        String chunkText = sourceFileChunkRecord.getChunkText().toLowerCase(Locale.ROOT);
        double score = sourceFileChunkRecord.isVerbatim() ? 0.5D : 0.0D;
        for (String queryToken : queryTokens) {
            if (filePath.contains(queryToken)) {
                score += 1.0D;
            }
            if (chunkText.contains(queryToken)) {
                score += 3.0D;
            }
        }
        return score;
    }

    /**
     * 构建源文件元数据 JSON。
     *
     * @param sourceFileChunkRecord 分块记录
     * @return 元数据 JSON
     */
    private String buildMetadataJson(SourceFileChunkRecord sourceFileChunkRecord) {
        return "{\"filePath\":\""
                + sourceFileChunkRecord.getFilePath()
                + "\",\"chunkIndex\":"
                + sourceFileChunkRecord.getChunkIndex()
                + ",\"verbatim\":"
                + sourceFileChunkRecord.isVerbatim()
                + "}";
    }
}
