package com.lab.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.annotation.SaCheckRole;
import cn.dev33.satoken.annotation.SaMode;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lab.common.Result;
import com.lab.audit.OperationAudit;
import com.lab.dto.ApplyRequest;
import com.lab.dto.AuditRequest;
import com.lab.dto.CancelReservationRequest;
import com.lab.dto.ReservationIdRequest;
import com.lab.dto.ReservationPageQuery;
import com.lab.entity.Reservation;
import com.lab.service.ReservationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;

/**
 * 预约接口：先由参数校验和Sa-Token检查请求，再交给服务层执行完整业务规则。
 * 客户端不能提交studentId、auditBy、signInTime等身份或审计字段。
 */
@Tag(name = "设备预约")
@RestController
@RequestMapping("/reservation")
@RequiredArgsConstructor
@SaCheckLogin
public class ReservationController {
    private final com.lab.service.ResourceViewService views;

    private final ReservationService reservationService;
    private final com.lab.service.ReservationScheduleService scheduleService;

    @Operation(summary = "查看设备单日占用时段", description = "登录用户可查询匿名占用快照，最终以申请时检查为准")
    @GetMapping("/schedule")
    public Result<com.lab.vo.ReservationScheduleVO> schedule(
            @Valid @ModelAttribute com.lab.dto.ReservationScheduleQuery query) {
        return Result.success(scheduleService.schedule(query));
    }


    @Operation(summary = "学生申请预约", description = "在分布式锁内检查黑名单、设备和时间冲突，成功后进入待审核状态")
    @SaCheckRole("student")
    @PostMapping("/apply")
    @OperationAudit(module = "reservation", action = "apply")
    public Result<Reservation> apply(@Valid @RequestBody ApplyRequest request) {
        return Result.success(reservationService.apply(request));
    }

    @Operation(summary = "学生查询我的预约", description = "始终按当前登录学生筛选，不允许查询其他学生的预约")
    @SaCheckRole("student")
    @GetMapping("/my")
    public Result<Page<com.lab.vo.ReservationView>> my(@Valid @ModelAttribute ReservationPageQuery query) {
        return Result.success(views.reservationPage(reservationService.my(query)));
    }

    @Operation(summary = "管理员分页查询预约")
    @SaCheckRole(value = {"admin", "lab_admin"}, mode = SaMode.OR)
    @GetMapping("/page")
    public Result<Page<com.lab.vo.ReservationView>> page(@Valid @ModelAttribute ReservationPageQuery query) {
        return Result.success(views.reservationPage(reservationService.pageReservations(query)));
    }

    @Operation(summary = "管理员审核预约", description = "仅待审核预约可处理；拒绝需填写意见，通过前再次检测冲突")
    @SaCheckRole(value = {"admin", "lab_admin"}, mode = SaMode.OR)
    @PostMapping("/audit")
    @OperationAudit(module = "reservation", action = "audit", targetArgument = 0, targetField = "reservationId")
    public Result<Reservation> audit(@Valid @RequestBody AuditRequest request) {
        return Result.success(reservationService.audit(request));
    }

    @Operation(summary = "学生取消本人预约", description = "仅未开始、未签到的待审核或已通过预约允许取消")
    @SaCheckRole("student")
    @PostMapping("/cancel")
    @OperationAudit(module = "reservation", action = "cancel", targetArgument = 0, targetField = "reservationId")
    public Result<Reservation> cancel(@Valid @RequestBody CancelReservationRequest request) {
        return Result.success(reservationService.cancel(request));
    }

    @Operation(summary = "学生预约签到", description = "仅本人已通过预约；从预约开始起可签到，开始30分钟后或预约结束后不可签到")
    @SaCheckRole("student")
    @PostMapping("/sign-in")
    @OperationAudit(module = "reservation", action = "sign-in", targetArgument = 0, targetField = "reservationId")
    public Result<Reservation> signIn(@Valid @RequestBody ReservationIdRequest request) {
        return Result.success(reservationService.signIn(request));
    }
    @GetMapping("/{id}")
    public Result<com.lab.vo.ReservationView> detail(@org.springframework.web.bind.annotation.PathVariable Long id) {
        if(id==null||id<=0)throw new com.lab.common.BusinessException(com.lab.common.ResultCode.BAD_REQUEST);
        return Result.success(views.reservationDetail(reservationService.detail(id)));
    }
}
