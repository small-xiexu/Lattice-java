package com.xbk.lattice.vault;

/**
 * Vault 冲突报告
 *
 * 职责：承载单个文件在受控回写中的冲突摘要
 *
 * @author xiexu
 */
public class VaultConflictReport {

    private final String filePath;

    private final String reason;

    private final String manifestHash;

    private final String currentDbHash;

    private final String currentFileHash;

    public VaultConflictReport(
            String filePath,
            String reason,
            String manifestHash,
            String currentDbHash,
            String currentFileHash
    ) {
        this.filePath = filePath;
        this.reason = reason;
        this.manifestHash = manifestHash;
        this.currentDbHash = currentDbHash;
        this.currentFileHash = currentFileHash;
    }

    public String getFilePath() {
        return filePath;
    }

    public String getReason() {
        return reason;
    }

    public String getManifestHash() {
        return manifestHash;
    }

    public String getCurrentDbHash() {
        return currentDbHash;
    }

    public String getCurrentFileHash() {
        return currentFileHash;
    }
}
