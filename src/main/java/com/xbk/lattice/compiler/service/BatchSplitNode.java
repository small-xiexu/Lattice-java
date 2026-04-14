package com.xbk.lattice.compiler.service;

import com.xbk.lattice.compiler.config.CompilerProperties;
import com.xbk.lattice.compiler.model.RawSource;
import com.xbk.lattice.compiler.model.SourceBatch;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;

/**
 * 分批切割节点
 *
 * 职责：根据字符上限把同组源文件切分为多个稳定批次
 *
 * @author xiexu
 */
public class BatchSplitNode {

    private final CompilerProperties compilerProperties;

    /**
     * 创建分批切割节点。
     *
     * @param compilerProperties 编译配置
     */
    public BatchSplitNode(CompilerProperties compilerProperties) {
        this.compilerProperties = compilerProperties;
    }

    /**
     * 按字符数对同组源文件切分批次。
     *
     * @param groupKey 分组键
     * @param rawSources 源文件集合
     * @return 批次列表
     */
    public List<SourceBatch> split(String groupKey, List<RawSource> rawSources) {
        List<SourceBatch> batches = new ArrayList<SourceBatch>();
        List<RawSource> currentSources = new ArrayList<RawSource>();
        int currentChars = 0;
        int batchIndex = 0;

        for (RawSource rawSource : rawSources) {
            int sourceChars = rawSource.getContent().length();
            if (!currentSources.isEmpty() && currentChars + sourceChars > compilerProperties.getBatchMaxChars()) {
                batches.add(createBatch(groupKey, batchIndex, currentSources));
                batchIndex++;
                currentSources = new ArrayList<RawSource>();
                currentChars = 0;
            }
            currentSources.add(rawSource);
            currentChars += sourceChars;
        }

        if (!currentSources.isEmpty()) {
            batches.add(createBatch(groupKey, batchIndex, currentSources));
        }
        return batches;
    }

    /**
     * 创建单个批次。
     *
     * @param groupKey 分组键
     * @param batchIndex 批次序号
     * @param rawSources 源文件集合
     * @return 源文件批次
     */
    private SourceBatch createBatch(String groupKey, int batchIndex, List<RawSource> rawSources) {
        List<RawSource> batchSources = new ArrayList<RawSource>(rawSources);
        String batchId = buildBatchId(groupKey, batchIndex, batchSources);
        return new SourceBatch(batchId, groupKey, batchSources);
    }

    /**
     * 生成稳定批次标识。
     *
     * @param groupKey 分组键
     * @param batchIndex 批次序号
     * @param rawSources 源文件集合
     * @return 批次标识
     */
    private String buildBatchId(String groupKey, int batchIndex, List<RawSource> rawSources) {
        StringBuilder builder = new StringBuilder();
        builder.append(groupKey).append(':').append(batchIndex);
        for (RawSource rawSource : rawSources) {
            builder.append(':').append(rawSource.getRelativePath());
        }
        return sha256(builder.toString());
    }

    /**
     * 计算 SHA-256。
     *
     * @param value 原始字符串
     * @return SHA-256 十六进制结果
     */
    private String sha256(String value) {
        try {
            MessageDigest messageDigest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = messageDigest.digest(value.getBytes(StandardCharsets.UTF_8));
            return toHex(bytes);
        }
        catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 不可用", ex);
        }
    }

    /**
     * 把字节数组转为十六进制字符串。
     *
     * @param bytes 字节数组
     * @return 十六进制字符串
     */
    private String toHex(byte[] bytes) {
        StringBuilder builder = new StringBuilder();
        for (byte value : bytes) {
            builder.append(String.format("%02x", value));
        }
        return builder.toString();
    }
}
