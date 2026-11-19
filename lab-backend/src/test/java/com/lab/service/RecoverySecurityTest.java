package com.lab.service;
import com.lab.mapper.UserMapper;
import com.lab.entity.User;
import com.lab.dto.ResetPasswordRequest;
import com.lab.common.*;
import com.lab.utils.PasswordUtils;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.*;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.junit.jupiter.api.Assertions.*;
class RecoverySecurityTest {
 UserMapper users;VerificationCodeService codes;AccountRecoveryService recovery;User user;
 @BeforeEach void init(){TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(),"recovery"),User.class);users=mock(UserMapper.class);codes=mock(VerificationCodeService.class);recovery=new AccountRecoveryService(users,codes,mock(RateLimitService.class));user=new User();user.setId(1L);user.setUsername("tester");user.setStatus(1);user.setEmail("tester@qq.com");user.setEmailVerified(1);user.setAuthVersion(3);when(users.selectOne(any())).thenReturn(user);when(codes.normalize("email","tester@qq.com")).thenReturn("tester@qq.com");TransactionSynchronizationManager.initSynchronization();}
 @AfterEach void close(){TransactionSynchronizationManager.clearSynchronization();}
 ResetPasswordRequest request(){ResetPasswordRequest q=new ResetPasswordRequest();q.setUsername("tester");q.setTarget("tester@qq.com");q.setChannel("email");q.setCode("123456");q.setNewPassword("NewTest@123");return q;}
 @Test void unverifiedLegacyEmailCannotReset(){user.setEmailVerified(0);assertThrows(BusinessException.class,()->recovery.reset(request()));verify(codes,never()).consume(any(),any(),any(),any(),any());verify(users,never()).update(any(),any());}
 @Test void disabledAccountCannotReset(){user.setStatus(0);assertThrows(BusinessException.class,()->recovery.reset(request()));verify(users,never()).update(any(),any());}
 @Test void wrongCodeDoesNotModifyPassword(){doThrow(new BusinessException(ResultCode.BAD_REQUEST)).when(codes).consume(any(),any(),any(),any(),any());assertThrows(BusinessException.class,()->recovery.reset(request()));verify(users,never()).update(any(),any());}
 @Test void passwordUtf8OverflowRejectedBeforeCodeConsumption(){ResetPasswordRequest q=request();q.setNewPassword(new String(new char[30]).replace("\0","中"));assertThrows(BusinessException.class,()->recovery.reset(q));verify(codes,never()).consume(any(),any(),any(),any(),any());}
 @Test void validResetConsumesRecoveryPurposeAndSchedulesRevocation(){recovery.reset(request());verify(codes).consume("recover","tester","email","tester@qq.com","123456");verify(users).update(isNull(),any());assertEquals(1,TransactionSynchronizationManager.getSynchronizations().size());}
 @Test void qqAndPhoneFormatsAreValidated(){VerificationCodeService v=new VerificationCodeService(mock(org.springframework.data.redis.core.StringRedisTemplate.class),mock(RateLimitService.class),mock(VerificationDelivery.class));assertEquals("test@qq.com",v.normalize("email"," Test@QQ.com "));assertEquals("13800138000",v.normalize("phone","13800138000"));assertThrows(BusinessException.class,()->v.normalize("email","test@example.com"));assertThrows(BusinessException.class,()->v.normalize("phone","123"));}
}
