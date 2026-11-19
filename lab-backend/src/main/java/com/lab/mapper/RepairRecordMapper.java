package com.lab.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lab.entity.RepairRecord;
import org.apache.ibatis.annotations.Mapper;

/** 维修记录数据访问接口。 */
@Mapper
public interface RepairRecordMapper extends BaseMapper<RepairRecord> {
}
