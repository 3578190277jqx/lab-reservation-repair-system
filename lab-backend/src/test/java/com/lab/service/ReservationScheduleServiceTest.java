package com.lab.service;
import com.lab.common.BusinessException;
import com.lab.dto.ReservationScheduleQuery;
import com.lab.entity.Equipment;
import com.lab.entity.Lab;
import com.lab.mapper.*;
import com.lab.vo.*;
import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
class ReservationScheduleServiceTest {
    @Test
    void validatesEquipmentAndLabAndBoundsOutput() {
        EquipmentMapper em=mock(EquipmentMapper.class);LabMapper lm=mock(LabMapper.class);ReservationMapper rm=mock(ReservationMapper.class);
        ReservationScheduleService service=new ReservationScheduleService(em,lm,rm);
        ReservationScheduleQuery query=new ReservationScheduleQuery();query.setEquipmentId(1L);query.setDate(LocalDate.of(2026,10,9));
        assertThrows(BusinessException.class,()->service.schedule(query));
        Equipment e=new Equipment();e.setId(1L);e.setLabId(2L);e.setStatus(1);e.setName("Test");
        Lab lab=new Lab();lab.setStatus(1);
        when(em.selectById(1L)).thenReturn(e);when(lm.selectById(2L)).thenReturn(lab);
        when(rm.selectOccupiedSlots(anyLong(),any(),any())).thenReturn(Collections.emptyList());
        assertTrue(service.schedule(query).isBookable(),"已预约的设备仍能预约其他时段");
        e.setStatus(2);assertFalse(service.schedule(query).isBookable());
        e.setStatus(0);lab.setStatus(0);assertFalse(service.schedule(query).isBookable());
        lab.setStatus(1);when(rm.selectOccupiedSlots(anyLong(),any(),any())).thenReturn(Collections.nCopies(201,new ReservationSlotVO()));
        ReservationScheduleVO result=service.schedule(query);assertTrue(result.isTruncated());assertEquals(200,result.getSlots().size());
        query.setDate(LocalDate.MAX);assertThrows(BusinessException.class,()->service.schedule(query));
    }
}
