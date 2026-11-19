package com.lab.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.ToString;

@Data
@Schema(description = "登录结果")
public class LoginVO {

    @ToString.Exclude
    @Schema(description = "Token值")
    private String token;

    @Schema(description = "Token请求头名称", example = "Authorization")
    private String tokenName;

    @Schema(description = "Token请求头前缀", example = "Bearer")
    private String tokenPrefix;

    @Schema(description = "Token剩余有效期，单位秒", example = "604800")
    private long expiresIn;

    @Schema(description = "当前登录用户")
    private UserInfoVO user;
}
