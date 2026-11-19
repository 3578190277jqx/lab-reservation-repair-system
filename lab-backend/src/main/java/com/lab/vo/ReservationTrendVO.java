package com.lab.vo;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Data
@Schema(description = "按日期排列的预约趋势")
public class ReservationTrendVO {

    @Schema(description = "统计时间范围")
    private StatsRangeVO range;

    @Schema(description = "横轴日期，格式yyyy-MM-dd")
    @Getter(onMethod_ = @JsonProperty("xAxis"))
    @Setter(onMethod_ = @JsonProperty("xAxis"))
    private List<String> xAxis;

    @Schema(description = "与日期逐项对应的预约统计系列")
    private List<ChartSeriesVO> series;
}
