package com.lab.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lab.config.BaiduAiProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 百度通用物体和场景识别客户端。
 * 该 API 识别的是物体标签，不是实验设备故障；故障大类仅由标签规则给出参考。
 * 外部调用必须在报修事务和设备锁之外完成，失败不影响提交或工单状态。
 * 官方文档：https://cloud.baidu.com/doc/IMAGERECOGNITION/s/Xk3bcxe21
 */
@Slf4j
@Service
public class BaiduImageRecognitionClient {

    private static final URI TOKEN_URI = URI.create("https://aip.baidubce.com/oauth/2.0/token");
    private static final String RECOGNITION_URL =
            "https://aip.baidubce.com/rest/2.0/image-classify/v2/advanced_general";
    private static final String UNAVAILABLE = "AI辅助暂不可用，请由维修人员人工确认故障大类。";
    private static final String UNKNOWN = "故障大类参考：待人工判断。图片标签不足以判定故障，需维修人员确认。";

    private final BaiduAiProperties properties;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private volatile CachedToken cachedToken;

    public BaiduImageRecognitionClient(BaiduAiProperties properties,
            @Qualifier("baiduRestTemplate") RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.properties = properties;
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    /** 只接收已经验证的本地图片字节，不接受客户端提供的远程 URL 或识别结论。 */
    public String suggest(byte[] image) {
        if (!properties.isEnabled()) {
            return "AI辅助未启用，请由维修人员人工确认故障大类。";
        }
        if (!StringUtils.hasText(properties.getApiKey()) || !StringUtils.hasText(properties.getSecretKey())) {
            return UNAVAILABLE;
        }
        if (image == null || image.length == 0 || image.length > LocalImageStorageService.MAX_IMAGE_BYTES) {
            return UNAVAILABLE;
        }
        try {
            // 最多调用两次识别：只有 token 失效时刷新并重试一次，其他错误直接降级。
            for (int attempt = 0; attempt < 2; attempt++) {
                String token = getAccessToken();
                JsonNode response = recognize(image, token);
                int errorCode = response.path("error_code").asInt(0);
                if ((errorCode == 110 || errorCode == 111) && attempt == 0) {
                    invalidateToken(token);
                    continue;
                }
                if (response.has("error_code") && errorCode != 0) {
                    log.warn("百度图像识别未成功，错误码={}", errorCode);
                    return UNAVAILABLE;
                }
                return toSuggestion(response.path("result"));
            }
        } catch (RuntimeException | IOException exception) {
            // 异常信息可能含请求 URL/token，因此仅记录异常类型，不打印消息、堆栈或响应正文。
            log.warn("百度图像识别调用失败，类型={}", exception.getClass().getSimpleName());
        }
        return UNAVAILABLE;
    }

    private JsonNode recognize(byte[] image, String token) throws IOException {
        URI uri = UriComponentsBuilder.fromHttpUrl(RECOGNITION_URL)
                .queryParam("access_token", token).build().encode().toUri();
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        // 5MiB 原图转 Base64 小于百度 8MiB 限制；Spring 表单转换器负责 URL 编码，勿手动二次编码。
        form.add("image", Base64.getEncoder().encodeToString(image));
        return postForm(uri, form);
    }

    private String getAccessToken() throws IOException {
        CachedToken current = cachedToken;
        if (current != null && current.validUntil > System.currentTimeMillis()) {
            return current.value;
        }
        synchronized (this) {
            current = cachedToken;
            if (current != null && current.validUntil > System.currentTimeMillis()) {
                return current.value;
            }
            MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
            form.add("grant_type", "client_credentials");
            form.add("client_id", properties.getApiKey());
            form.add("client_secret", properties.getSecretKey());
            JsonNode response = postForm(TOKEN_URI, form);
            String token = response.path("access_token").asText("");
            long expiresIn = response.path("expires_in").asLong(0L);
            if (response.has("error") || !StringUtils.hasText(token) || token.length() > 4096 || expiresIn <= 0) {
                throw new IllegalStateException("百度访问凭证获取失败");
            }
            // 提前60秒过期；最大缓存一年防止异常响应导致毫秒计算溢出。
            long cacheSeconds = Math.max(0L, Math.min(expiresIn, 365L * 24 * 3600) - 60L);
            cachedToken = new CachedToken(token, System.currentTimeMillis() + cacheSeconds * 1000L);
            return token;
        }
    }

    private synchronized void invalidateToken(String expiredToken) {
        // 另一个线程可能已更新凭证，不可用旧请求的失败结果清除新凭证。
        if (cachedToken != null && cachedToken.value.equals(expiredToken)) {
            cachedToken = null;
        }
    }

    private JsonNode postForm(URI uri, MultiValueMap<String, String> form) throws IOException {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headers.setAccept(java.util.Collections.singletonList(MediaType.APPLICATION_JSON));
        String response = restTemplate.postForObject(uri, new HttpEntity<>(form, headers), String.class);
        if (!StringUtils.hasText(response) || response.length() > 65536) {
            throw new IOException("百度响应为空或过大");
        }
        JsonNode json = objectMapper.readTree(response);
        if (json == null || !json.isObject()) {
            throw new IOException("百度响应格式不正确");
        }
        return json;
    }

    private String toSuggestion(JsonNode results) {
        if (!results.isArray()) {
            return UNKNOWN;
        }
        List<JsonNode> candidates = new ArrayList<>();
        // 官方最多返回5项；仍限制遍历数量，避免异常第三方响应占用过多资源。
        for (int index = 0; index < Math.min(results.size(), 20); index++) {
            JsonNode item = results.get(index);
            double score = item.path("score").asDouble(-1D);
            if (score >= 0.4D && score <= 1D && StringUtils.hasText(item.path("keyword").asText(""))) {
                candidates.add(item);
            }
        }
        candidates.sort(Comparator.comparingDouble((JsonNode node) -> node.path("score").asDouble()).reversed());
        if (candidates.isEmpty()) {
            return UNKNOWN;
        }
        List<String> labels = new ArrayList<>();
        Set<String> categories = new LinkedHashSet<>();
        for (int index = 0; index < Math.min(candidates.size(), 3); index++) {
            JsonNode item = candidates.get(index);
            String keyword = safeLabel(item.path("keyword").asText(""));
            String root = safeLabel(item.path("root").asText(""));
            labels.add(keyword + "（" + Math.round(item.path("score").asDouble() * 100) + "%）");
            String category = referenceCategory(keyword + " " + root);
            if (category != null) {
                categories.add(category);
            }
        }
        String categoryText = categories.isEmpty() ? "待人工判断" : String.join("、", categories);
        // 每项标签先截断，最终文本也限制长度以适配 repair_order.ai_suggestion 字段。
        String suggestion = "图像物体标签：" + String.join("、", labels)
                + "。故障大类参考：" + categoryText
                + "。以上为通用物体标签的规则映射，不能确认设备是否故障，需维修人员人工确认；不影响工单流程。";
        return suggestion.length() <= 2000 ? suggestion : suggestion.substring(0, 2000);
    }

    /** 简单可解释规则，不训练模型，也不把识别到设备等同于设备已损坏。 */
    private String referenceCategory(String label) {
        String text = label.toLowerCase(Locale.ROOT);
        if (containsAny(text, "显示器", "显示屏", "屏幕", "投影", "monitor", "display")) {
            return "显示/成像类";
        }
        if (containsAny(text, "电路", "电源", "电线", "插头", "电池", "电器", "电子", "circuit", "battery")) {
            return "电气/供电类";
        }
        if (containsAny(text, "机械", "齿轮", "轴承", "电机", "风扇", "螺丝", "五金", "motor", "gear")) {
            return "机械/结构类";
        }
        return null;
    }

    private boolean containsAny(String text, String... words) {
        for (String word : words) {
            if (text.contains(word)) {
                return true;
            }
        }
        return false;
    }

    private String safeLabel(String label) {
        String clean = label.replaceAll("[\\p{Cntrl}]", " ").trim();
        return clean.length() <= 80 ? clean : clean.substring(0, 80);
    }

    private static final class CachedToken {
        private final String value;
        private final long validUntil;

        private CachedToken(String value, long validUntil) {
            this.value = value;
            this.validUntil = validUntil;
        }
    }
}
