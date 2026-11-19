package com.lab.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.ToString;

import javax.validation.constraints.Email;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;

@Data
@Schema(description = "学生注册请求，角色由服务端固定为student")
public class RegisterRequest {

    @NotBlank(message = "用户名不能为空")
    @Pattern(regexp = "^[a-zA-Z0-9_]{4,32}$", message = "用户名须为4至32位字母、数字或下划线")
    @Schema(description = "用户名", example = "student001")
    private String username;

    @NotBlank(message = "密码不能为空")
    @Size(min = 8, max = 64, message = "密码长度须为8至64个字符")
    @ToString.Exclude
    @Schema(description = "密码，UTF-8编码不能超过72字节", accessMode = Schema.AccessMode.WRITE_ONLY)
    private String password;

    @NotBlank(message = "真实姓名不能为空")
    @Size(max = 64, message = "真实姓名不能超过64个字符")
    @Schema(description = "真实姓名", example = "张三")
    private String realName;

    @NotBlank(message = "学号不能为空")
    @Size(max = 32, message = "学号不能超过32个字符")
    @Schema(description = "学号", example = "20260001")
    private String studentNo;

    @Pattern(regexp = "^$|^1[3-9]\\d{9}$", message = "请输入有效的11位手机号")
    @Size(max = 20, message = "手机号不能超过20个字符")
    @Schema(description = "手机号，可不填")
    private String phone;

    @NotBlank(message = "QQ邮箱不能为空")
    @Pattern(regexp = "(?i)^[A-Za-z0-9._%+\\-]+@qq\\.com$", message = "请输入QQ邮箱（@qq.com）")
    @Email(message = "邮箱格式不正确")
    @Size(max = 128, message = "邮箱不能超过128个字符")
    @Schema(description = "邮箱，可不填")
    private String email;
    @NotBlank(message = "邮箱验证码不能为空")
    @Pattern(regexp = "^[0-9]{6}$", message = "请输入6位邮箱验证码")
    @ToString.Exclude
    private String emailCode;
}
