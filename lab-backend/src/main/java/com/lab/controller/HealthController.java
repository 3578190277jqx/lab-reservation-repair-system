package com.lab.controller;

import com.lab.common.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "服务状态")
@RestController
@RequestMapping("/api/health")
public class HealthController {

    @Operation(summary = "检查应用是否可以响应请求")
    @SecurityRequirements
    @GetMapping
    public Result<String> health() {
        // 此接口仅检查应用存活，不代表数据库和 Redis 已通过业务读写验证。
        return Result.success("UP");
    }
}
