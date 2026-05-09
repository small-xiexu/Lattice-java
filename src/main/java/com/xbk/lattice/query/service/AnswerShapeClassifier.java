package com.xbk.lattice.query.service;

import com.xbk.lattice.query.evidence.domain.AnswerShape;
import org.springframework.stereotype.Service;

/**
 * 答案形态分类器
 *
 * 职责：提供答案形态扩展点；具体形态应由证据、Fact Card 或检索元数据驱动
 *
 * @author xiexu
 */
@Service
public class AnswerShapeClassifier {

    /**
     * 识别答案形态。
     *
     * @param question 查询问题
     * @return 答案形态
     */
    public AnswerShape classify(String question) {
        return AnswerShape.GENERAL;
    }
}
