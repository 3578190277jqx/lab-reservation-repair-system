package com.lab.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
@Schema(description = "图表数据系列；数据顺序与横轴一致")
public class ChartSeriesVO {

    @Schema(description = "系列名称")
    private String name;

    @Schema(description = "图表类型，如bar或line")
    private String type;

    @Schema(description = "系列数据单位")
    private String unit;

    @Schema(description = "与横轴逐项对应的数据")
    private List<BigDecimal> data;
}
