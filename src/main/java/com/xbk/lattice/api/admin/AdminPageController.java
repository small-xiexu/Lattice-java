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
     * 转发管理后台首页。
     *
     * @return 静态页面路径
     */
    @GetMapping({"/admin", "/admin/"})
    public String index() {
        return "forward:/admin/index.html";
    }
}
