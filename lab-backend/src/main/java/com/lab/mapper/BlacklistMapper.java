package com.lab.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lab.entity.Blacklist;
import org.apache.ibatis.annotations.Mapper;

/** 爽约黑名单数据访问接口。 */
@Mapper
public interface BlacklistMapper extends BaseMapper<Blacklist> {
    @org.apache.ibatis.annotations.Select("SELECT * FROM blacklist WHERE student_id=#{studentId} FOR UPDATE")
    Blacklist lockAnyByStudent(@org.apache.ibatis.annotations.Param("studentId") Long studentId);

    @org.apache.ibatis.annotations.Update("UPDATE blacklist SET reason=#{entry.reason}, no_show_count=#{entry.noShowCount}, "
        + "start_time=#{entry.startTime}, expire_time=#{entry.expireTime}, status=1, deleted=0, update_time=#{now} WHERE id=#{entry.id}")
    int reactivate(@org.apache.ibatis.annotations.Param("entry") Blacklist entry,
                   @org.apache.ibatis.annotations.Param("now") java.time.LocalDateTime now);

}
