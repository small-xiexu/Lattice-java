package com.xbk.lattice.query.service;

import com.xbk.lattice.article.service.ArticleIdentityResolver;
import com.xbk.lattice.infra.persistence.ArticleJdbcRepository;
import com.xbk.lattice.infra.persistence.ArticleRecord;
import com.xbk.lattice.infra.persistence.SourceFileJdbcRepository;
import com.xbk.lattice.infra.persistence.SourceFileRecord;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * 知识详情查询服务
 *
 * 职责：按文章唯一键、概念标识或源文件路径读取知识详情，供 MCP `lattice_get` 使用
 *
 * @author xiexu
 */
@Service
public class KnowledgeLookupService {

    private final ArticleIdentityResolver articleIdentityResolver;

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
        this.articleIdentityResolver = new ArticleIdentityResolver(articleJdbcRepository);
        this.sourceFileJdbcRepository = sourceFileJdbcRepository;
    }

    /**
     * 查询知识详情。
     *
     * @param id 文章唯一键、概念标识或源文件路径
     * @return 知识详情结果
     */
    public KnowledgeLookupResult get(String id) {
        Optional<ArticleRecord> articleRecord = articleIdentityResolver.resolve(id);
        if (articleRecord.isPresent()) {
            ArticleRecord record = articleRecord.get();
            return new KnowledgeLookupResult(
                    true,
                    "article",
                    record.getSourceId(),
                    record.getArticleKey(),
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
                    record.getSourceId(),
                    null,
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
