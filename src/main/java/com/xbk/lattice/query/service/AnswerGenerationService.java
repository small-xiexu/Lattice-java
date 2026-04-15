package com.xbk.lattice.query.service;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 最小答案生成服务
 *
 * 职责：基于命中文章生成可读答案
 *
 * @author xiexu
 */
@Service
@Profile("jdbc")
public class AnswerGenerationService {

    private static final Pattern TOKEN_PATTERN = Pattern.compile("[A-Za-z0-9=_-]{2,}");

    /**
     * 生成最小答案。
     *
     * @param question 查询问题
     * @param articleHit 文章命中
     * @return 答案
     */
    public String generate(String question, QueryArticleHit articleHit) {
        List<String> queryTokens = extractQueryTokens(question);
        List<String> matchedLines = selectMatchedLines(articleHit.getContent(), queryTokens);

        StringBuilder answerBuilder = new StringBuilder();
        answerBuilder.append(articleHit.getTitle());
        if (!matchedLines.isEmpty()) {
            answerBuilder.append("：").append(String.join("；", matchedLines));
            return answerBuilder.toString();
        }

        String description = extractDescription(articleHit.getMetadataJson());
        if (!description.isEmpty()) {
            answerBuilder.append("：").append(description);
            return answerBuilder.toString();
        }

        answerBuilder.append("：").append(articleHit.getContent());
        return answerBuilder.toString();
    }

    /**
     * 提取查询 token。
     *
     * @param question 查询问题
     * @return token 列表
     */
    private List<String> extractQueryTokens(String question) {
        List<String> queryTokens = new ArrayList<String>();
        Matcher matcher = TOKEN_PATTERN.matcher(question.toLowerCase(Locale.ROOT));
        while (matcher.find()) {
            queryTokens.add(matcher.group());
        }
        return queryTokens;
    }

    /**
     * 选出与问题最相关的内容行。
     *
     * @param content 文章内容
     * @param queryTokens 查询 token
     * @return 匹配内容行
     */
    private List<String> selectMatchedLines(String content, List<String> queryTokens) {
        List<String> matchedLines = new ArrayList<String>();
        String[] lines = content.split("\\R");
        for (String line : lines) {
            String normalizedLine = line.trim();
            if (normalizedLine.isEmpty() || normalizedLine.startsWith("#") || normalizedLine.startsWith(">")) {
                continue;
            }

            String plainLine = normalizedLine.startsWith("- ") ? normalizedLine.substring(2) : normalizedLine;
            String lowercaseLine = plainLine.toLowerCase(Locale.ROOT);
            for (String queryToken : queryTokens) {
                if (lowercaseLine.contains(queryToken)) {
                    matchedLines.add(plainLine);
                    break;
                }
            }
            if (matchedLines.size() >= 2) {
                break;
            }
        }
        return matchedLines;
    }

    /**
     * 从 metadata_json 中提取 description。
     *
     * @param metadataJson 元数据 JSON
     * @return 描述
     */
    private String extractDescription(String metadataJson) {
        String marker = "\"description\":";
        int markerIndex = metadataJson.indexOf(marker);
        if (markerIndex < 0) {
            return "";
        }
        int quoteStart = metadataJson.indexOf('"', markerIndex + marker.length());
        if (quoteStart < 0) {
            return "";
        }
        int quoteEnd = metadataJson.indexOf('"', quoteStart + 1);
        if (quoteEnd < 0) {
            return "";
        }
        return metadataJson.substring(quoteStart + 1, quoteEnd);
    }
}
