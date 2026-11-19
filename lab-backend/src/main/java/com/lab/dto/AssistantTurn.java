package com.lab.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

/** 一轮成功问答。角色由服务端固定生成，客户端不能传入system/tool消息。 */
@Data
@Schema(description = "当前页面的一轮历史问答，不在服务器保存")
public class AssistantTurn {
    @NotBlank @Size(max = 1000)
    private String question;
    @NotBlank @Size(max = 3000)
    private String answer;
}
