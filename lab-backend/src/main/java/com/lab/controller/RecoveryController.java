package com.lab.controller;
import com.lab.common.Result;
import com.lab.dto.*;
import com.lab.service.AccountRecoveryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import javax.validation.Valid;
import java.util.Map;
@RestController @RequestMapping("/user/recovery") @RequiredArgsConstructor
public class RecoveryController {
 private final AccountRecoveryService recovery;
 @GetMapping("/options") public Result<Map<String,Boolean>> options(){return Result.success(recovery.options());}
 @PostMapping("/code") public Result<Void> code(@Valid @RequestBody RecoveryCodeRequest q){recovery.send(q);return Result.success();}
 @PostMapping("/reset") public Result<Void> reset(@Valid @RequestBody ResetPasswordRequest q){recovery.reset(q);return Result.success();}
 @PostMapping("/bind") public Result<Void> bind(@Valid @RequestBody BindContactRequest q){recovery.bind(q);return Result.success();}
}
