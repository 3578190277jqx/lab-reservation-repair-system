package com.lab.service.impl;

import cn.dev33.satoken.stp.StpLogic;
import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.lab.cache.EquipmentPageCache;
import com.lab.common.BusinessException;
import com.lab.dto.EquipmentSaveRequest;
import com.lab.entity.Equipment;
import com.lab.entity.Lab;
import com.lab.entity.RepairOrder;
import com.lab.entity.Reservation;
import com.lab.mapper.EquipmentMapper;
import com.lab.mapper.LabMapper;
import com.lab.mapper.RepairOrderMapper;
import com.lab.mapper.ReservationMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 回归：设备 CRUD 不得绕过预约/工单流程，导致学生无法签到而被记爽约。 */
class EquipmentReservationGuardTest {
    private EquipmentMapper equipmentMapper;
    private ReservationMapper reservationMapper;
    private RepairOrderMapper repairMapper;
    private EquipmentPageCache cache;
    private EquipmentServiceImpl service;
    private Equipment current;
    private StpLogic previousLogic;

    @BeforeEach
    void setUp() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "guard-test");
        for (Class<?> type : new Class<?>[]{Equipment.class, Lab.class, Reservation.class, RepairOrder.class}) {
            TableInfoHelper.initTableInfo(assistant, type);
        }
        previousLogic = StpUtil.stpLogic;
        StpLogic authenticatedAdmin = mock(StpLogic.class);
        when(authenticatedAdmin.hasRole("admin")).thenReturn(true);
        StpUtil.stpLogic = authenticatedAdmin;
        equipmentMapper = mock(EquipmentMapper.class);
        LabMapper labMapper = mock(LabMapper.class);
        reservationMapper = mock(ReservationMapper.class);
        repairMapper = mock(RepairOrderMapper.class);
        cache = mock(EquipmentPageCache.class);
        service = new EquipmentServiceImpl(labMapper, reservationMapper, repairMapper, cache);
        ReflectionTestUtils.setField(service, "baseMapper", equipmentMapper);
        current = new Equipment();
        current.setId(10L);
        current.setLabId(1L);
        current.setStatus(1);
        when(equipmentMapper.selectOne(any())).thenReturn(current);
        when(labMapper.selectOne(any())).thenAnswer(invocation -> {
            Lab lab = new Lab();
            lab.setId(1L);
            lab.setStatus(1);
            return lab;
        });
    }

    @AfterEach
    void restoreAuth() {
        StpUtil.stpLogic = previousLogic;
    }

    @ParameterizedTest
    @CsvSource({"0,2,1", "1,3,1", "1,0,1", "0,0,2"})
    void cannotStopScrapClearOrRelocateEquipmentWithActiveReservation(int reservationStatus,
                                                                      int requestedStatus, long requestedLab) {
        current.setStatus(reservationStatus == 0 ? 0 : 1);
        Reservation active = new Reservation();
        active.setId(20L);
        active.setStatus(reservationStatus);
        active.setEndTime(LocalDateTime.now().plusHours(1));
        when(reservationMapper.selectOne(any())).thenReturn(active);

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.updateEquipment(10L, request(requestedStatus, requestedLab)));

        assertEquals(409, error.getCode());
        assertTrue(error.getMessage().contains("预约"));
        verify(equipmentMapper, never()).update(isNull(), any());
        verifyNoInteractions(cache);
        ArgumentCaptor<Wrapper> guard = ArgumentCaptor.forClass(Wrapper.class);
        verify(reservationMapper).selectOne(guard.capture());
        String sql = guard.getValue().getSqlSegment();
        assertTrue(sql.contains("equipment_id"));
        assertTrue(sql.contains("status IN"));
        assertTrue(sql.contains("end_time >"));
        assertTrue(sql.endsWith("LIMIT 1 FOR UPDATE"), "Must use a locking current read after acquiring equipment lock");
        LambdaQueryWrapper<?> query = (LambdaQueryWrapper<?>) guard.getValue();
        assertTrue(query.getParamNameValuePairs().containsValue(10L));
        assertTrue(query.getParamNameValuePairs().containsValue(0));
        assertTrue(query.getParamNameValuePairs().containsValue(1));
    }

    @Test
    void canEditNameAndDescriptionWithoutInterruptingAnActiveReservation() {
        EquipmentSaveRequest request = request(1, 1L);
        request.setName("Updated equipment name");
        request.setDescription("Updated operating instructions");

        assertSame(current, service.updateEquipment(10L, request));

        verifyNoInteractions(reservationMapper, repairMapper);
        verify(equipmentMapper).update(isNull(), any());
        verify(cache).evictAfterCommit();
    }

    @Test
    void canChangeStatusWhenNoUnfinishedReservationOrOpenRepairExists() {
        assertSame(current, service.updateEquipment(10L, request(2, 1L)));

        verify(reservationMapper).selectOne(any());
        verify(equipmentMapper).update(isNull(), any());
        verify(cache).evictAfterCommit();
    }

    @Test
    void stillRejectsManualStatusChangesDuringAnOpenRepair() {
        when(repairMapper.selectOne(any())).thenReturn(new RepairOrder());

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.updateEquipment(10L, request(0, 1L)));

        assertEquals(409, error.getCode());
        assertTrue(error.getMessage().contains("工单"));
        verify(equipmentMapper, never()).update(isNull(), any());
    }

    private EquipmentSaveRequest request(int status, long labId) {
        EquipmentSaveRequest request = new EquipmentSaveRequest();
        request.setLabId(labId);
        request.setEquipmentCode("TEST-10");
        request.setName("Oscilloscope");
        request.setStatus(status);
        return request;
    }
}
