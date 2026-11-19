package com.lab.enums;

import com.lab.common.BusinessException;
import com.lab.common.ResultCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 对应 equipment.status，数据库和接口均使用数字编码。 */
@Getter
@RequiredArgsConstructor
public enum EquipmentStatusEnum {

    AVAILABLE(0, "可用"),
    RESERVED(1, "已预约"),
    REPAIRING(2, "维修中"),
    SCRAPPED(3, "已报废");

    private final int code;
    private final String description;

    public static EquipmentStatusEnum fromCode(Integer code) {
        if (code != null) {
            for (EquipmentStatusEnum status : values()) {
                if (status.code == code) {
                    return status;
                }
            }
        }
        throw new BusinessException(ResultCode.BAD_REQUEST, "设备状态必须在0至3之间");
    }
}
