package com.xbk.lattice.infra.persistence.mybatis;

import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.postgresql.util.PGobject;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * PostgreSQL jsonb TypeHandler
 *
 * 职责：把 String JSON 写入 jsonb 字段
 *
 * @author xiexu
 */
public class PostgresJsonbTypeHandler extends BaseTypeHandler<String> {

    /**
     * 设置非空 JSON 参数。
     *
     * @param preparedStatement PreparedStatement
     * @param index 参数序号
     * @param parameter JSON 字符串
     * @param jdbcType JDBC 类型
     * @throws SQLException SQL 异常
     */
    @Override
    public void setNonNullParameter(
            PreparedStatement preparedStatement,
            int index,
            String parameter,
            JdbcType jdbcType
    ) throws SQLException {
        PGobject pgObject = new PGobject();
        pgObject.setType("jsonb");
        pgObject.setValue(parameter);
        preparedStatement.setObject(index, pgObject);
    }

    /**
     * 读取 JSON 字符串。
     *
     * @param resultSet 结果集
     * @param columnName 列名
     * @return JSON 字符串
     * @throws SQLException SQL 异常
     */
    @Override
    public String getNullableResult(ResultSet resultSet, String columnName) throws SQLException {
        return resultSet.getString(columnName);
    }

    /**
     * 读取 JSON 字符串。
     *
     * @param resultSet 结果集
     * @param columnIndex 列序号
     * @return JSON 字符串
     * @throws SQLException SQL 异常
     */
    @Override
    public String getNullableResult(ResultSet resultSet, int columnIndex) throws SQLException {
        return resultSet.getString(columnIndex);
    }

    /**
     * 读取 JSON 字符串。
     *
     * @param callableStatement CallableStatement
     * @param columnIndex 列序号
     * @return JSON 字符串
     * @throws SQLException SQL 异常
     */
    @Override
    public String getNullableResult(CallableStatement callableStatement, int columnIndex) throws SQLException {
        return callableStatement.getString(columnIndex);
    }
}
