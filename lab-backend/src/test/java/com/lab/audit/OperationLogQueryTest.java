package com.lab.audit;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lab.controller.OperationLogController;
import com.lab.dto.OperationLogPageQuery;
import com.lab.entity.OperationLog;
import com.lab.mapper.OperationLogMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class OperationLogQueryTest {
    @Test void adminQueryAppliesFiltersAndRetainsHistoryWithoutSoftDeleteCondition() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), "audit-test"), OperationLog.class);
        OperationLogMapper mapper = mock(OperationLogMapper.class);
        when(mapper.selectPage(any(Page.class), any())).thenAnswer(call -> call.getArgument(0));
        OperationLogPageQuery query = new OperationLogPageQuery();
        query.setCurrent(2L); query.setSize(20L); query.setModule("reservation"); query.setSuccess(0);
        Page<OperationLog> result = new OperationLogController(mapper).page(query).getData();
        assertEquals(2, result.getCurrent()); assertEquals(20, result.getSize());
        ArgumentCaptor<LambdaQueryWrapper<OperationLog>> filter = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(mapper).selectPage(any(), filter.capture());
        String sql = filter.getValue().getSqlSegment();
        assertTrue(sql.contains("module =")); assertTrue(sql.contains("success ="));
        assertTrue(sql.contains("ORDER BY id DESC"));
        assertFalse(TableInfoHelper.getTableInfo(OperationLog.class).isWithLogicDelete());
    }
}
