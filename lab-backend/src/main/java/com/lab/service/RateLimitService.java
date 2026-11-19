package com.lab.service;

import com.lab.common.BusinessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;
import java.util.Collections;
import cn.hutool.crypto.digest.DigestUtil;

/** Atomic fixed-window rate limit. Redis failure rejects expensive/public operations. */
@Service
@RequiredArgsConstructor
public class RateLimitService {
    private final StringRedisTemplate redis;
    private static final DefaultRedisScript<Long> LIMIT = new DefaultRedisScript<>(
        "local n=redis.call('INCR',KEYS[1]); if n==1 then redis.call('EXPIRE',KEYS[1],ARGV[1]) end; return n;", Long.class);
    public void check(String bucket, String identity, int maximum, int seconds) {
        Long n;
        try { n=redis.execute(LIMIT, Collections.singletonList("lab:rate:"+bucket+":"+DigestUtil.sha256Hex(identity)), String.valueOf(seconds)); }
        catch(RuntimeException ex){ throw new BusinessException(com.lab.common.ResultCode.SERVICE_UNAVAILABLE); }
        if(n==null || n>maximum) throw new BusinessException(com.lab.common.ResultCode.TOO_MANY_REQUESTS);
    }
}
