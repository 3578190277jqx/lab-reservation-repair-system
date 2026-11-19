package com.lab.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Positive;

@Data
@Schema(description = "故障工单分页查询参数")
public class RepairPageQuery {

    @NotNull(message = "页码不能为空")
    @Min(value = 1, message = "页码不能小于1")
    @Max(value = 1000000, message = "页码不能超过1000000")
    @Schema(description = "页码", example = "1", defaultValue = "1")
    private Long current = 1L;

    @NotNull(message = "每页数量不能为空")
    @Min(value = 1, message = "每页数量不能小于1")
    @Max(value = 100, message = "每页数量不能超过100")
    @Schema(description = "每页数量，最多100条", example = "10", defaultValue = "10")
    private Long size = 10L;

    @Positive(message = "设备ID必须大于0")
    @Schema(description = "按设备ID筛选")
    private Long equipmentId;

    @Positive(message = "报修人ID必须大于0")
    @Schema(description = "管理员按报修人ID筛选；我的工单接口忽略此字段，始终查询当前登录用户")
    private Long reporterId;

    @Positive(message = "实验室ID必须大于0")
    @Schema(description = "按实验室ID筛选；实验室管理员的查询始终限制在其管理的实验室内")
    private Long labId;

    @Min(value = 0, message = "工单状态必须在0至3之间")
    @Max(value = 3, message = "工单状态必须在0至3之间")
    @Schema(description = "工单状态：0已报修待受理，1已受理，2维修中，3已完成")
    private Integer status;
}
