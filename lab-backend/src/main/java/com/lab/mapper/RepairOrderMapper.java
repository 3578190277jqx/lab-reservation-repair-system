package com.lab.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lab.entity.RepairOrder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lab.dto.RepairPageQuery;
import java.time.LocalDateTime;

/** 故障报修工单数据访问接口。 */
@Mapper
public interface RepairOrderMapper extends BaseMapper<RepairOrder> {
    Page<RepairOrder> selectRepairPage(Page<RepairOrder> page, @Param("query") RepairPageQuery query,
                                      @Param("reporterId") Long reporterId, @Param("managerId") Long managerId);

    long countReadableImage(@Param("imageUrl") String imageUrl, @Param("reporterId") Long reporterId,
                            @Param("managerId") Long managerId);

    int closeInterruptedUsage(@Param("equipmentId") Long equipmentId, @Param("now") LocalDateTime now);

    int cancelAffectedReservations(@Param("equipmentId") Long equipmentId,
                                    @Param("now") LocalDateTime now, @Param("reason") String reason);
}
