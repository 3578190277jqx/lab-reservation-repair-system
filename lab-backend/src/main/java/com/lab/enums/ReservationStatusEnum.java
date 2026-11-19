package com.lab.enums;

import com.lab.common.BusinessException;
import com.lab.common.ResultCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 对应 reservation.status；签到通过 sign_in_time 表示，不新增状态编码。 */
@Getter
@RequiredArgsConstructor
public enum ReservationStatusEnum {

    PENDING(0, "待审核"),
    APPROVED(1, "已通过"),
    REJECTED(2, "已拒绝"),
    CANCELLED(3, "已取消"),
    NO_SHOW(4, "爽约"),
    EXPIRED_PENDING(5, "审核超时");

    private final int code;
    private final String description;

    public static ReservationStatusEnum fromCode(Integer code) {
        if (code != null) {
            for (ReservationStatusEnum status : values()) {
                if (status.code == code) {
                    return status;
                }
            }
        }
        throw new BusinessException(ResultCode.BAD_REQUEST, "预约状态必须在0至5之间");
    }
}
