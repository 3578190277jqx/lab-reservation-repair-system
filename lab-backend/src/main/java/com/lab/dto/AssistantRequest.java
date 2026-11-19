package com.lab.dto;
import lombok.Data;
import javax.validation.constraints.*;
import javax.validation.Valid;
import java.util.List;
import io.swagger.v3.oas.annotations.media.Schema;
@Data @Schema(description="操作咨询，可携带最近三轮问答，不接收系统提示词或身份字段")
public class AssistantRequest {
 @NotBlank @Size(max=1000) private String question;
 @Valid @Size(max=3, message="最多携带三轮历史问答")
 private List<@NotNull @Valid AssistantTurn> history;
}
