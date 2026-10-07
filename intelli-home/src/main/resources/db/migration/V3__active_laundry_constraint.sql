ALTER TABLE laundry_session ADD COLUMN active_device VARCHAR(64)
 GENERATED ALWAYS AS (CASE WHEN status='ACTIVE' THEN device_key ELSE NULL END) STORED;
CREATE UNIQUE INDEX uk_active_laundry ON laundry_session(active_device);
