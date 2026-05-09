package com.xbk.lattice.compiler.service;

import java.util.ArrayList;
import java.util.List;

/**
 * 事实卡生成用 source chunk 视图
 *
 * 职责：承载 source chunk 及相邻窗口合并后的证据文本
 *
 * @author xiexu
 */
public class FactCardSourceChunkView {

    private final Long id;

    private final Long sourceId;

    private final Long sourceFileId;

    private final String filePath;

    private final int chunkIndex;

    private final String chunkText;

    private final List<Long> sourceChunkIds;

    /**
     * 创建 source chunk 视图。
     *
     * @param id chunk 主键
     * @param sourceId 资料源主键
     * @param sourceFileId 源文件主键
     * @param filePath 文件路径
     * @param chunkIndex chunk 序号
     * @param chunkText chunk 文本
     */
    public FactCardSourceChunkView(
            Long id,
            Long sourceId,
            Long sourceFileId,
            String filePath,
            int chunkIndex,
            String chunkText
    ) {
        this(id, sourceId, sourceFileId, filePath, chunkIndex, chunkText, List.of(id));
    }

    /**
     * 创建 source chunk 视图。
     *
     * @param id chunk 主键
     * @param sourceId 资料源主键
     * @param sourceFileId 源文件主键
     * @param filePath 文件路径
     * @param chunkIndex chunk 序号
     * @param chunkText chunk 文本
     * @param sourceChunkIds 窗口包含的 chunk 主键
     */
    public FactCardSourceChunkView(
            Long id,
            Long sourceId,
            Long sourceFileId,
            String filePath,
            int chunkIndex,
            String chunkText,
            List<Long> sourceChunkIds
    ) {
        this.id = id;
        this.sourceId = sourceId;
        this.sourceFileId = sourceFileId;
        this.filePath = filePath;
        this.chunkIndex = chunkIndex;
        this.chunkText = chunkText;
        this.sourceChunkIds = sourceChunkIds == null ? List.of() : List.copyOf(sourceChunkIds);
    }

    /**
     * 合并相邻 chunk 为证据窗口。
     *
     * @param nextChunk 下一个 chunk
     * @return 合并后的证据窗口
     */
    public FactCardSourceChunkView mergeWith(FactCardSourceChunkView nextChunk) {
        List<Long> mergedSourceChunkIds = new ArrayList<Long>(sourceChunkIds);
        mergedSourceChunkIds.addAll(nextChunk.getSourceChunkIds());
        String mergedChunkText = joinChunkText(chunkText, nextChunk.getChunkText());
        return new FactCardSourceChunkView(
                id,
                sourceId,
                sourceFileId,
                filePath,
                chunkIndex,
                mergedChunkText,
                mergedSourceChunkIds
        );
    }

    /**
     * 获取 chunk 主键。
     *
     * @return chunk 主键
     */
    public Long getId() {
        return id;
    }

    /**
     * 获取资料源主键。
     *
     * @return 资料源主键
     */
    public Long getSourceId() {
        return sourceId;
    }

    /**
     * 获取源文件主键。
     *
     * @return 源文件主键
     */
    public Long getSourceFileId() {
        return sourceFileId;
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
     * 获取 chunk 序号。
     *
     * @return chunk 序号
     */
    public int getChunkIndex() {
        return chunkIndex;
    }

    /**
     * 获取 chunk 文本。
     *
     * @return chunk 文本
     */
    public String getChunkText() {
        return chunkText;
    }

    /**
     * 获取窗口包含的 chunk 主键。
     *
     * @return chunk 主键列表
     */
    public List<Long> getSourceChunkIds() {
        return sourceChunkIds;
    }

    private String joinChunkText(String previousText, String nextText) {
        String previous = previousText == null ? "" : previousText.stripTrailing();
        String next = nextText == null ? "" : nextText.stripLeading();
        if (previous.isBlank()) {
            return next;
        }
        if (next.isBlank()) {
            return previous;
        }
        return previous + "\n" + next;
    }
}
