package com.lab.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 事务提交后，将事件发布为站内可见；不会发送邮件，也不代表学生已阅读。
 * 类名保留历史名称以兼容已有调用；条件更新使多个扫描实例只发布一次。
 */
@Slf4j
@Component
public class FaultNotificationDelivery {
    private final JdbcTemplate jdbc;
    private final Clock clock;
    public FaultNotificationDelivery(JdbcTemplate jdbc,Clock clock){this.jdbc=jdbc;this.clock=clock;}

    @Scheduled(fixedDelayString="${app.fault-notification.scan-delay-ms:60000}")
    public void publishDue() {
        LocalDateTime now=LocalDateTime.now(clock);Timestamp at=Timestamp.valueOf(now);
        List<Long> ids=jdbc.queryForList("SELECT id FROM fault_notification_outbox WHERE delivery_status IN (0,2) AND (next_attempt_at IS NULL OR next_attempt_at<=?) ORDER BY id LIMIT 100",Long.class,at);
        for(Long id:ids) {
            try {
                jdbc.update("UPDATE fault_notification_outbox SET delivery_status=1,attempts=attempts+1,next_attempt_at=NULL,updated_at=? WHERE id=? AND delivery_status IN (0,2) AND (next_attempt_at IS NULL OR next_attempt_at<=?)",at,id,at);
            } catch (RuntimeException ex) {
                log.warn("站内通知发布状态更新失败，eventId={}",id,ex);
                try {
                    jdbc.update("UPDATE fault_notification_outbox SET delivery_status=2,attempts=attempts+1,next_attempt_at=?,updated_at=? WHERE id=? AND delivery_status IN (0,2)",Timestamp.valueOf(now.plusMinutes(1)),at,id);
                } catch (RuntimeException storageUnavailable) {
                    // 持久层不可用时，事件仍不可见，后续扫描继续检查。
                    log.warn("无法记录发布状态更新异常，事件保留待后续扫描，eventId={}",id,storageUnavailable);
                }
            }
        }
    }

    /** 兼容历史方法名称；它只更新站内可见状态，不进行外部投递。 */
    @Deprecated
    public void deliverDue() {
        publishDue();
    }
}
