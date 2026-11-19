package com.lab.service;
import com.lab.config.RecoveryProperties;
import com.lab.common.BusinessException;
import com.lab.common.ResultCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import lombok.RequiredArgsConstructor;
@Service @RequiredArgsConstructor
public class QqVerificationDelivery implements VerificationDelivery {
 private final JavaMailSender mail;
 private final RecoveryProperties properties;
 @Value("${spring.mail.username:}") private String sender;
 @Override public boolean enabled(String channel){return "email".equals(channel)&&properties.isEmailEnabled()&&!sender.trim().isEmpty();}
 @Override public void send(String channel,String target,String code){
  if(!enabled(channel)) throw new BusinessException(ResultCode.SERVICE_UNAVAILABLE,
   "phone".equals(channel)?"短信找回暂未开放，请使用已验证QQ邮箱":"邮箱验证码服务尚未配置，请联系管理员");
  SimpleMailMessage message=new SimpleMailMessage();message.setFrom(sender);message.setTo(target);
  message.setSubject("实验室平台验证码");message.setText("验证码："+code+"，5分钟内有效。请勿向他人透露；如非本人操作请忽略。");
  try{mail.send(message);}catch(RuntimeException ex){throw new BusinessException(ResultCode.SERVICE_UNAVAILABLE,"邮件发送失败，请稍后重试");}
 }
}
