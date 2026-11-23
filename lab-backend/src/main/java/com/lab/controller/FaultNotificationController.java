package com.lab.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.annotation.SaCheckRole;
import cn.dev33.satoken.annotation.SaMode;
import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lab.common.Result;
import com.lab.service.FaultNotificationService;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/notification")
@SaCheckLogin
public class FaultNotificationController {
    private final FaultNotificationService service;
    public FaultNotificationController(FaultNotificationService service){this.service=service;}
    @GetMapping("/my") @SaCheckRole("student")
    public Result<Page<Map<String,Object>>> my(@RequestParam(defaultValue="1") long current,@RequestParam(defaultValue="20") long size){return Result.success(service.my(StpUtil.getLoginIdAsLong(),current,size));}
    @PostMapping("/{id}/confirm") @SaCheckRole("student")
    public Result<Void> confirm(@PathVariable long id){service.confirm(StpUtil.getLoginIdAsLong(),id);return Result.success();}
    @GetMapping("/contact-todos") @SaCheckRole(value={"admin","lab_admin"},mode=SaMode.OR)
    public Result<Page<Map<String,Object>>> todos(@RequestParam(defaultValue="1") long current,@RequestParam(defaultValue="20") long size){return Result.success(service.contactTodos(StpUtil.getLoginIdAsLong(),StpUtil.hasRole("admin"),current,size));}
    @PostMapping("/{id}/contacted") @SaCheckRole(value={"admin","lab_admin"},mode=SaMode.OR)
    public Result<Void> contacted(@PathVariable long id){service.contacted(StpUtil.getLoginIdAsLong(),StpUtil.hasRole("admin"),id);return Result.success();}
}
