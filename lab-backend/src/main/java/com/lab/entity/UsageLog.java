package com.lab.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.time.LocalDateTime;

/** 设备使用日志实体，公共字段继承自 BaseEntity。 */
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@TableName("usage_log")
@Schema(description = "设备使用日志")
public class UsageLog extends BaseEntity {

    private static final long serialVersionUID = 1L;

    @TableField("reservation_id")
    @Schema(description = "关联预约ID，一次预约对应一条使用日志")
    private Long reservationId;

    @TableField("start_time")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "实际开始使用时间")
    private LocalDateTime startTime;

    @TableField("end_time")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "实际结束使用时间，未结束时为空")
    private LocalDateTime endTime;

    @TableField("remark")
    @Schema(description = "使用备注")
    private String remark;
}
