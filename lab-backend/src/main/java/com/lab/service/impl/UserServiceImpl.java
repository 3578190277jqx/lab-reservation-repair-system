package com.lab.service.impl;

import cn.dev33.satoken.SaManager;
import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lab.common.BusinessException;
import com.lab.common.ResultCode;
import com.lab.dto.LoginRequest;
import com.lab.dto.RegisterRequest;
import com.lab.dto.RoleUpdateRequest;
import com.lab.dto.UserPageQuery;
import com.lab.entity.User;
import com.lab.mapper.UserMapper;
import com.lab.service.UserService;
import com.lab.utils.PasswordUtils;
import com.lab.vo.LoginVO;
import com.lab.vo.UserInfoVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Objects;

@Slf4j
@Service
@org.springframework.boot.sql.init.dependency.DependsOnDatabaseInitialization
@lombok.RequiredArgsConstructor
public class UserServiceImpl
        extends ServiceImpl<UserMapper, User>
        implements UserService {

    private final com.lab.service.VerificationCodeService codes;
    private final com.lab.service.RateLimitService limiter;
    private final com.lab.mapper.LabMapper labs;
    private final com.lab.mapper.ReservationMapper reservations;
    private final com.lab.service.AdminMutationExecutor adminMutation;
    private final com.lab.mapper.RepairOrderMapper repairs;

    @Override
    public LoginVO login(LoginRequest request) {
        limiter.check("login-account", request.getUsername().trim().toLowerCase(java.util.Locale.ROOT), 10, 600);
        User user = lambdaQuery()
                .eq(User::getUsername, request.getUsername().trim())
                .one();
        if (user == null || !PasswordUtils.matches(request.getPassword(), user.getPassword())) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "用户名或密码错误");
        }
        checkEnabled(user);

        // 只修改登录时间，避免把旧实体中的角色、状态覆盖回数据库。
        // 本方法不启动数据库事务，此处SQL提交之后才创建Redis登录态。
        LocalDateTime now = LocalDateTime.now();
        boolean updated = lambdaUpdate()
                .eq(User::getId, user.getId())
                .eq(User::getStatus, 1)
                .set(User::getLastLoginTime, now)
                .set(User::getUpdateTime, now)
                .update();
        if (!updated) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "账号状态已变化，请重新登录");
        }

        StpUtil.login(user.getId());
        // Password reset increments DB version; a login that raced reset keeps the old version and is rejected.
        StpUtil.getTokenSession().set("authVersion",user.getAuthVersion()==null?0:user.getAuthVersion());
        user.setLastLoginTime(now);
        user.setUpdateTime(now);

        LoginVO result = new LoginVO();
        result.setToken(StpUtil.getTokenValue());
        result.setTokenName(StpUtil.getTokenName());
        result.setTokenPrefix(SaManager.getConfig().getTokenPrefix());
        result.setExpiresIn(StpUtil.getTokenTimeout());
        result.setUser(toInfo(user));
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UserInfoVO register(RegisterRequest request) {
        String email = codes.normalize("email", request.getEmail());
        codes.consume("register",request.getUsername().trim(),"email",email,request.getEmailCode());
        User user = new User();
        // 显式复制允许注册的字段，绝不从客户端接收角色或状态。
        user.setUsername(request.getUsername().trim());
        user.setPassword(PasswordUtils.encode(request.getPassword()));
        user.setRealName(request.getRealName().trim());
        user.setStudentNo(request.getStudentNo().trim());
        user.setPhone(trimToNull(request.getPhone()));
        user.setEmail(email);
        user.setEmailVerified(1);
        user.setPhoneVerified(0);
        user.setAuthVersion(0);
        user.setRole("student");
        user.setStatus(1);
        user.setDeleted(0);
        try {
            if (!save(user)) {
                throw new BusinessException(ResultCode.INTERNAL_ERROR, "注册失败");
            }
        } catch (DuplicateKeyException ex) {
            // 唯一索引负责处理并发注册，逻辑删除记录也仍占用唯一键。
            throw new BusinessException(ResultCode.CONFLICT, "用户名或学号已存在");
        }
        return toInfo(user);
    }

    @Override
    public UserInfoVO info() {
        return toInfo(requireActiveUser(StpUtil.getLoginIdAsLong()));
    }

    @Override
    public void logout() {
        // 注销当前请求携带的Token。
        StpUtil.logout();
    }

    @Override
    public IPage<UserInfoVO> pageUsers(UserPageQuery query) {
        StpUtil.checkRole("admin");
        String username = trimToNull(query.getUsername());
        String role = trimToNull(query.getRole());
        LambdaQueryWrapper<User> wrapper = Wrappers.<User>lambdaQuery()
                .select(User.class, field -> !"password".equals(field.getProperty()))
                .like(username != null, User::getUsername, username)
                .eq(role != null, User::getRole, role)
                .eq(query.getStatus() != null, User::getStatus, query.getStatus())
                .orderByDesc(User::getCreateTime)
                .orderByDesc(User::getId);
        IPage<User> page = baseMapper.selectPage(new Page<>(query.getCurrent(), query.getSize()), wrapper);
        return page.convert(this::toInfo);
    }

    @Override
    public void updateRole(RoleUpdateRequest request) {
        adminMutation.run(() -> {
        StpUtil.checkRole("admin");
        if (!Arrays.asList("admin", "lab_admin", "student").contains(request.getRole())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "角色不合法");
        }
        User target = baseMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getId,request.getUserId()).last("FOR UPDATE"));
        if (target == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "用户不存在");
        }
        if (Objects.equals(target.getId(), StpUtil.getLoginIdAsLong())
                && !"admin".equals(request.getRole())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "不能撤销当前登录账号的管理员角色");
        }
        if (Objects.equals(target.getRole(), request.getRole())) {
            return;
        }
        if("student".equals(target.getRole())) ensureNoStudentReservations(target.getId());
        if("student".equals(request.getRole())) ensureHandover(target.getId());
        boolean updated = lambdaUpdate()
                .eq(User::getId, target.getId())
                .eq(User::getRole, target.getRole())
                .set(User::getRole, request.getRole())
                .set(User::getAuthVersion,(target.getAuthVersion()==null?0:target.getAuthVersion())+1)
                .set(User::getUpdateTime, LocalDateTime.now())
                .update();
        if (!updated) {
            throw new BusinessException(ResultCode.CONFLICT, "用户角色已变化，请刷新后重试");
        }

        // MySQL提交成功后再注销Redis会话，避免数据库回滚但会话已经被删除。
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    StpUtil.logout(target.getId());
                } catch (RuntimeException ex) {
                    // 鉴权始终读取数据库，即使注销暂时失败，也不会继续授予旧角色权限。
                    log.error("用户角色已提交，但注销登录态失败，userId={}", target.getId(), ex);
                }
            }
        });
    
        });
    }

    @Override
    public User requireActiveUser(Long userId) {
        // MyBatis-Plus自动过滤deleted=1。
        User user = getById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "账号不存在或已删除");
        }
        checkEnabled(user);
        return user;
    }

    @Override
    public void updateUser(Long id, com.lab.dto.UserAdminUpdateRequest q) {
        adminMutation.run(() -> {
        StpUtil.checkRole("admin");
        User u=baseMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getId,id).last("FOR UPDATE"));
        if(u==null)throw new BusinessException(ResultCode.NOT_FOUND);
        if(Integer.valueOf(0).equals(q.getStatus())) {
            if(Objects.equals(id,StpUtil.getLoginIdAsLong()))throw new BusinessException(ResultCode.FORBIDDEN,"不能禁用当前管理员");
            ensureHandover(id);
            ensureNoStudentReservations(id);
        }
        String email=trimToNull(q.getEmail()),phone=trimToNull(q.getPhone());
        if(email!=null)email=email.toLowerCase(java.util.Locale.ROOT);
        LambdaUpdateWrapper<User> update=new LambdaUpdateWrapper<User>().eq(User::getId,id)
            .set(User::getRealName,q.getRealName().trim()).set(User::getStudentNo,trimToNull(q.getStudentNo()))
            .set(User::getPhone,phone).set(User::getEmail,email).set(User::getStatus,q.getStatus()).set(User::getUpdateTime,LocalDateTime.now());
        if(!Objects.equals(email,u.getEmail()))update.set(User::getEmailVerified,0);
        if(!Objects.equals(phone,u.getPhone()))update.set(User::getPhoneVerified,0);
        boolean changed=!Objects.equals(q.getStatus(),u.getStatus());
        if(changed)update.set(User::getAuthVersion,(u.getAuthVersion()==null?0:u.getAuthVersion())+1);
        baseMapper.update(null,update);
        if(changed)com.lab.service.AccountRecoveryService.revokeAfterCommit(id);
    
        });
    }

    private void ensureNoStudentReservations(Long id) {
        com.lab.entity.Reservation active=reservations.selectOne(new LambdaQueryWrapper<com.lab.entity.Reservation>()
            .eq(com.lab.entity.Reservation::getStudentId,id).in(com.lab.entity.Reservation::getStatus,0,1)
            .gt(com.lab.entity.Reservation::getEndTime,LocalDateTime.now()).last("LIMIT 1 FOR UPDATE"));
        if(active!=null)throw new BusinessException(ResultCode.CONFLICT,"请先处理该账号未结束的预约，再修改角色或禁用");
    }

    private void ensureHandover(Long id) {
        if(labs.selectCount(new LambdaQueryWrapper<com.lab.entity.Lab>().eq(com.lab.entity.Lab::getAdminId,id))>0
            || repairs.selectCount(new LambdaQueryWrapper<com.lab.entity.RepairOrder>().eq(com.lab.entity.RepairOrder::getHandlerId,id).in(com.lab.entity.RepairOrder::getStatus,0,1,2))>0)
            throw new BusinessException(ResultCode.CONFLICT,"请先移交负责的实验室及未完成工单，再降级或禁用账号");
    }

    private void checkEnabled(User user) {
        if (!Integer.valueOf(1).equals(user.getStatus())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "账号已被禁用");
        }
        if (!Arrays.asList("admin", "lab_admin", "student").contains(user.getRole())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "账号角色无效");
        }
    }

    private UserInfoVO toInfo(User user) {
        // VO不声明password字段，登录、分页、个人信息均使用此响应模型。
        return BeanUtil.copyProperties(user, UserInfoVO.class);
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
