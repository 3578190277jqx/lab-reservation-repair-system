package com.lab.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lab.dto.ApplyRequest;
import com.lab.dto.AuditRequest;
import com.lab.dto.CancelReservationRequest;
import com.lab.dto.ReservationIdRequest;
import com.lab.dto.ReservationPageQuery;
import com.lab.entity.Reservation;

import java.util.List;

/** 预约业务入口。业务变更应调用以下方法，不应直接使用继承的通用CRUD绕过状态检查。 */
public interface ReservationService extends IService<Reservation> {

    /** 检查黑名单、设备和时间冲突后，创建当前学生的待审核预约。 */
    Reservation detail(Long id);

    Reservation apply(ApplyRequest request);

    /** 仅查询当前学生的预约，忽略客户端提供的studentId。 */
    Page<Reservation> my(ReservationPageQuery query);

    /** 管理员按条件分页查询预约。 */
    Page<Reservation> pageReservations(ReservationPageQuery query);

    /** 待审核预约通过或拒绝；通过前再次检查冲突。 */
    Reservation audit(AuditRequest request);

    /** 当前学生取消本人尚未开始、尚未签到且允许取消的预约。 */
    Reservation cancel(CancelReservationRequest request);

    /** 当前学生在签到窗口内签到本人已通过的预约，并生成使用记录。 */
    Reservation signIn(ReservationIdRequest request);

    /** 到开始时间仍待审的候选；任务扫描存在可恢复的短暂延迟。 */
    List<Long> findDuePendingExpiryIds(Long afterId, int limit);

    /** 将锁内重查后仍到期待审的预约标记为系统审核超时，不视为爽约。 */
    void processPendingExpiry(Long reservationId);

    /** 定时任务入口：幂等检查单条预约是否爽约，必要时累加次数和写入黑名单。 */
    void processNoShow(Long reservationId);

    /** 按ID游标查询到期未签到预约，供定时任务分批处理，避免偏移分页漏记录。 */
    List<Long> findDueNoShowIds(Long afterId, int limit);

    /** 按设备ID游标查询需要释放预约占用状态的设备。 */
    List<Long> findFinishedEquipmentIds(Long afterId, int limit);

    /** 在设备锁内确认使用已结束且没有当前占用后，释放设备的预约状态。 */
    void releaseFinishedEquipment(Long equipmentId);
}
