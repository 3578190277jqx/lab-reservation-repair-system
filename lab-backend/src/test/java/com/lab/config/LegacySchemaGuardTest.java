package com.lab.config;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import java.sql.*;
import static org.junit.jupiter.api.Assertions.*;

class LegacySchemaGuardTest {
    private Connection database() throws Exception {JdbcDataSource ds=new JdbcDataSource();ds.setURL("jdbc:h2:mem:"+System.nanoTime()+";MODE=MySQL;DATABASE_TO_LOWER=TRUE");return ds.getConnection();}
    private void legacy(Connection c) throws Exception {
        String[] definitions={"user(id bigint,username varchar(64),password varchar(255),role varchar(20))","lab(id bigint,admin_id bigint)","equipment(id bigint,lab_id bigint,equipment_code varchar(64),status tinyint)","reservation(id bigint,student_id bigint,equipment_id bigint,start_time datetime,end_time datetime,status tinyint)","blacklist(id bigint,student_id bigint,expire_time datetime)","repair_order(id bigint,equipment_id bigint,reporter_id bigint,status tinyint)","repair_record(id bigint,repair_order_id bigint,operator_id bigint,to_status tinyint)","usage_log(id bigint,reservation_id bigint,start_time datetime,end_time datetime)"};
        for(String def:definitions)c.createStatement().execute("CREATE TABLE `"+def.substring(0,def.indexOf('('))+"`"+def.substring(def.indexOf('(')));
    }
    @Test void permitsOnlyRecognizedEightTableLegacySchema() throws Exception {
        try(Connection c=database()) {assertFalse(LegacySchemaGuard.isKnownLegacy(c));legacy(c);assertTrue(LegacySchemaGuard.isKnownLegacy(c));}
    }
    @Test void refusesUnknownOrIncompleteSchemasInsteadOfBaseliningThem() throws Exception {
        try(Connection c=database()) {legacy(c);c.createStatement().execute("CREATE TABLE unrelated(id bigint)");assertFalse(LegacySchemaGuard.isKnownLegacy(c));}
        try(Connection c=database()) {legacy(c);c.createStatement().execute("ALTER TABLE reservation DROP COLUMN start_time");assertFalse(LegacySchemaGuard.isKnownLegacy(c));}
    }
    @Test void doesNotReadBusinessValuesOrResetAnyPasswordDuringSchemaInspection() throws Exception {
        try(Connection c=database()) {legacy(c);c.createStatement().execute("INSERT INTO `user` VALUES(1,'admin','keep-this-hash','admin')");assertTrue(LegacySchemaGuard.isKnownLegacy(c));try(ResultSet r=c.createStatement().executeQuery("SELECT password FROM `user`")){assertTrue(r.next());assertEquals("keep-this-hash",r.getString(1));}}
    }
}
