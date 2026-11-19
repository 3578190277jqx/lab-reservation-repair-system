package com.lab.service.impl;

import cn.dev33.satoken.stp.StpLogic;
import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.lab.common.BusinessException;
import com.lab.dto.LabSaveRequest;
import com.lab.entity.Lab;
import com.lab.mapper.EquipmentMapper;
import com.lab.mapper.LabMapper;
import com.lab.mapper.ReservationMapper;
import com.lab.mapper.UserMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class LabReservationGuardTest {
    private StpLogic previousLogic;
    private LabMapper labMapper;
    private ReservationMapper reservationMapper;
    private LabServiceImpl service;
    private Lab current;

    @BeforeEach
    void setUp() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), "lab-guard-test"), Lab.class);
        previousLogic = StpUtil.stpLogic;
        StpUtil.stpLogic = mock(StpLogic.class);
        labMapper = mock(LabMapper.class);
        reservationMapper = mock(ReservationMapper.class);
        when(reservationMapper.selectActiveReservationIdByLab(anyLong(), any())).thenReturn(null);
        service = new LabServiceImpl(mock(EquipmentMapper.class), mock(UserMapper.class), reservationMapper);
        ReflectionTestUtils.setField(service, "baseMapper", labMapper);
        current = new Lab();
        current.setId(1L);
        current.setStatus(1);
        when(labMapper.selectOne(any())).thenReturn(current);
    }

    @AfterEach
    void restoreAuth() {
        StpUtil.stpLogic = previousLogic;
    }

    @Test
    void rejectsDisablingLabWithUnfinishedReservationsBeforeWritingAnything() {
        when(reservationMapper.selectActiveReservationIdByLab(eq(1L), any())).thenReturn(20L);

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.updateLab(1L, request(0)));

        assertEquals(409, error.getCode());
        assertTrue(error.getMessage().contains("预约"));
        verify(labMapper, never()).update(isNull(), any());
    }

    @Test
    void canDisableLabAfterReservationsHaveFinished() {
        assertSame(current, service.updateLab(1L, request(0)));

        verify(reservationMapper).selectActiveReservationIdByLab(eq(1L), any());
        verify(labMapper).update(isNull(), any());
    }

    @Test
    void metadataChangesRemainAvailableWhileReservationsAreActive() {
        assertSame(current, service.updateLab(1L, request(1)));

        verifyNoInteractions(reservationMapper);
        verify(labMapper).update(isNull(), any());
    }

    @Test
    void reEnablingLabIsNotBlockedByReservations() {
        current.setStatus(0);

        assertSame(current, service.updateLab(1L, request(1)));

        verifyNoInteractions(reservationMapper);
        verify(labMapper).update(isNull(), any());
    }

    private LabSaveRequest request(int status) {
        LabSaveRequest request = new LabSaveRequest();
        request.setLabCode("TEST-LAB");
        request.setName("Electronics laboratory");
        request.setLocation("Building A 101");
        request.setStatus(status);
        return request;
    }
}
