package com.lab.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lab.common.BusinessException;
import com.lab.common.ResultCode;
import com.lab.dto.LabPageQuery;
import com.lab.dto.LabSaveRequest;
import com.lab.entity.Equipment;
import com.lab.entity.Lab;
import com.lab.entity.User;
import com.lab.mapper.EquipmentMapper;
import com.lab.mapper.LabMapper;
import com.lab.mapper.ReservationMapper;
import com.lab.mapper.UserMapper;
import com.lab.service.LabService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class LabServiceImpl extends ServiceImpl<LabMapper, Lab> implements LabService {
    private final EquipmentMapper equipmentMapper;
    private final UserMapper userMapper;
    private final ReservationMapper reservationMapper;

    @Override
    public Page<Lab> pageLabs(LabPageQuery query) {
        StpUtil.checkLogin();
        String code = trimToNull(query.getLabCode());
        String name = trimToNull(query.getName());
        LambdaQueryWrapper<Lab> wrapper = new LambdaQueryWrapper<Lab>()
                .like(code != null, Lab::getLabCode, code)
                .like(name != null, Lab::getName, name)
                .eq(query.getStatus() != null, Lab::getStatus, query.getStatus())
                .eq(query.getAdminId() != null, Lab::getAdminId, query.getAdminId())
                .orderByDesc(Lab::getCreateTime).orderByDesc(Lab::getId);
        return baseMapper.selectPage(new Page<>(query.getCurrent(), query.getSize()), wrapper);
    }

    @Override
    public Lab getLab(Long id) {
        StpUtil.checkLogin();
        return requireLab(id, false);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Lab createLab(LabSaveRequest request) {
        StpUtil.checkRole("admin");
        validateAdmin(request.getAdminId());
        Lab lab = new Lab();
        lab.setLabCode(request.getLabCode().trim());
        lab.setName(request.getName().trim());
        lab.setLocation(request.getLocation().trim());
        lab.setAdminId(request.getAdminId());
        lab.setContactPhone(trimToNull(request.getContactPhone()));
        lab.setDescription(trimToNull(request.getDescription()));
        lab.setStatus(request.getStatus());
        // deleted/createTime/updateTime 由 MyBatis-Plus 自动填充。
        save(lab);
        return lab;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Lab updateLab(Long id, LabSaveRequest request) {
        StpUtil.checkRole("admin");
        Lab current = requireLab(id, true);
        validateAdmin(request.getAdminId());
        // 停用实验室会使其设备无法预约和签到。先阻止直接中断现有预约，避免误记学生爽约。
        // 已持有实验室行锁，新预约同样需要该锁；SQL 使用当前读以看到等待锁期间的提交。
        if (Integer.valueOf(1).equals(current.getStatus()) && !Integer.valueOf(1).equals(request.getStatus())
                && reservationMapper.selectActiveReservationIdByLab(id, LocalDateTime.now().withNano(0)) != null) {
            throw new BusinessException(ResultCode.CONFLICT,
                    "实验室存在未结束的待审核或已通过预约，请先处理预约后再停用");
        }
        // PUT 使用显式 set，让可空字段能够通过 null 清空。
        LambdaUpdateWrapper<Lab> update = new LambdaUpdateWrapper<Lab>()
                .eq(Lab::getId, id)
                .set(Lab::getLabCode, request.getLabCode().trim())
                .set(Lab::getName, request.getName().trim())
                .set(Lab::getLocation, request.getLocation().trim())
                .set(Lab::getAdminId, request.getAdminId())
                .set(Lab::getContactPhone, trimToNull(request.getContactPhone()))
                .set(Lab::getDescription, trimToNull(request.getDescription()))
                .set(Lab::getStatus, request.getStatus())
                .set(Lab::getUpdateTime, LocalDateTime.now());
        baseMapper.update(null, update);
        return requireLab(id, true);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteLab(Long id) {
        StpUtil.checkRole("admin");
        requireLab(id, true);
        // 与设备写操作共用实验室行锁；当前读避免事务快照漏掉新提交的设备。
        Equipment child = equipmentMapper.selectOne(new LambdaQueryWrapper<Equipment>()
                .eq(Equipment::getLabId, id).last("LIMIT 1 FOR UPDATE"));
        if (child != null) {
            throw new BusinessException(ResultCode.CONFLICT, "实验室下仍有设备，请先处理设备");
        }
        baseMapper.update(null, new LambdaUpdateWrapper<Lab>()
                .eq(Lab::getId, id).set(Lab::getDeleted, 1)
                .set(Lab::getUpdateTime, LocalDateTime.now()));
    }

    private Lab requireLab(Long id, boolean lock) {
        LambdaQueryWrapper<Lab> query = new LambdaQueryWrapper<Lab>().eq(Lab::getId, id);
        if (lock) {
            // 固定 SQL 片段，不接受客户端传入排序或 SQL。
            query.last("FOR UPDATE");
        }
        Lab lab = baseMapper.selectOne(query);
        if (lab == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "实验室不存在");
        }
        return lab;
    }

    private void validateAdmin(Long adminId) {
        if (adminId == null) {
            return;
        }
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getId,adminId).last("FOR UPDATE"));
        if (user == null || !Integer.valueOf(1).equals(user.getStatus())
                || !("admin".equals(user.getRole()) || "lab_admin".equals(user.getRole()))) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "负责人必须是启用的管理员账号");
        }
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
