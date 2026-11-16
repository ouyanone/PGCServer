-- DDL: club assistant (chatbot) tables
-- Run this on the production database (pgc) before deploying the WAR that contains the chatbot.
-- Members are keyed by their Cognito `sub`; no email is stored here.

-- The last 3 question/answer pairs per member (older rows are deleted on insert).
CREATE TABLE IF NOT EXISTS chat_exchange (
  id          BIGINT       NOT NULL AUTO_INCREMENT,
  member_sub  VARCHAR(64)  NOT NULL,
  question    VARCHAR(500) NOT NULL,
  answer      TEXT         NOT NULL,
  created_at  DATETIME(3)  NOT NULL,
  PRIMARY KEY (id),
  KEY idx_chat_exchange_member (member_sub, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Questions asked per member per day (America/New_York), for the 30-a-day limit.
CREATE TABLE IF NOT EXISTS chat_daily_count (
  member_sub  VARCHAR(64)  NOT NULL,
  day         DATE         NOT NULL,
  questions   INT          NOT NULL DEFAULT 0,
  PRIMARY KEY (member_sub, day)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Model spend per calendar month (America/New_York), for the $10/month budget.
CREATE TABLE IF NOT EXISTS chat_monthly_spend (
  month          CHAR(7)        NOT NULL,   -- 'YYYY-MM'
  cost_usd       DECIMAL(10,6)  NOT NULL DEFAULT 0,
  input_tokens   BIGINT         NOT NULL DEFAULT 0,
  output_tokens  BIGINT         NOT NULL DEFAULT 0,
  PRIMARY KEY (month)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
