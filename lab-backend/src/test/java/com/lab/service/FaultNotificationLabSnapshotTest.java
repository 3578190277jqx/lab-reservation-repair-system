package com.lab.service;

import com.lab.common.BusinessException;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.core.io.ClassPathResource;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import java.sql.Connection;

import static org.junit.jupiter.api.Assertions.*;

/** 使用 H2 验证历史范围；不模拟真实 MySQL 或 Redisson 并发。 */
class FaultNotificationLabSnapshotTest {
    private static final LocalDateTime NOW = LocalDateTime.of(2030, 1, 1, 8, 0);

    @Test
    void equipmentTransferDoesNotTransferOldEventAccess() {
        Fixture f = new Fixture();
        f.insertEvent(1L);
        f.jdbc.update("UPDATE equipment SET lab_id=2 WHERE id=9");

        assertEquals(1, f.service.contactTodos(21, false, 1, 20).getTotal());
        assertEquals(0, f.service.contactTodos(22, false, 1, 20).getTotal());
        assertEquals(1, f.service.contactTodos(99, true, 1, 20).getTotal());
        assertThrows(BusinessException.class, () -> f.service.contacted(22, false, 1));
        f.service.contacted(21, false, 1);
        assertEquals(1, f.service.my(11, 1, 20).getTotal());
    }

    @Test
    void unknownHistoricalLabIsVisibleToAdminAndStudentOnly() {
        Fixture f = new Fixture();
        f.insertEvent(null);
        assertEquals(0, f.service.contactTodos(21, false, 1, 20).getTotal());
        assertEquals(0, f.service.contactTodos(22, false, 1, 20).getTotal());
        assertEquals(1, f.service.contactTodos(99, true, 1, 20).getTotal());
        assertNull(f.service.contactTodos(99, true, 1, 20).getRecords().get(0).get("labId"));
        assertThrows(BusinessException.class, () -> f.service.contacted(21, false, 1));
        assertThrows(BusinessException.class, () -> f.service.contacted(22, false, 1));
        assertEquals(1, f.service.my(11, 1, 20).getTotal());
        f.service.contacted(99, true, 1);
    }

    @Test
    void recordingAffectedReservationSnapshotsEquipmentLabInTransaction() {
        Fixture f = new Fixture();
        TransactionTemplate transaction = new TransactionTemplate(new DataSourceTransactionManager(f.ds));
        transaction.executeWithoutResult(status -> new FaultOutboxStore(f.jdbc).recordAffected(4L, 9L, NOW));
        assertEquals(1L, f.jdbc.queryForObject("SELECT lab_id FROM fault_notification_outbox WHERE reservation_id=101", Long.class));
        f.jdbc.update("UPDATE equipment SET lab_id=2 WHERE id=9");
        assertEquals(1L, f.jdbc.queryForObject("SELECT lab_id FROM fault_notification_outbox WHERE reservation_id=101", Long.class));
    }

    @Test
    void v6KeepsLegacyLabUnknownAndEnforcesKnownLabReference() throws Exception {
        JdbcDataSource ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        JdbcTemplate jdbc = new JdbcTemplate(ds);
        jdbc.execute("CREATE TABLE lab(id BIGINT PRIMARY KEY)");
        jdbc.execute("CREATE TABLE fault_notification_outbox(id BIGINT PRIMARY KEY, confirmed_at TIMESTAMP, contacted_at TIMESTAMP, start_time TIMESTAMP)");
        jdbc.execute("INSERT INTO fault_notification_outbox(id) VALUES(1)");
        try (Connection connection = ds.getConnection()) {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("db/migration/V6__Fault_notification_lab_snapshot.sql"));
        }
        assertNull(jdbc.queryForObject("SELECT lab_id FROM fault_notification_outbox WHERE id=1", Long.class));
        jdbc.execute("INSERT INTO lab VALUES(1)");
        jdbc.update("UPDATE fault_notification_outbox SET lab_id=1 WHERE id=1");
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class,
                () -> jdbc.update("UPDATE fault_notification_outbox SET lab_id=2 WHERE id=1"));
    }

    private static class Fixture {
        final JdbcDataSource ds = new JdbcDataSource();
        final JdbcTemplate jdbc;
        final FaultNotificationService service;

        Fixture() {
            ds.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
            jdbc = new JdbcTemplate(ds);
            jdbc.execute("CREATE TABLE `user`(id BIGINT PRIMARY KEY,real_name VARCHAR(64),phone VARCHAR(20))");
            jdbc.execute("CREATE TABLE lab(id BIGINT PRIMARY KEY,admin_id BIGINT,deleted INT)");
            jdbc.execute("CREATE TABLE equipment(id BIGINT PRIMARY KEY,lab_id BIGINT,name VARCHAR(50))");
            jdbc.execute("CREATE TABLE reservation(id BIGINT PRIMARY KEY,student_id BIGINT,equipment_id BIGINT,start_time TIMESTAMP,end_time TIMESTAMP,status INT,deleted INT)");
            jdbc.execute("CREATE TABLE fault_notification_outbox(id BIGINT AUTO_INCREMENT PRIMARY KEY,repair_order_id BIGINT,reservation_id BIGINT,student_id BIGINT,equipment_id BIGINT,lab_id BIGINT,start_time TIMESTAMP,delivery_status INT,attempts INT,confirmed_at TIMESTAMP,contacted_at TIMESTAMP,contacted_by BIGINT,created_at TIMESTAMP,updated_at TIMESTAMP,next_attempt_at TIMESTAMP)");
            jdbc.execute("INSERT INTO `user` VALUES(11,'测试学生','13800000000')");
            jdbc.execute("INSERT INTO lab VALUES(1,21,0),(2,22,0)");
            jdbc.execute("INSERT INTO equipment VALUES(9,1,'测试设备')");
            jdbc.execute("INSERT INTO reservation VALUES(101,11,9,'2030-01-01 09:00:00','2030-01-01 11:00:00',1,0)");
            service = new FaultNotificationService(jdbc, Clock.fixed(Instant.parse("2030-01-01T08:00:00Z"), ZoneOffset.UTC));
        }

        void insertEvent(Long labId) {
            jdbc.update("INSERT INTO fault_notification_outbox(id,repair_order_id,reservation_id,student_id,equipment_id,lab_id,start_time,delivery_status,attempts,created_at,updated_at) VALUES(1,4,101,11,9,?,'2030-01-01 09:00:00',1,0,'2030-01-01 08:00:00','2030-01-01 08:00:00')", labId);
        }
    }
}
