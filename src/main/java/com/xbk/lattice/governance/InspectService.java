package com.xbk.lattice.governance;

import com.xbk.lattice.infra.persistence.PendingQueryJdbcRepository;
import com.xbk.lattice.infra.persistence.PendingQueryRecord;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Inspect 服务
 *
 * 职责：把当前待人工确认的问题转换成统一 inspection 清单
 *
 * @author xiexu
 */
@Service
public class InspectService {

    private static final String PENDING_INSPECTION_PREFIX = "pending:";

    private final PendingQueryJdbcRepository pendingQueryJdbcRepository;

    /**
     * 创建 Inspect 服务。
     *
     * @param pendingQueryJdbcRepository PendingQuery 仓储
     */
    public InspectService(PendingQueryJdbcRepository pendingQueryJdbcRepository) {
        this.pendingQueryJdbcRepository = pendingQueryJdbcRepository;
    }

    /**
     * 输出 inspection 问题清单。
     *
     * @return inspection 报告
     */
    public InspectionReport inspect() {
        if (pendingQueryJdbcRepository == null) {
            return new InspectionReport(List.of());
        }
        List<InspectionQuestion> questions = new ArrayList<InspectionQuestion>();
        for (PendingQueryRecord pendingQueryRecord : pendingQueryJdbcRepository.findAllActive()) {
            questions.add(toInspectionQuestion(pendingQueryRecord));
        }
        return new InspectionReport(questions);
    }

    private InspectionQuestion toInspectionQuestion(PendingQueryRecord pendingQueryRecord) {
        return new InspectionQuestion(
                PENDING_INSPECTION_PREFIX + pendingQueryRecord.getQueryId(),
                "pending_query",
                pendingQueryRecord.getQuestion(),
                "请确认答案是否准确，必要时给出最终答案",
                pendingQueryRecord.getAnswer(),
                pendingQueryRecord.getSourceFilePaths(),
                pendingQueryRecord.getReviewStatus(),
                pendingQueryRecord.getCreatedAt().toString(),
                pendingQueryRecord.getExpiresAt().toString()
        );
    }
}
