package com.lab.enums;

import com.lab.common.BusinessException;
import com.lab.common.ResultCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 对应repair_order.status及repair_record.from_status/to_status。
 * 状态编码保持与现有数据库一致，流程为reported -> accepted -> repairing -> completed。
 */
@Getter
@RequiredArgsConstructor
public enum RepairStatusEnum {

    REPORTED(0, "已报修待受理"),
    ACCEPTED(1, "已受理"),
    REPAIRING(2, "维修中"),
    COMPLETED(3, "已完成");

    private final int code;
    private final String description;

    /** 将持久化的状态编码转换为枚举，禁止未知状态参与业务流转。 */
    public static RepairStatusEnum fromCode(Integer code) {
        if (code != null) {
            for (RepairStatusEnum status : values()) {
                if (status.code == code) {
                    return status;
                }
            }
        }
        throw new BusinessException(ResultCode.BAD_REQUEST, "工单状态必须在0至3之间");
    }
}
