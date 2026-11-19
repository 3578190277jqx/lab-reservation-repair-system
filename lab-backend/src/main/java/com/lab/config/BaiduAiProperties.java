package com.lab.config;

import lombok.Data;
import lombok.ToString;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;

/** 百度凭证只从配置/环境变量读取，不返回前端，也不输出到日志。 */
@Data
@Validated
@ConfigurationProperties(prefix = "app.ai.baidu")
public class BaiduAiProperties {

    private boolean enabled = false;

    @ToString.Exclude
    private String apiKey;

    @ToString.Exclude
    private String secretKey;

    @Min(100)
    @Max(10000)
    private int connectTimeoutMs = 2000;

    @Min(100)
    @Max(10000)
    private int readTimeoutMs = 5000;
}
