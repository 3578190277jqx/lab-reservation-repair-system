package com.lab.service;

import cn.dev33.satoken.stp.StpLogic;
import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import com.lab.cache.EquipmentPageCache;
import com.lab.dto.RepairActionRequest;
import com.lab.mapper.*;
import com.lab.service.impl.RepairOrderServiceImpl;
import com.lab.utils.ReservationLockExecutor;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.SqlSessionFactory;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.transaction.SpringManagedTransactionFactory;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.test.util.ReflectionTestUtils;
import java.io.InputStream;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** H2、真实 MyBatis SQL 和事务；Sa-Token 与 Redisson 锁为模拟实现。 */
class RepairAcceptanceOutboxIntegrationTest {
    @Test void acceptanceCommitsEventThenPublishesAndAcknowledges() throws Exception {
        Fixture f = new Fixture(false);
        StpLogic previous = f.installManager();
        try {
            f.service.accept(f.request());
            assertEquals(1, f.integer("SELECT status FROM repair_order WHERE id=20"));
            assertEquals(3, f.integer("SELECT status FROM reservation WHERE id=30"));
            assertEquals(1, f.integer("SELECT COUNT(*) FROM repair_record"));
            assertEquals(0, f.integer("SELECT delivery_status FROM fault_notification_outbox"));
            assertEquals(0, f.notifications.my(2, 1, 20).getTotal());
            f.delivery.deliverDue();
            assertEquals(1, f.notifications.my(2, 1, 20).getTotal());
            assertEquals(1, f.notifications.contactTodos(1, false, 1, 20).getTotal());
            long id = f.jdbc.queryForObject("SELECT id FROM fault_notification_outbox", Long.class);
            f.notifications.contacted(1, false, id);
            assertEquals(0, f.notifications.contactTodos(1, false, 1, 20).getTotal());
            f.notifications.confirm(2, id);
            assertEquals(1, f.integer("SELECT COUNT(*) FROM fault_notification_outbox WHERE contacted_at IS NOT NULL AND confirmed_at IS NOT NULL"));
        } finally { StpUtil.stpLogic = previous; }
    }

    @Test void repairRecordConstraintFailureRollsBackTheWholeAcceptance() throws Exception {
        Fixture f = new Fixture(true);
        StpLogic previous = f.installManager();
        try {
            RuntimeException failure = assertThrows(RuntimeException.class, () -> f.service.accept(f.request()));
            assertTrue(failure.toString().toLowerCase().contains("repair_record"),
                    "failure must originate from the injected repair_record constraint");
            assertEquals(0, f.integer("SELECT status FROM repair_order WHERE id=20"));
            assertEquals(1, f.integer("SELECT status FROM reservation WHERE id=30"));
            assertEquals(0, f.integer("SELECT status FROM equipment WHERE id=10"));
            assertEquals(0, f.integer("SELECT COUNT(*) FROM fault_notification_outbox"));
            assertEquals(0, f.integer("SELECT COUNT(*) FROM repair_record"));
        } finally { StpUtil.stpLogic = previous; }
    }

