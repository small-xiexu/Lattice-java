package com.xbk.lattice.source.domain;

import java.util.List;
import java.util.Map;

/**
 * 资料包摘要。
 *
 * 职责：承载统一上传识别所需的结构化 bundle 特征
 *
 * @author xiexu
 */
public class BundleSummary {

    private final String displayName;

    private final int fileCount;

    private final int dirCount;

    private final List<String> topLevelNames;

    private final Map<String, Integer> extensionDistribution;

    private final List<String> relativePathsSample;

    private final List<String> signatureFiles;

    private final String contentProfile;

    private final List<String> keywords;

    private final List<String> titleHints;

    private final String pathFingerprint;

    private final String contentFingerprint;

    private final String summaryText;

    /**
     * 创建资料包摘要。
     *
     * @param displayName 展示名称
     * @param fileCount 文件数量
     * @param dirCount 目录数量
     * @param topLevelNames 顶层名称
     * @param extensionDistribution 扩展名分布
     * @param relativePathsSample 相对路径样本
     * @param signatureFiles 签名文件
     * @param contentProfile 内容画像
     * @param keywords 关键词
     * @param titleHints 标题提示
     * @param pathFingerprint 路径指纹
     * @param contentFingerprint 内容指纹
     * @param summaryText 摘要文本
     */
    public BundleSummary(
            String displayName,
            int fileCount,
            int dirCount,
            List<String> topLevelNames,
            Map<String, Integer> extensionDistribution,
            List<String> relativePathsSample,
            List<String> signatureFiles,
            String contentProfile,
            List<String> keywords,
            List<String> titleHints,
            String pathFingerprint,
            String contentFingerprint,
            String summaryText
    ) {
        this.displayName = displayName;
        this.fileCount = fileCount;
        this.dirCount = dirCount;
        this.topLevelNames = topLevelNames;
        this.extensionDistribution = extensionDistribution;
        this.relativePathsSample = relativePathsSample;
        this.signatureFiles = signatureFiles;
        this.contentProfile = contentProfile;
        this.keywords = keywords;
        this.titleHints = titleHints;
        this.pathFingerprint = pathFingerprint;
        this.contentFingerprint = contentFingerprint;
        this.summaryText = summaryText;
    }

    public String getDisplayName() {
        return displayName;
    }

    public int getFileCount() {
        return fileCount;
    }

    public int getDirCount() {
        return dirCount;
    }

    public List<String> getTopLevelNames() {
        return topLevelNames;
    }

    public Map<String, Integer> getExtensionDistribution() {
        return extensionDistribution;
    }

    public List<String> getRelativePathsSample() {
        return relativePathsSample;
    }

    public List<String> getSignatureFiles() {
        return signatureFiles;
    }

    public String getContentProfile() {
        return contentProfile;
    }

    public List<String> getKeywords() {
        return keywords;
    }

    public List<String> getTitleHints() {
        return titleHints;
    }

    public String getPathFingerprint() {
        return pathFingerprint;
    }

    public String getContentFingerprint() {
        return contentFingerprint;
    }

    public String getSummaryText() {
        return summaryText;
    }
}
