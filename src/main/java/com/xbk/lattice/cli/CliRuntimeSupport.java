package com.xbk.lattice.cli;

import com.xbk.lattice.LatticeApplication;
import org.springframework.boot.Banner;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

/**
 * CLI 运行时支持
 *
 * 职责：为独立模式命令创建轻量 Spring 上下文，并统一异常处理
 *
 * @author xiexu
 */
public final class CliRuntimeSupport {

    private CliRuntimeSupport() {
    }

    /**
     * 在 CLI 独立模式上下文中执行命令。
     *
     * @param callback 回调逻辑
     * @return 退出码
     */
    public static int runWithContext(Callable<Integer> callback) {
        try {
            return callback.call();
        }
        catch (IllegalArgumentException exception) {
            System.err.println(exception.getMessage());
            return CliExitCodes.INVALID_ARGUMENT;
        }
        catch (Exception exception) {
            exception.printStackTrace(System.err);
            return CliExitCodes.EXECUTION_FAILED;
        }
    }

    /**
     * 创建 CLI 轻量上下文。
     *
     * @return Spring 上下文
     */
    public static ConfigurableApplicationContext createContext() {
        return createContext(List.of("jdbc", "cli"), List.of(
                "spring.main.lazy-initialization=true",
                "lattice.compiler.jobs.worker-enabled=false"
        ));
    }

    /**
     * 创建带附加 profile 与属性的 CLI 轻量上下文。
     *
     * @param profiles 激活 profile 列表
     * @param properties 附加属性列表
     * @return Spring 上下文
     */
    public static ConfigurableApplicationContext createContext(List<String> profiles, List<String> properties) {
        List<String> activeProfiles = new ArrayList<String>(profiles);
        List<String> activeProperties = new ArrayList<String>(properties);
        return new SpringApplicationBuilder(LatticeApplication.class, LatticeCliConfig.class)
                .web(WebApplicationType.NONE)
                .profiles(activeProfiles.toArray(new String[0]))
                .bannerMode(Banner.Mode.OFF)
                .properties(activeProperties.toArray(new String[0]))
                .run();
    }
}
