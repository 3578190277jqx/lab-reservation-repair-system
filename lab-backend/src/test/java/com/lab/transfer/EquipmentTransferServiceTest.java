package com.lab.transfer;

import cn.dev33.satoken.stp.StpLogic;
import cn.dev33.satoken.stp.StpUtil;
import com.lab.cache.EquipmentPageCache;
import com.lab.common.BusinessException;
import com.lab.dto.EquipmentPageQuery;
import com.lab.entity.Equipment;
import com.lab.entity.Lab;
import com.lab.mapper.EquipmentMapper;
import com.lab.mapper.LabMapper;
import org.junit.jupiter.api.*;
import org.springframework.mock.web.MockMultipartFile;
import javax.validation.Validation;
import javax.validation.ValidatorFactory;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class EquipmentTransferServiceTest {
    private StpLogic previous;
    private StpLogic auth;
    private EquipmentMapper equipment;
    private EquipmentTransferMapper transfers;
    private LabMapper labs;
    private EquipmentWorkbook workbook;
    private EquipmentPageCache cache;
    private EquipmentTransferService service;
    private ValidatorFactory validation;
    @BeforeEach void setup() {
        previous=StpUtil.stpLogic;auth=mock(StpLogic.class);StpUtil.stpLogic=auth;
        when(auth.hasRole("admin")).thenReturn(true);when(auth.getLoginIdAsLong()).thenReturn(22L);
        equipment=mock(EquipmentMapper.class);transfers=mock(EquipmentTransferMapper.class);
        labs=mock(LabMapper.class);workbook=mock(EquipmentWorkbook.class);cache=mock(EquipmentPageCache.class);
        validation=Validation.buildDefaultValidatorFactory();
        service=new EquipmentTransferService(workbook,equipment,transfers,labs,cache,validation.getValidator());
        when(transfers.findExistingCodes(anyList())).thenReturn(Collections.emptyList());
    }
    @AfterEach void reset(){StpUtil.stpLogic=previous;validation.close();}
    private EquipmentRow row(String code,long labId){EquipmentRow r=new EquipmentRow();r.setRowNumber(2);r.setEquipmentCode(code);r.setName("数字示波器");r.setLabId(labId);return r;}
    private MockMultipartFile file(){return new MockMultipartFile("file","设备.xlsx","application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",new byte[]{1});}
    private Lab lab(long id,long owner){Lab l=new Lab();l.setId(id);l.setAdminId(owner);l.setStatus(1);return l;}
    @Test void refusesOutOfScopeLabBeforeInsertingAnyRow(){
        when(auth.hasRole("admin")).thenReturn(false);
        when(workbook.read(any())).thenReturn(Arrays.asList(row("EQ-1",1),row("EQ-2",2)));
        when(labs.selectByIdForUpdate(1L)).thenReturn(lab(1,22));when(labs.selectByIdForUpdate(2L)).thenReturn(lab(2,99));
        assertEquals(403,assertThrows(BusinessException.class,()->service.importFile(file())).getCode());
        verify(equipment,never()).insert(any(Equipment.class));verifyNoInteractions(cache);
    }
    @Test void validatesEveryRowBeforeInsertingAndRejectsCaseInsensitiveDuplicates(){
        when(workbook.read(any())).thenReturn(Arrays.asList(row("EQ-1",1),row("eq-1",1)));
        assertThrows(BusinessException.class,()->service.importFile(file()));verify(equipment,never()).insert(any(Equipment.class));
        EquipmentRow invalid=row("EQ-2",1);invalid.setName("");
        when(workbook.read(any())).thenReturn(Arrays.asList(row("EQ-1",1),invalid));
        assertThrows(BusinessException.class,()->service.importFile(file()));verify(equipment,never()).insert(any(Equipment.class));
    }
    @Test void refusesAlreadyUsedOrLogicallyDeletedEquipmentCodes(){
        when(workbook.read(any())).thenReturn(Collections.singletonList(row("EQ-1",1)));
        when(labs.selectByIdForUpdate(1L)).thenReturn(lab(1,22));
        when(transfers.findExistingCodes(anyList())).thenReturn(Collections.singletonList("EQ-1"));
        assertEquals(409,assertThrows(BusinessException.class,()->service.importFile(file())).getCode());
        verify(equipment,never()).insert(any(Equipment.class));
    }
    @Test void insertsAllNewEquipmentWithAvailableStatusAndInvalidatesCacheOnce(){
        EquipmentRow first=row("EQ-1",1);first.setStatus(3);
        when(workbook.read(any())).thenReturn(Arrays.asList(first,row("EQ-2",1)));
        when(labs.selectByIdForUpdate(1L)).thenReturn(lab(1,22));
        assertEquals(2,service.importFile(file()).getImportedCount());
        verify(equipment,times(2)).insert(argThat((Equipment e)->e.getStatus()==0 && e.getLabId()==1L));
        verify(cache,times(1)).evictAfterCommit();
    }
    @Test void exportIsScopedAndRefusesExcessiveResults(){
        when(auth.hasRole("admin")).thenReturn(false);
        when(transfers.selectForExport(any(),eq(22L))).thenReturn(Collections.nCopies(2001,new Equipment()));
        assertThrows(BusinessException.class,()->service.export(new EquipmentPageQuery()));
        verify(transfers).selectForExport(any(),eq(22L));verify(workbook,never()).write(any());
    }
}
