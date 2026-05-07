package com.xbk.lattice.source.infra;

import com.xbk.lattice.source.domain.KnowledgeSource;
import com.xbk.lattice.source.infra.mapper.KnowledgeSourceMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 资料源 JDBC 仓储
 *
 * 职责：提供 knowledge_sources 表的增删改查能力
 *
 * @author xiexu
 */
@Repository
public class KnowledgeSourceJdbcRepository {

    private final KnowledgeSourceMapper knowledgeSourceMapper;

    public KnowledgeSourceJdbcRepository(KnowledgeSourceMapper knowledgeSourceMapper) {
        this.knowledgeSourceMapper = knowledgeSourceMapper;
    }

    public List<KnowledgeSource> findAll() {
        return knowledgeSourceMapper.findAll();
    }

    public Optional<KnowledgeSource> findById(Long id) {
        return Optional.ofNullable(knowledgeSourceMapper.findById(id));
    }

    public Optional<KnowledgeSource> findBySourceCode(String sourceCode) {
        return Optional.ofNullable(knowledgeSourceMapper.findBySourceCode(sourceCode));
    }

    /**
     * 统计资料源数量。
     *
     * 职责：为后台分页列表返回总条数，并默认排除 legacy-default
     *
     * @param keyword 关键词
     * @param status 状态过滤
     * @param sourceType 类型过滤
     * @return 资料源总数
     */
    public long countAll(String keyword, String status, String sourceType) {
        return knowledgeSourceMapper.countAll(
                normalizeNullable(keyword),
                normalizeNullable(status),
                normalizeNullable(sourceType)
        );
    }

    /**
     * 分页查询资料源。
     *
     * 职责：为后台列表提供带过滤条件的分页结果
     *
     * @param keyword 关键词
     * @param status 状态过滤
     * @param sourceType 类型过滤
     * @param offset 偏移量
     * @param limit 分页大小
     * @return 资料源列表
     */
    public List<KnowledgeSource> findPage(
            String keyword,
            String status,
            String sourceType,
            int offset,
            int limit
    ) {
        return knowledgeSourceMapper.findPage(
                normalizeNullable(keyword),
                normalizeNullable(status),
                normalizeNullable(sourceType),
                offset,
                limit
        );
    }

    public KnowledgeSource save(KnowledgeSource source) {
        if (source.getId() == null) {
            return insert(source);
        }
        update(source);
        return findById(source.getId()).orElseThrow();
    }

    private KnowledgeSource insert(KnowledgeSource source) {
        Long id = knowledgeSourceMapper.insert(source);
        if (id == null) {
            throw new IllegalStateException("Failed to insert knowledge_sources");
        }
        return findById(id).orElseThrow();
    }

    private void update(KnowledgeSource source) {
        knowledgeSourceMapper.update(source);
    }

    /**
     * 归一可空过滤条件。
     *
     * @param value 原始值
     * @return 归一后的值
     */
    private String normalizeNullable(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
