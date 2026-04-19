package com.xbk.lattice.source.service;

import com.xbk.lattice.source.domain.KnowledgeSource;
import com.xbk.lattice.source.domain.KnowledgeSourcePage;
import com.xbk.lattice.source.infra.KnowledgeSourceJdbcRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * 资料源后台服务
 *
 * 职责：提供资料源主模型的查询与保存能力
 *
 * @author xiexu
 */
@Service
@Profile("jdbc")
public class SourceService {

    private final KnowledgeSourceJdbcRepository knowledgeSourceJdbcRepository;

    public SourceService(KnowledgeSourceJdbcRepository knowledgeSourceJdbcRepository) {
        this.knowledgeSourceJdbcRepository = knowledgeSourceJdbcRepository;
    }

    public List<KnowledgeSource> listSources() {
        return knowledgeSourceJdbcRepository.findAll();
    }

    /**
     * 分页查询资料源。
     *
     * @param keyword 关键词
     * @param status 状态过滤
     * @param sourceType 类型过滤
     * @param page 页码，从 1 开始
     * @param size 每页大小
     * @return 分页结果
     */
    public KnowledgeSourcePage listSources(
            String keyword,
            String status,
            String sourceType,
            int page,
            int size
    ) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.max(size, 1);
        int offset = (safePage - 1) * safeSize;
        long total = knowledgeSourceJdbcRepository.countAll(keyword, status, sourceType);
        List<KnowledgeSource> items = knowledgeSourceJdbcRepository.findPage(
                keyword,
                status,
                sourceType,
                offset,
                safeSize
        );
        return new KnowledgeSourcePage(safePage, safeSize, total, items);
    }

    public Optional<KnowledgeSource> findById(Long id) {
        return knowledgeSourceJdbcRepository.findById(id);
    }

    public Optional<KnowledgeSource> findBySourceCode(String sourceCode) {
        return knowledgeSourceJdbcRepository.findBySourceCode(sourceCode);
    }

    @Transactional(rollbackFor = Exception.class)
    public KnowledgeSource save(KnowledgeSource source) {
        return knowledgeSourceJdbcRepository.save(source);
    }
}
