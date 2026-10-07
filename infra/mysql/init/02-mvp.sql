CREATE TABLE IF NOT EXISTS home_device_snapshot (
 device_key VARCHAR(64) PRIMARY KEY, location VARCHAR(32) NOT NULL, source VARCHAR(32) NOT NULL,
 event_id VARCHAR(64) NOT NULL, occurred_at BIGINT NOT NULL, last_seen_at BIGINT NOT NULL,
 properties_json JSON NOT NULL, times_json JSON NOT NULL
);
CREATE TABLE IF NOT EXISTS alert_outbox (
 message_id VARCHAR(64) PRIMARY KEY, payload JSON NOT NULL, published BOOLEAN NOT NULL DEFAULT FALSE,
 attempts INT NOT NULL DEFAULT 0, last_error VARCHAR(255), created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
 KEY idx_outbox_pending(published,created_at)
);
CREATE TABLE IF NOT EXISTS alert_cooldown (
 device_key VARCHAR(64) NOT NULL, rule_code VARCHAR(64) NOT NULL, last_occurred_at BIGINT NOT NULL DEFAULT 0,
 PRIMARY KEY(device_key,rule_code)
);
CREATE TABLE IF NOT EXISTS notification_delivery (
 message_id VARCHAR(64) PRIMARY KEY, notified_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
