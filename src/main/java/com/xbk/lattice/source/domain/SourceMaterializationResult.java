package com.xbk.lattice.source.domain;

import java.nio.file.Path;

/**
 * 资料源物化结果。
 *
 * 职责：承载 Git / SERVER_DIR 资料源被物化后的工作目录与元数据
 *
 * @author xiexu
 */
public class SourceMaterializationResult {

    private final Path stagingDir;

    private final String metadataJson;

    /**
     * 创建资料源物化结果。
     *
     * @param stagingDir staging 目录
     * @param metadataJson 物化元数据 JSON
     */
    public SourceMaterializationResult(Path stagingDir, String metadataJson) {
        this.stagingDir = stagingDir;
        this.metadataJson = metadataJson;
    }

    public Path getStagingDir() {
        return stagingDir;
    }

    public String getMetadataJson() {
        return metadataJson;
    }
}
