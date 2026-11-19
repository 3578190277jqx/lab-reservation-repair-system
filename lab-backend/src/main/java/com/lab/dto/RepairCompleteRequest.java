package com.lab.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.Digits;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Positive;
import javax.validation.constraints.Size;
import java.math.BigDecimal;

/** 完成维修时必须填写结果，业务限制8位整数、2位小数，数据库DECIMAL(12,2)可容纳。 */
@Data
@Schema(description = "完成工单维修参数")
public class RepairCompleteRequest {

    @NotNull(message = "工单ID不能为空")
    @Positive(message = "工单ID必须大于0")
    @Schema(description = "工单ID", example = "1")
    private Long repairOrderId;

    @NotBlank(message = "维修结果不能为空")
    @Size(max = 2000, message = "维修结果不能超过2000个字符")
    @Schema(description = "维修过程和处理结果", example = "更换损坏电源模块，通电测试恢复正常")
    private String content;

    @NotNull(message = "维修费用不能为空")
    @DecimalMin(value = "0.00", message = "维修费用不能小于0")
    @Digits(integer = 8, fraction = 2, message = "维修费用最多8位整数、2位小数")
    @Schema(description = "本次维修费用，单位元；默认0", example = "120.00", defaultValue = "0")
    private BigDecimal repairCost = BigDecimal.ZERO;
}
