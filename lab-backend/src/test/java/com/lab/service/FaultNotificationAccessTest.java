package com.lab.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lab.common.BusinessException;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.lang.reflect.InvocationTargetException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class FaultNotificationAccessTest {
    @Test void studentAndManagerCannotReadOrChangeOtherUsersMessages() throws Exception {
        JdbcDataSource ds=new JdbcDataSource();ds.setURL("jdbc:h2:mem:"+UUID.randomUUID()+";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        JdbcTemplate j=new JdbcTemplate(ds);
        j.execute("CREATE TABLE `user`(id BIGINT PRIMARY KEY,real_name VARCHAR(64),phone VARCHAR(20))");
        j.execute("CREATE TABLE lab(id BIGINT PRIMARY KEY,admin_id BIGINT,deleted INT)");
        j.execute("CREATE TABLE equipment(id BIGINT PRIMARY KEY,lab_id BIGINT,name VARCHAR(50))");
        j.execute("CREATE TABLE reservation(id BIGINT PRIMARY KEY,end_time TIMESTAMP)");
        j.execute("CREATE TABLE fault_notification_outbox(id BIGINT PRIMARY KEY,repair_order_id BIGINT,reservation_id BIGINT,student_id BIGINT,equipment_id BIGINT,lab_id BIGINT,start_time TIMESTAMP,delivery_status INT,attempts INT,confirmed_at TIMESTAMP,contacted_at TIMESTAMP,contacted_by BIGINT,created_at TIMESTAMP,updated_at TIMESTAMP,next_attempt_at TIMESTAMP)");
        j.execute("INSERT INTO `user` VALUES(11,'学生甲','13800000000'),(12,'学生乙','13900000000')");
        j.execute("INSERT INTO lab VALUES(1,21,0),(2,22,0)");
        j.execute("INSERT INTO equipment VALUES(1,1,'设备甲'),(2,2,'设备乙')");
        j.execute("INSERT INTO reservation VALUES(101,'2030-01-01 12:00:00'),(102,'2030-01-01 12:00:00')");
        j.execute("INSERT INTO fault_notification_outbox VALUES(1,1,101,11,1,1,'2030-01-01 09:00:00',1,0,NULL,NULL,NULL,'2030-01-01 07:00:00','2030-01-01 07:00:00',NULL),(2,2,102,12,2,2,'2030-01-01 09:00:00',1,0,NULL,NULL,NULL,'2030-01-01 07:00:00','2030-01-01 07:00:00',NULL)");
        Class<?> c=assertDoesNotThrow(()->Class.forName("com.lab.service.FaultNotificationService"));
        Object service=c.getConstructor(JdbcTemplate.class,Clock.class).newInstance(j,Clock.fixed(Instant.parse("2030-01-01T08:00:00Z"),ZoneOffset.UTC));
        @SuppressWarnings("unchecked") Page<Map<String,Object>> mine=(Page<Map<String,Object>>)c.getMethod("my",long.class,long.class,long.class).invoke(service,11L,1L,20L);
        assertEquals(1,mine.getTotal()); assertFalse(mine.getRecords().get(0).containsKey("studentPhone"));
        assertTrue(assertThrows(InvocationTargetException.class,()->c.getMethod("confirm",long.class,long.class).invoke(service,11L,2L)).getCause() instanceof BusinessException);
        @SuppressWarnings("unchecked") Page<Map<String,Object>> todos=(Page<Map<String,Object>>)c.getMethod("contactTodos",long.class,boolean.class,long.class,long.class).invoke(service,21L,false,1L,20L);
        assertEquals(1,todos.getTotal()); assertEquals("学生甲",todos.getRecords().get(0).get("studentName"));
        assertTrue(assertThrows(InvocationTargetException.class,()->c.getMethod("contacted",long.class,boolean.class,long.class).invoke(service,21L,false,2L)).getCause() instanceof BusinessException);
        c.getMethod("contacted",long.class,boolean.class,long.class).invoke(service,21L,false,1L);
        c.getMethod("contacted",long.class,boolean.class,long.class).invoke(service,21L,false,1L);
        c.getMethod("confirm",long.class,long.class).invoke(service,11L,1L);
        c.getMethod("confirm",long.class,long.class).invoke(service,11L,1L);
    }
}
