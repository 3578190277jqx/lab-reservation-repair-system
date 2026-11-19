package com.lab.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.lab.common.BusinessException;
import com.lab.common.ResultCode;
import com.lab.dto.StatsAggregateRows;
import com.lab.dto.StatsQuery;
import com.lab.dto.StatsScope;
import com.lab.entity.Lab;
import com.lab.mapper.LabMapper;
import com.lab.mapper.StatsMapper;
import com.lab.service.StatsService;
import com.lab.vo.ChartSeriesVO;
import com.lab.vo.EquipmentUsageVO;
import com.lab.vo.LabRankingVO;
import com.lab.vo.RepairStatusVO;
import com.lab.vo.ReservationTrendVO;
import com.lab.vo.StatsOverviewVO;
import com.lab.vo.StatsRangeVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** 统计只读：一次接口内使用同一数据库快照，不写入业务表，也不提前缓存易变化的数字。 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public class StatsServiceImpl implements StatsService {
    private final StatsMapper statsMapper;
    private final LabMapper labMapper;
    private final Clock clock;

    @Override
    public StatsOverviewVO overview(StatsQuery query) {
        StatsScope scope = resolveScope(query);
        StatsAggregateRows.EquipmentCounts equipment = statsMapper.selectEquipmentCounts(scope);
        StatsAggregateRows.ReservationCounts reservations = statsMapper.selectReservationCounts(scope);
        StatsAggregateRows.RepairCounts repairs = statsMapper.selectRepairCounts(scope);
        StatsAggregateRows.UsageTotals usage = statsMapper.selectUsageTotals(scope);
        StatsOverviewVO vo = new StatsOverviewVO();
        vo.setRange(range(scope));
        vo.setLabCount(statsMapper.selectLabCount(scope));
        vo.setEquipmentCount(equipment.getTotal());
        vo.setAvailableEquipmentCount(equipment.getAvailable());
        vo.setReservedEquipmentCount(equipment.getReserved());
        vo.setRepairingEquipmentCount(equipment.getRepairing());
        vo.setScrappedEquipmentCount(equipment.getScrapped());
        vo.setReservationCount(reservations.getTotal());
        vo.setPendingReservationCount(reservations.getPending());
        vo.setExpiredReservationCount(reservations.getExpired());
        long open = repairs.getReported() + repairs.getAccepted() + repairs.getRepairing();
        vo.setRepairOrderCount(open + repairs.getCompleted());
        vo.setOpenRepairOrderCount(open);
        vo.setCompletedRepairOrderCount(repairs.getCompleted());
        vo.setUsedHours(hours(usage.getUsedSeconds()));
        vo.setEquipmentUsageRate(rate(usage.getUsedSeconds(), usage.getCapacitySeconds()));
        return vo;
    }

    @Override
    public EquipmentUsageVO equipmentUsage(StatsQuery query) {
        StatsScope scope = resolveScope(query);
        EquipmentUsageVO vo = new EquipmentUsageVO();
        vo.setRange(range(scope));
        vo.setEquipmentIds(new ArrayList<>());
        vo.setXAxis(new ArrayList<>());
        vo.setItems(new ArrayList<>());
        ChartSeriesVO series = series("设备使用率", "bar", "%");
        vo.setSeries(Collections.singletonList(series));
        for (StatsAggregateRows.EquipmentUsageRow row : statsMapper.selectEquipmentUsage(scope, query.getLimit())) {
            EquipmentUsageVO.Item item = new EquipmentUsageVO.Item();
            item.setEquipmentId(row.getEquipmentId());
            item.setEquipmentName(row.getEquipmentName());
            item.setLabId(row.getLabId());
            item.setLabName(row.getLabName());
            item.setUsedHours(hours(row.getUsedSeconds()));
            item.setUsageRate(rate(row.getUsedSeconds(), row.getCapacitySeconds()));
            vo.getItems().add(item);
            vo.getEquipmentIds().add(item.getEquipmentId());
            // 同名设备也必须能区分，各数组的位置始终一一对应。
            vo.getXAxis().add(item.getEquipmentName() + " (#" + item.getEquipmentId() + ")");
            series.getData().add(item.getUsageRate());
        }
        return vo;
    }

    @Override
    public ReservationTrendVO reservationTrend(StatsQuery query) {
        StatsScope scope = resolveScope(query);
        Map<LocalDate, StatsAggregateRows.TrendRow> byDate = new HashMap<>();
        for (StatsAggregateRows.TrendRow row : statsMapper.selectReservationTrend(scope)) {
            byDate.put(row.getStatDate(), row);
        }
        ReservationTrendVO vo = new ReservationTrendVO();
        vo.setRange(range(scope));
        vo.setXAxis(new ArrayList<>());
        List<ChartSeriesVO> series = Arrays.asList(series("申请数", "line", "次"),
                series("待审核", "line", "次"), series("已通过", "line", "次"),
                series("已拒绝", "line", "次"), series("已取消", "line", "次"), series("爽约", "line", "次"),
                series("审核超时", "line", "次"));
        vo.setSeries(series);
        // 数据库只返回有记录的日期，服务端按日补0，确保横轴连续且每个系列长度一致。
        for (LocalDate day = scope.getFromTime().toLocalDate(); day.isBefore(scope.getToTime().toLocalDate());
                day = day.plusDays(1)) {
            vo.getXAxis().add(day.toString());
            StatsAggregateRows.TrendRow row = byDate.get(day);
            long[] values = row == null ? new long[7] : new long[]{row.getTotal(), row.getPending(),
                    row.getApproved(), row.getRejected(), row.getCancelled(), row.getNoShow(), row.getExpired()};
            for (int index = 0; index < values.length; index++) {
                series.get(index).getData().add(BigDecimal.valueOf(values[index]));
            }
        }
        return vo;
    }

    @Override
    public RepairStatusVO repairStatus(StatsQuery query) {
        StatsScope scope = resolveScope(query);
        StatsAggregateRows.RepairCounts counts = statsMapper.selectRepairCounts(scope);
        RepairStatusVO vo = new RepairStatusVO();
        vo.setRange(range(scope));
        vo.setLegend(Arrays.asList("待受理", "已受理", "维修中", "已完成"));
        vo.setData(new ArrayList<>());
        Long[] values = {counts.getReported(), counts.getAccepted(), counts.getRepairing(), counts.getCompleted()};
        for (int status = 0; status < values.length; status++) {
            RepairStatusVO.Item item = new RepairStatusVO.Item();
            item.setStatus(status);
            item.setName(vo.getLegend().get(status));
            item.setValue(values[status]);
            vo.getData().add(item);
        }
        return vo;
    }

    @Override
    public LabRankingVO labRanking(StatsQuery query) {
        StatsScope scope = resolveScope(query);
        LabRankingVO vo = new LabRankingVO();
        vo.setRange(range(scope));
        vo.setLabIds(new ArrayList<>());
        vo.setXAxis(new ArrayList<>());
        vo.setItems(new ArrayList<>());
        ChartSeriesVO series = series("预约申请数", "bar", "次");
        vo.setSeries(Collections.singletonList(series));
        for (StatsAggregateRows.LabRankingRow row : statsMapper.selectLabRanking(scope, query.getLimit())) {
            LabRankingVO.Item item = new LabRankingVO.Item();
            item.setLabId(row.getLabId());
            item.setLabName(row.getLabName());
            item.setEquipmentCount(row.getEquipmentCount());
            item.setReservationCount(row.getReservationCount());
            item.setUsedHours(hours(row.getUsedSeconds()));
            item.setUsageRate(rate(row.getUsedSeconds(), row.getCapacitySeconds()));
            vo.getItems().add(item);
            vo.getLabIds().add(item.getLabId());
            vo.getXAxis().add(item.getLabName() + " (#" + item.getLabId() + ")");
            series.getData().add(BigDecimal.valueOf(item.getReservationCount()));
        }
        return vo;
    }

    private StatsScope resolveScope(StatsQuery query) {
        StpUtil.checkLogin();
        Long managerId = null;
        if (!StpUtil.hasRole("admin")) {
            StpUtil.checkRole("lab_admin");
            managerId = StpUtil.getLoginIdAsLong();
        }
        if (query == null || query.getLimit() == null || query.getLimit() < 1 || query.getLimit() > 100) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "统计条数必须在1到100之间");
        }
        LocalDateTime now = LocalDateTime.now(clock).withNano(0);
        LocalDate start = query.getStartDate();
        LocalDate end = query.getEndDate();
        if ((start == null) != (end == null)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "开始日期和结束日期必须同时填写");
        }
        if (start == null) {
            end = now.toLocalDate();
            start = end.minusDays(29);
        }
        if (start.isAfter(end) || end.isAfter(now.toLocalDate()) || ChronoUnit.DAYS.between(start, end) >= 366
                || start.getYear() < 1000 || end.getYear() > 9998) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "日期范围最多366天，结束日期不能晚于今天，日期须在1000至9998年之间");
        }
        if (query.getLabId() != null) {
            if (query.getLabId() <= 0) {
                throw new BusinessException(ResultCode.BAD_REQUEST, "实验室ID必须大于0");
            }
            Lab lab = labMapper.selectById(query.getLabId());
            if (lab == null) {
                throw new BusinessException(ResultCode.NOT_FOUND, "实验室不存在");
            }
            if (managerId != null && !Objects.equals(managerId, lab.getAdminId())) {
                throw new BusinessException(ResultCode.FORBIDDEN, "只能查看本人负责实验室的统计");
            }
        }
        StatsScope scope = new StatsScope();
        scope.setManagerId(managerId);
        scope.setLabId(query.getLabId());
        scope.setFromTime(start.atStartOfDay());
        scope.setToTime(end.plusDays(1).atStartOfDay());
        scope.setEffectiveEndTime(scope.getToTime().isBefore(now) ? scope.getToTime() : now);
        return scope;
    }

    private StatsRangeVO range(StatsScope scope) {
        StatsRangeVO range = new StatsRangeVO();
        range.setStartDate(scope.getFromTime().toLocalDate());
        range.setEndDate(scope.getToTime().toLocalDate().minusDays(1));
        range.setEffectiveEndTime(scope.getEffectiveEndTime());
        return range;
    }

    private ChartSeriesVO series(String name, String type, String unit) {
        ChartSeriesVO series = new ChartSeriesVO();
        series.setName(name);
        series.setType(type);
        series.setUnit(unit);
        series.setData(new ArrayList<>());
        return series;
    }

    private BigDecimal hours(long seconds) {
        return BigDecimal.valueOf(seconds).divide(BigDecimal.valueOf(3600), 2, RoundingMode.HALF_UP);
    }

    private BigDecimal rate(long usedSeconds, long capacitySeconds) {
        if (capacitySeconds <= 0) {
            return BigDecimal.ZERO.setScale(2);
        }
        // 正常日志不会重叠；历史异常数据仍返回有限百分比，不出现NaN/Infinity或超过100%。
        return BigDecimal.valueOf(Math.max(0L, usedSeconds)).multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(capacitySeconds), 2, RoundingMode.HALF_UP)
                .min(BigDecimal.valueOf(100).setScale(2));
    }
}
