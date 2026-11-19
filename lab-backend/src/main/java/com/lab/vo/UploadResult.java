package com.lab.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "图片上传结果；报修时传入 imageUrl")
public class UploadResult {

    @Schema(description = "需携带登录令牌访问的图片路径")
    private String imageUrl;

    @Schema(description = "服务器生成的安全文件名")
    private String fileName;

    @Schema(description = "重新编码后的图片字节数")
    private long size;
}
