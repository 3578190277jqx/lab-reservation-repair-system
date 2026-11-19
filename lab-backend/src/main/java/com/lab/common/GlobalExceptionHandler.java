package com.lab.common;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.exception.NotPermissionException;
import cn.dev33.satoken.exception.NotRoleException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import javax.validation.ConstraintViolationException;
import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Result<Void>> handleBusiness(BusinessException ex) {
        return ResponseEntity.status(ex.getHttpStatus())
                .body(Result.fail(ex.getCode(), ex.getMessage()));
    }

    @ExceptionHandler(NotLoginException.class)
    public ResponseEntity<Result<Void>> handleNotLogin(NotLoginException ex) {
        return failure(ResultCode.UNAUTHORIZED);
    }

    @ExceptionHandler({NotRoleException.class, NotPermissionException.class})
    public ResponseEntity<Result<Void>> handleForbidden(Exception ex) {
        return failure(ResultCode.FORBIDDEN);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Result<Void>> handleConstraint(ConstraintViolationException ex) {
        String message = ex.getConstraintViolations().stream()
                .map(item -> item.getPropertyPath() + ": " + item.getMessage())
                .sorted()
                .collect(Collectors.joining("；"));
        return ResponseEntity.badRequest()
                .body(Result.fail(ResultCode.BAD_REQUEST.getCode(), message));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Result<Void>> handleUpload(MaxUploadSizeExceededException ex) {
        return failure(ResultCode.PAYLOAD_TOO_LARGE);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Result<Void>> handleDataConflict(DataIntegrityViolationException ex) {
        log.warn("数据库约束冲突，异常类型：{}", ex.getClass().getSimpleName());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Result.fail(ResultCode.CONFLICT.getCode(), "数据重复或关联约束不满足"));
    }

    /**
     * 统一处理 Spring MVC 的参数绑定、JSON 解析、405、415 等异常，
     * 保留框架提供的 HTTP 状态码和响应头。
     */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception ex, Object body, HttpHeaders headers,
            HttpStatus status, WebRequest request) {

        String message = "请求处理失败";
        if (ex instanceof BindException) {
            String errors = ((BindException) ex).getBindingResult().getAllErrors().stream()
                    .map(error -> error.getDefaultMessage() == null
                            ? "参数校验失败" : error.getDefaultMessage())
                    .distinct()
                    .collect(Collectors.joining("；"));
            message = errors.isEmpty() ? "参数校验失败" : errors;
        } else if (ex instanceof HttpMessageNotReadableException) {
            message = "请求体缺失或 JSON 格式、字段类型错误";
        } else if (status == HttpStatus.BAD_REQUEST) {
            message = "请求参数缺失或类型错误";
        } else if (status == HttpStatus.NOT_FOUND) {
            message = "请求的资源不存在";
        } else if (status == HttpStatus.METHOD_NOT_ALLOWED) {
            message = "请求方法不受支持";
        } else if (status == HttpStatus.UNSUPPORTED_MEDIA_TYPE) {
            message = "请求内容类型不受支持";
        } else if (status.is5xxServerError()) {
            message = "服务器内部错误";
            log.error("Spring MVC 请求处理异常", ex);
        }
        return new ResponseEntity<>(Result.fail(status.value(), message), headers, status);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<Void>> handleUnexpected(Exception ex) {
        log.error("未处理的服务器异常", ex);
        return failure(ResultCode.INTERNAL_ERROR);
    }

    private ResponseEntity<Result<Void>> failure(ResultCode resultCode) {
        return ResponseEntity.status(resultCode.getHttpStatus())
                .body(Result.fail(resultCode));
    }
}
