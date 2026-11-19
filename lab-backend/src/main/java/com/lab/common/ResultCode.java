package com.lab.common;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ResultCode {

    TOO_MANY_REQUESTS(429, "操作过于频繁，请稍后重试", HttpStatus.TOO_MANY_REQUESTS),
    SUCCESS(200, "操作成功", HttpStatus.OK),
    BAD_REQUEST(400, "请求参数错误", HttpStatus.BAD_REQUEST),
    UNAUTHORIZED(401, "未登录或登录已过期", HttpStatus.UNAUTHORIZED),
    FORBIDDEN(403, "没有访问权限", HttpStatus.FORBIDDEN),
    NOT_FOUND(404, "资源不存在", HttpStatus.NOT_FOUND),
    CONFLICT(409, "数据冲突", HttpStatus.CONFLICT),
    PAYLOAD_TOO_LARGE(413, "上传文件超过大小限制", HttpStatus.PAYLOAD_TOO_LARGE),
    INTERNAL_ERROR(500, "服务器内部错误", HttpStatus.INTERNAL_SERVER_ERROR),
    SERVICE_UNAVAILABLE(503, "服务暂时不可用", HttpStatus.SERVICE_UNAVAILABLE),
    RESERVATION_CONFLICT(40901, "预约时段已被占用", HttpStatus.CONFLICT),
    INVALID_STATUS_TRANSITION(40902, "不允许执行此状态流转", HttpStatus.CONFLICT),
    BLACKLIST_LIMITED(40301, "黑名单生效期间禁止预约", HttpStatus.FORBIDDEN);

    private final int code;
    private final String message;
    private final HttpStatus httpStatus;
}
