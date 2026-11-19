package com.lab.service;

import com.lab.config.BaiduAiProperties;
import com.lab.config.QwenAiProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AiSuggestionServiceTest {
    private final BaiduImageRecognitionClient baidu = mock(BaiduImageRecognitionClient.class);
    private final QwenImageRecognitionClient qwen = mock(QwenImageRecognitionClient.class);
    private final BaiduAiProperties baiduProperties = new BaiduAiProperties();
    private final QwenAiProperties qwenProperties = new QwenAiProperties();
    private final RedissonClient redisson = mock(RedissonClient.class);
    private final RLock lock = mock(RLock.class);
    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> values = mock(ValueOperations.class);
    private final RateLimitService limiter = mock(RateLimitService.class);
    private final byte[] image = {1, 2, 3};
    private final AiSuggestionService service = new AiSuggestionService(baidu, baiduProperties, qwen, qwenProperties, redisson, redis, limiter);
    private static final String SUGGESTION = "千问图片辅助：待人工确认，仅供参考。";

    @BeforeEach void configure() throws Exception {
        qwenProperties.setEnabled(true);
        when(redisson.getLock(anyString())).thenReturn(lock);
        when(lock.tryLock(0, TimeUnit.SECONDS)).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);
        when(redis.opsForValue()).thenReturn(values);
        when(qwen.suggest(image)).thenReturn(SUGGESTION);
    }
    @AfterEach void close() { service.destroy(); }
    @Test void qwenTakesPriorityAndUsesProviderSpecificCache() {
        baiduProperties.setEnabled(true);
        assertEquals(SUGGESTION, service.suggest(1L, 7L, image));
        verifyNoInteractions(baidu);
        verify(qwen).suggest(image);
        verify(limiter).check("ai-user-day", "7", 30, 86400);
        verify(limiter).check("ai-global-day", "all", 200, 86400);
        verify(values).set(startsWith("lab:ai:result:qwen:"), eq(SUGGESTION), eq(Duration.ofMinutes(10)));
        verify(lock).unlock();
    }
    @Test void modelChangesDoNotReuseThePreviousCacheNamespace() {
        service.suggest(1L, 7L, image);
        qwenProperties.setModel("qwen3-vl-flash");
        service.suggest(1L, 7L, image);
        ArgumentCaptor<String> keys = ArgumentCaptor.forClass(String.class);
        verify(values, times(2)).get(keys.capture());
        assertNotEquals(keys.getAllValues().get(0), keys.getAllValues().get(1));
    }
    @Test void cacheHitDoesNotConsumeAnotherModelCallOrQuota() {
        when(values.get(anyString())).thenReturn(SUGGESTION);
        assertEquals(SUGGESTION, service.suggest(1L, 7L, image));
        verifyNoInteractions(qwen, baidu, limiter);
    }
    @Test void temporaryQwenFailureIsNotCachedAndDoesNotCallBaidu() {
        when(qwen.suggest(image)).thenReturn("AI辅助暂不可用，请人工确认故障；不影响报修。");
        assertTrue(service.suggest(1L, 7L, image).contains("不影响报修"));
        verify(values, never()).set(anyString(), anyString(), any(Duration.class));
        verifyNoInteractions(baidu);
    }
    @Test void turningBothOffSkipsTheQueueLockAndQuota() {
        qwenProperties.setEnabled(false);
        when(baidu.suggest(image)).thenReturn("AI辅助未启用");
        assertEquals("AI辅助未启用", service.suggest(1L, 7L, image));
        verifyNoInteractions(qwen, redisson, redis, limiter);
    }
    @Test void explicitlyConfiguredBaiduStillWorksWithItsOwnCacheKey() {
        qwenProperties.setEnabled(false);
        baiduProperties.setEnabled(true);
        when(baidu.suggest(image)).thenReturn("图像标签待人工确认");
        assertEquals("图像标签待人工确认", service.suggest(1L, 7L, image));
        verifyNoInteractions(qwen);
        verify(values).get(startsWith("lab:ai:result:baidu:"));
    }
}
