package com.lab.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lab.entity.UsageLog;
import org.apache.ibatis.annotations.Mapper;

/** 设备使用日志数据访问接口。 */
@Mapper
public interface UsageLogMapper extends BaseMapper<UsageLog> {
}
