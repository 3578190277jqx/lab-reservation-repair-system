package com.lab.dto;
import lombok.Data;
import lombok.ToString;
import javax.validation.constraints.*;
@Data
public class ResetPasswordRequest {
 @NotBlank @Pattern(regexp="^[A-Za-z0-9_]{4,32}$") private String username;
 @NotBlank @Pattern(regexp="email|phone") private String channel;
 @NotBlank @Size(max=128) private String target;
 @NotBlank @Pattern(regexp="^[0-9]{6}$") @ToString.Exclude private String code;
 @NotBlank @Size(min=8,max=64) @ToString.Exclude private String newPassword;
}
