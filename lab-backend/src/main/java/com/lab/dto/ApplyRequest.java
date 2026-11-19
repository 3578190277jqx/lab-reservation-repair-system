package com.lab.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Positive;
import javax.validation.constraints.Size;
import java.time.LocalDateTime;

/** 学生只提交设备、时间和用途；学生ID和初始状态由服务端确定。 */
@Data
@Schema(description = "预约申请参数")
public class ApplyRequest {

    @NotNull(message = "设备ID不能为空")
    @Positive(message = "设备ID必须大于0")
    @Schema(description = "预约设备ID", example = "1")
    private Long equipmentId;

    @NotNull(message = "预约开始时间不能为空")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "开始时间，包含此时间点；必须晚于当前时间", example = "2026-10-01 09:00:00")
    private LocalDateTime startTime;

    @NotNull(message = "预约结束时间不能为空")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "结束时间，不包含此时间点；必须晚于开始时间", example = "2026-10-01 11:00:00")
    private LocalDateTime endTime;

    @NotBlank(message = "预约用途不能为空")
    @Size(max = 500, message = "预约用途不能超过500个字符")
    @Schema(description = "预约用途", example = "完成电子电路课程实验")
    private String purpose;
}
