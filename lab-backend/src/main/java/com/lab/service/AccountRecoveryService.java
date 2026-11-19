package com.lab.service;
import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.lab.entity.User;
import com.lab.mapper.UserMapper;
import com.lab.dto.*;
import com.lab.common.*;
import com.lab.utils.PasswordUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.time.LocalDateTime;
import java.util.*;
@Service @RequiredArgsConstructor @Slf4j
public class AccountRecoveryService {
 private final UserMapper users;
 private final VerificationCodeService codes;
 private final RateLimitService limiter;
 public Map<String,Boolean> options(){Map<String,Boolean> v=new HashMap<>();v.put("emailEnabled",codes.enabled("email"));v.put("phoneEnabled",codes.enabled("phone"));return v;}
 public void send(RecoveryCodeRequest q){
  String target=codes.normalize(q.getChannel(),q.getTarget());
  if(!codes.enabled(q.getChannel()))throw new BusinessException(ResultCode.SERVICE_UNAVAILABLE,"QQ邮箱服务未配置；短信找回暂未开放");
  codes.throttle(q.getChannel()+":"+target);
  User u=users.selectOne(new LambdaQueryWrapper<User>().eq(User::getUsername,q.getUsername()));
  if("bind".equals(q.getPurpose())){
   StpUtil.checkLogin();
   if(u==null||!Objects.equals(u.getId(),StpUtil.getLoginIdAsLong()))throw new BusinessException(ResultCode.FORBIDDEN);
   Object tokenVersion=StpUtil.getTokenSession().get("authVersion");
   int storedVersion=tokenVersion instanceof Number?((Number)tokenVersion).intValue():0;
   if(!Integer.valueOf(1).equals(u.getStatus())||storedVersion!=version(u))throw new BusinessException(ResultCode.UNAUTHORIZED);
  } else if("recover".equals(q.getPurpose())){
   // Same HTTP response whether account/contact exists; only verified contacts can recover.
   if(!matches(u,q.getChannel(),target))return;
  } else if(u!=null) return;
  codes.send(q.getPurpose(),q.getUsername(),q.getChannel(),target);
 }
 @Transactional(rollbackFor=Exception.class)
 public void reset(ResetPasswordRequest q){
  PasswordUtils.validateLength(q.getNewPassword());
  limiter.check("reset-account",q.getUsername().trim().toLowerCase(java.util.Locale.ROOT),10,600);
  String target=codes.normalize(q.getChannel(),q.getTarget());
  User u=users.selectOne(new LambdaQueryWrapper<User>().eq(User::getUsername,q.getUsername()).last("FOR UPDATE"));
  if(!matches(u,q.getChannel(),target))throw new BusinessException(ResultCode.BAD_REQUEST,"验证码无效或已过期，请重新获取");
  codes.consume("recover",u.getUsername(),q.getChannel(),target,q.getCode());
  // Only a valid one-use proof is allowed to trigger costly BCrypt work.
  String hash=PasswordUtils.encode(q.getNewPassword());
  users.update(null,new LambdaUpdateWrapper<User>().eq(User::getId,u.getId())
   .set(User::getPassword,hash).set(User::getAuthVersion,version(u)+1).set(User::getUpdateTime,LocalDateTime.now()));
  revokeAfterCommit(u.getId());
 }
 @Transactional(rollbackFor=Exception.class)
 public void bind(BindContactRequest q){
  StpUtil.checkLogin();
  limiter.check("bind-password",String.valueOf(StpUtil.getLoginIdAsLong()),10,600);
  User u=users.selectOne(new LambdaQueryWrapper<User>().eq(User::getId,StpUtil.getLoginIdAsLong()).last("FOR UPDATE"));
  if(u==null||!PasswordUtils.matches(q.getCurrentPassword(),u.getPassword()))throw new BusinessException(ResultCode.BAD_REQUEST,"当前密码错误");
  String target=codes.normalize(q.getChannel(),q.getTarget());
  codes.consume("bind",u.getUsername(),q.getChannel(),target,q.getCode());
  LambdaUpdateWrapper<User> update=new LambdaUpdateWrapper<User>().eq(User::getId,u.getId()).set(User::getUpdateTime,LocalDateTime.now());
  if("email".equals(q.getChannel()))update.set(User::getEmail,target).set(User::getEmailVerified,1);
  else update.set(User::getPhone,target).set(User::getPhoneVerified,1);
  users.update(null,update);
 }
 private boolean matches(User u,String channel,String target){
  if(u==null||!Integer.valueOf(1).equals(u.getStatus()))return false;
  return "email".equals(channel)?Integer.valueOf(1).equals(u.getEmailVerified())&&target.equalsIgnoreCase(u.getEmail())
   :Integer.valueOf(1).equals(u.getPhoneVerified())&&target.equals(u.getPhone());
 }
 private int version(User u){return u.getAuthVersion()==null?0:u.getAuthVersion();}
 public static void revokeAfterCommit(Long id){TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization(){
  @Override public void afterCommit(){try{StpUtil.logout(id);}catch(RuntimeException ex){log.warn("会话注销暂时失败，authVersion将拒绝旧Token，userId={}",id);}}
 });}
}
