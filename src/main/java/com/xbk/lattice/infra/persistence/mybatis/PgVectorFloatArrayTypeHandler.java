package com.xbk.lattice.infra.persistence.mybatis;

import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * pgvector float[] TypeHandler
 *
 * 职责：在 Java float[] 与 pgvector 文本字面量之间转换
 *
 * @author xiexu
 */
public class PgVectorFloatArrayTypeHandler extends BaseTypeHandler<float[]> {

    /**
     * 设置非空向量参数。
     *
     * @param preparedStatement PreparedStatement
     * @param index 参数序号
     * @param parameter 向量
     * @param jdbcType JDBC 类型
     * @throws SQLException SQL 异常
     */
    @Override
    public void setNonNullParameter(
            PreparedStatement preparedStatement,
            int index,
            float[] parameter,
            JdbcType jdbcType
    ) throws SQLException {
        preparedStatement.setString(index, formatVector(parameter));
    }

    /**
     * 读取向量。
     *
     * @param resultSet 结果集
     * @param columnName 列名
     * @return 向量
     * @throws SQLException SQL 异常
     */
    @Override
    public float[] getNullableResult(ResultSet resultSet, String columnName) throws SQLException {
        return parseVector(resultSet.getString(columnName));
    }

    /**
     * 读取向量。
     *
     * @param resultSet 结果集
     * @param columnIndex 列序号
     * @return 向量
     * @throws SQLException SQL 异常
     */
    @Override
    public float[] getNullableResult(ResultSet resultSet, int columnIndex) throws SQLException {
        return parseVector(resultSet.getString(columnIndex));
    }

    /**
     * 读取向量。
     *
     * @param callableStatement CallableStatement
     * @param columnIndex 列序号
     * @return 向量
     * @throws SQLException SQL 异常
     */
    @Override
    public float[] getNullableResult(CallableStatement callableStatement, int columnIndex) throws SQLException {
        return parseVector(callableStatement.getString(columnIndex));
    }

    private String formatVector(float[] embedding) {
        if (embedding == null || embedding.length == 0) {
            return "[]";
        }
        StringBuilder builder = new StringBuilder();
        builder.append('[');
        for (int index = 0; index < embedding.length; index++) {
            if (index > 0) {
                builder.append(',');
            }
            builder.append(embedding[index]);
        }
        builder.append(']');
        return builder.toString();
    }

    private float[] parseVector(String vectorLiteral) {
        if (vectorLiteral == null || vectorLiteral.isBlank()) {
            return new float[0];
        }
        String normalizedLiteral = vectorLiteral.trim();
        if (normalizedLiteral.startsWith("[") && normalizedLiteral.endsWith("]")) {
            normalizedLiteral = normalizedLiteral.substring(1, normalizedLiteral.length() - 1);
        }
        if (normalizedLiteral.isBlank()) {
            return new float[0];
        }
        String[] values = normalizedLiteral.split(",");
        float[] embedding = new float[values.length];
        for (int index = 0; index < values.length; index++) {
            embedding[index] = Float.parseFloat(values[index].trim());
        }
        return embedding;
    }
}
