package com.lab.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.annotation.SaCheckRole;
import cn.dev33.satoken.annotation.SaMode;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lab.common.Result;
import com.lab.audit.OperationAudit;
import com.lab.dto.RepairActionRequest;
import com.lab.dto.RepairCompleteRequest;
import com.lab.dto.RepairPageQuery;
import com.lab.dto.RepairSubmitRequest;
import com.lab.entity.RepairOrder;
import com.lab.entity.RepairRecord;
import com.lab.service.RepairOrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import javax.validation.constraints.Positive;
import java.util.List;

/**
 * 故障报修工单接口。角色注解负责接口准入，服务层进一步检查实验室管理范围和状态流转。
 * 所有身份字段由当前Token确定，客户端只提交业务参数。
 */
@Tag(name = "故障报修工单")
@RestController
@RequestMapping("/repair")
@RequiredArgsConstructor
@Validated
@SaCheckLogin
public class RepairController {
    private final com.lab.service.ResourceViewService views;

    private final RepairOrderService repairOrderService;

    @Operation(summary = "提交故障报修", description = "登录用户提交工单并进入reported状态；图片可选，AI失败不影响报修")
    @SaCheckRole(value = {"admin", "lab_admin", "student"}, mode = SaMode.OR)
    @PostMapping("/submit")
    @OperationAudit(module = "repair", action = "submit")
    public Result<RepairOrder> submit(@Valid @RequestBody RepairSubmitRequest request) {
        return Result.success(repairOrderService.submit(request));
    }

    @Operation(summary = "分页查询我的工单", description = "始终查询当前登录用户提交的工单")
    @GetMapping("/my")
    public Result<Page<com.lab.vo.RepairOrderView>> my(@Valid @ModelAttribute RepairPageQuery query) {
        return Result.success(views.repairPage(repairOrderService.my(query)));
    }

    @Operation(summary = "管理员分页查询工单", description = "超级管理员查看全部；实验室管理员仅查看其管理实验室的工单")
    @SaCheckRole(value = {"admin", "lab_admin"}, mode = SaMode.OR)
    @GetMapping("/page")
    public Result<Page<com.lab.vo.RepairOrderView>> page(@Valid @ModelAttribute RepairPageQuery query) {
        return Result.success(views.repairPage(repairOrderService.pageOrders(query)));
    }

    @Operation(summary = "受理工单", description = "仅允许reported -> accepted，成功后记录操作人及受理时间")
    @SaCheckRole(value = {"admin", "lab_admin"}, mode = SaMode.OR)
    @PostMapping("/accept")
    @OperationAudit(module = "repair", action = "accept", targetArgument = 0, targetField = "repairOrderId")
    public Result<RepairOrder> accept(@Valid @RequestBody RepairActionRequest request) {
        return Result.success(repairOrderService.accept(request));
    }

    @Operation(summary = "开始维修", description = "仅允许accepted -> repairing，成功后记录维修开始时间")
    @SaCheckRole(value = {"admin", "lab_admin"}, mode = SaMode.OR)
    @PostMapping("/start")
    @OperationAudit(module = "repair", action = "start", targetArgument = 0, targetField = "repairOrderId")
    public Result<RepairOrder> start(@Valid @RequestBody RepairActionRequest request) {
        return Result.success(repairOrderService.start(request));
    }

    @Operation(summary = "完成维修", description = "仅允许repairing -> completed，必须填写维修结果；已完成工单不可再次流转")
    @SaCheckRole(value = {"admin", "lab_admin"}, mode = SaMode.OR)
    @PostMapping("/complete")
    @OperationAudit(module = "repair", action = "complete", targetArgument = 0, targetField = "repairOrderId")
    public Result<RepairOrder> complete(@Valid @RequestBody RepairCompleteRequest request) {
        return Result.success(repairOrderService.complete(request));
    }

    @Operation(summary = "查看工单操作记录", description = "仅报修人、超级管理员及所属实验室管理员可查看")
    @GetMapping("/{id}/records")
    public Result<List<RepairRecord>> records(
            @PathVariable("id") @Positive(message = "工单ID必须大于0") Long id) {
        return Result.success(repairOrderService.records(id));
    }
    @GetMapping("/{id}")
    public Result<com.lab.vo.RepairOrderView> detail(@PathVariable @Positive Long id) { return Result.success(views.repairDetail(repairOrderService.detail(id))); }
}
