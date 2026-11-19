package com.lab.audit;

import com.lab.common.BusinessException;
import com.lab.common.Result;
import com.lab.common.ResultCode;
import com.lab.entity.BaseEntity;
import com.lab.entity.OperationLog;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.MDC;
import org.springframework.beans.BeanWrapperImpl;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.util.Arrays;

/** 白名单式元数据审计：不序列化入参、返回体、文件、请求头或异常消息。 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class OperationAuditAspect {
    private final OperationLogWriter writer;
    private final AuditActorResolver actors;

    @Around("@annotation(audit)")
    public Object record(ProceedingJoinPoint invocation, OperationAudit audit) throws Throwable {
        long started = System.nanoTime();
        OperationLog entry = metadata(audit, invocation.getArgs());
        try {
            Object result = invocation.proceed();
            int code = result instanceof Result ? ((Result<?>) result).getCode() : ResultCode.SUCCESS.getCode();
            entry.setResultCode(String.valueOf(code));
            entry.setSuccess(code >= 200 && code < 300 ? 1 : 0);
            if (entry.getTargetId() == null && result instanceof Result
                    && ((Result<?>) result).getData() instanceof BaseEntity) {
                Long id = ((BaseEntity) ((Result<?>) result).getData()).getId();
                if (id != null) entry.setTargetId(String.valueOf(id));
            }
            return result;
        } catch (Throwable failure) {
            entry.setSuccess(0);
            // 业务错误仅保留代码，异常消息可能包含用户输入、供应商响应和秘密，绝不持久化。
            entry.setResultCode(String.valueOf(failure instanceof BusinessException
                    ? ((BusinessException) failure).getCode() : ResultCode.INTERNAL_ERROR.getCode()));
            throw failure;
        } finally {
            entry.setDurationMs((System.nanoTime() - started) / 1_000_000L);
            try { writer.write(entry); }
            catch (RuntimeException unavailable) {
                // 不输出异常对象，以免数据库驱动在消息中回显参数或连接信息。
                log.warn("操作审计写入失败 module={} action={} traceId={}",
                        entry.getModule(), entry.getAction(), entry.getTraceId());
            }
        }
    }

    private OperationLog metadata(OperationAudit audit, Object[] arguments) {
        OperationLog entry = new OperationLog();
        entry.setModule(clean(audit.module(), 40));
        entry.setAction(clean(audit.action(), 100));
        entry.setCreateTime(LocalDateTime.now());
        entry.setTraceId(clean(MDC.get("traceId"), 64));
        try {
            AuditActor actor = actors.resolve();
            entry.setOperatorId(actor.getId());
            entry.setOperatorName(clean(actor.getName(), 64));
        } catch (RuntimeException unavailable) {
            entry.setOperatorName("unknown");
        }
        Object context = RequestContextHolder.getRequestAttributes();
        if (context instanceof ServletRequestAttributes) {
            HttpServletRequest request = ((ServletRequestAttributes) context).getRequest();
            entry.setRequestMethod(clean(request.getMethod(), 10));
            // URI 不包含 queryString；代理头可由客户端伪造，不直接信任 X-Forwarded-For。
            entry.setRequestPath(clean(request.getRequestURI(), 255));
            entry.setIp(clean(request.getRemoteAddr(), 64));
        }
        entry.setTargetId(targetId(audit, arguments));
        return entry;
    }

    private String targetId(OperationAudit audit, Object[] arguments) {
        int index = audit.targetArgument();
        if (index < 0 || index >= arguments.length || arguments[index] == null) return null;
        Object target = arguments[index];
        if (!audit.targetField().isEmpty()) {
            if (!Arrays.asList("id", "userId", "reservationId", "repairOrderId", "equipmentId").contains(audit.targetField())) return null;
            try { target = new BeanWrapperImpl(target).getPropertyValue(audit.targetField()); }
            catch (RuntimeException ignored) { return null; }
        }
        // 数值主键以外的任何字段均丢弃，防止误将 Token 等字符串写入审计。
        return target instanceof Long || target instanceof Integer ? String.valueOf(target) : null;
    }

    private String clean(String value, int limit) {
        if (value == null) return null;
        String sanitized = value.replaceAll("[\\p{Cntrl}]", "");
        return sanitized.substring(0, Math.min(limit, sanitized.length()));
    }
}
