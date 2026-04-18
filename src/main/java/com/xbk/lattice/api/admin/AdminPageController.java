package com.xbk.lattice.api.admin;

import org.springframework.context.annotation.Profile;
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
@Profile("jdbc")
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
     * 转发 AI 接入页。
     *
     * @return 静态页面路径
     */
    @GetMapping({"/admin/ai", "/admin/ai/"})
    public String ai() {
        return "forward:/admin/ai.html";
    }
}
