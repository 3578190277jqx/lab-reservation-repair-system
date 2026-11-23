package com.lab.mapper;

import cn.dev33.satoken.stp.StpLogic;
import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import com.lab.dto.*;
import com.lab.service.impl.StatsServiceImpl;
import com.lab.vo.ReservationTrendVO;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import java.io.InputStream;
import java.sql.Connection;
import java.time.*;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PendingExpiryStatsTest {
    @Test void expiredHasOwnAggregateAndTrendSeriesAndNoOtherStatusChanges() throws Exception {
        JdbcDataSource ds=new JdbcDataSource();ds.setURL("jdbc:h2:mem:"+UUID.randomUUID()+";MODE=MySQL;DATABASE_TO_LOWER=TRUE");
        MybatisConfiguration cfg=new MybatisConfiguration();cfg.setMapUnderscoreToCamelCase(true);
        cfg.setEnvironment(new Environment("test",new JdbcTransactionFactory(),ds));
        String file="mapper/StatsMapper.xml";
        try(InputStream in=getClass().getClassLoader().getResourceAsStream(file)){new XMLMapperBuilder(in,cfg,file,cfg.getSqlFragments()).parse();}
        try(SqlSession s=new MybatisSqlSessionFactoryBuilder().build(cfg).openSession(true)) {
            Connection c=s.getConnection();
            c.createStatement().execute("CREATE TABLE lab(id BIGINT PRIMARY KEY,deleted INT,admin_id BIGINT)");
            c.createStatement().execute("CREATE TABLE equipment(id BIGINT PRIMARY KEY,deleted INT,lab_id BIGINT)");
            c.createStatement().execute("CREATE TABLE reservation(id BIGINT PRIMARY KEY,deleted INT,equipment_id BIGINT,status INT,apply_time TIMESTAMP)");
            c.createStatement().execute("INSERT INTO lab VALUES(1,0,7)");c.createStatement().execute("INSERT INTO equipment VALUES(10,0,1)");
            for(int status=0;status<=5;status++)c.createStatement().execute("INSERT INTO reservation VALUES("+(status+1)+",0,10,"+status+",'2026-11-03 08:00:00')");
            StatsScope scope=new StatsScope();scope.setFromTime(LocalDate.of(2026,11,3).atStartOfDay());scope.setToTime(LocalDate.of(2026,11,4).atStartOfDay());
            StatsMapper mapper=s.getMapper(StatsMapper.class);StatsAggregateRows.ReservationCounts a=mapper.selectReservationCounts(scope);
            assertEquals(6L,a.getTotal());assertEquals(1L,a.getPending());assertEquals(1L,a.getRejected());assertEquals(1L,a.getNoShow());assertEquals(1L,a.getExpired());
            assertEquals(1L,mapper.selectReservationTrend(scope).get(0).getExpired());
            StpLogic previous=StpUtil.stpLogic,auth=mock(StpLogic.class);StpUtil.stpLogic=auth;when(auth.hasRole("admin")).thenReturn(true);
            try {
                Clock clock=Clock.fixed(Instant.parse("2026-11-03T12:00:00Z"),ZoneId.of("Asia/Shanghai"));
                StatsQuery query=new StatsQuery();query.setStartDate(LocalDate.of(2026,11,3));query.setEndDate(LocalDate.of(2026,11,3));
                ReservationTrendVO trend=new StatsServiceImpl(mapper,mock(LabMapper.class),clock).reservationTrend(query);
                assertEquals(7,trend.getSeries().size());assertEquals("审核超时",trend.getSeries().get(6).getName());
                assertEquals(1,trend.getSeries().get(6).getData().get(0).intValue());assertEquals(6,trend.getSeries().get(0).getData().get(0).intValue());
            } finally {StpUtil.stpLogic=previous;}
        }
    }
}
