package com.xbk.lattice.api.admin;

import com.xbk.lattice.governance.LinkEnhancementReport;
import com.xbk.lattice.governance.LinkEnhancementService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理侧链接增强控制器
 *
 * 职责：暴露 wiki-link 修复与受管关系区块同步接口
 *
 * @author xiexu
 */
@RestController
@RequestMapping("/api/v1/admin/link-enhance")
public class AdminLinkEnhanceController {

    private final LinkEnhancementService linkEnhancementService;

    public AdminLinkEnhanceController(LinkEnhancementService linkEnhancementService) {
        this.linkEnhancementService = linkEnhancementService;
    }

    /**
     * 执行链接增强。
     *
     * @param request 请求
     * @return 增强报告
     */
    @PostMapping
    public LinkEnhancementReport enhance(@RequestBody(required = false) AdminLinkEnhanceRequest request) {
        boolean persist = request != null && request.isPersist();
        return linkEnhancementService.enhance(persist);
    }
}
