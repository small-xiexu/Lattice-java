package com.xbk.lattice.infra.persistence;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 用户贡献记录
 *
 * 职责：表示确认后的贡献持久化对象
 *
 * @author xiexu
 */
public class ContributionRecord {

    private final UUID id;

    private final String question;

    private final String answer;

    private final String correctionsJson;

    private final String confirmedBy;

    private final OffsetDateTime confirmedAt;

    /**
     * 创建用户贡献记录。
     *
     * @param id 主键
     * @param question 问题
     * @param answer 答案
     * @param correctionsJson 纠错历史 JSON
     * @param confirmedBy 确认人
     * @param confirmedAt 确认时间
     */
    public ContributionRecord(
            UUID id,
            String question,
            String answer,
            String correctionsJson,
            String confirmedBy,
            OffsetDateTime confirmedAt
    ) {
        this.id = id;
        this.question = question;
        this.answer = answer;
        this.correctionsJson = correctionsJson;
        this.confirmedBy = confirmedBy;
        this.confirmedAt = confirmedAt;
    }

    /**
     * 获取主键。
     *
     * @return 主键
     */
    public UUID getId() {
        return id;
    }

    /**
     * 获取问题。
     *
     * @return 问题
     */
    public String getQuestion() {
        return question;
    }

    /**
     * 获取答案。
     *
     * @return 答案
     */
    public String getAnswer() {
        return answer;
    }

    /**
     * 获取纠错历史 JSON。
     *
     * @return 纠错历史 JSON
     */
    public String getCorrectionsJson() {
        return correctionsJson;
    }

    /**
     * 获取确认人。
     *
     * @return 确认人
     */
    public String getConfirmedBy() {
        return confirmedBy;
    }

    /**
     * 获取确认时间。
     *
     * @return 确认时间
     */
    public OffsetDateTime getConfirmedAt() {
        return confirmedAt;
    }
}
