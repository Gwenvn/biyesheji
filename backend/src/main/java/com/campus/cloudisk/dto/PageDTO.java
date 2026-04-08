package com.campus.cloudisk.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 通用分页结果 DTO
 * <p>
 * 与 MyBatis-Plus {@code Page<T>} 保持字段一致，便于前端统一处理。
 * 对应前端 api 层中 { records, total, current, size, pages } 结构。
 * </p>
 *
 * @param <T> 记录类型
 * @author campus-cloud
 * @since 2024-01-01
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PageDTO<T> {

    /** 当前页数据列表 */
    private List<T> records;

    /** 总记录数 */
    private long total;

    /** 当前页码（从 1 开始） */
    private int current;

    /** 每页条数 */
    private int size;

    /** 总页数 */
    private int pages;
}
