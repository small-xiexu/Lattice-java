package com.xbk.lattice.query.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xbk.lattice.compiler.ast.domain.AstEntity;
import com.xbk.lattice.compiler.ast.domain.AstFact;
import com.xbk.lattice.compiler.ast.domain.AstRelation;
import com.xbk.lattice.infra.persistence.ArticleSourceRefJdbcRepository;
import com.xbk.lattice.infra.persistence.GraphEntityJdbcRepository;
import com.xbk.lattice.infra.persistence.GraphFactJdbcRepository;
import com.xbk.lattice.infra.persistence.GraphRelationJdbcRepository;
import com.xbk.lattice.infra.persistence.SourceFileJdbcRepository;
import com.xbk.lattice.infra.persistence.SourceFileRecord;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 图谱检索服务
 *
 * 职责：把 AST 图谱实体、事实与关系组织成 Query 主链可消费的 graph channel
 *
 * @author xiexu
 */
@Service
@Profile("jdbc")
public class GraphSearchService {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().findAndRegisterModules();

    private final GraphEntityJdbcRepository graphEntityJdbcRepository;

    private final GraphFactJdbcRepository graphFactJdbcRepository;

    private final GraphRelationJdbcRepository graphRelationJdbcRepository;

    private final ArticleSourceRefJdbcRepository articleSourceRefJdbcRepository;

    private final SourceFileJdbcRepository sourceFileJdbcRepository;

    /**
     * 创建图谱检索服务（兼容测试环境的 no-op 实现）。
     */
    public GraphSearchService() {
        this(null, null, null, null, null);
    }

    /**
     * 创建图谱检索服务。
     *
     * @param graphEntityJdbcRepository 图谱实体仓储
     * @param graphFactJdbcRepository 图谱事实仓储
     * @param graphRelationJdbcRepository 图谱关系仓储
     * @param articleSourceRefJdbcRepository 文章来源映射仓储
     * @param sourceFileJdbcRepository 源文件仓储
     */
    public GraphSearchService(
            GraphEntityJdbcRepository graphEntityJdbcRepository,
            GraphFactJdbcRepository graphFactJdbcRepository,
            GraphRelationJdbcRepository graphRelationJdbcRepository,
            ArticleSourceRefJdbcRepository articleSourceRefJdbcRepository,
            SourceFileJdbcRepository sourceFileJdbcRepository
    ) {
        this.graphEntityJdbcRepository = graphEntityJdbcRepository;
        this.graphFactJdbcRepository = graphFactJdbcRepository;
        this.graphRelationJdbcRepository = graphRelationJdbcRepository;
        this.articleSourceRefJdbcRepository = articleSourceRefJdbcRepository;
        this.sourceFileJdbcRepository = sourceFileJdbcRepository;
    }

    /**
     * 执行 graph channel 检索。
     *
     * @param question 查询问题
     * @param limit 返回数量
     * @return 命中列表
     */
    public List<QueryArticleHit> search(String question, int limit) {
        if (graphEntityJdbcRepository == null
                || graphFactJdbcRepository == null
                || graphRelationJdbcRepository == null
                || articleSourceRefJdbcRepository == null
                || sourceFileJdbcRepository == null) {
            return List.of();
        }
        List<String> mentions = QueryTokenExtractor.extract(question);
        if (mentions.isEmpty()) {
            return List.of();
        }
        int safeLimit = limit <= 0 ? 5 : limit;
        List<AstEntity> entities = graphEntityJdbcRepository.searchByMentions(mentions, Math.max(safeLimit * 2, 8));
        if (entities.isEmpty()) {
            return List.of();
        }
        List<Long> sourceFileIds = collectSourceFileIds(entities);
        Map<Long, List<String>> articleKeysBySourceFileId =
                articleSourceRefJdbcRepository.findArticleKeysBySourceFileIds(sourceFileIds);
        Map<Long, SourceFileRecord> sourceFilesById = sourceFileJdbcRepository.findByIds(sourceFileIds);
        List<QueryArticleHit> hits = new ArrayList<QueryArticleHit>();
        for (AstEntity entity : entities) {
            GraphGroundingPack groundingPack = buildGroundingPack(
                    entity,
                    mentions,
                    articleKeysBySourceFileId,
                    sourceFilesById
            );
            if (groundingPack == null || groundingPack.getFactsBlock().isBlank()) {
                continue;
            }
            hits.add(toQueryArticleHit(groundingPack));
        }
        hits.sort(Comparator.comparing(QueryArticleHit::getScore).reversed()
                .thenComparing(QueryArticleHit::getConceptId, Comparator.nullsLast(String::compareTo)));
        if (hits.size() <= safeLimit) {
            return hits;
        }
        return hits.subList(0, safeLimit);
    }

