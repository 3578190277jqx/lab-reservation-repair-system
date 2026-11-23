package com.lab.mapper;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import java.io.InputStream;
import java.sql.Connection;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class PendingExpiryMapperTest {
    @Test void candidateScanIncludesAtOrBeforeStartAndExcludesOtherStatusesAndDeleted() throws Exception {
        JdbcDataSource ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE");
        MybatisConfiguration cfg = new MybatisConfiguration();cfg.setMapUnderscoreToCamelCase(true);
        cfg.setEnvironment(new Environment("memory", new JdbcTransactionFactory(), ds));
        String resource="mapper/ReservationMapper.xml";
        try (InputStream in=getClass().getClassLoader().getResourceAsStream(resource)) {
            new XMLMapperBuilder(in,cfg,resource,cfg.getSqlFragments()).parse();
        }
        try (SqlSession s=new MybatisSqlSessionFactoryBuilder().build(cfg).openSession(true)) {
            Connection c=s.getConnection();
            c.createStatement().execute("CREATE TABLE reservation(id BIGINT PRIMARY KEY,equipment_id BIGINT,status INT,deleted INT,start_time TIMESTAMP,end_time TIMESTAMP,sign_in_time TIMESTAMP)");
            c.createStatement().execute("INSERT INTO reservation VALUES (1,10,0,0,'2026-11-03 08:59:59','2026-11-03 17:00:00',NULL),(2,11,0,0,'2026-11-03 09:00:00','2026-11-03 17:00:00',NULL),(3,12,0,0,'2026-11-03 09:00:01','2026-11-03 17:00:00',NULL),(4,13,1,0,'2026-11-03 08:00:00','2026-11-03 17:00:00',NULL),(5,14,0,1,'2026-11-03 08:00:00','2026-11-03 17:00:00',NULL)");
            Map<String,Object> p=new HashMap<>();p.put("afterId",0L);p.put("limit",100);p.put("now",LocalDateTime.of(2026,11,3,9,0));
            assertEquals(Arrays.asList(1L,2L),s.selectList("com.lab.mapper.ReservationMapper.selectDuePendingExpiryIds",p));
            p.put("afterId",1L);assertEquals(Collections.singletonList(2L),s.selectList("com.lab.mapper.ReservationMapper.selectDuePendingExpiryIds",p));
            ReservationMapper mapper=s.getMapper(ReservationMapper.class);
            LocalDateTime from=LocalDateTime.of(2026,11,3,10,0),to=from.plusHours(1);
            assertEquals(1,mapper.countTimeConflicts(10L,from,to,null));
            assertEquals(1,mapper.selectOccupiedSlots(10L,from,to).size());
            c.createStatement().execute("UPDATE reservation SET status=5 WHERE id=1");s.clearCache();
            assertEquals(0,mapper.countTimeConflicts(10L,from,to,null));
            assertEquals(0,mapper.selectOccupiedSlots(10L,from,to).size());
            assertFalse(mapper.selectDueNoShowIds(0L,100,from).contains(1L));
        }
    }
}
