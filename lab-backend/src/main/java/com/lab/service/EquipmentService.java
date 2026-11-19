package com.lab.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lab.dto.EquipmentPageQuery;
import com.lab.dto.EquipmentSaveRequest;
import com.lab.entity.Equipment;

/** 设备基础服务接口，业务规则在后续业务方法中实现。 */
public interface EquipmentService extends IService<Equipment> {
    Page<Equipment> pageEquipment(EquipmentPageQuery query);
    Equipment getEquipment(Long id);
    Equipment createEquipment(EquipmentSaveRequest request);
    Equipment updateEquipment(Long id, EquipmentSaveRequest request);
    void deleteEquipment(Long id);
}
