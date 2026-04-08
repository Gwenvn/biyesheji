/**
 * 前端通用格式化工具函数
 *
 * 包含：
 *  - formatSize(bytes)      文件大小格式化（B / KB / MB / GB）
 *  - formatDate(time)       日期时间格式化（yyyy-MM-dd HH:mm:ss）
 *  - formatRelativeTime(time) 相对时间（3分钟前、昨天等）
 *  - formatFileType(ext)    文件类型中文名
 *  - formatDuration(secs)   时长格式化（mm:ss）
 *  - getFileIconName(type)  根据文件类型返回 Element Plus 图标名
 */

// ===================================================================
//  文件大小格式化
// ===================================================================

/**
 * 将字节数转换为可读的文件大小字符串
 *
 * @param {number|null|undefined} bytes 字节数
 * @param {number} [decimals=2] 保留小数位数
 * @returns {string} 格式化后的文件大小，如 "1.23 MB"
 *
 * @example
 *   formatSize(0)           // "0 B"
 *   formatSize(1024)        // "1.00 KB"
 *   formatSize(1048576)     // "1.00 MB"
 *   formatSize(1073741824)  // "1.00 GB"
 *   formatSize(null)        // "-"
 */
export function formatSize(bytes, decimals = 2) {
  // 处理无效值
  if (bytes === null || bytes === undefined || bytes === '') return '-'
  if (bytes === 0) return '0 B'

  const num = Number(bytes)
  if (isNaN(num) || num < 0) return '-'

  const units = ['B', 'KB', 'MB', 'GB', 'TB']
  // 计算单位级别：每 1024 进一级
  const level = Math.floor(Math.log(num) / Math.log(1024))
  // 防止超出单位数组范围（超大文件）
  const unitIndex = Math.min(level, units.length - 1)

  const value = num / Math.pow(1024, unitIndex)

  // B 不显示小数
  if (unitIndex === 0) return `${num} B`

  return `${value.toFixed(decimals)} ${units[unitIndex]}`
}

// ===================================================================
//  日期时间格式化
// ===================================================================

/**
 * 将日期/时间戳格式化为标准字符串
 *
 * @param {string|number|Date|null|undefined} time 时间值（ISO字符串 / 时间戳ms / Date对象）
 * @param {string} [fmt='yyyy-MM-dd HH:mm:ss'] 输出格式
 * @returns {string} 格式化后的日期字符串
 *
 * @example
 *   formatDate('2024-03-15T08:30:00')         // "2024-03-15 08:30:00"
 *   formatDate(1710490200000)                  // "2024-03-15 16:30:00"
 *   formatDate(new Date(), 'yyyy-MM-dd')       // "2024-03-15"
 *   formatDate(null)                           // "-"
 */
export function formatDate(time, fmt = 'yyyy-MM-dd HH:mm:ss') {
  if (!time) return '-'

  let date
  if (time instanceof Date) {
    date = time
  } else if (typeof time === 'number') {
    date = new Date(time)
  } else {
    // ISO 字符串：处理 "2024-03-15T08:30:00" 或 "2024-03-15 08:30:00" 两种格式
    date = new Date(String(time).replace(/-/g, '/').replace('T', ' '))
  }

  if (isNaN(date.getTime())) return '-'

  const padZero = (n) => String(n).padStart(2, '0')

  const replacements = {
    'yyyy': date.getFullYear(),
    'MM':   padZero(date.getMonth() + 1),
    'dd':   padZero(date.getDate()),
    'HH':   padZero(date.getHours()),
    'mm':   padZero(date.getMinutes()),
    'ss':   padZero(date.getSeconds()),
  }

  return fmt.replace(/yyyy|MM|dd|HH|mm|ss/g, (key) => replacements[key])
}

// ===================================================================
//  相对时间（友好时间显示）
// ===================================================================

/**
 * 将时间转换为相对时间描述（适用于文件列表、动态时间线等）
 *
 * @param {string|number|Date|null|undefined} time 时间值
 * @returns {string} 相对时间描述
 *
 * @example
 *   formatRelativeTime(Date.now() - 30000)    // "刚刚"
 *   formatRelativeTime(Date.now() - 180000)   // "3分钟前"
 *   formatRelativeTime(Date.now() - 3600000)  // "1小时前"
 *   formatRelativeTime(yesterday)             // "昨天 14:30"
 *   formatRelativeTime(lastWeek)              // "3天前"
 *   formatRelativeTime(lastYear)              // "2023-06-15"（超过30天直接显示日期）
 */
export function formatRelativeTime(time) {
  if (!time) return '-'

  const date = time instanceof Date ? time : new Date(String(time).replace(/-/g, '/'))
  if (isNaN(date.getTime())) return '-'

  const now   = Date.now()
  const diff  = now - date.getTime()  // 毫秒差
  const secs  = Math.floor(diff / 1000)
  const mins  = Math.floor(secs / 60)
  const hours = Math.floor(mins / 60)
  const days  = Math.floor(hours / 24)

  if (secs < 60)           return '刚刚'
  if (mins < 60)           return `${mins}分钟前`
  if (hours < 24)          return `${hours}小时前`
  if (days === 1)          return `昨天 ${formatDate(date, 'HH:mm')}`
  if (days < 30)           return `${days}天前`
  if (days < 365)          return formatDate(date, 'MM-dd HH:mm')
  return formatDate(date, 'yyyy-MM-dd')
}

