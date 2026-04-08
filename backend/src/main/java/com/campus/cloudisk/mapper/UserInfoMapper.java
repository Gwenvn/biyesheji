package com.campus.cloudisk.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.cloudisk.entity.UserInfo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * 用户信息 Mapper 接口
 * <p>继承 MyBatis-Plus BaseMapper，自动提供 CRUD 方法。</p>
 *
 * @author campus-cloud
 * @since 2024-01-01
 */
@Mapper
public interface UserInfoMapper extends BaseMapper<UserInfo> {

    /**
     * 根据用户名查询用户（登录时使用）
     *
     * @param username 用户名
     * @return UserInfo，不存在返回 null
     */
    @Select("SELECT * FROM user_info WHERE username = #{username} LIMIT 1")
    UserInfo selectByUsername(@Param("username") String username);

    /**
     * 根据邮箱查询用户（注册去重检查）
     *
     * @param email 邮箱
     * @return UserInfo，不存在返回 null
     */
    @Select("SELECT * FROM user_info WHERE email = #{email} LIMIT 1")
    UserInfo selectByEmail(@Param("email") String email);

    /**
     * 更新最后登录时间（登录成功时调用）
     *
     * @param userId 用户 ID
     * @return 受影响行数
     */
    @Update("UPDATE user_info SET last_login_at = NOW() WHERE id = #{userId}")
    int updateLastLoginAt(@Param("userId") Long userId);

    /**
     * 更新账号状态（管理员禁用/启用用户）
     *
     * @param userId 用户 ID
     * @param status 1=正常 0=禁用
     * @return 受影响行数
     */
    @Update("UPDATE user_info SET status = #{status}, update_time = NOW() WHERE id = #{userId}")
    int updateStatus(@Param("userId") Long userId, @Param("status") Integer status);

    /**
     * 统计总用户数（管理后台统计卡片用）
     *
     * @return 用户总数
     */
    @Select("SELECT COUNT(*) FROM user_info")
    int countTotal();

    /**
     * 统计今日注册用户数
     *
     * @return 今日注册数
     */
    @Select("SELECT COUNT(*) FROM user_info WHERE DATE(create_time) = CURDATE()")
    int countTodayRegister();

    /**
     * 查询所有用户的存储量总和（管理后台 OSS 统计用）
     *
     * @return 所有用户已用存储量之和（字节）
     */
    @Select("SELECT COALESCE(SUM(storage_used), 0) FROM user_info")
    Long sumStorageUsed();
}
