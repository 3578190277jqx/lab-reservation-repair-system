package com.lab.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Schema(description = "统计总览")
public class StatsOverviewVO {

    @Schema(description = "统计时间范围")
    private StatsRangeVO range;

    @Schema(description = "可见范围内的实验室数量")
    private Long labCount;

    @Schema(description = "可见范围内的设备总数")
    private Long equipmentCount;

    @Schema(description = "当前可用设备数量")
    private Long availableEquipmentCount;

    @Schema(description = "当前已预约设备数量")
    private Long reservedEquipmentCount;

    @Schema(description = "当前维修中设备数量")
    private Long repairingEquipmentCount;

    @Schema(description = "当前已报废设备数量")
    private Long scrappedEquipmentCount;

    @Schema(description = "统计范围内的预约数量")
    private Long reservationCount;

    @Schema(description = "统计范围内待审批的预约数量")
    private Long pendingReservationCount;

    @Schema(description = "统计范围内审核超时的预约数量")
    private Long expiredReservationCount;

    @Schema(description = "统计范围内的报修工单数量")
    private Long repairOrderCount;

    @Schema(description = "统计范围内未完成的报修工单数量")
    private Long openRepairOrderCount;

    @Schema(description = "统计范围内已完成的报修工单数量")
    private Long completedRepairOrderCount;

    @Schema(description = "统计范围内设备实际使用时长，单位小时")
    private BigDecimal usedHours;

    @Schema(description = "统计范围内设备使用率")
    private BigDecimal equipmentUsageRate;
}
