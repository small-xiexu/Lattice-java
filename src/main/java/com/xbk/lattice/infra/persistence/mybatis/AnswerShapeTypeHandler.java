package com.xbk.lattice.infra.persistence.mybatis;

import com.xbk.lattice.query.evidence.domain.AnswerShape;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * AnswerShape TypeHandler
 *
 * 职责：在数据库字符串与 AnswerShape 枚举之间转换
 *
 * @author xiexu
 */
public class AnswerShapeTypeHandler extends BaseTypeHandler<AnswerShape> {

    /**
     * 设置非空答案形态参数。
     *
     * @param preparedStatement PreparedStatement
     * @param index 参数序号
     * @param parameter 答案形态
     * @param jdbcType JDBC 类型
     * @throws SQLException SQL 异常
     */
    @Override
    public void setNonNullParameter(
            PreparedStatement preparedStatement,
            int index,
            AnswerShape parameter,
            JdbcType jdbcType
    ) throws SQLException {
        preparedStatement.setString(index, parameter.name());
    }

    /**
     * 读取答案形态。
     *
     * @param resultSet 结果集
     * @param columnName 列名
     * @return 答案形态
     * @throws SQLException SQL 异常
     */
    @Override
    public AnswerShape getNullableResult(ResultSet resultSet, String columnName) throws SQLException {
        return valueOf(resultSet.getString(columnName));
    }

    /**
     * 读取答案形态。
     *
     * @param resultSet 结果集
     * @param columnIndex 列序号
     * @return 答案形态
     * @throws SQLException SQL 异常
     */
    @Override
    public AnswerShape getNullableResult(ResultSet resultSet, int columnIndex) throws SQLException {
        return valueOf(resultSet.getString(columnIndex));
    }

    /**
     * 读取答案形态。
     *
     * @param callableStatement CallableStatement
     * @param columnIndex 列序号
     * @return 答案形态
     * @throws SQLException SQL 异常
     */
    @Override
    public AnswerShape getNullableResult(CallableStatement callableStatement, int columnIndex) throws SQLException {
        return valueOf(callableStatement.getString(columnIndex));
    }

    private AnswerShape valueOf(String value) {
        return value == null ? null : AnswerShape.fromValue(value);
    }
}
