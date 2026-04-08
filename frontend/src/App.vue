<template>
  <!-- 根应用组件：路由视图容器 -->
  <router-view />
</template>

<script setup>
import { watch } from 'vue'
import { useRouter } from 'vue-router'
import NProgress from 'nprogress'
import 'nprogress/nprogress.css'

// ========== NProgress 全局配置 ==========
NProgress.configure({
  easing: 'ease',       // 动画效果
  speed: 400,           // 进度条速度(ms)
  showSpinner: false,   // 不显示右上角圆形加载圈
  trickleSpeed: 200,    // 自动递增间隔(ms)
  minimum: 0.08,        // 最小起始值
})

// ========== 路由守卫触发进度条 ==========
const router = useRouter()

// 路由切换开始 → 进度条启动
router.beforeEach((to, from, next) => {
  NProgress.start()
  next()
})

// 路由切换完成 → 进度条结束
router.afterEach(() => {
  NProgress.done()
})

// 路由发生错误 → 也要关闭进度条，避免卡死
router.onError(() => {
  NProgress.done()
})
</script>

<style>
/* ========== 全局基础样式重置 ========== */
*, *::before, *::after {
  box-sizing: border-box;
  margin: 0;
  padding: 0;
}

html, body {
  height: 100%;
  font-family: 'PingFang SC', 'Microsoft YaHei', -apple-system, BlinkMacSystemFont,
               'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif;
  font-size: 14px;
  color: #303133;
  background-color: #f5f7fa;
  -webkit-font-smoothing: antialiased;
  -moz-osx-font-smoothing: grayscale;
}

#app {
  height: 100%;
}

/* ========== NProgress 进度条样式覆盖 ========== */
/* 进度条主体颜色 —— 使用品牌主色 */
#nprogress .bar {
  background: #409eff !important;
  height: 3px !important;
}

/* 进度条末端光晕效果 */
#nprogress .peg {
  box-shadow: 0 0 10px #409eff, 0 0 5px #409eff !important;
}

/* ========== Element Plus 全局样式覆盖 ========== */
/* 表格行鼠标悬停背景色 */
.el-table__row:hover td {
  background-color: #ecf5ff !important;
}

/* 分页组件统一样式 */
.el-pagination {
  justify-content: flex-end;
  margin-top: 16px;
}

/* 卡片统一圆角和阴影 */
.el-card {
  border-radius: 8px !important;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.06) !important;
}

/* 按钮圆角统一 */
.el-button {
  border-radius: 6px !important;
}

/* 输入框圆角 */
.el-input__wrapper {
  border-radius: 6px !important;
}

/* 对话框圆角 */
.el-dialog {
  border-radius: 10px !important;
}

/* ========== 公共工具类 ========== */
/* 文本省略（单行） */
.text-ellipsis {
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}

/* flex 居中 */
.flex-center {
  display: flex;
  align-items: center;
  justify-content: center;
}

/* 全高页面 */
.full-height {
  height: 100vh;
}

/* 页面容器内边距 */
.page-container {
  padding: 20px;
}

/* 间距工具类 */
.mt-8 { margin-top: 8px; }
.mt-16 { margin-top: 16px; }
.mt-24 { margin-top: 24px; }
.mb-8 { margin-bottom: 8px; }
.mb-16 { margin-bottom: 16px; }
.mb-24 { margin-bottom: 24px; }
.ml-8 { margin-left: 8px; }
.mr-8 { margin-right: 8px; }

/* ========== 滚动条美化（Webkit） ========== */
::-webkit-scrollbar {
  width: 6px;
  height: 6px;
}

::-webkit-scrollbar-track {
  background: #f1f1f1;
  border-radius: 3px;
}

::-webkit-scrollbar-thumb {
  background: #c1c1c1;
  border-radius: 3px;
  transition: background 0.2s;
}

::-webkit-scrollbar-thumb:hover {
  background: #a8a8a8;
}

/* ========== 过渡动画 ========== */
/* 路由切换淡入淡出 */
.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.25s ease;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}

/* 列表项过渡 */
.list-enter-active,
.list-leave-active {
  transition: all 0.3s ease;
}

.list-enter-from,
.list-leave-to {
  opacity: 0;
  transform: translateX(-10px);
}
</style>
