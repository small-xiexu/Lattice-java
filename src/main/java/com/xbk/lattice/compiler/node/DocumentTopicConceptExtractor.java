package com.xbk.lattice.compiler.node;

import com.xbk.lattice.compiler.config.CompilerProperties;
import com.xbk.lattice.compiler.domain.AnalyzedConcept;
import com.xbk.lattice.compiler.domain.ConceptSection;
import com.xbk.lattice.compiler.domain.RawSource;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 文档专题概念提取器
 *
 * 职责：在缺少结构化分析结果时，把长文档按文档自身的标题层级拆成多个概念
 *
 * @author xiexu
 */
public class DocumentTopicConceptExtractor extends DocumentTopicConceptSegmentationSupport {

    /**
     * 创建文档专题概念提取器。
     */
    public DocumentTopicConceptExtractor() {
        this(new CompilerProperties.DocumentTopics());
    }

    /**
     * 创建文档专题概念提取器。
     *
     * @param documentTopics 长文档专题拆分配置
     */
    public DocumentTopicConceptExtractor(CompilerProperties.DocumentTopics documentTopics) {
        super(documentTopics);
    }

    /**
     * 从长文档中提取专题概念。
     *
     * @param groupKey 分组键
     * @param sortedSources 已排序源文件
     * @return 专题概念列表；无法稳定拆分时返回空列表
     */
    public List<AnalyzedConcept> extract(String groupKey, List<RawSource> sortedSources) {
        List<AnalyzedConcept> analyzedConcepts = new ArrayList<AnalyzedConcept>();
        if (!documentTopics.isEnabled()) {
            return analyzedConcepts;
        }
        int topicIndex = 0;
        for (RawSource rawSource : sortedSources) {
            if (!isLongDocument(rawSource)) {
                continue;
            }
            List<TopicSegment> topicSegments = splitTopics(rawSource);
            if (topicSegments.size() < 2) {
                continue;
            }
            for (TopicSegment topicSegment : topicSegments) {
                topicIndex++;
                analyzedConcepts.add(toAnalyzedConcept(groupKey, rawSource, topicSegment, topicIndex));
            }
        }
        return analyzedConcepts;
    }
}
