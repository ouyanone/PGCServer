-- DDL: event_photo table
-- Run this on the production database (pgc) before deploying the new WAR.

CREATE TABLE IF NOT EXISTS event_photo (
  id             BIGINT       NOT NULL AUTO_INCREMENT,
  event_id       INT          NOT NULL,
  file_name      VARCHAR(255) NOT NULL,
  thumbnail_name VARCHAR(255),
  original_name  VARCHAR(255),
  uploaded_at    DATETIME     DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY fk_photo_event_idx (event_id),
  CONSTRAINT fk_photo_event FOREIGN KEY (event_id) REFERENCES event (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- If table already exists, add the thumbnail column:
-- ALTER TABLE event_photo ADD COLUMN thumbnail_name VARCHAR(255) AFTER file_name;
