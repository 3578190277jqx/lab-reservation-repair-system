package com.lab.config;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
@Data @ConfigurationProperties(prefix="assistant")
public class AssistantProperties {
 private boolean enabled=false;
 private String endpoint="https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions";
 private String apiKey="";
 private String model="qwen-plus";
}
