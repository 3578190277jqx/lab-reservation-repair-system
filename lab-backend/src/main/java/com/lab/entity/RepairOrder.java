package com.lab.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.time.LocalDateTime;

/** 故障报修工单实体，公共字段继承自 BaseEntity。 */
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@TableName("repair_order")
@Schema(description = "故障报修工单")
public class RepairOrder extends BaseEntity {

    private static final long serialVersionUID = 1L;

    @TableField("order_no")
    @Schema(description = "工单编号，由业务层生成，全局唯一")
    private String orderNo;

    @TableField("equipment_id")
    @Schema(description = "故障设备ID")
    private Long equipmentId;

    @TableField("reporter_id")
    @Schema(description = "报修人用户ID")
    private Long reporterId;

    @TableField("description")
    @Schema(description = "故障现象描述")
    private String description;

    @TableField("image_url")
    @Schema(description = "故障图片访问地址")
    private String imageUrl;

    @TableField("status")
    @Schema(description = "工单状态：0待报修（已提交待受理），1已受理，2维修中，3已完成")
    private Integer status;

    @TableField("handler_id")
    @Schema(description = "工单处理人用户ID")
    private Long handlerId;

    @TableField("ai_suggestion")
    @Schema(description = "AI图片辅助返回的故障大类提示，仅供参考")
    private String aiSuggestion;

    @TableField("accepted_time")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "工单受理时间")
    private LocalDateTime acceptedTime;

    @TableField("repair_start_time")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "开始维修时间")
    private LocalDateTime repairStartTime;

    @TableField("completed_time")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "维修完成时间")
    private LocalDateTime completedTime;
}
