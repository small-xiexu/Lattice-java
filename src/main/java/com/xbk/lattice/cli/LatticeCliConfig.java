package com.xbk.lattice.cli;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * CLI 配置
 *
 * 职责：标识命令行独立模式专用 profile
 *
 * @author xiexu
 */
@Configuration
@Profile("cli")
public class LatticeCliConfig {
}
