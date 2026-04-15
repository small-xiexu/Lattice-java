package com.xbk.lattice.api.admin;

import com.xbk.lattice.api.query.PendingQueryAnswerResponse;
import com.xbk.lattice.api.query.PendingQueryCorrectionRequest;
import com.xbk.lattice.api.query.PendingQueryStatusResponse;
import com.xbk.lattice.infra.persistence.PendingQueryRecord;
import com.xbk.lattice.query.service.PendingQueryManager;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

/**
 * 管理侧 pending 控制器
 *
 * 职责：暴露管理侧 pending 浏览与处理接口
 *
 * @author xiexu
 */
@RestController
@Profile("jdbc")
@RequestMapping("/api/v1/admin/pending")
public class AdminPendingController {

    private final PendingQueryManager pendingQueryManager;

    /**
     * 创建管理侧 pending 控制器。
     *
     * @param pendingQueryManager pending 查询管理器
     */
    public AdminPendingController(PendingQueryManager pendingQueryManager) {
        this.pendingQueryManager = pendingQueryManager;
    }

    /**
     * 返回管理侧 pending 列表。
     *
     * @return pending 列表
     */
    @GetMapping
    public AdminPendingResponse list() {
        List<PendingQueryRecord> pendingQueryRecords = pendingQueryManager.listPendingQueries();
        List<AdminPendingItemResponse> items = new ArrayList<AdminPendingItemResponse>();
        for (PendingQueryRecord pendingQueryRecord : pendingQueryRecords) {
            items.add(new AdminPendingItemResponse(
                    pendingQueryRecord.getQueryId(),
                    pendingQueryRecord.getQuestion(),
                    pendingQueryRecord.getAnswer(),
                    pendingQueryRecord.getReviewStatus(),
                    pendingQueryRecord.getSelectedConceptIds(),
                    pendingQueryRecord.getSourceFilePaths(),
                    pendingQueryRecord.getCreatedAt() == null ? null : pendingQueryRecord.getCreatedAt().toString(),
                    pendingQueryRecord.getExpiresAt() == null ? null : pendingQueryRecord.getExpiresAt().toString()
            ));
        }
        return new AdminPendingResponse(items.size(), items);
    }

    /**
     * 修正待确认查询答案。
     *
     * @param queryId 查询标识
     * @param correctionRequest 纠错请求
     * @return 纠错结果
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
