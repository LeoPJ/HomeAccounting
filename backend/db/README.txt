数据库约定（MySQL）

术语：MySQL 里 SCHEMA 与 DATABASE 同义；当前只有一个库 home_accounting。
Java 侧实体与 Mapper 统一放在 com.homeaccounting.entity / com.homeaccounting.mapper，
XML 在 classpath:mapper/。

1. Flyway：backend/src/main/resources/db/migration/（当前基线 V1__baseline.sql）
2. 首次建库：backend/db/bootstrap/00_create_databases.sql
3. JDBC 与 flyway_schema_history 均在 home_accounting。

若曾用旧版分包 Generator 生成过代码，重新生成前请删掉旧的 entity/mapper/xml 以免混淆。
