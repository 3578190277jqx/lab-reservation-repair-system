package com.lab.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.annotation.SaCheckRole;
import cn.dev33.satoken.annotation.SaMode;
import com.lab.common.Result;
import com.lab.dto.StatsQuery;
import com.lab.service.StatsService;
import com.lab.vo.EquipmentUsageVO;
import com.lab.vo.LabRankingVO;
import com.lab.vo.RepairStatusVO;
import com.lab.vo.ReservationTrendVO;
import com.lab.vo.StatsOverviewVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;

/** 管理统计接口；服务层进一步校验当前管理员的实验室管理范围。 */
@Tag(name = "统计分析")
@RestController
@RequestMapping("/stats")
@RequiredArgsConstructor
@Validated
@SaCheckLogin
@SaCheckRole(value = {"admin", "lab_admin"}, mode = SaMode.OR)
public class StatsController {

    private final StatsService statsService;

    @Operation(summary = "统计总览", description = "默认最近30天；实验室管理员仅统计其管理的实验室")
    @GetMapping("/overview")
    public Result<StatsOverviewVO> overview(@Valid @ModelAttribute StatsQuery query) {
        return Result.success(statsService.overview(query));
    }

    @Operation(summary = "设备使用情况", description = "返回横轴、图表系列及设备使用明细")
    @GetMapping("/equipment-usage")
    public Result<EquipmentUsageVO> equipmentUsage(@Valid @ModelAttribute StatsQuery query) {
        return Result.success(statsService.equipmentUsage(query));
    }

    @Operation(summary = "预约趋势", description = "返回统计范围内逐日预约数量及图表系列")
    @GetMapping("/reservation-trend")
    public Result<ReservationTrendVO> reservationTrend(@Valid @ModelAttribute StatsQuery query) {
        return Result.success(statsService.reservationTrend(query));
    }

    @Operation(summary = "报修状态分布", description = "返回统计范围内各报修工单状态的数量")
    @GetMapping("/repair-status")
    public Result<RepairStatusVO> repairStatus(@Valid @ModelAttribute StatsQuery query) {
        return Result.success(statsService.repairStatus(query));
    }

    @Operation(summary = "实验室使用排名", description = "返回实验室排名横轴、图表系列及统计明细")
    @GetMapping("/lab-ranking")
    public Result<LabRankingVO> labRanking(@Valid @ModelAttribute StatsQuery query) {
        return Result.success(statsService.labRanking(query));
    }
}
