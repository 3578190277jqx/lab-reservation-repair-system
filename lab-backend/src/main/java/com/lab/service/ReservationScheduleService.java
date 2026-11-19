package com.lab.service;
import com.lab.common.BusinessException;
import com.lab.common.ResultCode;
import com.lab.dto.ReservationScheduleQuery;
import com.lab.entity.Equipment;
import com.lab.entity.Lab;
import com.lab.mapper.EquipmentMapper;
import com.lab.mapper.LabMapper;
import com.lab.mapper.ReservationMapper;
import com.lab.vo.ReservationScheduleVO;
import com.lab.vo.ReservationSlotVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
/** 只读排期，不占用预约锁，也不缓存可能过期的占用信息。 */
@Service
@RequiredArgsConstructor
public class ReservationScheduleService {
    private final EquipmentMapper equipmentMapper;
    private final LabMapper labMapper;
    private final ReservationMapper reservationMapper;
    public ReservationScheduleVO schedule(ReservationScheduleQuery query) {
        LocalDate date = query.getDate();
        // 保护非HTTP调用；确保下一个自然日仍在MySQL DATETIME范围内。
        if (query.getEquipmentId() == null || query.getEquipmentId() <= 0 || date == null
                || date.getYear() < 1000 || date.getYear() > 9998) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "请提供有效设备和日期");
        }
        Equipment equipment = equipmentMapper.selectById(query.getEquipmentId());
        if (equipment == null) throw new BusinessException(ResultCode.NOT_FOUND, "设备不存在");
        Lab lab = labMapper.selectById(equipment.getLabId());
        boolean enabled = lab != null && Integer.valueOf(1).equals(lab.getStatus());
        boolean usable = Integer.valueOf(0).equals(equipment.getStatus())
                || Integer.valueOf(1).equals(equipment.getStatus());
        ReservationScheduleVO result = new ReservationScheduleVO();
        result.setEquipmentId(equipment.getId());
        result.setEquipmentName(equipment.getName());
        result.setDate(date);
        result.setBookable(enabled && usable);
        result.setReason(!enabled ? "实验室未启用" : !usable ? "设备维修中或已报废" : "");
        // 取201条判断截断，限制响应大小；不返回他人的预约实体。
        List<ReservationSlotVO> slots = reservationMapper.selectOccupiedSlots(
                equipment.getId(), date.atStartOfDay(), date.plusDays(1).atStartOfDay());
        result.setTruncated(slots.size() > 200);
        result.setSlots(new ArrayList<>(slots.subList(0, Math.min(slots.size(), 200))));
        return result;
    }
}
