package com.xbk.lattice.api.query;

/**
 * 查询请求
 *
 * 职责：承载最小查询接口的请求参数
 *
 * @author xiexu
 */
public class QueryRequest {

    private String question;

    /**
     * 获取查询问题。
     *
     * @return 查询问题
     */
    public String getQuestion() {
        return question;
    }

    /**
     * 设置查询问题。
     *
     * @param question 查询问题
     */
    public void setQuestion(String question) {
        this.question = question;
    }
}
