package com.xbk.lattice.api.admin;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 管理后台页面控制器
 *
 * 职责：提供内嵌式管理后台页面访问入口
 *
 * @author xiexu
 */
@Controller
public class AdminPageController {

    /**
     * 转发知识库管理页。
     *
     * @return 静态页面路径
     */
    @GetMapping({"/admin", "/admin/"})
    public String index() {
        return "forward:/admin/index.html";
    }

    /**
     * 转发知识问答页。
     *
     * @return 静态页面路径
     */
    @GetMapping({"/admin/ask", "/admin/ask/"})
    public String ask() {
        return "forward:/admin/ask.html";
    }

    /**
     * 转发管理员设置页。
     *
     * @return 静态页面路径
     */
    @GetMapping({"/admin/settings", "/admin/settings/"})
    public String settings() {
        return "forward:/admin/settings.html";
    }

    /**
     * 转发开发者接入页。
     *
     * @return 静态页面路径
     */
    @GetMapping({"/admin/developer-access", "/admin/developer-access/"})
    public String developerAccess() {
        return "forward:/admin/developer-access.html";
    }
}
