package com.lab.dto;
import lombok.Data;
import javax.validation.constraints.*;
@Data
public class RecoveryCodeRequest {
 @NotBlank @Pattern(regexp="register|recover|bind") private String purpose;
 @NotBlank @Pattern(regexp="email|phone") private String channel;
 @NotBlank @Size(max=32) @Pattern(regexp="^[A-Za-z0-9_]{4,32}$") private String username;
 @NotBlank @Size(max=128) private String target;
}
