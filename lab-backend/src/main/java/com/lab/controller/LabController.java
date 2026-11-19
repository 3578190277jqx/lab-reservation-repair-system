package com.lab.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.annotation.SaCheckRole;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lab.common.Result;
import com.lab.audit.OperationAudit;
import com.lab.dto.LabPageQuery;
import com.lab.dto.LabSaveRequest;
import com.lab.entity.Lab;
import com.lab.service.LabService;
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

@Tag(name = "实验室管理")
@RestController
@RequestMapping("/lab")
@RequiredArgsConstructor
@Validated
@SaCheckLogin
public class LabController {

    private final LabService labService;

    @Operation(summary = "分页查询实验室")
    @GetMapping("/page")
    public Result<Page<Lab>> page(@Valid @ModelAttribute LabPageQuery query) {
        return Result.success(labService.pageLabs(query));
    }

    @Operation(summary = "查询实验室详情")
    @GetMapping("/{id}")
    public Result<Lab> detail(@Parameter(description = "实验室ID")
                            @PathVariable("id") @Positive(message = "实验室ID必须大于0") Long id) {
        return Result.success(labService.getLab(id));
    }

    @Operation(summary = "新增实验室，仅超级管理员")
    @SaCheckRole("admin")
    @PostMapping
    @OperationAudit(module = "lab", action = "create")
    public Result<Lab> create(@Valid @RequestBody LabSaveRequest request) {
        return Result.success(labService.createLab(request));
    }

    @Operation(summary = "完整更新实验室，仅超级管理员")
    @SaCheckRole("admin")
    @PutMapping("/{id}")
    @OperationAudit(module = "lab", action = "update", targetArgument = 0)
    public Result<Lab> update(@Parameter(description = "实验室ID")
                             @PathVariable("id") @Positive(message = "实验室ID必须大于0") Long id,
                             @Valid @RequestBody LabSaveRequest request) {
        return Result.success(labService.updateLab(id, request));
    }

    @Operation(summary = "逻辑删除实验室，仅超级管理员")
    @SaCheckRole("admin")
    @DeleteMapping("/{id}")
    @OperationAudit(module = "lab", action = "delete", targetArgument = 0)
    public Result<Void> delete(@Parameter(description = "实验室ID")
                               @PathVariable("id") @Positive(message = "实验室ID必须大于0") Long id) {
        labService.deleteLab(id);
        return Result.success();
    }
}
