package com.lab.vo;
import lombok.Value;
import io.swagger.v3.oas.annotations.media.Schema;
@Value @Schema(description="助手回复；mode区分模型生成与固定指南")
public class AssistantAnswer { String mode; String answer; }
