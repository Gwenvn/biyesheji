package com.campus.cloudisk.config;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Spring MVC 全局配置
 * <p>
 * 负责：
 * <ol>
 *   <li>注册 {@link JwtInterceptor} 并配置拦截路径和白名单</li>
 *   <li>配置 CORS 跨域（允许前端 localhost:5173 访问后端接口）</li>
 * </ol>
 * </p>
 *
 * @author campus-cloud
 * @since 2024-01-01
 */
@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    /** 注入 JWT 拦截器 Bean */
    private final JwtInterceptor jwtInterceptor;

    /** 允许的前端跨域源（从 application.yml 读取） */
    @Value("${cors.allowed-origins:http://localhost:5173}")
    private String allowedOrigins;

    // ===================================================================
    //  JWT 拦截器注册
    // ===================================================================

    /**
     * 注册 JWT 认证拦截器
     * <p>
     * 拦截所有以 /api/** 开头的请求，同时配置以下白名单路径（无需 Token）：
     * <ul>
     *   <li>{@code /api/auth/**}      — 登录、注册、刷新 Token</li>
     *   <li>{@code /api/share/**}     — 公开分享链接匿名访问</li>
     *   <li>{@code /swagger-ui/**}    — Swagger UI（开发环境接口文档）</li>
     *   <li>{@code /v3/api-docs/**}   — OpenAPI 3.0 文档数据</li>
     *   <li>{@code /actuator/health}  — 健康检查（K8s 探针用）</li>
     *   <li>{@code /error}            — Spring 默认错误页</li>
     * </ul>
     * </p>
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(jwtInterceptor)
                // 拦截所有 API 路径
                .addPathPatterns("/api/**")
                // ===== 白名单：以下路径无需 JWT =====
                .excludePathPatterns(
                        // 认证接口（登录/注册/刷新 token）
                        "/api/auth/**",
                        // 公开分享文件访问（匿名可访问）
                        "/api/share/**",
                        // Swagger / OpenAPI 接口文档（开发环境）
                        "/swagger-ui/**",
                        "/swagger-ui.html",
                        "/v3/api-docs/**",
                        "/webjars/**",
                        // Actuator 健康检查
                        "/actuator/health",
                        // Spring 默认错误页
                        "/error"
                );
    }

    // ===================================================================
    //  CORS 跨域配置
    // ===================================================================

    /**
     * 配置全局跨域策略
     * <p>
     * 允许前端开发服务器（Vite localhost:5173）调用后端 API。
     * 生产环境需将 allowedOrigins 改为实际域名，禁止使用 *。
     * </p>
     */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                // 允许的前端源（多个用逗号分隔，从 yml 读取）
                .allowedOriginPatterns(allowedOrigins.split(","))
                // 允许的 HTTP 方法
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH")
                // 允许所有请求头（包括 Authorization）
                .allowedHeaders("*")
                // 允许携带认证信息（Cookie / Authorization）
                .allowCredentials(true)
                // OPTIONS 预检请求缓存时间（秒），减少预检请求次数
                .maxAge(3600);
    }
}
