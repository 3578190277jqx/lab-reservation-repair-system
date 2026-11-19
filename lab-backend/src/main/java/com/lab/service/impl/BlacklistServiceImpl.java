package com.lab.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lab.common.BusinessException;
import com.lab.common.ResultCode;
import com.lab.entity.Blacklist;
import com.lab.entity.Reservation;
import com.lab.mapper.BlacklistMapper;
import com.lab.mapper.ReservationMapper;
import com.lab.service.BlacklistService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Collections;

@Slf4j
@Service
@RequiredArgsConstructor
public class BlacklistServiceImpl extends ServiceImpl<BlacklistMapper, Blacklist>
        implements BlacklistService {
    private static final String KEY_PREFIX = "reservation:blacklist:";
    // Redis 7 的普通 Set 没有成员级 TTL，故每个学生一个 Set key，成员为学生ID。
    // Lua 将添加成员和设置绝对到期时间合为原子操作，避免进程中途退出留下永久黑名单。
    private static final DefaultRedisScript<Long> WRITE_SET = new DefaultRedisScript<>(
            "redis.call('SADD', KEYS[1], ARGV[1]); "
                    + "redis.call('PEXPIREAT', KEYS[1], ARGV[2]); return 1;", Long.class);

    private final ReservationMapper reservationMapper;
    private final StringRedisTemplate redisTemplate;
    private final Clock clock;

    @Override
    public void checkAllowed(Long studentId, LocalDateTime now) {
        boolean cached = false;
        try {
            cached = Boolean.TRUE.equals(redisTemplate.opsForSet()
                    .isMember(KEY_PREFIX + studentId, studentId.toString()));
        } catch (RuntimeException ex) {
            log.warn("黑名单缓存读取失败，使用数据库校验：{}", ex.getClass().getSimpleName());
        }
        // 以数据库为准：既防 Redis 丢失绕过封禁，也防解除封禁后旧缓存继续阻拦。
        Blacklist active = findActive(studentId, now);
        if (active != null) {
            if (!cached) {
                // 这条记录已经提交，可直接回填；不从当前时间重新计30天。
                cache(active);
            }
            throw new BusinessException(ResultCode.BLACKLIST_LIMITED);
        }
        if (cached) {
            try {
                redisTemplate.delete(KEY_PREFIX + studentId);
            } catch (RuntimeException ex) {
                log.warn("已失效黑名单缓存清理失败，数据库判定仍允许申请", ex);
            }
        }
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void onNoShow(Long studentId, LocalDateTime now) {
        // 调用方已持有学生分布式锁和用户行锁，不同设备的两次爽约也不会漏计。
        Long count = reservationMapper.selectCount(new LambdaQueryWrapper<Reservation>()
                .eq(Reservation::getStudentId, studentId).eq(Reservation::getStatus, 4));
        if (count < 2) {
            return;
        }
        Blacklist active = findActive(studentId, now);
        if (active == null) {
            // UNIQUE(student_id) includes expired/disabled/deleted rows. Lock and reuse it.
            active = baseMapper.lockAnyByStudent(studentId);
            boolean existing = active != null;
            if (!existing) active = new Blacklist();
            active.setStudentId(studentId);
            active.setReason("累计爽约达到2次，限制预约30天");
            active.setNoShowCount(Math.toIntExact(count));
            active.setStartTime(now);
            active.setExpireTime(now.plusDays(30));
            active.setStatus(1);
            active.setDeleted(0);
            if (existing) {
                if (baseMapper.reactivate(active, now) != 1) throw new IllegalStateException("黑名单更新失败");
            } else save(active);
        }
        // 已有生效记录则不延长封禁时间。先提交 MySQL，再同步 Redis。
        Blacklist committed = active;
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                cache(committed);
            }
        });
    }

    private Blacklist findActive(Long studentId, LocalDateTime now) {
        return baseMapper.selectOne(new LambdaQueryWrapper<Blacklist>()
                .eq(Blacklist::getStudentId, studentId).eq(Blacklist::getStatus, 1)
                .le(Blacklist::getStartTime, now).gt(Blacklist::getExpireTime, now)
                .orderByDesc(Blacklist::getExpireTime).last("LIMIT 1"));
    }

    private void cache(Blacklist entry) {
        try {
            long deadline = entry.getExpireTime().atZone(clock.getZone()).toInstant().toEpochMilli();
            redisTemplate.execute(WRITE_SET, Collections.singletonList(KEY_PREFIX + entry.getStudentId()),
                    entry.getStudentId().toString(), Long.toString(deadline));
        } catch (RuntimeException ex) {
            // 即使缓存同步失败，下一次申请仍会通过数据库校验被拒绝。
            log.warn("黑名单已持久化，Redis同步失败，studentId={}", entry.getStudentId(), ex);
        }
    }
}
