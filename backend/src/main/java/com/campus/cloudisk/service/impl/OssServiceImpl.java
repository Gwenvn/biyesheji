package com.campus.cloudisk.service.impl;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.OSSException;
import com.aliyun.oss.model.DeleteObjectsRequest;
import com.aliyun.oss.model.ObjectMetadata;
import com.campus.cloudisk.common.ErrorCode;
import com.campus.cloudisk.exception.BusinessException;
import com.campus.cloudisk.service.OssService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.time.LocalDate;
import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 * 阿里云 OSS 服务实现
 * <p>
 * 基于阿里云 OSS Java SDK（aliyun-sdk-oss）实现文件上传、删除、预签名 URL 等功能。
 * Bucket 位于新加坡节点（ap-southeast-1），私有权限，通过预签名 URL 授权访问。
 * </p>
 *
 * 依赖（pom.xml 需添加）：
 * <pre>
 * &lt;dependency&gt;
 *   &lt;groupId&gt;com.aliyun.oss&lt;/groupId&gt;
 *   &lt;artifactId&gt;aliyun-sdk-oss&lt;/artifactId&gt;
 *   &lt;version&gt;3.17.4&lt;/version&gt;
 * &lt;/dependency&gt;
 * </pre>
 *
 * @author campus-cloud
 * @since 2024-01-01
 */
@Slf4j
@Service
public class OssServiceImpl implements OssService {

    // ===== OSS 配置（从 application.yml 注入）=====

    /** OSS Endpoint（新加坡外网：oss-ap-southeast-1.aliyuncs.com） */
    @Value("${aliyun.oss.endpoint}")
    private String endpoint;

    /** RAM 子账号 AccessKey ID */
    @Value("${aliyun.oss.access-key-id}")
    private String accessKeyId;

    /** RAM 子账号 AccessKey Secret */
    @Value("${aliyun.oss.access-key-secret}")
    private String accessKeySecret;

    /** Bucket 名称 */
    @Value("${aliyun.oss.bucket-name}")
    private String bucketName;

    /** 文件存储路径前缀（如 campus-files） */
    @Value("${aliyun.oss.file-prefix:campus-files}")
    private String filePrefix;

    /**
     * 预签名 URL 默认有效期（秒）
     * 从 yml 读取：aliyun.oss.presigned-url-expire，默认 3600 秒 = 1小时
     */
    @Value("${aliyun.oss.presigned-url-expire:3600}")
    private long presignedUrlExpire;

    // ===== OSS 客户端单例（应用启动后初始化，关闭时销毁）=====
    private OSS ossClient;

    // ===================================================================
    //  OSS 客户端生命周期管理
    // ===================================================================

    /**
     * 应用启动后初始化 OSS 客户端（单例，线程安全）
     * <p>
     * OSS 客户端创建成本较高，全局共享一个实例。
     * 生产环境建议配置连接池参数（ClientBuilderConfiguration）。
     * </p>
     */
    @PostConstruct
    public void initOssClient() {
        ossClient = new OSSClientBuilder().build(endpoint, accessKeyId, accessKeySecret);
        log.info("[OSS] 客户端初始化成功，endpoint={}, bucket={}", endpoint, bucketName);
    }

    /**
     * 应用关闭时释放 OSS 客户端连接资源
     */
    @PreDestroy
    public void destroyOssClient() {
        if (ossClient != null) {
            ossClient.shutdown();
            log.info("[OSS] 客户端已关闭");
        }
    }

    // ===================================================================
    //  文件上传
    // ===================================================================

