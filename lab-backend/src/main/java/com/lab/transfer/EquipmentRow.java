package com.lab.transfer;

import com.lab.dto.EquipmentSaveRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 一行待导入的设备，只用于校验，不接受设备状态或审计字段。 */
@Data
@EqualsAndHashCode(callSuper = true)
public class EquipmentRow extends EquipmentSaveRequest {
    private int rowNumber;
}
