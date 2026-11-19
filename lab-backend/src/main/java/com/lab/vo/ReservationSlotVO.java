package com.lab.vo;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import java.time.LocalDateTime;
/** 只公开占用时段，不公开用户、用途或预约ID。 */
@Data
@Schema(description = "匿名占用时段，左闭右开")
public class ReservationSlotVO {
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime startTime;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime endTime;
    @Schema(description = "0待审核，1已通过；两者均占用时段")
    private Integer status;
}