// ===================================================================
//  文件类型
// ===================================================================

/**
 * 根据文件类型分类返回中文名称
 *
 * @param {string|null|undefined} type 文件类型（image / document / video / audio / other）
 * @returns {string} 中文类型名
 *
 * @example
 *   formatFileType('image')    // "图片"
 *   formatFileType('document') // "文档"
 *   formatFileType('video')    // "视频"
 *   formatFileType(null)       // "未知"
 */
export function formatFileType(type) {
  const map = {
    image:    '图片',
    document: '文档',
    video:    '视频',
    audio:    '音频',
    other:    '其他',
  }
  return map[type] || '未知'
}

/**
 * 根据文件扩展名判断文件类型分类
 *
 * @param {string|null|undefined} ext 文件扩展名（不含点，如 pdf / jpg / mp4）
 * @returns {'image'|'document'|'video'|'audio'|'other'} 文件类型分类
 *
 * @example
 *   getFileTypeByExt('pdf')  // "document"
 *   getFileTypeByExt('jpg')  // "image"
 *   getFileTypeByExt('mp4')  // "video"
 *   getFileTypeByExt('zip')  // "other"
 */
export function getFileTypeByExt(ext) {
  if (!ext) return 'other'
  const lower = ext.toLowerCase()

  const typeMap = {
    image:    ['jpg', 'jpeg', 'png', 'gif', 'bmp', 'webp', 'svg', 'ico', 'tiff', 'heic'],
    document: ['pdf', 'doc', 'docx', 'xls', 'xlsx', 'ppt', 'pptx', 'txt', 'md', 'csv', 'rtf'],
    video:    ['mp4', 'avi', 'mov', 'wmv', 'flv', 'mkv', 'webm', 'rmvb', 'm4v'],
    audio:    ['mp3', 'wav', 'flac', 'aac', 'ogg', 'm4a', 'wma'],
  }

  for (const [type, exts] of Object.entries(typeMap)) {
    if (exts.includes(lower)) return type
  }
  return 'other'
}

/**
 * 根据文件类型返回对应的 Element Plus 图标组件名
 *
 * @param {string|null|undefined} type 文件类型（image / document / video / audio / other）
 * @returns {string} Element Plus 图标名（可用 <component :is="iconName" /> 渲染）
 *
 * @example
 *   getFileIconName('image')    // "Picture"
 *   getFileIconName('video')    // "VideoPlay"
 *   getFileIconName('document') // "Document"
 */
export function getFileIconName(type) {
  const iconMap = {
    image:    'Picture',
    document: 'Document',
    video:    'VideoPlay',
    audio:    'Headset',
    folder:   'Folder',
    other:    'Files',
  }
  return iconMap[type] || 'Files'
}

/**
 * 根据文件类型返回对应的主题色（用于图标着色）
 *
 * @param {string|null|undefined} type 文件类型
 * @returns {string} CSS 颜色值
 */
export function getFileIconColor(type) {
  const colorMap = {
    image:    '#67c23a',   // 绿色
    document: '#409eff',   // 蓝色
    video:    '#f56c6c',   // 红色
    audio:    '#e6a23c',   // 橙色
    folder:   '#fac858',   // 黄色
    other:    '#909399',   // 灰色
  }
  return colorMap[type] || '#909399'
}

// ===================================================================
//  时长格式化
// ===================================================================

/**
 * 将秒数格式化为 mm:ss 或 HH:mm:ss 格式（用于视频/音频时长展示）
 *
 * @param {number|null|undefined} seconds 总秒数
 * @returns {string} 格式化后的时长字符串
 *
 * @example
 *   formatDuration(65)    // "01:05"
 *   formatDuration(3665)  // "01:01:05"
 *   formatDuration(0)     // "00:00"
 *   formatDuration(null)  // "--:--"
 */
export function formatDuration(seconds) {
  if (seconds === null || seconds === undefined || isNaN(seconds)) return '--:--'

  const total = Math.floor(Number(seconds))
  const h = Math.floor(total / 3600)
  const m = Math.floor((total % 3600) / 60)
  const s = total % 60

  const pad = (n) => String(n).padStart(2, '0')

  if (h > 0) return `${pad(h)}:${pad(m)}:${pad(s)}`
  return `${pad(m)}:${pad(s)}`
}

// ===================================================================
//  数字格式化
// ===================================================================

/**
 * 将大数字格式化为带单位的字符串（用于下载次数、访问量等展示）
 *
 * @param {number|null|undefined} num 数值
 * @returns {string} 格式化后的字符串
 *
 * @example
 *   formatCount(999)    // "999"
 *   formatCount(1000)   // "1.0k"
 *   formatCount(12345)  // "12.3k"
 *   formatCount(1500000) // "1.5M"
 */
export function formatCount(num) {
  if (num === null || num === undefined) return '0'
  const n = Number(num)
  if (isNaN(n)) return '0'
  if (n >= 1_000_000) return `${(n / 1_000_000).toFixed(1)}M`
  if (n >= 1_000)     return `${(n / 1_000).toFixed(1)}k`
  return String(n)
}
