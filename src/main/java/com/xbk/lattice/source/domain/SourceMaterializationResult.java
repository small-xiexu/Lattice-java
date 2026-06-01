package com.xbk.lattice.source.domain;

import lombok.Getter;

import java.nio.file.Path;

/**
 * 资料源物化结果。
 *
 * <p>承载 Git 资料源被物化后的工作目录与元数据——staging 目录路径和物化上下文 JSON。
 *
 * @author xiexu
 */
@Getter
public class SourceMaterializationResult {

    /**
     * staging 工作目录路径（{@link Path}）。
     *
     * <p>包含物化后的文件树，后续编译步骤从此目录读取源文件。
     * 应做路径规范化校验。</p>
     */
    private final Path stagingDir;

    /** 物化元数据 JSON（含 repo URL、branch、commit 等信息）。可能较大。 */
    private final String metadataJson;

    public SourceMaterializationResult(Path stagingDir, String metadataJson) {
        this.stagingDir = stagingDir;
        this.metadataJson = metadataJson;
    }
}
