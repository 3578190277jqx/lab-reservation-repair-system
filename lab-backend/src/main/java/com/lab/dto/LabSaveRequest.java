package com.lab.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Positive;
import javax.validation.constraints.Size;

/** 新增和完整更新实验室的请求，不接收主键或逻辑删除字段。 */
@Data
@Schema(description = "实验室新增、更新参数")
public class LabSaveRequest {

    @NotBlank(message = "实验室编码不能为空")
    @Size(max = 32, message = "实验室编码不能超过32个字符")
    @Schema(description = "实验室编码，全局唯一", example = "LAB-001")
    private String labCode;

    @NotBlank(message = "实验室名称不能为空")
    @Size(max = 100, message = "实验室名称不能超过100个字符")
    @Schema(description = "实验室名称", example = "计算机实验室")
    private String name;

    @NotBlank(message = "实验室位置不能为空")
    @Size(max = 255, message = "实验室位置不能超过255个字符")
    @Schema(description = "实验室位置", example = "实验楼301")
    private String location;

    @Positive(message = "管理员ID必须大于0")
    @Schema(description = "负责管理实验室的用户ID，可为空", example = "2")
    private Long adminId;

    @Size(max = 20, message = "联系电话不能超过20个字符")
    @Schema(description = "联系电话", example = "13800000000")
    private String contactPhone;

    @Size(max = 1000, message = "实验室介绍不能超过1000个字符")
    @Schema(description = "实验室介绍")
    private String description;

    @NotNull(message = "实验室状态不能为空")
    @Min(value = 0, message = "实验室状态只能为0或1")
    @Max(value = 1, message = "实验室状态只能为0或1")
    @Schema(description = "实验室状态：0停用，1启用", example = "1", defaultValue = "1")
    private Integer status = 1;
}
