<template>
  <div class="admin-container">

    <!-- ===== 顶部导航栏 ===== -->
    <div class="admin-header">
      <div class="header-left">
        <el-icon class="header-icon"><Setting /></el-icon>
        <span class="header-title">校园云盘 · 管理后台</span>
      </div>
      <div class="header-right">
        <el-avatar :size="32" :src="adminAvatar" style="margin-right:8px" />
        <span class="admin-name">{{ adminName }}</span>
        <el-divider direction="vertical" />
        <el-button link type="primary" @click="handleLogout">
          <el-icon><SwitchButton /></el-icon> 退出登录
        </el-button>
      </div>
    </div>

    <!-- ===== 主体内容 ===== -->
    <div class="admin-main">

      <!-- 统计卡片区域 -->
      <el-row :gutter="16" class="stat-cards">
        <el-col :span="6" v-for="card in statCards" :key="card.key">
          <el-card class="stat-card" shadow="hover">
            <div class="stat-content">
              <el-icon class="stat-icon" :style="{ color: card.color }">
                <component :is="card.icon" />
              </el-icon>
              <div class="stat-info">
                <div class="stat-value">{{ card.value }}</div>
                <div class="stat-label">{{ card.label }}</div>
              </div>
            </div>
          </el-card>
        </el-col>
      </el-row>

      <!-- Tab 切换区域 -->
      <el-card class="main-card">
        <el-tabs v-model="activeTab" @tab-change="handleTabChange">

          <!-- ===== Tab 1: 用户管理 ===== -->
          <el-tab-pane label="用户管理" name="users">
            <template #label>
              <span><el-icon><User /></el-icon> 用户管理</span>
            </template>

            <!-- 搜索栏 -->
            <div class="toolbar">
              <el-input
                v-model="userSearch.keyword"
                placeholder="搜索用户名/邮箱"
                style="width: 240px"
                clearable
                @keyup.enter="loadUsers"
                @clear="loadUsers"
              >
                <template #prefix><el-icon><Search /></el-icon></template>
              </el-input>
              <el-select
                v-model="userSearch.status"
                placeholder="账号状态"
                clearable
                style="width: 130px; margin-left: 8px"
                @change="loadUsers"
              >
                <el-option label="正常" :value="1" />
                <el-option label="已禁用" :value="0" />
              </el-select>
              <el-button type="primary" @click="loadUsers" style="margin-left: 8px">
                <el-icon><Search /></el-icon> 查询
              </el-button>
              <el-button @click="resetUserSearch">重置</el-button>
            </div>

            <!-- 用户表格 -->
            <el-table
              v-loading="userLoading"
              :data="userList"
              border
              stripe
              style="width: 100%"
              row-key="id"
            >
              <el-table-column type="index" label="#" width="55" align="center" />
              <el-table-column prop="username" label="用户名" min-width="120">
                <template #default="{ row }">
                  <div class="user-cell">
                    <el-avatar :size="28" :src="row.avatarUrl">
                      {{ row.username?.charAt(0)?.toUpperCase() }}
                    </el-avatar>
                    <span style="margin-left: 8px">{{ row.username }}</span>
                  </div>
                </template>
              </el-table-column>
              <el-table-column prop="email" label="邮箱" min-width="180" show-overflow-tooltip />
              <el-table-column prop="role" label="角色" width="90" align="center">
                <template #default="{ row }">
                  <el-tag :type="row.role === 'ADMIN' ? 'danger' : 'primary'" size="small">
                    {{ row.role === 'ADMIN' ? '管理员' : '普通用户' }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="storageUsed" label="已用空间" width="120" align="right">
                <template #default="{ row }">
                  {{ formatSize(row.storageUsed) }}
                </template>
              </el-table-column>
              <el-table-column prop="fileCount" label="文件数" width="90" align="center" />
              <el-table-column prop="status" label="状态" width="90" align="center">
                <template #default="{ row }">
                  <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">
                    {{ row.status === 1 ? '正常' : '已禁用' }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="createTime" label="注册时间" width="160" align="center">
                <template #default="{ row }">
                  {{ formatDate(row.createTime) }}
                </template>
              </el-table-column>
              <el-table-column label="操作" width="160" align="center" fixed="right">
                <template #default="{ row }">
                  <el-button
                    link
                    :type="row.status === 1 ? 'warning' : 'success'"
                    size="small"
                    @click="toggleUserStatus(row)"
                    :disabled="row.role === 'ADMIN'"
                  >
                    {{ row.status === 1 ? '禁用' : '启用' }}
                  </el-button>
                  <el-divider direction="vertical" />
                  <el-popconfirm
                    title="确定删除该用户？此操作不可撤销"
                    @confirm="deleteUser(row.id)"
                    confirm-button-type="danger"
                  >
                    <template #reference>
                      <el-button
                        link
                        type="danger"
                        size="small"
                        :disabled="row.role === 'ADMIN'"
                      >
                        删除
                      </el-button>
                    </template>
                  </el-popconfirm>
                </template>
              </el-table-column>
            </el-table>

            <!-- 分页 -->
            <el-pagination
              v-model:current-page="userPage.current"
              v-model:page-size="userPage.size"
              :total="userPage.total"
              :page-sizes="[10, 20, 50]"
              layout="total, sizes, prev, pager, next, jumper"
              @current-change="loadUsers"
              @size-change="loadUsers"
            />
          </el-tab-pane>

          <!-- ===== Tab 2: 文件管理 ===== -->
          <el-tab-pane label="文件管理" name="files">
            <template #label>
              <span><el-icon><Files /></el-icon> 文件管理</span>
            </template>

            <!-- 搜索栏 -->
            <div class="toolbar">
              <el-input
                v-model="fileSearch.keyword"
                placeholder="搜索文件名"
                style="width: 240px"
                clearable
                @keyup.enter="loadFiles"
                @clear="loadFiles"
              >
                <template #prefix><el-icon><Search /></el-icon></template>
              </el-input>
              <el-select
                v-model="fileSearch.fileType"
                placeholder="文件类型"
                clearable
                style="width: 130px; margin-left: 8px"
                @change="loadFiles"
              >
                <el-option label="图片" value="image" />
                <el-option label="文档" value="document" />
                <el-option label="视频" value="video" />
                <el-option label="音频" value="audio" />
                <el-option label="其他" value="other" />
              </el-select>
              <el-button type="primary" @click="loadFiles" style="margin-left: 8px">
                <el-icon><Search /></el-icon> 查询
              </el-button>
              <el-button @click="resetFileSearch">重置</el-button>

              <!-- 批量删除 -->
              <el-button
                type="danger"
                style="margin-left: auto"
                :disabled="selectedFiles.length === 0"
                @click="batchDeleteFiles"
              >
                <el-icon><Delete /></el-icon> 批量删除 ({{ selectedFiles.length }})
              </el-button>
            </div>

            <!-- 文件表格 -->
            <el-table
              v-loading="fileLoading"
              :data="fileList"
              border
              stripe
              style="width: 100%"
              row-key="id"
              @selection-change="handleFileSelectionChange"
            >
              <el-table-column type="selection" width="50" align="center" />
              <el-table-column prop="fileName" label="文件名" min-width="200" show-overflow-tooltip>
                <template #default="{ row }">
                  <div class="file-cell">
                    <el-icon class="file-type-icon" :style="{ color: getFileIconColor(row.fileType) }">
                      <component :is="getFileIcon(row.fileType)" />
                    </el-icon>
                    <span class="text-ellipsis" style="margin-left: 6px">{{ row.fileName }}</span>
                  </div>
                </template>
              </el-table-column>
              <el-table-column prop="ownerName" label="上传者" width="120" show-overflow-tooltip />
              <el-table-column prop="fileSize" label="大小" width="100" align="right">
                <template #default="{ row }">{{ formatSize(row.fileSize) }}</template>
              </el-table-column>
              <el-table-column prop="fileType" label="类型" width="90" align="center">
                <template #default="{ row }">
                  <el-tag size="small" :type="getFileTagType(row.fileType)">
                    {{ row.fileType || '未知' }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="downloadCount" label="下载次数" width="100" align="center" />
              <el-table-column prop="isPublic" label="共享" width="80" align="center">
                <template #default="{ row }">
                  <el-icon :color="row.isPublic ? '#67c23a' : '#c0c4cc'">
                    <component :is="row.isPublic ? 'Unlock' : 'Lock'" />
                  </el-icon>
                </template>
              </el-table-column>
              <el-table-column prop="createTime" label="上传时间" width="160" align="center">
                <template #default="{ row }">{{ formatDate(row.createTime) }}</template>
              </el-table-column>
              <el-table-column label="操作" width="120" align="center" fixed="right">
                <template #default="{ row }">
                  <el-button link type="primary" size="small" @click="previewFile(row)">
                    预览
                  </el-button>
                  <el-divider direction="vertical" />
                  <el-popconfirm
                    title="确定删除该文件？"
                    @confirm="deleteFile(row.id)"
                    confirm-button-type="danger"
                  >
                    <template #reference>
                      <el-button link type="danger" size="small">删除</el-button>
                    </template>
                  </el-popconfirm>
                </template>
              </el-table-column>
            </el-table>

            <!-- 分页 -->
            <el-pagination
              v-model:current-page="filePage.current"
              v-model:page-size="filePage.size"
              :total="filePage.total"
              :page-sizes="[10, 20, 50]"
              layout="total, sizes, prev, pager, next, jumper"
              @current-change="loadFiles"
              @size-change="loadFiles"
            />
          </el-tab-pane>

          <!-- ===== Tab 3: 存储统计 ===== -->
          <el-tab-pane label="存储统计" name="storage">
            <template #label>
              <span><el-icon><DataAnalysis /></el-icon> 存储统计</span>
            </template>

            <div v-loading="storageLoading" class="storage-panel">

              <!-- OSS 总览卡片 -->
              <el-row :gutter="16" style="margin-bottom: 20px">
                <el-col :span="8">
                  <el-card class="oss-card" shadow="hover">
                    <div class="oss-card-title">
                      <el-icon color="#409eff"><Coin /></el-icon> OSS 总存储量
                    </div>
                    <div class="oss-card-value">{{ formatSize(ossStats.totalCapacity) }}</div>
                    <el-progress
                      :percentage="ossUsagePercent"
                      :color="ossProgressColor"
                      :stroke-width="10"
                      style="margin-top: 12px"
                    />
                    <div class="oss-card-sub">
                      已用 {{ formatSize(ossStats.usedCapacity) }} /
                      剩余 {{ formatSize(ossStats.totalCapacity - ossStats.usedCapacity) }}
                    </div>
                  </el-card>
                </el-col>

                <el-col :span="8">
                  <el-card class="oss-card" shadow="hover">
                    <div class="oss-card-title">
                      <el-icon color="#67c23a"><Upload /></el-icon> 本月上传流量
                    </div>
                    <div class="oss-card-value">{{ formatSize(ossStats.monthlyUpload) }}</div>
                    <div class="oss-card-sub">
                      较上月
                      <span :class="ossStats.uploadTrend >= 0 ? 'trend-up' : 'trend-down'">
                        {{ ossStats.uploadTrend >= 0 ? '+' : '' }}{{ ossStats.uploadTrend }}%
                      </span>
                    </div>
                  </el-card>
                </el-col>

                <el-col :span="8">
                  <el-card class="oss-card" shadow="hover">
                    <div class="oss-card-title">
                      <el-icon color="#e6a23c"><Download /></el-icon> 本月下载流量
                    </div>
                    <div class="oss-card-value">{{ formatSize(ossStats.monthlyDownload) }}</div>
                    <div class="oss-card-sub">
                      较上月
                      <span :class="ossStats.downloadTrend >= 0 ? 'trend-up' : 'trend-down'">
                        {{ ossStats.downloadTrend >= 0 ? '+' : '' }}{{ ossStats.downloadTrend }}%
                      </span>
                    </div>
                  </el-card>
                </el-col>
              </el-row>

              <!-- 文件类型分布 -->
              <el-row :gutter="16">
                <el-col :span="12">
                  <el-card shadow="hover">
                    <template #header>
                      <span style="font-weight: 600">文件类型占比</span>
                    </template>
                    <div class="file-type-chart">
                      <div
                        v-for="item in ossStats.typeDistribution"
                        :key="item.type"
                        class="type-bar-row"
                      >
                        <span class="type-label">{{ item.label }}</span>
                        <el-progress
                          :percentage="item.percent"
                          :color="item.color"
                          :stroke-width="16"
                          style="flex: 1; margin: 0 12px"
                        />
                        <span class="type-value">{{ formatSize(item.size) }}</span>
                      </div>
                    </div>
                  </el-card>
                </el-col>

                <el-col :span="12">
                  <el-card shadow="hover">
                    <template #header>
                      <span style="font-weight: 600">存储用量 Top 5 用户</span>
                    </template>
                    <el-table :data="ossStats.topUsers" size="small" :show-header="true">
                      <el-table-column type="index" label="排名" width="55" align="center">
                        <template #default="{ $index }">
                          <el-tag
                            :type="['danger','warning','','',''][  $index]"
                            size="small"
                            style="font-weight:700"
                          >
                            {{ $index + 1 }}
                          </el-tag>
                        </template>
                      </el-table-column>
                      <el-table-column prop="username" label="用户名" min-width="100" />
                      <el-table-column prop="used" label="已用空间" width="120" align="right">
                        <template #default="{ row }">{{ formatSize(row.used) }}</template>
                      </el-table-column>
                      <el-table-column prop="fileCount" label="文件数" width="80" align="center" />
                    </el-table>
                  </el-card>
                </el-col>
              </el-row>

              <!-- 刷新按钮 -->
              <div style="margin-top: 16px; text-align: right">
                <el-button @click="loadStorageStats" :loading="storageLoading">
                  <el-icon><Refresh /></el-icon> 刷新数据
                </el-button>
              </div>
            </div>
          </el-tab-pane>

        </el-tabs>
      </el-card>
    </div>

    <!-- ===== 文件预览对话框 ===== -->
    <el-dialog
      v-model="previewVisible"
      :title="previewFile?.fileName || '文件预览'"
      width="700px"
      destroy-on-close
    >
      <div class="preview-content">
        <!-- 图片预览 -->
        <img
          v-if="isImageFile(previewFileData?.fileType)"
          :src="previewFileData?.downloadUrl"
          style="max-width: 100%; max-height: 60vh; object-fit: contain"
          alt="预览图"
        />
        <!-- 视频预览 -->
        <video
          v-else-if="isVideoFile(previewFileData?.fileType)"
          :src="previewFileData?.downloadUrl"
          controls
          style="width: 100%; max-height: 60vh"
        />
        <!-- 其他文件 -->
        <el-empty v-else description="该文件类型暂不支持在线预览">
          <el-button type="primary" @click="downloadPreviewFile">
            <el-icon><Download /></el-icon> 下载文件
          </el-button>
        </el-empty>
      </div>
      <template #footer>
        <div class="preview-footer">
          <span class="preview-meta">
            大小：{{ formatSize(previewFileData?.fileSize) }} ·
            上传者：{{ previewFileData?.ownerName }} ·
            上传时间：{{ formatDate(previewFileData?.createTime) }}
          </span>
          <el-button @click="previewVisible = false">关闭</el-button>
        </div>
      </template>
    </el-dialog>

  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  Setting, User, Files, DataAnalysis, Search, Delete,
  Refresh, SwitchButton, Coin, Upload, Download,
  Document, Picture, VideoPlay, Headset, Lock, Unlock
} from '@element-plus/icons-vue'
import request from '@/utils/request'
import { useUserStore } from '@/stores/user'
import { formatDate, formatSize } from '@/utils/format'

// ========== 路由和状态 ==========
const router = useRouter()
const userStore = useUserStore()

// ========== 管理员信息 ==========
const adminName = computed(() => userStore.userInfo?.username || '管理员')
const adminAvatar = computed(() => userStore.userInfo?.avatarUrl || '')

// ========== Tab 控制 ==========
/** 当前激活的 Tab */
const activeTab = ref('users')

/** Tab 切换时加载对应数据 */
const handleTabChange = (tabName) => {
  if (tabName === 'users') loadUsers()
  else if (tabName === 'files') loadFiles()
  else if (tabName === 'storage') loadStorageStats()
}

// ========== 顶部统计卡片 ==========
const statCards = reactive([
  { key: 'users',   label: '注册用户数', value: '0', icon: 'User',         color: '#409eff' },
  { key: 'files',   label: '总文件数',   value: '0', icon: 'Files',        color: '#67c23a' },
  { key: 'storage', label: '已用存储',   value: '0', icon: 'Coin',         color: '#e6a23c' },
  { key: 'today',   label: '今日上传',   value: '0', icon: 'Upload',       color: '#f56c6c' },
])

/** 加载顶部统计数据 */
const loadStatCards = async () => {
  try {
    const res = await request.get('/admin/stats/overview')
    if (res.data?.code === 200) {
      const d = res.data.data
      statCards[0].value = d.totalUsers || 0
      statCards[1].value = d.totalFiles || 0
      statCards[2].value = formatSize(d.totalStorage || 0)
      statCards[3].value = d.todayUpload || 0
    }
  } catch (e) {
    console.error('加载统计数据失败', e)
  }
}

// ========================================
// ===== 用户管理 =====
// ========================================

const userLoading = ref(false)
const userList = ref([])

/** 用户搜索条件 */
const userSearch = reactive({
  keyword: '',
  status: null,
})

/** 用户分页 */
const userPage = reactive({
  current: 1,
  size: 10,
  total: 0,
})

/** 加载用户列表 */
const loadUsers = async () => {
  userLoading.value = true
  try {
    const res = await request.get('/admin/users', {
      params: {
        page: userPage.current,
        size: userPage.size,
        keyword: userSearch.keyword || undefined,
        status: userSearch.status !== null ? userSearch.status : undefined,
      },
    })
    if (res.data?.code === 200) {
      const data = res.data.data
      userList.value = data.records || []
      userPage.total = data.total || 0
    }
  } catch (e) {
    ElMessage.error('加载用户列表失败')
    console.error(e)
  } finally {
    userLoading.value = false
  }
}

/** 重置用户搜索条件 */
const resetUserSearch = () => {
  userSearch.keyword = ''
  userSearch.status = null
  userPage.current = 1
  loadUsers()
}

/** 切换用户启用/禁用状态 */
const toggleUserStatus = async (row) => {
  const newStatus = row.status === 1 ? 0 : 1
  const action = newStatus === 0 ? '禁用' : '启用'
  try {
    await ElMessageBox.confirm(
      `确定要${action}用户「${row.username}」吗？`,
      '操作确认',
      { type: 'warning', confirmButtonText: action, cancelButtonText: '取消' }
    )
    const res = await request.put(`/admin/users/${row.id}/status`, { status: newStatus })
    if (res.data?.code === 200) {
      row.status = newStatus
      ElMessage.success(`已${action}用户 ${row.username}`)
    }
  } catch (e) {
    if (e !== 'cancel') ElMessage.error(`${action}失败`)
  }
}

/** 删除用户 */
const deleteUser = async (userId) => {
  try {
    const res = await request.delete(`/admin/users/${userId}`)
    if (res.data?.code === 200) {
      ElMessage.success('用户已删除')
      loadUsers()
    }
  } catch (e) {
    ElMessage.error('删除失败')
    console.error(e)
  }
}

// ========================================
// ===== 文件管理 =====
// ========================================

const fileLoading = ref(false)
const fileList = ref([])
const selectedFiles = ref([])

/** 文件搜索条件 */
const fileSearch = reactive({
  keyword: '',
  fileType: '',
})

/** 文件分页 */
const filePage = reactive({
  current: 1,
  size: 10,
  total: 0,
})

/** 加载文件列表 */
const loadFiles = async () => {
  fileLoading.value = true
  try {
    const res = await request.get('/admin/files', {
      params: {
        page: filePage.current,
        size: filePage.size,
        keyword: fileSearch.keyword || undefined,
        fileType: fileSearch.fileType || undefined,
      },
    })
    if (res.data?.code === 200) {
      const data = res.data.data
      fileList.value = data.records || []
      filePage.total = data.total || 0
    }
  } catch (e) {
    ElMessage.error('加载文件列表失败')
    console.error(e)
  } finally {
    fileLoading.value = false
  }
}

/** 重置文件搜索条件 */
const resetFileSearch = () => {
  fileSearch.keyword = ''
  fileSearch.fileType = ''
  filePage.current = 1
  loadFiles()
}

/** 表格多选变化 */
const handleFileSelectionChange = (rows) => {
  selectedFiles.value = rows
}

/** 批量删除文件 */
const batchDeleteFiles = async () => {
  if (selectedFiles.value.length === 0) return
  try {
    await ElMessageBox.confirm(
      `确定删除选中的 ${selectedFiles.value.length} 个文件吗？`,
      '批量删除',
      { type: 'warning', confirmButtonText: '确定删除', cancelButtonText: '取消' }
    )
    const ids = selectedFiles.value.map(f => f.id)
    const res = await request.delete('/admin/files/batch', { data: { ids } })
    if (res.data?.code === 200) {
      ElMessage.success(`已删除 ${ids.length} 个文件`)
      selectedFiles.value = []
      loadFiles()
    }
  } catch (e) {
    if (e !== 'cancel') ElMessage.error('批量删除失败')
  }
}

/** 删除单个文件 */
const deleteFile = async (fileId) => {
  try {
    const res = await request.delete(`/admin/files/${fileId}`)
    if (res.data?.code === 200) {
      ElMessage.success('文件已删除')
      loadFiles()
    }
  } catch (e) {
    ElMessage.error('删除失败')
    console.error(e)
  }
}

// ========================================
// ===== 存储统计 =====
// ========================================

const storageLoading = ref(false)

/** OSS 统计数据 */
const ossStats = reactive({
  totalCapacity:  107374182400,  // 100GB，后端返回后覆盖
  usedCapacity:   0,
  monthlyUpload:  0,
  monthlyDownload: 0,
  uploadTrend:    0,
  downloadTrend:  0,
  typeDistribution: [],
  topUsers: [],
})

/** OSS 使用率百分比 */
const ossUsagePercent = computed(() => {
  if (!ossStats.totalCapacity) return 0
  return Math.min(
    Math.round((ossStats.usedCapacity / ossStats.totalCapacity) * 100),
    100
  )
})

/** 根据使用率返回进度条颜色 */
const ossProgressColor = computed(() => {
  if (ossUsagePercent.value >= 90) return '#f56c6c'
  if (ossUsagePercent.value >= 70) return '#e6a23c'
  return '#67c23a'
})

/** 加载 OSS 存储统计 */
const loadStorageStats = async () => {
  storageLoading.value = true
  try {
    const res = await request.get('/admin/stats/storage')
    if (res.data?.code === 200) {
      const d = res.data.data
      Object.assign(ossStats, d)
    }
  } catch (e) {
    ElMessage.error('加载存储统计失败')
    console.error(e)
  } finally {
    storageLoading.value = false
  }
}

// ========================================
// ===== 文件预览 =====
// ========================================

const previewVisible = ref(false)
const previewFileData = ref(null)

/** 打开文件预览 */
const previewFile = async (row) => {
  previewFileData.value = row
  previewVisible.value = true
  // 若未获取到 downloadUrl，则请求预签名 URL
  if (!row.downloadUrl) {
    try {
      const res = await request.get(`/files/${row.id}/download-url`)
      if (res.data?.code === 200) {
        previewFileData.value = { ...row, downloadUrl: res.data.data }
      }
    } catch (e) {
      console.error('获取预览 URL 失败', e)
    }
  }
}

/** 下载预览文件 */
const downloadPreviewFile = () => {
  if (previewFileData.value?.downloadUrl) {
    window.open(previewFileData.value.downloadUrl, '_blank')
  }
}

// ========================================
// ===== 工具函数 =====
// ========================================

/** 根据文件类型返回图标组件名 */
const getFileIcon = (type) => {
  const map = {
    image: 'Picture',
    video: 'VideoPlay',
    audio: 'Headset',
    document: 'Document',
  }
  return map[type] || 'Document'
}

/** 根据文件类型返回图标颜色 */
const getFileIconColor = (type) => {
  const map = {
    image:    '#67c23a',
    video:    '#f56c6c',
    audio:    '#e6a23c',
    document: '#409eff',
  }
  return map[type] || '#909399'
}

/** 根据文件类型返回 Tag 类型 */
const getFileTagType = (type) => {
  const map = {
    image: 'success', video: 'danger', audio: 'warning', document: 'primary'
  }
  return map[type] || 'info'
}

/** 判断是否为图片 */
const isImageFile = (type) => type === 'image'

/** 判断是否为视频 */
const isVideoFile = (type) => type === 'video'

// ========================================
// ===== 退出登录 =====
// ========================================
const handleLogout = async () => {
  try {
    await ElMessageBox.confirm('确定退出管理后台吗？', '退出登录', {
      type: 'warning',
      confirmButtonText: '退出',
      cancelButtonText: '取消',
    })
    userStore.logout()
    router.push('/login')
  } catch (e) {
    // 取消退出，不处理
  }
}

// ========================================
// ===== 初始化 =====
// ========================================
onMounted(async () => {
  await Promise.all([
    loadStatCards(),
    loadUsers(),
  ])
})
</script>

<style scoped>
/* ===== 整体布局 ===== */
.admin-container {
  min-height: 100vh;
  background: #f0f2f5;
  display: flex;
  flex-direction: column;
}

/* ===== 顶部导航 ===== */
.admin-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 24px;
  height: 56px;
  background: #fff;
  box-shadow: 0 1px 4px rgba(0, 21, 41, 0.08);
  position: sticky;
  top: 0;
  z-index: 100;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 10px;
}

.header-icon {
  font-size: 22px;
  color: #409eff;
}

.header-title {
  font-size: 17px;
  font-weight: 600;
  color: #303133;
  letter-spacing: 0.5px;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 8px;
}

.admin-name {
  font-size: 14px;
  color: #606266;
}

/* ===== 主体内容 ===== */
.admin-main {
  padding: 20px 24px;
  flex: 1;
}

/* ===== 统计卡片 ===== */
.stat-cards {
  margin-bottom: 16px;
}

.stat-card {
  cursor: default;
  transition: transform 0.2s;
}

.stat-card:hover {
  transform: translateY(-2px);
}

.stat-content {
  display: flex;
  align-items: center;
  gap: 14px;
}

.stat-icon {
  font-size: 36px;
}

.stat-value {
  font-size: 22px;
  font-weight: 700;
  color: #303133;
  line-height: 1;
}

.stat-label {
  font-size: 12px;
  color: #909399;
  margin-top: 4px;
}

/* ===== 主卡片 ===== */
.main-card {
  border-radius: 8px;
}

/* ===== 工具栏 ===== */
.toolbar {
  display: flex;
  align-items: center;
  margin-bottom: 14px;
  flex-wrap: wrap;
  gap: 8px;
}

/* ===== 用户单元格 ===== */
.user-cell {
  display: flex;
  align-items: center;
}

/* ===== 文件单元格 ===== */
.file-cell {
  display: flex;
  align-items: center;
  max-width: 100%;
}

.file-type-icon {
  font-size: 18px;
  flex-shrink: 0;
}

/* ===== 存储统计 ===== */
.storage-panel {
  padding: 4px 0;
}

.oss-card {
  text-align: center;
  padding: 8px 0;
}

.oss-card-title {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  font-size: 14px;
  color: #606266;
  margin-bottom: 8px;
}

.oss-card-value {
  font-size: 26px;
  font-weight: 700;
  color: #303133;
  margin-bottom: 4px;
}

.oss-card-sub {
  font-size: 12px;
  color: #909399;
  margin-top: 8px;
}

.trend-up {
  color: #f56c6c;
  font-weight: 600;
}

.trend-down {
  color: #67c23a;
  font-weight: 600;
}

/* 文件类型分布条 */
.file-type-chart {
  padding: 8px 0;
}

.type-bar-row {
  display: flex;
  align-items: center;
  margin-bottom: 12px;
}

.type-label {
  width: 50px;
  font-size: 13px;
  color: #606266;
  flex-shrink: 0;
}

.type-value {
  width: 70px;
  text-align: right;
  font-size: 12px;
  color: #909399;
  flex-shrink: 0;
}

/* ===== 预览对话框 ===== */
.preview-content {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 200px;
  background: #f5f7fa;
  border-radius: 6px;
  padding: 16px;
}

.preview-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.preview-meta {
  font-size: 12px;
  color: #909399;
}
</style>
