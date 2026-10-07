CREATE TABLE device_model (
 model_key VARCHAR(64) NOT NULL, version INT NOT NULL, definition JSON NOT NULL,
 PRIMARY KEY(model_key,version)
);
CREATE TABLE home_device (
 device_key VARCHAR(64) PRIMARY KEY, display_name VARCHAR(128) NOT NULL,
 model_key VARCHAR(64) NOT NULL, model_version INT NOT NULL, source VARCHAR(32) NOT NULL,
 location VARCHAR(32) NOT NULL, created_at BIGINT NOT NULL
);
CREATE TABLE device_telemetry (
 event_id VARCHAR(64) PRIMARY KEY, device_key VARCHAR(64) NOT NULL,
 model_key VARCHAR(64) NOT NULL, model_version INT NOT NULL,
 occurred_at BIGINT NOT NULL, received_at BIGINT NOT NULL, properties_json JSON NOT NULL,
 KEY idx_history(device_key,occurred_at,event_id), KEY idx_retention(received_at)
);
CREATE TABLE home_change_event (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, event_key VARCHAR(128) NOT NULL,
 type VARCHAR(64) NOT NULL, device_key VARCHAR(64), occurred_at BIGINT NOT NULL,
 resource_version BIGINT NOT NULL, payload JSON NOT NULL,
 UNIQUE KEY uk_change_key(event_key), KEY idx_change_time(occurred_at)
);
CREATE TABLE home_change_clock (id INT PRIMARY KEY);
INSERT INTO home_change_clock VALUES(1);
CREATE TABLE dashboard_page (
 id VARCHAR(64) PRIMARY KEY, owner VARCHAR(64) NOT NULL, title VARCHAR(128) NOT NULL,
 revision BIGINT NOT NULL, archived BOOLEAN NOT NULL DEFAULT FALSE, definition JSON NOT NULL,
 updated_at BIGINT NOT NULL
);
CREATE TABLE dashboard_page_revision (
 page_id VARCHAR(64) NOT NULL, revision BIGINT NOT NULL, definition JSON NOT NULL,
 created_at BIGINT NOT NULL, PRIMARY KEY(page_id,revision)
);
ALTER TABLE rule_config ADD COLUMN revision BIGINT NOT NULL DEFAULT 1;
CREATE TABLE rule_config_revision (
 rule_code VARCHAR(64) NOT NULL, revision BIGINT NOT NULL, definition JSON NOT NULL,
 created_at BIGINT NOT NULL, PRIMARY KEY(rule_code,revision)
);
CREATE TABLE laundry_session (
 id VARCHAR(64) PRIMARY KEY, device_key VARCHAR(64) NOT NULL, status VARCHAR(32) NOT NULL,
 started_at BIGINT NOT NULL, completed_at BIGINT, risk_active BOOLEAN NOT NULL DEFAULT FALSE,
 risk_cycle INT NOT NULL DEFAULT 0, version BIGINT NOT NULL DEFAULT 1,
 KEY idx_laundry_device(device_key,status)
);
CREATE TABLE weather_snapshot (
 id INT PRIMARY KEY, location VARCHAR(128) NOT NULL, source VARCHAR(64) NOT NULL,
 observed_at BIGINT NOT NULL, valid_until BIGINT NOT NULL, available BOOLEAN NOT NULL,
 raining BOOLEAN, rain_probability DOUBLE, reason VARCHAR(128)
);
CREATE TABLE device_command (
 id VARCHAR(64) PRIMARY KEY, idempotency_key VARCHAR(128) NOT NULL, device_key VARCHAR(64) NOT NULL,
 action_code VARCHAR(64) NOT NULL, parameters JSON NOT NULL, requester VARCHAR(64) NOT NULL,
 status VARCHAR(32) NOT NULL, created_at BIGINT NOT NULL, expires_at BIGINT NOT NULL,
 result JSON, late_result JSON, version BIGINT NOT NULL DEFAULT 1,
 UNIQUE KEY uk_command_request(device_key,idempotency_key), KEY idx_command_timeout(status,expires_at)
);
CREATE TABLE command_outbox (
 command_id VARCHAR(64) PRIMARY KEY, published BOOLEAN NOT NULL DEFAULT FALSE,
 attempts INT NOT NULL DEFAULT 0, last_error VARCHAR(255)
);
CREATE TABLE mock_command_execution (
 command_id VARCHAR(64) PRIMARY KEY, device_key VARCHAR(64) NOT NULL, result JSON NOT NULL
);
INSERT INTO rule_config(rule_code,rule_name,scene,enabled,priority,params_json)
 VALUES ('COLLECT_LAUNDRY','收衣提醒','LAUNDRY',TRUE,50,JSON_OBJECT('rainProbability',0.6,'weatherMaxAgeSeconds',600));
