package com.xbk.lattice.infra.persistence.mybatis;

import com.xbk.lattice.query.evidence.domain.FactCardReviewStatus;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * FactCardReviewStatus TypeHandler
 *
 * 职责：在数据库字符串与 FactCardReviewStatus 枚举之间转换
 *
 * @author xiexu
 */
public class FactCardReviewStatusTypeHandler extends BaseTypeHandler<FactCardReviewStatus> {

    /**
     * 设置非空审查状态参数。
     *
     * @param preparedStatement PreparedStatement
     * @param index 参数序号
     * @param parameter 审查状态
     * @param jdbcType JDBC 类型
     * @throws SQLException SQL 异常
     */
    @Override
    public void setNonNullParameter(
            PreparedStatement preparedStatement,
            int index,
            FactCardReviewStatus parameter,
            JdbcType jdbcType
    ) throws SQLException {
        preparedStatement.setString(index, parameter.databaseValue());
    }

    /**
     * 读取审查状态。
     *
     * @param resultSet 结果集
     * @param columnName 列名
     * @return 审查状态
     * @throws SQLException SQL 异常
     */
    @Override
    public FactCardReviewStatus getNullableResult(ResultSet resultSet, String columnName) throws SQLException {
        return valueOf(resultSet.getString(columnName));
    }

    /**
     * 读取审查状态。
     *
     * @param resultSet 结果集
     * @param columnIndex 列序号
     * @return 审查状态
     * @throws SQLException SQL 异常
     */
    @Override
    public FactCardReviewStatus getNullableResult(ResultSet resultSet, int columnIndex) throws SQLException {
        return valueOf(resultSet.getString(columnIndex));
    }

    /**
     * 读取审查状态。
     *
     * @param callableStatement CallableStatement
     * @param columnIndex 列序号
     * @return 审查状态
     * @throws SQLException SQL 异常
     */
    @Override
    public FactCardReviewStatus getNullableResult(
            CallableStatement callableStatement,
            int columnIndex
    ) throws SQLException {
        return valueOf(callableStatement.getString(columnIndex));
    }

    private FactCardReviewStatus valueOf(String value) {
        return value == null ? null : FactCardReviewStatus.fromValue(value);
    }
}
