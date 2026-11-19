package com.lab.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class Knife4jConfig {

    @Bean
    public OpenAPI labOpenApi() {
        String securityName = "bearerAuth";
        return new OpenAPI()
                .info(new Info()
                        .title("实验室设备预约与故障报修管理系统")
                        .description("预约、设备管理、故障工单及统计接口")
                        .version("1.0.0"))
                .components(new Components().addSecuritySchemes(securityName,
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("Sa-Token")))
                .addSecurityItem(new SecurityRequirement().addList(securityName));
    }

    @Bean
    public GroupedOpenApi labApi() {
        return GroupedOpenApi.builder()
                .group("lab-api")
                .packagesToScan("com.lab.controller")
                .pathsToMatch("/api/**", "/user/**", "/lab/**", "/equipment/**", "/reservation/**", "/repair/**", "/file/**", "/stats/**")
                .build();
    }
}
