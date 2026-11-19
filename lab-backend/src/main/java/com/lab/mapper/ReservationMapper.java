package com.lab.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lab.entity.Reservation;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lab.dto.ReservationPageQuery;
import java.time.LocalDateTime;
import java.util.List;

/** 设备预约数据访问接口。 */
@Mapper
public interface ReservationMapper extends BaseMapper<Reservation> {
    /** 与申请冲突检测保持相同边界；仅投影匿名占用信息，最多201条。 */
    List<com.lab.vo.ReservationSlotVO> selectOccupiedSlots(@Param("equipmentId") Long equipmentId,
            @Param("startTime") LocalDateTime startTime, @Param("endTime") LocalDateTime endTime);

    long countTimeConflicts(@Param("equipmentId") Long equipmentId,
                           @Param("startTime") LocalDateTime startTime,
                           @Param("endTime") LocalDateTime endTime,
                           @Param("excludeId") Long excludeId);

    Page<Reservation> selectReservationPage(Page<Reservation> page,
                                          @Param("query") ReservationPageQuery query,
                                          @Param("studentId") Long studentId,
                                          @Param("managerId") Long managerId);

    Long lockStudentRow(@Param("studentId") Long studentId);

    /** 调用方先持有实验室行锁，再以当前读确认是否仍有占用中的预约。 */
    Long selectActiveReservationIdByLab(@Param("labId") Long labId, @Param("now") LocalDateTime now);

    /** 候选仅供后台处理，必须持锁后重新读取并校验。 */
    List<Long> selectDuePendingExpiryIds(@Param("afterId") Long afterId, @Param("limit") int limit,
                                        @Param("now") LocalDateTime now);

    List<Long> selectDueNoShowIds(@Param("afterId") Long afterId, @Param("limit") int limit,
                                @Param("deadline") LocalDateTime deadline);

    List<Long> selectFinishedEquipmentIds(@Param("afterId") Long afterId, @Param("limit") int limit,
                                         @Param("now") LocalDateTime now);

    int closeFinishedUsage(@Param("equipmentId") Long equipmentId, @Param("now") LocalDateTime now);
}
