package com.campus.cloudisk.service;

import org.springframework.web.multipart.MultipartFile;

/**
 * OSS 文件存储服务接口
 * <p>
 * 封装阿里云 OSS SDK，提供文件上传、删除、预签名 URL 等能力。
 * 具体实现见 {@link com.campus.cloudisk.service.impl.OssServiceImpl}。
 * </p>
 *
 * @author campus-cloud
 * @since 2024-01-01
 */
public interface OssService {

    /**
     * 上传文件到 OSS
     * <p>
     * 文件将以 {prefix}/{userId}/{year}/{month}/{uuid}.{ext} 路径存储。
     * 上传成功后返回 OSS Key（路径），可通过 {@link #generatePresignedUrl} 获取访问链接。
     * </p>
     *
     * @param file   要上传的文件（MultipartFile）
     * @param userId 上传者用户 ID（用于构造存储路径，隔离不同用户文件）
     * @return OSS 对象 Key（如 campus-files/100001/2024/03/abc123.pdf）
     * @throws com.campus.cloudisk.exception.BusinessException 上传失败时
     */
    String uploadFile(MultipartFile file, Long userId);

    /**
     * 上传字节数组到 OSS（用于头像等小文件的直接上传）
     *
     * @param bytes       文件字节数组
     * @param ossKey      OSS 对象 Key（完整路径，调用方自行构造）
     * @param contentType MIME 类型，如 "image/jpeg"
     * @throws com.campus.cloudisk.exception.BusinessException 上传失败时
     */
    void uploadBytes(byte[] bytes, String ossKey, String contentType);

    /**
     * 删除 OSS 对象
     * <p>
     * 删除操作是幂等的：若对象不存在，OSS 不会报错。
     * 彻底删除文件时调用此方法清理 OSS 存储，避免产生费用。
     * </p>
     *
     * @param ossKey OSS 对象 Key
     * @throws com.campus.cloudisk.exception.BusinessException OSS 服务异常时
     */
    void deleteObject(String ossKey);

    /**
     * 批量删除 OSS 对象（清空回收站时使用，减少 HTTP 调用次数）
     *
     * @param ossKeys OSS 对象 Key 列表（最多 1000 个）
     */
    void deleteObjects(java.util.List<String> ossKeys);

    /**
     * 生成文件预签名访问 URL（私有 Bucket 文件临时访问）
     * <p>
     * 预签名 URL 包含时效性签名，到期后自动失效。
     * 有效期在 application.yml 中配置：aliyun.oss.presigned-url-expire（秒）。
     * </p>
     *
     * @param ossKey OSS 对象 Key
     * @return 带签名的临时访问 URL（HTTPS）
     * @throws com.campus.cloudisk.exception.BusinessException 生成失败时
     */
    String generatePresignedUrl(String ossKey);

    /**
     * 生成文件预签名访问 URL（自定义有效期）
     *
     * @param ossKey      OSS 对象 Key
     * @param expireSeconds 有效期（秒），如 3600 = 1小时，最大 32400 = 9小时（OSS 限制）
     * @return 带签名的临时访问 URL
     */
    String generatePresignedUrl(String ossKey, long expireSeconds);

    /**
     * 检查 OSS 对象是否存在
     *
     * @param ossKey OSS 对象 Key
     * @return true 表示对象存在
     */
    boolean doesObjectExist(String ossKey);

    /**
     * 获取 OSS Bucket 的存储用量统计
     * <p>
     * 注意：此接口通过 OSS API 查询，有一定延迟（通常延迟 1 小时）。
     * 管理后台展示时可每小时刷新一次，无需实时调用。
     * </p>
     *
     * @return 存储用量（字节）；若查询失败返回 -1
     */
    long getBucketStorageSize();
}
