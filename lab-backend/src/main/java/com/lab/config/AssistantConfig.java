package com.lab.config;
import org.springframework.context.annotation.*;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;
import java.net.HttpURLConnection;
import java.io.IOException;
@Configuration @EnableConfigurationProperties(AssistantProperties.class)
public class AssistantConfig {
 @Bean("assistantHttp") public RestTemplate assistantHttp(){
  SimpleClientHttpRequestFactory f=new SimpleClientHttpRequestFactory(){
   @Override protected void prepareConnection(HttpURLConnection c,String method)throws IOException{super.prepareConnection(c,method);c.setInstanceFollowRedirects(false);}
  };f.setConnectTimeout(2000);f.setReadTimeout(12000);RestTemplate client=new RestTemplate(f);
  client.setErrorHandler(new org.springframework.web.client.ResponseErrorHandler(){
   public boolean hasError(org.springframework.http.client.ClientHttpResponse r){return false;}
   public void handleError(org.springframework.http.client.ClientHttpResponse r){}
  });return client;
 }
}
