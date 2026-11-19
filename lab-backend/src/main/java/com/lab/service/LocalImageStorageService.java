package com.lab.service;

import com.lab.common.BusinessException;
import com.lab.common.ResultCode;
import com.lab.config.FileStorageProperties;
import com.lab.vo.UploadResult;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.MemoryCacheImageInputStream;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Iterator;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 图片本地存储：只接受真实 JPEG/PNG，按上传者隔离目录，不接受远程 URL。
 * 不配置静态资源映射；所有读取都经过 FileController 的登录和工单权限校验。
 */
@Service
public class LocalImageStorageService {

    public static final int MAX_IMAGE_BYTES = 5 * 1024 * 1024;
    private static final long MAX_PIXELS = 16_000_000L;
    private static final Pattern IMAGE_URL = Pattern.compile(
            "^/file/images/([1-9][0-9]{0,18})/([0-9a-f]{32}\\.(jpg|png))$");

    private final Path storageRoot;
    private final RateLimitService limiter;
    private final org.redisson.api.RedissonClient redisson;
    private final com.lab.mapper.RepairOrderMapper orders;

    public LocalImageStorageService(FileStorageProperties properties, RateLimitService limiter,
            org.redisson.api.RedissonClient redisson, com.lab.mapper.RepairOrderMapper orders) {
        this.limiter=limiter; this.redisson=redisson; this.orders=orders;
        try {
            Path configuredRoot = Paths.get(properties.getDirectory()).toAbsolutePath().normalize();
            Files.createDirectories(configuredRoot);
            // 固定真实根目录，后面的所有路径必须在该目录内。
            this.storageRoot = configuredRoot.toRealPath();
        } catch (IOException exception) {
            throw new IllegalStateException("无法初始化图片上传目录", exception);
        }
    }

