package com.lab.config;

import lombok.Data;
import lombok.ToString;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;

@Data
@Validated
@ConfigurationProperties(prefix = "app.ai.qwen")
public class QwenAiProperties {
    private boolean enabled = false;
    @ToString.Exclude
    private String apiKey;
    private String endpoint = "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions";
    private String model = "qwen3-vl-plus";
    @Min(100) @Max(2000)
    private int connectTimeoutMs = 2000;
    @Min(100) @Max(7000)
    private int readTimeoutMs = 7000;
}
