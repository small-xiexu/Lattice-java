package com.xbk.lattice.compiler.graph;

import com.xbk.lattice.infra.persistence.ArticleRecord;
import com.xbk.lattice.query.service.ReviewResult;
import lombok.Data;

/**
 * 文章审查包裹对象
 *
 * 职责：承载图执行期间的草稿文章、审查结果与修复状态
 *
 * @author xiexu
 */
@Data
public class ArticleReviewEnvelope {

    private ArticleRecord article;

    private ReviewResult reviewResult;

    private String reviewStatus;

    private boolean fixed;

    private int reviewAttemptCount;

    private int fixAttemptCount;

    private String reviewerRoute;

    private String fixerRoute;
}
