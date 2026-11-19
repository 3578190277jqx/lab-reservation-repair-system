package com.lab.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.time.LocalDateTime;

/** 爽约黑名单实体，公共字段继承自 BaseEntity。 */
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@TableName("blacklist")
@Schema(description = "爽约黑名单")
public class Blacklist extends BaseEntity {

    private static final long serialVersionUID = 1L;

    @TableField("student_id")
    @Schema(description = "被限制预约的学生ID")
    private Long studentId;

    @TableField("reason")
    @Schema(description = "加入黑名单的原因")
    private String reason;

    @TableField("no_show_count")
    @Schema(description = "加入黑名单时的累计爽约次数快照")
    private Integer noShowCount;

    @TableField("start_time")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "封禁开始时间")
    private LocalDateTime startTime;

    @TableField("expire_time")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "封禁到期时间，业务层设置为开始时间加30天")
    private LocalDateTime expireTime;

    @TableField("status")
    @Schema(description = "状态：0已解除，1启用；启用时仍须校验封禁时间范围")
    private Integer status;
}
