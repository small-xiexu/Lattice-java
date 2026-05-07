package com.xbk.lattice.api.admin;

import com.xbk.lattice.infra.persistence.FactCardJdbcRepository;
import com.xbk.lattice.infra.persistence.FactCardRecord;
import com.xbk.lattice.infra.persistence.SourceFileJdbcRepository;
import com.xbk.lattice.infra.persistence.SourceFileRecord;
import com.xbk.lattice.query.evidence.domain.FactCardReviewStatus;
import com.xbk.lattice.query.evidence.domain.FactCardType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 管理侧 Fact Card 控制器
 *
 * 职责：暴露结构化证据卡统计、列表与明细查看接口
 *
 * @author xiexu
 */
@RestController
@RequestMapping("/api/v1/admin/fact-cards")
public class AdminFactCardController {

    private final FactCardJdbcRepository factCardJdbcRepository;

    private final SourceFileJdbcRepository sourceFileJdbcRepository;

    /**
     * 创建管理侧 Fact Card 控制器。
     *
     * @param factCardJdbcRepository Fact Card 仓储
     * @param sourceFileJdbcRepository 源文件仓储
     */
    public AdminFactCardController(
            FactCardJdbcRepository factCardJdbcRepository,
            SourceFileJdbcRepository sourceFileJdbcRepository
    ) {
        this.factCardJdbcRepository = factCardJdbcRepository;
        this.sourceFileJdbcRepository = sourceFileJdbcRepository;
    }

    /**
     * 返回 Fact Card 统计摘要。
     *
     * @return Fact Card 统计摘要
     */
    @GetMapping("/summary")
    public AdminFactCardSummaryResponse summary() {
        int totalCount = factCardJdbcRepository.countAll();
        Map<String, Integer> countByCardType = cardTypeCounts(factCardJdbcRepository.countByCardType());
        Map<String, Integer> countByReviewStatus = reviewStatusCounts(factCardJdbcRepository.countByReviewStatus());
        int sourceReferenceMissingCount = factCardJdbcRepository.countWithoutSourceChunks();
        int lowConfidenceCount = factCardJdbcRepository.countLowConfidence();
        return new AdminFactCardSummaryResponse(
                totalCount,
                countByCardType,
                countByReviewStatus,
                sourceReferenceMissingCount,
                lowConfidenceCount
        );
    }

    /**
     * 返回 Fact Card 列表。
     *
     * @param limit 返回数量
     * @return Fact Card 列表
     */
    @GetMapping
    public AdminFactCardListResponse list(@RequestParam(defaultValue = "50") int limit) {
        List<AdminFactCardItemResponse> items = new ArrayList<AdminFactCardItemResponse>();
        int safeLimit = limit <= 0 ? 50 : Math.min(limit, 200);
        List<FactCardRecord> factCardRecords = factCardJdbcRepository.findAll();
        int count = Math.min(safeLimit, factCardRecords.size());
        for (int index = 0; index < count; index++) {
            items.add(toItemResponse(factCardRecords.get(index)));
        }
        return new AdminFactCardListResponse(items.size(), items);
    }

    /**
     * 返回 Fact Card 明细。
     *
     * @param id Fact Card 主键
     * @return Fact Card 明细
     */
    @GetMapping("/{id}")
    public AdminFactCardItemResponse detail(@PathVariable Long id) {
        FactCardRecord factCardRecord = factCardJdbcRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("fact card not found: " + id));
        return toItemResponse(factCardRecord);
    }

    /**
     * 映射 Fact Card 条目响应。
     *
     * @param factCardRecord Fact Card 记录
     * @return 条目响应
     */
    private AdminFactCardItemResponse toItemResponse(FactCardRecord factCardRecord) {
        return new AdminFactCardItemResponse(
                factCardRecord.getId(),
                factCardRecord.getCardId(),
                factCardRecord.getSourceId(),
                factCardRecord.getSourceFileId(),
                resolveSourceFilePath(factCardRecord),
                factCardRecord.getCardType().name(),
                factCardRecord.getAnswerShape().name(),
                factCardRecord.getTitle(),
                factCardRecord.getClaim(),
                factCardRecord.getItemsJson(),
                factCardRecord.getEvidenceText(),
                factCardRecord.getSourceChunkIds(),
                factCardRecord.getArticleIds(),
                factCardRecord.getConfidence(),
                factCardRecord.getReviewStatus().databaseValue(),
                factCardRecord.getContentHash(),
                factCardRecord.getCreatedAt() == null ? null : factCardRecord.getCreatedAt().toString(),
                factCardRecord.getUpdatedAt() == null ? null : factCardRecord.getUpdatedAt().toString()
        );
    }

    /**
     * 解析源文件路径。
     *
     * @param factCardRecord Fact Card 记录
     * @return 源文件路径
     */
    private String resolveSourceFilePath(FactCardRecord factCardRecord) {
        if (sourceFileJdbcRepository == null || factCardRecord.getSourceFileId() == null) {
            return "";
        }
        Map<Long, SourceFileRecord> sourceFileMap = sourceFileJdbcRepository.findByIds(
                List.of(factCardRecord.getSourceFileId())
        );
        SourceFileRecord sourceFileRecord = sourceFileMap.get(factCardRecord.getSourceFileId());
        if (sourceFileRecord == null || sourceFileRecord.getFilePath() == null) {
            return "";
        }
        return sourceFileRecord.getFilePath();
    }

    /**
     * 转换卡类型统计。
     *
     * @param counts 原始统计
     * @return 响应统计
     */
    private Map<String, Integer> cardTypeCounts(Map<FactCardType, Integer> counts) {
        Map<String, Integer> values = new LinkedHashMap<String, Integer>();
        for (Map.Entry<FactCardType, Integer> entry : counts.entrySet()) {
            values.put(entry.getKey().name(), entry.getValue());
        }
        return values;
    }

    /**
     * 转换审查状态统计。
     *
     * @param counts 原始统计
     * @return 响应统计
     */
    private Map<String, Integer> reviewStatusCounts(Map<FactCardReviewStatus, Integer> counts) {
        Map<String, Integer> values = new LinkedHashMap<String, Integer>();
        for (Map.Entry<FactCardReviewStatus, Integer> entry : counts.entrySet()) {
            values.put(entry.getKey().databaseValue(), entry.getValue());
        }
        return values;
    }
}
