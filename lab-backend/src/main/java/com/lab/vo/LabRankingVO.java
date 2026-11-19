package com.lab.vo;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;

@Data
@Schema(description = "实验室使用排名图表及明细")
public class LabRankingVO {

    @Schema(description = "统计时间范围")
    private StatsRangeVO range;

    @Schema(description = "与横轴逐项对应的实验室ID")
    private List<Long> labIds;

    @Schema(description = "横轴实验室名称")
    @Getter(onMethod_ = @JsonProperty("xAxis"))
    @Setter(onMethod_ = @JsonProperty("xAxis"))
    private List<String> xAxis;

    @Schema(description = "实验室使用统计系列")
    private List<ChartSeriesVO> series;

    @Schema(description = "与横轴逐项对应的实验室统计明细")
    private List<Item> items;

    @Data
    @Schema(description = "单个实验室的使用统计")
    public static class Item {

        @Schema(description = "实验室ID")
        private Long labId;

        @Schema(description = "实验室名称")
        private String labName;

        @Schema(description = "设备数量")
        private Long equipmentCount;

        @Schema(description = "统计范围内预约数量")
        private Long reservationCount;

        @Schema(description = "实际使用时长，单位小时")
        private BigDecimal usedHours;

        @Schema(description = "实验室设备使用率")
        private BigDecimal usageRate;
    }
}
