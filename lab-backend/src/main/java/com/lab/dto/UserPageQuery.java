package com.lab.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;

@Data
@Schema(description = "用户分页查询条件")
public class UserPageQuery {

    @NotNull(message = "页码不能为空")
    @Min(value = 1, message = "页码不能小于1")
    @Schema(description = "页码", example = "1")
    private Long current = 1L;

    @NotNull(message = "每页数量不能为空")
    @Min(value = 1, message = "每页数量不能小于1")
    @Max(value = 100, message = "每页数量不能超过100")
    @Schema(description = "每页数量", example = "10")
    private Long size = 10L;

    @Size(max = 64, message = "用户名查询条件不能超过64个字符")
    @Schema(description = "用户名模糊查询")
    private String username;

    @Pattern(regexp = "^(admin|lab_admin|student)?$", message = "角色条件不合法")
    @Schema(description = "按角色筛选")
    private String role;

    @Min(value = 0, message = "状态只能为0或1")
    @Max(value = 1, message = "状态只能为0或1")
    @Schema(description = "账号状态：0禁用，1正常")
    private Integer status;
}
