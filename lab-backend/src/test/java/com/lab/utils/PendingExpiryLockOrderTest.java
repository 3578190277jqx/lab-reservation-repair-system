package com.lab.utils;

import com.lab.common.BusinessException;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Real executor against mocked distributed locks and transaction manager. */
class PendingExpiryLockOrderTest {
    @Test void studentThenEquipmentThenTransactionCommitsBeforeReverseUnlock() throws Exception {
        RedissonClient client=mock(RedissonClient.class);RLock student=mock(RLock.class),equipment=mock(RLock.class);
        PlatformTransactionManager tm=mock(PlatformTransactionManager.class);Runnable mutation=mock(Runnable.class);
        when(client.getLock("reservation:student:lock:7")).thenReturn(student);
        when(client.getLock("equipment:lock:10")).thenReturn(equipment);
        when(student.tryLock(3,TimeUnit.SECONDS)).thenReturn(true);when(equipment.tryLock(3,TimeUnit.SECONDS)).thenReturn(true);
        when(student.isHeldByCurrentThread()).thenReturn(true);when(equipment.isHeldByCurrentThread()).thenReturn(true);
        SimpleTransactionStatus status=new SimpleTransactionStatus();when(tm.getTransaction(any())).thenReturn(status);
        new ReservationLockExecutor(client,tm).execute(7L,10L,()->{mutation.run();return null;});
        InOrder order=inOrder(client,student,equipment,tm,mutation);
        order.verify(client).getLock("reservation:student:lock:7");order.verify(student).tryLock(3,TimeUnit.SECONDS);
        order.verify(client).getLock("equipment:lock:10");order.verify(equipment).tryLock(3,TimeUnit.SECONDS);
        order.verify(tm).getTransaction(any());order.verify(mutation).run();order.verify(tm).commit(status);
        order.verify(equipment).isHeldByCurrentThread();order.verify(equipment).unlock();
        order.verify(student).isHeldByCurrentThread();order.verify(student).unlock();
    }
    @Test void unavailableEquipmentLockReleasesStudentAndNeverStartsTransaction() throws Exception {
        RedissonClient client=mock(RedissonClient.class);RLock student=mock(RLock.class),equipment=mock(RLock.class);
        PlatformTransactionManager tm=mock(PlatformTransactionManager.class);Runnable mutation=mock(Runnable.class);
        when(client.getLock("reservation:student:lock:7")).thenReturn(student);
        when(client.getLock("equipment:lock:10")).thenReturn(equipment);
        when(student.tryLock(3,TimeUnit.SECONDS)).thenReturn(true);when(equipment.tryLock(3,TimeUnit.SECONDS)).thenReturn(false);
        when(student.isHeldByCurrentThread()).thenReturn(true);
        assertThrows(BusinessException.class,()->new ReservationLockExecutor(client,tm).execute(7L,10L,()->{mutation.run();return null;}));
        verify(student).unlock();verifyNoInteractions(tm,mutation);
    }
    @Test void failedMutationRollsBackBeforeUnlock() throws Exception {
        RedissonClient client=mock(RedissonClient.class);RLock student=mock(RLock.class),equipment=mock(RLock.class);
        PlatformTransactionManager tm=mock(PlatformTransactionManager.class);
        when(client.getLock("reservation:student:lock:7")).thenReturn(student);when(client.getLock("equipment:lock:10")).thenReturn(equipment);
        when(student.tryLock(3,TimeUnit.SECONDS)).thenReturn(true);when(equipment.tryLock(3,TimeUnit.SECONDS)).thenReturn(true);
        when(student.isHeldByCurrentThread()).thenReturn(true);when(equipment.isHeldByCurrentThread()).thenReturn(true);
        SimpleTransactionStatus status=new SimpleTransactionStatus();when(tm.getTransaction(any())).thenReturn(status);
        assertThrows(IllegalStateException.class,()->new ReservationLockExecutor(client,tm).execute(7L,10L,()->{throw new IllegalStateException("failed");}));
        InOrder order=inOrder(tm,equipment,student);order.verify(tm).getTransaction(any());order.verify(tm).rollback(status);
        order.verify(equipment).isHeldByCurrentThread();order.verify(equipment).unlock();order.verify(student).isHeldByCurrentThread();order.verify(student).unlock();
    }
}
