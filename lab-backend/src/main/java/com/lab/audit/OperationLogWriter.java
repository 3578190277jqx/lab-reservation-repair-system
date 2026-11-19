package com.lab.audit;

import com.lab.entity.OperationLog;
import com.lab.mapper.OperationLogMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OperationLogWriter {
    private final OperationLogMapper mapper;

    /** 独立事务保留失败的业务尝试，写入异常由切面兜底，不改变已成功的业务响应。 */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void write(OperationLog log) { mapper.insert(log); }
}
