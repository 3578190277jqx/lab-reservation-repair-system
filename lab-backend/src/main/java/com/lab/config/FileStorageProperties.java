package com.lab.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import javax.validation.constraints.NotBlank;

/** 上传目录须由应用账号独占写入；多实例部署时挂载同一个持久化目录。 */
@Data
@Validated
@ConfigurationProperties(prefix = "app.upload")
public class FileStorageProperties {

    @NotBlank
    private String directory = "./uploads";
}
