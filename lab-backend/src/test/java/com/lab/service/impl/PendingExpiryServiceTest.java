package com.lab.service.impl;

import cn.dev33.satoken.stp.StpLogic;
import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.lab.cache.EquipmentPageCache;
import com.lab.common.BusinessException;
import com.lab.common.ResultCode;
import com.lab.dto.AuditRequest;
import com.lab.dto.CancelReservationRequest;
import com.lab.entity.*;
import com.lab.mapper.*;
import com.lab.service.BlacklistService;
import com.lab.service.UserService;
import com.lab.utils.ReservationLockExecutor;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.io.Serializable;
import java.time.*;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.regex.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Real threads with deterministic in-memory test locking and mock persistence; not MySQL/Redis integration. */
class PendingExpiryServiceTest {
    private static final LocalDateTime START=LocalDateTime.of(2026,11,3,9,0);
    private static final ZoneId ZONE=ZoneId.of("Asia/Shanghai");
    private static class Fixture {
        final AtomicInteger status=new AtomicInteger(0);
        final Object lockMonitor=new Object();
        final ReservationMapper rm=mock(ReservationMapper.class);
        final EquipmentMapper em=mock(EquipmentMapper.class);
        final LabMapper lm=mock(LabMapper.class);
        final ReservationLockExecutor lock=mock(ReservationLockExecutor.class);
        final BlacklistService blacklist=mock(BlacklistService.class);
        final EquipmentPageCache cache=mock(EquipmentPageCache.class);
        final UserService users=mock(UserService.class);
        final ReservationServiceImpl service;
        volatile String lastSet="";
        volatile Map<String,Object> lastParams;

