package com.xbk.lattice.infra.persistence.mybatis;

import com.xbk.lattice.query.evidence.domain.FactCardType;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * FactCardType TypeHandler
 *
 * 职责：在数据库字符串与 FactCardType 枚举之间转换
 *
 * @author xiexu
 */
public class FactCardTypeHandler extends BaseTypeHandler<FactCardType> {

    /**
     * 设置非空证据卡类型参数。
     *
     * @param preparedStatement PreparedStatement
     * @param index 参数序号
     * @param parameter 证据卡类型
     * @param jdbcType JDBC 类型
     * @throws SQLException SQL 异常
     */
    @Override
    public void setNonNullParameter(
            PreparedStatement preparedStatement,
            int index,
            FactCardType parameter,
            JdbcType jdbcType
    ) throws SQLException {
        preparedStatement.setString(index, parameter.name());
    }

    /**
     * 读取证据卡类型。
     *
     * @param resultSet 结果集
     * @param columnName 列名
     * @return 证据卡类型
     * @throws SQLException SQL 异常
     */
    @Override
    public FactCardType getNullableResult(ResultSet resultSet, String columnName) throws SQLException {
        return valueOf(resultSet.getString(columnName));
    }

    /**
     * 读取证据卡类型。
     *
     * @param resultSet 结果集
     * @param columnIndex 列序号
     * @return 证据卡类型
     * @throws SQLException SQL 异常
     */
    @Override
    public FactCardType getNullableResult(ResultSet resultSet, int columnIndex) throws SQLException {
        return valueOf(resultSet.getString(columnIndex));
    }

    /**
     * 读取证据卡类型。
     *
     * @param callableStatement CallableStatement
     * @param columnIndex 列序号
     * @return 证据卡类型
     * @throws SQLException SQL 异常
     */
    @Override
    public FactCardType getNullableResult(CallableStatement callableStatement, int columnIndex) throws SQLException {
        return valueOf(callableStatement.getString(columnIndex));
    }

    private FactCardType valueOf(String value) {
        return value == null ? null : FactCardType.fromValue(value);
    }
}
