package com.lab.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lab.entity.RepairRecord;
import com.lab.mapper.RepairRecordMapper;
import com.lab.service.RepairRecordService;
import org.springframework.stereotype.Service;

/** 维修记录基础服务实现。 */
@Service
public class RepairRecordServiceImpl
        extends ServiceImpl<RepairRecordMapper, RepairRecord>
        implements RepairRecordService {
}
