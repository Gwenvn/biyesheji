package com.campus.cloudisk.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

/**
 * 用户信息实体类（对应数据库表 user_info）
 *
 * 建表 SQL：
 * <pre>
 * CREATE TABLE user_info (
 *   id            BIGINT        NOT NULL PRIMARY KEY COMMENT '用户ID（雪花算法）',
 *   username      VARCHAR(50)   NOT NULL UNIQUE      COMMENT '用户名（登录用，唯一）',
 *   password      VARCHAR(100)  NOT NULL             COMMENT '密码（BCrypt加密存储）',
 *   email         VARCHAR(100)  UNIQUE               COMMENT '邮箱（唯一）',
 *   avatar_url    VARCHAR(500)                       COMMENT '头像URL（OSS地址）',
 *   role          VARCHAR(20)   DEFAULT 'USER'       COMMENT '角色：USER普通用户 / ADMIN管理员',
 *   status        TINYINT(1)    DEFAULT 1            COMMENT '账号状态：1正常 0禁用',
 *   storage_used  BIGINT        DEFAULT 0            COMMENT '已用存储量（字节）',
 *   storage_quota BIGINT        DEFAULT 5368709120   COMMENT '存储配额（字节，默认5GB）',
 *   file_count    INT           DEFAULT 0            COMMENT '文件总数',
 *   last_login_at DATETIME                           COMMENT '最后登录时间',
 *   create_time   DATETIME      NOT NULL             COMMENT '注册时间',
 *   update_time   DATETIME                           COMMENT '信息更新时间',
 *   INDEX idx_username (username),
 *   INDEX idx_email (email)
 * ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户信息表';
 *
 * -- 初始管理员账号（密码：admin123，BCrypt加密）
 * INSERT INTO user_info (id, username, password, email, role, status, create_time)
 * VALUES (1, 'admin', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi',
 *         'admin@campus.edu', 'ADMIN', 1, NOW());
 * </pre>
 *
 * @author campus-cloud
 * @since 2024-01-01
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("user_info")
public class UserInfo {

    // ===== 主键 =====

    /** 用户唯一ID（雪花算法生成） */
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    // ===== 账号信息 =====

    /** 用户名（登录凭证，3-20个字符，数据库 UNIQUE 约束） */
    @TableField("username")
    private String username;

    /**
     * 密码（BCrypt 加密存储，绝不明文）
     * @JsonIgnore 确保接口响应中不会序列化密码字段
     */
    @JsonIgnore
    @TableField("password")
    private String password;

    /** 邮箱（可选，数据库 UNIQUE 约束） */
    @TableField("email")
    private String email;

    /** 头像 URL（OSS 地址，为空时前端显示默认头像） */
    @TableField("avatar_url")
    private String avatarUrl;

    // ===== 权限信息 =====

    /**
     * 用户角色
     * "USER"  — 普通用户（默认）
     * "ADMIN" — 管理员（可访问 /admin/** 接口）
     */
    @TableField("role")
    private String role;

    /**
     * 账号状态
     * 1 — 正常，可正常登录
     * 0 — 已禁用，登录时返回 ACCOUNT_DISABLED 错误
     */
    @TableField("status")
    private Integer status;

    // ===== 存储信息 =====

    /**
     * 已用存储量（字节）
     * 由 FileInfoMapper.increaseUserStorage / decreaseUserStorage 维护
     * 文件上传成功后 +fileSize，彻底删除后 -fileSize
     */
    @TableField("storage_used")
    private Long storageUsed;

    /**
     * 存储配额（字节，默认 5GB = 5 * 1024^3 = 5368709120）
     * 管理员可在后台为用户调整配额
     */
    @TableField("storage_quota")
    private Long storageQuota;

    /**
     * 文件总数（冗余字段，避免频繁 COUNT 查询）
     * 上传时 +1，删除时 -1（包括移入回收站不减，彻底删除才减）
     */
    @TableField("file_count")
    private Integer fileCount;

    // ===== 时间字段 =====

    /** 最后登录时间（每次登录成功时更新） */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField("last_login_at")
    private LocalDateTime lastLoginAt;

    /** 注册时间（INSERT 时自动填充） */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /** 信息更新时间（INSERT / UPDATE 时自动填充） */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
