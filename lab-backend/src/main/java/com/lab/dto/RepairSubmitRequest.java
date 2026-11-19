package com.lab.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Positive;
import javax.validation.constraints.Size;

/**
 * 工单提交参数。报修人、工单编号、状态及AI结果均由服务端确定，客户端不能指定。
 */
@Data
@Schema(description = "提交故障报修工单")
public class RepairSubmitRequest {

    @NotNull(message = "设备ID不能为空")
    @Positive(message = "设备ID必须大于0")
    @Schema(description = "故障设备ID", example = "1")
    private Long equipmentId;

    @NotBlank(message = "故障描述不能为空")
    @Size(max = 2000, message = "故障描述不能超过2000个字符")
    @Schema(description = "故障现象描述", example = "设备启动后屏幕无显示，电源指示灯持续闪烁")
    private String description;

    @Size(max = 512, message = "图片地址不能超过512个字符")
    @Schema(description = "本系统上传接口返回的图片地址；可不上传图片，不接受任意外部URL")
    private String imageUrl;

    @NotNull(message = "是否分析图片不能为空")
    @Schema(description = "是否请求AI图片辅助分析；仅在服务端已启用且上传了图片时执行，失败不影响报修", defaultValue = "true")
    private Boolean analyzeImage = true;
}
