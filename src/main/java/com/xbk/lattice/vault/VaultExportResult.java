package com.xbk.lattice.vault;

/**
 * Vault 导出结果
 *
 * 职责：承载单次 Vault 导出的写入、跳过与删除统计
 *
 * @author xiexu
 */
public class VaultExportResult {

    private final String vaultDir;

    private final int writtenFiles;

    private final int skippedFiles;

    private final int deletedFiles;

    /**
     * 创建 Vault 导出结果。
     *
     * @param vaultDir Vault 目录
     * @param writtenFiles 写入文件数
     * @param skippedFiles 跳过文件数
     * @param deletedFiles 删除文件数
     */
    public VaultExportResult(String vaultDir, int writtenFiles, int skippedFiles, int deletedFiles) {
        this.vaultDir = vaultDir;
        this.writtenFiles = writtenFiles;
        this.skippedFiles = skippedFiles;
        this.deletedFiles = deletedFiles;
    }

    public String getVaultDir() {
        return vaultDir;
    }

    public int getWrittenFiles() {
        return writtenFiles;
    }

    public int getSkippedFiles() {
        return skippedFiles;
    }

    public int getDeletedFiles() {
        return deletedFiles;
    }
}
