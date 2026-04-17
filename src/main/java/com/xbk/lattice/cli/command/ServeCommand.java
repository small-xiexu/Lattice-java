package com.xbk.lattice.cli.command;

import com.xbk.lattice.LatticeApplication;
import com.xbk.lattice.cli.CliExitCodes;
import picocli.CommandLine;

/**
 * serve 命令
 *
 * 职责：通过 CLI 委托启动 Web 服务
 *
 * @author xiexu
 */
@CommandLine.Command(name = "serve", description = "启动 Web 服务")
public class ServeCommand extends AbstractCliCommand {

    @Override
    protected Integer runInStandaloneMode() {
        LatticeApplication.main(new String[0]);
        return CliExitCodes.SUCCESS;
    }
}