    public UploadResult upload(MultipartFile file, Long ownerId) {
        if(ownerId==null)throw new BusinessException(ResultCode.UNAUTHORIZED);
        limiter.check("upload-minute",ownerId.toString(),10,60);
        limiter.check("upload-day",ownerId.toString(),100,86400);
        return withOwnerLock(ownerId, () -> uploadLocked(file,ownerId));
    }
    private UploadResult uploadLocked(MultipartFile file, Long ownerId) {
        if (ownerId == null || ownerId <= 0) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "请选择需要上传的图片");
        }
        if (file.getSize() > MAX_IMAGE_BYTES) {
            throw new BusinessException(ResultCode.PAYLOAD_TOO_LARGE, "图片不能超过5MiB");
        }
        byte[] original;
        try (InputStream input = file.getInputStream()) {
            // 不只相信 MultipartFile.size，再按实际读取字节数限制大小。
            original = readBounded(input);
        } catch (IOException exception) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "无法读取上传图片");
        }

        SanitizedImage sanitized = sanitize(original);
        String fileName = UUID.randomUUID().toString().replace("-", "") + "." + sanitized.extension;
        Path ownerDirectory = storageRoot.resolve(ownerId.toString());
        Path target = ownerDirectory.resolve(fileName);
        try {
            Files.createDirectories(ownerDirectory);
            verifyOwnerDirectory(ownerDirectory);
            long used;
            try(java.util.stream.Stream<Path> files=Files.list(ownerDirectory)) {
                used=files.filter(f->Files.isRegularFile(f,LinkOption.NOFOLLOW_LINKS)).mapToLong(f->{try{return Files.size(f);}catch(IOException ex){throw new java.io.UncheckedIOException(ex);}}).sum();
            }
            if(used+sanitized.bytes.length>200L*1024*1024)throw new BusinessException(ResultCode.PAYLOAD_TOO_LARGE,"本账号上传容量已达200MiB，请联系管理员");
            // CREATE_NEW 不覆盖已有文件；文件名、目录完全由服务端生成。
            try (OutputStream output = Files.newOutputStream(target,
                    StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
                output.write(sanitized.bytes);
            }
        } catch (IOException exception) {
            throw new BusinessException(ResultCode.INTERNAL_ERROR, "图片保存失败，请稍后重试");
        }
        return new UploadResult("/file/images/" + ownerId + "/" + fileName,
                fileName, sanitized.bytes.length);
    }

    /** Serialize file lifecycle with attachment insertion so GC cannot remove an about-to-bind image. */
    public <T> T withOwnerLock(Long owner, java.util.function.Supplier<T> action) {
        org.redisson.api.RLock lock=redisson.getLock("file:owner:lock:"+owner);
        try {
            if(!lock.tryLock(3,java.util.concurrent.TimeUnit.SECONDS))throw new BusinessException(ResultCode.CONFLICT,"图片处理中，请稍后重试");
            try{return action.get();}finally{if(lock.isHeldByCurrentThread())lock.unlock();}
        }catch(InterruptedException ex){Thread.currentThread().interrupt();throw new BusinessException(ResultCode.SERVICE_UNAVAILABLE);}
    }
    @org.springframework.scheduling.annotation.Scheduled(fixedDelay=3600000,initialDelay=60000)
    public void removeOrphans() {
        long deadline=System.currentTimeMillis()-86400000L;
        try(java.util.stream.Stream<Path> owners=Files.list(storageRoot)) {
            for(Path directory:(Iterable<Path>)owners.filter(f->Files.isDirectory(f,LinkOption.NOFOLLOW_LINKS))::iterator) {
                String owner=directory.getFileName().toString();if(!owner.matches("[1-9][0-9]{0,18}"))continue;
                withOwnerLock(Long.valueOf(owner),()->{
                    try(java.util.stream.Stream<Path> files=Files.list(directory)) {
                        java.util.Iterator<Path> it=files.iterator();
                        while(it.hasNext()) {
                            Path file=it.next(); if(!Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS)||Files.getLastModifiedTime(file).toMillis()>deadline)continue;
                            String url="/file/images/"+owner+"/"+file.getFileName();
                            if(!IMAGE_URL.matcher(url).matches())continue;
                            if(orders.selectCount(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.lab.entity.RepairOrder>().eq(com.lab.entity.RepairOrder::getImageUrl,url))==0)Files.deleteIfExists(file);
                        }
                    }catch(IOException ex){throw new java.io.UncheckedIOException(ex);} return null;
                });
            }
        }catch(IOException|RuntimeException ex){ org.slf4j.LoggerFactory.getLogger(getClass()).warn("未绑定图片清理暂时失败，将重试：{}",ex.getClass().getSimpleName()); }
    }

    /** 提交工单时只能引用本人上传的图片，杜绝把他人的附件绑定到自己的工单。 */
    public byte[] readOwnedImage(String imageUrl, Long ownerId) {
        ImagePath imagePath = parse(imageUrl);
        if (ownerId == null || !ownerId.equals(imagePath.ownerId)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "只能使用本人上传的图片");
        }
        return readImage(imageUrl);
    }

    /** 调用方必须先检查登录用户的工单访问权限；此方法负责路径和文件类型安全。 */
    public byte[] readImage(String imageUrl) {
        ImagePath imagePath = parse(imageUrl);
        Path ownerDirectory = storageRoot.resolve(imagePath.ownerId.toString());
        Path target = ownerDirectory.resolve(imagePath.fileName).normalize();
        try {
            if (!Files.isDirectory(ownerDirectory, LinkOption.NOFOLLOW_LINKS)
                    || !Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS)) {
                throw new BusinessException(ResultCode.NOT_FOUND, "图片不存在");
            }
            verifyOwnerDirectory(ownerDirectory);
            if (!target.startsWith(storageRoot) || !target.toRealPath().equals(target)) {
                throw new BusinessException(ResultCode.BAD_REQUEST, "图片路径无效");
            }
            try (InputStream input = Files.newInputStream(target, LinkOption.NOFOLLOW_LINKS)) {
                return readBounded(input);
            }
        } catch (IOException exception) {
            throw new BusinessException(ResultCode.INTERNAL_ERROR, "图片读取失败");
        }
    }

    public String contentType(String imageUrl) {
        return "jpg".equals(parse(imageUrl).extension) ? "image/jpeg" : "image/png";
    }

    private void verifyOwnerDirectory(Path directory) throws IOException {
        if (Files.isSymbolicLink(directory) || !directory.toRealPath().equals(directory)
                || !directory.startsWith(storageRoot)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "图片目录无效");
        }
    }

    private ImagePath parse(String imageUrl) {
        Matcher matcher = IMAGE_URL.matcher(imageUrl == null ? "" : imageUrl);
        if (!matcher.matches()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "必须使用本系统上传接口返回的图片路径");
        }
        try {
            return new ImagePath(Long.valueOf(matcher.group(1)), matcher.group(2), matcher.group(3));
        } catch (NumberFormatException exception) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "图片所属用户无效");
        }
    }

    private SanitizedImage sanitize(byte[] original) {
        ImageReader reader = null;
        LimitedOutput output = new LimitedOutput();
        try (ImageInputStream input = new MemoryCacheImageInputStream(new ByteArrayInputStream(original))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                throw new BusinessException(ResultCode.BAD_REQUEST, "文件不是有效的JPEG或PNG图片");
            }
            reader = readers.next();
            String format = reader.getFormatName().toLowerCase(Locale.ROOT);
            if (!"jpeg".equals(format) && !"jpg".equals(format) && !"png".equals(format)) {
                throw new BusinessException(ResultCode.BAD_REQUEST, "只支持JPEG和PNG图片");
            }
            reader.setInput(input, true, true);
            // 先读取尺寸，再解码像素，避免小文件声明超大尺寸导致内存耗尽。
            int width = reader.getWidth(0);
            int height = reader.getHeight(0);
            if (width < 15 || height < 15 || width > 4096 || height > 4096
                    || (long) width * height > MAX_PIXELS) {
                throw new BusinessException(ResultCode.BAD_REQUEST,
                        "图片宽高需在15至4096像素之间，且总像素不能超过1600万");
            }
            BufferedImage decoded = reader.read(0);
            if (decoded == null) {
                throw new BusinessException(ResultCode.BAD_REQUEST, "图片解码失败");
            }
            String extension = "png".equals(format) ? "png" : "jpg";
            BufferedImage clean = new BufferedImage(width, height, "png".equals(extension)
                    ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
            Graphics2D graphics = clean.createGraphics();
            try {
                graphics.drawImage(decoded, 0, 0, null);
            } finally {
                graphics.dispose();
                decoded.flush();
            }
            // 只保存新图像的像素，去除原文件名、EXIF及附加内容；再限制输出大小。
            try {
                if (!ImageIO.write(clean, extension, output)) {
                    throw new BusinessException(ResultCode.BAD_REQUEST, "图片重新编码失败");
                }
            } finally {
                clean.flush();
            }
            return new SanitizedImage(extension, output.toByteArray());
        } catch (IOException exception) {
            if (output.limitReached) {
                throw new BusinessException(ResultCode.PAYLOAD_TOO_LARGE, "重新编码后的图片超过5MiB，请压缩后上传");
            }
            throw new BusinessException(ResultCode.BAD_REQUEST, "图片损坏或无法正常解码");
        } finally {
            if (reader != null) {
                reader.dispose();
            }
        }
    }

    private byte[] readBounded(InputStream input) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int read;
        while ((read = input.read(buffer)) != -1) {
            if (bytes.size() + read > MAX_IMAGE_BYTES) {
                throw new BusinessException(ResultCode.PAYLOAD_TOO_LARGE, "图片不能超过5MiB");
            }
            bytes.write(buffer, 0, read);
        }
        return bytes.toByteArray();
    }

    private static final class SanitizedImage {
        private final String extension;
        private final byte[] bytes;

        private SanitizedImage(String extension, byte[] bytes) {
            this.extension = extension;
            this.bytes = bytes;
        }
    }

    private static final class ImagePath {
        private final Long ownerId;
        private final String fileName;
        private final String extension;

        private ImagePath(Long ownerId, String fileName, String extension) {
            this.ownerId = ownerId;
            this.fileName = fileName;
            this.extension = extension;
        }
    }

    /** 编码器边写边限制，不能先无限制地编码到内存后才检查大小。 */
    private static final class LimitedOutput extends OutputStream {
        private final ByteArrayOutputStream delegate = new ByteArrayOutputStream();
        private boolean limitReached;

        @Override
        public void write(int value) throws IOException {
            ensureCapacity(1);
            delegate.write(value);
        }

        @Override
        public void write(byte[] bytes, int offset, int length) throws IOException {
            ensureCapacity(length);
            delegate.write(bytes, offset, length);
        }

        private void ensureCapacity(int additional) throws IOException {
            if ((long) delegate.size() + additional > MAX_IMAGE_BYTES) {
                limitReached = true;
                throw new IOException("Image exceeds upload size limit");
            }
        }

        private byte[] toByteArray() {
            return delegate.toByteArray();
        }
    }
}
