package com.lab.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Positive;
import javax.validation.constraints.Size;

@Data
@Schema(description = "设备分页查询条件")
public class EquipmentPageQuery {

    @NotNull(message = "页码不能为空")
    @Min(value = 1, message = "页码不能小于1")
    @Schema(description = "页码", example = "1", defaultValue = "1")
    private Long current = 1L;

    @NotNull(message = "每页数量不能为空")
    @Min(value = 1, message = "每页数量不能小于1")
    @Max(value = 100, message = "每页数量不能超过100")
    @Schema(description = "每页数量，最多100条", example = "10", defaultValue = "10")
    private Long size = 10L;

    @Positive(message = "所属实验室ID必须大于0")
    @Schema(description = "按所属实验室ID筛选")
    private Long labId;

    @Size(max = 32, message = "设备资产编号查询条件不能超过32个字符")
    @Schema(description = "设备资产编号模糊查询")
    private String equipmentCode;

    @Size(max = 100, message = "设备名称查询条件不能超过100个字符")
    @Schema(description = "设备名称模糊查询")
    private String name;

    @Size(max = 100, message = "设备型号查询条件不能超过100个字符")
    @Schema(description = "设备型号模糊查询")
    private String model;

    @Min(value = 0, message = "设备状态必须在0至3之间")
    @Max(value = 3, message = "设备状态必须在0至3之间")
    @Schema(description = "设备状态：0可用，1已预约，2维修中，3已报废")
    private Integer status;
}
