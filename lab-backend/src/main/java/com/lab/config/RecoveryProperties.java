package com.lab.config;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
@Data @Configuration @ConfigurationProperties(prefix="app.recovery")
public class RecoveryProperties {
 private boolean emailEnabled=false;
 private boolean smsEnabled=false;
 private String smsProvider="aliyun";
 private String smsSignName="";
 private String smsTemplateCode="";
 @lombok.ToString.Exclude private String smsAccessKeyId="";
 @lombok.ToString.Exclude private String smsAccessKeySecret="";
}
