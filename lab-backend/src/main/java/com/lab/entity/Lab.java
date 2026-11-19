package com.lab.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

/** 实验室实体，公共字段继承自 BaseEntity。 */
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@TableName("lab")
@Schema(description = "实验室")
public class Lab extends BaseEntity {

    private static final long serialVersionUID = 1L;

    @TableField("lab_code")
    @Schema(description = "实验室编码，全局唯一")
    private String labCode;

    @TableField("name")
    @Schema(description = "实验室名称")
    private String name;

    @TableField("location")
    @Schema(description = "实验室位置")
    private String location;

    @TableField("admin_id")
    @Schema(description = "负责管理该实验室的用户ID")
    private Long adminId;

    @TableField("contact_phone")
    @Schema(description = "联系电话")
    private String contactPhone;

    @TableField("description")
    @Schema(description = "实验室介绍")
    private String description;

    @TableField("status")
    @Schema(description = "实验室状态：0停用，1启用")
    private Integer status;
}
