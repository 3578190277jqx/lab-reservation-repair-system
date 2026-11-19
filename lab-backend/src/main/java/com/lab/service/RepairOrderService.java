package com.lab.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lab.dto.RepairActionRequest;
import com.lab.dto.RepairCompleteRequest;
import com.lab.dto.RepairPageQuery;
import com.lab.dto.RepairSubmitRequest;
import com.lab.entity.RepairOrder;
import com.lab.entity.RepairRecord;

import java.util.List;

/** 工单业务服务；所有写操作均校验身份、实验室范围、状态机及并发更新结果。 */
public interface RepairOrderService extends IService<RepairOrder> {

    /** 新建报修工单并记录首次提交；AI建议仅作参考，不参与状态流转。 */
    RepairOrder detail(Long id);

    RepairOrder submit(RepairSubmitRequest request);

    /** 查询本人提交的工单，不接受客户端覆盖报修人身份。 */
    Page<RepairOrder> my(RepairPageQuery query);

    /** 管理员分页查询；实验室管理员仅能查询其管理的实验室。 */
    Page<RepairOrder> pageOrders(RepairPageQuery query);

    /** 已报修待受理 -> 已受理，并写入操作记录。 */
    RepairOrder accept(RepairActionRequest request);

    /** 已受理 -> 维修中，并写入操作记录。 */
    RepairOrder start(RepairActionRequest request);

    /** 维修中 -> 已完成，并记录维修结果及费用。 */
    RepairOrder complete(RepairCompleteRequest request);

    /** 查询工单流转记录；仅报修人及有实验室管理权限的管理员可以访问。 */
    List<RepairRecord> records(Long orderId);

    /** 图片读取前执行工单可见范围校验，上传服务另外校验上传人权限。 */
    void checkImageReadAccess(String imageUrl);
}
