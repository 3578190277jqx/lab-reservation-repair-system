package com.lab.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.time.LocalDate;

/** 设备实体，公共字段继承自 BaseEntity。 */
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@TableName("equipment")
@Schema(description = "设备")
public class Equipment extends BaseEntity {

    private static final long serialVersionUID = 1L;

    @TableField("lab_id")
    @Schema(description = "所属实验室ID")
    private Long labId;

    @TableField("equipment_code")
    @Schema(description = "设备资产编号，全局唯一")
    private String equipmentCode;

    @TableField("name")
    @Schema(description = "设备名称")
    private String name;

    @TableField("model")
    @Schema(description = "设备型号")
    private String model;

    @TableField("manufacturer")
    @Schema(description = "设备制造商")
    private String manufacturer;

    @TableField("status")
    @Schema(description = "设备状态：0可用，1已预约，2维修中，3已报废")
    private Integer status;

    @TableField("purchase_date")
    @JsonFormat(pattern = "yyyy-MM-dd")
    @Schema(description = "采购日期，对应MySQL DATE类型")
    private LocalDate purchaseDate;

    @TableField("image_url")
    @Schema(description = "设备图片访问地址")
    private String imageUrl;

    @TableField("description")
    @Schema(description = "设备说明及使用注意事项")
    private String description;
}
