package com.campus.cloudisk.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 回收站文件 DTO（数据传输对象）
 * <p>
 * 用于将回收站文件信息传输给前端，过滤掉 OSS Key 等敏感内部字段。
 * 对应前端 RecycleBin.vue 中的列表项字段。
 * </p>
 *
 * @author campus-cloud
 * @since 2024-01-01
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecycleBinFileDTO {

    /** 文件 ID */
    private Long id;

    /** 文件名（含扩展名） */
    private String fileName;

    /** 文件大小（字节） */
    private Long fileSize;

    /**
     * 文件类型分类
     * 取值：image / document / video / audio / other
     */
    private String fileType;

    /** 文件扩展名（不含点，如 jpg / pdf / mp4） */
    private String fileExtension;

    /** 原所在目录 ID（0 表示根目录） */
    private Long folderId;

    /** 原所在目录路径（如 /文档/2024 级/，用于展示） */
    private String folderPath;

    /**
     * 软删除时间（移入回收站的时间）
     * 格式：yyyy-MM-dd HH:mm:ss
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime deletedAt;

    /**
     * 文件原始创建/上传时间
     * 格式：yyyy-MM-dd HH:mm:ss
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    /**
     * 预计彻底删除时间（deletedAt + 30 天）
     * 格式：yyyy-MM-dd HH:mm:ss
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime expireTime;
}
