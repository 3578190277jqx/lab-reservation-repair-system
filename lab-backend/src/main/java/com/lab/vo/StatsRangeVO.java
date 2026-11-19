package com.lab.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Schema(description = "实际采用的统计时间范围")
public class StatsRangeVO {

    @JsonFormat(pattern = "yyyy-MM-dd")
    @Schema(description = "统计开始日期，包含当日")
    private LocalDate startDate;

    @JsonFormat(pattern = "yyyy-MM-dd")
    @Schema(description = "统计结束日期，包含当日")
    private LocalDate endDate;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "实际统计截止时间；当天的使用时长最多统计至当前时间")
    private LocalDateTime effectiveEndTime;
}
