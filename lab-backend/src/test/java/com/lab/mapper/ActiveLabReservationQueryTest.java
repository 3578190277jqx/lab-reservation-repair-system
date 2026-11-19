package com.lab.mapper;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.InputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** 执行真实 Mapper XML，覆盖状态、结束边界、逻辑删除和实验室归属；不访问外部服务。 */
class ActiveLabReservationQueryTest {
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 2, 10, 0);

    @ParameterizedTest
    @CsvSource({
            "0,60,0,0,1,true",   // pending
            "1,60,0,0,1,true",   // approved
            "2,60,0,0,1,false",  // rejected
            "3,60,0,0,1,false",  // cancelled
            "4,60,0,0,1,false",  // no-show
            "1,0,0,0,1,false",   // ends exactly now
            "1,-1,0,0,1,false",  // already ended
            "1,60,1,0,1,false",  // logically deleted reservation
            "1,60,0,1,1,false",  // logically deleted equipment
            "1,60,0,0,2,false"   // equipment belongs to a different lab
    })
    void onlyUnfinishedPendingOrApprovedReservationsBlockLabClosure(int status, int minutesToEnd,
            int reservationDeleted, int equipmentDeleted, int equipmentLab, boolean expected) throws Exception {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE");
        MybatisConfiguration config = new MybatisConfiguration();
        config.setEnvironment(new Environment("test", new JdbcTransactionFactory(), dataSource));
        String resource = "mapper/ReservationMapper.xml";
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(resource)) {
            assertNotNull(input);
            new XMLMapperBuilder(input, config, resource, config.getSqlFragments()).parse();
        }
        try (SqlSession session = new MybatisSqlSessionFactoryBuilder().build(config).openSession(true)) {
            Connection connection = session.getConnection();
            try (Statement statement = connection.createStatement()) {
                statement.execute("CREATE TABLE equipment(id BIGINT PRIMARY KEY, lab_id BIGINT, deleted INT)");
                statement.execute("CREATE TABLE reservation(id BIGINT PRIMARY KEY, equipment_id BIGINT, "
                        + "status INT, end_time TIMESTAMP, deleted INT)");
                statement.execute("INSERT INTO equipment VALUES (10, " + equipmentLab + ", " + equipmentDeleted + ")");
            }
            try (PreparedStatement statement = connection.prepareStatement("INSERT INTO reservation VALUES(20,10,?,?,?)")) {
                statement.setInt(1, status);
                statement.setTimestamp(2, Timestamp.valueOf(NOW.plusMinutes(minutesToEnd)));
                statement.setInt(3, reservationDeleted);
                statement.executeUpdate();
            }
            Long reservationId = session.getMapper(ReservationMapper.class).selectActiveReservationIdByLab(1L, NOW);
            assertEquals(expected ? Long.valueOf(20) : null, reservationId);
        }
    }
}
