package com.lab.vo;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;

@Data
@Schema(description = "设备使用情况图表及明细")
public class EquipmentUsageVO {

    @Schema(description = "统计时间范围")
    private StatsRangeVO range;

    @Schema(description = "与横轴逐项对应的设备ID")
    private List<Long> equipmentIds;

    @Schema(description = "横轴设备名称")
    @Getter(onMethod_ = @JsonProperty("xAxis"))
    @Setter(onMethod_ = @JsonProperty("xAxis"))
    private List<String> xAxis;

    @Schema(description = "设备使用统计系列")
    private List<ChartSeriesVO> series;

    @Schema(description = "与横轴逐项对应的设备统计明细")
    private List<Item> items;

    @Data
    @Schema(description = "单台设备的使用统计")
    public static class Item {

        @Schema(description = "设备ID")
        private Long equipmentId;

        @Schema(description = "设备名称")
        private String equipmentName;

        @Schema(description = "所属实验室ID")
        private Long labId;

        @Schema(description = "所属实验室名称")
        private String labName;

        @Schema(description = "实际使用时长，单位小时")
        private BigDecimal usedHours;

        @Schema(description = "设备使用率")
        private BigDecimal usageRate;
    }
}
