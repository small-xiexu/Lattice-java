package com.xbk.lattice.cli;

/**
 * CLI 退出码常量
 *
 * 职责：统一命令行返回码，便于脚本与 CI 判断执行结果
 *
 * @author xiexu
 */
public final class CliExitCodes {

    public static final int SUCCESS = 0;

    public static final int INVALID_ARGUMENT = 2;

    public static final int EXECUTION_FAILED = 3;

    private CliExitCodes() {
    }
}