    /**
     * 上传 MultipartFile 到 OSS
     * <p>
     * 存储路径格式：{filePrefix}/{userId}/{year}/{month}/{uuid}.{ext}
     * 示例：campus-files/100001/2024/03/a1b2c3d4e5f6.pdf
     * </p>
     */
    @Override
    public String uploadFile(MultipartFile file, Long userId) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "上传文件不能为空");
        }

        // 1. 构造 OSS 对象 Key
        String ossKey = buildOssKey(userId, file.getOriginalFilename());

        // 2. 设置对象元数据（ContentType 等）
        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentLength(file.getSize());
        if (StringUtils.hasText(file.getContentType())) {
            metadata.setContentType(file.getContentType());
        }
        // 设置 Content-Disposition，下载时保持原始文件名（处理中文文件名）
        String encodedFileName = encodeFileName(file.getOriginalFilename());
        metadata.setContentDisposition("attachment;filename=" + encodedFileName);

        // 3. 执行上传
        try (InputStream inputStream = file.getInputStream()) {
            ossClient.putObject(bucketName, ossKey, inputStream, metadata);
            log.info("[OSS] 文件上传成功，ossKey={}, size={}bytes", ossKey, file.getSize());
            return ossKey;
        } catch (OSSException e) {
            log.error("[OSS] 上传失败（OSSException），errorCode={}, message={}", e.getErrorCode(), e.getMessage());
            throw new BusinessException(ErrorCode.OSS_UPLOAD_FAILED, "文件上传失败：" + e.getMessage());
        } catch (IOException e) {
            log.error("[OSS] 读取文件流失败：{}", e.getMessage(), e);
            throw new BusinessException(ErrorCode.FILE_UPLOAD_FAILED, "读取文件失败，请重试");
        }
    }

    /**
     * 上传字节数组到 OSS（头像等小文件）
     */
    @Override
    public void uploadBytes(byte[] bytes, String ossKey, String contentType) {
        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentLength(bytes.length);
        if (StringUtils.hasText(contentType)) {
            metadata.setContentType(contentType);
        }
        try {
            ossClient.putObject(bucketName, ossKey, new ByteArrayInputStream(bytes), metadata);
            log.info("[OSS] 字节数组上传成功，ossKey={}, size={}bytes", ossKey, bytes.length);
        } catch (OSSException e) {
            log.error("[OSS] 字节数组上传失败：{}", e.getMessage());
            throw new BusinessException(ErrorCode.OSS_UPLOAD_FAILED);
        }
    }

    // ===================================================================
    //  文件删除
    // ===================================================================

    /**
     * 删除单个 OSS 对象
     * <p>OSS 删除是幂等的，对象不存在时不会报错。</p>
     */
    @Override
    public void deleteObject(String ossKey) {
        if (!StringUtils.hasText(ossKey)) {
            log.warn("[OSS] 跳过删除：ossKey 为空");
            return;
        }
        try {
            ossClient.deleteObject(bucketName, ossKey);
            log.info("[OSS] 对象删除成功，ossKey={}", ossKey);
        } catch (OSSException e) {
            log.error("[OSS] 删除失败，ossKey={}，errorCode={}, message={}", ossKey, e.getErrorCode(), e.getMessage());
            throw new BusinessException(ErrorCode.OSS_DELETE_FAILED, "存储文件删除失败：" + e.getMessage());
        }
    }

    /**
     * 批量删除 OSS 对象（一次 API 调用最多 1000 个，减少 HTTP 请求次数）
     */
    @Override
    public void deleteObjects(List<String> ossKeys) {
        if (ossKeys == null || ossKeys.isEmpty()) return;

        // OSS 批量删除单次上限 1000 个，超过则分批
        int batchSize = 1000;
        for (int i = 0; i < ossKeys.size(); i += batchSize) {
            List<String> batch = ossKeys.subList(i, Math.min(i + batchSize, ossKeys.size()));
            try {
                DeleteObjectsRequest request = new DeleteObjectsRequest(bucketName)
                        .withKeys(batch)
                        .withQuiet(true);  // 静默模式：只返回删除失败的对象，不返回成功列表
                ossClient.deleteObjects(request);
                log.info("[OSS] 批量删除成功，本批数量={}", batch.size());
            } catch (OSSException e) {
                log.error("[OSS] 批量删除失败，errorCode={}, message={}", e.getErrorCode(), e.getMessage());
                // 批量删除失败不抛出异常，记录日志后继续（防止单批失败影响整体清空操作）
            }
        }
    }

    // ===================================================================
    //  预签名 URL（私有文件临时访问）
    // ===================================================================

    /**
     * 生成预签名 URL（使用默认有效期）
     */
    @Override
    public String generatePresignedUrl(String ossKey) {
        return generatePresignedUrl(ossKey, presignedUrlExpire);
    }

    /**
     * 生成预签名 URL（自定义有效期）
     * <p>
     * 注意：阿里云 OSS 预签名 URL 有效期最长 32400 秒（9小时）。
     * 若需要更长时效，建议使用 STS 临时凭证。
     * </p>
     */
    @Override
    public String generatePresignedUrl(String ossKey, long expireSeconds) {
        if (!StringUtils.hasText(ossKey)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "ossKey 不能为空");
        }
        try {
            // 计算过期时间点
            Date expiration = new Date(System.currentTimeMillis() + expireSeconds * 1000);
            URL url = ossClient.generatePresignedUrl(bucketName, ossKey, expiration);
            String presignedUrl = url.toString();
            log.debug("[OSS] 预签名URL生成成功，ossKey={}, expireSeconds={}", ossKey, expireSeconds);
            return presignedUrl;
        } catch (OSSException e) {
            log.error("[OSS] 生成预签名URL失败，ossKey={}，message={}", ossKey, e.getMessage());
            throw new BusinessException(ErrorCode.OSS_PRESIGN_FAILED);
        }
    }

    // ===================================================================
    //  其他工具方法
    // ===================================================================

    /**
     * 检查 OSS 对象是否存在
     */
    @Override
    public boolean doesObjectExist(String ossKey) {
        if (!StringUtils.hasText(ossKey)) return false;
        try {
            return ossClient.doesObjectExist(bucketName, ossKey);
        } catch (Exception e) {
            log.warn("[OSS] 检查对象存在性失败，ossKey={}：{}", ossKey, e.getMessage());
            return false;
        }
    }

    /**
     * 获取 Bucket 存储用量
     * <p>
     * 阿里云 OSS 存储用量统计有约 1 小时延迟，此处通过 getBucketStat 接口获取。
     * 该接口在部分 Bucket 策略下可能需要额外权限，若失败则返回 -1。
     * </p>
     */
    @Override
    public long getBucketStorageSize() {
        try {
            return ossClient.getBucketStat(bucketName).getStorageSize();
        } catch (Exception e) {
            log.warn("[OSS] 获取Bucket存储用量失败（可能权限不足）：{}", e.getMessage());
            return -1L;
        }
    }

    // ===================================================================
    //  私有辅助方法
    // ===================================================================

    /**
     * 构造 OSS 对象 Key
     * <p>
     * 格式：{filePrefix}/{userId}/{year}/{month}/{uuid}.{ext}
     * 示例：campus-files/100001/2024/03/3f4a5b6c7d8e.pdf
     * <br/>
     * 设计原则：
     * <ul>
     *   <li>按用户 ID 分目录，隔离不同用户文件，便于权限管理</li>
     *   <li>按年月分目录，避免单目录文件过多影响 OSS List 性能</li>
     *   <li>使用 UUID 命名，避免同名文件覆盖，同时支持秒传（相同文件不同路径）</li>
     * </ul>
     * </p>
     *
     * @param userId   用户 ID
     * @param fileName 原始文件名（含扩展名）
     * @return OSS 对象 Key
     */
    private String buildOssKey(Long userId, String fileName) {
        // 提取文件扩展名（转小写，统一处理大小写不一致的问题）
        String ext = "";
        if (StringUtils.hasText(fileName) && fileName.contains(".")) {
            ext = "." + fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase();
        }

        // 按年月构造子目录（如 2024/03）
        LocalDate today = LocalDate.now();
        String datePath = today.getYear() + "/" + String.format("%02d", today.getMonthValue());

        // UUID 去掉连字符（节省 Key 长度）
        String uuid = UUID.randomUUID().toString().replace("-", "");

        return String.format("%s/%d/%s/%s%s", filePrefix, userId, datePath, uuid, ext);
    }

    /**
     * 对文件名进行 URL 编码（处理中文文件名下载乱码问题）
     * <p>
     * RFC 5987 标准：filename*=UTF-8''{encoded}
     * 为了兼容性，此处使用较简单的 URL 编码方式。
     * </p>
     *
     * @param fileName 原始文件名
     * @return URL 编码后的文件名
     */
    private String encodeFileName(String fileName) {
        if (!StringUtils.hasText(fileName)) return "download";
        try {
            return java.net.URLEncoder.encode(fileName, "UTF-8").replace("+", "%20");
        } catch (Exception e) {
            return "download";
        }
    }
}
