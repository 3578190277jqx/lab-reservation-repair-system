package com.lab.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.annotation.SaCheckRole;
import cn.dev33.satoken.annotation.SaMode;
import com.lab.audit.OperationAudit;
import com.lab.common.Result;
import com.lab.dto.EquipmentPageQuery;
import com.lab.transfer.EquipmentTransferService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import javax.validation.Valid;
import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/equipment")
@SaCheckLogin
@SaCheckRole(value={"admin","lab_admin"},mode=SaMode.OR)
@RequiredArgsConstructor
@Tag(name="设备Excel")
public class EquipmentTransferController {
    private final EquipmentTransferService transfer;
    @GetMapping("/export")
    @Operation(summary="导出授权范围设备，最多2000条")
    public ResponseEntity<byte[]> export(@Valid @ModelAttribute EquipmentPageQuery query) {
        return attachment("设备清单.xlsx",transfer.export(query));
    }
    @GetMapping("/import-template")
    @Operation(summary="下载7列设备导入模板")
    public ResponseEntity<byte[]> template(){return attachment("设备导入模板.xlsx",transfer.template());}
    @PostMapping(value="/import",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    @OperationAudit(module="equipment",action="import")
    @Operation(summary="整批新增设备，最大2MiB和500行，失败全部回滚")
    public Result<EquipmentTransferService.ImportResult> importFile(@RequestParam("file") MultipartFile file) {
        return Result.success(transfer.importFile(file));
    }
    private ResponseEntity<byte[]> attachment(String filename,byte[] content){
        return ResponseEntity.ok().contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .header(HttpHeaders.CONTENT_DISPOSITION,ContentDisposition.attachment().filename(filename,StandardCharsets.UTF_8).build().toString())
                .header(HttpHeaders.CACHE_CONTROL,"private, no-store").header("X-Content-Type-Options","nosniff")
                .body(content);
    }
}
