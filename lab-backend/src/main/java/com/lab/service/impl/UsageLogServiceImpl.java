package com.lab.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lab.entity.UsageLog;
import com.lab.mapper.UsageLogMapper;
import com.lab.service.UsageLogService;
import org.springframework.stereotype.Service;

/** 设备使用日志基础服务实现。 */
@Service
public class UsageLogServiceImpl
        extends ServiceImpl<UsageLogMapper, UsageLog>
        implements UsageLogService {
}
