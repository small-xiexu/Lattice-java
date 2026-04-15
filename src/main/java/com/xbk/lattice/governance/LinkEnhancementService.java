package com.xbk.lattice.governance;

import com.xbk.lattice.infra.persistence.ArticleJdbcRepository;
import com.xbk.lattice.infra.persistence.ArticleRecord;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 链接增强服务
 *
 * 职责：修复标题型 broken wiki-links，并把 related / depends_on 同步为受管正文区块
 *
 * @author xiexu
 */
@Service
@Profile("jdbc")
public class LinkEnhancementService {

    private static final Pattern WIKI_LINK_PATTERN = Pattern.compile("\\[\\[([^\\]]+)\\]\\]");

    private static final Pattern AUTO_DEPENDS_BLOCK_PATTERN = Pattern.compile(
            "(?s)\\n*<!-- lattice:auto-depends-on:start -->.*?<!-- lattice:auto-depends-on:end -->\\n*"
    );

    private static final Pattern AUTO_RELATED_BLOCK_PATTERN = Pattern.compile(
            "(?s)\\n*<!-- lattice:auto-related:start -->.*?<!-- lattice:auto-related:end -->\\n*"
    );

    private final ArticleJdbcRepository articleJdbcRepository;

    /**
     * 创建链接增强服务。
     *
     * @param articleJdbcRepository 文章仓储
     */
    public LinkEnhancementService(ArticleJdbcRepository articleJdbcRepository) {
        this.articleJdbcRepository = articleJdbcRepository;
    }

    /**
     * 执行链接增强，可选是否持久化。
     *
     * @param persist 是否落库
     * @return 链接增强报告
     */
    @Transactional(rollbackFor = Exception.class)
    public LinkEnhancementReport enhance(boolean persist) {
        List<ArticleRecord> articleRecords = articleJdbcRepository.findAll();
        Map<String, String> conceptTitles = collectConceptTitles(articleRecords);
        Map<String, String> conceptIdsByTitle = collectConceptIdsByTitle(articleRecords);
        Set<String> conceptIds = new LinkedHashSet<String>(conceptTitles.keySet());

        List<LinkEnhancementItem> items = new ArrayList<LinkEnhancementItem>();
        int updatedArticleCount = 0;
        int fixedLinkCount = 0;
        int syncedSectionCount = 0;
        int unresolvedLinkCount = 0;

        for (ArticleRecord articleRecord : articleRecords) {
            EnhancementComputation computation = enhanceArticle(articleRecord, conceptIds, conceptIdsByTitle, conceptTitles);
            if (persist && computation.updatedRecord != null) {
                articleJdbcRepository.upsert(computation.updatedRecord);
            }
            if (computation.item.isUpdated()) {
                updatedArticleCount++;
            }
            fixedLinkCount += computation.item.getFixedLinkCount();
            syncedSectionCount += computation.item.getSyncedSectionCount();
            unresolvedLinkCount += computation.item.getUnresolvedLinks().size();
            items.add(computation.item);
        }

        return new LinkEnhancementReport(
                articleRecords.size(),
                articleRecords.size(),
                updatedArticleCount,
                fixedLinkCount,
                syncedSectionCount,
                unresolvedLinkCount,
                items
        );
    }

    /**
     * 计算单篇文章的增强结果。
     *
     * @param articleRecord 文章记录
     * @param conceptIds 已知概念标识
     * @param conceptIdsByTitle 标题到概念标识映射
     * @param conceptTitles 概念标题映射
     * @return 增强计算结果
     */
    private EnhancementComputation enhanceArticle(
            ArticleRecord articleRecord,
            Set<String> conceptIds,
            Map<String, String> conceptIdsByTitle,
            Map<String, String> conceptTitles
    ) {
        LinkRepairResult repairedResult = repairWikiLinks(articleRecord.getContent(), conceptIds, conceptIdsByTitle);
        ManagedBlockResult dependsOnResult = syncManagedBlock(
                repairedResult.content,
                AUTO_DEPENDS_BLOCK_PATTERN,
                buildManagedBlock(
                        "lattice:auto-depends-on",
                        "Depends On",
                        articleRecord.getDependsOn(),
                        conceptTitles
                )
        );
        ManagedBlockResult relatedResult = syncManagedBlock(
                dependsOnResult.content,
                AUTO_RELATED_BLOCK_PATTERN,
                buildManagedBlock(
                        "lattice:auto-related",
                        "Related Concepts",
                        articleRecord.getRelated(),
                        conceptTitles
                )
        );

        boolean updated = !articleRecord.getContent().equals(relatedResult.content);
        ArticleRecord updatedRecord = null;
        if (updated) {
            updatedRecord = new ArticleRecord(
                    articleRecord.getConceptId(),
                    articleRecord.getTitle(),
                    relatedResult.content,
                    articleRecord.getLifecycle(),
                    articleRecord.getCompiledAt(),
                    articleRecord.getSourcePaths(),
                    articleRecord.getMetadataJson(),
                    articleRecord.getSummary(),
                    articleRecord.getReferentialKeywords(),
                    articleRecord.getDependsOn(),
                    articleRecord.getRelated(),
                    articleRecord.getConfidence(),
                    articleRecord.getReviewStatus()
            );
        }

        LinkEnhancementItem item = new LinkEnhancementItem(
                articleRecord.getConceptId(),
                articleRecord.getTitle(),
                updated,
                repairedResult.fixedLinkCount,
                dependsOnResult.changedSectionCount + relatedResult.changedSectionCount,
                repairedResult.unresolvedLinks
        );
        return new EnhancementComputation(item, updatedRecord);
    }

