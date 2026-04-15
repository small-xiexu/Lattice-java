package com.xbk.lattice.infra.persistence;

/**
 * 源文件分块记录
 *
 * 职责：表示源文件检索层的单个 chunk
 *
 * @author xiexu
 */
public class SourceFileChunkRecord {

    private final String filePath;

    private final int chunkIndex;

    private final String chunkText;

    private final boolean verbatim;

    /**
     * 创建源文件分块记录。
     *
     * @param filePath 文件路径
     * @param chunkIndex 分块序号
     * @param chunkText 分块文本
     * @param verbatim 是否按原文保留
     */
    public SourceFileChunkRecord(String filePath, int chunkIndex, String chunkText, boolean verbatim) {
        this.filePath = filePath;
        this.chunkIndex = chunkIndex;
        this.chunkText = chunkText;
        this.verbatim = verbatim;
    }

    /**
     * 获取文件路径。
     *
     * @return 文件路径
     */
    public String getFilePath() {
        return filePath;
    }

    /**
     * 获取分块序号。
     *
     * @return 分块序号
     */
    public int getChunkIndex() {
        return chunkIndex;
    }

    /**
     * 获取分块文本。
     *
     * @return 分块文本
     */
    public String getChunkText() {
        return chunkText;
    }

    /**
     * 是否按原文保留。
     *
     * @return 是否按原文保留
     */
    public boolean isVerbatim() {
        return verbatim;
    }
}
