package com.lab.transfer;

import cn.dev33.satoken.stp.StpLogic;
import cn.dev33.satoken.stp.StpUtil;
import com.lab.cache.EquipmentPageCache;
import com.lab.entity.Equipment;
import com.lab.entity.Lab;
import com.lab.mapper.EquipmentMapper;
import com.lab.mapper.LabMapper;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import javax.validation.Validation;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class EquipmentImportTransactionTest {
    @Test void databaseConflictOnSecondInsertRollsBackFirstInsert() throws Exception {
        JdbcDataSource ds=new JdbcDataSource();ds.setURL("jdbc:h2:mem:atomic"+System.nanoTime()+";MODE=MySQL;DB_CLOSE_DELAY=-1");
        JdbcTemplate jdbc=new JdbcTemplate(ds);jdbc.execute("CREATE TABLE equipment(id bigint auto_increment primary key,equipment_code varchar(64) unique,name varchar(100))");
        jdbc.update("INSERT INTO equipment(equipment_code,name) VALUES('EQ-conflict','原有设备')");
        StpLogic previous=StpUtil.stpLogic;StpLogic auth=mock(StpLogic.class);StpUtil.stpLogic=auth;when(auth.hasRole("admin")).thenReturn(true);
        try {
            EquipmentWorkbook codec=mock(EquipmentWorkbook.class);EquipmentMapper mapper=mock(EquipmentMapper.class);
            EquipmentTransferMapper transfers=mock(EquipmentTransferMapper.class);LabMapper labs=mock(LabMapper.class);
            EquipmentPageCache cache=mock(EquipmentPageCache.class);
            EquipmentRow first=row("EQ-new"),second=row("EQ-conflict");when(codec.read(any())).thenReturn(Arrays.asList(first,second));
            when(transfers.findExistingCodes(anyList())).thenReturn(Collections.emptyList());
            Lab lab=new Lab();lab.setId(1L);when(labs.selectByIdForUpdate(1L)).thenReturn(lab);
            when(mapper.insert(any(Equipment.class))).thenAnswer(call->{Equipment e=call.getArgument(0);return jdbc.update("INSERT INTO equipment(equipment_code,name) VALUES(?,?)",e.getEquipmentCode(),e.getName());});
            EquipmentTransferService target=new EquipmentTransferService(codec,mapper,transfers,labs,cache,Validation.buildDefaultValidatorFactory().getValidator());
            ProxyFactory proxy=new ProxyFactory(target);proxy.setProxyTargetClass(true);
            proxy.addAdvice(new TransactionInterceptor(new DataSourceTransactionManager(ds),new AnnotationTransactionAttributeSource()));
            EquipmentTransferService service=(EquipmentTransferService)proxy.getProxy();
            assertThrows(org.springframework.dao.DataIntegrityViolationException.class,()->service.importFile(new MockMultipartFile("file","设备.xlsx","application/octet-stream",new byte[]{1})));
            assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM equipment",Integer.class));
            assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM equipment WHERE equipment_code='EQ-new'",Integer.class));
            verifyNoInteractions(cache);
        } finally {StpUtil.stpLogic=previous;}
    }
    private EquipmentRow row(String code){EquipmentRow row=new EquipmentRow();row.setRowNumber(2);row.setEquipmentCode(code);row.setName("示波器");row.setLabId(1L);return row;}
}