    /**
     * 修复标题型 wiki-link。
     *
     * @param content 正文
     * @param conceptIds 已知概念标识
     * @param conceptIdsByTitle 标题到概念标识映射
     * @return 修复结果
     */
    private LinkRepairResult repairWikiLinks(
            String content,
            Set<String> conceptIds,
            Map<String, String> conceptIdsByTitle
    ) {
        Matcher matcher = WIKI_LINK_PATTERN.matcher(content);
        StringBuffer buffer = new StringBuffer();
        Set<String> unresolvedLinks = new LinkedHashSet<String>();
        int fixedLinkCount = 0;

        while (matcher.find()) {
            String rawLink = matcher.group(1);
            ParsedWikiLink parsedWikiLink = parseWikiLink(rawLink);
            String baseTarget = parsedWikiLink.baseTarget;
            String replacement = matcher.group(0);

            if (conceptIds.contains(baseTarget)) {
                matcher.appendReplacement(buffer, Matcher.quoteReplacement(replacement));
                continue;
            }

            String resolvedConceptId = conceptIdsByTitle.get(normalizeTitle(baseTarget));
            if (resolvedConceptId == null) {
                unresolvedLinks.add(baseTarget);
                matcher.appendReplacement(buffer, Matcher.quoteReplacement(replacement));
                continue;
            }

            String normalizedLink = buildNormalizedWikiLink(resolvedConceptId, parsedWikiLink);
            replacement = "[[" + normalizedLink + "]]";
            fixedLinkCount++;
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(buffer);
        return new LinkRepairResult(buffer.toString(), fixedLinkCount, new ArrayList<String>(unresolvedLinks));
    }

    /**
     * 同步单个受管区块。
     *
     * @param content 正文
     * @param blockPattern 受管区块匹配表达式
     * @param managedBlock 目标受管区块
     * @return 同步结果
     */
    private ManagedBlockResult syncManagedBlock(String content, Pattern blockPattern, String managedBlock) {
        String currentContent = content == null ? "" : content;
        Matcher matcher = blockPattern.matcher(currentContent);
        String updatedContent;
        boolean changed = false;

        if (managedBlock == null) {
            if (!matcher.find()) {
                return new ManagedBlockResult(currentContent, 0);
            }
            updatedContent = matcher.replaceAll("");
            changed = true;
        }
        else if (matcher.find()) {
            updatedContent = matcher.replaceAll(Matcher.quoteReplacement("\n\n" + managedBlock + "\n"));
            changed = !currentContent.equals(updatedContent);
        }
        else {
            String baseContent = trimTrailingWhitespace(currentContent);
            if (baseContent.isBlank()) {
                updatedContent = managedBlock + "\n";
            }
            else {
                updatedContent = baseContent + "\n\n" + managedBlock + "\n";
            }
            changed = true;
        }

        return new ManagedBlockResult(trimTrailingWhitespace(updatedContent), changed ? 1 : 0);
    }

    /**
     * 构造受管区块内容。
     *
     * @param markerName 标记名
     * @param heading 标题
     * @param conceptIdList 概念标识列表
     * @param conceptTitles 标题映射
     * @return 受管区块内容
     */
    private String buildManagedBlock(
            String markerName,
            String heading,
            List<String> conceptIdList,
            Map<String, String> conceptTitles
    ) {
        List<String> normalizedConceptIds = new ArrayList<String>();
        LinkedHashSet<String> uniqueConceptIds = new LinkedHashSet<String>(conceptIdList);
        for (String conceptId : uniqueConceptIds) {
            if (conceptId != null && !conceptId.isBlank()) {
                normalizedConceptIds.add(conceptId.trim());
            }
        }
        if (normalizedConceptIds.isEmpty()) {
            return null;
        }

        StringBuilder builder = new StringBuilder();
        builder.append("<!-- ").append(markerName).append(":start -->").append("\n");
        builder.append("## ").append(heading).append("\n");
        for (String conceptId : normalizedConceptIds) {
            String title = conceptTitles.get(conceptId);
            if (title == null || title.isBlank() || title.equals(conceptId)) {
                builder.append("- [[").append(conceptId).append("]]").append("\n");
            }
            else {
                builder.append("- [[").append(conceptId).append("|").append(title).append("]]").append("\n");
            }
        }
        builder.append("<!-- ").append(markerName).append(":end -->");
        return builder.toString();
    }

    /**
     * 解析 wiki-link。
     *
     * @param rawLink 原始 wiki-link
     * @return 解析结果
     */
    private ParsedWikiLink parseWikiLink(String rawLink) {
        String normalizedLink = rawLink == null ? "" : rawLink.trim();
        String label = null;
        int pipeIndex = normalizedLink.indexOf('|');
        if (pipeIndex >= 0) {
            label = normalizedLink.substring(pipeIndex + 1).trim();
            normalizedLink = normalizedLink.substring(0, pipeIndex).trim();
        }

        String heading = null;
        int headingIndex = normalizedLink.indexOf('#');
        if (headingIndex >= 0) {
            heading = normalizedLink.substring(headingIndex + 1).trim();
            normalizedLink = normalizedLink.substring(0, headingIndex).trim();
        }

        return new ParsedWikiLink(normalizedLink, heading, label);
    }

    /**
     * 组装规范化 wiki-link。
     *
     * @param resolvedConceptId 解析后的概念标识
     * @param parsedWikiLink 解析结果
     * @return 规范化 wiki-link 目标
     */
    private String buildNormalizedWikiLink(String resolvedConceptId, ParsedWikiLink parsedWikiLink) {
        StringBuilder builder = new StringBuilder();
        builder.append(resolvedConceptId);
        if (parsedWikiLink.heading != null && !parsedWikiLink.heading.isBlank()) {
            builder.append("#").append(parsedWikiLink.heading);
        }
        String visibleLabel = parsedWikiLink.label;
        if ((visibleLabel == null || visibleLabel.isBlank())
                && parsedWikiLink.baseTarget != null
                && !parsedWikiLink.baseTarget.equalsIgnoreCase(resolvedConceptId)) {
            visibleLabel = parsedWikiLink.baseTarget;
        }
        if (visibleLabel != null && !visibleLabel.isBlank()) {
            builder.append("|").append(visibleLabel);
        }
        return builder.toString();
    }

    /**
     * 收集概念标题映射。
     *
     * @param articleRecords 文章记录列表
     * @return 标题映射
     */
    private Map<String, String> collectConceptTitles(List<ArticleRecord> articleRecords) {
        Map<String, String> conceptTitles = new LinkedHashMap<String, String>();
        for (ArticleRecord articleRecord : articleRecords) {
            conceptTitles.put(articleRecord.getConceptId(), articleRecord.getTitle());
        }
        return conceptTitles;
    }

    /**
     * 收集标题到概念标识映射。
     *
     * @param articleRecords 文章记录列表
     * @return 标题到概念标识映射
     */
    private Map<String, String> collectConceptIdsByTitle(List<ArticleRecord> articleRecords) {
        Map<String, String> conceptIdsByTitle = new LinkedHashMap<String, String>();
        for (ArticleRecord articleRecord : articleRecords) {
            String normalizedTitle = normalizeTitle(articleRecord.getTitle());
            if (!normalizedTitle.isBlank() && !conceptIdsByTitle.containsKey(normalizedTitle)) {
                conceptIdsByTitle.put(normalizedTitle, articleRecord.getConceptId());
            }
        }
        return conceptIdsByTitle;
    }

    /**
     * 规范化标题。
     *
     * @param title 标题
     * @return 规范化标题
     */
    private String normalizeTitle(String title) {
        if (title == null) {
            return "";
        }
        return title.trim().replaceAll("\\s+", " ").toLowerCase();
    }

    /**
     * 去除结尾空白。
     *
     * @param content 正文
     * @return 去除结尾空白后的正文
     */
    private String trimTrailingWhitespace(String content) {
        if (content == null) {
            return "";
        }
        return content.replaceFirst("\\s+$", "");
    }

    private static class EnhancementComputation {

        private final LinkEnhancementItem item;

        private final ArticleRecord updatedRecord;

        private EnhancementComputation(LinkEnhancementItem item, ArticleRecord updatedRecord) {
            this.item = item;
            this.updatedRecord = updatedRecord;
        }
    }

    private static class LinkRepairResult {

        private final String content;

        private final int fixedLinkCount;

        private final List<String> unresolvedLinks;

        private LinkRepairResult(String content, int fixedLinkCount, List<String> unresolvedLinks) {
            this.content = content;
            this.fixedLinkCount = fixedLinkCount;
            this.unresolvedLinks = unresolvedLinks;
        }
    }

    private static class ManagedBlockResult {

        private final String content;

        private final int changedSectionCount;

        private ManagedBlockResult(String content, int changedSectionCount) {
            this.content = content;
            this.changedSectionCount = changedSectionCount;
        }
    }

    private static class ParsedWikiLink {

        private final String baseTarget;

        private final String heading;

        private final String label;

        private ParsedWikiLink(String baseTarget, String heading, String label) {
            this.baseTarget = baseTarget;
            this.heading = heading;
            this.label = label;
        }
    }
}
