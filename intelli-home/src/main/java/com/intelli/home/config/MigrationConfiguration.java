package com.intelli.home.config;

import java.util.*;
import org.flywaydb.core.api.MigrationVersion;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.context.annotation.*;

@Configuration
public class MigrationConfiguration {
  @Bean
  FlywayMigrationStrategy migrationStrategy(
      @Value("\u0024{intelli.migrations.adopt-existing:false}") boolean adopt) {
    return flyway -> {
      if (adopt) {
        try (var connection = flyway.getConfiguration().getDataSource().getConnection()) {
          var tables = new HashSet<String>();
          try (var rs =
              connection
                  .getMetaData()
                  .getTables(connection.getCatalog(), null, "%", new String[] {"TABLE"})) {
            while (rs.next()) tables.add(rs.getString("TABLE_NAME"));
          }
          if (!tables.contains("flyway_schema_history") && !tables.isEmpty()) {
            var required =
                Map.of(
                    "device", List.of("device_key", "online"),
                    "device_state", List.of("device_key", "property_key"),
                    "rule_config", List.of("rule_code", "params_json"),
                    "home_device_snapshot", List.of("event_id", "properties_json", "times_json"),
                    "alert_event", List.of("message_id", "occurred_at"),
                    "alert_outbox", List.of("message_id", "published"),
                    "alert_cooldown", List.of("device_key", "rule_code"),
                    "notification_delivery", List.of("message_id"));
            if (!tables.equals(required.keySet()))
              throw new IllegalStateException("Refusing to baseline an unrecognized schema");
            for (var entry : required.entrySet()) {
              var columns = new HashSet<String>();
              try (var rs =
                  connection
                      .getMetaData()
                      .getColumns(connection.getCatalog(), null, entry.getKey(), "%")) {
                while (rs.next()) columns.add(rs.getString("COLUMN_NAME"));
              }
              if (!columns.containsAll(entry.getValue()))
                throw new IllegalStateException("Legacy columns missing: " + entry.getKey());
            }
            org.flywaydb.core.Flyway.configure()
                .dataSource(flyway.getConfiguration().getDataSource())
                .baselineVersion(MigrationVersion.fromVersion("1"))
                .load()
                .baseline();
          }
        } catch (java.sql.SQLException e) {
          throw new IllegalStateException("Cannot inspect existing schema", e);
        }
      }
      flyway.migrate();
    };
  }
}
