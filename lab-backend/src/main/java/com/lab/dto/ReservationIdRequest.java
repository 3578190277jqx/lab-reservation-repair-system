package com.lab.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Positive;

/** 签到请求，实际签到时间由服务端生成，不能由客户端指定。 */
@Data
@Schema(description = "预约签到参数")
public class ReservationIdRequest {

    @NotNull(message = "预约ID不能为空")
    @Positive(message = "预约ID必须大于0")
    @Schema(description = "当前学生的预约ID", example = "1")
    private Long reservationId;
}
