package com.xbk.lattice.api.admin;

import com.xbk.lattice.governance.InspectService;
import com.xbk.lattice.governance.InspectionAnswerImportService;
import com.xbk.lattice.governance.InspectionImportResult;
import com.xbk.lattice.governance.InspectionReport;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理侧 inspection 控制器
 *
 * 职责：暴露 inspection 清单与人工答案导入接口
 *
 * @author xiexu
 */
@RestController
@RequestMapping("/api/v1/admin/inspect")
public class AdminInspectController {

    private final InspectService inspectService;

    private final InspectionAnswerImportService inspectionAnswerImportService;

    /**
     * 创建管理侧 inspection 控制器。
     *
     * @param inspectService inspect 服务
     * @param inspectionAnswerImportService 答案导入服务
     */
    public AdminInspectController(
            InspectService inspectService,
            InspectionAnswerImportService inspectionAnswerImportService
    ) {
        this.inspectService = inspectService;
        this.inspectionAnswerImportService = inspectionAnswerImportService;
    }

    /**
     * 返回 inspection 清单。
     *
     * @return inspection 报告
     */
    @GetMapping
    public InspectionReport inspect() {
        return inspectService.inspect();
    }

    /**
     * 导入人工最终答案。
     *
     * @param request 导入请求
     * @return 导入结果
     */
    @PostMapping("/import-answers")
    public InspectionImportResult importAnswers(@RequestBody AdminInspectImportRequest request) {
        return inspectionAnswerImportService.importAnswer(
                request.getInspectionId(),
                request.getFinalAnswer(),
                request.getConfirmedBy()
        );
    }
}
