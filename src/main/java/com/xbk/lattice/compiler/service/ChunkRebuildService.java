package com.xbk.lattice.compiler.service;

import com.xbk.lattice.infra.persistence.ArticleChunkJdbcRepository;
import com.xbk.lattice.infra.persistence.ArticleJdbcRepository;
import com.xbk.lattice.infra.persistence.ArticleRecord;
import com.xbk.lattice.infra.persistence.SourceFileChunkJdbcRepository;
import com.xbk.lattice.infra.persistence.SourceFileJdbcRepository;
import com.xbk.lattice.infra.persistence.SourceFileRecord;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Chunk 全量重建服务
 *
 * 职责：基于当前 articles/source_files 正文重新生成 article_chunks/source_file_chunks
 *
 * @author xiexu
 */
@Slf4j
@Service
public class ChunkRebuildService {

    private final ArticleJdbcRepository articleJdbcRepository;

    private final SourceFileJdbcRepository sourceFileJdbcRepository;

    private final ArticleChunkJdbcRepository articleChunkJdbcRepository;

    private final SourceFileChunkJdbcRepository sourceFileChunkJdbcRepository;

    /**
     * 创建 chunk 全量重建服务。
     *
     * @param articleJdbcRepository 文章仓储
     * @param sourceFileJdbcRepository 源文件仓储
     * @param articleChunkJdbcRepository 文章 chunk 仓储
     * @param sourceFileChunkJdbcRepository 源文件 chunk 仓储
     */
    public ChunkRebuildService(
            ArticleJdbcRepository articleJdbcRepository,
            SourceFileJdbcRepository sourceFileJdbcRepository,
            ArticleChunkJdbcRepository articleChunkJdbcRepository,
            SourceFileChunkJdbcRepository sourceFileChunkJdbcRepository
    ) {
        this.articleJdbcRepository = articleJdbcRepository;
        this.sourceFileJdbcRepository = sourceFileJdbcRepository;
        this.articleChunkJdbcRepository = articleChunkJdbcRepository;
        this.sourceFileChunkJdbcRepository = sourceFileChunkJdbcRepository;
    }

    /**
     * 执行 article/source chunks 的完整重建。
     *
     * @return 重建结果
     */
    @Transactional(rollbackFor = Exception.class)
    public ChunkRebuildResult rebuildAll() {
        List<ArticleRecord> articleRecords = articleJdbcRepository.findAll();
        List<SourceFileRecord> sourceFileRecords = sourceFileJdbcRepository.findAll();

        int rebuiltArticleCount = articleChunkJdbcRepository.rebuildAll(articleRecords);
        int rebuiltSourceFileCount = sourceFileChunkJdbcRepository.rebuildAll(sourceFileRecords);
        int articleChunkCount = articleChunkJdbcRepository.countAll();
        int sourceFileChunkCount = sourceFileChunkJdbcRepository.countAll();
        OffsetDateTime rebuiltAt = OffsetDateTime.now();

        log.info(
                "Chunk rebuild completed. rebuiltArticleCount: {}, rebuiltSourceFileCount: {}, articleChunkCount: {}, sourceFileChunkCount: {}",
                rebuiltArticleCount,
                rebuiltSourceFileCount,
                articleChunkCount,
                sourceFileChunkCount
        );
        return new ChunkRebuildResult(
                rebuiltArticleCount,
                rebuiltSourceFileCount,
                articleChunkCount,
                sourceFileChunkCount,
                rebuiltAt.toString()
        );
    }
}
