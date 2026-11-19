package com.lab.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "报修工单状态分布")
public class RepairStatusVO {

    @Schema(description = "统计时间范围")
    private StatsRangeVO range;

    @Schema(description = "工单状态图例名称")
    private List<String> legend;

    @Schema(description = "各状态的工单数量")
    private List<Item> data;

    @Data
    @Schema(description = "单个工单状态的数量")
    public static class Item {

        @Schema(description = "工单状态：0已报修待受理，1已受理，2维修中，3已完成")
        private Integer status;

        @Schema(description = "工单状态名称")
        private String name;

        @Schema(description = "工单数量")
        private Long value;
    }
}
