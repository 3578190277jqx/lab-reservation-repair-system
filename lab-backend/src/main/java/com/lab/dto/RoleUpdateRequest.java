package com.lab.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Positive;

@Data
@Schema(description = "管理员分配用户角色")
public class RoleUpdateRequest {

    @NotNull(message = "用户ID不能为空")
    @Positive(message = "用户ID必须大于0")
    @Schema(description = "目标用户ID", example = "3")
    private Long userId;

    @NotBlank(message = "角色不能为空")
    @Pattern(regexp = "^(admin|lab_admin|student)$", message = "角色只能为admin、lab_admin或student")
    @Schema(description = "新角色", allowableValues = {"admin", "lab_admin", "student"})
    private String role;
}
