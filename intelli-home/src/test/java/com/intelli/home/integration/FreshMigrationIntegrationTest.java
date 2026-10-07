package com.intelli.home.integration;

import static org.junit.jupiter.api.Assertions.*;

import java.sql.DriverManager;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

@EnabledIfSystemProperty(named = "home.integration", matches = "true")
class FreshMigrationIntegrationTest {
  @Test
  void freshDatabaseMigratesOnceAndUnknownSchemaIsRefused() throws Exception {
    String database = "intelli_migration_it_" + UUID.randomUUID().toString().replace("-", "");
    String password = System.getenv().getOrDefault("MYSQL_ROOT_PASSWORD", "intelli123");
    String base = "jdbc:mysql://localhost:3306/";
    String options = "?useSSL=false&allowPublicKeyRetrieval=true";
    try (var connection = DriverManager.getConnection(base + options, "root", password);
        var sql = connection.createStatement()) {
      sql.execute("CREATE DATABASE " + database);
      try {
        var flyway =
            Flyway.configure()
                .dataSource(base + database + options, "root", password)
                .locations("classpath:db/migration")
                .load();
        assertEquals(4, flyway.migrate().migrationsExecuted);
        assertEquals(0, flyway.migrate().migrationsExecuted);
        try (var rows = sql.executeQuery("SELECT COUNT(*) FROM " + database + ".device_model")) {
          assertTrue(rows.next());
        }
      } finally {
        sql.execute("DROP DATABASE " + database);
      }
      sql.execute("CREATE DATABASE " + database);
      try {
        sql.execute("CREATE TABLE " + database + ".unrelated (id INT)");
        var unknown =
            Flyway.configure()
                .dataSource(base + database + options, "root", password)
                .locations("classpath:db/migration")
                .load();
        assertThrows(org.flywaydb.core.api.FlywayException.class, unknown::migrate);
      } finally {
        sql.execute("DROP DATABASE " + database);
      }
    }
  }
}
