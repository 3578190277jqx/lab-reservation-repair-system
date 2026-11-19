package com.lab.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lab.entity.Lab;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** 实验室数据访问接口。 */
@Mapper
public interface LabMapper extends BaseMapper<Lab> {
    @Select("SELECT * FROM lab WHERE id=#{id} AND deleted=0 FOR UPDATE")
    Lab selectByIdForUpdate(@Param("id") Long id);
}
