package com.lab.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Positive;
import javax.validation.constraints.Size;
import java.time.LocalDate;

/** 新增和完整更新设备的请求，不接收主键或逻辑删除字段。 */
@Data
@Schema(description = "设备新增、更新参数")
public class EquipmentSaveRequest {

    @NotNull(message = "所属实验室ID不能为空")
    @Positive(message = "所属实验室ID必须大于0")
    @Schema(description = "所属实验室ID", example = "1")
    private Long labId;

    @NotBlank(message = "设备资产编号不能为空")
    @Size(max = 32, message = "设备资产编号不能超过32个字符")
    @Schema(description = "设备资产编号，全局唯一", example = "EQ-001")
    private String equipmentCode;

    @NotBlank(message = "设备名称不能为空")
    @Size(max = 100, message = "设备名称不能超过100个字符")
    @Schema(description = "设备名称", example = "数字示波器")
    private String name;

    @Size(max = 100, message = "设备型号不能超过100个字符")
    @Schema(description = "设备型号", example = "DS-1000")
    private String model;

    @Size(max = 100, message = "设备制造商不能超过100个字符")
    @Schema(description = "设备制造商")
    private String manufacturer;

    @NotNull(message = "设备状态不能为空")
    @Min(value = 0, message = "设备状态必须在0至3之间")
    @Max(value = 3, message = "设备状态必须在0至3之间")
    @Schema(description = "设备状态：0可用，1已预约，2维修中，3已报废", example = "0", defaultValue = "0")
    private Integer status = 0;

    @JsonFormat(pattern = "yyyy-MM-dd")
    @Schema(description = "采购日期", example = "2026-09-01")
    private LocalDate purchaseDate;

    @Size(max = 512, message = "设备图片地址不能超过512个字符")
    @Schema(description = "设备图片访问地址")
    private String imageUrl;

    @Size(max = 1000, message = "设备说明不能超过1000个字符")
    @Schema(description = "设备说明及使用注意事项")
    private String description;
}
