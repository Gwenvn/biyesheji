package com.campus.cloudisk.service;

import com.campus.cloudisk.dto.PageDTO;

import java.util.List;

/**
 * 回收站服务接口
 * <p>
 * 定义回收站所有业务方法契约，具体实现见 {@link com.campus.cloudisk.service.impl.RecycleBinServiceImpl}。
 * <br/>
 * 设计说明：
 * <ul>
 *   <li>回收站文件通过 {@code file_info.deleted = true} 标记软删除</li>
 *   <li>自动清理：超过 30 天的文件会被定时任务彻底删除（可配置）</li>
 *   <li>彻底删除：同时删除 OSS 对象和数据库记录</li>
 * </ul>
 * </p>
 *
 * @author campus-cloud
 * @since 2024-01-01
 */
public interface RecycleBinService {

    /**
     * 分页查询指定用户的回收站文件列表
     *
     * @param userId  用户 ID
     * @param page    页码（从 1 开始）
     * @param size    每页条数
     * @param keyword 文件名模糊搜索关键字（传 null 则不过滤）
     * @return 分页结果
     */
    PageDTO<?> listRecycleBin(Long userId, int page, int size, String keyword);

    /**
     * 还原单个文件（取消软删除）
     * <p>若原所在目录已被删除，则还原到根目录</p>
     *
     * @param userId 用户 ID（用于鉴权，确保只能操作自己的文件）
     * @param fileId 文件 ID
     * @throws com.campus.cloudisk.exception.BusinessException 文件不存在或无权操作
     */
    void restoreFile(Long userId, Long fileId);

    /**
     * 批量还原文件
     *
     * @param userId  用户 ID
     * @param fileIds 文件 ID 列表
     * @return 实际成功还原的文件数量
     */
    int batchRestoreFiles(Long userId, List<Long> fileIds);

    /**
     * 彻底删除单个文件（数据库 + OSS 双删除）
     * <p>此操作不可撤销</p>
     *
     * @param userId 用户 ID（鉴权）
     * @param fileId 文件 ID
     * @throws com.campus.cloudisk.exception.BusinessException 文件不存在或无权操作
     */
    void deleteFilePermanently(Long userId, Long fileId);

    /**
     * 批量彻底删除文件
     *
     * @param userId  用户 ID
     * @param fileIds 文件 ID 列表
     * @return 实际成功删除的文件数量
     */
    int batchDeletePermanently(Long userId, List<Long> fileIds);

    /**
     * 清空回收站（彻底删除该用户的所有已软删除文件）
     *
     * @param userId 用户 ID
     * @return 清空的文件数量
     */
    int clearRecycleBin(Long userId);

    /**
     * 定时清理超期回收站文件（由定时任务调用，不暴露 HTTP 接口）
     * <p>默认清理 30 天前软删除的文件，具体天数可通过配置项覆盖</p>
     *
     * @param retentionDays 保留天数，超过该天数的文件将被彻底删除
     * @return 清理的文件数量
     */
    int cleanExpiredFiles(int retentionDays);
}
