package com.xbk.lattice.vault.snapshot;

/**
 * Vault Git 文件差异摘要
 *
 * 职责：承载单个文件在两次 Vault Git 提交间的变更类型
 *
 * @author xiexu
 */
public class VaultDiffSummary {

    private final String filePath;

    private final String changeType;

    /**
     * 创建 Vault Git 文件差异摘要。
     *
     * @param filePath 文件路径
     * @param changeType 变更类型
     */
    public VaultDiffSummary(String filePath, String changeType) {
        this.filePath = filePath;
        this.changeType = changeType;
    }

    public String getFilePath() {
        return filePath;
    }

    public String getChangeType() {
        return changeType;
    }
}
