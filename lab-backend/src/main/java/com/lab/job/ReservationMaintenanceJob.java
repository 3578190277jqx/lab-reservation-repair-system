package com.lab.job;

import com.lab.service.ReservationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 预约维护任务。
 * 每个应用实例均可运行本任务；服务层通过分布式锁和状态条件更新保证幂等。
 * 批次使用ID游标，不使用offset，避免处理后记录退出结果集导致后续记录被跳过。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.reservation.scheduling-enabled", havingValue = "true", matchIfMissing = true)
public class ReservationMaintenanceJob {

    private static final int BATCH_SIZE = 100;
    private static final int MAX_BATCHES_PER_SWEEP = 20;

    private final ReservationService reservationService;

    @Scheduled(fixedDelayString = "${app.reservation.scan-delay-ms:60000}",
            initialDelayString = "${app.reservation.scan-initial-delay-ms:10000}")
    public void sweep() {
        // 各项维护分别捕获异常，某一项暂时失败不妨碍其他维护继续运行。
        try {
            processPendingExpiries();
        } catch (RuntimeException ex) {
            log.error("扫描审核超时预约失败，将在下一轮重试", ex);
        }
        try {
            processNoShows();
        } catch (RuntimeException ex) {
            log.error("扫描到期未签到预约失败，将在下一轮重试", ex);
        }
        try {
            releaseFinishedEquipment();
        } catch (RuntimeException ex) {
            log.error("扫描已结束预约的设备失败，将在下一轮重试", ex);
        }
    }

    private void processPendingExpiries() {
        long afterId = 0L;
        for (int batch = 0; batch < MAX_BATCHES_PER_SWEEP; batch++) {
            List<Long> ids = reservationService.findDuePendingExpiryIds(afterId, BATCH_SIZE);
            if (ids.isEmpty()) return;
            for (Long reservationId : ids) {
                try {
                    reservationService.processPendingExpiry(reservationId);
                } catch (RuntimeException ex) {
                    log.error("处理审核超时失败，reservationId={}，将在下一轮重试", reservationId, ex);
                }
            }
            afterId = ids.get(ids.size() - 1);
            if (ids.size() < BATCH_SIZE) return;
        }
    }

    private void processNoShows() {
        long afterId = 0L;
        for (int batch = 0; batch < MAX_BATCHES_PER_SWEEP; batch++) {
            // 第一步：查询一批已到爽约判定时间、仍未签到的预约ID。
            List<Long> ids = reservationService.findDueNoShowIds(afterId, BATCH_SIZE);
            if (ids.isEmpty()) {
                return;
            }
            // 第二步：每条记录独立加锁、开启事务；一条失败不影响本批其他记录。
            for (Long reservationId : ids) {
                try {
                    reservationService.processNoShow(reservationId);
                } catch (RuntimeException ex) {
                    log.error("处理爽约失败，reservationId={}，将在下一轮重试", reservationId, ex);
                }
            }
            // 第三步：推进游标；下一轮扫描重新从0开始，失败记录仍有重试机会。
            afterId = ids.get(ids.size() - 1);
            if (ids.size() < BATCH_SIZE) {
                return;
            }
        }
    }

    private void releaseFinishedEquipment() {
        long afterId = 0L;
        for (int batch = 0; batch < MAX_BATCHES_PER_SWEEP; batch++) {
            // 只查询候选设备；是否可释放必须在服务层设备锁内重新判断。
            List<Long> ids = reservationService.findFinishedEquipmentIds(afterId, BATCH_SIZE);
            if (ids.isEmpty()) {
                return;
            }
            for (Long equipmentId : ids) {
                try {
                    reservationService.releaseFinishedEquipment(equipmentId);
                } catch (RuntimeException ex) {
                    log.error("释放设备预约状态失败，equipmentId={}，将在下一轮重试", equipmentId, ex);
                }
            }
            afterId = ids.get(ids.size() - 1);
            if (ids.size() < BATCH_SIZE) {
                return;
            }
        }
    }
}
