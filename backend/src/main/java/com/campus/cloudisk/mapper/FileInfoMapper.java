package com.campus.cloudisk.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.cloudisk.entity.FileInfo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 文件信息 Mapper 接口
 * <p>
 * 继承 MyBatis-Plus {@link BaseMapper}，自动提供以下方法：
 * <ul>
 *   <li>insert / deleteById / updateById / selectById</li>
 *   <li>selectList / selectPage / selectCount 等</li>
 * </ul>
 * 本接口只需定义 BaseMapper 未覆盖的自定义 SQL 方法。
 * </p>
 *
 * 注意事项：
 * <ul>
 *   <li>受 {@code @TableLogic} 影响，所有 BaseMapper 的查询方法会自动追加 {@code AND deleted = 0}
 *       条件，若需查询回收站（deleted=1）的文件，必须手动使用 Wrapper 指定条件</li>
 *   <li>{@code decreaseUserStorage} 使用的是 user_info 表，确保该表存在</li>
 * </ul>
 *
 * @author campus-cloud
 * @since 2024-01-01
 */
@Mapper
public interface FileInfoMapper extends BaseMapper<FileInfo> {

    // ===================================================================
    //  用户存储量相关
    // ===================================================================

    /**
     * 增加用户已用存储量（文件上传成功后调用）
     * <p>
     * SQL：UPDATE user_info SET storage_used = storage_used + #{bytes}
     *      WHERE id = #{userId}
     * </p>
     *
     * @param userId 用户 ID
     * @param bytes  要增加的字节数（必须 > 0）
     * @return 受影响的行数
     */
    @Update("UPDATE user_info SET storage_used = storage_used + #{bytes} WHERE id = #{userId}")
    int increaseUserStorage(@Param("userId") Long userId, @Param("bytes") Long bytes);

    /**
     * 减少用户已用存储量（文件彻底删除后调用）
     * <p>
     * 使用 GREATEST 防止 storage_used 出现负数（防御性编程）。
     * SQL：UPDATE user_info
     *      SET storage_used = GREATEST(0, storage_used - #{bytes})
     *      WHERE id = #{userId}
     * </p>
     *
     * @param userId 用户 ID
     * @param bytes  要减少的字节数（必须 > 0）
     * @return 受影响的行数
     */
    @Update("UPDATE user_info SET storage_used = GREATEST(0, storage_used - #{bytes}) WHERE id = #{userId}")
    int decreaseUserStorage(@Param("userId") Long userId, @Param("bytes") Long bytes);

    // ===================================================================
    //  MD5 相关（秒传功能）
    // ===================================================================

    /**
     * 根据 MD5 查询已存在的文件（用于秒传检查）
     * <p>
     * 注意：此查询不受 @TableLogic 约束，因为使用了原生 SQL。
     * 这里只查询未删除的文件（deleted = 0），已删除的文件不能用于秒传。
     * </p>
     *
     * @param md5 文件 MD5 值（32位小写十六进制）
     * @return 第一个匹配的 FileInfo，若不存在则返回 null
     */
    @Select("SELECT * FROM file_info WHERE md5 = #{md5} AND deleted = 0 LIMIT 1")
    FileInfo selectByMd5(@Param("md5") String md5);

    // ===================================================================
    //  统计相关（管理后台使用）
    // ===================================================================

    /**
     * 统计指定用户的文件总数（不含文件夹，不含已删除）
     *
     * @param userId 用户 ID
     * @return 文件总数
     */
    @Select("SELECT COUNT(*) FROM file_info WHERE user_id = #{userId} AND is_folder = 0 AND deleted = 0")
    int countUserFiles(@Param("userId") Long userId);

    /**
     * 统计指定用户各类型文件的数量和大小（管理后台文件类型分布图用）
     * <p>
     * 返回结果示例：[{fileType:'image', count:50, totalSize:104857600}, ...]
     * </p>
     *
     * @param userId 用户 ID（传 null 则统计所有用户）
     * @return 分组统计结果列表
     */
    @Select({
        "<script>",
        "SELECT file_type AS fileType,",
        "       COUNT(*)    AS fileCount,",
        "       SUM(file_size) AS totalSize",
        "FROM file_info",
        "WHERE is_folder = 0 AND deleted = 0",
        "<if test='userId != null'> AND user_id = #{userId} </if>",
        "GROUP BY file_type",
        "</script>"
    })
    List<java.util.Map<String, Object>> countFilesByType(@Param("userId") Long userId);

    /**
     * 查询存储用量最多的 Top N 用户（管理后台存储统计用）
     * <p>
     * 注意：此方法直接查询 user_info 表的 storage_used 字段，
     * 该字段由 increaseUserStorage / decreaseUserStorage 维护。
     * </p>
     *
     * @param limit 查询条数，通常传 5
     * @return [{userId, username, storageUsed, fileCount}, ...]
     */
    @Select({
        "SELECT u.id AS userId,",
        "       u.username,",
        "       u.storage_used AS storageUsed,",
        "       COUNT(f.id)    AS fileCount",
        "FROM user_info u",
        "LEFT JOIN file_info f ON f.user_id = u.id AND f.is_folder = 0 AND f.deleted = 0",
        "GROUP BY u.id, u.username, u.storage_used",
        "ORDER BY u.storage_used DESC",
        "LIMIT #{limit}"
    })
    List<java.util.Map<String, Object>> selectTopUsersByStorage(@Param("limit") int limit);

    // ===================================================================
    //  回收站专用查询
    // ===================================================================

    /**
     * 统计用户回收站文件总数（用于顶部提示徽标）
     * <p>
     * 注意：因为 @TableLogic 会自动加 deleted=0，这里使用原生 SQL 绕过限制。
     * </p>
     *
     * @param userId 用户 ID
     * @return 回收站文件总数（不含文件夹）
     */
    @Select("SELECT COUNT(*) FROM file_info WHERE user_id = #{userId} AND deleted = 1 AND is_folder = 0")
    int countRecycleBinFiles(@Param("userId") Long userId);

    /**
     * 查询用户回收站中所有文件的 OSS Key 列表（批量清空时使用）
     * <p>用于清空回收站前批量获取需要从 OSS 删除的对象列表</p>
     *
     * @param userId 用户 ID
     * @return OSS Key 列表（排除空字符串）
     */
    @Select("SELECT oss_key FROM file_info WHERE user_id = #{userId} AND deleted = 1 AND oss_key IS NOT NULL AND oss_key != ''")
    List<String> selectRecycleBinOssKeys(@Param("userId") Long userId);
}
