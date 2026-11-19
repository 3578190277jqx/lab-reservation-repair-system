package com.lab.config;

import org.flywaydb.core.Flyway;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import java.sql.*;
import static org.junit.jupiter.api.Assertions.*;

class DatabaseMigrationIntegrationTest {
    private JdbcDataSource legacy() throws Exception {
        JdbcDataSource ds=new JdbcDataSource();ds.setURL("jdbc:h2:mem:migration"+System.nanoTime()+";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        String[] defs={"user(id bigint,username varchar(64),password varchar(255),role varchar(20))","lab(id bigint,admin_id bigint)","equipment(id bigint,lab_id bigint,equipment_code varchar(64),status tinyint)","reservation(id bigint,student_id bigint,equipment_id bigint,start_time datetime,end_time datetime,status tinyint)","blacklist(id bigint,student_id bigint,expire_time datetime)","repair_order(id bigint,equipment_id bigint,reporter_id bigint,status tinyint)","repair_record(id bigint,repair_order_id bigint,operator_id bigint,to_status tinyint)","usage_log(id bigint,reservation_id bigint,start_time datetime,end_time datetime)"};
        try(Connection c=ds.getConnection()) {
            // 已知存量库的 id 均为主键；V5 外键需要在夹具中体现这一结构。
            for(String def:defs)c.createStatement().execute("CREATE TABLE `"+def.substring(0,def.indexOf('('))+"`"+def.substring(def.indexOf('(')).replaceFirst("\\(id bigint,", "(id bigint primary key,"));
            c.createStatement().execute("INSERT INTO `user` VALUES(1,'admin','preserve-hash','admin')");
        }
        return ds;
    }
    @Test void explicitLegacyAdoptionMigratesOnceAndPreservesUserData() throws Exception {
        JdbcDataSource ds=legacy();
        Flyway flyway=Flyway.configure().dataSource(ds).baselineOnMigrate(false).cleanDisabled(true).load();
        DatabaseMigrationConfig config=new DatabaseMigrationConfig();
        config.verifiedLegacyBaseline(true).migrate(flyway);
        config.verifiedLegacyBaseline(true).migrate(flyway);
        try(Connection c=ds.getConnection();ResultSet r=c.createStatement().executeQuery("SELECT password,auth_version,email_verified,phone_verified FROM `user` WHERE id=1")){
            assertTrue(r.next());assertEquals("preserve-hash",r.getString(1));assertEquals(0,r.getInt(2));assertEquals(0,r.getInt(3));assertEquals(0,r.getInt(4));
        }
        try(Connection c=ds.getConnection();ResultSet r=c.createStatement().executeQuery("SELECT COUNT(*) FROM flyway_schema_history WHERE success=true AND version IS NOT NULL")) {assertTrue(r.next());assertEquals(6,r.getInt(1));}
        try(Connection c=ds.getConnection()) {assertTrue(LegacySchemaGuard.tables(c).contains("operation_log"));}
    }
    @Test void refusesUnapprovedLegacyAdoptionWithoutWritingMigrationHistory() throws Exception {
        JdbcDataSource ds=legacy();Flyway flyway=Flyway.configure().dataSource(ds).baselineOnMigrate(false).cleanDisabled(true).load();
        assertThrows(IllegalStateException.class,()->new DatabaseMigrationConfig().verifiedLegacyBaseline(false).migrate(flyway));
        try(Connection c=ds.getConnection()){assertFalse(LegacySchemaGuard.tables(c).contains("flyway_schema_history"));}
    }
}
