package com.lab.utils;

import cn.hutool.crypto.digest.BCrypt;
import java.io.BufferedReader;
import java.io.Console;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/** Deployment-only CLI. Generates a BCrypt hash without starting Spring or a DB. */
public final class PasswordHashTool {
    private PasswordHashTool() { }

    public static void main(String[] args) throws Exception {
        char[] password = null;
        char[] confirmation = null;
        try {
            if (args.length == 1 && "--stdin".equals(args[0])) {
                // For automation: two lines via stdin, never a command-line password.
                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(System.in, StandardCharsets.UTF_8));
                String first = reader.readLine();
                String second = reader.readLine();
                password = first == null ? null : first.toCharArray();
                confirmation = second == null ? null : second.toCharArray();
            } else {
                Console console = System.console();
                if (console == null) {
                    throw new IllegalArgumentException("Use docker run --rm -it to enable hidden password input.");
                }
                password = console.readPassword("New admin password (12-72 UTF-8 bytes): ");
                confirmation = console.readPassword("Confirm password: ");
            }
            if (password == null || confirmation == null || !Arrays.equals(password, confirmation)) {
                throw new IllegalArgumentException("Passwords are missing or do not match.");
            }
            String raw = new String(password);
            int bytes = raw.getBytes(StandardCharsets.UTF_8).length;
            if (bytes < 12 || bytes > 72 || password.length > 64) {
                throw new IllegalArgumentException("Use 12-72 UTF-8 bytes and at most 64 characters.");
            }
            System.out.println(BCrypt.hashpw(raw, BCrypt.gensalt(12)));
        } finally {
            if (password != null) Arrays.fill(password, '\0');
            if (confirmation != null) Arrays.fill(confirmation, '\0');
        }
    }
}
