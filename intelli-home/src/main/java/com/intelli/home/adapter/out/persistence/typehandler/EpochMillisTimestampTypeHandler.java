package com.intelli.home.adapter.out.persistence.typehandler;

import java.sql.*;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;

/** Converts alert epoch milliseconds to SQL TIMESTAMP using JDBC's existing timezone behavior. */
public class EpochMillisTimestampTypeHandler extends BaseTypeHandler<Long> {
  public void setNonNullParameter(PreparedStatement ps, int index, Long value, JdbcType jdbcType)
      throws SQLException {
    ps.setTimestamp(index, new Timestamp(value));
  }

  public Long getNullableResult(ResultSet rs, String column) throws SQLException {
    return epoch(rs.getTimestamp(column));
  }

  public Long getNullableResult(ResultSet rs, int column) throws SQLException {
    return epoch(rs.getTimestamp(column));
  }

  public Long getNullableResult(CallableStatement cs, int column) throws SQLException {
    return epoch(cs.getTimestamp(column));
  }

  private Long epoch(Timestamp value) {
    return value == null ? null : value.getTime();
  }
}
