package com.lab.service;
import com.lab.config.FileStorageProperties;
import com.lab.mapper.RepairOrderMapper;
import org.redisson.api.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import java.nio.file.*;
import java.nio.file.attribute.FileTime;
import java.util.concurrent.TimeUnit;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.io.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.junit.jupiter.api.Assertions.*;
class StorageLifecycleTest {
 @TempDir Path directory;
 LocalImageStorageService service;RepairOrderMapper orders;
 @BeforeEach void setup()throws Exception{FileStorageProperties p=new FileStorageProperties();p.setDirectory(directory.toString());RedissonClient redisson=mock(RedissonClient.class);RLock lock=mock(RLock.class);when(redisson.getLock(anyString())).thenReturn(lock);when(lock.tryLock(3,TimeUnit.SECONDS)).thenReturn(true);when(lock.isHeldByCurrentThread()).thenReturn(true);orders=mock(RepairOrderMapper.class);service=new LocalImageStorageService(p,mock(RateLimitService.class),redisson,orders);}
 MockMultipartFile image()throws Exception{ByteArrayOutputStream out=new ByteArrayOutputStream();ImageIO.write(new BufferedImage(16,16,BufferedImage.TYPE_INT_RGB),"png",out);return new MockMultipartFile("file","test.png","image/png",out.toByteArray());}
 @Test void orphanOldImageRemovedAndRecentImageKept()throws Exception{Path owner=Files.createDirectory(directory.resolve("1"));Path old=owner.resolve("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa.png"),recent=owner.resolve("bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb.png");Files.write(old,new byte[]{1});Files.write(recent,new byte[]{1});Files.setLastModifiedTime(old,FileTime.fromMillis(System.currentTimeMillis()-172800000L));when(orders.selectCount(any())).thenReturn(0L);service.removeOrphans();assertFalse(Files.exists(old));assertTrue(Files.exists(recent));}
 @Test void boundImagePreservedDespiteAge()throws Exception{Path owner=Files.createDirectory(directory.resolve("1"));Path file=owner.resolve("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa.png");Files.write(file,new byte[]{1});Files.setLastModifiedTime(file,FileTime.fromMillis(System.currentTimeMillis()-172800000L));when(orders.selectCount(any())).thenReturn(1L);service.removeOrphans();assertTrue(Files.exists(file));}
 @Test void userQuotaRejectsAdditionalUpload()throws Exception{Path owner=Files.createDirectory(directory.resolve("1"));try(RandomAccessFile file=new RandomAccessFile(owner.resolve("quota.tmp").toFile(),"rw")){file.setLength(200L*1024*1024);}com.lab.common.BusinessException ex=assertThrows(com.lab.common.BusinessException.class,()->service.upload(image(),1L));assertEquals(413,ex.getCode());}
}
