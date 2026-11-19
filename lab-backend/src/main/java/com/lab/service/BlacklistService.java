package com.lab.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.lab.entity.Blacklist;

/** 爽约黑名单基础服务接口，业务规则在后续业务方法中实现。 */
public interface BlacklistService extends IService<Blacklist> {
    /** 申请/审批前检查；Redis miss 时由数据库兜底。 */
    void checkAllowed(Long studentId, java.time.LocalDateTime now);

    /** 只在预约首次从已通过转为爽约后调用，且必须持有学生锁及数据库事务。 */
    void onNoShow(Long studentId, java.time.LocalDateTime now);
}
