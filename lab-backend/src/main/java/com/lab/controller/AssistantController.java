package com.lab.controller;
import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.stp.StpUtil;
import com.lab.common.Result;
import com.lab.dto.AssistantRequest;
import com.lab.service.AssistantService;
import com.lab.vo.AssistantAnswer;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.tags.Tag;
import javax.validation.Valid;
import java.util.Collections;
import java.util.Map;
@RestController @RequestMapping("/assistant") @SaCheckLogin @RequiredArgsConstructor @Tag(name="操作助手")
public class AssistantController {
 private final AssistantService assistant;
 @GetMapping("/options") public Result<Map<String,Boolean>> options(){return Result.success(Collections.singletonMap("enabled",assistant.available()));}
 @PostMapping("/ask") public Result<AssistantAnswer> ask(@Valid @RequestBody AssistantRequest request){return Result.success(assistant.ask(StpUtil.getLoginIdAsLong(),request.getQuestion().trim(),request.getHistory()));}
}
