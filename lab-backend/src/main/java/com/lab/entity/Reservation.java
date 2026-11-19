package com.lab.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.time.LocalDateTime;

/** 设备预约实体，公共字段继承自 BaseEntity。 */
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@TableName("reservation")
@Schema(description = "设备预约")
public class Reservation extends BaseEntity {

    private static final long serialVersionUID = 1L;

    @TableField("student_id")
    @Schema(description = "申请学生ID")
    private Long studentId;

    @TableField("equipment_id")
    @Schema(description = "预约设备ID")
    private Long equipmentId;

    @TableField("start_time")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "预约开始时间，区间起点包含")
    private LocalDateTime startTime;

    @TableField("end_time")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "预约结束时间，区间终点不包含")
    private LocalDateTime endTime;

    @TableField("purpose")
    @Schema(description = "预约用途")
    private String purpose;

    @TableField("status")
    @Schema(description = "预约状态：0待审核，1已通过，2已拒绝，3已取消，4爽约，5审核超时")
    private Integer status;

    @TableField("apply_time")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "申请时间")
    private LocalDateTime applyTime;

    @TableField("audit_time")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "审核时间")
    private LocalDateTime auditTime;

    @TableField("audit_by")
    @Schema(description = "审核人用户ID")
    private Long auditBy;

    @TableField("audit_remark")
    @Schema(description = "审核意见、拒绝原因；审核超时时为系统关闭原因，updateTime为系统处理时间")
    private String auditRemark;

    @TableField("sign_in_time")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "实际签到时间，为空表示未签到")
    private LocalDateTime signInTime;

    @TableField("cancel_time")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "取消时间")
    private LocalDateTime cancelTime;

    @TableField("cancel_reason")
    @Schema(description = "取消原因")
    private String cancelReason;

    @TableField("no_show_time")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "标记爽约的时间")
    private LocalDateTime noShowTime;
}
