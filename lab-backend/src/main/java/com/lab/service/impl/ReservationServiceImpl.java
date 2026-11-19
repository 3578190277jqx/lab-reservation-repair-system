package com.lab.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lab.cache.EquipmentPageCache;
import com.lab.common.BusinessException;
import com.lab.common.ResultCode;
import com.lab.dto.ApplyRequest;
import com.lab.dto.AuditRequest;
import com.lab.dto.CancelReservationRequest;
import com.lab.dto.ReservationIdRequest;
import com.lab.dto.ReservationPageQuery;
import com.lab.entity.Equipment;
import com.lab.entity.Lab;
import com.lab.entity.Reservation;
import com.lab.entity.UsageLog;
import com.lab.entity.User;
import com.lab.enums.ReservationStatusEnum;
import com.lab.mapper.EquipmentMapper;
import com.lab.mapper.LabMapper;
import com.lab.mapper.ReservationMapper;
import com.lab.mapper.UsageLogMapper;
import com.lab.service.BlacklistService;
import com.lab.service.ReservationService;
import com.lab.service.UserService;
import com.lab.utils.ReservationLockExecutor;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/**
 * 预约业务入口不能标注@Transactional：必须先拿分布式锁，再由执行器创建事务。
 * 所有预约写入口（包括定时任务）均经学生锁+设备锁，并在事务提交后释放。
 */
