package com.xbk.lattice.infra.persistence.mybatis;

import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;

import java.sql.Array;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * PostgreSQL text[] TypeHandler
 *
 * 职责：在 Java List<String> 与 PostgreSQL text[] 之间转换
 *
 * @author xiexu
 */
public class StringListArrayTypeHandler extends BaseTypeHandler<List<String>> {

    /**
     * 设置非空 text[] 参数。
     *
     * @param preparedStatement PreparedStatement
     * @param index 参数序号
     * @param parameter 字符串列表
     * @param jdbcType JDBC 类型
     * @throws SQLException SQL 异常
     */
    @Override
    public void setNonNullParameter(
            PreparedStatement preparedStatement,
            int index,
            List<String> parameter,
            JdbcType jdbcType
    ) throws SQLException {
        Connection connection = preparedStatement.getConnection();
        Array array = connection.createArrayOf("text", parameter.toArray(new String[0]));
        preparedStatement.setArray(index, array);
    }

    /**
     * 读取 text[]。
     *
     * @param resultSet 结果集
     * @param columnName 列名
     * @return 字符串列表
     * @throws SQLException SQL 异常
     */
    @Override
    public List<String> getNullableResult(ResultSet resultSet, String columnName) throws SQLException {
        return readArray(resultSet.getArray(columnName));
    }

    /**
     * 读取 text[]。
     *
     * @param resultSet 结果集
     * @param columnIndex 列序号
     * @return 字符串列表
     * @throws SQLException SQL 异常
     */
    @Override
    public List<String> getNullableResult(ResultSet resultSet, int columnIndex) throws SQLException {
        return readArray(resultSet.getArray(columnIndex));
    }

    /**
     * 读取 text[]。
     *
     * @param callableStatement CallableStatement
     * @param columnIndex 列序号
     * @return 字符串列表
     * @throws SQLException SQL 异常
     */
    @Override
    public List<String> getNullableResult(CallableStatement callableStatement, int columnIndex) throws SQLException {
        return readArray(callableStatement.getArray(columnIndex));
    }

    private List<String> readArray(Array array) throws SQLException {
        if (array == null) {
            return List.of();
        }
        Object[] values = (Object[]) array.getArray();
        List<String> result = new ArrayList<String>();
        for (Object value : values) {
            result.add(String.valueOf(value));
        }
        return result;
    }
}
