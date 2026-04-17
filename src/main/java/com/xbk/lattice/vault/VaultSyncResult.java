package com.xbk.lattice.vault;

import java.util.List;

/**
 * Vault 回写结果
 *
 * 职责：承载受控回写的同步、跳过与冲突统计
 *
 * @author xiexu
 */
public class VaultSyncResult {

    private final String vaultDir;

    private final int syncedFiles;

    private final int skippedFiles;

    private final List<VaultConflictReport> conflicts;

    public VaultSyncResult(
            String vaultDir,
            int syncedFiles,
            int skippedFiles,
            List<VaultConflictReport> conflicts
    ) {
        this.vaultDir = vaultDir;
        this.syncedFiles = syncedFiles;
        this.skippedFiles = skippedFiles;
        this.conflicts = conflicts;
    }

    public String getVaultDir() {
        return vaultDir;
    }

    public int getSyncedFiles() {
        return syncedFiles;
    }

    public int getSkippedFiles() {
        return skippedFiles;
    }

    public List<VaultConflictReport> getConflicts() {
        return conflicts;
    }

    public int getConflictCount() {
        return conflicts == null ? 0 : conflicts.size();
    }
}
