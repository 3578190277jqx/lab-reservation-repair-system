package com.lab.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lab.entity.User;
import org.apache.ibatis.annotations.Mapper;

/** 用户数据访问接口。 */
@Mapper
public interface UserMapper extends BaseMapper<User> {
}
