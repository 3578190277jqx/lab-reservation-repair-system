package com.lab.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.lab.dto.LoginRequest;
import com.lab.dto.RegisterRequest;
import com.lab.dto.RoleUpdateRequest;
import com.lab.dto.UserPageQuery;
import com.lab.entity.User;
import com.lab.vo.LoginVO;
import com.lab.vo.UserInfoVO;

/** 用户登录、注册及权限管理服务。 */
public interface UserService extends IService<User> {

    LoginVO login(LoginRequest request);

    UserInfoVO register(RegisterRequest request);

    UserInfoVO info();

    void logout();

    IPage<UserInfoVO> pageUsers(UserPageQuery query);

    void updateRole(RoleUpdateRequest request);
    void updateUser(Long id, com.lab.dto.UserAdminUpdateRequest request);

    /** 查询未删除且启用的用户，供鉴权和用户信息接口使用。 */
    User requireActiveUser(Long userId);
}
