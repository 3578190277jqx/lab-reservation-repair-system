package com.lab.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lab.cache.EquipmentPageCache;
import com.lab.common.BusinessException;
import com.lab.common.ResultCode;
import com.lab.dto.EquipmentPageQuery;
import com.lab.dto.EquipmentSaveRequest;
import com.lab.entity.Equipment;
import com.lab.entity.Lab;
import com.lab.entity.RepairOrder;
import com.lab.entity.Reservation;
import com.lab.enums.EquipmentStatusEnum;
import com.lab.mapper.EquipmentMapper;
import com.lab.mapper.LabMapper;
import com.lab.mapper.RepairOrderMapper;
import com.lab.mapper.ReservationMapper;
import com.lab.service.EquipmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Objects;
import java.util.TreeSet;

@Service
@RequiredArgsConstructor
public class EquipmentServiceImpl extends ServiceImpl<EquipmentMapper, Equipment>
        implements EquipmentService {

    private final LabMapper labMapper;
    private final ReservationMapper reservationMapper;
    private final RepairOrderMapper repairOrderMapper;
    private final EquipmentPageCache pageCache;

    @Override
    public Page<Equipment> pageEquipment(EquipmentPageQuery query) {
        StpUtil.checkLogin();
        // 分页读取不启动事务，避免将旧事务快照写进新一代缓存。
        return pageCache.getOrLoad(query, () -> selectPageFromDatabase(query));
    }

    private Page<Equipment> selectPageFromDatabase(EquipmentPageQuery query) {
        String code = trimToNull(query.getEquipmentCode());
        String name = trimToNull(query.getName());
        String model = trimToNull(query.getModel());
        LambdaQueryWrapper<Equipment> wrapper = new LambdaQueryWrapper<Equipment>()
                .eq(query.getLabId() != null, Equipment::getLabId, query.getLabId())
                .like(code != null, Equipment::getEquipmentCode, code)
                .like(name != null, Equipment::getName, name)
                .like(model != null, Equipment::getModel, model)
                .eq(query.getStatus() != null, Equipment::getStatus, query.getStatus())
                .orderByDesc(Equipment::getCreateTime).orderByDesc(Equipment::getId);
        return baseMapper.selectPage(new Page<>(query.getCurrent(), query.getSize()), wrapper);
    }

    @Override
    public Equipment getEquipment(Long id) {
        StpUtil.checkLogin();
        return requireEquipment(id, false);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Equipment createEquipment(EquipmentSaveRequest request) {
        lockAndAuthorizeLabs(request.getLabId());
        EquipmentStatusEnum.fromCode(request.getStatus());
        Equipment equipment = new Equipment();
        equipment.setLabId(request.getLabId());
        equipment.setEquipmentCode(request.getEquipmentCode().trim());
        equipment.setName(request.getName().trim());
        equipment.setModel(trimToNull(request.getModel()));
        equipment.setManufacturer(trimToNull(request.getManufacturer()));
        equipment.setStatus(request.getStatus());
        equipment.setPurchaseDate(request.getPurchaseDate());
        equipment.setImageUrl(trimToNull(request.getImageUrl()));
        equipment.setDescription(trimToNull(request.getDescription()));
        save(equipment);
        pageCache.evictAfterCommit();
        return equipment;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Equipment updateEquipment(Long id, EquipmentSaveRequest request) {
        Equipment previous = requireEquipment(id, false);
        // 按ID顺序先锁实验室，再锁设备，和删除实验室保持相同加锁顺序。
        lockAndAuthorizeLabs(previous.getLabId(), request.getLabId());
        Equipment current = requireEquipment(id, true);
        ensureLabUnchanged(previous, current);
        EquipmentStatusEnum.fromCode(request.getStatus());
        if (!Objects.equals(current.getLabId(), request.getLabId())
                || !Objects.equals(current.getStatus(), request.getStatus())) {
            // 当前读保证能看到等待设备行锁期间新提交的工单，防止CRUD绕过工单状态机。
            RepairOrder openOrder = repairOrderMapper.selectOne(new LambdaQueryWrapper<RepairOrder>()
                    .eq(RepairOrder::getEquipmentId, id).in(RepairOrder::getStatus, 0, 1, 2)
                    .last("LIMIT 1 FOR UPDATE"));
            if (openOrder != null) {
                throw new BusinessException(ResultCode.CONFLICT, "设备存在未完成工单，不能调整所属实验室或手动修改状态");
            }
            // 普通 CRUD 不负责中断预约。若直接停用/报废设备，学生将无法签到并可能被误判爽约。
            // 保持实验室、设备行锁后使用当前读，不能遗漏等待锁期间刚创建/审批通过的预约。
            Reservation activeReservation = reservationMapper.selectOne(new LambdaQueryWrapper<Reservation>()
                    .eq(Reservation::getEquipmentId, id).in(Reservation::getStatus, 0, 1)
                    .gt(Reservation::getEndTime, LocalDateTime.now().withNano(0))
                    .last("LIMIT 1 FOR UPDATE"));
            if (activeReservation != null) {
                throw new BusinessException(ResultCode.CONFLICT,
                        "设备存在未结束的待审核或已通过预约，不能调整所属实验室或手动修改状态；故障请通过工单受理处理");
            }
        }
        LambdaUpdateWrapper<Equipment> update = new LambdaUpdateWrapper<Equipment>()
                .eq(Equipment::getId, id)
                .set(Equipment::getLabId, request.getLabId())
                .set(Equipment::getEquipmentCode, request.getEquipmentCode().trim())
                .set(Equipment::getName, request.getName().trim())
                .set(Equipment::getModel, trimToNull(request.getModel()))
                .set(Equipment::getManufacturer, trimToNull(request.getManufacturer()))
                .set(Equipment::getStatus, request.getStatus())
                .set(Equipment::getPurchaseDate, request.getPurchaseDate())
                .set(Equipment::getImageUrl, trimToNull(request.getImageUrl()))
                .set(Equipment::getDescription, trimToNull(request.getDescription()))
                .set(Equipment::getUpdateTime, LocalDateTime.now());
        baseMapper.update(null, update);
        pageCache.evictAfterCommit();
        return requireEquipment(id, true);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteEquipment(Long id) {
        Equipment previous = requireEquipment(id, false);
        lockAndAuthorizeLabs(previous.getLabId());
        Equipment current = requireEquipment(id, true);
        ensureLabUnchanged(previous, current);
        // 逻辑删除不会触发外键删除检查，显式保护尚未删除的预约和工单关联。
        // 使用当前读：不能让事务早先的快照漏掉等待设备行锁期间刚提交的预约。
        Reservation reservation = reservationMapper.selectOne(new LambdaQueryWrapper<Reservation>()
                .eq(Reservation::getEquipmentId, id).last("LIMIT 1 FOR UPDATE"));
        RepairOrder repair = repairOrderMapper.selectOne(new LambdaQueryWrapper<RepairOrder>()
                .eq(RepairOrder::getEquipmentId, id).last("LIMIT 1 FOR UPDATE"));
        if (reservation != null || repair != null) {
            throw new BusinessException(ResultCode.CONFLICT, "设备仍有关联预约或工单，不能删除");
        }
        baseMapper.update(null, new LambdaUpdateWrapper<Equipment>()
                .eq(Equipment::getId, id).set(Equipment::getDeleted, 1)
                .set(Equipment::getUpdateTime, LocalDateTime.now()));
        pageCache.evictAfterCommit();
    }

    private Equipment requireEquipment(Long id, boolean lock) {
        LambdaQueryWrapper<Equipment> query = new LambdaQueryWrapper<Equipment>()
                .eq(Equipment::getId, id);
        if (lock) {
            query.last("FOR UPDATE");
        }
        Equipment equipment = baseMapper.selectOne(query);
        if (equipment == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "设备不存在");
        }
        return equipment;
    }

    private void lockAndAuthorizeLabs(Long... labIds) {
        StpUtil.checkLogin();
        boolean superAdmin = StpUtil.hasRole("admin");
        if (!superAdmin) {
            StpUtil.checkRole("lab_admin");
        }
        for (Long labId : new TreeSet<>(Arrays.asList(labIds))) {
            Lab lab = labMapper.selectOne(new LambdaQueryWrapper<Lab>()
                    .eq(Lab::getId, labId).last("FOR UPDATE"));
            if (lab == null) {
                throw new BusinessException(ResultCode.NOT_FOUND, "实验室不存在");
            }
            if (!superAdmin && !Objects.equals(lab.getAdminId(), StpUtil.getLoginIdAsLong())) {
                throw new BusinessException(ResultCode.FORBIDDEN, "只能管理本人负责实验室的设备");
            }
        }
    }

    private void ensureLabUnchanged(Equipment before, Equipment after) {
        if (!Objects.equals(before.getLabId(), after.getLabId())) {
            throw new BusinessException(ResultCode.CONFLICT, "设备所属实验室已变化，请刷新后重试");
        }
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
