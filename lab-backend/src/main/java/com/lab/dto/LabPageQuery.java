package com.lab.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Positive;
import javax.validation.constraints.Size;

@Data
@Schema(description = "实验室分页查询条件")
public class LabPageQuery {

    @NotNull(message = "页码不能为空")
    @Min(value = 1, message = "页码不能小于1")
    @Schema(description = "页码", example = "1", defaultValue = "1")
    private Long current = 1L;

    @NotNull(message = "每页数量不能为空")
    @Min(value = 1, message = "每页数量不能小于1")
    @Max(value = 100, message = "每页数量不能超过100")
    @Schema(description = "每页数量，最多100条", example = "10", defaultValue = "10")
    private Long size = 10L;

    @Size(max = 32, message = "实验室编码查询条件不能超过32个字符")
    @Schema(description = "实验室编码模糊查询")
    private String labCode;

    @Size(max = 100, message = "实验室名称查询条件不能超过100个字符")
    @Schema(description = "实验室名称模糊查询")
    private String name;

    @Min(value = 0, message = "实验室状态只能为0或1")
    @Max(value = 1, message = "实验室状态只能为0或1")
    @Schema(description = "实验室状态：0停用，1启用")
    private Integer status;

    @Positive(message = "管理员ID必须大于0")
    @Schema(description = "按实验室管理员ID筛选")
    private Long adminId;
}
