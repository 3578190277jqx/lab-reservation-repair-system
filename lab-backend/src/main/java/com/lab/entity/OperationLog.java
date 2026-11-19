package com.lab.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/** 独立审计记录，不继承 BaseEntity，不参与业务逻辑删除，也不通过外键级联删除。 */
@Data
@TableName("operation_log")
@Schema(description = "操作审计记录，仅超级管理员可查询")
public class OperationLog {
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    private Long operatorId;
    private String operatorName;
    private String module;
    private String action;
    private String requestMethod;
    private String requestPath;
    private String targetId;
    private String ip;
    private String traceId;
    @Schema(description = "1成功，0失败")
    private Integer success;
    private String resultCode;
    private Long durationMs;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
}
