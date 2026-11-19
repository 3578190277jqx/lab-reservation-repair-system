package com.lab.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import javax.validation.constraints.*;

@Data
@Schema(description = "操作审计分页查询")
public class OperationLogPageQuery {
    @NotNull @Min(1) @Max(1000000)
    private Long current = 1L;
    @NotNull @Min(1) @Max(100)
    private Long size = 10L;
    @Pattern(regexp = "^(user|lab|equipment|reservation|repair)$")
    private String module;
    @Min(0) @Max(1)
    private Integer success;
}
