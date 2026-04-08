package com.campus.cloudisk.utils;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * JWT Token 工具类
 * <p>
 * 负责 Token 的生成与解析，与 {@link com.campus.cloudisk.config.JwtInterceptor} 使用相同的密钥和算法，
 * 保证签发和验证逻辑完全对称。
 * <br/>
 * 使用场景：
 * <ul>
 *   <li>登录成功后调用 {@link #generateToken} 签发 Token 返回给前端</li>
 *   <li>刷新 Token 接口中调用 {@link #generateToken} 重新签发</li>
 *   <li>拦截器中调用 {@link #parseToken} 验证并解析 Token（通常直接在拦截器内处理）</li>
 * </ul>
 * </p>
 *
 * @author campus-cloud
 * @since 2024-01-01
 */
@Slf4j
@Component
public class JwtUtils {

    /** JWT 签名密钥（从 application.yml jwt.secret 注入） */
    @Value("${jwt.secret}")
    private String jwtSecret;

    /** Token 有效期（毫秒，从 application.yml jwt.expire 注入，默认 24 小时） */
    @Value("${jwt.expire:86400000}")
    private long jwtExpire;

    // ===== Claims 中存储用户信息的键名（与 JwtInterceptor 保持一致）=====
    private static final String CLAIM_USER_ID   = "userId";
    private static final String CLAIM_USERNAME  = "username";
    private static final String CLAIM_USER_ROLE = "role";

    // ===================================================================
    //  Token 生成
    // ===================================================================

    /**
     * 生成 JWT Token（登录成功时调用）
     *
     * @param userId   用户 ID（Long 类型，写入 Claims）
     * @param username 用户名（写入 Claims，便于日志和调试）
     * @param role     用户角色（如 "USER" / "ADMIN"，用于权限校验）
     * @return 签名后的 JWT Token 字符串（不含 "Bearer " 前缀）
     */
    public String generateToken(Long userId, String username, String role) {
        // 构造 Payload（Claims）
        Map<String, Object> claims = new HashMap<>(4);
        claims.put(CLAIM_USER_ID,   userId);
        claims.put(CLAIM_USERNAME,  username);
        claims.put(CLAIM_USER_ROLE, role);

        Date now    = new Date();
        Date expire = new Date(now.getTime() + jwtExpire);

        String token = Jwts.builder()
                // 主题（subject）：存储用户 ID 的字符串形式（标准 JWT 字段）
                .setSubject(String.valueOf(userId))
                // 自定义载荷（userId / username / role）
                .addClaims(claims)
                // 签发时间
                .setIssuedAt(now)
                // 过期时间
                .setExpiration(expire)
                // 使用 HMAC-SHA256 签名
                .signWith(buildKey(), SignatureAlgorithm.HS256)
                .compact();

        log.debug("[JwtUtils] Token 签发成功，userId={}, username={}, expireAt={}", userId, username, expire);
        return token;
    }

    // ===================================================================
    //  Token 解析
    // ===================================================================

    /**
     * 解析 JWT Token，返回 Claims（载荷）
     * <p>若 Token 已过期或签名无效，将抛出 JJWT 的相关异常，由调用方处理。</p>
     *
     * @param token 纯 JWT Token 字符串（不含 "Bearer " 前缀）
     * @return Claims 对象，包含 userId / username / role 等信息
     */
    public Claims parseToken(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(buildKey())
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    /**
     * 从 Token 中提取用户 ID
     *
     * @param token 纯 JWT Token 字符串
     * @return 用户 ID，解析失败返回 null
     */
    public Long getUserIdFromToken(String token) {
        try {
            Claims claims = parseToken(token);
            Object obj = claims.get(CLAIM_USER_ID);
            if (obj instanceof Integer) return ((Integer) obj).longValue();
            if (obj instanceof Long)    return (Long) obj;
            if (obj instanceof String)  return Long.parseLong((String) obj);
            return null;
        } catch (Exception e) {
            log.warn("[JwtUtils] 提取 userId 失败：{}", e.getMessage());
            return null;
        }
    }

    /**
     * 从 Token 中提取用户名
     *
     * @param token 纯 JWT Token 字符串
     * @return 用户名，解析失败返回 null
     */
    public String getUsernameFromToken(String token) {
        try {
            return parseToken(token).get(CLAIM_USERNAME, String.class);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 验证 Token 是否有效（签名正确且未过期）
     *
     * @param token 纯 JWT Token 字符串
     * @return true 表示有效
     */
    public boolean isTokenValid(String token) {
        try {
            Claims claims = parseToken(token);
            // 检查是否已过期
            return !claims.getExpiration().before(new Date());
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 获取 Token 的剩余有效时间（毫秒）
     *
     * @param token 纯 JWT Token 字符串
     * @return 剩余毫秒数；若 Token 无效则返回 0
     */
    public long getTokenRemainingMs(String token) {
        try {
            Date expiration = parseToken(token).getExpiration();
            long remaining = expiration.getTime() - System.currentTimeMillis();
            return Math.max(0, remaining);
        } catch (Exception e) {
            return 0;
        }
    }

    // ===================================================================
    //  私有辅助方法
    // ===================================================================

    /**
     * 将字符串密钥转换为 HMAC-SHA256 签名 Key
     * <p>
     * Keys.hmacShaKeyFor 要求密钥至少 256 bit（32 字节），
     * 若 jwtSecret 不足 32 字符，JJWT 会抛出 WeakKeyException。
     * application.yml 中的默认密钥已满足长度要求。
     * </p>
     */
    private Key buildKey() {
        return Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
    }
}
