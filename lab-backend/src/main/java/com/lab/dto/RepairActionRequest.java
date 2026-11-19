package com.lab.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Positive;
import javax.validation.constraints.Size;

/** 受理和开始维修仅接收业务操作参数，不允许直接指定目标状态或操作人。 */
@Data
@Schema(description = "受理工单或开始维修参数")
public class RepairActionRequest {

    @NotNull(message = "工单ID不能为空")
    @Positive(message = "工单ID必须大于0")
    @Schema(description = "工单ID", example = "1")
    private Long repairOrderId;

    @Size(max = 2000, message = "操作说明不能超过2000个字符")
    @Schema(description = "操作说明；未填写时由服务端记录本次操作的默认说明", example = "已确认故障，等待安排维修")
    private String content;
}
