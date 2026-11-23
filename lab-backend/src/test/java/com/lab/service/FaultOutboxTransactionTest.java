package com.lab.service;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.ResultSet;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class FaultOutboxTransactionTest {
    @Test
    void eventIsVisibleOnlyAfterCommitAndTracksOnlyAffectedReservations() throws Exception {
        Fixture f = new Fixture();
        f.tx.executeWithoutResult(s -> {
            f.record();
            f.jdbc.update("UPDATE reservation SET status=3 WHERE id=1");
            assertEquals(0, f.outsideCount());
        });
        assertEquals(1, f.outsideCount());
        assertEquals(3, f.jdbc.queryForObject("SELECT status FROM reservation WHERE id=1", Integer.class));
    }

    @Test
    void failedRepairAcceptanceRollsBackBothCancellationAndEvent() throws Exception {
        Fixture f = new Fixture();
        assertThrows(IllegalStateException.class, () -> f.tx.executeWithoutResult(s -> {
            f.record();
            f.jdbc.update("UPDATE reservation SET status=3 WHERE id=1");
            throw new IllegalStateException("repair record failure");
        }));
        assertEquals(0, f.outsideCount());
        assertEquals(1, f.jdbc.queryForObject("SELECT status FROM reservation WHERE id=1", Integer.class));
    }

    static class Fixture {
        final JdbcDataSource ds = new JdbcDataSource();
        final JdbcTemplate jdbc;
        final TransactionTemplate tx;
        final Object store;
        final Method record;
        Fixture() throws Exception {
            ds.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
            jdbc = new JdbcTemplate(ds);
            tx = new TransactionTemplate(new DataSourceTransactionManager(ds));
            jdbc.execute("CREATE TABLE reservation(id BIGINT PRIMARY KEY,student_id BIGINT,equipment_id BIGINT,start_time TIMESTAMP,end_time TIMESTAMP,status INT,deleted INT)");
            jdbc.execute("CREATE TABLE equipment(id BIGINT PRIMARY KEY,lab_id BIGINT)");
            jdbc.execute("INSERT INTO equipment VALUES(9,1)");
            jdbc.execute("CREATE TABLE fault_notification_outbox(id BIGINT AUTO_INCREMENT PRIMARY KEY,repair_order_id BIGINT,reservation_id BIGINT,student_id BIGINT,equipment_id BIGINT,lab_id BIGINT,start_time TIMESTAMP,delivery_status INT,attempts INT,created_at TIMESTAMP,updated_at TIMESTAMP,UNIQUE(repair_order_id,reservation_id))");
            jdbc.execute("INSERT INTO reservation VALUES(1,11,9,'2030-01-01 09:00:00','2030-01-01 11:00:00',1,0),(2,12,9,'2029-01-01 09:00:00','2029-01-01 11:00:00',1,0),(3,13,9,'2030-01-01 09:00:00','2030-01-01 11:00:00',3,0)");
            Class<?> type = assertDoesNotThrow(() -> Class.forName("com.lab.service.FaultOutboxStore"), "fault outbox store must exist");
            Constructor<?> constructor = type.getConstructor(JdbcTemplate.class);
            store = constructor.newInstance(jdbc);
            record = type.getMethod("recordAffected", Long.class, Long.class, LocalDateTime.class);
        }
        void record() {
            assertDoesNotThrow(() -> record.invoke(store, 4L, 9L, LocalDateTime.of(2030,1,1,8,0)));
        }
        int outsideCount() {
            try (Connection c = ds.getConnection(); ResultSet r = c.createStatement().executeQuery("SELECT COUNT(*) FROM fault_notification_outbox")) {
                r.next(); return r.getInt(1);
            } catch (Exception e) { throw new IllegalStateException(e); }
        }
    }
}
