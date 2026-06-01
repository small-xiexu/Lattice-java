package com.xbk.lattice.source.domain;

import lombok.Getter;

import java.util.List;
import java.util.Map;

/**
 * 资料包摘要。
 *
 * <p>承载统一上传识别所需的结构化 bundle 特征——文件结构、内容画像、关键词和指纹信息。
 * 用于 source 自动识别时的匹配判定和展示名解析。
 *
 * @author xiexu
 */
@Getter
public class BundleSummary {

    /** 展示名称。为 null 时由 controller 从文件/目录名推断。 */
    private final String displayName;
    /** 文件数量。 */
    private final int fileCount;
    /** 目录数量。 */
    private final int dirCount;
    /** 顶层文件/目录名列表。 */
    private final List<String> topLevelNames;
    /** 扩展名分布（key=扩展名, value=数量）。 */
    private final Map<String, Integer> extensionDistribution;
    /** 相对路径样本列表（截断）。 */
    private final List<String> relativePathsSample;
    /** 签名文件列表（如 README.md、LICENSE）。 */
    private final List<String> signatureFiles;
    /** 内容画像（如 code / document / mixed）。 */
    private final String contentProfile;
    /** 关键词列表。 */
    private final List<String> keywords;
    /** 标题提示列表（从文件名/内容首行提取）。 */
    private final List<String> titleHints;
    /** 路径结构指纹（用于检测目录结构变更）。 */
    private final String pathFingerprint;
    /** 内容指纹（用于检测文件内容变更）。 */
    private final String contentFingerprint;
    /** 摘要文本（自动生成的结构化描述）。 */
    private final String summaryText;

    public BundleSummary(
            String displayName, int fileCount, int dirCount, List<String> topLevelNames,
            Map<String, Integer> extensionDistribution, List<String> relativePathsSample,
            List<String> signatureFiles, String contentProfile, List<String> keywords,
            List<String> titleHints, String pathFingerprint, String contentFingerprint, String summaryText
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
}
