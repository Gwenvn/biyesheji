package com.campus.cloudisk.config;

import com.campus.cloudisk.utils.UserContext;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.UnsupportedJwtException;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

import java.nio.charset.StandardCharsets;
import java.security.Key;

/**
 * JWT 认证拦截器
 * <p>
 * 拦截所有受保护的 HTTP 请求，验证请求头中的 JWT Token，
 * 并将解析出的用户信息写入 {@link UserContext}（ThreadLocal）。
 * </p>
 *
 * 请求认证流程：
 * <pre>
 * 前端请求
 *   → 请求头：Authorization: Bearer {token}
 *   → JwtInterceptor.preHandle()
 *       ├─ 提取 token
 *       ├─ 验证签名、过期时间
 *       ├─ 解析 userId / username / role
 *       ├─ 写入 UserContext（ThreadLocal）
 *       └─ 放行请求
 *   → Controller 处理业务（通过 UserContext.getCurrentUserId() 获取用户）
 *   → JwtInterceptor.afterCompletion()
 *       └─ 清理 UserContext
 * </pre>
 *
 * 白名单路径（无需 Token）：
 * <ul>
 *   <li>POST /api/auth/login    — 登录</li>
 *   <li>POST /api/auth/register — 注册</li>
 *   <li>GET  /api/share/**      — 公开分享链接访问</li>
 * </ul>
 * 白名单配置在 {@link WebMvcConfig#addInterceptors} 中通过 excludePathPatterns 实现。
 *
 * @author campus-cloud
 * @since 2024-01-01
 */
@Slf4j
@Component
public class JwtInterceptor implements HandlerInterceptor {

    // ===== JWT 配置（从 application.yml 注入）=====

    /**
     * JWT 签名密钥（application.yml → jwt.secret）
     */
    @Value("${jwt.secret}")
    private String jwtSecret;

    /**
     * Token 在请求头中的 Key 名称（application.yml → jwt.header）
     * 默认值：Authorization
     */
    @Value("${jwt.header:Authorization}")
    private String tokenHeader;

    /**
     * Token 前缀（application.yml → jwt.prefix）
     * 默认值：Bearer
     */
    @Value("${jwt.prefix:Bearer}")
    private String tokenPrefix;

    // ===== JWT Claims 中存储用户信息的键名常量 =====

    /** Claims 中用户 ID 对应的键 */
    private static final String CLAIM_USER_ID   = "userId";
    /** Claims 中用户名对应的键 */
    private static final String CLAIM_USERNAME  = "username";
    /** Claims 中用户角色对应的键 */
    private static final String CLAIM_USER_ROLE = "role";

    // ===================================================================
    //  拦截器主方法：preHandle（请求进入时执行）
    // ===================================================================

