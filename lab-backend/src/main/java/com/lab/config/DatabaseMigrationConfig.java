package com.lab.config;

import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Set;

@Configuration
public class DatabaseMigrationConfig {
    @Bean
    public FlywayMigrationStrategy verifiedLegacyBaseline(@Value("${app.schema.adopt-existing:false}") boolean adoptExisting) {
        return flyway -> {
            try (Connection connection = flyway.getConfiguration().getDataSource().getConnection()) {
                Set<String> tables = LegacySchemaGuard.tables(connection);
                if (!tables.isEmpty() && !tables.contains("flyway_schema_history")) {
                    if (!adoptExisting) throw new IllegalStateException("检测到无迁移历史的存量库。请先备份并核对README迁移步骤，再显式设置SCHEMA_ADOPT_EXISTING=true接管。");
                    if (!LegacySchemaGuard.isKnownLegacy(connection)) throw new IllegalStateException("存量库表名或关键列与已知8表结构不一致，拒绝自动建立迁移基线，请人工核对。");
                    // baseline-on-migrate仍为false，只有显式开关且预检通过才建立V1基线。
                    flyway.baseline();
                }
            } catch (SQLException unavailable) {
                throw new IllegalStateException("数据库迁移元数据检查失败，请检查数据库连接与权限");
            }
            flyway.migrate();
        };
    }
}
