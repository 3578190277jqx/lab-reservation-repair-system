package com.lab.utils;

import com.lab.common.BusinessException;
import com.lab.common.ResultCode;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/** 统一顺序：学生锁 → 设备锁 → 数据库事务 → 提交/回滚 → 逆序释放锁。 */
@Slf4j
@Component
public class ReservationLockExecutor {
    private final RedissonClient redissonClient;
    private final TransactionTemplate transactionTemplate;

    public ReservationLockExecutor(RedissonClient redissonClient,
                                   PlatformTransactionManager transactionManager) {
        this.redissonClient = redissonClient;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.transactionTemplate.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        this.transactionTemplate.setTimeout(30);
    }

    public <T> T execute(Long studentId, Long equipmentId, Supplier<T> action) {
        // 防止外层事务尚未提交就释放锁；调用方不得在本方法外包 @Transactional。
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("预约锁必须在数据库事务开始之前获取");
        }
        List<RLock> acquired = new ArrayList<>();
        try {
            // 学生锁串行化同一学生在不同设备上的爽约计数、入黑名单和申请。
            if (studentId != null) {
                acquire("reservation:student:lock:" + studentId, acquired);
            }
            // 与项目约定一致，所有同设备预约写操作共用此 key。
            acquire("equipment:lock:" + equipmentId, acquired);

            // execute 返回前已经提交或回滚，afterCommit 回调也在释放锁之前执行。
            return transactionTemplate.execute(status -> action.get());
        } finally {
            Collections.reverse(acquired);
            for (RLock lock : acquired) {
                try {
                    if (lock.isHeldByCurrentThread()) {
                        lock.unlock();
                    }
                } catch (RuntimeException ex) {
                    // 不用解锁异常覆盖已提交的结果；日志供运维排查，锁最终会过期。
                    log.error("预约分布式锁释放失败，key={}", lock.getName(), ex);
                }
            }
        }
    }

    private void acquire(String key, List<RLock> acquired) {
        try {
            RLock lock = redissonClient.getLock(key);
            // 只指定等待时间，不指定固定 leaseTime，启用 Redisson watchdog 自动续期。
            if (!lock.tryLock(3, TimeUnit.SECONDS)) {
                throw new BusinessException(ResultCode.CONFLICT, "操作频繁，请稍后重试");
            }
            acquired.add(lock);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new BusinessException(ResultCode.SERVICE_UNAVAILABLE, "操作被中断，请重试");
        } catch (BusinessException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            // 锁不可用时拒绝写入，绝不绕过并发保护继续创建预约。
            log.error("预约分布式锁获取失败，key={}", key, ex);
            throw new BusinessException(ResultCode.SERVICE_UNAVAILABLE, "预约服务暂时不可用，请稍后重试");
        }
    }
}
