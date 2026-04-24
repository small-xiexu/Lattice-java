package com.xbk.lattice.query.deepresearch.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xbk.lattice.infra.persistence.DeepResearchEvidenceCardJdbcRepository;
import com.xbk.lattice.infra.persistence.DeepResearchEvidenceCardRecord;
import com.xbk.lattice.infra.persistence.DeepResearchRunJdbcRepository;
import com.xbk.lattice.infra.persistence.DeepResearchRunRecord;
import com.xbk.lattice.query.deepresearch.domain.DeepResearchAuditSnapshot;
import com.xbk.lattice.query.deepresearch.domain.EvidenceCard;
import com.xbk.lattice.query.deepresearch.domain.EvidenceLedger;
import com.xbk.lattice.query.deepresearch.domain.LayeredResearchPlan;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Deep Research 审计持久化服务
 *
 * 职责：把运行摘要与证据卡一次性写入 Deep Research 审计表
 *
 * @author xiexu
 */
@Service
@Profile("jdbc")
public class DeepResearchAuditPersistenceService {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().findAndRegisterModules();

    private final DeepResearchRunJdbcRepository deepResearchRunJdbcRepository;

    private final DeepResearchEvidenceCardJdbcRepository deepResearchEvidenceCardJdbcRepository;

    /**
     * 创建 Deep Research 审计持久化服务。
     *
     * @param deepResearchRunJdbcRepository 运行主表仓储
     * @param deepResearchEvidenceCardJdbcRepository 证据卡仓储
     */
    public DeepResearchAuditPersistenceService(
            DeepResearchRunJdbcRepository deepResearchRunJdbcRepository,
            DeepResearchEvidenceCardJdbcRepository deepResearchEvidenceCardJdbcRepository
    ) {
        this.deepResearchRunJdbcRepository = deepResearchRunJdbcRepository;
        this.deepResearchEvidenceCardJdbcRepository = deepResearchEvidenceCardJdbcRepository;
    }

    /**
     * 持久化一次 Deep Research 运行快照。
     *
     * @param queryId 查询标识
     * @param question 查询问题
     * @param routeReason 路由原因
     * @param plan 研究计划
     * @param evidenceLedger 证据账本
     * @param llmCallCount LLM 调用数
     * @param citationCoverage 引用覆盖率
     * @param partialAnswer 是否部分答案
     * @param hasConflicts 是否存在冲突
     * @param finalAnswerAuditId 最终答案审计主键
     * @return 审计快照
     */
    @Transactional(rollbackFor = Exception.class)
    public DeepResearchAuditSnapshot persist(
            String queryId,
            String question,
            String routeReason,
            LayeredResearchPlan plan,
            EvidenceLedger evidenceLedger,
            int llmCallCount,
            double citationCoverage,
            boolean partialAnswer,
            boolean hasConflicts,
            Long finalAnswerAuditId
    ) {
        List<EvidenceCard> evidenceCards = evidenceLedger == null ? List.of() : evidenceLedger.getCards();
        Long runId = deepResearchRunJdbcRepository.insert(new DeepResearchRunRecord(
                queryId,
                question,
                routeReason,
                writeJson(plan),
                plan == null ? 0 : plan.layerCount(),
                plan == null ? 0 : plan.taskCount(),
                llmCallCount,
                citationCoverage,
                partialAnswer,
                hasConflicts,
                finalAnswerAuditId
        ));
        for (EvidenceCard evidenceCard : evidenceCards) {
            deepResearchEvidenceCardJdbcRepository.insert(new DeepResearchEvidenceCardRecord(
                    runId,
                    evidenceCard.getEvidenceId(),
                    evidenceCard.getLayerIndex(),
                    evidenceCard.getTaskId(),
                    evidenceCard.getScope(),
                    writeJson(evidenceCard.getFindings()),
                    writeJson(evidenceCard.getGaps()),
                    writeJson(evidenceCard.getRelatedLeads())
            ));
        }
        return new DeepResearchAuditSnapshot(runId, evidenceCards.size());
    }

    private String writeJson(Object value) {
        try {
            return OBJECT_MAPPER.writeValueAsString(value);
        }
        catch (Exception exception) {
            return "{}";
        }
    }
}