@Service
@RequiredArgsConstructor
public class ReservationServiceImpl extends ServiceImpl<ReservationMapper, Reservation>
        implements ReservationService {
    private final ReservationLockExecutor lockExecutor;
    private final EquipmentMapper equipmentMapper;
    private final LabMapper labMapper;
    private final UsageLogMapper usageLogMapper;
    private final BlacklistService blacklistService;
    private final UserService userService;
    private final EquipmentPageCache equipmentPageCache;
    private final Clock clock;

    @Override
    public Reservation detail(Long id) {
        Reservation value=requireReservation(id,false);
        if(StpUtil.hasRole("student"))requireOwner(value);
        else {
            Equipment e=equipmentMapper.selectById(value.getEquipmentId());
            if(e==null)throw new BusinessException(ResultCode.NOT_FOUND);
            checkManagerOf(e);
        }
        return value;
    }

    @Override
    public Reservation apply(ApplyRequest request) {
        // 1. 身份来自Token，绝不接受客户端指定studentId。
        StpUtil.checkRole("student");
        Long studentId = StpUtil.getLoginIdAsLong();
        return lockExecutor.execute(studentId, request.getEquipmentId(), () -> {
            // 2. 用户行锁进一步保护跨设备的黑名单判定，与爽约处理统一顺序。
            lockStudent(studentId);
            StpUtil.checkRole("student");
            // 3. 按实验室→设备顺序取行锁，与既有设备管理模块保持相同顺序。
            Equipment equipment = lockEquipment(request.getEquipmentId());
            // 等待行锁可能耗时，必须取得行锁后再读取当前时间，不能使用等待前的旧时间。
            LocalDateTime now = now();
            validateApplyTime(request, now);
            blacklistService.checkAllowed(studentId, now);
            checkBookable(equipment);

            // 4. 同设备分布式锁+行锁内进行SQL重叠检测，待审核/已通过都占用时段。
            checkConflict(equipment.getId(), request.getStartTime(), request.getEndTime(), null);

            // 5. 检测通过后插入待审核记录；执行器提交成功之后才释放锁。
            Reservation reservation = new Reservation();
            reservation.setStudentId(studentId);
            reservation.setEquipmentId(equipment.getId());
            reservation.setStartTime(request.getStartTime());
            reservation.setEndTime(request.getEndTime());
            reservation.setPurpose(request.getPurpose().trim());
            reservation.setStatus(ReservationStatusEnum.PENDING.getCode());
            reservation.setApplyTime(now);
            save(reservation);
            return reservation;
        });
    }

    @Override
    public Page<Reservation> my(ReservationPageQuery query) {
        StpUtil.checkRole("student");
        // 即使请求中伪造studentId，也只返回当前登录学生的数据。
        return baseMapper.selectReservationPage(new Page<>(query.getCurrent(), query.getSize()),
                query, StpUtil.getLoginIdAsLong(), null);
    }

    @Override
    public Page<Reservation> pageReservations(ReservationPageQuery query) {
        Long managerId = null;
        if (!StpUtil.hasRole("admin")) {
            StpUtil.checkRole("lab_admin");
            managerId = StpUtil.getLoginIdAsLong();
        }
        // 实验室管理员的范围由服务端注入SQL，不接受客户端扩大管理范围。
        return baseMapper.selectReservationPage(new Page<>(query.getCurrent(), query.getSize()),
                query, query.getStudentId(), managerId);
    }

    @Override
    public Reservation audit(AuditRequest request) {
        checkManagerRole();
        // 这里只读锁路由信息；进入锁和事务后必须再次读取并校验状态。
        Reservation hint = requireReservation(request.getReservationId(), false);
        return lockExecutor.execute(hint.getStudentId(), hint.getEquipmentId(), () -> {
            lockStudent(hint.getStudentId());
            Equipment equipment = lockEquipment(hint.getEquipmentId());
            Reservation current = requireReservation(hint.getId(), true);
            checkManagerOf(equipment);
            requireStatus(current, ReservationStatusEnum.PENDING);
            LocalDateTime now = now();
            boolean approve = Boolean.TRUE.equals(request.getApproved());
            if (approve) {
                // 已经过开始时间的申请不能补批，否则可能立刻变成爽约。
                if (!now.isBefore(current.getStartTime())) {
                    throw new BusinessException(ResultCode.INVALID_STATUS_TRANSITION, "预约已开始，不能再审核通过");
                }
                User student = userService.requireActiveUser(current.getStudentId());
                if (!"student".equals(student.getRole())) {
                    throw new BusinessException(ResultCode.FORBIDDEN, "申请人已不是有效学生账号");
                }
                blacklistService.checkAllowed(current.getStudentId(), now);
                checkBookable(equipment);
                // 审批再次检测，必须排除当前记录，不能与自己冲突。
                checkConflict(equipment.getId(), current.getStartTime(), current.getEndTime(), current.getId());
            } else if (!StringUtils.hasText(request.getRemark())) {
                throw new BusinessException(ResultCode.BAD_REQUEST, "拒绝预约时必须填写审核意见");
            }
            int targetStatus = approve ? ReservationStatusEnum.APPROVED.getCode()
                    : ReservationStatusEnum.REJECTED.getCode();
            int changed = baseMapper.update(null, new LambdaUpdateWrapper<Reservation>()
                    .eq(Reservation::getId, current.getId())
                    .eq(Reservation::getStatus, ReservationStatusEnum.PENDING.getCode())
                    .set(Reservation::getStatus, targetStatus)
                    .set(Reservation::getAuditBy, StpUtil.getLoginIdAsLong())
                    .set(Reservation::getAuditTime, now)
                    .set(Reservation::getAuditRemark, trimToNull(request.getRemark()))
                    .set(Reservation::getUpdateTime, now));
            ensureChanged(changed);
            refreshEquipmentStatus(equipment, now);
            return requireReservation(current.getId(), true);
        });
    }

    @Override
    public Reservation cancel(CancelReservationRequest request) {
        StpUtil.checkRole("student");
        Reservation hint = requireReservation(request.getReservationId(), false);
        requireOwner(hint);
        return lockExecutor.execute(hint.getStudentId(), hint.getEquipmentId(), () -> {
            lockStudent(hint.getStudentId());
            Equipment equipment = lockEquipment(hint.getEquipmentId());
            Reservation current = requireReservation(hint.getId(), true);
            requireOwner(current);
            if (!(isStatus(current, ReservationStatusEnum.PENDING)
                    || isStatus(current, ReservationStatusEnum.APPROVED))) {
                throw new BusinessException(ResultCode.INVALID_STATUS_TRANSITION, "仅待审核或已通过的预约允许取消");
            }
            LocalDateTime now = now();
            if (current.getSignInTime() != null || !now.isBefore(current.getStartTime())) {
                throw new BusinessException(ResultCode.INVALID_STATUS_TRANSITION, "预约开始后或签到后不能取消");
            }
            int changed = baseMapper.update(null, new LambdaUpdateWrapper<Reservation>()
                    .eq(Reservation::getId, current.getId())
                    .eq(Reservation::getStatus, current.getStatus()).isNull(Reservation::getSignInTime)
                    .set(Reservation::getStatus, ReservationStatusEnum.CANCELLED.getCode())
                    .set(Reservation::getCancelTime, now)
                    .set(Reservation::getCancelReason, trimToNull(request.getReason()))
                    .set(Reservation::getUpdateTime, now));
            ensureChanged(changed);
            refreshEquipmentStatus(equipment, now);
            return requireReservation(current.getId(), true);
        });
    }

    @Override
    public Reservation signIn(ReservationIdRequest request) {
        StpUtil.checkRole("student");
        Reservation hint = requireReservation(request.getReservationId(), false);
        requireOwner(hint);
        return lockExecutor.execute(hint.getStudentId(), hint.getEquipmentId(), () -> {
            lockStudent(hint.getStudentId());
            Equipment equipment = lockEquipment(hint.getEquipmentId());
            Reservation current = requireReservation(hint.getId(), true);
            requireOwner(current);
            requireStatus(current, ReservationStatusEnum.APPROVED);
            // 重复签到幂等，不改首次签到时间，不重复插入usage_log。
            if (current.getSignInTime() != null) {
                return current;
            }
            LocalDateTime now = now();
            LocalDateTime deadline = current.getStartTime().plusMinutes(30);
            if (now.isBefore(current.getStartTime()) || !now.isBefore(deadline)
                    || !now.isBefore(current.getEndTime())) {
                throw new BusinessException(ResultCode.INVALID_STATUS_TRANSITION,
                        "请在预约开始后30分钟内、且预约结束前签到");
            }
            checkBookable(equipment);
            int changed = baseMapper.update(null, new LambdaUpdateWrapper<Reservation>()
                    .eq(Reservation::getId, current.getId())
                    .eq(Reservation::getStatus, ReservationStatusEnum.APPROVED.getCode())
                    .isNull(Reservation::getSignInTime)
                    .set(Reservation::getSignInTime, now).set(Reservation::getUpdateTime, now));
            ensureChanged(changed);
            UsageLog usage = new UsageLog();
            usage.setReservationId(current.getId());
            usage.setStartTime(now);
            usage.setRemark("预约签到，到期任务按预约结束时间关闭记录");
            // reservation_id唯一索引是额外防线；与签到更新处于同一个数据库事务。
            usageLogMapper.insert(usage);
            return requireReservation(current.getId(), true);
        });
    }

    @Override
    public List<Long> findDuePendingExpiryIds(Long afterId, int limit) {
        return baseMapper.selectDuePendingExpiryIds(afterId, Math.min(Math.max(limit, 1), 100), now());
    }

    @Override
    public void processPendingExpiry(Long reservationId) {
        Reservation hint = getById(reservationId);
        if (hint == null) return;
        lockExecutor.execute(hint.getStudentId(), hint.getEquipmentId(), () -> {
            lockStudent(hint.getStudentId());
            lockEquipment(hint.getEquipmentId());
            Reservation current = requireReservation(hint.getId(), true);
            // 等锁可能跨越开始时间，必须在所有行锁取得后重新读取时钟。
            LocalDateTime now = now();
            if (!isStatus(current, ReservationStatusEnum.PENDING) || now.isBefore(current.getStartTime())) return null;
            baseMapper.update(null, new LambdaUpdateWrapper<Reservation>()
                    .eq(Reservation::getId, current.getId())
                    .eq(Reservation::getStatus, ReservationStatusEnum.PENDING.getCode())
                    .le(Reservation::getStartTime, now)
                    .set(Reservation::getStatus, ReservationStatusEnum.EXPIRED_PENDING.getCode())
                    // 系统关闭不代表任何管理员执行了审核。
                    .set(Reservation::getAuditBy, null)
                    .set(Reservation::getAuditTime, null)
                    .set(Reservation::getAuditRemark, "预约开始仍未审核，系统自动关闭")
                    .set(Reservation::getUpdateTime, now));
            // 待审核不改变设备状态；本次只释放预约排期，无需写设备或失效设备缓存。
            return null;
        });
    }

    @Override
    public List<Long> findDueNoShowIds(Long afterId, int limit) {
        return baseMapper.selectDueNoShowIds(afterId, Math.min(Math.max(limit, 1), 100), now().minusMinutes(30));
    }

    @Override
    public void processNoShow(Long reservationId) {
        // 后台任务没有登录上下文。候选查询只是提示，持锁后必须再次判断。
        Reservation hint = getById(reservationId);
        if (hint == null) {
            return;
        }
        lockExecutor.execute(hint.getStudentId(), hint.getEquipmentId(), () -> {
            lockStudent(hint.getStudentId());
            Equipment equipment = lockEquipment(hint.getEquipmentId());
            Reservation current = requireReservation(hint.getId(), true);
            LocalDateTime now = now();
            if (!isStatus(current, ReservationStatusEnum.APPROVED) || current.getSignInTime() != null
                    || now.isBefore(current.getStartTime().plusMinutes(30))) {
                return null;
            }
            // CAS条件与签到互斥：一次预约只允许首次成功转入爽约时计数。
            int changed = baseMapper.update(null, new LambdaUpdateWrapper<Reservation>()
                    .eq(Reservation::getId, current.getId())
                    .eq(Reservation::getStatus, ReservationStatusEnum.APPROVED.getCode())
                    .isNull(Reservation::getSignInTime)
                    .le(Reservation::getStartTime, now.minusMinutes(30))
                    .set(Reservation::getStatus, ReservationStatusEnum.NO_SHOW.getCode())
                    .set(Reservation::getNoShowTime, now).set(Reservation::getUpdateTime, now));
            if (changed == 1) {
                // DB里状态变更、累计次数判定、黑名单记录原子提交。
                blacklistService.onNoShow(current.getStudentId(), now);
                refreshEquipmentStatus(equipment, now);
            }
            return null;
        });
    }

    @Override
    public List<Long> findFinishedEquipmentIds(Long afterId, int limit) {
        return baseMapper.selectFinishedEquipmentIds(afterId, Math.min(Math.max(limit, 1), 100), now());
    }

    @Override
    public void releaseFinishedEquipment(Long equipmentId) {
        // 本任务不修改预约和黑名单，只需设备锁；不会反向获取学生锁。
        lockExecutor.execute(null, equipmentId, () -> {
            if (equipmentMapper.selectById(equipmentId) == null) {
                return null;
            }
            Equipment equipment = lockEquipment(equipmentId);
            LocalDateTime now = now();
            baseMapper.closeFinishedUsage(equipmentId, now);
            refreshEquipmentStatus(equipment, now);
            return null;
        });
    }

    private void validateApplyTime(ApplyRequest request, LocalDateTime now) {
        if (request.getStartTime() == null || request.getEndTime() == null
                || !request.getStartTime().isBefore(request.getEndTime())
                || !request.getStartTime().isAfter(now)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "开始时间必须晚于当前时间，结束时间必须晚于开始时间");
        }
    }

    private void checkConflict(Long equipmentId, LocalDateTime start, LocalDateTime end, Long excludeId) {
        if (baseMapper.countTimeConflicts(equipmentId, start, end, excludeId) > 0) {
            throw new BusinessException(ResultCode.RESERVATION_CONFLICT);
        }
    }

    private void lockStudent(Long studentId) {
        if (baseMapper.lockStudentRow(studentId) == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "预约学生不存在");
        }
    }

    private Equipment lockEquipment(Long equipmentId) {
        Equipment hint = equipmentMapper.selectById(equipmentId);
        if (hint == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "设备不存在");
        }
        Lab lab = labMapper.selectOne(new LambdaQueryWrapper<Lab>()
                .eq(Lab::getId, hint.getLabId()).last("FOR UPDATE"));
        if (lab == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "实验室不存在");
        }
        Equipment current = equipmentMapper.selectOne(new LambdaQueryWrapper<Equipment>()
                .eq(Equipment::getId, equipmentId).last("FOR UPDATE"));
        if (current == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "设备不存在");
        }
        if (!Objects.equals(current.getLabId(), lab.getId())) {
            throw new BusinessException(ResultCode.CONFLICT, "设备所属实验室已变化，请重试");
        }
        return current;
    }

    private void checkBookable(Equipment equipment) {
        Lab lab = labMapper.selectById(equipment.getLabId());
        if (lab == null || !Integer.valueOf(1).equals(lab.getStatus())) {
            throw new BusinessException(ResultCode.CONFLICT, "实验室未启用，不能预约或签到");
        }
        // status=1代表已有未结束的已通过预约，不代表全部时段都被占用。
        if (!(Integer.valueOf(0).equals(equipment.getStatus()) || Integer.valueOf(1).equals(equipment.getStatus()))) {
            throw new BusinessException(ResultCode.CONFLICT, "设备正在维修或已报废，不能预约或签到");
        }
    }

    private Reservation requireReservation(Long id, boolean lock) {
        LambdaQueryWrapper<Reservation> query = new LambdaQueryWrapper<Reservation>().eq(Reservation::getId, id);
        if (lock) {
            query.last("FOR UPDATE");
        }
        Reservation reservation = baseMapper.selectOne(query);
        if (reservation == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "预约不存在");
        }
        return reservation;
    }

    private void requireOwner(Reservation reservation) {
        StpUtil.checkRole("student");
        if (!Objects.equals(reservation.getStudentId(), StpUtil.getLoginIdAsLong())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "只能操作本人的预约");
        }
    }

    private void checkManagerRole() {
        if (!StpUtil.hasRole("admin")) {
            StpUtil.checkRole("lab_admin");
        }
    }

    private void checkManagerOf(Equipment equipment) {
        if (StpUtil.hasRole("admin")) {
            return;
        }
        StpUtil.checkRole("lab_admin");
        Lab lab = labMapper.selectById(equipment.getLabId());
        if (lab == null || !Objects.equals(lab.getAdminId(), StpUtil.getLoginIdAsLong())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "只能审核本人负责实验室的预约");
        }
    }

    private void refreshEquipmentStatus(Equipment equipment, LocalDateTime now) {
        // 禁止把维修中/已报废设备覆盖为可用。
        if (!(Integer.valueOf(0).equals(equipment.getStatus()) || Integer.valueOf(1).equals(equipment.getStatus()))) {
            return;
        }
        long active = baseMapper.selectCount(new LambdaQueryWrapper<Reservation>()
                .eq(Reservation::getEquipmentId, equipment.getId())
                .eq(Reservation::getStatus, ReservationStatusEnum.APPROVED.getCode())
                .gt(Reservation::getEndTime, now));
        int status = active > 0 ? 1 : 0;
        if (!Integer.valueOf(status).equals(equipment.getStatus())) {
            equipmentMapper.update(null, new LambdaUpdateWrapper<Equipment>()
                    .eq(Equipment::getId, equipment.getId()).in(Equipment::getStatus, 0, 1)
                    .set(Equipment::getStatus, status).set(Equipment::getUpdateTime, now));
            equipmentPageCache.evictAfterCommit();
        }
    }

    private boolean isStatus(Reservation reservation, ReservationStatusEnum status) {
        return Integer.valueOf(status.getCode()).equals(reservation.getStatus());
    }

    private void requireStatus(Reservation reservation, ReservationStatusEnum status) {
        if (!isStatus(reservation, status)) {
            throw new BusinessException(ResultCode.INVALID_STATUS_TRANSITION);
        }
    }

    private void ensureChanged(int changed) {
        if (changed != 1) {
            throw new BusinessException(ResultCode.INVALID_STATUS_TRANSITION, "预约状态已变化，请刷新后重试");
        }
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock).withNano(0);
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
