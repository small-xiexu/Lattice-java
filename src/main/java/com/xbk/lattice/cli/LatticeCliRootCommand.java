package com.xbk.lattice.cli;

import picocli.CommandLine;

/**
 * CLI 根命令
 *
 * 职责：承载命令行程序的顶层描述与子命令注册
 *
 * @author xiexu
 */
@CommandLine.Command(
        name = "lattice-java",
        mixinStandardHelpOptions = true,
        version = "1.0-SNAPSHOT",
        description = "Lattice Java CLI"
)
public class LatticeCliRootCommand implements Runnable {

    @Override
    public void run() {
        CommandLine.usage(this, System.out);
    }
}
