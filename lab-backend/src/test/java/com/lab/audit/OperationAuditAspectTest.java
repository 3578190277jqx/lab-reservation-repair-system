package com.lab.audit;

import com.lab.common.BusinessException;
import com.lab.common.Result;
import com.lab.common.ResultCode;
import com.lab.dto.AuditRequest;
import com.lab.entity.OperationLog;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.MDC;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OperationAuditAspectTest {
    private OperationLogWriter writer;
    private Actions actions;

    @BeforeEach void init() {
        writer = mock(OperationLogWriter.class);
        AuditActorResolver actors = mock(AuditActorResolver.class);
        when(actors.resolve()).thenReturn(new AuditActor(7L, "admin"));
        AspectJProxyFactory proxy = new AspectJProxyFactory(new Actions());
        proxy.addAspect(new OperationAuditAspect(writer, actors));
        actions = proxy.getProxy();
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/reservation/audit");
        request.setRemoteAddr("127.0.0.1");
        request.addHeader("X-Forwarded-For", "forged-address");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        MDC.put("traceId", "trace-123");
    }

    @AfterEach void close() { RequestContextHolder.resetRequestAttributes(); MDC.clear(); }

    @Test void recordsOnlyMetadataAndNumericTarget() {
        AuditRequest request = new AuditRequest(); request.setReservationId(21L);
        request.setRemark("password=Secret api-key=SECRET-CODE");
        assertEquals(200, actions.approve(request).getCode());
        OperationLog log = captured();
        assertEquals(7L, log.getOperatorId());
        assertEquals("admin", log.getOperatorName());
        assertEquals("21", log.getTargetId());
        assertEquals("127.0.0.1", log.getIp());
        assertEquals("trace-123", log.getTraceId());
        assertEquals(1, log.getSuccess());
        assertEquals("200", log.getResultCode());
        assertFalse(log.toString().contains("SECRET"));
        assertNotNull(log.getCreateTime());
        assertTrue(log.getDurationMs() >= 0);
    }

    @Test void failedBusinessActionKeepsExceptionAndOnlyRecordsCode() {
        BusinessException exception = assertThrows(BusinessException.class, () -> actions.fail(9L));
        assertEquals("secret-in-exception", exception.getMessage());
        OperationLog log = captured();
        assertEquals(0, log.getSuccess());
        assertEquals(String.valueOf(ResultCode.CONFLICT.getCode()), log.getResultCode());
        assertFalse(log.toString().contains("secret-in-exception"));
    }

    @Test void failedResultIsNotMarkedSuccessful() {
        assertEquals(ResultCode.CONFLICT.getCode(), actions.reject().getCode());
        assertEquals(0, captured().getSuccess());
    }

    @Test void failedAuditWriteDoesNotOverrideSuccessfulBusinessResponse() {
        doThrow(new IllegalStateException("database secret")).when(writer).write(any());
        assertEquals(200, actions.approve(new AuditRequest()).getCode());
    }

    @Test void failedAuditWriteDoesNotOverrideBusinessException() {
        doThrow(new IllegalStateException("database secret")).when(writer).write(any());
        assertEquals("secret-in-exception", assertThrows(BusinessException.class, () -> actions.fail(9L)).getMessage());
    }

    private OperationLog captured() {
        ArgumentCaptor<OperationLog> argument = ArgumentCaptor.forClass(OperationLog.class);
        verify(writer).write(argument.capture());
        return argument.getValue();
    }

    public static class Actions {
        @OperationAudit(module = "reservation", action = "audit", targetArgument = 0, targetField = "reservationId")
        public Result<Void> approve(AuditRequest request) { return Result.success(); }
        @OperationAudit(module = "reservation", action = "cancel", targetArgument = 0)
        public Result<Void> fail(Long id) { throw new BusinessException(ResultCode.CONFLICT, "secret-in-exception"); }
        @OperationAudit(module = "equipment", action = "delete")
        public Result<Void> reject() { return Result.fail(ResultCode.CONFLICT); }
    }
}
