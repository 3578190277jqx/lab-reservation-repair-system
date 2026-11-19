package com.lab.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lab.entity.Equipment;
import org.apache.ibatis.annotations.Mapper;

/** 设备数据访问接口。 */
@Mapper
public interface EquipmentMapper extends BaseMapper<Equipment> {
}
