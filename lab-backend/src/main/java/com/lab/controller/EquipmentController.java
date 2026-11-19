package com.lab.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.annotation.SaCheckRole;
import cn.dev33.satoken.annotation.SaMode;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lab.common.Result;
import com.lab.audit.OperationAudit;
import com.lab.dto.EquipmentPageQuery;
import com.lab.dto.EquipmentSaveRequest;
import com.lab.entity.Equipment;
import com.lab.service.EquipmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import javax.validation.constraints.Positive;

@Tag(name = "设备管理")
@RestController
@RequestMapping("/equipment")
@RequiredArgsConstructor
@Validated
@SaCheckLogin
public class EquipmentController {
    private final com.lab.service.ResourceViewService views;

    private final EquipmentService equipmentService;

    @Operation(summary = "分页查询设备，使用Redis缓存")
    @GetMapping("/page")
    public Result<Page<com.lab.vo.EquipmentView>> page(@Valid @ModelAttribute EquipmentPageQuery query) {
        return Result.success(views.equipmentPage(equipmentService.pageEquipment(query)));
    }

    @Operation(summary = "查询设备详情")
    @GetMapping("/{id}")
    public Result<com.lab.vo.EquipmentView> detail(@Parameter(description = "设备ID")
                                  @PathVariable("id") @Positive(message = "设备ID必须大于0") Long id) {
        return Result.success(views.equipmentDetail(equipmentService.getEquipment(id)));
    }

    @Operation(summary = "新增设备，仅超级管理员或实验室管理员")
    @SaCheckRole(value = {"admin", "lab_admin"}, mode = SaMode.OR)
    @PostMapping
    @OperationAudit(module = "equipment", action = "create")
    public Result<Equipment> create(@Valid @RequestBody EquipmentSaveRequest request) {
        return Result.success(equipmentService.createEquipment(request));
    }

    @Operation(summary = "完整更新设备，仅超级管理员或实验室管理员")
    @SaCheckRole(value = {"admin", "lab_admin"}, mode = SaMode.OR)
    @PutMapping("/{id}")
    @OperationAudit(module = "equipment", action = "update", targetArgument = 0)
    public Result<Equipment> update(@Parameter(description = "设备ID")
                                  @PathVariable("id") @Positive(message = "设备ID必须大于0") Long id,
                                  @Valid @RequestBody EquipmentSaveRequest request) {
        return Result.success(equipmentService.updateEquipment(id, request));
    }

    @Operation(summary = "逻辑删除设备，仅超级管理员或实验室管理员")
    @SaCheckRole(value = {"admin", "lab_admin"}, mode = SaMode.OR)
    @DeleteMapping("/{id}")
    @OperationAudit(module = "equipment", action = "delete", targetArgument = 0)
    public Result<Void> delete(@Parameter(description = "设备ID")
                               @PathVariable("id") @Positive(message = "设备ID必须大于0") Long id) {
        equipmentService.deleteEquipment(id);
        return Result.success();
    }
}
