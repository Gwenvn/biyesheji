package com.campus.cloudisk.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.cloudisk.common.ErrorCode;
import com.campus.cloudisk.dto.PageDTO;
import com.campus.cloudisk.dto.RecycleBinFileDTO;
import com.campus.cloudisk.entity.FileInfo;
import com.campus.cloudisk.exception.BusinessException;
import com.campus.cloudisk.mapper.FileInfoMapper;
import com.campus.cloudisk.service.OssService;
import com.campus.cloudisk.service.RecycleBinService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 回收站服务实现
 * <p>
 * 实现逻辑概要：
 * <ol>
 *   <li>软删除：file_info.deleted = true，deleted_at = 当前时间</li>
 *   <li>还原：deleted = false，deleted_at = null，恢复 folder_id（原目录不存在则置 0/根目录）</li>
 *   <li>彻底删除：先删 OSS 对象，再删 DB 记录，更新用户 storage_used</li>
 *   <li>定时清理：每天凌晨 2 点清理超过 30 天的软删除文件</li>
 * </ol>
 * </p>
 *
 * @author campus-cloud
 * @since 2024-01-01
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RecycleBinServiceImpl implements RecycleBinService {

    /** 文件信息 Mapper */
    private final FileInfoMapper fileInfoMapper;

    /** OSS 文件存储服务（负责 OSS 对象的删除） */
    private final OssService ossService;

    // ===================================================================
    //  查询回收站列表
    // ===================================================================

    /**
     * 分页查询用户回收站文件列表
     * <p>
     * SQL 条件：user_id = ? AND deleted = true AND deleted_at IS NOT NULL
     * 按 deleted_at 倒序排列（最近删除的排在前面）
     * </p>
     */
    @Override
    public PageDTO<RecycleBinFileDTO> listRecycleBin(Long userId, int page, int size, String keyword) {
        // 构造 MyBatis-Plus 分页对象
        Page<FileInfo> pageParam = new Page<>(page, size);

        // 构造查询条件
        LambdaQueryWrapper<FileInfo> wrapper = new LambdaQueryWrapper<FileInfo>()
                .eq(FileInfo::getUserId, userId)         // 只查当前用户
                .eq(FileInfo::getDeleted, true)          // 已软删除
                .isNotNull(FileInfo::getDeletedAt)       // 确保是真正软删除的记录
                // 若有关键字，则模糊搜索文件名
                .like(StringUtils.hasText(keyword), FileInfo::getFileName, keyword)
                .orderByDesc(FileInfo::getDeletedAt);    // 最近删除排前面

        // 执行分页查询
        Page<FileInfo> result = fileInfoMapper.selectPage(pageParam, wrapper);

        // 将 FileInfo 列表转换为 DTO（避免暴露内部字段）
        List<RecycleBinFileDTO> dtoList = result.getRecords().stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());

        return PageDTO.<RecycleBinFileDTO>builder()
                .records(dtoList)
                .total(result.getTotal())
                .current((int) result.getCurrent())
                .size((int) result.getSize())
                .pages((int) result.getPages())
                .build();
    }

    // ===================================================================
    //  还原文件
    // ===================================================================

    /**
     * 还原单个文件
     * <p>
     * 流程：
     * 1. 查询并校验文件存在且属于当前用户
     * 2. 检查原目录（folder_id）是否存在；若已被删除，则改为根目录（folder_id = 0）
     * 3. 更新 deleted = false，deleted_at = null
     * </p>
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void restoreFile(Long userId, Long fileId) {
        // 1. 查询文件，校验归属
        FileInfo fileInfo = getDeletedFileByIdAndUser(userId, fileId);

        // 2. 检查原目录是否有效（folder_id 不为 0/null 时才需要校验）
        Long folderId = fileInfo.getFolderId();
        if (folderId != null && folderId != 0L) {
            boolean folderExists = checkFolderExists(folderId, userId);
            if (!folderExists) {
                // 原目录不存在（可能也被删除了），还原到根目录
                log.info("文件[{}]原目录[{}]不存在，还原到根目录", fileId, folderId);
                folderId = 0L;
            }
        }

        // 3. 执行还原
        LambdaUpdateWrapper<FileInfo> updateWrapper = new LambdaUpdateWrapper<FileInfo>()
                .eq(FileInfo::getId, fileId)
                .eq(FileInfo::getUserId, userId)
                .set(FileInfo::getDeleted, false)
                .set(FileInfo::getDeletedAt, null)
                .set(FileInfo::getFolderId, folderId);

        int rows = fileInfoMapper.update(null, updateWrapper);
        if (rows == 0) {
            throw new BusinessException(ErrorCode.OPERATION_FAILED, "还原失败，请重试");
        }

        log.info("文件[{}]还原成功，user={}, folderId={}", fileId, userId, folderId);
    }

    /**
     * 批量还原文件
     * <p>遍历每个 fileId 逐一调用 restoreFile，保证每个文件都独立校验</p>
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int batchRestoreFiles(Long userId, List<Long> fileIds) {
        if (CollectionUtils.isEmpty(fileIds)) return 0;

        int successCount = 0;
        List<Long> failedIds = new ArrayList<>();

        for (Long fileId : fileIds) {
            try {
                restoreFile(userId, fileId);
                successCount++;
            } catch (Exception e) {
                log.warn("批量还原：文件[{}]还原失败，原因：{}", fileId, e.getMessage());
                failedIds.add(fileId);
            }
        }

        if (!failedIds.isEmpty()) {
            log.warn("批量还原：{}个文件失败，IDs={}", failedIds.size(), failedIds);
        }

        return successCount;
    }

    // ===================================================================
    //  彻底删除
    // ===================================================================

    /**
     * 彻底删除单个文件
     * <p>
     * 流程：
     * 1. 查询并校验文件
     * 2. 调用 OSS 服务删除 OSS 对象（非阻塞异步，失败不影响 DB 删除）
     * 3. 从数据库中物理删除记录
     * 4. 更新用户 storage_used（减少对应文件大小）
     * </p>
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteFilePermanently(Long userId, Long fileId) {
        // 1. 查询并校验文件归属
        FileInfo fileInfo = getDeletedFileByIdAndUser(userId, fileId);

        // 2. 异步删除 OSS 对象（使用 ossKey 标识 OSS 中的对象路径）
        String ossKey = fileInfo.getOssKey();
        if (StringUtils.hasText(ossKey)) {
            try {
                ossService.deleteObject(ossKey);
                log.info("OSS 对象删除成功：{}", ossKey);
            } catch (Exception e) {
                // OSS 删除失败不阻断流程，记录日志后继续删除 DB 记录
                // 实际生产中可加入补偿队列
                log.error("OSS 对象删除失败，ossKey={}，原因：{}", ossKey, e.getMessage(), e);
            }
        }

        // 3. 物理删除数据库记录
        int rows = fileInfoMapper.deleteById(fileId);
        if (rows == 0) {
            throw new BusinessException(ErrorCode.OPERATION_FAILED, "删除失败，请重试");
        }

        // 4. 更新用户已用存储量（减去该文件大小）
        if (fileInfo.getFileSize() != null && fileInfo.getFileSize() > 0) {
            fileInfoMapper.decreaseUserStorage(userId, fileInfo.getFileSize());
        }

        log.info("文件[{}]彻底删除成功，user={}, size={}", fileId, userId, fileInfo.getFileSize());
    }

    /**
     * 批量彻底删除文件
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int batchDeletePermanently(Long userId, List<Long> fileIds) {
        if (CollectionUtils.isEmpty(fileIds)) return 0;

        int successCount = 0;

        for (Long fileId : fileIds) {
            try {
                deleteFilePermanently(userId, fileId);
                successCount++;
            } catch (Exception e) {
                log.warn("批量彻底删除：文件[{}]失败，原因：{}", fileId, e.getMessage());
            }
        }

        return successCount;
    }

    /**
     * 清空回收站（删除当前用户所有已软删除的文件）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int clearRecycleBin(Long userId) {
        // 1. 查询所有软删除文件 ID
        List<FileInfo> deletedFiles = fileInfoMapper.selectList(
                new LambdaQueryWrapper<FileInfo>()
                        .eq(FileInfo::getUserId, userId)
                        .eq(FileInfo::getDeleted, true)
                        .select(FileInfo::getId)
        );

        if (CollectionUtils.isEmpty(deletedFiles)) {
            log.info("用户[{}]回收站为空，无需清理", userId);
            return 0;
        }

        List<Long> allIds = deletedFiles.stream()
                .map(FileInfo::getId)
                .collect(Collectors.toList());

        return batchDeletePermanently(userId, allIds);
    }

    // ===================================================================
    //  定时清理超期文件
    // ===================================================================

    /**
     * 定时任务：每天凌晨 2:00 自动清理超过保留期限的回收站文件
     * <p>
     * cron 表达式：0 0 2 * * ?  —— 每天 02:00:00 执行
     * 清理条件：deleted = true AND deleted_at < NOW() - retentionDays
     * </p>
     */
    @Scheduled(cron = "0 0 2 * * ?")
    public void scheduledCleanExpiredFiles() {
        int retentionDays = 30; // 默认保留 30 天，可改为从配置读取
        log.info("[定时任务] 开始清理超过 {} 天的回收站文件...", retentionDays);
        int count = cleanExpiredFiles(retentionDays);
        log.info("[定时任务] 回收站清理完成，共删除 {} 个文件", count);
    }

    /**
     * 清理超期回收站文件（内部实现）
     *
     * @param retentionDays 保留天数
     * @return 清理的文件数量
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int cleanExpiredFiles(int retentionDays) {
        // 计算过期时间点
        LocalDateTime expireTime = LocalDateTime.now().minusDays(retentionDays);

        // 查询所有超期文件（包含 userId 和 ossKey 用于后续清理）
        List<FileInfo> expiredFiles = fileInfoMapper.selectList(
                new LambdaQueryWrapper<FileInfo>()
                        .eq(FileInfo::getDeleted, true)
                        .lt(FileInfo::getDeletedAt, expireTime)  // deleted_at < 过期时间
        );

        if (CollectionUtils.isEmpty(expiredFiles)) {
            return 0;
        }

        int totalCleaned = 0;

        for (FileInfo file : expiredFiles) {
            try {
                // 删除 OSS 对象
                if (StringUtils.hasText(file.getOssKey())) {
                    ossService.deleteObject(file.getOssKey());
                }
                // 物理删除 DB 记录
                fileInfoMapper.deleteById(file.getId());
                // 更新用户存储量
                if (file.getFileSize() != null && file.getFileSize() > 0) {
                    fileInfoMapper.decreaseUserStorage(file.getUserId(), file.getFileSize());
                }
                totalCleaned++;
            } catch (Exception e) {
                log.error("[定时清理] 文件[{}]清理失败：{}", file.getId(), e.getMessage(), e);
            }
        }

        return totalCleaned;
    }

    // ===================================================================
    //  私有辅助方法
    // ===================================================================

    /**
     * 查询并校验回收站文件（已删除状态），同时验证归属用户
     *
     * @param userId 用户 ID
     * @param fileId 文件 ID
     * @return FileInfo 实体
     * @throws BusinessException 文件不存在、未在回收站、或不属于该用户
     */
    private FileInfo getDeletedFileByIdAndUser(Long userId, Long fileId) {
        FileInfo fileInfo = fileInfoMapper.selectOne(
                new LambdaQueryWrapper<FileInfo>()
                        .eq(FileInfo::getId, fileId)
                        .eq(FileInfo::getUserId, userId)
                        .eq(FileInfo::getDeleted, true)
        );

        if (fileInfo == null) {
            log.warn("文件不存在或无权操作：fileId={}, userId={}", fileId, userId);
            throw new BusinessException(ErrorCode.FILE_NOT_FOUND, "文件不存在或已被彻底删除");
        }

        return fileInfo;
    }

    /**
     * 检查目录是否存在（且未被删除）
     *
     * @param folderId 目录 ID
     * @param userId   用户 ID
     * @return true 表示目录有效
     */
    private boolean checkFolderExists(Long folderId, Long userId) {
        // folder_id = 0 代表根目录，始终有效
        if (folderId == null || folderId == 0L) return true;

        // 查询文件夹表（这里复用 FileInfo 表，通过 is_folder 字段区分目录和文件）
        Integer count = fileInfoMapper.selectCount(
                new LambdaQueryWrapper<FileInfo>()
                        .eq(FileInfo::getId, folderId)
                        .eq(FileInfo::getUserId, userId)
                        .eq(FileInfo::getIsFolder, true)
                        .eq(FileInfo::getDeleted, false)   // 目录未被删除
        ).intValue();

        return count > 0;
    }

    /**
     * 将 FileInfo 实体转换为回收站 DTO
     * <p>过滤掉 ossKey 等内部字段，不暴露给前端</p>
     *
     * @param fileInfo FileInfo 实体
     * @return RecycleBinFileDTO
     */
    private RecycleBinFileDTO convertToDTO(FileInfo fileInfo) {
        return RecycleBinFileDTO.builder()
                .id(fileInfo.getId())
                .fileName(fileInfo.getFileName())
                .fileSize(fileInfo.getFileSize())
                .fileType(fileInfo.getFileType())
                .fileExtension(fileInfo.getFileExtension())
                .folderId(fileInfo.getFolderId())
                .folderPath(fileInfo.getFolderPath())   // 原始目录路径，用于展示
                .deletedAt(fileInfo.getDeletedAt())
                .createTime(fileInfo.getCreateTime())
                // 预计到期时间 = deletedAt + 30 天
                .expireTime(fileInfo.getDeletedAt() != null
                        ? fileInfo.getDeletedAt().plusDays(30) : null)
                .build();
    }
}
