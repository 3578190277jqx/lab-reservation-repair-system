package com.lab.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Positive;
import javax.validation.constraints.Size;

@Data
@Schema(description = "取消预约参数")
public class CancelReservationRequest {

    @NotNull(message = "预约ID不能为空")
    @Positive(message = "预约ID必须大于0")
    @Schema(description = "当前学生的预约ID", example = "1")
    private Long reservationId;

    @Size(max = 500, message = "取消原因不能超过500个字符")
    @Schema(description = "取消原因，可不填写", example = "实验安排调整")
    private String reason;
}
