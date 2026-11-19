package com.lab.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.net.HttpURLConnection;

/** 工单附件与百度识别配置；不新增 SDK 依赖。 */
@Configuration
@EnableConfigurationProperties({FileStorageProperties.class, BaiduAiProperties.class, QwenAiProperties.class})
public class RepairIntegrationConfig {

    @Bean("qwenImageRestTemplate")
    public RestTemplate qwenImageRestTemplate(QwenAiProperties properties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory() {
            @Override
            protected void prepareConnection(HttpURLConnection connection, String method) throws IOException {
                super.prepareConnection(connection, method);
                connection.setInstanceFollowRedirects(false);
            }
        };
        factory.setConnectTimeout(properties.getConnectTimeoutMs());
        factory.setReadTimeout(properties.getReadTimeoutMs());
        RestTemplate http = new RestTemplate(factory);
        http.setErrorHandler(new org.springframework.web.client.ResponseErrorHandler() {
            @Override public boolean hasError(org.springframework.http.client.ClientHttpResponse response) { return false; }
            @Override public void handleError(org.springframework.http.client.ClientHttpResponse response) { }
        });
        return http;
    }

    @Bean("baiduRestTemplate")
    public RestTemplate baiduRestTemplate(BaiduAiProperties properties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory() {
            @Override
            protected void prepareConnection(HttpURLConnection connection, String method) throws IOException {
                super.prepareConnection(connection, method);
                // 凭证和图片只发送到固定百度地址，不跟随重定向到其他服务器。
                connection.setInstanceFollowRedirects(false);
            }
        };
        factory.setConnectTimeout(properties.getConnectTimeoutMs());
        factory.setReadTimeout(properties.getReadTimeoutMs());
        return new RestTemplate(factory);
    }
}
