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
 * PostgreSQL bigint[] TypeHandler
 *
 * 职责：在 Java List<Long> 与 PostgreSQL bigint[] 之间转换
 *
 * @author xiexu
 */
public class LongListArrayTypeHandler extends BaseTypeHandler<List<Long>> {

    /**
     * 设置非空 bigint[] 参数。
     *
     * @param preparedStatement PreparedStatement
     * @param index 参数序号
     * @param parameter Long 列表
     * @param jdbcType JDBC 类型
     * @throws SQLException SQL 异常
     */
    @Override
    public void setNonNullParameter(
            PreparedStatement preparedStatement,
            int index,
            List<Long> parameter,
            JdbcType jdbcType
    ) throws SQLException {
        Connection connection = preparedStatement.getConnection();
        Array array = connection.createArrayOf("int8", parameter.toArray(new Long[0]));
        preparedStatement.setArray(index, array);
    }

    /**
     * 读取 bigint[]。
     *
     * @param resultSet 结果集
     * @param columnName 列名
     * @return Long 列表
     * @throws SQLException SQL 异常
     */
    @Override
    public List<Long> getNullableResult(ResultSet resultSet, String columnName) throws SQLException {
        return readArray(resultSet.getArray(columnName));
    }

    /**
     * 读取 bigint[]。
     *
     * @param resultSet 结果集
     * @param columnIndex 列序号
     * @return Long 列表
     * @throws SQLException SQL 异常
     */
    @Override
    public List<Long> getNullableResult(ResultSet resultSet, int columnIndex) throws SQLException {
        return readArray(resultSet.getArray(columnIndex));
    }

    /**
     * 读取 bigint[]。
     *
     * @param callableStatement CallableStatement
     * @param columnIndex 列序号
     * @return Long 列表
     * @throws SQLException SQL 异常
     */
    @Override
    public List<Long> getNullableResult(CallableStatement callableStatement, int columnIndex) throws SQLException {
        return readArray(callableStatement.getArray(columnIndex));
    }

    private List<Long> readArray(Array array) throws SQLException {
        if (array == null) {
            return List.of();
        }
        Object[] values = (Object[]) array.getArray();
        List<Long> result = new ArrayList<Long>();
        for (Object value : values) {
            if (value instanceof Number) {
                Number number = (Number) value;
                result.add(Long.valueOf(number.longValue()));
            }
            else if (value != null) {
                result.add(Long.valueOf(String.valueOf(value)));
            }
        }
        return result;
    }
}
