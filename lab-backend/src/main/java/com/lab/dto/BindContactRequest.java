package com.lab.dto;
import lombok.Data;
import lombok.ToString;
import javax.validation.constraints.*;
@Data
public class BindContactRequest {
 @NotBlank @Pattern(regexp="email|phone") private String channel;
 @NotBlank @Size(max=128) private String target;
 @NotBlank @Pattern(regexp="^[0-9]{6}$") @ToString.Exclude private String code;
 @NotBlank @Size(max=64) @ToString.Exclude private String currentPassword;
}
