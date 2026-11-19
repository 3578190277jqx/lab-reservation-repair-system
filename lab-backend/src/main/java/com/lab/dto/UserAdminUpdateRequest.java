package com.lab.dto;
import lombok.Data;
import javax.validation.constraints.*;
@Data
public class UserAdminUpdateRequest {
 @NotBlank @Size(max=64) private String realName;
 @Size(max=32) private String studentNo;
 @Pattern(regexp="^$|^1[3-9]\\d{9}$") private String phone;
 @Email @Size(max=128) private String email;
 @NotNull @Min(0) @Max(1) private Integer status;
}
