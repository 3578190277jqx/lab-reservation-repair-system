package com.lab.service;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.redisson.api.RedissonClient;
import org.redisson.api.RLock;
import com.lab.common.*;
import java.util.concurrent.TimeUnit;
@Component
public class AdminMutationExecutor {
 private final RedissonClient redisson;private final TransactionTemplate tx;
 public AdminMutationExecutor(RedissonClient r,PlatformTransactionManager t){redisson=r;tx=new TransactionTemplate(t);tx.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);tx.setTimeout(30);}
 public void run(Runnable action){
  RLock lock=redisson.getLock("lab:admin:mutation");
  try{if(!lock.tryLock(3,TimeUnit.SECONDS))throw new BusinessException(ResultCode.CONFLICT);
   try{tx.execute(status->{action.run();return null;});}finally{if(lock.isHeldByCurrentThread())lock.unlock();}
  }catch(InterruptedException ex){Thread.currentThread().interrupt();throw new BusinessException(ResultCode.SERVICE_UNAVAILABLE);}
 }
}
