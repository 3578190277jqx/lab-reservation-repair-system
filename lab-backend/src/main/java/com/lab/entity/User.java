package com.lab.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.time.LocalDateTime;

/** 用户实体，公共字段继承自 BaseEntity。 */
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@TableName("`user`")
@Schema(description = "用户")
public class User extends BaseEntity {

    private static final long serialVersionUID = 1L;

    @TableField("username")
    @Schema(description = "登录用户名，全局唯一")
    private String username;

    @TableField("password")
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    @ToString.Exclude
    @Schema(description = "BCrypt密码哈希，不在响应中返回", accessMode = Schema.AccessMode.WRITE_ONLY)
    private String password;

    @TableField("real_name")
    @Schema(description = "真实姓名")
    private String realName;

    @TableField("student_no")
    @Schema(description = "学号，非学生用户可为空")
    private String studentNo;

    @TableField("phone")
    @Schema(description = "手机号码")
    private String phone;

    @TableField("email")
    @Schema(description = "电子邮箱")
    private String email;

    @TableField("avatar_url")
    @Schema(description = "头像访问地址")
    private String avatarUrl;

    @TableField("role")
    @Schema(description = "角色：admin超级管理员，lab_admin实验室管理员，student学生")
    private String role;

    @TableField("status")
    @Schema(description = "账号状态：0禁用，1正常")
    private Integer status;

    @TableField("last_login_time")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "最近登录时间")
    private LocalDateTime lastLoginTime;

    private Integer emailVerified;
    private Integer phoneVerified;
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private Integer authVersion;
}
