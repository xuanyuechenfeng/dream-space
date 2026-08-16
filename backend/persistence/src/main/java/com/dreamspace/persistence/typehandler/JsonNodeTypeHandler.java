package com.dreamspace.persistence.typehandler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;

public final class JsonNodeTypeHandler extends BaseTypeHandler<JsonNode> {
  private final ObjectMapper mapper;

  public JsonNodeTypeHandler(ObjectMapper mapper) { this.mapper = mapper; }

  @Override public void setNonNullParameter(PreparedStatement ps, int i, JsonNode parameter, JdbcType jdbcType) throws SQLException {
    ps.setObject(i, parameter.toString(), java.sql.Types.OTHER);
  }
  @Override public JsonNode getNullableResult(ResultSet rs, String columnName) throws SQLException { return parse(rs.getString(columnName)); }
  @Override public JsonNode getNullableResult(ResultSet rs, int columnIndex) throws SQLException { return parse(rs.getString(columnIndex)); }
  @Override public JsonNode getNullableResult(CallableStatement cs, int columnIndex) throws SQLException { return parse(cs.getString(columnIndex)); }

  private JsonNode parse(String value) throws SQLException {
    if (value == null) return null;
    try { return mapper.readTree(value); } catch (Exception e) { throw new SQLException("invalid JSON column", e); }
  }
}
