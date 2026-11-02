package io.github.jameshome.galley.common.mybatis;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedJdbcTypes;
import org.apache.ibatis.type.MappedTypes;

/**
 * {@link UUID} 与 PostgreSQL {@code uuid} 列之间的转换。
 *
 * <p>MyBatis 3.5.19 没有内置 UUID 处理器，不注册会在执行任何带 UUID 参数的语句时抛 {@code Type handler was
 * null}——错误发生在参数绑定阶段，看起来像 SQL 写错了，实则与 SQL 无关。
 *
 * <p>写入用 {@code setObject}，交给 PostgreSQL JDBC 决定表示；读取时该列已经是 {@link UUID}， 但驱动版本变化时也可能给字符串，故两种都接。
 */
@MappedTypes(UUID.class)
@MappedJdbcTypes({JdbcType.OTHER, JdbcType.VARCHAR})
public class UuidTypeHandler extends BaseTypeHandler<UUID> {

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, UUID parameter, JdbcType jdbcType)
            throws SQLException {
        ps.setObject(i, parameter);
    }

    @Override
    public UUID getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return toUuid(rs.getObject(columnName));
    }

    @Override
    public UUID getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return toUuid(rs.getObject(columnIndex));
    }

    @Override
    public UUID getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return toUuid(cs.getObject(columnIndex));
    }

    private static UUID toUuid(Object value) {
        if (value == null) {
            return null;
        }
        return value instanceof UUID uuid ? uuid : UUID.fromString(value.toString());
    }
}
