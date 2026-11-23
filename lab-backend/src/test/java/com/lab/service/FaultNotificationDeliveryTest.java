package com.lab.service;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class FaultNotificationDeliveryTest {
    @Test void scheduledTaskUsesInAppPublicationTerminology() {
        assertDoesNotThrow(() -> {
            Class<?> type = Class.forName("com.lab.service.FaultNotificationDelivery");
            java.lang.reflect.Method method = type.getMethod("publishDue");
            assertNotNull(method.getAnnotation(org.springframework.scheduling.annotation.Scheduled.class));
        }, "the scheduled in-app visibility task must be named as publication");
    }
    @Test void twoWorkersPublishOnceAndRespectRetryTime() throws Exception {
        JdbcDataSource ds=new JdbcDataSource();ds.setURL("jdbc:h2:mem:"+UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1");JdbcTemplate j=new JdbcTemplate(ds);
        j.execute("CREATE TABLE fault_notification_outbox(id BIGINT PRIMARY KEY,delivery_status INT,attempts INT,next_attempt_at TIMESTAMP,updated_at TIMESTAMP)");
        j.execute("INSERT INTO fault_notification_outbox VALUES(1,0,0,NULL,NULL),(2,2,1,'2029-12-31 00:00:00',NULL),(3,2,1,'2031-01-01 00:00:00',NULL)");
        Class<?> type=assertDoesNotThrow(()->Class.forName("com.lab.service.FaultNotificationDelivery"));
        Object worker=type.getConstructor(JdbcTemplate.class,Clock.class).newInstance(j,Clock.fixed(Instant.parse("2030-01-01T08:00:00Z"),ZoneOffset.UTC));
        java.lang.reflect.Method run=type.getMethod("deliverDue");
        ExecutorService pool=Executors.newFixedThreadPool(2);
        try {
            Future<?> a=pool.submit(()->{try{run.invoke(worker);}catch(Exception e){throw new RuntimeException(e);}});
            Future<?> b=pool.submit(()->{try{run.invoke(worker);}catch(Exception e){throw new RuntimeException(e);}});
            a.get(5,TimeUnit.SECONDS);b.get(5,TimeUnit.SECONDS);
        } finally {pool.shutdownNow();}
        assertEquals(1,j.queryForObject("SELECT delivery_status FROM fault_notification_outbox WHERE id=1",Integer.class));
        assertEquals(1,j.queryForObject("SELECT attempts FROM fault_notification_outbox WHERE id=1",Integer.class));
        assertEquals(1,j.queryForObject("SELECT delivery_status FROM fault_notification_outbox WHERE id=2",Integer.class));
        assertEquals(2,j.queryForObject("SELECT attempts FROM fault_notification_outbox WHERE id=2",Integer.class));
        assertEquals(2,j.queryForObject("SELECT delivery_status FROM fault_notification_outbox WHERE id=3",Integer.class));
    }
}
