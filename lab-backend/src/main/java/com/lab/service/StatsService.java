package com.lab.service;

import com.lab.dto.StatsQuery;
import com.lab.vo.EquipmentUsageVO;
import com.lab.vo.LabRankingVO;
import com.lab.vo.RepairStatusVO;
import com.lab.vo.ReservationTrendVO;
import com.lab.vo.StatsOverviewVO;

/** 统计业务服务；所有查询均校验日期范围并限制在当前用户的实验室管理范围内。 */
public interface StatsService {

    /** 查询设备、预约、工单和使用情况总览。 */
    StatsOverviewVO overview(StatsQuery query);

    /** 查询设备使用情况及排名。 */
    EquipmentUsageVO equipmentUsage(StatsQuery query);

    /** 查询逐日预约趋势。 */
    ReservationTrendVO reservationTrend(StatsQuery query);

    /** 查询报修工单状态分布。 */
    RepairStatusVO repairStatus(StatsQuery query);

    /** 查询实验室使用排名。 */
    LabRankingVO labRanking(StatsQuery query);
}
