-- MySQL 里 DATABASE 与 SCHEMA 同义；这里只建应用使用的这一个库。
-- 首次有云库权限时执行一次；应用 JDBC 指向同一库名。

CREATE DATABASE IF NOT EXISTS home_accounting
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_unicode_ci;
