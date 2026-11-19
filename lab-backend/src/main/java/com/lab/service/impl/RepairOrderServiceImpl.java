package com.lab.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lab.cache.EquipmentPageCache;
import com.lab.common.BusinessException;
import com.lab.common.RepairStateMachine;
import com.lab.common.ResultCode;
import com.lab.dto.RepairActionRequest;
import com.lab.dto.RepairCompleteRequest;
import com.lab.dto.RepairPageQuery;
import com.lab.dto.RepairSubmitRequest;
import com.lab.entity.Equipment;
import com.lab.entity.Lab;
import com.lab.entity.RepairOrder;
import com.lab.entity.RepairRecord;
import com.lab.entity.Reservation;
import com.lab.enums.RepairStatusEnum;
import com.lab.mapper.EquipmentMapper;
import com.lab.mapper.LabMapper;
import com.lab.mapper.RepairOrderMapper;
import com.lab.mapper.RepairRecordMapper;
import com.lab.mapper.ReservationMapper;
import com.lab.service.LocalImageStorageService;
import com.lab.service.RepairOrderService;
import com.lab.service.UserService;
import com.lab.utils.ReservationLockExecutor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/** 工单状态机业务。锁执行器内部开启事务，禁止在本类外层提前开启事务。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RepairOrderServiceImpl extends ServiceImpl<RepairOrderMapper, RepairOrder>
        implements RepairOrderService {
    private final EquipmentMapper equipmentMapper;
    private final LabMapper labMapper;
    private final RepairRecordMapper repairRecordMapper;
    private final ReservationMapper reservationMapper;
    private final ReservationLockExecutor lockExecutor;
    private final EquipmentPageCache equipmentPageCache;
    private final LocalImageStorageService imageStorage;
    private final com.lab.service.AiSuggestionService aiSuggestionService;
    private final UserService userService;
    private final Clock clock;
    private final com.lab.service.FaultOutboxStore faultOutboxStore;

    @Override
    public RepairOrder detail(Long id) {
        StpUtil.checkLogin();
        RepairOrder order=requireOrder(id,false);
        if(!StpUtil.hasRole("admin")&&!Objects.equals(order.getReporterId(),StpUtil.getLoginIdAsLong())) {
            Equipment e=equipmentMapper.selectById(order.getEquipmentId());
            if(e==null)throw new BusinessException(ResultCode.FORBIDDEN);
            checkManagerOf(e);
        }
        return order;
    }

    @Override
    public RepairOrder submit(RepairSubmitRequest request) {
        StpUtil.checkLogin();
        Long reporterId = StpUtil.getLoginIdAsLong();
        // 1. 先做快速检查，减少明显无效或重复请求消耗第三方API额度；锁内仍会复查。
        Equipment hint = equipmentMapper.selectById(request.getEquipmentId());
        checkReportable(hint);
        checkNoOpenOrder(hint.getId());
        String imageUrl = trimToNull(request.getImageUrl());
        String suggestion = null;
        if (imageUrl != null) {
            // 2. 只读取当前用户已经上传的本地图片，不下载客户端任意URL。
            byte[] image = imageStorage.readOwnedImage(imageUrl, reporterId);
            if (Boolean.TRUE.equals(request.getAnalyzeImage())) {
                // 3. 外部HTTP调用在数据库事务、设备锁之前执行，超时也不阻塞核心事务。
                try {
                    suggestion = aiSuggestionService.suggest(hint.getId(),reporterId,image);
                } catch (RuntimeException ex) {
                    // 防御客户端实现异常；记录类型，不记录请求地址、Token或图片内容。
                    log.warn("AI辅助失败，报修继续处理：{}", ex.getClass().getSimpleName());
                    suggestion = "AI辅助暂不可用，请人工检查；此提示不影响工单处理。";
                }
            }
        }
        String serverSuggestion = limitSuggestion(suggestion);
        return imageStorage.withOwnerLock(reporterId, () -> lockExecutor.execute(null, hint.getId(), () -> {
            if(imageUrl!=null)imageStorage.readOwnedImage(imageUrl,reporterId);
            // 外部识别和等待锁期间账号可能已被禁用或退出，入库前再次确认身份有效。
            StpUtil.checkLogin();
            userService.requireActiveUser(reporterId);
            // 4. 与预约共用 equipment:lock:{id}，再按实验室→设备顺序取得数据库行锁。
            Equipment equipment = lockEquipment(hint.getId());
            checkReportable(equipment);
            checkNoOpenOrder(equipment.getId());
            RepairStateMachine.validateTransition(null, RepairStatusEnum.REPORTED);
            RepairOrder order = new RepairOrder();
            order.setOrderNo("RO" + IdUtil.fastSimpleUUID());
            order.setEquipmentId(equipment.getId());
            order.setReporterId(reporterId);
            order.setDescription(request.getDescription().trim());
            order.setImageUrl(imageUrl);
            order.setAiSuggestion(serverSuggestion);
            order.setStatus(RepairStatusEnum.REPORTED.getCode());
            save(order);
            // 5. 首次提交也记录null→reported。日志失败会回滚工单创建。
            appendRecord(order.getId(), null, RepairStatusEnum.REPORTED,
                    reporterId, order.getDescription(), null, now());
            // 提交阶段不改变设备和预约，必须由管理员受理确认故障。
            return order;
        }));
    }

    @Override
    public Page<RepairOrder> my(RepairPageQuery query) {
        StpUtil.checkLogin();
        return baseMapper.selectRepairPage(new Page<>(query.getCurrent(), query.getSize()),
                query, StpUtil.getLoginIdAsLong(), null);
    }

    @Override
    public Page<RepairOrder> pageOrders(RepairPageQuery query) {
        Long managerId = managerScope();
        return baseMapper.selectRepairPage(new Page<>(query.getCurrent(), query.getSize()),
                query, query.getReporterId(), managerId);
    }

    @Override
    public RepairOrder accept(RepairActionRequest request) {
        return transition(request.getRepairOrderId(), RepairStatusEnum.ACCEPTED,
                defaultContent(request.getContent(), "工单已受理"), null);
    }

    @Override
    public RepairOrder start(RepairActionRequest request) {
        return transition(request.getRepairOrderId(), RepairStatusEnum.REPAIRING,
                defaultContent(request.getContent(), "开始维修"), null);
    }

    @Override
    public RepairOrder complete(RepairCompleteRequest request) {
        if (!StringUtils.hasText(request.getContent()) || request.getRepairCost() == null
                || request.getRepairCost().signum() < 0) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "请填写维修结果和非负维修费用");
        }
        return transition(request.getRepairOrderId(), RepairStatusEnum.COMPLETED,
                request.getContent().trim(), request.getRepairCost());
    }

    private RepairOrder transition(Long orderId, RepairStatusEnum target, String content, BigDecimal cost) {
        managerScope();
        RepairOrder hint = requireOrder(orderId, false);
        return lockExecutor.execute(null, hint.getEquipmentId(), () -> {
            Equipment equipment = lockEquipment(hint.getEquipmentId());
            RepairOrder order = requireOrder(orderId, true);
            checkManagerOf(equipment);
            // 1. 状态机校验：只允许0→1→2→3，禁止重复、回退或跳过受理直接维修。
            RepairStateMachine.validateTransition(order.getStatus(), target);
            Long operatorId = StpUtil.getLoginIdAsLong();
            if (target != RepairStatusEnum.ACCEPTED && !StpUtil.hasRole("admin")
                    && !Objects.equals(order.getHandlerId(), operatorId)) {
                throw new BusinessException(ResultCode.FORBIDDEN, "仅受理人或超级管理员可以继续处理此工单");
            }
            LocalDateTime now = now();
            LambdaUpdateWrapper<RepairOrder> update = new LambdaUpdateWrapper<RepairOrder>()
                    .eq(RepairOrder::getId, orderId).eq(RepairOrder::getStatus, order.getStatus())
                    .set(RepairOrder::getStatus, target.getCode()).set(RepairOrder::getUpdateTime, now);
            if (target == RepairStatusEnum.ACCEPTED) {
                checkReportable(equipment);
                update.set(RepairOrder::getHandlerId, operatorId).set(RepairOrder::getAcceptedTime, now);
            } else if (target == RepairStatusEnum.REPAIRING) {
                update.set(RepairOrder::getRepairStartTime, now);
            } else if (target == RepairStatusEnum.COMPLETED) {
                update.set(RepairOrder::getCompletedTime, now);
            }
            // 2. SQL条件更新作为额外保护。只有赢得状态更新的请求才能写流转记录。
            if (baseMapper.update(null, update) != 1) {
                throw new BusinessException(ResultCode.INVALID_STATUS_TRANSITION, "工单状态已变化，请刷新后重试");
            }
            if (target == RepairStatusEnum.ACCEPTED) {
                // 3. 确认故障后停用设备；已签到使用记录先关闭，再系统取消受影响预约。
                setEquipmentStatus(equipment, 2, now);
                baseMapper.closeInterruptedUsage(equipment.getId(), now);
                // 与取消使用相同条件记录事件；仅在本事务提交后可被站内发布任务看到。
                faultOutboxStore.recordAffected(orderId, equipment.getId(), now);
                baseMapper.cancelAffectedReservations(equipment.getId(), now,
                        "设备故障，工单" + order.getOrderNo() + "受理后系统取消预约");
            } else if (target == RepairStatusEnum.COMPLETED) {
                restoreEquipment(equipment, orderId, now);
            }
            // 4. 记录操作人、前后状态、时间、说明和费用；任何写入失败都会整体回滚。
            appendRecord(orderId, order.getStatus(), target, operatorId, content, cost, now);
            return requireOrder(orderId, true);
        });
    }

    @Override
    public List<RepairRecord> records(Long orderId) {
        StpUtil.checkLogin();
        RepairOrder order = requireOrder(orderId, false);
        if (!StpUtil.hasRole("admin") && !Objects.equals(order.getReporterId(), StpUtil.getLoginIdAsLong())) {
            Equipment equipment = equipmentMapper.selectById(order.getEquipmentId());
            if (equipment == null) {
                throw new BusinessException(ResultCode.FORBIDDEN);
            }
            checkManagerOf(equipment);
        }
        return repairRecordMapper.selectList(new LambdaQueryWrapper<RepairRecord>()
                .eq(RepairRecord::getRepairOrderId, orderId)
                .orderByAsc(RepairRecord::getOperationTime).orderByAsc(RepairRecord::getId));
    }

    @Override
    public void checkImageReadAccess(String imageUrl) {
        StpUtil.checkLogin();
        Long currentId = StpUtil.getLoginIdAsLong();
        // 未绑定工单的新上传图片也允许上传者本人预览。文件服务随后继续验证路径格式。
        if (StpUtil.hasRole("admin") || imageUrl.startsWith("/file/images/" + currentId + "/")) {
            return;
        }
        Long managerId = StpUtil.hasRole("lab_admin") ? currentId : null;
        if (baseMapper.countReadableImage(imageUrl, currentId, managerId) == 0) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权查看该报修图片");
        }
    }

    private void appendRecord(Long orderId, Integer from, RepairStatusEnum to, Long operatorId,
                              String content, BigDecimal cost, LocalDateTime time) {
        RepairRecord record = new RepairRecord();
        record.setRepairOrderId(orderId);
        record.setOperatorId(operatorId);
        record.setFromStatus(from);
        record.setToStatus(to.getCode());
        record.setContent(content);
        record.setRepairCost(cost);
        record.setOperationTime(time);
        repairRecordMapper.insert(record);
    }

    private Equipment lockEquipment(Long id) {
        Equipment hint = equipmentMapper.selectById(id);
        if (hint == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "设备不存在");
        }
        Lab lab = labMapper.selectOne(new LambdaQueryWrapper<Lab>()
                .eq(Lab::getId, hint.getLabId()).last("FOR UPDATE"));
        if (lab == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "实验室不存在");
        }
        Equipment current = equipmentMapper.selectOne(new LambdaQueryWrapper<Equipment>()
                .eq(Equipment::getId, id).last("FOR UPDATE"));
        if (current == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "设备不存在");
        }
        if (!Objects.equals(current.getLabId(), lab.getId())) {
            throw new BusinessException(ResultCode.CONFLICT, "设备所属实验室已变化，请重试");
        }
        return current;
    }

    private RepairOrder requireOrder(Long id, boolean lock) {
        LambdaQueryWrapper<RepairOrder> query = new LambdaQueryWrapper<RepairOrder>().eq(RepairOrder::getId, id);
        if (lock) {
            query.last("FOR UPDATE");
        }
        RepairOrder order = baseMapper.selectOne(query);
        if (order == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "工单不存在");
        }
        return order;
    }

    private void checkReportable(Equipment equipment) {
        if (equipment == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "设备不存在");
        }
        if (Integer.valueOf(3).equals(equipment.getStatus())) {
            throw new BusinessException(ResultCode.CONFLICT, "已报废设备不能提交或受理报修");
        }
    }

    private void checkNoOpenOrder(Long equipmentId) {
        if (baseMapper.selectCount(new LambdaQueryWrapper<RepairOrder>()
                .eq(RepairOrder::getEquipmentId, equipmentId).in(RepairOrder::getStatus, 0, 1, 2)) > 0) {
            throw new BusinessException(ResultCode.CONFLICT, "该设备已有未完成工单，请勿重复报修");
        }
    }

    private Long managerScope() {
        if (StpUtil.hasRole("admin")) {
            return null;
        }
        StpUtil.checkRole("lab_admin");
        return StpUtil.getLoginIdAsLong();
    }

    private void checkManagerOf(Equipment equipment) {
        Long managerId = managerScope();
        if (managerId != null) {
            Lab lab = labMapper.selectById(equipment.getLabId());
            if (lab == null || !Objects.equals(lab.getAdminId(), managerId)) {
                throw new BusinessException(ResultCode.FORBIDDEN, "只能处理本人负责实验室的工单");
            }
        }
    }

    private void restoreEquipment(Equipment equipment, Long completedOrderId, LocalDateTime now) {
        // 历史异常数据中若还有其他未完成工单，保持维修中；绝不恢复已报废设备。
        if (Integer.valueOf(3).equals(equipment.getStatus())) {
            return;
        }
        long otherOpen = baseMapper.selectCount(new LambdaQueryWrapper<RepairOrder>()
                .eq(RepairOrder::getEquipmentId, equipment.getId()).ne(RepairOrder::getId, completedOrderId)
                .in(RepairOrder::getStatus, 0, 1, 2));
        if (otherOpen > 0) {
            setEquipmentStatus(equipment, 2, now);
            return;
        }
        long reserved = reservationMapper.selectCount(new LambdaQueryWrapper<Reservation>()
                .eq(Reservation::getEquipmentId, equipment.getId()).eq(Reservation::getStatus, 1)
                .gt(Reservation::getEndTime, now));
        setEquipmentStatus(equipment, reserved > 0 ? 1 : 0, now);
    }

    private void setEquipmentStatus(Equipment equipment, int status, LocalDateTime now) {
        if (!Integer.valueOf(status).equals(equipment.getStatus())) {
            equipmentMapper.update(null, new LambdaUpdateWrapper<Equipment>()
                    .eq(Equipment::getId, equipment.getId()).set(Equipment::getStatus, status)
                    .set(Equipment::getUpdateTime, now));
            equipmentPageCache.evictAfterCommit();
        }
    }

    private LocalDateTime now() { return LocalDateTime.now(clock).withNano(0); }
    private String trimToNull(String value) { return StringUtils.hasText(value) ? value.trim() : null; }
    private String defaultContent(String value, String fallback) {
        return StringUtils.hasText(value) ? value.trim() : fallback;
    }
    private String limitSuggestion(String value) {
        return value == null || value.length() <= 2000 ? value : value.substring(0, 2000);
    }
}
