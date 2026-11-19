package com.lab.dto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Positive;
import java.time.LocalDate;
/** 只查询一个北京时间自然日，避免返回设备的全部预约历史。 */
@Data
@Schema(description = "设备单日占用查询")
public class ReservationScheduleQuery {
    @NotNull @Positive
    private Long equipmentId;
    @NotNull
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    @Schema(description = "北京时间日期 yyyy-MM-dd", example = "2026-10-09")
    private LocalDate date;
}