    /**
     * 构建可供 Prompt 直接消费的 facts block。
     *
     * @param graphHits 图谱命中
     * @return facts block
     */
    public String buildFactsBlock(List<QueryArticleHit> graphHits) {
        if (graphHits == null || graphHits.isEmpty()) {
            return "";
        }
        StringBuilder factsBlockBuilder = new StringBuilder();
        int count = 0;
        for (QueryArticleHit graphHit : graphHits) {
            if (graphHit == null || graphHit.getEvidenceType() != QueryEvidenceType.GRAPH) {
                continue;
            }
            if (count >= 4) {
                break;
            }
            factsBlockBuilder.append("- ").append(graphHit.getTitle()).append(": ")
                    .append(graphHit.getContent()).append("\n");
            count++;
        }
        return factsBlockBuilder.toString().trim();
    }

    private GraphGroundingPack buildGroundingPack(
            AstEntity entity,
            List<String> mentions,
            Map<Long, List<String>> articleKeysBySourceFileId,
            Map<Long, SourceFileRecord> sourceFilesById
    ) {
        List<AstFact> facts = graphFactJdbcRepository.findActiveFactsByEntityIds(List.of(entity.getId()), 6);
        List<AstRelation> relations = graphRelationJdbcRepository.findActiveRelationsByEntityIds(List.of(entity.getId()), 6);
        String factsBlock = buildFactsBlock(entity, facts, relations);
        double score = scoreEntity(entity, factsBlock, mentions);
        if (score <= 0.0D) {
            return null;
        }
        List<String> articleKeys = resolveArticleKeys(entity.getSourceFileId(), articleKeysBySourceFileId);
        List<String> sourcePaths = resolveSourcePaths(entity, facts, relations, sourceFilesById);
        return new GraphGroundingPack(
                entity.getId(),
                entity.getCanonicalName(),
                articleKeys,
                sourcePaths,
                factsBlock,
                score
        );
    }

    private String buildFactsBlock(AstEntity entity, List<AstFact> facts, List<AstRelation> relations) {
        StringBuilder factsBlockBuilder = new StringBuilder();
        factsBlockBuilder.append("实体=").append(entity.getCanonicalName());
        if (!facts.isEmpty()) {
            for (AstFact fact : facts) {
                factsBlockBuilder.append("；")
                        .append(fact.getPredicate())
                        .append("=")
                        .append(fact.getValue());
            }
        }
        if (!relations.isEmpty()) {
            for (AstRelation relation : relations) {
                factsBlockBuilder.append("；")
                        .append(relation.getEdgeType())
                        .append("->")
                        .append(relation.getDstId());
            }
        }
        return factsBlockBuilder.toString();
    }

    private double scoreEntity(AstEntity entity, String factsBlock, List<String> mentions) {
        String simpleName = safeLowercase(entity.getSimpleName());
        String canonicalName = safeLowercase(entity.getCanonicalName());
        String factsText = safeLowercase(factsBlock);
        double score = 0.0D;
        for (String mention : mentions) {
            if (simpleName.contains(mention)) {
                score += 3.0D;
            }
            if (canonicalName.contains(mention)) {
                score += 2.5D;
            }
            if (factsText.contains(mention)) {
                score += 1.0D;
            }
        }
        if ("UNRESOLVED".equalsIgnoreCase(entity.getResolutionStatus())) {
            score = score * 0.6D;
        }
        return score;
    }

