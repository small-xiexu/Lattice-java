package com.xbk.lattice.api.admin;

import com.xbk.lattice.governance.HistoryReport;
import com.xbk.lattice.governance.HistoryService;
import com.xbk.lattice.governance.RollbackResult;
import com.xbk.lattice.governance.SnapshotReport;
import com.xbk.lattice.governance.SnapshotService;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理侧快照控制器
 *
 * 职责：暴露文章级快照浏览与文章级回滚接口
 *
 * @author xiexu
 */
@RestController
@Profile("jdbc")
public class AdminSnapshotController {

    private final SnapshotService snapshotService;

    private final HistoryService historyService;

    /**
     * 创建管理侧快照控制器。
     *
     * @param snapshotService 快照服务
     * @param historyService 历史服务
     */
    public AdminSnapshotController(SnapshotService snapshotService, HistoryService historyService) {
        this.snapshotService = snapshotService;
        this.historyService = historyService;
    }

    /**
     * 返回文章级快照列表。
     *
     * @param conceptId 概念标识
     * @param limit 返回数量
     * @return 快照列表
     */
    @GetMapping("/api/v1/admin/snapshot/article")
    public AdminArticleSnapshotListResponse snapshots(
            @RequestParam(required = false) String conceptId,
            @RequestParam(defaultValue = "10") int limit
    ) {
        if (conceptId == null || conceptId.isBlank()) {
            SnapshotReport snapshotReport = snapshotService.snapshot(limit);
            return new AdminArticleSnapshotListResponse(null, snapshotReport.getTotalSnapshots(), snapshotReport.getItems());
        }
        HistoryReport historyReport = historyService.history(conceptId, limit);
        return new AdminArticleSnapshotListResponse(
                historyReport.getConceptId(),
                historyReport.getTotalEntries(),
                historyReport.getItems()
        );
    }

    /**
     * 执行文章级回滚。
     *
     * @param request 回滚请求
     * @return 回滚结果
     */
    @PostMapping("/api/v1/admin/rollback/article")
    public RollbackResult rollback(@RequestBody AdminArticleRollbackRequest request) {
        return snapshotService.rollback(request.getConceptId(), request.getSnapshotId());
    }
}
