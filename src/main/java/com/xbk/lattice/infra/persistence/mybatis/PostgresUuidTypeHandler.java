package com.xbk.lattice.infra.persistence.mybatis;

import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

/**
 * PostgreSQL UUID TypeHandler
 *
 * 职责：在 Java UUID 与 PostgreSQL uuid 之间转换
 *
 * @author xiexu
 */
public class PostgresUuidTypeHandler extends BaseTypeHandler<UUID> {

    /**
     * 设置非空 UUID 参数。
     *
     * @param preparedStatement PreparedStatement
     * @param index 参数序号
     * @param parameter UUID 参数
     * @param jdbcType JDBC 类型
     * @throws SQLException SQL 异常
     */
    @Override
    public void setNonNullParameter(
            PreparedStatement preparedStatement,
            int index,
            UUID parameter,
            JdbcType jdbcType
    ) throws SQLException {
        preparedStatement.setObject(index, parameter);
    }

    /**
     * 读取 UUID。
     *
     * @param resultSet 结果集
     * @param columnName 列名
     * @return UUID
     * @throws SQLException SQL 异常
     */
    @Override
    public UUID getNullableResult(ResultSet resultSet, String columnName) throws SQLException {
        return readUuid(resultSet.getObject(columnName));
    }

    /**
     * 读取 UUID。
     *
     * @param resultSet 结果集
     * @param columnIndex 列序号
     * @return UUID
     * @throws SQLException SQL 异常
     */
    @Override
    public UUID getNullableResult(ResultSet resultSet, int columnIndex) throws SQLException {
        return readUuid(resultSet.getObject(columnIndex));
    }

    /**
     * 读取 UUID。
     *
     * @param callableStatement CallableStatement
     * @param columnIndex 列序号
     * @return UUID
     * @throws SQLException SQL 异常
     */
    @Override
    public UUID getNullableResult(CallableStatement callableStatement, int columnIndex) throws SQLException {
        return readUuid(callableStatement.getObject(columnIndex));
    }

    /**
     * 把数据库返回值转换为 UUID。
     *
     * @param value 数据库返回值
     * @return UUID
     */
    private UUID readUuid(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof UUID) {
            return (UUID) value;
        }
        return UUID.fromString(String.valueOf(value));
    }
}
