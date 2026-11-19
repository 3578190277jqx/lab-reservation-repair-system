package com.lab.common;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class BusinessException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final int code;
    private final HttpStatus httpStatus;

    public BusinessException(ResultCode resultCode) {
        this(resultCode, resultCode.getMessage());
    }

    public BusinessException(ResultCode resultCode, String message) {
        super(message);
        this.code = resultCode.getCode();
        this.httpStatus = resultCode.getHttpStatus();
    }
}
