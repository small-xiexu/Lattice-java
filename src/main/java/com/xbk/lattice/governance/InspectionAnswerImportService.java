package com.xbk.lattice.governance;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xbk.lattice.infra.persistence.ContributionJdbcRepository;
import com.xbk.lattice.infra.persistence.ContributionRecord;
import com.xbk.lattice.infra.persistence.PendingQueryJdbcRepository;
import com.xbk.lattice.infra.persistence.PendingQueryRecord;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Inspection 答案导入服务
 *
 * 职责：把人工最终答案导回 contribution 沉淀，并清理对应 pending query
 *
 * @author xiexu
 */
@Service
@Profile("jdbc")
public class InspectionAnswerImportService {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static final String PENDING_INSPECTION_PREFIX = "pending:";

    private final PendingQueryJdbcRepository pendingQueryJdbcRepository;

    private final ContributionJdbcRepository contributionJdbcRepository;

    /**
     * 创建 InspectionAnswerImportService。
     *
     * @param pendingQueryJdbcRepository PendingQuery 仓储
     * @param contributionJdbcRepository Contribution 仓储
     */
    public InspectionAnswerImportService(
            PendingQueryJdbcRepository pendingQueryJdbcRepository,
            ContributionJdbcRepository contributionJdbcRepository
    ) {
        this.pendingQueryJdbcRepository = pendingQueryJdbcRepository;
        this.contributionJdbcRepository = contributionJdbcRepository;
    }

    /**
     * 导入单条人工最终答案。
     *
     * @param inspectionId inspection 问题标识
     * @param finalAnswer 人工最终答案
     * @param confirmedBy 确认人
     * @return 导入结果
     */
    public InspectionImportResult importAnswer(String inspectionId, String finalAnswer, String confirmedBy) {
        if (inspectionId == null || !inspectionId.startsWith(PENDING_INSPECTION_PREFIX)) {
            throw new IllegalArgumentException("暂不支持的 inspectionId: " + inspectionId);
        }
        if (finalAnswer == null || finalAnswer.isBlank()) {
            throw new IllegalArgumentException("finalAnswer 不能为空");
        }
        PendingQueryRecord pendingQueryRecord = requirePendingQuery(inspectionId.substring(PENDING_INSPECTION_PREFIX.length()));
        OffsetDateTime now = OffsetDateTime.now();
        contributionJdbcRepository.save(new ContributionRecord(
                UUID.randomUUID(),
                pendingQueryRecord.getQuestion(),
                finalAnswer,
                appendImportedAnswer(pendingQueryRecord, finalAnswer, now, inspectionId),
                normalizeConfirmedBy(confirmedBy),
                now
        ));
        pendingQueryJdbcRepository.deleteByQueryId(pendingQueryRecord.getQueryId());
        return new InspectionImportResult(1, List.of(inspectionId));
    }

    private PendingQueryRecord requirePendingQuery(String queryId) {
        if (pendingQueryJdbcRepository == null) {
            throw new UnsupportedOperationException("pendingQueryJdbcRepository not configured");
        }
        return pendingQueryJdbcRepository.findByQueryId(queryId)
                .orElseThrow(() -> new IllegalArgumentException("pending query 不存在: " + queryId));
    }

    private String appendImportedAnswer(
            PendingQueryRecord pendingQueryRecord,
            String finalAnswer,
            OffsetDateTime importedAt,
            String inspectionId
    ) {
        List<Map<String, Object>> corrections = readCorrections(pendingQueryRecord.getCorrectionsJson());
        Map<String, Object> importEntry = new LinkedHashMap<String, Object>();
        importEntry.put("type", "imported-answer");
        importEntry.put("inspectionId", inspectionId);
        importEntry.put("previousAnswer", pendingQueryRecord.getAnswer());
        importEntry.put("finalAnswer", finalAnswer);
        importEntry.put("importedAt", importedAt.toString());
        corrections.add(importEntry);
        try {
            return OBJECT_MAPPER.writeValueAsString(corrections);
        }
        catch (JsonProcessingException ex) {
            throw new IllegalStateException("序列化导入记录失败", ex);
        }
    }

    private List<Map<String, Object>> readCorrections(String correctionsJson) {
        if (correctionsJson == null || correctionsJson.isBlank()) {
            return new java.util.ArrayList<Map<String, Object>>();
        }
        try {
            return new java.util.ArrayList<Map<String, Object>>(OBJECT_MAPPER.readValue(
                    correctionsJson,
                    new TypeReference<List<Map<String, Object>>>() {
                    }
            ));
        }
        catch (JsonProcessingException ex) {
            return new java.util.ArrayList<Map<String, Object>>();
        }
    }

    private String normalizeConfirmedBy(String confirmedBy) {
        if (confirmedBy == null || confirmedBy.isBlank()) {
            return "inspection-import";
        }
        return confirmedBy;
    }
}
