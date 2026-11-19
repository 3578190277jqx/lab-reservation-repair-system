package com.lab.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.annotation.SaCheckRole;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.lab.common.Result;
import com.lab.audit.OperationAudit;
import com.lab.dto.LoginRequest;
import com.lab.dto.RegisterRequest;
import com.lab.dto.RoleUpdateRequest;
import com.lab.dto.UserPageQuery;
import com.lab.service.UserService;
import com.lab.vo.LoginVO;
import com.lab.vo.UserInfoVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;

@Tag(name = "用户登录与权限")
@RestController
@RequestMapping("/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @Operation(summary = "登录并获取Token")
    @SecurityRequirements
    @PostMapping("/login")
    public Result<LoginVO> login(@Valid @RequestBody LoginRequest request) {
        return Result.success(userService.login(request));
    }

    @Operation(summary = "学生注册")
    @SecurityRequirements
    @PostMapping("/register")
    public Result<UserInfoVO> register(@Valid @RequestBody RegisterRequest request) {
        return Result.success(userService.register(request));
    }

    @Operation(summary = "获取当前用户信息")
    @SaCheckLogin
    @GetMapping("/info")
    public Result<UserInfoVO> info() {
        return Result.success(userService.info());
    }

    @Operation(summary = "退出当前登录")
    @SaCheckLogin
    @PostMapping("/logout")
    public Result<Void> logout() {
        userService.logout();
        return Result.success();
    }

    @Operation(summary = "分页查询用户，仅超级管理员")
    @SaCheckRole("admin")
    @GetMapping("/page")
    public Result<IPage<UserInfoVO>> page(@Valid @ModelAttribute UserPageQuery query) {
        return Result.success(userService.pageUsers(query));
    }

    @Operation(summary = "分配用户角色，仅超级管理员")
    @SaCheckRole("admin")
    @PutMapping("/role")
    @OperationAudit(module = "user", action = "change-role", targetArgument = 0, targetField = "userId")
    public Result<Void> role(@Valid @RequestBody RoleUpdateRequest request) {
        userService.updateRole(request);
        return Result.success();
    }
    @SaCheckRole("admin")
    @PutMapping("/{id}")
    @OperationAudit(module = "user", action = "update", targetArgument = 0)
    public Result<Void> update(@org.springframework.web.bind.annotation.PathVariable Long id,
            @Valid @RequestBody com.lab.dto.UserAdminUpdateRequest request) {
        if(id==null||id<=0)throw new com.lab.common.BusinessException(com.lab.common.ResultCode.BAD_REQUEST);
        userService.updateUser(id,request);return Result.success();
    }
}
