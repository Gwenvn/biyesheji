-- =============================================================================
--  校园云盘系统 - 数据库初始化脚本
--  数据库：MySQL 8.0+
--  字符集：utf8mb4（支持中文、emoji）
--  执行方式：mysql -u root -p < schema.sql
-- =============================================================================

-- 创建数据库（若已存在则跳过）
CREATE DATABASE IF NOT EXISTS campus_cloud
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_unicode_ci;

USE campus_cloud;

-- =============================================================================
--  1. 用户信息表（user_info）
-- =============================================================================
DROP TABLE IF EXISTS `user_info`;
CREATE TABLE `user_info` (
    `id`            BIGINT        NOT NULL                    COMMENT '用户ID（雪花算法）',
    `username`      VARCHAR(50)   NOT NULL                    COMMENT '用户名（登录凭证，唯一）',
    `password`      VARCHAR(100)  NOT NULL                    COMMENT '密码（BCrypt加密，不存明文）',
    `email`         VARCHAR(100)  DEFAULT NULL                COMMENT '邮箱（可选，唯一）',
    `avatar_url`    VARCHAR(500)  DEFAULT NULL                COMMENT '头像OSS地址',
    `role`          VARCHAR(20)   NOT NULL DEFAULT 'USER'     COMMENT '角色：USER普通用户 / ADMIN管理员',
    `status`        TINYINT(1)    NOT NULL DEFAULT 1          COMMENT '账号状态：1正常 0禁用',
    `storage_used`  BIGINT        NOT NULL DEFAULT 0          COMMENT '已用存储量（字节）',
    `storage_quota` BIGINT        NOT NULL DEFAULT 5368709120 COMMENT '存储配额（字节，默认5GB）',
    `file_count`    INT           NOT NULL DEFAULT 0          COMMENT '文件总数（不含文件夹和已删除）',
    `last_login_at` DATETIME      DEFAULT NULL                COMMENT '最后登录时间',
    `create_time`   DATETIME      NOT NULL                    COMMENT '注册时间',
    `update_time`   DATETIME      DEFAULT NULL                COMMENT '信息更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_username` (`username`),
    UNIQUE KEY `uk_email` (`email`),
    KEY `idx_role`   (`role`),
    KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='用户信息表';

-- =============================================================================
--  2. 文件信息表（file_info）
--  说明：文件夹也存储在此表，通过 is_folder 字段区分
-- =============================================================================
DROP TABLE IF EXISTS `file_info`;
CREATE TABLE `file_info` (
    `id`             BIGINT        NOT NULL                COMMENT '文件ID（雪花算法）',
    `user_id`        BIGINT        NOT NULL                COMMENT '所属用户ID',
    `file_name`      VARCHAR(255)  NOT NULL                COMMENT '文件名（含扩展名）',
    `file_size`      BIGINT        DEFAULT 0               COMMENT '文件大小（字节，文件夹为0）',
    `file_type`      VARCHAR(50)   DEFAULT NULL            COMMENT '文件类型：image/document/video/audio/other',
    `file_extension` VARCHAR(20)   DEFAULT NULL            COMMENT '文件扩展名（不含点，小写）',
    `oss_key`        VARCHAR(500)  DEFAULT NULL            COMMENT 'OSS对象Key（用于删除和预签名）',
    `oss_url`        VARCHAR(1000) DEFAULT NULL            COMMENT 'OSS公开访问URL（公开文件才有）',
    `md5`            VARCHAR(32)   DEFAULT NULL            COMMENT '文件MD5（秒传和去重）',
    `folder_id`      BIGINT        NOT NULL DEFAULT 0      COMMENT '所在目录ID（0=根目录）',
    `folder_path`    VARCHAR(2000) DEFAULT '/'             COMMENT '目录路径（冗余，如 /文档/2024/）',
    `is_folder`      TINYINT(1)    NOT NULL DEFAULT 0      COMMENT '是否文件夹：0文件 1目录',
    `is_public`      TINYINT(1)    NOT NULL DEFAULT 0      COMMENT '是否公开分享：0私有 1公开',
    `share_code`     VARCHAR(20)   DEFAULT NULL            COMMENT '分享码（公开分享时生成）',
    `download_count` INT           NOT NULL DEFAULT 0      COMMENT '下载次数',
    `deleted`        TINYINT(1)    NOT NULL DEFAULT 0      COMMENT '软删除：0正常 1回收站',
    `deleted_at`     DATETIME      DEFAULT NULL            COMMENT '移入回收站时间',
    `create_time`    DATETIME      NOT NULL                COMMENT '上传/创建时间',
    `update_time`    DATETIME      DEFAULT NULL            COMMENT '最后修改时间',
    PRIMARY KEY (`id`),
    KEY `idx_user_folder`  (`user_id`, `folder_id`, `deleted`),  -- 最常用的查询条件组合
    KEY `idx_user_deleted` (`user_id`, `deleted`),               -- 回收站查询
    KEY `idx_md5`          (`md5`),                               -- 秒传查询
    KEY `idx_share_code`   (`share_code`),                        -- 分享码查询
    KEY `idx_create_time`  (`create_time`)                        -- 时间排序
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='文件信息表（含文件夹）';

-- =============================================================================
--  3. 分享链接表（share_link）
--  说明：用于生成有时效性的分享链接，支持提取码
-- =============================================================================
DROP TABLE IF EXISTS `share_link`;
CREATE TABLE `share_link` (
    `id`          BIGINT       NOT NULL               COMMENT '分享ID（雪花算法）',
    `user_id`     BIGINT       NOT NULL               COMMENT '分享者用户ID',
    `file_id`     BIGINT       NOT NULL               COMMENT '被分享的文件ID',
    `share_code`  VARCHAR(32)  NOT NULL               COMMENT '分享唯一标识（URL中的短码）',
    `extract_code` VARCHAR(10) DEFAULT NULL           COMMENT '提取码（为空则无需提取码）',
    `expire_time` DATETIME     DEFAULT NULL           COMMENT '过期时间（NULL=永久有效）',
    `view_count`  INT          NOT NULL DEFAULT 0     COMMENT '访问次数',
    `status`      TINYINT(1)   NOT NULL DEFAULT 1     COMMENT '状态：1有效 0已失效',
    `create_time` DATETIME     NOT NULL               COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_share_code` (`share_code`),
    KEY `idx_user_id`  (`user_id`),
    KEY `idx_file_id`  (`file_id`),
    KEY `idx_expire`   (`expire_time`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='分享链接表';

-- =============================================================================
--  初始数据
-- =============================================================================

-- 初始管理员账号
-- 账号：admin
-- 密码：admin123（BCrypt加密，登录时使用明文 admin123）
INSERT INTO `user_info`
    (`id`, `username`, `password`, `email`, `role`, `status`,
     `storage_used`, `storage_quota`, `file_count`, `create_time`)
VALUES
    (1000000000000000001,
     'admin',
     '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi',
     'admin@campus.edu',
     'ADMIN', 1, 0, 5368709120, 0,
     NOW());

-- 演示普通用户（密码：123456）
INSERT INTO `user_info`
    (`id`, `username`, `password`, `email`, `role`, `status`,
     `storage_used`, `storage_quota`, `file_count`, `create_time`)
VALUES
    (1000000000000000002,
     'demo_user',
     '$2a$10$92IXUNpkjO0rOQ5byMi.Ye4oKoEa3Ro9llC/.og/at2uheWG/igi.',
     'demo@campus.edu',
     'USER', 1, 0, 5368709120, 0,
     NOW());

-- =============================================================================
--  完成提示
-- =============================================================================
SELECT '数据库初始化完成！' AS message;
SELECT '管理员账号: admin / admin123' AS tip1;
SELECT '演示账号:   demo_user / 123456' AS tip2;