    private static final class Fixture {
        final JdbcTemplate jdbc;
        final RepairOrderServiceImpl service;
        final FaultNotificationService notifications;
        final FaultNotificationDelivery delivery;
        Fixture(boolean rejectRecord) throws Exception {
            JdbcDataSource ds = new JdbcDataSource();
            ds.setURL("jdbc:h2:mem:"+UUID.randomUUID()+";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
            jdbc = new JdbcTemplate(ds);
            jdbc.execute("CREATE TABLE lab(id BIGINT PRIMARY KEY,lab_code VARCHAR(20),name VARCHAR(50),location VARCHAR(50),admin_id BIGINT,contact_phone VARCHAR(20),description VARCHAR(100),status INT,deleted INT,create_time TIMESTAMP,update_time TIMESTAMP)");
            jdbc.execute("CREATE TABLE equipment(id BIGINT PRIMARY KEY,lab_id BIGINT,equipment_code VARCHAR(20),name VARCHAR(50),model VARCHAR(50),manufacturer VARCHAR(50),status INT,purchase_date DATE,image_url VARCHAR(100),description VARCHAR(100),deleted INT,create_time TIMESTAMP,update_time TIMESTAMP)");
            jdbc.execute("CREATE TABLE repair_order(id BIGINT PRIMARY KEY,order_no VARCHAR(50),equipment_id BIGINT,reporter_id BIGINT,description VARCHAR(100),image_url VARCHAR(100),status INT,handler_id BIGINT,ai_suggestion VARCHAR(100),accepted_time TIMESTAMP,repair_start_time TIMESTAMP,completed_time TIMESTAMP,deleted INT,create_time TIMESTAMP,update_time TIMESTAMP)");
            jdbc.execute("CREATE TABLE reservation(id BIGINT PRIMARY KEY,student_id BIGINT,equipment_id BIGINT,start_time TIMESTAMP,end_time TIMESTAMP,status INT,deleted INT,sign_in_time TIMESTAMP,cancel_time TIMESTAMP,cancel_reason VARCHAR(200),update_time TIMESTAMP)");
            jdbc.execute("CREATE TABLE usage_log(id BIGINT PRIMARY KEY,reservation_id BIGINT,start_time TIMESTAMP,end_time TIMESTAMP,remark VARCHAR(100),deleted INT,update_time TIMESTAMP)");
            jdbc.execute("CREATE TABLE repair_record(id BIGINT AUTO_INCREMENT PRIMARY KEY,repair_order_id BIGINT,operator_id BIGINT,from_status INT,to_status INT"+(rejectRecord?" CHECK(to_status<>1)":"")+",content VARCHAR(200),repair_cost DECIMAL,operation_time TIMESTAMP,deleted INT DEFAULT 0,create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
            jdbc.execute("CREATE TABLE `user`(id BIGINT PRIMARY KEY,real_name VARCHAR(50),phone VARCHAR(30))");
            jdbc.execute("CREATE TABLE fault_notification_outbox(id BIGINT AUTO_INCREMENT PRIMARY KEY,repair_order_id BIGINT,reservation_id BIGINT,student_id BIGINT,equipment_id BIGINT,lab_id BIGINT,start_time TIMESTAMP,delivery_status INT,attempts INT,next_attempt_at TIMESTAMP,confirmed_at TIMESTAMP,contacted_at TIMESTAMP,contacted_by BIGINT,created_at TIMESTAMP,updated_at TIMESTAMP)");
            jdbc.execute("INSERT INTO lab(id,lab_code,name,location,admin_id,status,deleted) VALUES(5,'L','实验室','A',1,1,0)");
            jdbc.execute("INSERT INTO equipment(id,lab_id,name,status,deleted) VALUES(10,5,'设备',0,0)");
            jdbc.execute("INSERT INTO repair_order(id,order_no,equipment_id,reporter_id,description,status,deleted) VALUES(20,'RO-20',10,2,'故障',0,0)");
            jdbc.execute("INSERT INTO reservation(id,student_id,equipment_id,start_time,end_time,status,deleted) VALUES(30,2,10,'2030-01-01 09:00:00','2030-01-01 11:00:00',1,0)");
            jdbc.execute("INSERT INTO `user` VALUES(2,'学生',NULL)");
            MybatisConfiguration cfg = new MybatisConfiguration();
            cfg.setMapUnderscoreToCamelCase(true);
            cfg.setEnvironment(new Environment("h2", new SpringManagedTransactionFactory(), ds));
            String resource = "mapper/RepairOrderMapper.xml";
            try (InputStream in = getClass().getClassLoader().getResourceAsStream(resource)) {
                assertNotNull(in, "real repair mapper XML must be on test classpath");
                new XMLMapperBuilder(in, cfg, resource, cfg.getSqlFragments()).parse();
            }
            cfg.addMapper(EquipmentMapper.class); cfg.addMapper(LabMapper.class);
            cfg.addMapper(RepairRecordMapper.class); cfg.addMapper(ReservationMapper.class);
            SqlSessionFactory factory = new MybatisSqlSessionFactoryBuilder().build(cfg);
            SqlSessionTemplate session = new SqlSessionTemplate(factory);
            RedissonClient redisson = mock(RedissonClient.class); RLock lock = mock(RLock.class);
            when(redisson.getLock(anyString())).thenReturn(lock);
            when(lock.tryLock(anyLong(), any(TimeUnit.class))).thenReturn(true);
            when(lock.isHeldByCurrentThread()).thenReturn(true);
            Clock clock = Clock.fixed(Instant.parse("2030-01-01T08:00:00Z"), ZoneOffset.UTC);
            ReservationLockExecutor executor = new ReservationLockExecutor(redisson, new DataSourceTransactionManager(ds));
            service = new RepairOrderServiceImpl(session.getMapper(EquipmentMapper.class),session.getMapper(LabMapper.class),
                    session.getMapper(RepairRecordMapper.class),session.getMapper(ReservationMapper.class),executor,
                    mock(EquipmentPageCache.class),mock(LocalImageStorageService.class),mock(AiSuggestionService.class),
                    mock(UserService.class),clock,new FaultOutboxStore(jdbc));
            ReflectionTestUtils.setField(service, "baseMapper", session.getMapper(RepairOrderMapper.class));
            notifications = new FaultNotificationService(jdbc,clock);
            delivery = new FaultNotificationDelivery(jdbc,clock);
        }
        RepairActionRequest request(){RepairActionRequest r=new RepairActionRequest();r.setRepairOrderId(20L);return r;}
        int integer(String sql){return jdbc.queryForObject(sql,Integer.class);}
        StpLogic installManager(){StpLogic previous=StpUtil.stpLogic, fake=mock(StpLogic.class);StpUtil.stpLogic=fake;when(fake.hasRole("admin")).thenReturn(true);when(fake.getLoginIdAsLong()).thenReturn(1L);return previous;}
    }
}
