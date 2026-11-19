package com.lab.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Positive;
import java.time.LocalDate;

@Data
@Schema(description = "统计查询参数；日期必须成对传入，默认最近30天，最多366天")
public class StatsQuery {

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    @Schema(description = "统计开始日期，包含当日；与结束日期同时传入", example = "2026-09-01")
    private LocalDate startDate;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    @Schema(description = "统计结束日期，包含当日，不得晚于今天", example = "2026-09-18")
    private LocalDate endDate;

    @Positive(message = "实验室ID必须大于0")
    @Schema(description = "可选实验室ID；实验室管理员始终仅统计其管理范围")
    private Long labId;

    @NotNull(message = "排名数量不能为空")
    @Min(value = 1, message = "排名数量不能小于1")
    @Max(value = 100, message = "排名数量不能超过100")
    @Schema(description = "设备使用率及实验室排名返回数量，最多100条", example = "10", defaultValue = "10")
    private Integer limit = 10;
}