    /**
     * 请求进入 Controller 之前执行
     * <p>
     * 验证 JWT Token 并设置用户上下文。
     * 返回 true 表示放行，返回 false 表示拦截（已写入 401 响应）。
     * </p>
     *
     * @param request  HTTP 请求
     * @param response HTTP 响应
     * @param handler  目标处理器
     * @return true 放行，false 拦截
     */
    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) throws Exception {
        // 1. 从请求头中提取 Token
        String token = extractToken(request);

        // 2. Token 为空 → 未登录，返回 401
        if (!StringUtils.hasText(token)) {
            log.warn("[JWT] 请求缺少 Token，path={}, ip={}",
                    request.getRequestURI(), getClientIp(request));
            writeUnauthorized(response, "未提供认证 Token，请先登录");
            return false;
        }

        // 3. 解析并验证 Token
        Claims claims;
        try {
            claims = parseToken(token);
        } catch (ExpiredJwtException e) {
            log.warn("[JWT] Token 已过期，path={}", request.getRequestURI());
            writeUnauthorized(response, "登录已过期，请重新登录");
            return false;
        } catch (SignatureException e) {
            log.warn("[JWT] Token 签名无效，path={}", request.getRequestURI());
            writeUnauthorized(response, "Token 签名无效");
            return false;
        } catch (MalformedJwtException | UnsupportedJwtException e) {
            log.warn("[JWT] Token 格式错误：{}", e.getMessage());
            writeUnauthorized(response, "Token 格式错误");
            return false;
        } catch (Exception e) {
            log.error("[JWT] Token 解析异常：{}", e.getMessage(), e);
            writeUnauthorized(response, "Token 解析失败");
            return false;
        }

        // 4. 从 Claims 中提取用户信息
        Long userId   = extractUserId(claims);
        String username = claims.get(CLAIM_USERNAME, String.class);
        String role     = claims.get(CLAIM_USER_ROLE, String.class);

        if (userId == null) {
            log.warn("[JWT] Claims 中缺少 userId，token={}", token.substring(0, Math.min(20, token.length())));
            writeUnauthorized(response, "Token 中缺少用户信息");
            return false;
        }

        // 5. 将用户信息写入 ThreadLocal（当前请求线程可用）
        UserContext.setCurrentUserId(userId);
        if (StringUtils.hasText(username)) {
            UserContext.setCurrentUsername(username);
        }
        if (StringUtils.hasText(role)) {
            UserContext.setCurrentUserRole(role);
        }

        log.debug("[JWT] 认证通过 userId={}, username={}, path={}",
                userId, username, request.getRequestURI());

        return true; // 放行请求
    }

    // ===================================================================
    //  拦截器结束方法：afterCompletion（请求完成后执行）
    // ===================================================================

    /**
     * 请求处理完成后（无论成功或异常），清理 ThreadLocal
     * <p>
     * 防止线程池复用时，下一个请求读到本次请求的用户信息（内存泄漏 / 越权风险）。
     * </p>
     */
    @Override
    public void afterCompletion(HttpServletRequest request,
                                HttpServletResponse response,
                                Object handler,
                                Exception ex) {
        // 必须清理，无论是否有异常
        UserContext.clear();
        log.debug("[JWT] 已清理用户上下文，path={}", request.getRequestURI());
    }

    // ===================================================================
    //  私有辅助方法
    // ===================================================================

    /**
     * 从请求头中提取 JWT Token（去掉 Bearer 前缀）
     *
     * @param request HTTP 请求
     * @return 纯 Token 字符串（不含 "Bearer "），若无则返回 null
     */
    private String extractToken(HttpServletRequest request) {
        String headerValue = request.getHeader(tokenHeader);
        if (!StringUtils.hasText(headerValue)) {
            return null;
        }
        // 标准格式：Bearer eyJhbGciOiJIUzI1NiJ9...
        String prefix = tokenPrefix + " ";
        if (headerValue.startsWith(prefix)) {
            return headerValue.substring(prefix.length()).trim();
        }
        // 兼容不带 Bearer 前缀的情况（部分客户端直接传 Token）
        return headerValue.trim();
    }

    /**
     * 解析 JWT Token，返回 Claims（载荷）
     * <p>
     * 使用 HMAC-SHA256 算法验证签名，密钥来自 application.yml jwt.secret。
     * 若 Token 过期、签名错误或格式不合法，将抛出对应异常。
     * </p>
     *
     * @param token 纯 JWT Token 字符串
     * @return Claims 对象
     */
    private Claims parseToken(String token) {
        // 将字符串密钥转换为 HMAC Key（长度不足 256 bit 时 JJWT 会补全）
        Key key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));

        return Jwts.parserBuilder()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    /**
     * 从 Claims 中提取用户 ID（兼容 Integer 和 Long 两种类型）
     * <p>
     * JJWT 默认将 JSON 数字解析为 Integer，当 userId 很大时需强制转 Long。
     * </p>
     *
     * @param claims JWT Claims
     * @return 用户 ID（Long 类型），解析失败则返回 null
     */
    private Long extractUserId(Claims claims) {
        try {
            Object userIdObj = claims.get(CLAIM_USER_ID);
            if (userIdObj == null) return null;
            if (userIdObj instanceof Long)    return (Long) userIdObj;
            if (userIdObj instanceof Integer) return ((Integer) userIdObj).longValue();
            if (userIdObj instanceof String)  return Long.parseLong((String) userIdObj);
            return Long.valueOf(userIdObj.toString());
        } catch (NumberFormatException e) {
            log.warn("[JWT] userId 解析失败：{}", e.getMessage());
            return null;
        }
    }

    /**
     * 向客户端写入 401 未认证响应（JSON 格式）
     *
     * @param response HTTP 响应
     * @param message  错误提示信息
     */
    private void writeUnauthorized(HttpServletResponse response, String message) throws Exception {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType("application/json;charset=UTF-8");
        // 与前端约定的统一响应格式 { code, message, data }
        String body = String.format(
                "{\"code\":401,\"message\":\"%s\",\"data\":null}",
                message
        );
        response.getWriter().write(body);
        response.getWriter().flush();
    }

    /**
     * 获取客户端真实 IP（兼容 Nginx 反代）
     *
     * @param request HTTP 请求
     * @return 客户端 IP 地址
     */
    private String getClientIp(HttpServletRequest request) {
        // 优先从 X-Forwarded-For 获取（Nginx 反代时设置）
        String ip = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(ip) && !"unknown".equalsIgnoreCase(ip)) {
            // X-Forwarded-For 可能包含多个 IP，取第一个（真实客户端 IP）
            return ip.split(",")[0].trim();
        }
        ip = request.getHeader("X-Real-IP");
        if (StringUtils.hasText(ip) && !"unknown".equalsIgnoreCase(ip)) {
            return ip;
        }
        return request.getRemoteAddr();
    }
}
