package com.xbk.lattice.infra.persistence;

/**
 * FactCard 统计查询行
 *
 * 职责：承载 fact_cards 分组统计结果
 *
 * @author xiexu
 */
public class FactCardCountRow {

    private final String countKey;

    private final int cardCount;

    /**
     * 创建 FactCard 统计查询行。
     *
     * @param countKey 统计键
     * @param cardCount 证据卡数量
     */
    public FactCardCountRow(String countKey, int cardCount) {
        this.countKey = countKey;
        this.cardCount = cardCount;
    }

    /**
     * 获取统计键。
     *
     * @return 统计键
     */
    public String getCountKey() {
        return countKey;
    }

    /**
     * 获取证据卡数量。
     *
     * @return 证据卡数量
     */
    public int getCardCount() {
        return cardCount;
    }
}
