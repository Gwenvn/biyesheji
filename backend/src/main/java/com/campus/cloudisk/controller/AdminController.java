package com.campus.cloudisk.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.cloudisk.common.ErrorCode;
import com.campus.cloudisk.common.Result;
import com.campus.cloudisk.dto.PageDTO;
import com.campus.cloudisk.entity.FileInfo;
import com.campus.cloudisk.entity.UserInfo;
import com.campus.cloudisk.exception.BusinessException;
import com.campus.cloudisk.mapper.FileInfoMapper;
import com.campus.cloudisk.mapper.UserInfoMapper;
import com.campus.cloudisk.service.OssService;
import com.campus.cloudisk.utils.UserContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 管理后台控制器
 * <p>
 * 所有接口仅限 ADMIN 角色访问，在每个方法入口处通过 {@link #checkAdmin()} 验证。
 * 对应前端 Admin.vue 的三个 Tab：用户管理 / 文件管理 / 存储统计。
 * <br/>
 * 接口列表：
 * <ul>
 *   <li>GET  /api/admin/stats/overview  — 顶部统计卡片数据</li>
 *   <li>GET  /api/admin/stats/storage   — OSS 存储统计</li>
 *   <li>GET  /api/admin/users           — 用户列表（分页）</li>
 *   <li>PUT  /api/admin/users/{id}/status — 启用/禁用用户</li>
 *   <li>DEL  /api/admin/users/{id}      — 删除用户</li>
 *   <li>GET  /api/admin/files           — 文件列表（分页）</li>
 *   <li>DEL  /api/admin/files/{id}      — 删除单个文件</li>
 *   <li>DEL  /api/admin/files/batch     — 批量删除文件</li>
 * </ul>
 * </p>
 *
 * @author campus-cloud
 * @since 2024-01-01
 */
@Slf4j
@Tag(name = "管理后台", description = "用户管理、文件管理、存储统计（仅管理员可访问）")
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final UserInfoMapper userInfoMapper;
    private final FileInfoMapper fileInfoMapper;
    private final OssService     ossService;

    // ===================================================================
    //  统计概览（Admin.vue 顶部统计卡片）
    // ===================================================================

    /**
     * 获取顶部统计卡片数据
     * <p>返回：总用户数 / 总文件数 / 总存储用量 / 今日上传数</p>
     */
    @Operation(summary = "统计概览", description = "返回顶部四个统计卡片的数据")
    @GetMapping("/stats/overview")
    public Result<Map<String, Object>> getOverviewStats() {
        checkAdmin();

        Map<String, Object> stats = new HashMap<>(4);

        // 总用户数
        stats.put("totalUsers", userInfoMapper.countTotal());

        // 总文件数（不含文件夹、不含已删除）
        stats.put("totalFiles", fileInfoMapper.selectCount(
                new LambdaQueryWrapper<FileInfo>()
                        .eq(FileInfo::getIsFolder, false)
                        .eq(FileInfo::getDeleted, false)
        ));

        // 所有用户已用存储量总和
        Long totalStorage = userInfoMapper.sumStorageUsed();
        stats.put("totalStorage", totalStorage != null ? totalStorage : 0L);

        // 今日上传文件数（创建时间为今天且未删除）
        stats.put("todayUpload", fileInfoMapper.selectCount(
                new LambdaQueryWrapper<FileInfo>()
                        .eq(FileInfo::getIsFolder, false)
                        .eq(FileInfo::getDeleted, false)
                        .apply("DATE(create_time) = CURDATE()")
        ));

        return Result.success(stats);
    }

    /**
     * 获取 OSS 存储统计数据（Admin.vue 存储统计 Tab）
     * <p>
     * 返回：总容量 / 已用容量 / 本月上传下载流量 / 文件类型分布 / Top5 用户
     * </p>
     */
    @Operation(summary = "存储统计", description = "返回 OSS 存储用量及文件类型分布")
    @GetMapping("/stats/storage")
    public Result<Map<String, Object>> getStorageStats() {
        checkAdmin();

        Map<String, Object> stats = new HashMap<>(8);

        // --- OSS 实际存储用量（通过 OSS API 查询，有约 1 小时延迟）---
        long ossUsed = ossService.getBucketStorageSize();
        // 若 OSS API 查询失败（-1），则回退为数据库统计值
        if (ossUsed < 0) {
            Long dbUsed = userInfoMapper.sumStorageUsed();
            ossUsed = dbUsed != null ? dbUsed : 0L;
        }

        // 总容量：100GB（演示用固定值，实际可从 OSS 套餐配置中读取）
        long totalCapacity = 100L * 1024 * 1024 * 1024;
        stats.put("totalCapacity",   totalCapacity);
        stats.put("usedCapacity",    ossUsed);

        // --- 本月流量统计（演示用随机数，实际需接入阿里云监控 API）---
        // 毕业设计演示阶段，可用数据库中本月上传文件大小之和代替
        Long monthlyUpload = fileInfoMapper.selectObjs(
                new LambdaQueryWrapper<FileInfo>()
                        .select(FileInfo::getFileSize)
                        .eq(FileInfo::getIsFolder, false)
                        .eq(FileInfo::getDeleted, false)
                        .apply("DATE_FORMAT(create_time,'%Y-%m') = DATE_FORMAT(NOW(),'%Y-%m')")
        ).stream()
                .filter(o -> o != null)
                .mapToLong(o -> Long.parseLong(o.toString()))
                .sum();

        stats.put("monthlyUpload",    monthlyUpload);
        stats.put("monthlyDownload",  monthlyUpload / 3);  // 演示：下载量约为上传量的 1/3
        stats.put("uploadTrend",      12);   // 演示：较上月增长 12%
        stats.put("downloadTrend",    -5);   // 演示：较上月下降 5%

        // --- 文件类型分布 ---
        List<Map<String, Object>> rawDist = fileInfoMapper.countFilesByType(null);
        List<Map<String, Object>> typeDistribution = buildTypeDistribution(rawDist, ossUsed);
        stats.put("typeDistribution", typeDistribution);

        // --- 存储用量 Top 5 用户 ---
        List<Map<String, Object>> topUsers = fileInfoMapper.selectTopUsersByStorage(5);
        // 字段重命名以匹配前端期望格式
        List<Map<String, Object>> formattedTopUsers = topUsers.stream().map(row -> {
            Map<String, Object> user = new HashMap<>(4);
            user.put("username",  row.get("username"));
            user.put("used",      row.get("storageUsed"));
            user.put("fileCount", row.get("fileCount"));
            return user;
        }).collect(Collectors.toList());
        stats.put("topUsers", formattedTopUsers);

        return Result.success(stats);
    }

    // ===================================================================
    //  用户管理（Admin.vue 用户管理 Tab）
    // ===================================================================

    /**
     * 分页查询用户列表
     *
     * @param page    页码（从 1 开始）
     * @param size    每页条数
     * @param keyword 用户名/邮箱模糊搜索
     * @param status  账号状态筛选（1=正常 0=禁用，不传则全部）
     */
    @Operation(summary = "用户列表", description = "分页查询所有用户，支持关键字和状态筛选")
    @GetMapping("/users")
    public Result<PageDTO<UserInfo>> listUsers(
            @RequestParam(defaultValue = "1")  int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false)    String keyword,
            @RequestParam(required = false)    Integer status
    ) {
        checkAdmin();

        Page<UserInfo> pageParam = new Page<>(page, size);

        LambdaQueryWrapper<UserInfo> wrapper = new LambdaQueryWrapper<UserInfo>()
                // 关键字：匹配用户名或邮箱
                .and(StringUtils.hasText(keyword), w -> w
                        .like(FileInfo::class != null, UserInfo::getUsername, keyword)
                        .or()
                        .like(UserInfo::getEmail, keyword)
                )
                // 状态筛选
                .eq(status != null, UserInfo::getStatus, status)
                // 按注册时间倒序
                .orderByDesc(UserInfo::getCreateTime);

        Page<UserInfo> result = userInfoMapper.selectPage(pageParam, wrapper);

        return Result.success(PageDTO.<UserInfo>builder()
                .records(result.getRecords())
                .total(result.getTotal())
                .current((int) result.getCurrent())
                .size((int) result.getSize())
                .pages((int) result.getPages())
                .build());
    }

    /**
     * 启用 / 禁用用户账号
     *
     * @param userId 目标用户 ID
     * @param req    包含 status 字段（1=启用 0=禁用）
     */
    @Operation(summary = "修改用户状态", description = "启用或禁用指定用户账号")
    @PutMapping("/users/{userId}/status")
    public Result<Void> updateUserStatus(
            @PathVariable Long userId,
            @RequestBody StatusRequest req
    ) {
        checkAdmin();

        // 防止管理员禁用自己
        Long currentUserId = UserContext.getCurrentUserId();
        if (currentUserId.equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "不能修改自己的账号状态");
        }

        // 防止禁用其他管理员（保护机制）
        UserInfo target = userInfoMapper.selectById(userId);
        if (target == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        if ("ADMIN".equals(target.getRole())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "不能修改管理员账号状态");
        }

        int rows = userInfoMapper.updateStatus(userId, req.getStatus());
        if (rows == 0) {
            throw new BusinessException(ErrorCode.OPERATION_FAILED, "状态更新失败");
        }

        log.info("[Admin] 用户状态变更，operatorId={}, targetId={}, newStatus={}",
                currentUserId, userId, req.getStatus());
        return Result.success();
    }

    /**
     * 删除用户（物理删除，同时删除其所有文件）
     *
     * @param userId 目标用户 ID
     */
    @Operation(summary = "删除用户", description = "删除用户账号及其所有文件（不可恢复）")
    @DeleteMapping("/users/{userId}")
    public Result<Void> deleteUser(@PathVariable Long userId) {
        checkAdmin();

        Long currentUserId = UserContext.getCurrentUserId();
        if (currentUserId.equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "不能删除自己的账号");
        }

        UserInfo target = userInfoMapper.selectById(userId);
        if (target == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        if ("ADMIN".equals(target.getRole())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "不能删除管理员账号");
        }

        // 1. 获取该用户所有文件的 OSS Key，异步批量删除 OSS 对象
        List<String> ossKeys = fileInfoMapper.selectObjs(
                new LambdaQueryWrapper<FileInfo>()
                        .select(FileInfo::getOssKey)
                        .eq(FileInfo::getUserId, userId)
                        .isNotNull(FileInfo::getOssKey)
        ).stream()
                .filter(o -> o != null && !o.toString().isEmpty())
                .map(Object::toString)
                .collect(Collectors.toList());

        if (!ossKeys.isEmpty()) {
            try {
                ossService.deleteObjects(ossKeys);
                log.info("[Admin] 删除用户[{}]的OSS文件，共{}个", userId, ossKeys.size());
            } catch (Exception e) {
                log.error("[Admin] OSS批量删除失败，userId={}：{}", userId, e.getMessage());
                // OSS 删除失败不阻断用户删除流程
            }
        }

        // 2. 删除数据库中文件记录
        fileInfoMapper.delete(new LambdaQueryWrapper<FileInfo>().eq(FileInfo::getUserId, userId));

        // 3. 删除用户账号
        userInfoMapper.deleteById(userId);

        log.warn("[Admin] 用户已删除，operatorId={}, deletedUserId={}, username={}",
                currentUserId, userId, target.getUsername());
        return Result.success();
    }

    // ===================================================================
    //  文件管理（Admin.vue 文件管理 Tab）
    // ===================================================================

    /**
     * 分页查询所有用户的文件列表
     *
     * @param page     页码
     * @param size     每页条数
     * @param keyword  文件名搜索
     * @param fileType 文件类型筛选（image/document/video/audio/other）
     */
    @Operation(summary = "文件列表", description = "管理员查看所有用户的文件，支持搜索和类型筛选")
    @GetMapping("/files")
    public Result<PageDTO<?>> listFiles(
            @RequestParam(defaultValue = "1")  int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false)    String keyword,
            @RequestParam(required = false)    String fileType
    ) {
        checkAdmin();

        Page<FileInfo> pageParam = new Page<>(page, size);

        LambdaQueryWrapper<FileInfo> wrapper = new LambdaQueryWrapper<FileInfo>()
                .eq(FileInfo::getIsFolder, false)        // 不含文件夹
                .eq(FileInfo::getDeleted, false)         // 不含回收站
                .like(StringUtils.hasText(keyword),  FileInfo::getFileName, keyword)
                .eq(StringUtils.hasText(fileType),   FileInfo::getFileType, fileType)
                .orderByDesc(FileInfo::getCreateTime);

        Page<FileInfo> result = fileInfoMapper.selectPage(pageParam, wrapper);

        // 补充上传者用户名（JOIN 查询或单独查，这里用简单方式：逐条填充）
        List<Map<String, Object>> records = result.getRecords().stream().map(f -> {
            Map<String, Object> row = new HashMap<>(12);
            row.put("id",            f.getId());
            row.put("fileName",      f.getFileName());
            row.put("fileSize",      f.getFileSize());
            row.put("fileType",      f.getFileType());
            row.put("fileExtension", f.getFileExtension());
            row.put("isPublic",      f.getIsPublic());
            row.put("downloadCount", f.getDownloadCount());
            row.put("createTime",    f.getCreateTime());
            row.put("userId",        f.getUserId());
            // 简化：使用 userId 作为上传者标识，生产环境应 JOIN user_info 表
            row.put("ownerName",     "用户" + f.getUserId());
            return row;
        }).collect(Collectors.toList());

        return Result.success(PageDTO.builder()
                .records(records)
                .total(result.getTotal())
                .current((int) result.getCurrent())
                .size((int) result.getSize())
                .pages((int) result.getPages())
                .build());
    }

    /**
     * 管理员删除单个文件（从 OSS 和数据库彻底删除）
     *
     * @param fileId 文件 ID
     */
    @Operation(summary = "删除文件", description = "管理员彻底删除指定文件")
    @DeleteMapping("/files/{fileId}")
    public Result<Void> deleteFile(@PathVariable Long fileId) {
        checkAdmin();

        FileInfo file = fileInfoMapper.selectById(fileId);
        if (file == null) {
            throw new BusinessException(ErrorCode.FILE_NOT_FOUND);
        }

        // 1. 删除 OSS 对象
        if (StringUtils.hasText(file.getOssKey())) {
            try {
                ossService.deleteObject(file.getOssKey());
            } catch (Exception e) {
                log.error("[Admin] 文件OSS删除失败，ossKey={}：{}", file.getOssKey(), e.getMessage());
            }
        }

        // 2. 删除数据库记录
        fileInfoMapper.deleteById(fileId);

        // 3. 更新用户存储量
        if (file.getFileSize() != null && file.getFileSize() > 0) {
            fileInfoMapper.decreaseUserStorage(file.getUserId(), file.getFileSize());
        }

        log.info("[Admin] 文件已删除，fileId={}, operator={}", fileId, UserContext.getCurrentUserId());
        return Result.success();
    }

    /**
     * 批量删除文件
     *
     * @param req 包含文件 ID 列表的请求体
     */
    @Operation(summary = "批量删除文件", description = "管理员批量彻底删除文件")
    @DeleteMapping("/files/batch")
    public Result<Integer> batchDeleteFiles(@RequestBody BatchDeleteRequest req) {
        checkAdmin();

        if (req.getIds() == null || req.getIds().isEmpty()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "文件ID列表不能为空");
        }

        int successCount = 0;
        List<String> ossKeys = new ArrayList<>();

        // 收集所有 OSS Key
        for (Long fileId : req.getIds()) {
            FileInfo file = fileInfoMapper.selectById(fileId);
            if (file == null) continue;

            if (StringUtils.hasText(file.getOssKey())) {
                ossKeys.add(file.getOssKey());
            }
            // 更新存储量
            if (file.getFileSize() != null && file.getFileSize() > 0) {
                fileInfoMapper.decreaseUserStorage(file.getUserId(), file.getFileSize());
            }
            fileInfoMapper.deleteById(fileId);
            successCount++;
        }

        // 批量删除 OSS 对象
        if (!ossKeys.isEmpty()) {
            try {
                ossService.deleteObjects(ossKeys);
            } catch (Exception e) {
                log.error("[Admin] 批量OSS删除异常：{}", e.getMessage());
            }
        }

        log.info("[Admin] 批量删除文件，共{}个，operator={}", successCount, UserContext.getCurrentUserId());
        return Result.success(successCount);
    }

    // ===================================================================
    //  请求体 DTO（内部静态类）
    // ===================================================================

    /** 修改状态请求体 */
    @Data
    public static class StatusRequest {
        /** 1=启用，0=禁用 */
        private Integer status;
    }

    /** 批量删除请求体 */
    @Data
    public static class BatchDeleteRequest {
        @NotEmpty(message = "文件ID列表不能为空")
        private List<Long> ids;
    }

    // ===================================================================
    //  私有辅助方法
    // ===================================================================

    /**
     * 管理员权限校验
     * <p>非管理员调用此方法将抛出 403 异常，防止普通用户访问管理接口。</p>
     */
    private void checkAdmin() {
        if (!UserContext.isAdmin()) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "该操作仅限管理员");
        }
    }

    /**
     * 将文件类型原始统计数据转换为带颜色、百分比的展示数据
     *
     * @param rawDist  数据库统计结果（fileType, fileCount, totalSize）
     * @param totalSize 总存储量（用于计算百分比）
     * @return 前端可直接渲染的类型分布列表
     */
    private List<Map<String, Object>> buildTypeDistribution(
            List<Map<String, Object>> rawDist, long totalSize) {

        // 类型标签映射
        Map<String, String> labelMap = Map.of(
                "image",    "图片",
                "document", "文档",
                "video",    "视频",
                "audio",    "音频",
                "other",    "其他"
        );
        // 类型颜色映射
        Map<String, String> colorMap = Map.of(
                "image",    "#67c23a",
                "document", "#409eff",
                "video",    "#f56c6c",
                "audio",    "#e6a23c",
                "other",    "#909399"
        );

        return rawDist.stream().map(row -> {
            String type = String.valueOf(row.get("fileType"));
            long   size = row.get("totalSize") != null
                    ? Long.parseLong(row.get("totalSize").toString()) : 0L;
            int percent = totalSize > 0
                    ? (int) Math.round((double) size / totalSize * 100) : 0;

            Map<String, Object> item = new HashMap<>(5);
            item.put("type",    type);
            item.put("label",   labelMap.getOrDefault(type, "其他"));
            item.put("color",   colorMap.getOrDefault(type, "#909399"));
            item.put("size",    size);
            item.put("percent", percent);
            return item;
        }).collect(Collectors.toList());
    }
}
