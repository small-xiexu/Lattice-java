package com.xbk.lattice.query.service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 查询 token 提取器
 *
 * 职责：复用查询阶段的关键 token 提取逻辑
 *
 * @author xiexu
 */
public final class QueryTokenExtractor {

    private static final Pattern TOKEN_PATTERN = Pattern.compile("[A-Za-z0-9=_-]{2,}");

    private QueryTokenExtractor() {
    }

    /**
     * 从查询语句中提取稳定 token。
     *
     * @param question 查询问题
     * @return 去重后的 token 列表
     */
    public static List<String> extract(String question) {
        Set<String> tokens = new LinkedHashSet<String>();
        Matcher matcher = TOKEN_PATTERN.matcher(question.toLowerCase(Locale.ROOT));
        while (matcher.find()) {
            tokens.add(matcher.group());
        }
        return new ArrayList<String>(tokens);
    }
}
