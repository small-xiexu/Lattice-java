package com.xbk.lattice.query.service;

import com.xbk.lattice.infra.persistence.ArticleVectorJdbcRepository;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 向量 schema 检查器
 *
 * 职责：对比当前 embedding profile 维度、数据库向量列维度与 ANN 索引状态
 *
 * @author xiexu
 */
@Service
public class VectorSchemaInspector {

    private static final Pattern VECTOR_DIMENSIONS_PATTERN = Pattern.compile(".*vector\\((\\d+)\\)$");

    private final QueryVectorConfigService queryVectorConfigService;

    private final ArticleVectorJdbcRepository articleVectorJdbcRepository;

    /**
     * 创建向量 schema 检查器。
     *
     * @param queryVectorConfigService Query 向量配置服务
     * @param articleVectorJdbcRepository 文章向量仓储
     */
    public VectorSchemaInspector(
            QueryVectorConfigService queryVectorConfigService,
            ArticleVectorJdbcRepository articleVectorJdbcRepository
    ) {
        this.queryVectorConfigService = queryVectorConfigService;
        this.articleVectorJdbcRepository = articleVectorJdbcRepository;
    }

    /**
     * 返回当前向量 schema 检查结果。
     *
     * @return 检查结果
     */
    public VectorSchemaInspection inspect() {
        QueryVectorConfigState state = queryVectorConfigService.getCurrentState();
        Integer profileDimensions = state.getProfileDimensions();
        Integer schemaDimensions = extractSchemaDimensions(
                articleVectorJdbcRepository.findEmbeddingColumnType().orElse("")
        ).orElse(null);
        Optional<String> annIndexType = articleVectorJdbcRepository.findEmbeddingAnnIndexType();
        boolean dimensionsConsistent = profileDimensions != null
                && schemaDimensions != null
                && profileDimensions.intValue() == schemaDimensions.intValue();
        return new VectorSchemaInspection(
                profileDimensions,
                schemaDimensions,
                dimensionsConsistent,
                annIndexType.isPresent(),
                annIndexType.orElse("")
        );
    }

    private Optional<Integer> extractSchemaDimensions(String embeddingColumnType) {
        if (embeddingColumnType == null || embeddingColumnType.isBlank()) {
            return Optional.empty();
        }
        Matcher matcher = VECTOR_DIMENSIONS_PATTERN.matcher(embeddingColumnType.trim());
        if (!matcher.matches()) {
            return Optional.empty();
        }
        return Optional.of(Integer.parseInt(matcher.group(1)));
    }
}
