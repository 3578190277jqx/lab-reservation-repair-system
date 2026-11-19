package com.lab.service;
import cn.hutool.crypto.digest.DigestUtil;
import com.lab.common.BusinessException;
import com.lab.common.ResultCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import java.util.Collections;
import java.security.SecureRandom;
import java.util.Locale;

/** Codes are purpose+account+channel+target bound, expire in 5min, one-use, max5 guesses. */
@Service @RequiredArgsConstructor
public class VerificationCodeService {
 private final StringRedisTemplate redis;
 private final RateLimitService limiter;
 private final VerificationDelivery delivery;
 private final SecureRandom random=new SecureRandom();
 private static final DefaultRedisScript<Long> WRITE=new DefaultRedisScript<>(
  "redis.call('HSET',KEYS[1],'digest',ARGV[1],'attempts',0); redis.call('EXPIRE',KEYS[1],300); return 1;",Long.class);
 private static final DefaultRedisScript<Long> CONSUME=new DefaultRedisScript<>(
  "local d=redis.call('HGET',KEYS[1],'digest'); if not d then return 0 end; "
  +"if d==ARGV[1] then redis.call('DEL',KEYS[1]); return 1 end; "
  +"local n=redis.call('HINCRBY',KEYS[1],'attempts',1); if n>=5 then redis.call('DEL',KEYS[1]) end; return 0;",Long.class);
 public String normalize(String channel,String target){
  String value=target==null?"":target.trim().toLowerCase(Locale.ROOT);
  boolean ok="email".equals(channel)?value.matches("[a-z0-9._%+\\-]+@qq\\.com"):"phone".equals(channel)&&value.matches("1[3-9][0-9]{9}");
  if(!ok)throw new BusinessException(ResultCode.BAD_REQUEST,"请输入有效QQ邮箱或11位手机号");
  return value;
 }
 public boolean enabled(String channel){return delivery.enabled(channel);}
 public void throttle(String target){
  limiter.check("code-cooldown",target,1,60);
  limiter.check("code-hour",target,5,3600);
 }
 public void send(String purpose,String username,String channel,String target){
  if(!enabled(channel))throw new BusinessException(ResultCode.SERVICE_UNAVAILABLE,"验证码发送服务未配置；短信找回暂未开放");
  String key=key(purpose,username,channel,target);
  String code=String.format(Locale.ROOT,"%06d",random.nextInt(1000000));
  redis.execute(WRITE,Collections.singletonList(key),DigestUtil.sha256Hex(key+":"+code));
  try{delivery.send(channel,target,code);}catch(RuntimeException ex){redis.delete(key);throw ex;}
 }
 public void consume(String purpose,String username,String channel,String target,String code){
  if(code==null||!code.matches("[0-9]{6}"))throw invalid();
  String key=key(purpose,username,channel,target);
  Long ok=redis.execute(CONSUME,Collections.singletonList(key),DigestUtil.sha256Hex(key+":"+code));
  if(!Long.valueOf(1).equals(ok))throw invalid();
 }
 private String key(String purpose,String username,String channel,String target){
  return "lab:otp:"+DigestUtil.sha256Hex(purpose+":"+username.toLowerCase(Locale.ROOT)+":"+channel+":"+target);
 }
 private BusinessException invalid(){return new BusinessException(ResultCode.BAD_REQUEST,"验证码无效或已过期，请重新获取");}
}
