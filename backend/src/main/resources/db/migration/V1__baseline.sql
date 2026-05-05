-- 单库 home_accounting：全部业务表

USE home_accounting;

-- ----- 用户与家庭 -----
CREATE TABLE users (
  id             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  wechat_openid  VARCHAR(64)     NOT NULL,
  wechat_unionid VARCHAR(64)     NULL,
  nickname       VARCHAR(64)     NULL,
  avatar_url     VARCHAR(512)    NULL,
  created_at     DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at     DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_users_openid (wechat_openid),
  UNIQUE KEY uk_users_unionid (wechat_unionid)
) ENGINE=InnoDB;

CREATE TABLE households (
  id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  name        VARCHAR(64)     NOT NULL,
  invite_code VARCHAR(16)     NOT NULL,
  created_at  DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_households_invite (invite_code)
) ENGINE=InnoDB;

CREATE TABLE household_members (
  id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  household_id BIGINT UNSIGNED NOT NULL,
  user_id      BIGINT UNSIGNED NOT NULL,
  role         VARCHAR(16)     NOT NULL COMMENT 'OWNER / MEMBER',
  joined_at    DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_member_house_user (household_id, user_id),
  UNIQUE KEY uk_member_user (user_id),
  CONSTRAINT fk_member_household
    FOREIGN KEY (household_id) REFERENCES households (id),
  CONSTRAINT fk_member_user
    FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE=InnoDB;

-- ----- 账本与流水 -----
CREATE TABLE ledgers (
  id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  household_id BIGINT UNSIGNED NOT NULL,
  name         VARCHAR(64)     NOT NULL,
  created_at   DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at   DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_ledgers_household (household_id),
  CONSTRAINT fk_ledgers_household
    FOREIGN KEY (household_id) REFERENCES households (id)
) ENGINE=InnoDB;

CREATE TABLE fund_accounts (
  id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  household_id BIGINT UNSIGNED NOT NULL,
  name         VARCHAR(64)     NOT NULL,
  balance      DECIMAL(19, 4)  NOT NULL DEFAULT 0.0000,
  version      BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '乐观锁',
  created_at   DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at   DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_fund_accounts_household (household_id),
  CONSTRAINT fk_fund_accounts_household
    FOREIGN KEY (household_id) REFERENCES households (id)
) ENGINE=InnoDB;

CREATE TABLE categories (
  id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  household_id BIGINT UNSIGNED NOT NULL,
  type         VARCHAR(16)     NOT NULL COMMENT 'EXPENSE / INCOME',
  name         VARCHAR(64)     NOT NULL,
  sort_order   INT             NOT NULL DEFAULT 0,
  enabled      TINYINT(1)      NOT NULL DEFAULT 1,
  created_at   DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at   DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_categories_household (household_id),
  UNIQUE KEY uk_categories_house_type_name (household_id, type, name),
  CONSTRAINT fk_categories_household
    FOREIGN KEY (household_id) REFERENCES households (id)
) ENGINE=InnoDB;

CREATE TABLE tags (
  id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  household_id BIGINT UNSIGNED NOT NULL,
  name         VARCHAR(64)     NOT NULL,
  created_at   DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at   DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_tags_house_name (household_id, name),
  CONSTRAINT fk_tags_household
    FOREIGN KEY (household_id) REFERENCES households (id)
) ENGINE=InnoDB;

CREATE TABLE transactions (
  id               BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  household_id     BIGINT UNSIGNED NOT NULL,
  ledger_id        BIGINT UNSIGNED NOT NULL,
  fund_account_id  BIGINT UNSIGNED NULL,
  category_id      BIGINT UNSIGNED NOT NULL,
  type             VARCHAR(16)     NOT NULL COMMENT 'EXPENSE / INCOME',
  amount           DECIMAL(19, 4)  NOT NULL,
  occurred_at      DATETIME(3)     NOT NULL,
  note             VARCHAR(512)    NULL,
  created_by       BIGINT UNSIGNED NOT NULL,
  created_at       DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at       DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_tx_house_time (household_id, occurred_at),
  KEY idx_tx_ledger_time (ledger_id, occurred_at),
  KEY idx_tx_creator_time (created_by, occurred_at),
  CONSTRAINT fk_tx_household
    FOREIGN KEY (household_id) REFERENCES households (id),
  CONSTRAINT fk_tx_ledger
    FOREIGN KEY (ledger_id) REFERENCES ledgers (id),
  CONSTRAINT fk_tx_fund_account
    FOREIGN KEY (fund_account_id) REFERENCES fund_accounts (id),
  CONSTRAINT fk_tx_category
    FOREIGN KEY (category_id) REFERENCES categories (id),
  CONSTRAINT fk_tx_creator
    FOREIGN KEY (created_by) REFERENCES users (id),
  CONSTRAINT chk_tx_amount_positive CHECK (amount > 0)
) ENGINE=InnoDB;

CREATE TABLE transaction_tags (
  transaction_id BIGINT UNSIGNED NOT NULL,
  tag_id         BIGINT UNSIGNED NOT NULL,
  PRIMARY KEY (transaction_id, tag_id),
  KEY idx_tt_tag (tag_id, transaction_id),
  CONSTRAINT fk_tt_transaction
    FOREIGN KEY (transaction_id) REFERENCES transactions (id) ON DELETE CASCADE,
  CONSTRAINT fk_tt_tag
    FOREIGN KEY (tag_id) REFERENCES tags (id)
) ENGINE=InnoDB;
