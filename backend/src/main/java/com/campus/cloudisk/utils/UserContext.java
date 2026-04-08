package com.campus.cloudisk.utils;

/**
 * 用户上下文工具类（基于 ThreadLocal）
 * <p>
 * 用途：在一次 HTTP 请求的处理链路中，安全地传递当前登录用户的信息。
 * <br/>
 * 工作流程：
 * <ol>
 *   <li>{@link com.campus.cloudisk.config.JwtInterceptor} 在请求进入时解析 JWT，
 *       调用 {@link #setCurrentUserId} 和 {@link #setCurrentUser} 将用户信息存入 ThreadLocal</li>
 *   <li>Controller / Service 层通过 {@link #getCurrentUserId()} 获取当前用户 ID，无需方法传参</li>
 *   <li>请求结束后，拦截器调用 {@link #clear()} 清理 ThreadLocal，防止内存泄漏</li>
 * </ol>
 * </p>
 *
 * 使用示例（Controller 层）：
 * <pre>
 *   Long userId = UserContext.getCurrentUserId();   // 直接获取，无需注入
 * </pre>
 *
 * 注意事项：
 * <ul>
 *   <li>ThreadLocal 绑定线程，在异步任务（@Async）或线程池中无法直接使用，需手动传递</li>
 *   <li>必须在 finally 中调用 clear()，否则线程池复用时会读到上一个请求的数据</li>
 * </ul>
 *
 * @author campus-cloud
 * @since 2024-01-01
 */
public class UserContext {

    // ===== ThreadLocal 容器 =====

    /**
     * 当前用户 ID（Long 类型，与数据库 user_info.id 一致）
     */
    private static final ThreadLocal<Long> USER_ID_HOLDER = new ThreadLocal<>();

    /**
     * 当前用户名（可选，用于日志打印，避免频繁查数据库）
     */
    private static final ThreadLocal<String> USERNAME_HOLDER = new ThreadLocal<>();

    /**
     * 当前用户角色（可选，用于简单权限判断，如 "ADMIN" / "USER"）
     */
    private static final ThreadLocal<String> USER_ROLE_HOLDER = new ThreadLocal<>();

    // ===== 构造方法私有化（工具类不允许实例化）=====
    private UserContext() {
        throw new UnsupportedOperationException("Utility class");
    }

    // ===================================================================
    //  写入方法（由 JwtInterceptor 调用）
    // ===================================================================

    /**
     * 设置当前请求的用户 ID
     *
     * @param userId 用户 ID（不允许为 null）
     */
    public static void setCurrentUserId(Long userId) {
        if (userId == null) {
            throw new IllegalArgumentException("userId 不能为 null");
        }
        USER_ID_HOLDER.set(userId);
    }

    /**
     * 设置当前请求的用户名（用于日志记录）
     *
     * @param username 用户名
     */
    public static void setCurrentUsername(String username) {
        USERNAME_HOLDER.set(username);
    }

    /**
     * 设置当前请求的用户角色
     *
     * @param role 角色字符串，如 "ADMIN" / "USER"
     */
    public static void setCurrentUserRole(String role) {
        USER_ROLE_HOLDER.set(role);
    }

    // ===================================================================
    //  读取方法（由 Controller / Service 层调用）
    // ===================================================================

    /**
     * 获取当前请求的用户 ID
     * <p>
     * 若在未经 JWT 认证的请求中调用（如匿名接口），将抛出异常。
     * 因此，请确保此方法只在 JwtInterceptor 拦截的路由下调用。
     * </p>
     *
     * @return 当前登录用户的 ID
     * @throws IllegalStateException 若 ThreadLocal 中无用户信息（未登录或拦截器未设置）
     */
    public static Long getCurrentUserId() {
        Long userId = USER_ID_HOLDER.get();
        if (userId == null) {
            throw new IllegalStateException("当前请求未设置用户上下文，请检查接口是否需要登录");
        }
        return userId;
    }

    /**
     * 获取当前请求的用户 ID（可能为 null，适用于可选认证接口）
     *
     * @return 当前用户 ID，未登录则返回 null
     */
    public static Long getCurrentUserIdOrNull() {
        return USER_ID_HOLDER.get();
    }

    /**
     * 获取当前请求的用户名
     *
     * @return 用户名，若未设置则返回 "unknown"
     */
    public static String getCurrentUsername() {
        String username = USERNAME_HOLDER.get();
        return username != null ? username : "unknown";
    }

    /**
     * 获取当前请求的用户角色
     *
     * @return 角色字符串，若未设置则返回 "USER"
     */
    public static String getCurrentUserRole() {
        String role = USER_ROLE_HOLDER.get();
        return role != null ? role : "USER";
    }

    /**
     * 判断当前用户是否为管理员
     *
     * @return true 表示管理员
     */
    public static boolean isAdmin() {
        return "ADMIN".equalsIgnoreCase(getCurrentUserRole());
    }

    // ===================================================================
    //  清理方法（必须在请求结束时调用，防止内存泄漏）
    // ===================================================================

    /**
     * 清除当前线程的所有用户上下文信息
     * <p>
     * 此方法由 {@link com.campus.cloudisk.config.JwtInterceptor#afterCompletion}
     * 在请求处理完成后自动调用，业务代码无需手动调用。
     * </p>
     */
    public static void clear() {
        USER_ID_HOLDER.remove();
        USERNAME_HOLDER.remove();
        USER_ROLE_HOLDER.remove();
    }
}
