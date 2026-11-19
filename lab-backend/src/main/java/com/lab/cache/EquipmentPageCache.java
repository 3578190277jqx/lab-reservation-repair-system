package com.lab.cache;

import cn.hutool.crypto.digest.DigestUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lab.dto.EquipmentPageQuery;
import com.lab.entity.Equipment;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.Arrays;
import java.util.UUID;
import java.util.function.Supplier;

/** 设备分页缓存：查询条件独立缓存，修改提交后切换版本，旧缓存由TTL清理。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EquipmentPageCache {

    private static final String VERSION_KEY = "lab:equipment:cache:version";
    private static final String PAGE_PREFIX = "lab:equipment:page:";
    private static final Duration TTL = Duration.ofMinutes(5);
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public Page<Equipment> getOrLoad(EquipmentPageQuery query, Supplier<Page<Equipment>> loader) {
        // 已有事务中直接读库，避免缓存事务内未提交的数据。
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            return loader.get();
        }
        String key;
        try {
            String version = currentVersion();
            String parameters = objectMapper.writeValueAsString(Arrays.asList(
                    query.getCurrent(), query.getSize(), query.getLabId(),
                    normalize(query.getEquipmentCode()), normalize(query.getName()),
                    normalize(query.getModel()), query.getStatus()));
            key = PAGE_PREFIX + version + ":" + DigestUtil.sha256Hex(parameters);
            String cached = redisTemplate.opsForValue().get(key);
            if (cached != null) {
                JavaType type = objectMapper.getTypeFactory()
                        .constructParametricType(Page.class, Equipment.class);
                return objectMapper.readValue(cached, type);
            }
        } catch (RuntimeException | JsonProcessingException ex) {
            log.warn("读取设备列表缓存失败，改为查询数据库：{}", ex.getClass().getSimpleName());
            return loader.get();
        }
        // 不捕获 loader 的业务异常，不重复执行查询。
        Page<Equipment> page = loader.get();
        try {
            // 回填读库前取得的版本，防止并发更新后旧查询污染新版本。
            redisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(page), TTL);
        } catch (RuntimeException | JsonProcessingException ex) {
            log.warn("写入设备列表缓存失败：{}", ex.getClass().getSimpleName());
        }
        return page;
    }

    public void evictAfterCommit() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    invalidate();
                }
            });
        } else {
            invalidate();
        }
    }

    private String currentVersion() {
        String version = redisTemplate.opsForValue().get(VERSION_KEY);
        if (version != null) {
            return version;
        }
        String initial = UUID.randomUUID().toString();
        if (Boolean.TRUE.equals(redisTemplate.opsForValue().setIfAbsent(VERSION_KEY, initial))) {
            return initial;
        }
        version = redisTemplate.opsForValue().get(VERSION_KEY);
        if (version == null) {
            throw new IllegalStateException("设备缓存版本初始化失败");
        }
        return version;
    }

    private void invalidate() {
        try {
            // 不扫描分页key；换代后旧key不再被新查询命中，5分钟后自动清理。
            redisTemplate.opsForValue().set(VERSION_KEY, UUID.randomUUID().toString());
        } catch (RuntimeException ex) {
            // DB已经提交；暂时失效失败时由TTL限制旧缓存生存时间。
            log.warn("设备已保存，但缓存失效失败，旧分页将在TTL到期后清理", ex);
        }
    }

    private String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
