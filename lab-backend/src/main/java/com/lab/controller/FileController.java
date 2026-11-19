package com.lab.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.stp.StpUtil;
import com.lab.common.Result;
import com.lab.service.LocalImageStorageService;
import com.lab.service.RepairOrderService;
import com.lab.vo.UploadResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Tag(name = "工单图片")
@RestController
@RequestMapping("/file")
@RequiredArgsConstructor
@SaCheckLogin
public class FileController {

    private final LocalImageStorageService imageStorageService;
    private final RepairOrderService repairOrderService;

    @Operation(summary = "上传报修图片", description = "JPEG/PNG，最大5MiB；验证真实图像并重新编码后保存")
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<UploadResult> upload(@RequestPart("file") MultipartFile file) {
        return Result.success(imageStorageService.upload(file, StpUtil.getLoginIdAsLong()));
    }

    @Operation(summary = "读取工单图片", description = "本人上传图片，或管理员有权处理的工单图片；请求必须携带令牌")
    @GetMapping("/images/{ownerId}/{filename}")
    public ResponseEntity<byte[]> image(@PathVariable Long ownerId, @PathVariable String filename) {
        String imageUrl = "/file/images/" + ownerId + "/" + filename;
        // 先验证业务访问权限，再读取磁盘文件，不能仅凭知道 URL 就读取他人图片。
        repairOrderService.checkImageReadAccess(imageUrl);
        byte[] bytes = imageStorageService.readImage(imageUrl);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(imageStorageService.contentType(imageUrl)))
                .contentLength(bytes.length)
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .header("X-Content-Type-Options", "nosniff")
                .body(bytes);
    }
}
