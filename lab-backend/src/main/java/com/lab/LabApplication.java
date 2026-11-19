package com.lab;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.TimeZone;

@SpringBootApplication
public class LabApplication {

    public static void main(String[] args) {
        // 与数据库预约时间、签到时间使用相同的时区。
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Shanghai"));
        SpringApplication.run(LabApplication.class, args);
    }
}
