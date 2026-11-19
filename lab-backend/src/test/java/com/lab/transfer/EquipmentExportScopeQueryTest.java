package com.lab.transfer;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import com.lab.dto.EquipmentPageQuery;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import java.io.InputStream;
import java.sql.Connection;
import java.util.Collections;
import static org.junit.jupiter.api.Assertions.*;

class EquipmentExportScopeQueryTest {
    @Test void realSqlCannotExportOtherManagersOrDeletedLabEquipment() throws Exception {
        JdbcDataSource ds=new JdbcDataSource();ds.setURL("jdbc:h2:mem:export"+System.nanoTime()+";MODE=MySQL;DB_CLOSE_DELAY=-1");
        try(Connection c=ds.getConnection()) {
            c.createStatement().execute("CREATE TABLE lab(id bigint,admin_id bigint,deleted tinyint)");
            c.createStatement().execute("CREATE TABLE equipment(id bigint,lab_id bigint,equipment_code varchar(64),name varchar(100),model varchar(100),status tinyint,deleted tinyint)");
            c.createStatement().execute("INSERT INTO lab VALUES(1,22,0),(2,99,0),(3,22,1)");
            c.createStatement().execute("INSERT INTO equipment VALUES(1,1,'EQ1','示波器','A',0,0),(2,2,'EQ2','万用表','B',0,0),(3,3,'EQ3','光学平台','C',0,0),(4,1,'EQ4','旧设备','D',0,1)");
        }
        MybatisConfiguration config=new MybatisConfiguration();config.setMapUnderscoreToCamelCase(true);
        config.setEnvironment(new Environment("test",new JdbcTransactionFactory(),ds));
        try(InputStream input=getClass().getResourceAsStream("/mapper/EquipmentTransferMapper.xml")) {
            new XMLMapperBuilder(input,config,"mapper/EquipmentTransferMapper.xml",config.getSqlFragments()).parse();
        }
        try(SqlSession session=new MybatisSqlSessionFactoryBuilder().build(config).openSession()) {
            EquipmentTransferMapper mapper=session.getMapper(EquipmentTransferMapper.class);EquipmentPageQuery query=new EquipmentPageQuery();
            assertEquals(1,mapper.selectForExport(query,22L).size());
            assertEquals("EQ1",mapper.selectForExport(query,22L).get(0).getEquipmentCode());
            query.setLabId(2L);assertTrue(mapper.selectForExport(query,22L).isEmpty());
            query.setLabId(null);assertEquals(2,mapper.selectForExport(query,null).size());
            assertEquals(Collections.singletonList("EQ4"),mapper.findExistingCodes(Collections.singletonList("EQ4")));
        }
    }
}
