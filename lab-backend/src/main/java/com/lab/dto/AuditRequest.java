package com.lab.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Positive;
import javax.validation.constraints.Size;

/** 审核仅接收通过/拒绝指令，禁止客户端直接指定任意预约状态。 */
@Data
@Schema(description = "预约审核参数")
public class AuditRequest {

    @NotNull(message = "预约ID不能为空")
    @Positive(message = "预约ID必须大于0")
    @Schema(description = "待审核预约ID", example = "1")
    private Long reservationId;

    @NotNull(message = "审核结果不能为空")
    @Schema(description = "true表示通过，false表示拒绝", example = "true")
    private Boolean approved;

    @Size(max = 500, message = "审核意见不能超过500个字符")
    @Schema(description = "审核意见；拒绝时必须填写，由服务层校验", example = "符合实验室使用要求")
    private String remark;
}
