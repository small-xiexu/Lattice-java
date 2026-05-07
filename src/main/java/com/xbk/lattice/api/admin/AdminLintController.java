package com.xbk.lattice.api.admin;

import com.xbk.lattice.governance.LintFixResult;
import com.xbk.lattice.governance.LintFixService;
import com.xbk.lattice.governance.LintReport;
import com.xbk.lattice.governance.LintService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理侧 lint 控制器
 *
 * 职责：暴露治理检查与自动修复接口
 *
 * @author xiexu
 */
@RestController
@RequestMapping("/api/v1/admin/lint")
public class AdminLintController {

    private final LintService lintService;

    private final LintFixService lintFixService;

    /**
     * 创建管理侧 lint 控制器。
     *
     * @param lintService lint 服务
     * @param lintFixService lint fix 服务
     */
    public AdminLintController(LintService lintService, LintFixService lintFixService) {
        this.lintService = lintService;
        this.lintFixService = lintFixService;
    }

    /**
     * 返回当前 lint 报告。
     *
     * @return lint 报告
     */
    @GetMapping
    public LintReport lint() {
        return lintService.lint();
    }

    /**
     * 执行 lint 自动修复。
     *
     * @param request 修复请求
     * @return 修复结果
     */
    @PostMapping("/fix")
    public LintFixResult fix(@RequestBody(required = false) AdminLintFixRequest request) {
        LintReport report = lintService.lint();
        if (request == null) {
            return lintFixService.fix(report);
        }
        return lintFixService.fix(report, request.getTargetIds());
    }
}
