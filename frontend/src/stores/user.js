/**
 * 用户状态管理（Pinia Store）
 *
 * 职责：
 *  1. 存储当前登录用户信息（userInfo：id / username / role / avatarUrl / storageUsed 等）
 *  2. 维护登录态（token 的读写由 utils/request.js 负责，store 只做内存状态管理）
 *  3. 提供 login / logout / fetchUserInfo / updateUserInfo 等操作
 *  4. 通过 localStorage 持久化用户信息，刷新页面后自动恢复登录状态
 *
 * 使用示例：
 *   import { useUserStore } from '@/stores/user'
 *   const userStore = useUserStore()
 *
 *   // 读取用户信息
 *   console.log(userStore.userInfo.username)
 *   console.log(userStore.isAdmin)
 *   console.log(userStore.storagePercent)
 *
 *   // 登录
 *   await userStore.login({ username, password })
 *
 *   // 退出
 *   userStore.logout()
 */

import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { ElMessage } from 'element-plus'
import request, { setToken, removeToken, getToken } from '@/utils/request'
import router from '@/router'

// localStorage 中用户信息的存储 Key
const USER_INFO_KEY = 'campus_cloud_user'

export const useUserStore = defineStore('user', () => {

  // ===================================================================
  //  State（响应式状态）
  // ===================================================================

  /**
   * 当前登录用户信息
   * 结构与后端 UserInfo 实体保持一致：
   * {
   *   id, username, email, avatarUrl,
   *   role,          // "USER" | "ADMIN"
   *   status,        // 1=正常 0=禁用
   *   storageUsed,   // 已用字节数
   *   storageQuota,  // 配额字节数（默认 5GB）
   *   fileCount,
   *   createTime,
   * }
   */
  const userInfo = ref(loadUserInfoFromStorage())

  /** 登录/获取用户信息时的加载状态 */
  const loading = ref(false)

  // ===================================================================
  //  Getters（计算属性）
  // ===================================================================

  /** 是否已登录（token 存在且 userInfo 不为 null） */
  const isLoggedIn = computed(() => !!getToken() && !!userInfo.value)

  /** 是否为管理员 */
  const isAdmin = computed(() => userInfo.value?.role === 'ADMIN')

  /** 用户名（未登录时返回空字符串） */
  const username = computed(() => userInfo.value?.username || '')

  /** 头像 URL */
  const avatarUrl = computed(() => userInfo.value?.avatarUrl || '')

  /**
   * 存储使用百分比（0~100）
   * 用于顶部存储进度条展示
   */
  const storagePercent = computed(() => {
    const used  = userInfo.value?.storageUsed  || 0
    const quota = userInfo.value?.storageQuota || 1
    return Math.min(Math.round((used / quota) * 100), 100)
  })

  /**
   * 剩余存储量（字节）
   */
  const storageRemaining = computed(() => {
    const used  = userInfo.value?.storageUsed  || 0
    const quota = userInfo.value?.storageQuota || 0
    return Math.max(0, quota - used)
  })

  // ===================================================================
  //  Actions（操作方法）
  // ===================================================================

  /**
   * 登录
   * <p>
   * 调用 /auth/login 接口，成功后：
   * 1. 将 Token 存入 localStorage（由 setToken 完成）
   * 2. 将用户信息存入 store 和 localStorage
   * 3. 根据角色跳转到对应页面（管理员 → /admin，普通用户 → /home）
   * </p>
   *
   * @param {object} credentials - { username, password }
   * @param {string} [redirectPath] - 登录成功后跳转的路径（来自 URL query 参数 redirect）
   */
  async function login(credentials, redirectPath) {
    loading.value = true
    try {
      const res = await request.post('/auth/login', credentials)
      const data = res.data?.data

      if (!data?.token) {
        ElMessage.error('登录失败：服务器未返回 Token')
        return false
      }

      // 1. 保存 Token
      setToken(data.token)

      // 2. 保存用户信息
      setUserInfo(data.userInfo || data.user || {})

      ElMessage.success(`欢迎回来，${userInfo.value?.username || ''}！`)

      // 3. 跳转目标页面
      const target = redirectPath || (isAdmin.value ? '/admin' : '/home')
      await router.push(target)

      return true
    } catch (e) {
      // 错误已在 request.js 拦截器中弹出，此处不重复提示
      return false
    } finally {
      loading.value = false
    }
  }

  /**
   * 退出登录
   * <p>
   * 清除本地 Token 和用户信息，跳转到登录页。
   * 同时调用后端 /auth/logout 接口（可选，用于服务端 Token 黑名单）。
   * </p>
   */
  function logout() {
    // 异步调用后端 logout（忽略失败，不阻塞本地清理）
    request.post('/auth/logout').catch(() => {})

    // 清除本地状态
    removeToken()
    userInfo.value = null
    localStorage.removeItem(USER_INFO_KEY)

    // 跳转登录页
    router.push('/login')
  }

  /**
   * 从服务端拉取最新用户信息（页面刷新后同步最新数据）
   * <p>
   * 调用时机：应用初始化时（App.vue onMounted 或 router.beforeEach）
   * </p>
   */
  async function fetchUserInfo() {
    if (!getToken()) return  // 未登录，跳过
    loading.value = true
    try {
      const res = await request.get('/user/info')
      if (res.data?.code === 200) {
        setUserInfo(res.data.data)
      }
    } catch (e) {
      // 401 已在 request.js 中处理（跳转登录）
      console.warn('[UserStore] 获取用户信息失败：', e.message)
    } finally {
      loading.value = false
    }
  }

  /**
   * 更新本地用户信息（上传头像、修改昵称等操作后调用）
   *
   * @param {object} partialInfo - 需要更新的字段（partial update）
   */
  function updateUserInfo(partialInfo) {
    if (!userInfo.value) return
    userInfo.value = { ...userInfo.value, ...partialInfo }
    // 同步到 localStorage
    persistUserInfo()
  }

  /**
   * 更新存储使用量（文件上传/删除后调用，避免频繁请求接口）
   *
   * @param {number} delta - 变化量（字节），正数为增加，负数为减少
   */
  function updateStorageUsed(delta) {
    if (!userInfo.value) return
    const newUsed = Math.max(0, (userInfo.value.storageUsed || 0) + delta)
    userInfo.value.storageUsed = newUsed
    persistUserInfo()
  }

  // ===================================================================
  //  私有辅助方法
  // ===================================================================

  /**
   * 将用户信息写入 store 和 localStorage
   */
  function setUserInfo(info) {
    userInfo.value = info
    persistUserInfo()
  }

  /**
   * 持久化用户信息到 localStorage
   */
  function persistUserInfo() {
    if (userInfo.value) {
      localStorage.setItem(USER_INFO_KEY, JSON.stringify(userInfo.value))
    }
  }

  return {
    // State
    userInfo,
    loading,
    // Getters
    isLoggedIn,
    isAdmin,
    username,
    avatarUrl,
    storagePercent,
    storageRemaining,
    // Actions
    login,
    logout,
    fetchUserInfo,
    updateUserInfo,
    updateStorageUsed,
  }
})

// ===================================================================
//  辅助函数（模块内部使用）
// ===================================================================

/**
 * 从 localStorage 读取用户信息（用于 store 初始化）
 *
 * @returns {object|null} 用户信息对象，不存在则返回 null
 */
function loadUserInfoFromStorage() {
  try {
    const raw = localStorage.getItem(USER_INFO_KEY)
    return raw ? JSON.parse(raw) : null
  } catch (e) {
    // JSON 解析失败（数据损坏），清除并返回 null
    localStorage.removeItem(USER_INFO_KEY)
    return null
  }
}
