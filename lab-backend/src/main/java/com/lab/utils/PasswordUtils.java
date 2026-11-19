package com.lab.utils;

import cn.hutool.crypto.digest.BCrypt;
import com.lab.common.BusinessException;
import com.lab.common.ResultCode;

import java.nio.charset.StandardCharsets;

public final class PasswordUtils {

    private PasswordUtils() {
    }

    public static void validateLength(String rawPassword) {
        if (!isValidLength(rawPassword)) {
            throw new BusinessException(ResultCode.BAD_REQUEST,
                    "密码不能为空，且 UTF-8 编码长度不能超过72字节");
        }
    }

    public static String encode(String rawPassword) {
        validateLength(rawPassword);
        return BCrypt.hashpw(rawPassword, BCrypt.gensalt(12));
    }

    public static boolean matches(String rawPassword, String encodedPassword) {
        if (!isValidLength(rawPassword) || encodedPassword == null) {
            return false;
        }
        try {
            return BCrypt.checkpw(rawPassword, encodedPassword);
        } catch (IllegalArgumentException | IndexOutOfBoundsException ex) {
            return false;
        }
    }

    private static boolean isValidLength(String rawPassword) {
        return rawPassword != null && !rawPassword.isEmpty()
                && rawPassword.getBytes(StandardCharsets.UTF_8).length <= 72;
    }
}