    private QueryArticleHit toQueryArticleHit(GraphGroundingPack groundingPack) {
        List<String> articleKeys = groundingPack.getArticleKeys();
        String articleKey = articleKeys.isEmpty() ? null : articleKeys.get(0);
        String metadataJson = buildMetadataJson(groundingPack);
        String title = "图谱实体：" + groundingPack.getCanonicalName();
        return new QueryArticleHit(
                QueryEvidenceType.GRAPH,
                null,
                articleKey,
                groundingPack.getEntityId(),
                title,
                groundingPack.getFactsBlock(),
                metadataJson,
                groundingPack.getSourcePaths(),
                groundingPack.getScore()
        );
    }

    private String buildMetadataJson(GraphGroundingPack groundingPack) {
        try {
            Map<String, Object> metadata = new LinkedHashMap<String, Object>();
            metadata.put("entityId", groundingPack.getEntityId());
            metadata.put("canonicalName", groundingPack.getCanonicalName());
            metadata.put("articleKeys", groundingPack.getArticleKeys());
            metadata.put("sourcePaths", groundingPack.getSourcePaths());
            metadata.put("factsBlock", groundingPack.getFactsBlock());
            return OBJECT_MAPPER.writeValueAsString(metadata);
        }
        catch (Exception exception) {
            return "{\"entityId\":\"" + groundingPack.getEntityId() + "\"}";
        }
    }

    private List<Long> collectSourceFileIds(List<AstEntity> entities) {
        Set<Long> sourceFileIds = new LinkedHashSet<Long>();
        for (AstEntity entity : entities) {
            if (entity.getSourceFileId() != null) {
                sourceFileIds.add(entity.getSourceFileId());
            }
        }
        return new ArrayList<Long>(sourceFileIds);
    }

    private List<String> resolveArticleKeys(Long sourceFileId, Map<Long, List<String>> articleKeysBySourceFileId) {
        if (sourceFileId == null) {
            return List.of();
        }
        List<String> articleKeys = articleKeysBySourceFileId.get(sourceFileId);
        if (articleKeys == null) {
            return List.of();
        }
        return articleKeys;
    }

    private List<String> resolveSourcePaths(
            AstEntity entity,
            List<AstFact> facts,
            List<AstRelation> relations,
            Map<Long, SourceFileRecord> sourceFilesById
    ) {
        List<String> sourcePaths = new ArrayList<String>();
        if (entity.getSourceFileId() != null) {
            SourceFileRecord sourceFileRecord = sourceFilesById.get(entity.getSourceFileId());
            if (sourceFileRecord != null) {
                String sourcePath = sourceFileRecord.getRelativePath();
                if (sourcePath == null || sourcePath.isBlank()) {
                    sourcePath = sourceFileRecord.getFilePath();
                }
                if (entity.getAnchorRef() != null && entity.getAnchorRef().contains(":")) {
                    String[] anchorParts = entity.getAnchorRef().split(":", 2);
                    if (anchorParts.length == 2) {
                        sourcePath = anchorParts[0] + ", lines " + anchorParts[1];
                    }
                }
                sourcePaths.add(sourcePath);
            }
        }
        if (sourcePaths.isEmpty()) {
            for (AstFact fact : facts) {
                if (fact.getSourceRef() != null && !fact.getSourceRef().isBlank()) {
                    sourcePaths.add(formatSourceRef(fact.getSourceRef(), fact.getSourceStartLine(), fact.getSourceEndLine()));
                    break;
                }
            }
        }
        if (sourcePaths.isEmpty()) {
            for (AstRelation relation : relations) {
                if (relation.getSourceRef() != null && !relation.getSourceRef().isBlank()) {
                    sourcePaths.add(formatSourceRef(relation.getSourceRef(), relation.getSourceStartLine(), relation.getSourceEndLine()));
                    break;
                }
            }
        }
        return sourcePaths;
    }

    private String formatSourceRef(String sourceRef, int sourceStartLine, int sourceEndLine) {
        if (sourceStartLine <= 0) {
            return sourceRef;
        }
        if (sourceEndLine <= 0 || sourceEndLine == sourceStartLine) {
            return sourceRef + ", lines " + sourceStartLine;
        }
        return sourceRef + ", lines " + sourceStartLine + "-" + sourceEndLine;
    }

    private String safeLowercase(String value) {
        if (value == null) {
            return "";
        }
        return value.toLowerCase(Locale.ROOT);
    }
}
