package com.lab.service.impl;
import com.lab.mapper.*;
import com.lab.entity.*;
import org.junit.jupiter.api.*;
import org.mockito.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.time.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.junit.jupiter.api.Assertions.*;
class BlacklistReactivationTest {
 BlacklistMapper mapper;ReservationMapper reservations;BlacklistServiceImpl service;
 LocalDateTime now=LocalDateTime.of(2026,10,6,10,0);
 @BeforeEach void init(){mapper=mock(BlacklistMapper.class);reservations=mock(ReservationMapper.class);when(reservations.selectCount(any())).thenReturn(3L);service=new BlacklistServiceImpl(reservations,mock(StringRedisTemplate.class),Clock.system(ZoneId.of("Asia/Shanghai")));ReflectionTestUtils.setField(service,"baseMapper",mapper);TransactionSynchronizationManager.initSynchronization();}
 @AfterEach void clear(){TransactionSynchronizationManager.clearSynchronization();}
 @Test void expiredSingleRowIsReusedWithoutInsert(){Blacklist old=new Blacklist();old.setId(8L);old.setStudentId(3L);old.setExpireTime(now.minusDays(1));when(mapper.lockAnyByStudent(3L)).thenReturn(old);when(mapper.reactivate(any(),eq(now))).thenReturn(1);service.onNoShow(3L,now);verify(mapper,never()).insert(any(Blacklist.class));verify(mapper).reactivate(old,now);assertEquals(now.plusDays(30),old.getExpireTime());assertEquals(8L,old.getId());}
 @Test void logicallyDeletedRowIsReactivated(){Blacklist old=new Blacklist();old.setId(8L);old.setDeleted(1);when(mapper.lockAnyByStudent(3L)).thenReturn(old);when(mapper.reactivate(any(),eq(now))).thenReturn(1);service.onNoShow(3L,now);assertEquals(0,old.getDeleted());assertEquals(1,old.getStatus());verify(mapper,never()).insert(any(Blacklist.class));}
 @Test void activeBlockIsNotExtended(){Blacklist active=new Blacklist();active.setExpireTime(now.plusDays(1));when(mapper.selectOne(any())).thenReturn(active);service.onNoShow(3L,now);verify(mapper,never()).reactivate(any(),any());verify(mapper,never()).insert(any(Blacklist.class));assertEquals(now.plusDays(1),active.getExpireTime());}
 @Test void firstNoShowDoesNotCreateBlock(){when(reservations.selectCount(any())).thenReturn(1L);service.onNoShow(3L,now);verify(mapper,never()).lockAnyByStudent(any());verify(mapper,never()).insert(any(Blacklist.class));}
}

