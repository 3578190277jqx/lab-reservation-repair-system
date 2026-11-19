package com.lab.service;
import com.lab.entity.*;import com.lab.mapper.*;import com.lab.vo.*;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.junit.jupiter.api.Test;import java.util.*;
import static org.junit.jupiter.api.Assertions.*;import static org.mockito.Mockito.*;import static org.mockito.ArgumentMatchers.*;
class ResourceViewServiceTest {
 EquipmentMapper em=mock(EquipmentMapper.class);LabMapper lm=mock(LabMapper.class);ResourceViewService service=new ResourceViewService(em,lm);
 @Test void emptyPageNeverQueriesAllRecords(){Page<Reservation> source=new Page<>(3,10,0);assertTrue(service.reservationPage(source).getRecords().isEmpty());verifyNoInteractions(em,lm);}
 @Test void hundredRowsUseTwoQueriesAndPreservePagination(){
  Equipment e=new Equipment();e.setId(5L);e.setLabId(7L);e.setName("示波器");e.setEquipmentCode("EQ-5");Lab l=new Lab();l.setId(7L);l.setName("电子实验室");l.setLocation("B201");
  when(em.selectBatchIds(anyCollection())).thenReturn(Arrays.asList(e));when(lm.selectBatchIds(anyCollection())).thenReturn(Arrays.asList(l));
  Reservation r=new Reservation();r.setId(1L);r.setEquipmentId(5L);r.setStudentId(9L);r.setCancelReason("设备故障");Page<Reservation> source=new Page<>(2,100,250);source.setRecords(Collections.nCopies(100,r));
  Page<ReservationView> result=service.reservationPage(source);assertEquals(250,result.getTotal());assertEquals(2,result.getCurrent());assertEquals(100,result.getRecords().size());
  assertEquals("示波器",result.getRecords().get(0).getEquipmentName());assertEquals("B201",result.getRecords().get(0).getLabLocation());assertEquals("设备故障",result.getRecords().get(0).getCancelReason());assertNotSame(r,result.getRecords().get(0));
  verify(em,times(1)).selectBatchIds(Collections.singleton(5L));verify(lm,times(1)).selectBatchIds(Collections.singleton(7L));
 }
 @Test void missingRelatedDeviceKeepsHistoricalOrder(){when(em.selectBatchIds(anyCollection())).thenReturn(Collections.emptyList());RepairOrder r=new RepairOrder();r.setId(8L);r.setEquipmentId(5L);assertEquals(8L,service.repairDetail(r).getId());assertNull(service.repairDetail(r).getEquipmentName());verifyNoInteractions(lm);}
 @Test void equipmentViewDoesNotMutateCachedEquipment(){Equipment e=new Equipment();e.setId(5L);e.setLabId(7L);when(lm.selectBatchIds(anyCollection())).thenReturn(Collections.emptyList());EquipmentView v=service.equipmentDetail(e);assertNull(v.getLabName());assertEquals(Equipment.class,e.getClass());verifyNoInteractions(em);}
}
