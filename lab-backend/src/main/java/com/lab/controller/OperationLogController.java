package com.lab.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lab.common.Result;
import com.lab.dto.OperationLogPageQuery;
import com.lab.entity.OperationLog;
import com.lab.mapper.OperationLogMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import javax.validation.Valid;

@RestController
@RequestMapping("/operation-log")
@SaCheckRole("admin")
@RequiredArgsConstructor
@Tag(name = "操作审计")
public class OperationLogController {
    private final OperationLogMapper logs;

    @GetMapping("/page")
    @Operation(summary = "分页查询操作审计，仅超级管理员")
    public Result<Page<OperationLog>> page(@Valid @ModelAttribute OperationLogPageQuery query) {
        LambdaQueryWrapper<OperationLog> wrapper = new LambdaQueryWrapper<OperationLog>()
                .eq(query.getModule() != null, OperationLog::getModule, query.getModule())
                .eq(query.getSuccess() != null, OperationLog::getSuccess, query.getSuccess())
                .orderByDesc(OperationLog::getId);
        return Result.success(logs.selectPage(new Page<>(query.getCurrent(), query.getSize()), wrapper));
    }
}
