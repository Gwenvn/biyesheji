package com.campus.cloudisk.controller;

import com.campus.cloudisk.common.ErrorCode;
import com.campus.cloudisk.common.Result;
import com.campus.cloudisk.entity.UserInfo;
import com.campus.cloudisk.exception.BusinessException;
import com.campus.cloudisk.mapper.UserInfoMapper;
import com.campus.cloudisk.utils.JwtUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 认证控制器（登录 / 注册 / 退出）
 * <p>
 * 接口路径均在 WebMvcConfig 白名单中，无需 JWT Token 即可访问：
 * <ul>
 *   <li>POST /api/auth/login    — 用户登录</li>
 *   <li>POST /api/auth/register — 用户注册</li>
 *   <li>POST /api/auth/logout   — 退出登录（可选，前端清除 token 即可）</li>
 * </ul>
 * </p>
 *
 * @author campus-cloud
 * @since 2024-01-01
 */
@Slf4j
@Tag(name = "认证管理", description = "登录、注册、退出登录")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserInfoMapper userInfoMapper;
    private final JwtUtils       jwtUtils;

    /**
     * BCrypt 密码加密器（单例，Spring Boot 中通常注册为 Bean）
     * 此处直接 new，cost factor 默认 10，适合毕业设计演示
     */
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    /** 新用户默认存储配额（从 application.yml 读取，默认 5GB） */
    @Value("${file.user-quota:5368709120}")
    private long defaultQuota;

    // ===================================================================
    //  登录
    // ===================================================================

    /**
     * 用户登录
     * <p>
     * 流程：校验参数 → 查询用户 → 验证密码 → 检查账号状态 → 签发 JWT Token
     * </p>
     *
     * @param req 登录请求体（username + password）
     * @return 包含 token 和 userInfo 的响应数据
     */
    @Operation(summary = "用户登录", description = "校验用户名密码，成功返回 JWT Token 和用户信息")
    @PostMapping("/login")
    public Result<Map<String, Object>> login(@Valid @RequestBody LoginRequest req) {
        log.info("[Auth] 登录请求，username={}", req.getUsername());

        // 1. 根据用户名查询用户
        UserInfo user = userInfoMapper.selectByUsername(req.getUsername());
        if (user == null) {
            // 用户名不存在时，与密码错误返回相同提示（防止用户名枚举攻击）
            throw new BusinessException(ErrorCode.WRONG_CREDENTIALS);
        }

        // 2. 验证密码（BCrypt 比对）
        if (!passwordEncoder.matches(req.getPassword(), user.getPassword())) {
            log.warn("[Auth] 密码错误，username={}", req.getUsername());
            throw new BusinessException(ErrorCode.WRONG_CREDENTIALS);
        }

        // 3. 检查账号状态
        if (user.getStatus() == null || user.getStatus() != 1) {
            log.warn("[Auth] 账号已禁用，username={}", req.getUsername());
            throw new BusinessException(ErrorCode.ACCOUNT_DISABLED);
        }

        // 4. 签发 JWT Token
        String token = jwtUtils.generateToken(user.getId(), user.getUsername(), user.getRole());

        // 5. 更新最后登录时间（异步不阻断登录流程）
        try {
            userInfoMapper.updateLastLoginAt(user.getId());
        } catch (Exception e) {
            log.warn("[Auth] 更新最后登录时间失败，userId={}：{}", user.getId(), e.getMessage());
        }

        // 6. 构造响应（不返回密码字段，password 已被 @JsonIgnore 处理，双重保险）
        Map<String, Object> data = new HashMap<>(2);
        data.put("token",    token);
        data.put("userInfo", buildSafeUserInfo(user));

        log.info("[Auth] 登录成功，username={}, role={}", user.getUsername(), user.getRole());
        return Result.success("登录成功", data);
    }

    // ===================================================================
    //  注册
    // ===================================================================

    /**
     * 用户注册
     * <p>
     * 流程：校验参数 → 检查用户名/邮箱唯一性 → 密码加密 → 写入数据库
     * </p>
     *
     * @param req 注册请求体
     * @return 注册成功后返回用户信息（不含 Token，需重新登录）
     */
    @Operation(summary = "用户注册", description = "注册新账号，用户名和邮箱全局唯一")
    @PostMapping("/register")
    public Result<Map<String, Object>> register(@Valid @RequestBody RegisterRequest req) {
        log.info("[Auth] 注册请求，username={}, email={}", req.getUsername(), req.getEmail());

        // 1. 检查用户名是否已存在
        if (userInfoMapper.selectByUsername(req.getUsername()) != null) {
            throw new BusinessException(ErrorCode.USERNAME_EXISTS);
        }

        // 2. 检查邮箱是否已存在（邮箱非必填，有值时才检查）
        if (req.getEmail() != null && !req.getEmail().isBlank()) {
            if (userInfoMapper.selectByEmail(req.getEmail()) != null) {
                throw new BusinessException(ErrorCode.EMAIL_EXISTS);
            }
        }

        // 3. 构造新用户实体
        UserInfo newUser = new UserInfo()
                .setUsername(req.getUsername())
                // BCrypt 加密密码（每次加密结果不同，防止彩虹表攻击）
                .setPassword(passwordEncoder.encode(req.getPassword()))
                .setEmail(req.getEmail())
                .setRole("USER")           // 新注册用户默认为普通用户
                .setStatus(1)              // 默认启用
                .setStorageUsed(0L)        // 初始已用存储为 0
                .setStorageQuota(defaultQuota)  // 默认 5GB 配额
                .setFileCount(0);

        // 4. 写入数据库（createTime 由 MyBatisPlusConfig.MetaObjectHandler 自动填充）
        userInfoMapper.insert(newUser);

        // 5. 构造响应（注册成功后返回基本信息，前端引导用户去登录）
        Map<String, Object> data = new HashMap<>(1);
        data.put("userInfo", buildSafeUserInfo(newUser));

        log.info("[Auth] 注册成功，userId={}, username={}", newUser.getId(), newUser.getUsername());
        return Result.success("注册成功，请登录", data);
    }

    // ===================================================================
    //  退出登录
    // ===================================================================

    /**
     * 退出登录
     * <p>
     * JWT 是无状态的，服务端不维护 Token 黑名单（毕业设计简化）。
     * 前端调用此接口后，自行清除本地 Token 即完成退出。
     * 生产环境可在此处将 Token 加入 Redis 黑名单。
     * </p>
     */
    @Operation(summary = "退出登录", description = "服务端记录退出日志，前端清除本地 Token")
    @PostMapping("/logout")
    public Result<Void> logout() {
        // 此接口在拦截器白名单外，可获取用户上下文
        log.info("[Auth] 用户退出登录");
        return Result.success();
    }

    // ===================================================================
    //  请求体 DTO（内部静态类）
    // ===================================================================

    /** 登录请求体 */
    @Data
    public static class LoginRequest {

        @NotBlank(message = "用户名不能为空")
        @Size(min = 2, max = 50, message = "用户名长度为 2-50 个字符")
        private String username;

        @NotBlank(message = "密码不能为空")
        @Size(min = 6, max = 100, message = "密码长度为 6-100 个字符")
        private String password;
    }

    /** 注册请求体 */
    @Data
    public static class RegisterRequest {

        @NotBlank(message = "用户名不能为空")
        @Size(min = 2, max = 50, message = "用户名长度为 2-50 个字符")
        private String username;

        @NotBlank(message = "密码不能为空")
        @Size(min = 6, max = 100, message = "密码长度为 6-100 个字符")
        private String password;

        @Email(message = "邮箱格式不正确")
        private String email;
    }

    // ===================================================================
    //  私有辅助方法
    // ===================================================================

    /**
     * 构造安全的用户信息 Map（去除敏感字段）
     * <p>
     * 虽然 UserInfo.password 已加 @JsonIgnore，使用 Map 可以更精确地控制返回字段。
     * </p>
     */
    private Map<String, Object> buildSafeUserInfo(UserInfo user) {
        Map<String, Object> info = new HashMap<>(10);
        info.put("id",           user.getId());
        info.put("username",     user.getUsername());
        info.put("email",        user.getEmail());
        info.put("avatarUrl",    user.getAvatarUrl());
        info.put("role",         user.getRole());
        info.put("status",       user.getStatus());
        info.put("storageUsed",  user.getStorageUsed());
        info.put("storageQuota", user.getStorageQuota());
        info.put("fileCount",    user.getFileCount());
        info.put("createTime",   user.getCreateTime());
        return info;
    }
}
