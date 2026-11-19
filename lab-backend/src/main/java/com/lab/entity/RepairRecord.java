package com.lab.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 维修记录实体，公共字段继承自 BaseEntity。 */
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@TableName("repair_record")
@Schema(description = "维修记录")
public class RepairRecord extends BaseEntity {

    private static final long serialVersionUID = 1L;

    @TableField("repair_order_id")
    @Schema(description = "所属报修工单ID")
    private Long repairOrderId;

    @TableField("operator_id")
    @Schema(description = "本次操作人用户ID")
    private Long operatorId;

    @TableField("from_status")
    @Schema(description = "流转前状态：NULL首次提交，0待报修，1已受理，2维修中")
    private Integer fromStatus;

    @TableField("to_status")
    @Schema(description = "流转后状态：0待报修，1已受理，2维修中，3已完成")
    private Integer toStatus;

    @TableField("content")
    @Schema(description = "操作说明、维修过程或维修结果")
    private String content;

    @TableField("repair_cost")
    @Schema(description = "本次记录新增维修费用，单位元")
    private BigDecimal repairCost;

    @TableField("operation_time")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "实际操作时间")
    private LocalDateTime operationTime;
}
