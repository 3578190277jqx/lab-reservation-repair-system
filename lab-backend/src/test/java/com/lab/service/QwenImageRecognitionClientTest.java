package com.lab.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lab.config.QwenAiProperties;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;
import java.util.Arrays;
import java.util.Collections;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class QwenImageRecognitionClientTest {
    private final QwenAiProperties properties = new QwenAiProperties();
    private final RestTemplate http = new RestTemplate();
    private final MockRestServiceServer server = MockRestServiceServer.createServer(http);
    private final QwenImageRecognitionClient client = new QwenImageRecognitionClient(properties, http, new ObjectMapper());
    private final byte[] png = {(byte)137,80,78,71,13,10,26,10,1};
    private void enable() { properties.setEnabled(true); properties.setApiKey("fixture-key-not-real"); }
    @Test void sendsVisionDataUrlAndBoundedNonThinkingRequest() {
        enable();
        server.expect(requestTo(properties.getEndpoint())).andExpect(method(HttpMethod.POST))
            .andExpect(header("Authorization", "Bearer fixture-key-not-real"))
            .andExpect(jsonPath("$.model").value("qwen3-vl-plus"))
            .andExpect(jsonPath("$.enable_thinking").value(false))
            .andExpect(jsonPath("$.stream").value(false))
            .andExpect(jsonPath("$.max_tokens").value(350))
            .andExpect(jsonPath("$.messages[1].content[0].type").value("image_url"))
            .andExpect(jsonPath("$.messages[1].content[0].image_url.url").value(org.hamcrest.Matchers.startsWith("data:image/png;base64,")))
            .andRespond(withSuccess("{\"choices\":[{\"message\":{\"content\":\"可见屏幕裂纹，显示成像类待人工确认\"}}]}", MediaType.APPLICATION_JSON));
        String result = client.suggest(png);
        assertTrue(result.startsWith("千问图片辅助"));
        assertTrue(result.contains("屏幕裂纹"));
        assertTrue(result.contains("仅供参考"));
        server.verify();
    }
    @Test void disabledMissingKeyAndInvalidImagesDoNotInvokeProvider() {
        assertFalse(client.suggest(png).startsWith("千问图片辅助"));
        properties.setEnabled(true);
        assertFalse(client.suggest(png).startsWith("千问图片辅助"));
        enable();
        for (byte[] image : Arrays.asList(null, new byte[0], new byte[]{1,2,3}, new byte[5 * 1024 * 1024 + 1])) {
            assertFalse(client.suggest(image).startsWith("千问图片辅助"));
        }
        server.verify();
    }
    @Test void jpegUsesTheCorrectMimeType() {
        enable();
        server.expect(requestTo(properties.getEndpoint()))
            .andExpect(jsonPath("$.messages[1].content[0].image_url.url").value(org.hamcrest.Matchers.startsWith("data:image/jpeg;base64,")))
            .andRespond(withSuccess("{\"choices\":[{\"message\":{\"content\":\"无法由图片确认故障\"}}]}", MediaType.APPLICATION_JSON));
        assertTrue(client.suggest(new byte[]{(byte)255,(byte)216,(byte)255,1}).startsWith("千问图片辅助"));
        server.verify();
    }
    @Test void invalidEndpointsNeverReceiveCredentials() {
        enable();
        for (String endpoint : Arrays.asList("http://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions", "https://evil.example/chat/completions", "https://dashscope.aliyuncs.com.evil.example/chat/completions", "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions?secret=1")) {
            properties.setEndpoint(endpoint);
            assertFalse(client.suggest(png).startsWith("千问图片辅助"));
        }
        server.verify();
    }
    @Test void failuresAndMalformedResponsesFallBackWithoutExposingDetails() {
        enable();
        server.expect(requestTo(properties.getEndpoint())).andRespond(withStatus(HttpStatus.UNAUTHORIZED).body("fixture-key-not-real upstream-secret"));
        server.expect(requestTo(properties.getEndpoint())).andRespond(withSuccess("not-json", MediaType.APPLICATION_JSON));
        server.expect(requestTo(properties.getEndpoint())).andRespond(withSuccess("{\"choices\":[]}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(properties.getEndpoint())).andRespond(withSuccess(String.join("", Collections.nCopies(70000,"x")), MediaType.APPLICATION_JSON));
        for (int i=0;i<4;i++) {
            String result = client.suggest(png);
            assertFalse(result.startsWith("千问图片辅助"));
            assertFalse(result.contains("upstream-secret"));
            assertFalse(result.contains("fixture-key"));
        }
        server.verify();
    }
    @Test void suggestionLengthIsBoundedAndThinkingContentIsExcluded() {
        enable();
        String longAnswer = String.join("", Collections.nCopies(2500, "字"));
        server.expect(requestTo(properties.getEndpoint())).andRespond(withSuccess("{\"choices\":[{\"message\":{\"content\":\"" + longAnswer + "\",\"reasoning_content\":\"private-thought\"}}]}", MediaType.APPLICATION_JSON));
        String result = client.suggest(png);
        assertTrue(result.startsWith("千问图片辅助"));
        assertTrue(result.length() <= 2000);
        assertTrue(result.contains("仅供参考"));
        assertFalse(result.contains("private-thought"));
        server.verify();
    }
    @Test void networkTimeoutFallsBackWithoutFailingTheRepairCaller() {
        enable();
        server.expect(requestTo(properties.getEndpoint())).andRespond(withException(new java.net.SocketTimeoutException("fixture timeout")));
        assertFalse(client.suggest(png).startsWith("千问图片辅助"));
        server.verify();
    }
}
