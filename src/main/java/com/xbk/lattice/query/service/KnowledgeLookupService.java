package com.xbk.lattice.query.service;

import com.xbk.lattice.infra.persistence.ArticleJdbcRepository;
import com.xbk.lattice.infra.persistence.ArticleRecord;
import com.xbk.lattice.infra.persistence.SourceFileJdbcRepository;
import com.xbk.lattice.infra.persistence.SourceFileRecord;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * 知识详情查询服务
 *
 * 职责：按概念标识或源文件路径读取知识详情，供 MCP `lattice_get` 使用
 *
 * @author xiexu
 */
@Service
@Profile("jdbc")
public class KnowledgeLookupService {

    private final ArticleJdbcRepository articleJdbcRepository;

    private final SourceFileJdbcRepository sourceFileJdbcRepository;

    /**
     * 创建知识详情查询服务。
     *
     * @param articleJdbcRepository 文章仓储
     * @param sourceFileJdbcRepository 源文件仓储
     */
    public KnowledgeLookupService(
            ArticleJdbcRepository articleJdbcRepository,
            SourceFileJdbcRepository sourceFileJdbcRepository
    ) {
        this.articleJdbcRepository = articleJdbcRepository;
        this.sourceFileJdbcRepository = sourceFileJdbcRepository;
    }

    /**
     * 查询知识详情。
     *
     * @param id 概念标识或源文件路径
     * @return 知识详情结果
     */
    public KnowledgeLookupResult get(String id) {
        Optional<ArticleRecord> articleRecord = articleJdbcRepository.findByConceptId(id);
        if (articleRecord.isPresent()) {
            ArticleRecord record = articleRecord.get();
            return new KnowledgeLookupResult(
                    true,
                    "article",
                    record.getConceptId(),
                    record.getTitle(),
                    record.getContent(),
                    record.getSourcePaths(),
                    record.getMetadataJson()
            );
        }
        Optional<SourceFileRecord> sourceFileRecord = sourceFileJdbcRepository.findByPath(id);
        if (sourceFileRecord.isPresent()) {
            SourceFileRecord record = sourceFileRecord.get();
            return new KnowledgeLookupResult(
                    true,
                    "source",
                    record.getFilePath(),
                    record.getFilePath(),
                    record.getContentText(),
                    List.of(record.getFilePath()),
                    record.getMetadataJson()
            );
        }
        return KnowledgeLookupResult.notFound(id);
    }
}
