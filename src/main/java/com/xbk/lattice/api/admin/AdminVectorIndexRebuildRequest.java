package com.xbk.lattice.api.admin;

/**
 * 管理侧向量索引重建请求
 *
 * 职责：承载向量索引重建模式与操作人
 *
 * @author xiexu
 */
public class AdminVectorIndexRebuildRequest {

    private boolean truncateFirst;

    private String operator;

    /**
     * 返回是否先清空旧向量索引。
     *
     * @return 是否先清空旧向量索引
     */
    public boolean isTruncateFirst() {
        return truncateFirst;
    }

    /**
     * 设置是否先清空旧向量索引。
     *
     * @param truncateFirst 是否先清空旧向量索引
     */
    public void setTruncateFirst(boolean truncateFirst) {
        this.truncateFirst = truncateFirst;
    }

    /**
     * 返回操作人。
     *
     * @return 操作人
     */
    public String getOperator() {
        return operator;
    }

    /**
     * 设置操作人。
     *
     * @param operator 操作人
     */
    public void setOperator(String operator) {
        this.operator = operator;
    }
}
