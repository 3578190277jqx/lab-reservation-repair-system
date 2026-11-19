package com.lab.vo;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import java.time.LocalDate;
import java.util.List;
@Data
@Schema(description = "设备单日占用快照；提交时仍须在锁内重新检查")
public class ReservationScheduleVO {
    private Long equipmentId;
    private String equipmentName;
    private LocalDate date;
    private boolean bookable;
    private String reason;
    @Schema(description = "超过200条为true，不能根据缺失时段判断空闲")
    private boolean truncated;
    private List<ReservationSlotVO> slots;
}
