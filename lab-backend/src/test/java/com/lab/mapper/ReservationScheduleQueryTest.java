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
import java.sql.*;
import java.time.LocalDateTime;
import java.util.*;
import com.lab.vo.ReservationSlotVO;
import static org.junit.jupiter.api.Assertions.*;

class ReservationScheduleQueryTest {
    @Test
    void realSqlHonorsCrossDayBoundariesStatusesDeletionAndEquipment() throws Exception {
        JdbcDataSource ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE");
        MybatisConfiguration config = new MybatisConfiguration();
        config.setMapUnderscoreToCamelCase(true);
        config.setEnvironment(new Environment("test", new JdbcTransactionFactory(), ds));
        String resource = "mapper/ReservationMapper.xml";
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(resource)) {
            new XMLMapperBuilder(in, config, resource, config.getSqlFragments()).parse();
        }
        try (SqlSession session = new MybatisSqlSessionFactoryBuilder().build(config).openSession(true)) {
            Connection c = session.getConnection();
            c.createStatement().execute("CREATE TABLE reservation(id BIGINT PRIMARY KEY, equipment_id BIGINT, status INT, deleted INT, start_time TIMESTAMP, end_time TIMESTAMP)");
            // Midnight adjacency is excluded; a previous day's ongoing booking is included.
            String[][] rows = {
                {"1","10","0","0","2026-10-08 23:00:00","2026-10-09 01:00:00"},
                {"2","10","1","0","2026-10-09 09:00:00","2026-10-09 10:00:00"},
                {"3","10","1","0","2026-10-08 22:00:00","2026-10-09 00:00:00"},
                {"4","10","1","0","2026-10-10 00:00:00","2026-10-10 01:00:00"},
                {"5","10","2","0","2026-10-09 09:00:00","2026-10-09 10:00:00"},
                {"6","10","3","0","2026-10-09 09:00:00","2026-10-09 10:00:00"},
                {"7","10","4","0","2026-10-09 09:00:00","2026-10-09 10:00:00"},
                {"8","10","0","1","2026-10-09 09:00:00","2026-10-09 10:00:00"},
                {"9","11","0","0","2026-10-09 09:00:00","2026-10-09 10:00:00"}
            };
            for(String[] row:rows) {
                try(PreparedStatement p=c.prepareStatement("INSERT INTO reservation VALUES(?,?,?,?,?,?)")) {
                    for(int i=0;i<4;i++)p.setInt(i+1,Integer.parseInt(row[i]));
                    p.setTimestamp(5,Timestamp.valueOf(row[4]));p.setTimestamp(6,Timestamp.valueOf(row[5]));p.executeUpdate();
                }
            }
            ReservationMapper mapper = session.getMapper(ReservationMapper.class);
            LocalDateTime start = LocalDateTime.of(2026,10,9,0,0), end=start.plusDays(1);
            List<ReservationSlotVO> slots=mapper.selectOccupiedSlots(10L,start,end);
            assertEquals(2,slots.size());
            assertEquals(start.minusHours(1),slots.get(0).getStartTime());
            assertEquals(Integer.valueOf(0),slots.get(0).getStatus());
            assertEquals(start.plusHours(10),slots.get(1).getEndTime());
            assertEquals(0,mapper.countTimeConflicts(10L,start.plusHours(10),start.plusHours(11),null));
            assertEquals(1,mapper.countTimeConflicts(10L,start.plusHours(9),start.plusHours(10),null));
            for(int i=100;i<310;i++)c.createStatement().execute("INSERT INTO reservation VALUES("+i+",10,0,0,'2026-10-09 12:00:00','2026-10-09 13:00:00')");
            session.clearCache(); // 测试通过JDBC直接插入，显式刷新MyBatis一级缓存。
            assertEquals(201,mapper.selectOccupiedSlots(10L,start,end).size());
        }
    }
}
