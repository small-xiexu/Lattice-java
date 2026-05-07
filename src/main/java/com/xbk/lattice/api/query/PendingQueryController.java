package com.xbk.lattice.api.query;

import com.xbk.lattice.infra.persistence.PendingQueryRecord;
import com.xbk.lattice.query.service.PendingQueryManager;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * PendingQuery 控制器
 *
 * 职责：暴露纠错、确认与丢弃待确认查询的接口
 *
 * @author xiexu
 */
@RestController
@RequestMapping("/api/v1/query")
public class PendingQueryController {

    private final PendingQueryManager pendingQueryManager;

    /**
     * 创建 PendingQuery 控制器。
     *
     * @param pendingQueryManager PendingQuery 管理器
     */
    public PendingQueryController(PendingQueryManager pendingQueryManager) {
        this.pendingQueryManager = pendingQueryManager;
    }

    /**
     * 纠正待确认查询答案。
     *
     * @param queryId 查询标识
     * @param correctionRequest 纠错请求
     * @return 纠错后的答案响应
     */
    @PostMapping("/{queryId}/correct")
    public PendingQueryAnswerResponse correct(
            @PathVariable String queryId,
            @RequestBody PendingQueryCorrectionRequest correctionRequest
    ) {
        PendingQueryRecord pendingQueryRecord = pendingQueryManager.correct(queryId, correctionRequest.getCorrection());
        return new PendingQueryAnswerResponse(
                pendingQueryRecord.getQueryId(),
                pendingQueryRecord.getAnswer(),
                "PENDING"
        );
    }

    /**
     * 确认待确认查询。
     *
     * @param queryId 查询标识
     * @return 执行状态
     */
    @PostMapping("/{queryId}/confirm")
    public PendingQueryStatusResponse confirm(@PathVariable String queryId) {
        pendingQueryManager.confirm(queryId);
        return new PendingQueryStatusResponse("CONFIRMED");
    }

    /**
     * 丢弃待确认查询。
     *
     * @param queryId 查询标识
     * @return 执行状态
     */
    @PostMapping("/{queryId}/discard")
    public PendingQueryStatusResponse discard(@PathVariable String queryId) {
        pendingQueryManager.discard(queryId);
        return new PendingQueryStatusResponse("DISCARDED");
    }
}
