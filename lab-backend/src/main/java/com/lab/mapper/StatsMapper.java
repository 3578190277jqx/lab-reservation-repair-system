package com.lab.mapper;

import com.lab.dto.StatsAggregateRows;
import com.lab.dto.StatsScope;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** 跨表只读统计，无对应实体，不继承 BaseMapper。 */
public interface StatsMapper {
    long selectLabCount(@Param("scope") StatsScope scope);
    StatsAggregateRows.EquipmentCounts selectEquipmentCounts(@Param("scope") StatsScope scope);
    StatsAggregateRows.ReservationCounts selectReservationCounts(@Param("scope") StatsScope scope);
    StatsAggregateRows.RepairCounts selectRepairCounts(@Param("scope") StatsScope scope);
    StatsAggregateRows.UsageTotals selectUsageTotals(@Param("scope") StatsScope scope);
    List<StatsAggregateRows.EquipmentUsageRow> selectEquipmentUsage(
            @Param("scope") StatsScope scope, @Param("limit") int limit);
    List<StatsAggregateRows.TrendRow> selectReservationTrend(@Param("scope") StatsScope scope);
    List<StatsAggregateRows.LabRankingRow> selectLabRanking(
            @Param("scope") StatsScope scope, @Param("limit") int limit);
}
