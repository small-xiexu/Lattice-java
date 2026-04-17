package com.xbk.lattice.api.admin;

import com.xbk.lattice.governance.CoverageReport;
import com.xbk.lattice.governance.CoverageTrackingService;
import com.xbk.lattice.governance.OmissionReport;
import com.xbk.lattice.governance.OmissionTrackingService;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理侧覆盖率控制器
 *
 * 职责：暴露覆盖率与遗漏报告接口
 *
 * @author xiexu
 */
@RestController
@Profile("jdbc")
public class AdminCoverageController {

    private final CoverageTrackingService coverageTrackingService;

    private final OmissionTrackingService omissionTrackingService;

    /**
     * 创建管理侧覆盖率控制器。
     *
     * @param coverageTrackingService 覆盖率服务
     * @param omissionTrackingService 遗漏服务
     */
    public AdminCoverageController(
            CoverageTrackingService coverageTrackingService,
            OmissionTrackingService omissionTrackingService
    ) {
        this.coverageTrackingService = coverageTrackingService;
        this.omissionTrackingService = omissionTrackingService;
    }

    /**
     * 返回覆盖率报告。
     *
     * @return 覆盖率报告
     */
    @GetMapping("/api/v1/admin/coverage")
    public CoverageReport coverage() {
        return coverageTrackingService.measure();
    }

    /**
     * 返回遗漏报告。
     *
     * @return 遗漏报告
     */
    @GetMapping("/api/v1/admin/omissions")
    public OmissionReport omissions() {
        return omissionTrackingService.track();
    }
}
