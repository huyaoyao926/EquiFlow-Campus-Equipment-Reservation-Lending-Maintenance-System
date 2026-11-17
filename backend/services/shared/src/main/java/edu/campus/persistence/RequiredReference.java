package edu.campus.persistence;

import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;

public final class RequiredReference {
  private RequiredReference() {}

  public static void enforce(JdbcTemplate jdbc, String table, String column) {
    jdbc.execute((ConnectionCallback<Void>) connection -> {
      boolean mysql = "MySQL".equals(connection.getMetaData().getDatabaseProductName());
      String schema = mysql ? connection.getCatalog() : connection.getSchema();
      boolean nullable;
      try (var query = connection.prepareStatement("""
          SELECT IS_NULLABLE FROM INFORMATION_SCHEMA.COLUMNS
          WHERE TABLE_SCHEMA = ? AND LOWER(TABLE_NAME) = ? AND LOWER(COLUMN_NAME) = ?
          """)) {
        query.setString(1, schema);
        query.setString(2, table);
        query.setString(3, column);
        try (var result = query.executeQuery()) {
          if (!result.next()) {
            throw new IllegalStateException("Missing column " + table + "." + column);
          }
          nullable = "YES".equals(result.getString(1));
        }
      }
      if (nullable) {
        String alteration = mysql ? " MODIFY COLUMN " + column + " BIGINT NOT NULL"
            : " ALTER COLUMN " + column + " SET NOT NULL";
        try (var statement = connection.createStatement()) {
          statement.executeUpdate("ALTER TABLE " + table + alteration);
        }
      }
      return null;
    });
  }
}
