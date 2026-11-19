package com.lab.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import java.time.Clock;
import java.time.ZoneId;

@Configuration
@EnableScheduling
public class ReservationSchedulingConfig {
    /** 注入时钟，统一业务时区，并便于测试签到和封禁边界。 */
    @Bean
    public Clock reservationClock() {
        return Clock.system(ZoneId.of("Asia/Shanghai"));
    }
}
