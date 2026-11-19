package com.lab.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.redisson.config.Config;
import org.redisson.config.SingleServerConfig;
import org.springframework.boot.autoconfigure.data.redis.RedisProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.util.StringUtils;

@Configuration
@EnableConfigurationProperties(RedisProperties.class)
public class RedisConfig {

    /** 字符串及 Set 操作直接使用本模板；对象缓存先转换为 JSON 字符串。 */
    @Bean
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory connectionFactory) {
        return new StringRedisTemplate(connectionFactory);
    }

    /** 与 Spring Redis 共用连接参数，用于设备预约分布式锁。 */
    @Bean(destroyMethod = "shutdown")
    public RedissonClient redissonClient(RedisProperties properties) {
        Config config = new Config();
        config.setThreads(4);
        config.setNettyThreads(4);
        config.setCodec(StringCodec.INSTANCE);
        config.setLockWatchdogTimeout(30000L);

        String protocol = properties.isSsl() ? "rediss://" : "redis://";
        SingleServerConfig server = config.useSingleServer()
                .setAddress(protocol + properties.getHost() + ":" + properties.getPort())
                .setDatabase(properties.getDatabase())
                .setConnectionMinimumIdleSize(2)
                .setConnectionPoolSize(16)
                .setSubscriptionConnectionMinimumIdleSize(1)
                .setSubscriptionConnectionPoolSize(4);

        if (properties.getTimeout() != null) {
            server.setTimeout(Math.toIntExact(properties.getTimeout().toMillis()));
        }
        if (properties.getConnectTimeout() != null) {
            server.setConnectTimeout(Math.toIntExact(properties.getConnectTimeout().toMillis()));
        }
        if (StringUtils.hasText(properties.getUsername())) {
            server.setUsername(properties.getUsername());
        }
        if (StringUtils.hasText(properties.getPassword())) {
            server.setPassword(properties.getPassword());
        }
        return Redisson.create(config);
    }
}
