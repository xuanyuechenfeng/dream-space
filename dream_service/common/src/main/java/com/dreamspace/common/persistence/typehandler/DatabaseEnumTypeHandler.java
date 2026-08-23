package com.dreamspace.common.persistence.typehandler;

import com.dreamspace.common.persistence.database.DatabaseValue;
import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;

public final class DatabaseEnumTypeHandler<E extends Enum<E> & DatabaseValue> extends BaseTypeHandler<E> {
  private final Class<E> type;
  public DatabaseEnumTypeHandler(Class<E> type) { this.type = type; }
  @Override public void setNonNullParameter(PreparedStatement ps, int i, E parameter, JdbcType jdbcType) throws SQLException { ps.setObject(i, parameter.databaseValue(), Types.OTHER); }
  @Override public E getNullableResult(ResultSet rs, String columnName) throws SQLException { return parse(rs.getString(columnName)); }
  @Override public E getNullableResult(ResultSet rs, int columnIndex) throws SQLException { return parse(rs.getString(columnIndex)); }
  @Override public E getNullableResult(CallableStatement cs, int columnIndex) throws SQLException { return parse(cs.getString(columnIndex)); }
  private E parse(String value) throws SQLException {
    if (value == null) return null;
    for (E constant : type.getEnumConstants()) if (constant.databaseValue().equals(value)) return constant;
    throw new SQLException("unknown " + type.getSimpleName() + " database value");
  }
}