        Fixture(LocalDateTime now) {this(Clock.fixed(now.atZone(ZONE).toInstant(),ZONE));}
        Fixture(Clock clock) {
            MapperBuilderAssistant assistant=new MapperBuilderAssistant(new MybatisConfiguration(),"expiry");
            for(Class<?> type:new Class<?>[]{Equipment.class,Lab.class,Reservation.class}) TableInfoHelper.initTableInfo(assistant,type);
            Equipment equipment=new Equipment();equipment.setId(10L);equipment.setLabId(3L);equipment.setStatus(0);
            Lab lab=new Lab();lab.setId(3L);lab.setStatus(1);
            User student=new User();student.setRole("student");
            when(rm.selectById(any(Serializable.class))).thenAnswer(i->row());
            when(rm.selectOne(any())).thenAnswer(i->row());
            when(rm.lockStudentRow(7L)).thenReturn(7L);
            when(rm.selectCount(any())).thenReturn(0L);
            when(em.selectById(10L)).thenReturn(equipment);when(em.selectOne(any())).thenReturn(equipment);
            when(lm.selectOne(any())).thenReturn(lab);when(lm.selectById(3L)).thenReturn(lab);
            when(users.requireActiveUser(7L)).thenReturn(student);
            when(lock.execute(anyLong(),anyLong(),any())).thenAnswer(i->{
                synchronized(lockMonitor) {return ((Supplier<?>)i.getArgument(2)).get();}
            });
            when(rm.update(isNull(),any())).thenAnswer(i->{
                @SuppressWarnings("unchecked") LambdaUpdateWrapper<Reservation> w=i.getArgument(1);
                String set=w.getSqlSet();w.getSqlSegment();lastSet=set;lastParams=w.getParamNameValuePairs();
                Matcher m=Pattern.compile("status=#\\{ew\\.paramNameValuePairs\\.(\\w+)\\}").matcher(set);
                if(!m.find()) throw new AssertionError("Missing status assignment: "+set);
                int target=((Number)lastParams.get(m.group(1))).intValue();
                return status.compareAndSet(0,target)?1:0;
            });
            service=new ReservationServiceImpl(lock,em,lm,mock(UsageLogMapper.class),blacklist,users,cache,clock);
            ReflectionTestUtils.setField(service,"baseMapper",rm);
        }
        Reservation row(){Reservation r=new Reservation();r.setId(1L);r.setStudentId(7L);r.setEquipmentId(10L);r.setStatus(status.get());r.setStartTime(START);r.setEndTime(START.plusHours(8));return r;}
        void approve(){AuditRequest r=new AuditRequest();r.setReservationId(1L);r.setApproved(true);service.audit(r);}
        void reject(){AuditRequest r=new AuditRequest();r.setReservationId(1L);r.setApproved(false);r.setRemark("人工拒绝");service.audit(r);}
        void cancel(){CancelReservationRequest r=new CancelReservationRequest();r.setReservationId(1L);service.cancel(r);}
    }
    private void withAuth(Runnable body) {
        StpLogic previous=StpUtil.stpLogic, auth=mock(StpLogic.class);
        StpUtil.stpLogic=auth;when(auth.hasRole("admin")).thenReturn(true);when(auth.getLoginIdAsLong()).thenReturn(7L);
        try {body.run();} finally {StpUtil.stpLogic=previous;}
    }
    @Test void atStartExpiresOnceWithoutAuditorOrNoShow() {
        withAuth(()->{
            Fixture before=new Fixture(START.minusSeconds(1));before.service.processPendingExpiry(1L);
            assertEquals(0,before.status.get());verify(before.rm,never()).update(isNull(),any());
            Fixture f=new Fixture(START);f.service.processPendingExpiry(1L);
            assertEquals(5,f.status.get());assertTrue(f.lastSet.contains("audit_remark"));
            assertTrue(f.lastParams.containsValue("预约开始仍未审核，系统自动关闭"));
            assertTrue(f.lastParams.containsValue(START));assertTrue(f.lastSet.contains("audit_time"));
            assertTrue(f.lastSet.contains("audit_by"));assertTrue(f.lastParams.containsValue(null));assertFalse(f.lastSet.contains("no_show"));
            f.service.processPendingExpiry(1L);verify(f.rm,times(1)).update(isNull(),any());
            verifyNoInteractions(f.blacklist);verify(f.em,never()).update(isNull(),any());verifyNoInteractions(f.cache);
        });
    }
    @Test void lockFailureNeverMutatesAndAlreadyHandledStatesRemainUntouched() {
        withAuth(()->{
            Fixture f=new Fixture(START.plusSeconds(1));
            doThrow(new BusinessException(ResultCode.SERVICE_UNAVAILABLE)).when(f.lock).execute(anyLong(),anyLong(),any());
            assertThrows(BusinessException.class,()->f.service.processPendingExpiry(1L));
            verify(f.rm,never()).update(isNull(),any());assertEquals(0,f.status.get());
            for(int current:new int[]{1,2,3,4,5}) {Fixture done=new Fixture(START.plusSeconds(1));done.status.set(current);done.service.processPendingExpiry(1L);assertEquals(current,done.status.get());verify(done.rm,never()).update(isNull(),any());}
        });
    }
    @Test void simultaneousMaintenanceApprovalRejectionAndCancellationRespectLockAndBoundary() {
        withAuth(()->{
            for(String competitor:new String[]{"approve","reject","cancel"}) {
                for(int run=0;run<12;run++) {
                    Fixture f=new Fixture(START);ExecutorService pool=Executors.newFixedThreadPool(2);CountDownLatch go=new CountDownLatch(1);
                    try {
                        Future<?> a=pool.submit(()->{await(go);f.service.processPendingExpiry(1L);});
                        Future<?> b=pool.submit(()->{await(go);try {if(competitor.equals("approve"))f.approve();else if(competitor.equals("reject"))f.reject();else f.cancel();}catch(BusinessException expected){}});
                        go.countDown();a.get(5,TimeUnit.SECONDS);b.get(5,TimeUnit.SECONDS);
                        if(competitor.equals("reject"))assertTrue(f.status.get()==2||f.status.get()==5);
                        else assertEquals(5,f.status.get());
                        verify(f.rm,atMost(1)).update(isNull(),any());verifyNoInteractions(f.blacklist);
                    } catch(Exception ex) {throw new AssertionError(ex);} finally {pool.shutdownNow();}
                }
            }
        });
    }
    @Test void clockIsReadAfterWaitingForLocksAcrossStartBoundary() {
        withAuth(()->{
            for(boolean approval:new boolean[]{false,true}) {
                AtomicReference<Instant> time=new AtomicReference<>(START.minusSeconds(1).atZone(ZONE).toInstant());
                Clock moving=new Clock(){public ZoneId getZone(){return ZONE;}public Clock withZone(ZoneId z){throw new UnsupportedOperationException();}public Instant instant(){return time.get();}};
                Fixture f=new Fixture(moving);CountDownLatch entered=new CountDownLatch(1),released=new CountDownLatch(1);
                doAnswer(i->{entered.countDown();await(released);return ((Supplier<?>)i.getArgument(2)).get();}).when(f.lock).execute(anyLong(),anyLong(),any());
                ExecutorService pool=Executors.newSingleThreadExecutor();
                try {
                    Future<?> result=pool.submit(()->{if(approval)f.approve();else f.service.processPendingExpiry(1L);});
                    assertTrue(entered.await(5,TimeUnit.SECONDS));time.set(START.atZone(ZONE).toInstant());released.countDown();
                    if(approval){ExecutionException e=assertThrows(ExecutionException.class,()->result.get(5,TimeUnit.SECONDS));assertTrue(e.getCause() instanceof BusinessException);assertEquals(0,f.status.get());}
                    else {result.get(5,TimeUnit.SECONDS);assertEquals(5,f.status.get());}
                }catch(InterruptedException e){Thread.currentThread().interrupt();throw new AssertionError(e);}
                catch(ExecutionException|TimeoutException e){throw new AssertionError(e);}
                finally {released.countDown();pool.shutdownNow();}
            }
        });
    }
    private static void await(CountDownLatch latch){try {latch.await();}catch(InterruptedException e){Thread.currentThread().interrupt();throw new AssertionError(e);}}
}
