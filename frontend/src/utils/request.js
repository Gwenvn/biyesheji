/**
 * Axios HTTP 请求封装
 *
 * 功能：
 *  1. 统一 baseURL（对应后端 application.yml server.port=8080）
 *  2. 请求拦截器：自动在 Header 中注入 JWT Token
 *  3. 响应拦截器：统一处理业务错误码、401 自动跳转登录
 *  4. 超时配置、JSON 内容类型默认设置
 *
 * 使用示例：
 *   import request from '@/utils/request'
 *
 *   // GET 请求
 *   const res = await request.get('/files', { params: { page: 1, size: 10 } })
 *
 *   // POST 请求
 *   const res = await request.post('/auth/login', { username, password })
 *
 *   // 文件上传（FormData）
 *   const res = await request.post('/files/upload', formData, {
 *     headers: { 'Content-Type': 'multipart/form-data' },
 *     onUploadProgress: (e) => { progress.value = Math.round(e.loaded / e.total * 100) }
 *   })
 */

import axios from 'axios'
import { ElMessage, ElMessageBox } from 'element-plus'
import router from '@/router'

// ===================================================================
//  创建 Axios 实例
// ===================================================================

const request = axios.create({
  // 后端接口基础地址（与 application.yml server.port 一致）
  // 开发环境通过 vite.config.js 代理转发，避免跨域问题
  // 生产环境直接写服务器地址，如 http://your-server-ip:8080
  baseURL: import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080/api',

  // 请求超时时间（ms）
  // 普通接口 15 秒，文件上传接口在调用时单独覆盖此值
  timeout: 15000,

  // 默认请求头
  headers: {
    'Content-Type': 'application/json;charset=UTF-8',
  },
})

// ===================================================================
//  Token 本地存储 Key（与 stores/user.js 保持一致）
// ===================================================================

const TOKEN_KEY = 'campus_cloud_token'

/**
 * 从 localStorage 读取 Token
 * @returns {string|null} JWT Token 字符串，未登录则为 null
 */
export const getToken = () => localStorage.getItem(TOKEN_KEY)

/**
 * 将 Token 写入 localStorage
 * @param {string} token JWT Token 字符串
 */
export const setToken = (token) => localStorage.setItem(TOKEN_KEY, token)

/**
 * 从 localStorage 删除 Token（退出登录时调用）
 */
export const removeToken = () => localStorage.removeItem(TOKEN_KEY)

// ===================================================================
//  请求拦截器：自动注入 JWT Token
// ===================================================================

request.interceptors.request.use(
  (config) => {
    const token = getToken()

    if (token) {
      // 在请求头中注入 JWT Token
      // 后端 JwtInterceptor 从 Authorization: Bearer {token} 中解析
      config.headers['Authorization'] = `Bearer ${token}`
    }

    // 文件上传时不覆盖 Content-Type，让浏览器自动设置 multipart/form-data 及 boundary
    if (config.data instanceof FormData) {
      delete config.headers['Content-Type']
    }

    return config
  },
  (error) => {
    // 请求配置错误（极少发生）
    console.error('[Request] 请求配置错误:', error)
    return Promise.reject(error)
  }
)

// ===================================================================
//  响应拦截器：统一处理业务错误和 HTTP 错误
// ===================================================================

/**
 * 防止 401 弹窗重复出现（多个并发请求同时 401 时只弹一次）
 */
let isShowingLoginExpiredDialog = false

request.interceptors.response.use(
  // --------- 请求成功（HTTP 2xx）---------
  (response) => {
    const res = response.data

    // 后端返回的业务状态码
    const code = res?.code

    // code === 200 表示业务成功，直接返回响应
    if (code === 200 || code === undefined) {
      return response
    }

    // ---- 业务错误码处理 ----

    // 401：Token 失效或未登录，跳转到登录页
    if (code === 401) {
      handleTokenExpired()
      return Promise.reject(new Error(res.message || '登录已过期'))
    }

    // 403：无权限，给出提示但不跳转
    if (code === 403) {
      ElMessage.error(res.message || '无权限执行此操作')
      return Promise.reject(new Error(res.message || '无权限'))
    }

    // 其他业务错误：弹出错误提示
    // 调用方可以通过 catch 进一步处理，也可以忽略（弹窗已提示用户）
    ElMessage.error(res.message || '操作失败，请重试')
    return Promise.reject(new Error(res.message || '业务错误'))
  },

  // --------- 请求失败（HTTP 非 2xx 或网络错误）---------
  (error) => {
    const status = error.response?.status
    const serverMessage = error.response?.data?.message

    if (status === 401) {
      // HTTP 401（如拦截器直接返回 401，而非业务层 code=401）
      handleTokenExpired()
      return Promise.reject(error)
    }

    if (status === 403) {
      ElMessage.error(serverMessage || '无权限执行此操作')
      return Promise.reject(error)
    }

    if (status === 404) {
      ElMessage.error(serverMessage || '请求的接口不存在')
      return Promise.reject(error)
    }

    if (status === 413) {
      // 413 Payload Too Large：文件超过 Nginx/Tomcat 限制
      ElMessage.error('文件大小超出服务器限制，请压缩后重试')
      return Promise.reject(error)
    }

    if (status >= 500) {
      ElMessage.error(serverMessage || '服务器开小差了，请稍后重试')
      return Promise.reject(error)
    }

    // 网络错误（无 status，如断网、CORS 错误、请求超时）
    if (error.code === 'ECONNABORTED' || error.message.includes('timeout')) {
      ElMessage.error('请求超时，请检查网络连接')
    } else if (!window.navigator.onLine) {
      ElMessage.error('网络已断开，请检查网络连接')
    } else {
      ElMessage.error('网络异常，请稍后重试')
    }

    console.error('[Response] 请求失败:', error.config?.url, error.message)
    return Promise.reject(error)
  }
)

// ===================================================================
//  辅助函数
// ===================================================================

/**
 * 处理 Token 失效（401）
 * <p>
 * 弹出提示框告知用户，确认后清除本地 Token 并跳转到登录页。
 * 使用标志位防止多个并发请求同时触发多个弹窗。
 * </p>
 */
function handleTokenExpired() {
  if (isShowingLoginExpiredDialog) return
  isShowingLoginExpiredDialog = true

  // 清除本地 Token（无论用户点确认还是关闭弹窗）
  removeToken()

  ElMessageBox.confirm(
    '登录已过期，请重新登录',
    '登录超时',
    {
      confirmButtonText: '重新登录',
      cancelButtonText: '取消',
      type: 'warning',
      // 禁止点击遮罩关闭（强制用户选择）
      closeOnClickModal: false,
    }
  )
    .then(() => {
      // 跳转登录页，并携带当前页面路径（登录成功后可返回原页面）
      const currentPath = router.currentRoute.value.fullPath
      router.push({
        path: '/login',
        query: currentPath !== '/login' ? { redirect: currentPath } : {},
      })
    })
    .catch(() => {
      // 用户点取消，也跳转登录页（Token 已失效，不允许继续操作）
      router.push('/login')
    })
    .finally(() => {
      isShowingLoginExpiredDialog = false
    })
}

export default request
