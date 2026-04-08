/**
 * Vue Router 路由配置
 *
 * 路由结构：
 *   /login              — 登录页（无需登录）
 *   /register           — 注册页（无需登录）
 *   /share/:code        — 公开分享文件访问（无需登录）
 *   /                   — 主布局（需要登录）
 *     /home             — 首页/文件列表（默认重定向）
 *     /files            — 文件管理（可带 folderId 参数）
 *     /recycle-bin      — 回收站
 *     /profile          — 个人设置
 *   /admin              — 管理后台（需要登录 + ADMIN 角色）
 *   /403                — 无权限提示页
 *   /:pathMatch(.*)*    — 404 页面
 *
 * 权限守卫逻辑：
 *   1. 访问白名单页面 → 直接放行
 *   2. 未登录访问受保护页面 → 跳转 /login（携带 redirect 参数）
 *   3. 普通用户访问 /admin → 跳转 /403
 *   4. 已登录访问 /login → 跳转 /home（防止重复登录）
 */

import { createRouter, createWebHistory } from 'vue-router'
import { getToken } from '@/utils/request'

// ===================================================================
//  路由懒加载（Vite 代码分割，减小首屏包体积）
// ===================================================================

const LoginView     = () => import('@/views/Login.vue')
const RegisterView  = () => import('@/views/Register.vue')
const ShareView     = () => import('@/views/Share.vue')
const HomeView      = () => import('@/views/Home.vue')
const FilesView     = () => import('@/views/Files.vue')
const RecycleBin    = () => import('@/views/RecycleBin.vue')
const ProfileView   = () => import('@/views/Profile.vue')
const AdminView     = () => import('@/views/Admin.vue')
const NotFound      = () => import('@/views/NotFound.vue')
const Forbidden     = () => import('@/views/Forbidden.vue')

// ===================================================================
//  路由定义
// ===================================================================

const routes = [

  // ===== 无需登录的公开路由 =====

  {
    path: '/login',
    name: 'Login',
    component: LoginView,
    meta: {
      title: '登录',
      requiresAuth: false,   // 不需要登录
      hideForAuth: true,     // 已登录时不可访问（会重定向到 /home）
    },
  },

  {
    path: '/register',
    name: 'Register',
    component: RegisterView,
    meta: {
      title: '注册',
      requiresAuth: false,
      hideForAuth: true,
    },
  },

  {
    path: '/share/:code',
    name: 'Share',
    component: ShareView,
    meta: {
      title: '文件分享',
      requiresAuth: false,   // 匿名访问公开分享链接
    },
  },

  // ===== 需要登录的主要路由（嵌套布局） =====

  {
    // 根路径重定向到 /home
    path: '/',
    redirect: '/home',
  },

  {
    path: '/home',
    name: 'Home',
    component: HomeView,
    meta: {
      title: '我的云盘',
      requiresAuth: true,
    },
  },

  {
    path: '/files',
    name: 'Files',
    component: FilesView,
    meta: {
      title: '文件管理',
      requiresAuth: true,
    },
    // 支持 /files?folderId=xxx 的目录跳转
    props: (route) => ({ folderId: route.query.folderId || null }),
  },

  {
    path: '/recycle-bin',
    name: 'RecycleBin',
    component: RecycleBin,
    meta: {
      title: '回收站',
      requiresAuth: true,
    },
  },

  {
    path: '/profile',
    name: 'Profile',
    component: ProfileView,
    meta: {
      title: '个人设置',
      requiresAuth: true,
    },
  },

  // ===== 管理后台（仅 ADMIN 角色可访问）=====

  {
    path: '/admin',
    name: 'Admin',
    component: AdminView,
    meta: {
      title: '管理后台',
      requiresAuth: true,
      requiresAdmin: true,   // 需要 ADMIN 角色
    },
  },

  // ===== 错误页面 =====

  {
    path: '/403',
    name: 'Forbidden',
    component: Forbidden,
    meta: { title: '无权限' },
  },

  {
    // 必须放在最后，匹配所有未定义的路径
    path: '/:pathMatch(.*)*',
    name: 'NotFound',
    component: NotFound,
    meta: { title: '页面不存在' },
  },
]

// ===================================================================
//  创建路由实例
// ===================================================================

const router = createRouter({
  // history 模式（使用 HTML5 History API，URL 不带 #）
  // 生产部署时 Nginx 需要配置 try_files 支持此模式
  history: createWebHistory(import.meta.env.BASE_URL),
  routes,
  // 路由切换时滚动到顶部
  scrollBehavior(to, from, savedPosition) {
    if (savedPosition) {
      // 浏览器后退/前进时恢复上次滚动位置
      return savedPosition
    }
    return { top: 0, behavior: 'smooth' }
  },
})

// ===================================================================
//  全局前置守卫（权限控制核心逻辑）
// ===================================================================

router.beforeEach((to, from, next) => {
  // 1. 设置页面标题
  const title = to.meta?.title
  document.title = title ? `${title} - 校园云盘` : '校园云盘'

  const token      = getToken()
  const isLoggedIn = !!token

  // 2. 已登录用户访问登录/注册页 → 跳转到首页（防止重复登录）
  if (isLoggedIn && to.meta?.hideForAuth) {
    return next('/home')
  }

  // 3. 不需要登录的页面 → 直接放行
  if (!to.meta?.requiresAuth) {
    return next()
  }

  // 4. 需要登录但未登录 → 跳转登录页，携带 redirect 参数
  if (!isLoggedIn) {
    return next({
      path: '/login',
      query: to.fullPath !== '/home' ? { redirect: to.fullPath } : {},
    })
  }

  // 5. 需要管理员权限 → 从 localStorage 读取角色判断
  if (to.meta?.requiresAdmin) {
    const userInfoRaw = localStorage.getItem('campus_cloud_user')
    let role = 'USER'
    try {
      role = userInfoRaw ? JSON.parse(userInfoRaw)?.role || 'USER' : 'USER'
    } catch (e) {
      role = 'USER'
    }

    if (role !== 'ADMIN') {
      return next('/403')
    }
  }

  // 6. 所有检查通过，放行
  next()
})

// ===================================================================
//  全局后置钩子（NProgress 进度条由 App.vue 中的 router 守卫控制）
// ===================================================================

router.afterEach((to, from, failure) => {
  if (failure) {
    console.warn('[Router] 导航失败：', failure)
  }
})

export default router
