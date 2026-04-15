package com.xbk.lattice.query.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xbk.lattice.api.query.QueryArticleResponse;
import com.xbk.lattice.api.query.QueryResponse;
import com.xbk.lattice.api.query.QuerySourceResponse;
import com.xbk.lattice.infra.persistence.ContributionJdbcRepository;
import com.xbk.lattice.infra.persistence.ContributionRecord;
import com.xbk.lattice.infra.persistence.PendingQueryJdbcRepository;
import com.xbk.lattice.infra.persistence.PendingQueryRecord;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * PendingQuery 服务
 *
 * 职责：管理待确认查询的创建、纠错、确认与丢弃闭环
 *
 * @author xiexu
 */
@Service
@Profile("jdbc")
public class PendingQueryService implements PendingQueryManager {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static final int PENDING_TTL_DAYS = 7;

    private final PendingQueryJdbcRepository pendingQueryJdbcRepository;

    private final ContributionJdbcRepository contributionJdbcRepository;

    /**
     * 创建 PendingQuery 服务。
     *
     * @param pendingQueryJdbcRepository PendingQuery 仓储
     * @param contributionJdbcRepository Contribution 仓储
     */
    public PendingQueryService(
            PendingQueryJdbcRepository pendingQueryJdbcRepository,
            ContributionJdbcRepository contributionJdbcRepository
    ) {
        this.pendingQueryJdbcRepository = pendingQueryJdbcRepository;
        this.contributionJdbcRepository = contributionJdbcRepository;
    }

    /**
     * 创建待确认查询。
     *
     * @param question 问题
     * @param queryResponse 查询结果
     * @return 待确认查询记录
     */
    @Override
    public PendingQueryRecord createPendingQuery(String question, QueryResponse queryResponse) {
        OffsetDateTime now = OffsetDateTime.now();
        PendingQueryRecord pendingQueryRecord = new PendingQueryRecord(
                UUID.randomUUID().toString(),
                question,
                queryResponse.getAnswer(),
                extractConceptIds(queryResponse.getArticles()),
                extractSourcePaths(queryResponse.getSources()),
                "[]",
                queryResponse.getReviewStatus() == null ? ReviewStatus.PASSED.name() : queryResponse.getReviewStatus(),
                now,
                now.plusDays(PENDING_TTL_DAYS)
        );
        pendingQueryJdbcRepository.upsert(pendingQueryRecord);
        return pendingQueryRecord;
    }

    /**
     * 修订待确认查询答案。
     *
     * @param queryId 查询标识
     * @param correction 纠正内容
     * @return 更新后的待确认查询记录
     */
    @Override
    public PendingQueryRecord correct(String queryId, String correction) {
        PendingQueryRecord pendingQueryRecord = getRequiredRecord(queryId);
        OffsetDateTime now = OffsetDateTime.now();
        String revisedAnswer = pendingQueryRecord.getAnswer() + "\n\n用户纠正：" + correction.trim();
        String correctionsJson = appendCorrectionHistory(
                pendingQueryRecord.getCorrectionsJson(),
                correction,
                revisedAnswer,
                now
        );
        PendingQueryRecord updatedRecord = new PendingQueryRecord(
                pendingQueryRecord.getQueryId(),
                pendingQueryRecord.getQuestion(),
                revisedAnswer,
                pendingQueryRecord.getSelectedConceptIds(),
                pendingQueryRecord.getSourceFilePaths(),
                correctionsJson,
                pendingQueryRecord.getReviewStatus(),
                pendingQueryRecord.getCreatedAt(),
                now.plusDays(PENDING_TTL_DAYS)
        );
        pendingQueryJdbcRepository.upsert(updatedRecord);
        return updatedRecord;
    }

    /**
     * 确认待确认查询并沉淀贡献。
     *
     * @param queryId 查询标识
     */
    @Override
    public void confirm(String queryId) {
        PendingQueryRecord pendingQueryRecord = getRequiredRecord(queryId);
        contributionJdbcRepository.save(new ContributionRecord(
                UUID.randomUUID(),
                pendingQueryRecord.getQuestion(),
                pendingQueryRecord.getAnswer(),
                pendingQueryRecord.getCorrectionsJson(),
                "system",
                OffsetDateTime.now()
        ));
        pendingQueryJdbcRepository.deleteByQueryId(queryId);
    }

    /**
     * 丢弃待确认查询。
     *
     * @param queryId 查询标识
     */
    @Override
    public void discard(String queryId) {
        getRequiredRecord(queryId);
        pendingQueryJdbcRepository.deleteByQueryId(queryId);
    }

    /**
     * 提取概念标识。
     *
     * @param articleResponses 命中文章列表
     * @return 概念标识列表
     */
    private List<String> extractConceptIds(List<QueryArticleResponse> articleResponses) {
        List<String> conceptIds = new ArrayList<String>();
        for (QueryArticleResponse articleResponse : articleResponses) {
            conceptIds.add(articleResponse.getConceptId());
        }
        return conceptIds;
    }

    /**
     * 提取来源路径。
     *
     * @param sourceResponses 来源响应列表
     * @return 去重后的来源路径
     */
    private List<String> extractSourcePaths(List<QuerySourceResponse> sourceResponses) {
        LinkedHashSet<String> sourcePaths = new LinkedHashSet<String>();
        for (QuerySourceResponse sourceResponse : sourceResponses) {
            sourcePaths.addAll(sourceResponse.getSourcePaths());
        }
        return new ArrayList<String>(sourcePaths);
    }

    /**
     * 读取必需的待确认记录。
     *
     * @param queryId 查询标识
     * @return 待确认记录
     */
    private PendingQueryRecord getRequiredRecord(String queryId) {
        return pendingQueryJdbcRepository.findByQueryId(queryId)
                .orElseThrow(() -> new IllegalArgumentException("pending query 不存在: " + queryId));
    }

    /**
     * 追加纠错历史。
     *
     * @param correctionsJson 原纠错历史 JSON
     * @param correction 纠正内容
     * @param revisedAnswer 修订后答案
     * @param correctedAt 修订时间
     * @return 新纠错历史 JSON
     */
    private String appendCorrectionHistory(
            String correctionsJson,
            String correction,
            String revisedAnswer,
            OffsetDateTime correctedAt
    ) {
        List<Map<String, Object>> correctionHistory = readCorrectionHistory(correctionsJson);
        Map<String, Object> correctionEntry = new LinkedHashMap<String, Object>();
        correctionEntry.put("correction", correction);
        correctionEntry.put("revisedAnswer", revisedAnswer);
        correctionEntry.put("correctedAt", correctedAt.toString());
        correctionHistory.add(correctionEntry);
        try {
            return OBJECT_MAPPER.writeValueAsString(correctionHistory);
        }
        catch (JsonProcessingException ex) {
            throw new IllegalStateException("序列化纠错历史失败", ex);
        }
    }

    /**
     * 读取纠错历史。
     *
     * @param correctionsJson 纠错历史 JSON
     * @return 纠错历史
     */
    private List<Map<String, Object>> readCorrectionHistory(String correctionsJson) {
        try {
            return OBJECT_MAPPER.readValue(
                    correctionsJson,
                    OBJECT_MAPPER.getTypeFactory().constructCollectionType(List.class, Map.class)
            );
        }
        catch (JsonProcessingException ex) {
            throw new IllegalStateException("反序列化纠错历史失败", ex);
        }
    }
}
