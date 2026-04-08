package com.campus.cloudisk.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

/**
 * 文件信息实体类（对应数据库表 file_info）
 * <p>
 * 该表存储所有用户文件的元数据信息，包括：
 * <ul>
 *   <li>普通文件：文件名、大小、类型、OSS Key 等</li>
 *   <li>文件夹：通过 isFolder = true 标识</li>
 *   <li>回收站：通过 deleted = true + deletedAt 字段实现软删除</li>
 * </ul>
 * </p>
 *
 * 建表 SQL 示例：
 * <pre>
 * CREATE TABLE file_info (
 *   id            BIGINT       NOT NULL PRIMARY KEY COMMENT '雪花算法ID',
 *   user_id       BIGINT       NOT NULL             COMMENT '所属用户ID',
 *   file_name     VARCHAR(255) NOT NULL             COMMENT '文件名（含扩展名）',
 *   file_size     BIGINT                            COMMENT '文件大小（字节）',
 *   file_type     VARCHAR(50)                       COMMENT '文件类型：image/document/video/audio/other',
 *   file_extension VARCHAR(20)                      COMMENT '扩展名（不含点）',
 *   oss_key       VARCHAR(500)                      COMMENT 'OSS对象Key（路径）',
 *   oss_url       VARCHAR(1000)                     COMMENT 'OSS访问URL（公开文件）',
 *   md5           VARCHAR(32)                       COMMENT '文件MD5（秒传/去重用）',
 *   folder_id     BIGINT       DEFAULT 0            COMMENT '所在目录ID，0为根目录',
 *   folder_path   VARCHAR(2000)                     COMMENT '目录路径（冗余存储，查询用）',
 *   is_folder     TINYINT(1)   DEFAULT 0            COMMENT '是否为文件夹：0否 1是',
 *   is_public     TINYINT(1)   DEFAULT 0            COMMENT '是否公开共享：0否 1是',
 *   share_code    VARCHAR(20)                       COMMENT '分享码（公开分享时生成）',
 *   download_count INT         DEFAULT 0            COMMENT '下载次数',
 *   deleted       TINYINT(1)   DEFAULT 0            COMMENT '软删除标志：0正常 1已删除',
 *   deleted_at    DATETIME                          COMMENT '软删除时间（移入回收站时间）',
 *   create_time   DATETIME     NOT NULL             COMMENT '创建时间',
 *   update_time   DATETIME                          COMMENT '最后修改时间',
 *   INDEX idx_user_id (user_id),
 *   INDEX idx_folder_id (folder_id),
 *   INDEX idx_md5 (md5),
 *   INDEX idx_deleted (user_id, deleted)
 * ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文件信息表';
 * </pre>
 *
 * @author campus-cloud
 * @since 2024-01-01
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("file_info")
public class FileInfo {

    // ===== 主键 =====

    /**
     * 文件唯一ID（雪花算法生成）
     * IdType.ASSIGN_ID 由 MyBatis-Plus 自动填充，无需手动设置
     */
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    // ===== 所属关系 =====

    /**
     * 所属用户ID（关联 user_info.id）
     */
    @TableField("user_id")
    private Long userId;

    /**
     * 所在目录ID（0 表示根目录）
     * 用于构建树形文件系统结构
     */
    @TableField("folder_id")
    private Long folderId;

    /**
     * 目录路径（冗余存储，避免递归查询）
     * 格式：/文档/2024级/ 或 / (根目录)
     * 还原文件时展示原始路径
     */
    @TableField("folder_path")
    private String folderPath;

    // ===== 文件基本信息 =====

    /**
     * 文件名（含扩展名，如 "毕业论文.pdf"）
     */
    @TableField("file_name")
    private String fileName;

    /**
     * 文件大小（字节 byte）
     * 文件夹时为 null 或 0
     */
    @TableField("file_size")
    private Long fileSize;

    /**
     * 文件类型分类
     * 取值：image / document / video / audio / other
     * 用于前端图标显示和筛选过滤
     */
    @TableField("file_type")
    private String fileType;

    /**
     * 文件扩展名（不含点，如 pdf / jpg / mp4）
     * 小写存储，方便大小写不敏感的类型判断
     */
    @TableField("file_extension")
    private String fileExtension;

    // ===== OSS 存储信息 =====

    /**
     * OSS 对象 Key（即 OSS 中的文件路径）
     * 格式：campus-files/{userId}/{year}/{month}/{uuid}.{ext}
     * 示例：campus-files/100001/2024/03/a1b2c3d4.pdf
     * 彻底删除时通过此 Key 调用 OSS SDK 删除对象
     */
    @TableField("oss_key")
    private String ossKey;

    /**
     * OSS 文件访问 URL（仅公开文件有效）
     * 私有文件通过预签名 URL 访问，此字段为空
     */
    @TableField("oss_url")
    private String ossUrl;

    /**
     * 文件 MD5 值（32位小写十六进制）
     * 用途：
     * 1. 秒传：上传前检查 MD5 是否已存在
     * 2. 去重：相同 MD5 文件可复用 OSS 对象，节省存储
     * 3. 完整性校验
     */
    @TableField("md5")
    private String md5;

    // ===== 文件类型标志 =====

    /**
     * 是否为文件夹
     * true：目录节点（无 ossKey，size 为 0）
     * false：普通文件
     */
    @TableField("is_folder")
    private Boolean isFolder;

    /**
     * 是否公开分享
     * true：已开启链接分享，任何人可访问
     * false：私有文件，仅本人可见
     */
    @TableField("is_public")
    private Boolean isPublic;

    /**
     * 分享码（公开分享时生成的随机短码，如 abc123）
     * 通过分享码可以不登录直接访问公开文件
     */
    @TableField("share_code")
    private String shareCode;

    /**
     * 下载次数（每次生成预签名 URL 即记为一次下载）
     */
    @TableField("download_count")
    private Integer downloadCount;

    // ===== 回收站相关字段 =====

    /**
     * 软删除标志
     * false（0）：正常文件
     * true（1）：已移入回收站
     *
     * 注意：@TableLogic 会让 MyBatis-Plus 的 selectById 等方法
     * 自动加上 WHERE deleted = 0 条件，无需手动过滤
     * 回收站查询需要用 eq(FileInfo::getDeleted, true) 显式指定
     */
    @TableLogic(value = "0", delval = "1")
    @TableField("deleted")
    private Boolean deleted;

    /**
     * 软删除时间（移入回收站的时间）
     * null 表示正常文件，非 null 表示已在回收站
     * 定时任务以此字段判断是否超过保留期（默认 30 天）
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField("deleted_at")
    private LocalDateTime deletedAt;

    // ===== 时间戳字段 =====

    /**
     * 创建时间（文件上传时间）
     * @TableField(fill = INSERT) 由 MetaObjectHandler 自动填充
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /**
     * 最后修改时间（文件重命名、移动等操作时更新）
     * @TableField(fill = INSERT_UPDATE) 由 MetaObjectHandler 自动填充
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
