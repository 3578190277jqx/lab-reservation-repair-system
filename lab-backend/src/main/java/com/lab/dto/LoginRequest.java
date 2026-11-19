package com.lab.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.ToString;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

@Data
@Schema(description = "登录请求")
public class LoginRequest {

    @NotBlank(message = "用户名不能为空")
    @Size(max = 64, message = "用户名不能超过64个字符")
    @Schema(description = "用户名", example = "admin")
    private String username;

    @NotBlank(message = "密码不能为空")
    @Size(max = 64, message = "密码不能超过64个字符")
    @ToString.Exclude
    @Schema(description = "明文密码，通过HTTPS传输", accessMode = Schema.AccessMode.WRITE_ONLY)
    private String password;
}
