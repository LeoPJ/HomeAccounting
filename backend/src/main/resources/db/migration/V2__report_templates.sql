USE home_accounting;

CREATE TABLE report_templates (
  id             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  household_id   BIGINT UNSIGNED NOT NULL,
  name           VARCHAR(128)    NOT NULL,
  definition     JSON            NOT NULL COMMENT '筛选、维度、指标、排序等，结构由服务端校验',
  created_by     BIGINT UNSIGNED NOT NULL,
  created_at     DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at     DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_report_templates_house_name (household_id, name),
  KEY idx_report_templates_household (household_id),
  CONSTRAINT fk_report_templates_household
    FOREIGN KEY (household_id) REFERENCES households (id),
  CONSTRAINT fk_report_templates_creator
    FOREIGN KEY (created_by) REFERENCES users (id)
) ENGINE=InnoDB;
