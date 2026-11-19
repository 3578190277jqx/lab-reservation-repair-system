package com.lab.vo;
import com.lab.entity.Equipment;
import lombok.Data;
import lombok.EqualsAndHashCode;
import io.swagger.v3.oas.annotations.media.Schema;
/** 展示模型，只在权限过滤后的响应中补充名称，不用于数据库写入。 */
@Data @EqualsAndHashCode(callSuper = true)
public class EquipmentView extends Equipment {
    @Schema(description = "实验室名称")
    private String labName;
    @Schema(description = "实验室位置")
    private String labLocation;
}
