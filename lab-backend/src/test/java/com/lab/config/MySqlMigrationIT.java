package com.lab.config;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import java.sql.*;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

/** 显式开启后只创建独立随机测试库，绝不在业务库执行DDL或删库。 */
@EnabledIfEnvironmentVariable(named="LAB_LOCAL_MYSQL_TEST",matches="true")
class MySqlMigrationIT {
    @Test void freshMySqlSchemaMigratesToNineTablesAndIsIdempotent() throws Exception {
        String host=System.getenv().getOrDefault("MYSQL_HOST","127.0.0.1");
        assertTrue(host.equals("127.0.0.1")||host.equals("localhost"),"测试只允许本地MySQL");
        String port=System.getenv().getOrDefault("MYSQL_PORT","3306");
        assertTrue(port.matches("[0-9]{1,5}"));
        String user=System.getenv().getOrDefault("MYSQL_USERNAME","root"),password=System.getenv().getOrDefault("MYSQL_PASSWORD","");
        String db="lab_review_20261027_"+UUID.randomUUID().toString().replace("-","").substring(0,8);
        String base="jdbc:mysql://"+host+":"+port+"/";
        String options="?useSSL=false&allowPublicKeyRetrieval=true&connectionTimeZone=GMT%2B08:00";
        try(Connection c=DriverManager.getConnection(base+options,user,password)) {
            c.createStatement().execute("CREATE DATABASE `"+db+"` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci");
        }
        DriverManagerDataSource ds=new DriverManagerDataSource(base+db+options,user,password);
        Flyway flyway=Flyway.configure().dataSource(ds).baselineOnMigrate(false).cleanDisabled(true).load();
        DatabaseMigrationConfig config=new DatabaseMigrationConfig();config.verifiedLegacyBaseline(false).migrate(flyway);
        config.verifiedLegacyBaseline(false).migrate(flyway);
        try(Connection c=ds.getConnection()) {
            assertEquals(11,LegacySchemaGuard.tables(c).size()); // 业务/审计/通知表及迁移历史表
            assertTrue(LegacySchemaGuard.tables(c).contains("operation_log"));
            try(ResultSet r=c.createStatement().executeQuery("SELECT COUNT(*) FROM flyway_schema_history WHERE success=1 AND version IS NOT NULL")) {assertTrue(r.next());assertEquals(6,r.getInt(1));}
        }
        System.out.println("ISOLATED_MYSQL_TEST_DATABASE="+db);
    }
}
