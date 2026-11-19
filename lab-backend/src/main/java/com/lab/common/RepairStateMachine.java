package com.lab.common;

import com.lab.enums.RepairStatusEnum;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/**
 * 工单状态机：通过显式迁移表定义允许的方向，避免在各个接口中重复编写状态判断。
 * 此类只负责流程规则；权限、数据库事务和并发条件更新由服务层负责。
 */
public final class RepairStateMachine {

    private static final Map<RepairStatusEnum, RepairStatusEnum> TRANSITIONS;

    static {
        EnumMap<RepairStatusEnum, RepairStatusEnum> transitions = new EnumMap<>(RepairStatusEnum.class);
        transitions.put(RepairStatusEnum.REPORTED, RepairStatusEnum.ACCEPTED);
        transitions.put(RepairStatusEnum.ACCEPTED, RepairStatusEnum.REPAIRING);
        transitions.put(RepairStatusEnum.REPAIRING, RepairStatusEnum.COMPLETED);
        // COMPLETED为终态，没有任何后继状态；不可逆、不可跳级、不可重复流转。
        TRANSITIONS = Collections.unmodifiableMap(transitions);
    }

    private RepairStateMachine() {
    }

    /**
     * 校验一次状态迁移是否合法。
     *
     * @param fromStatus 原状态编码；仅首次提交新工单时传null
     * @param toStatus 由具体业务方法指定的目标状态，禁止直接采用客户端输入
     * @throws BusinessException 不符合状态机规则时返回40902业务错误
     */
    public static void validateTransition(Integer fromStatus, RepairStatusEnum toStatus) {
        // 第一步：校验目标状态。该参数应该来自业务代码中的枚举常量。
        if (toStatus == null) {
            throw new BusinessException(ResultCode.INVALID_STATUS_TRANSITION, "工单目标状态不能为空");
        }

        // 第二步：新工单只能从无状态进入REPORTED，并写入一条from_status为NULL的提交记录。
        if (fromStatus == null) {
            if (toStatus != RepairStatusEnum.REPORTED) {
                throw new BusinessException(ResultCode.INVALID_STATUS_TRANSITION, "新工单只能进入已报修待受理状态");
            }
            return;
        }

        // 第三步：严格查迁移表，仅允许当前状态的唯一后继状态。
        RepairStatusEnum from = RepairStatusEnum.fromCode(fromStatus);
        if (TRANSITIONS.get(from) != toStatus) {
            throw new BusinessException(ResultCode.INVALID_STATUS_TRANSITION,
                    "工单不能从“" + from.getDescription() + "”流转为“" + toStatus.getDescription() + "”");
        }
    }
}
