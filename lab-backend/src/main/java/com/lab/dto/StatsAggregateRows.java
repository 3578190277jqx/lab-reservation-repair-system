package com.lab.dto;

import lombok.Data;

import java.time.LocalDate;

/** Mapper 专用聚合结果，服务层转换为对外图表 VO；时间统一在数据库按秒聚合。 */
public final class StatsAggregateRows {
    private StatsAggregateRows() { }

    @Data
    public static class EquipmentCounts {
        private Long total;
        private Long available;
        private Long reserved;
        private Long repairing;
        private Long scrapped;
    }

    @Data
    public static class ReservationCounts {
        private Long total;
        private Long pending;
        private Long approved;
        private Long rejected;
        private Long cancelled;
        private Long noShow;
        private Long expired;
    }

    @Data
    public static class TrendRow {
        private LocalDate statDate;
        private Long total;
        private Long pending;
        private Long approved;
        private Long rejected;
        private Long cancelled;
        private Long noShow;
        private Long expired;
    }

    @Data
    public static class RepairCounts {
        private Long reported;
        private Long accepted;
        private Long repairing;
        private Long completed;
    }

    @Data
    public static class UsageTotals {
        private Long usedSeconds;
        private Long capacitySeconds;
    }

    @Data
    public static class EquipmentUsageRow {
        private Long equipmentId;
        private String equipmentName;
        private Long labId;
        private String labName;
        private Long usedSeconds;
        private Long capacitySeconds;
    }

    @Data
    public static class LabRankingRow {
        private Long labId;
        private String labName;
        private Long equipmentCount;
        private Long reservationCount;
        private Long usedSeconds;
        private Long capacitySeconds;
    }
}
