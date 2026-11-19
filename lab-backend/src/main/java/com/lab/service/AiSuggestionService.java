package com.lab.service;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.DisposableBean;
import org.redisson.api.RedissonClient;
import org.redisson.api.RLock;
import org.springframework.data.redis.core.StringRedisTemplate;
import cn.hutool.crypto.digest.DigestUtil;
import java.util.concurrent.*;
import java.time.Duration;
@Service @RequiredArgsConstructor
public class AiSuggestionService implements DisposableBean {
 private final BaiduImageRecognitionClient client;
 private final com.lab.config.BaiduAiProperties properties;
 private final QwenImageRecognitionClient qwenClient;
 private final com.lab.config.QwenAiProperties qwenProperties;
 private final RedissonClient redisson;
 private final StringRedisTemplate redis;
 private final RateLimitService limiter;
 private final ThreadPoolExecutor executor=new ThreadPoolExecutor(2,2,0,TimeUnit.SECONDS,
  new ArrayBlockingQueue<Runnable>(4),r->{Thread t=new Thread(r,"lab-ai");t.setDaemon(true);return t;},new ThreadPoolExecutor.AbortPolicy());
 public String suggest(Long equipmentId,Long userId,byte[] image){
  boolean useQwen=qwenProperties.isEnabled();
  if(!useQwen&&!properties.isEnabled())return client.suggest(image);
  try{
   Future<String> future=executor.submit(()->{
    RLock lock=redisson.getLock("repair:ai:lock:"+equipmentId);
    if(!lock.tryLock(0,TimeUnit.SECONDS))return "该设备识别处理中，本次请人工判断；不影响报修。";
    try{
     String provider=useQwen?"qwen:"+DigestUtil.sha256Hex(qwenProperties.getEndpoint()+"|"+qwenProperties.getModel()).substring(0,16):"baidu";
     String key="lab:ai:result:"+provider+":"+equipmentId+":"+DigestUtil.sha256Hex(image);
     String cached=redis.opsForValue().get(key);if(cached!=null)return cached;
     limiter.check("ai-user-day",String.valueOf(userId),30,86400);
     limiter.check("ai-global-day","all",200,86400);
     String result=useQwen?qwenClient.suggest(image):client.suggest(image);
     if(!useQwen||result.startsWith("千问图片辅助："))redis.opsForValue().set(key,result,Duration.ofMinutes(10));return result;
    }finally{if(lock.isHeldByCurrentThread())lock.unlock();}
   });
   try{return future.get(10,TimeUnit.SECONDS);}catch(TimeoutException ex){future.cancel(true);return "AI识别超时，请人工确认；报修正常提交。";}
  }catch(InterruptedException ex){Thread.currentThread().interrupt();return "AI辅助暂不可用，请人工确认。";}
  catch(ExecutionException|RejectedExecutionException ex){return "AI辅助额度不足或暂不可用，请人工确认；不影响报修。";}
 }
 @Override public void destroy(){executor.shutdownNow();}
}
