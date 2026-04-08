package com.campus.cloudisk.controller;

import com.campus.cloudisk.common.Result;
import com.campus.cloudisk.dto.PageDTO;
import com.campus.cloudisk.service.RecycleBinService;
import com.campus.cloudisk.utils.UserContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 回收站控制器
 * <p>
 * 提供回收站相关的 REST API：
 * - 查询回收站文件列表（分页）
 * - 还原文件到原目录
 * - 彻底删除（从 OSS 和数据库中永久删除）
 * - 清空回收站（彻底删除全部文件）
 * </p>
 *
 * @author campus-cloud
 * @since 2024-01-01
 */
@Slf4j
@Tag(name = "回收站管理", description = "回收站文件的查询、还原、彻底删除等接口")
@RestController
@RequestMapping("/api/recycle-bin")
@RequiredArgsConstructor
public class RecycleBinController {

    /** 回收站业务服务 */
    private final RecycleBinService recycleBinService;

    // ===================================================================
    //  查询回收站列表
    // ===================================================================

    /**
     * 分页查询当前用户的回收站文件列表
     *
     * @param page    当前页（从 1 开始）
     * @param size    每页条数（默认 10）
     * @param keyword 文件名模糊搜索关键字（可选）
     * @return 分页结果，包含文件基本信息及软删除时间
     */
    @Operation(summary = "获取回收站列表", description = "分页查询当前登录用户的回收站文件")
    @GetMapping
    public Result<PageDTO<?>> listRecycleBin(
            @Parameter(description = "页码，从1开始") @RequestParam(defaultValue = "1")  int page,
            @Parameter(description = "每页条数")      @RequestParam(defaultValue = "10") int size,
            @Parameter(description = "文件名搜索")    @RequestParam(required = false)    String keyword
    ) {
        // 从 ThreadLocal 中获取当前登录用户 ID
        Long userId = UserContext.getCurrentUserId();
        log.debug("查询用户[{}]回收站，page={}, size={}, keyword={}", userId, page, size, keyword);
        return Result.success(recycleBinService.listRecycleBin(userId, page, size, keyword));
    }

    // ===================================================================
    //  还原文件
    // ===================================================================

    /**
     * 还原单个文件到原目录
     * <p>
     * 将文件的 deleted 标志置为 false，并更新 deletedAt 为 null。
     * 若原目录已被删除，则还原到根目录下。
     * </p>
     *
     * @param fileId 要还原的文件 ID
     * @return 操作结果
     */
    @Operation(summary = "还原文件", description = "将回收站中的文件还原到原目录")
    @PutMapping("/{fileId}/restore")
    public Result<Void> restoreFile(
            @Parameter(description = "文件ID") @PathVariable Long fileId
    ) {
        Long userId = UserContext.getCurrentUserId();
        log.info("用户[{}]还原文件[{}]", userId, fileId);
        recycleBinService.restoreFile(userId, fileId);
        return Result.success();
    }

    /**
     * 批量还原文件
     *
     * @param fileIds 要还原的文件 ID 列表
     * @return 操作结果，包含成功还原的数量
     */
    @Operation(summary = "批量还原文件", description = "批量将回收站文件还原到原目录")
    @PutMapping("/restore/batch")
    public Result<Integer> batchRestoreFiles(
            @Parameter(description = "文件ID列表") @RequestBody @Validated List<Long> fileIds
    ) {
        Long userId = UserContext.getCurrentUserId();
        log.info("用户[{}]批量还原文件，数量={}", userId, fileIds.size());
        int count = recycleBinService.batchRestoreFiles(userId, fileIds);
        return Result.success(count);
    }

    // ===================================================================
    //  彻底删除
    // ===================================================================

    /**
     * 彻底删除单个文件
     * <p>
     * 从数据库删除记录，并异步调用 OSS SDK 删除对象。
     * 此操作不可逆，请谨慎调用。
     * </p>
     *
     * @param fileId 要彻底删除的文件 ID
     * @return 操作结果
     */
    @Operation(summary = "彻底删除文件", description = "从回收站彻底删除文件（不可恢复）")
    @DeleteMapping("/{fileId}")
    public Result<Void> deleteFilePermanently(
            @Parameter(description = "文件ID") @PathVariable Long fileId
    ) {
        Long userId = UserContext.getCurrentUserId();
        log.warn("用户[{}]彻底删除文件[{}]", userId, fileId);
        recycleBinService.deleteFilePermanently(userId, fileId);
        return Result.success();
    }

    /**
     * 批量彻底删除文件
     *
     * @param fileIds 要彻底删除的文件 ID 列表
     * @return 操作结果，包含成功删除的数量
     */
    @Operation(summary = "批量彻底删除", description = "批量彻底删除回收站文件")
    @DeleteMapping("/batch")
    public Result<Integer> batchDeleteFiles(
            @Parameter(description = "文件ID列表") @RequestBody @Validated List<Long> fileIds
    ) {
        Long userId = UserContext.getCurrentUserId();
        log.warn("用户[{}]批量彻底删除文件，数量={}", userId, fileIds.size());
        int count = recycleBinService.batchDeletePermanently(userId, fileIds);
        return Result.success(count);
    }

    /**
     * 清空回收站（彻底删除当前用户所有回收站文件）
     *
     * @return 操作结果，包含清空的文件数量
     */
    @Operation(summary = "清空回收站", description = "彻底删除当前用户回收站内的所有文件")
    @DeleteMapping("/clear")
    public Result<Integer> clearRecycleBin() {
        Long userId = UserContext.getCurrentUserId();
        log.warn("用户[{}]清空回收站", userId);
        int count = recycleBinService.clearRecycleBin(userId);
        return Result.success(count);
    }
}
