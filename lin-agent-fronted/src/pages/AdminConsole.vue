<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import ConfirmDialog from '@/components/ConfirmDialog.vue'
import ProjectBrand from '@/components/ProjectBrand.vue'
import { showGlobalToast } from '@/services/toast'
import {
  clearAdminLogs,
  deleteAdminConversation,
  deleteKnowledgeDocument,
  getAdminDashboard,
  getAdminLogs,
  getKnowledgeDocument,
  getKnowledgeUploadTask,
  listAdminConversations,
  listAdminUsers,
  listKnowledgeDocuments,
  queryAdminLogs,
  saveKnowledgeDocument,
  updateAdminUserDeleteState,
  updateAdminUserRole,
  uploadKnowledgeDocument,
} from '@/services/admin'

const router = useRouter()
const activeSection = ref('overview')

const dashboard = ref({
  userCount: 0,
  conversationCount: 0,
  messageCount: 0,
  knowledgeFileCount: 0,
  knowledgeSectionCount: 0,
  vectorRowCount: 0,
  elasticDocCount: 0,
  activeStreamCount: 0,
  logCount: 0,
  errorLogCount: 0,
  lastRebuildTime: '',
})

const loadingDashboard = ref(false)
const loadingLogs = ref(false)
const loadingDocuments = ref(false)
const savingDocument = ref(false)
const uploadingDocument = ref(false)
const loadingUsers = ref(false)
const loadingConversations = ref(false)
const operatingUserId = ref('')
const removingConversationId = ref('')

const documents = ref([])
const logs = ref([])
const users = ref([])
const conversations = ref([])

const currentFile = ref('')
const editor = ref(createEmptyDocument())
const uploadInputRef = ref(null)
const uploadFeedback = ref(createEmptyUploadFeedback())

const documentKeyword = ref('')
const userKeyword = ref('')
const userRoleFilter = ref('')
const userStatusFilter = ref('active')
const conversationKeyword = ref('')
const conversationModeFilter = ref('')
const conversationPinnedFilter = ref('')
const logKeyword = ref('')
const logLevel = ref('')
const logCategory = ref('')
const ADMIN_LOG_LIMIT = 300
const DOCUMENT_PAGE_SIZE = 10
const USER_SEARCH_DEBOUNCE_MS = 260
const CONVERSATION_SEARCH_DEBOUNCE_MS = 260
const activeAuditDropdown = ref('')
const currentDocumentPage = ref(1)

const confirmDialog = ref({
  visible: false,
  title: '',
  message: '',
  confirmText: '确认',
  cancelText: '取消',
  danger: false,
})

let confirmResolver = null
let uploadTaskTimer = 0
let userSearchTimer = 0
let conversationSearchTimer = 0
let conversationFetchSequence = 0

const conversationModeOptions = [
  { value: '', label: '全部模式', meta: '不过滤任何模式' },
  { value: 'emotion', label: '情感陪伴', meta: '偏陪伴与情绪回应' },
  { value: 'agent', label: '任务执行', meta: '偏工具协同执行' },
  { value: 'manus', label: '超级智能体', meta: '偏复杂任务处理' },
  { value: 'auto', label: '自动路由', meta: '按输入自动分流' },
]

const conversationPinnedOptions = [
  { value: '', label: '全部置顶状态', meta: '同时展示普通与置顶会话' },
  { value: 'pinned', label: '仅已置顶', meta: '优先排查重点会话' },
  { value: 'normal', label: '仅未置顶', meta: '查看普通沉淀会话' },
]

const userRoleOptions = [
  { value: '', label: '全部角色', meta: '同时查看管理员与普通用户' },
  { value: '0', label: '普通用户', meta: '聚焦常规账号的使用与治理情况' },
  { value: '1', label: '管理员', meta: '只查看具备后台权限的管理账号' },
]

const userStatusOptions = [
  { value: 'active', label: '仅正常用户', meta: '默认查看当前可以正常登录的账号' },
  { value: 'disabled', label: '仅已禁用用户', meta: '集中查看已被禁用的账号记录' },
  { value: 'all', label: '全部状态', meta: '同时展示正常与禁用状态的全部账号' },
]

function createEmptyDocument() {
  return {
    fileName: '',
    title: '',
    content: '# 新知识库文档\n\n#### 新问题\n请在这里填写内容。',
  }
}

function createEmptyUploadFeedback() {
  return {
    visible: false,
    fileName: '',
    progress: 0,
    phase: 'idle',
    status: 'info',
    message: '',
    taskId: '',
  }
}

const sectionTitle = computed(() => ({
  overview: '工作台',
  knowledge: '文档管理',
  users: '用户管理',
  conversations: '会话管理',
  logs: '系统日志',
}[activeSection.value] || '工作台'))

const navItems = computed(() => [
  { key: 'overview', label: '工作台' },
  { key: 'knowledge', label: '文档管理' },
  { key: 'users', label: '用户管理' },
  { key: 'conversations', label: '会话管理' },
  { key: 'logs', label: '系统日志' },
])

const currentDocumentSummary = computed(() => documents.value.find(item => String(item?.fileName || '') === String(currentFile.value || '')) || null)
const selectedUserRoleOption = computed(() => userRoleOptions.find(option => option.value === userRoleFilter.value) || userRoleOptions[0])
const selectedUserStatusOption = computed(() => userStatusOptions.find(option => option.value === userStatusFilter.value) || userStatusOptions[0])
const selectedConversationModeOption = computed(() => conversationModeOptions.find(option => option.value === conversationModeFilter.value) || conversationModeOptions[0])
const selectedConversationPinnedOption = computed(() => conversationPinnedOptions.find(option => option.value === conversationPinnedFilter.value) || conversationPinnedOptions[0])
const filteredDocuments = computed(() => {
  const keyword = String(documentKeyword.value || '').trim().toLowerCase()
  if (!keyword) return documents.value
  return documents.value.filter(item => `${item?.fileName || ''} ${item?.title || ''} ${item?.updateTime || ''}`.toLowerCase().includes(keyword))
})
const documentPageCount = computed(() => {
  const total = filteredDocuments.value.length
  return total > 0 ? Math.ceil(total / DOCUMENT_PAGE_SIZE) : 1
})
const pagedDocuments = computed(() => {
  const start = (currentDocumentPage.value - 1) * DOCUMENT_PAGE_SIZE
  return filteredDocuments.value.slice(start, start + DOCUMENT_PAGE_SIZE)
})
const documentRangeStart = computed(() => (
  filteredDocuments.value.length ? (currentDocumentPage.value - 1) * DOCUMENT_PAGE_SIZE + 1 : 0
))
const documentRangeEnd = computed(() => (
  filteredDocuments.value.length ? Math.min(currentDocumentPage.value * DOCUMENT_PAGE_SIZE, filteredDocuments.value.length) : 0
))
const overviewSnapshot = computed(() => {
  const knowledgeFileCount = Number(dashboard.value.knowledgeFileCount || 0)
  const knowledgeSectionCount = Number(dashboard.value.knowledgeSectionCount || 0)
  const vectorRowCount = Number(dashboard.value.vectorRowCount || 0)
  const elasticDocCount = Number(dashboard.value.elasticDocCount || 0)
  const userCount = Number(dashboard.value.userCount || 0)
  const conversationCount = Number(dashboard.value.conversationCount || 0)
  const messageCount = Number(dashboard.value.messageCount || 0)
  const activeStreamCount = Number(dashboard.value.activeStreamCount || 0)
  const logCount = Number(dashboard.value.logCount || 0)
  const errorLogCount = Number(dashboard.value.errorLogCount || 0)
  const indexCoverage = knowledgeSectionCount > 0 ? Math.min(100, Math.round(vectorRowCount / knowledgeSectionCount * 100)) : 0
  const searchCoverage = knowledgeSectionCount > 0 ? Math.min(100, Math.round(elasticDocCount / knowledgeSectionCount * 100)) : 0
  return {
    knowledgeFileCount,
    knowledgeSectionCount,
    vectorRowCount,
    elasticDocCount,
    userCount,
    conversationCount,
    messageCount,
    activeStreamCount,
    logCount,
    errorLogCount,
    lastRebuildTime: String(dashboard.value.lastRebuildTime || ''),
    indexCoverage,
    searchCoverage,
  }
})
const overviewStatus = computed(() => {
  if (overviewSnapshot.value.errorLogCount > 0) {
    return {
      title: '需要留意',
      tone: 'danger',
      badgeTone: 'danger',
      description: `当前有 ${overviewSnapshot.value.errorLogCount} 条异常日志，建议优先查看系统日志。`,
    }
  }
  if (pageBusy.value || overviewSnapshot.value.activeStreamCount > 0) {
    return {
      title: '处理中',
      tone: 'busy',
      badgeTone: 'warn',
      description: pageBusy.value
        ? '页面数据正在刷新。'
        : `当前有 ${overviewSnapshot.value.activeStreamCount} 个活跃流正在运行。`,
    }
  }
  return {
    title: '工作台已就绪',
    tone: 'success',
    badgeTone: 'success',
    description: '当前系统运行平稳，可以继续处理具体工作。',
  }
})
const overviewGreeting = computed(() => {
  const hour = new Date().getHours()
  const greeting = hour < 11 ? '早上好' : hour < 14 ? '中午好' : hour < 19 ? '下午好' : '晚上好'
  return {
    title: `${greeting}，这里是当前系统的工作台`,
    description: `当前已接入 ${overviewSnapshot.value.knowledgeFileCount} 份知识文档，累计沉淀 ${overviewSnapshot.value.conversationCount} 个会话与 ${overviewSnapshot.value.messageCount} 条消息，可直接从左侧继续处理具体模块。`,
  }
})
const overviewPulseCards = computed(() => ([
  {
    label: '知识资产',
    value: `${overviewSnapshot.value.knowledgeFileCount} 份文档`,
    meta: overviewSnapshot.value.knowledgeSectionCount > 0
      ? `${overviewSnapshot.value.knowledgeSectionCount} 个片段，向量覆盖 ${overviewSnapshot.value.indexCoverage}% ，检索覆盖 ${overviewSnapshot.value.searchCoverage}%`
      : '当前还没有知识片段，可先补充业务资料。',
  },
  {
    label: '账号规模',
    value: `${overviewSnapshot.value.userCount} 个账号`,
    meta: `当前系统共接入 ${overviewSnapshot.value.userCount} 个账号。`,
  },
  {
    label: '会话沉淀',
    value: `${overviewSnapshot.value.conversationCount} 个会话`,
    meta: `${overviewSnapshot.value.messageCount} 条消息，活跃流 ${overviewSnapshot.value.activeStreamCount} 个。`,
  },
  {
    label: '运行记录',
    value: `${overviewSnapshot.value.logCount} 条日志`,
    meta: overviewSnapshot.value.errorLogCount > 0
      ? `其中 ${overviewSnapshot.value.errorLogCount} 条为异常记录，建议结合系统日志继续排查。`
      : '当前没有新的异常日志，整体运行平稳。',
  },
]))
const overviewFocusList = computed(() => ([
  overviewSnapshot.value.knowledgeSectionCount === 0
    ? {
        title: '先补充知识内容',
        tag: '待开始',
        tone: 'warn',
        desc: '当前还没有知识片段，后续回答会缺少业务上下文。',
      }
    : {
        title: '知识库内容可持续维护',
        tag: '继续更新',
        tone: 'accent',
        desc: `已接入 ${overviewSnapshot.value.knowledgeFileCount} 份文档，可根据最新资料继续补充或修订。`,
      },
  overviewSnapshot.value.conversationCount === 0
    ? {
        title: '对话数据还在积累中',
        tag: '刚起步',
        tone: 'warn',
        desc: '目前还没有历史会话沉淀，后续可关注真实使用情况。',
      }
    : {
        title: '已有可回看的会话沉淀',
        tag: '持续积累',
        tone: 'success',
        desc: `当前已累计 ${overviewSnapshot.value.conversationCount} 个会话、${overviewSnapshot.value.messageCount} 条消息。`,
      },
  overviewSnapshot.value.errorLogCount > 0
    ? {
        title: '日志里有待处理问题',
        tag: '建议查看',
        tone: 'danger',
        desc: `系统日志中有 ${overviewSnapshot.value.errorLogCount} 条异常记录，必要时再进入日志模块处理。`,
      }
    : {
        title: '日志面保持平稳',
        tag: '运行正常',
        tone: 'success',
        desc: `当前共有 ${overviewSnapshot.value.logCount} 条系统日志，暂未发现新的异常记录。`,
      },
  overviewSnapshot.value.activeStreamCount > 0
    ? {
        title: '存在进行中的处理任务',
        tag: '运行中',
        tone: 'accent',
        desc: `当前有 ${overviewSnapshot.value.activeStreamCount} 个活跃流正在运行，可稍后查看结果。`,
      }
    : {
        title: '当前没有进行中的流式任务',
        tag: '空闲',
        tone: 'success',
        desc: '系统当前没有持续运行中的流式处理任务。',
      },
]))
const pageBusy = computed(() => (
  loadingDashboard.value
  || loadingLogs.value
  || loadingDocuments.value
  || savingDocument.value
  || uploadingDocument.value
  || loadingUsers.value
  || loadingConversations.value
))
const uploadButtonText = computed(() => {
  if (!uploadingDocument.value) return '上传文档'
  if (uploadFeedback.value.phase === 'uploading') return '上传中...'
  if (uploadFeedback.value.phase === 'queued') return '排队中...'
  if (uploadFeedback.value.phase === 'indexing') return '索引中...'
  return '处理中...'
})
const uploadPhaseLabel = computed(() => {
  if (uploadFeedback.value.status === 'success') return '同步完成'
  if (uploadFeedback.value.status === 'error') return '同步失败'
  if (uploadFeedback.value.phase === 'uploading') return '文件传输'
  if (uploadFeedback.value.phase === 'queued') return '索引排队'
  if (uploadFeedback.value.phase === 'indexing') return '索引同步'
  return '等待处理'
})

function setStatus(text, type = 'info') {
  const message = String(text || '')
  if (!message) {
    return
  }
  if (type === 'error') {
    showGlobalToast(message, { type: 'error', duration: 3200 })
  } else if (type === 'success') {
    showGlobalToast(message, { type: 'success', duration: 2400 })
  }
}

function formatDateTime(value) {
  if (!value) return '—'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return String(value)
  return date.toLocaleString('zh-CN', { hour12: false }).replace(/\//g, '-')
}

function userRoleLabel(role) {
  return Number(role) === 1 ? '管理员' : '普通用户'
}

function clearUploadTaskTimer() {
  if (uploadTaskTimer && typeof window !== 'undefined') {
    window.clearTimeout(uploadTaskTimer)
  }
  uploadTaskTimer = 0
}

function clearUserSearchTimer() {
  if (userSearchTimer && typeof window !== 'undefined') {
    window.clearTimeout(userSearchTimer)
  }
  userSearchTimer = 0
}

function clearConversationSearchTimer() {
  if (conversationSearchTimer && typeof window !== 'undefined') {
    window.clearTimeout(conversationSearchTimer)
  }
  conversationSearchTimer = 0
}

function updateUploadFeedback(patch = {}) {
  uploadFeedback.value = {
    ...uploadFeedback.value,
    ...patch,
  }
}

function resetUploadFeedback(delay = 0) {
  clearUploadTaskTimer()
  if (!delay || typeof window === 'undefined') {
    uploadFeedback.value = createEmptyUploadFeedback()
    return
  }
  uploadTaskTimer = window.setTimeout(() => {
    uploadFeedback.value = createEmptyUploadFeedback()
    uploadTaskTimer = 0
  }, delay)
}

function resolveTransferProgress(progressEvent) {
  const total = Number(progressEvent?.total || 0)
  const loaded = Number(progressEvent?.loaded || 0)
  if (!total || loaded <= 0) {
    return 12
  }
  const percent = Math.max(1, Math.min(100, Math.round(loaded / total * 100)))
  return Math.max(6, Math.min(35, Math.round(percent * 0.35)))
}

function resolveTaskDisplayProgress(progress) {
  const normalized = Math.max(0, Math.min(100, Number(progress || 0)))
  return Math.max(38, Math.min(100, Math.round(35 + normalized * 0.65)))
}

function normalizeDocumentPage(page) {
  const numericPage = Number(page)
  const normalized = Number.isFinite(numericPage) ? Math.max(1, Math.floor(numericPage)) : 1
  return Math.min(normalized, documentPageCount.value)
}

function setDocumentPage(page) {
  currentDocumentPage.value = normalizeDocumentPage(page)
}

function syncDocumentPageWithFile(fileName) {
  if (!fileName) return
  const index = filteredDocuments.value.findIndex(item => String(item?.fileName || '') === String(fileName || ''))
  if (index >= 0) {
    currentDocumentPage.value = Math.floor(index / DOCUMENT_PAGE_SIZE) + 1
  }
}

function upsertDocumentSummary(document) {
  if (!document || !document.fileName) return
  const next = Array.isArray(documents.value) ? [...documents.value] : []
  const summary = {
    ...document,
    content: undefined,
  }
  const index = next.findIndex(item => String(item?.fileName || '') === String(document.fileName || ''))
  if (index >= 0) {
    next[index] = {
      ...next[index],
      ...summary,
    }
  } else {
    next.unshift(summary)
  }
  next.sort((a, b) => String(a?.fileName || '').localeCompare(String(b?.fileName || ''), 'zh-CN'))
  documents.value = next
  syncDocumentPageWithFile(document.fileName)
}

function userStatusLabel(user) {
  return user?.deleted ? '已禁用' : '正常'
}

function conversationModeLabel(mode) {
  const normalized = String(mode || '').toLowerCase()
  if (normalized === 'emotion') return '情感陪伴'
  if (normalized === 'agent') return '任务执行'
  if (normalized === 'manus') return '超级智能体'
  if (normalized === 'auto') return '自动路由'
  if (normalized === 'general_assistance') return '通用对话'
  if (normalized === 'emotion_support') return '情绪支持'
  return mode || '未分类'
}

function conversationModeMeta(mode) {
  const normalized = String(mode || '').toLowerCase()
  if (normalized === 'emotion' || normalized === 'emotion_support') return '偏陪伴与情绪回应'
  if (normalized === 'agent') return '偏工具协同执行'
  if (normalized === 'manus') return '偏复杂任务处理'
  if (normalized === 'auto') return '按输入自动分流'
  if (normalized === 'general_assistance') return '通用问答场景'
  return mode ? `原始标识：${mode}` : '尚未标注模式'
}

function conversationModeTone(mode) {
  const normalized = String(mode || '').toLowerCase()
  if (normalized === 'emotion' || normalized === 'emotion_support') return 'emotion'
  if (normalized === 'agent') return 'agent'
  if (normalized === 'manus') return 'manus'
  if (normalized === 'auto') return 'auto'
  return 'neutral'
}

function conversationPinnedLabel(pinned) {
  return pinned ? '已置顶' : '普通'
}

function conversationPinnedMeta(pinned) {
  return pinned ? '列表优先展示' : '按最近时间排序'
}

function toggleAuditDropdown(name) {
  activeAuditDropdown.value = activeAuditDropdown.value === name ? '' : name
}

function closeAuditDropdown() {
  activeAuditDropdown.value = ''
}

function selectConversationMode(value) {
  conversationModeFilter.value = value
  closeAuditDropdown()
}

function selectConversationPinned(value) {
  conversationPinnedFilter.value = value
  closeAuditDropdown()
}

function selectUserRole(value) {
  userRoleFilter.value = value
  closeAuditDropdown()
}

function selectUserStatus(value) {
  userStatusFilter.value = value
  closeAuditDropdown()
}

function handleAuditGlobalPointerDown(event) {
  const target = event?.target
  if (typeof Element !== 'undefined' && target instanceof Element && target.closest('.custom-select')) {
    return
  }
  closeAuditDropdown()
}

function logTone(level) {
  const normalized = String(level || '').toUpperCase()
  if (normalized === 'ERROR') return 'danger'
  if (normalized === 'WARN') return 'warn'
  return 'info'
}

function goBack() {
  router.push('/assistant')
}

function ensureAdmin() {
  try {
    return !!(localStorage.getItem('user_id') || '') && String(localStorage.getItem('user_role') || '') === '1'
  } catch (e) {
    return false
  }
}

function applyDocument(doc) {
  const source = doc && typeof doc === 'object' ? doc : {}
  currentFile.value = String(source.fileName || '')
  editor.value = {
    fileName: String(source.fileName || ''),
    title: String(source.title || ''),
    content: String(source.content || ''),
  }
}

function openConfirmDialog(options = {}) {
  if (confirmResolver) {
    confirmResolver(false)
    confirmResolver = null
  }
  confirmDialog.value = {
    visible: true,
    title: String(options.title || '请确认操作'),
    message: String(options.message || ''),
    confirmText: String(options.confirmText || '确认'),
    cancelText: String(options.cancelText || '取消'),
    danger: !!options.danger,
  }
  return new Promise(resolve => {
    confirmResolver = resolve
  })
}

function resolveConfirmDialog(result) {
  const resolve = confirmResolver
  confirmResolver = null
  confirmDialog.value = { ...confirmDialog.value, visible: false }
  if (resolve) {
    resolve(result)
  }
}

async function fetchDashboard() {
  loadingDashboard.value = true
  try {
    dashboard.value = { ...dashboard.value, ...(await getAdminDashboard()) }
  } catch (e) {
    setStatus(e?.response?.data?.message || '读取后台统计失败', 'error')
  } finally {
    loadingDashboard.value = false
  }
}

async function fetchLogs() {
  loadingLogs.value = true
  try {
    logs.value = await queryAdminLogs({
      limit: ADMIN_LOG_LIMIT,
      level: logLevel.value || undefined,
      category: logCategory.value || undefined,
      keyword: logKeyword.value || undefined,
    })
  } catch (e) {
    setStatus(e?.response?.data?.message || '读取系统日志失败', 'error')
  } finally {
    loadingLogs.value = false
  }
}

function resetLogFilters() {
  logLevel.value = ''
  logCategory.value = ''
  logKeyword.value = ''
}

async function fetchAllLogs() {
  loadingLogs.value = true
  try {
    logs.value = await getAdminLogs(ADMIN_LOG_LIMIT)
  } catch (e) {
    setStatus(e?.response?.data?.message || '读取系统日志失败', 'error')
  } finally {
    loadingLogs.value = false
  }
}

async function openLogsSection() {
  resetLogFilters()
  await fetchAllLogs()
}

async function fetchDocuments(preferredFile = '') {
  loadingDocuments.value = true
  try {
    documents.value = await listKnowledgeDocuments()
    const preferredExists = preferredFile && documents.value.some(item => String(item?.fileName || '') === String(preferredFile))
    const currentExists = currentFile.value && documents.value.some(item => String(item?.fileName || '') === String(currentFile.value))
    const nextFile = (preferredExists ? preferredFile : '') || (currentExists ? currentFile.value : '') || documents.value[0]?.fileName || ''
    if (nextFile) {
      syncDocumentPageWithFile(nextFile)
      await selectDocument(nextFile)
    } else {
      setDocumentPage(1)
      applyDocument(createEmptyDocument())
    }
  } catch (e) {
    setStatus(e?.response?.data?.message || '读取知识库列表失败', 'error')
  } finally {
    loadingDocuments.value = false
  }
}

function refreshKnowledgeSection(preferredFile = '', includeLogs = false) {
  const tasks = [fetchDashboard(), fetchDocuments(preferredFile)]
  if (includeLogs) {
    tasks.push(fetchLogs())
  }
  void Promise.allSettled(tasks)
}

async function selectDocument(fileName) {
  if (!fileName) return
  try {
    applyDocument(await getKnowledgeDocument(fileName))
  } catch (e) {
    setStatus(e?.response?.data?.message || '读取知识库文档失败', 'error')
  }
}

function createNewDocument() {
  activeSection.value = 'knowledge'
  applyDocument(createEmptyDocument())
  setStatus('已创建新的知识库草稿。', 'info')
}

async function saveCurrentDocument() {
  if (!editor.value.content.trim()) {
    setStatus('知识库文档内容不能为空', 'error')
    return
  }
  savingDocument.value = true
  try {
    const saved = await saveKnowledgeDocument({
      fileName: editor.value.fileName,
      title: editor.value.title,
      content: editor.value.content,
      rebuildIndex: true,
    })
    applyDocument(saved)
    setStatus('知识库文档已保存，并已同步重建索引。', 'success')
    refreshKnowledgeSection(saved.fileName)
  } catch (e) {
    setStatus(e?.response?.data?.message || '保存知识库文档失败', 'error')
  } finally {
    savingDocument.value = false
  }
}

async function deleteCurrentDocument() {
  if (!currentFile.value) {
    setStatus('当前没有可删除的文档', 'error')
    return
  }
  const confirmed = await openConfirmDialog({
    title: '删除知识库文档',
    message: `确认删除知识库文档「${currentFile.value}」吗？删除后会同步重建索引。`,
    confirmText: '删除并重建',
    danger: true,
  })
  if (!confirmed) return
  try {
    await deleteKnowledgeDocument(currentFile.value, true)
    applyDocument(createEmptyDocument())
    setStatus('知识库文档已删除，并已完成索引重建。', 'success')
    refreshKnowledgeSection('')
  } catch (e) {
    setStatus(e?.response?.data?.message || '删除知识库文档失败', 'error')
  }
}

function triggerUpload() {
  if (!uploadInputRef.value) return
  uploadInputRef.value.value = ''
  uploadInputRef.value.click && uploadInputRef.value.click()
}

async function pollUploadTask(taskId, preferredFile = '') {
  try {
    const task = await getKnowledgeUploadTask(taskId)
    const status = String(task?.status || '').toUpperCase()
    updateUploadFeedback({
      visible: true,
      fileName: String(task?.fileName || preferredFile || uploadFeedback.value.fileName || ''),
      progress: resolveTaskDisplayProgress(task?.progress),
      phase: status === 'PENDING' ? 'queued' : (status === 'SUCCEEDED' || status === 'FAILED' ? 'done' : 'indexing'),
      status: status === 'FAILED' ? 'error' : status === 'SUCCEEDED' ? 'success' : 'active',
      message: String(task?.message || '正在同步知识库索引'),
      taskId,
    })
    if (status === 'SUCCEEDED') {
      uploadingDocument.value = false
      setStatus(String(task?.message || '知识库文档上传完成，索引已同步。'), 'success')
      refreshKnowledgeSection(preferredFile || task?.fileName || '', true)
      resetUploadFeedback(2600)
      return
    }
    if (status === 'FAILED') {
      uploadingDocument.value = false
      setStatus(String(task?.message || '文档已保存，但索引同步失败，请稍后重试。'), 'error')
      refreshKnowledgeSection(preferredFile || task?.fileName || '', true)
      return
    }
    clearUploadTaskTimer()
    if (typeof window !== 'undefined') {
      uploadTaskTimer = window.setTimeout(() => {
        void pollUploadTask(taskId, preferredFile || task?.fileName || '')
      }, 1200)
    }
  } catch (e) {
    uploadingDocument.value = false
    const message = e?.response?.data?.message || '获取上传进度失败，请稍后刷新页面查看结果'
    updateUploadFeedback({
      visible: true,
      status: 'error',
      phase: 'done',
      message,
    })
    setStatus(message, 'error')
  }
}

async function handleUpload(event) {
  const file = event?.target?.files?.[0]
  if (!file || uploadingDocument.value) return
  clearUploadTaskTimer()
  uploadingDocument.value = true
  activeSection.value = 'knowledge'
  updateUploadFeedback({
    visible: true,
    fileName: String(file.name || ''),
    progress: 6,
    phase: 'uploading',
    status: 'active',
    message: '正在上传文档内容...',
    taskId: '',
  })
  setStatus(`正在上传文档「${String(file.name || '未命名文档')}」...`, 'info')
  try {
    const result = await uploadKnowledgeDocument(file, true, progressEvent => {
      updateUploadFeedback({
        visible: true,
        fileName: String(file.name || ''),
        progress: resolveTransferProgress(progressEvent),
        phase: 'uploading',
        status: 'active',
        message: '正在上传文档内容...',
      })
    })
    const saved = result?.document || null
    const task = result?.task || null
    if (saved) {
      applyDocument(saved)
      upsertDocumentSummary(saved)
    }
    if (task?.taskId) {
      updateUploadFeedback({
        visible: true,
        fileName: String(saved?.fileName || file.name || ''),
        progress: Math.max(uploadFeedback.value.progress || 0, 38),
        phase: 'queued',
        status: 'active',
        message: String(task?.message || '文档已上传，正在排队同步索引'),
        taskId: String(task.taskId),
      })
      setStatus(`文档「${String(saved?.fileName || file.name || '未命名文档')}」已上传，正在后台同步索引。`, 'info')
      void pollUploadTask(String(task.taskId), String(saved?.fileName || file.name || ''))
    } else {
      uploadingDocument.value = false
      updateUploadFeedback({
        visible: true,
        fileName: String(saved?.fileName || file.name || ''),
        progress: 100,
        phase: 'done',
        status: 'success',
        message: '知识库文档上传成功',
      })
      setStatus('知识库文档上传成功。', 'success')
      refreshKnowledgeSection(saved?.fileName || '')
      resetUploadFeedback(2200)
    }
  } catch (e) {
    uploadingDocument.value = false
    const message = e?.response?.data?.message || '上传知识库文档失败'
    updateUploadFeedback({
      visible: true,
      fileName: String(file.name || ''),
      progress: Math.max(8, Number(uploadFeedback.value.progress || 0)),
      phase: 'done',
      status: 'error',
      message,
    })
    setStatus(message, 'error')
  } finally {
    if (event?.target) event.target.value = ''
  }
}

async function fetchUsers() {
  loadingUsers.value = true
  clearUserSearchTimer()
  closeAuditDropdown()
  try {
    const params = {
      keyword: userKeyword.value || undefined,
      userRole: userRoleFilter.value === '' ? undefined : Number(userRoleFilter.value),
      limit: 180,
    }
    if (userStatusFilter.value === 'all') {
      params.includeDeleted = true
    } else if (userStatusFilter.value === 'disabled') {
      params.includeDeleted = true
      params.deleted = true
    } else {
      params.deleted = false
    }
    users.value = await listAdminUsers(params)
  } catch (e) {
    setStatus(e?.response?.data?.message || '读取用户列表失败', 'error')
  } finally {
    loadingUsers.value = false
  }
}

async function switchUserRole(user) {
  if (!user || operatingUserId.value) return
  const nextRole = Number(user.userRole) === 1 ? 0 : 1
  operatingUserId.value = String(user.id || '')
  try {
    await updateAdminUserRole(user.id, nextRole)
    setStatus(`已将用户 ${user.userName || user.id} 调整为${userRoleLabel(nextRole)}。`, 'info')
    await Promise.all([fetchUsers(), fetchDashboard(), fetchLogs()])
  } catch (e) {
    setStatus(e?.response?.data?.message || '更新用户角色失败', 'error')
  } finally {
    operatingUserId.value = ''
  }
}

async function toggleUserDelete(user) {
  if (!user || operatingUserId.value) return
  const nextDeleted = !user.deleted
  const actionText = nextDeleted ? '禁用' : '恢复'
  const confirmed = await openConfirmDialog({
    title: `${actionText}用户`,
    message: `确认${actionText}用户「${user.userName || user.id}」吗？${nextDeleted ? '禁用后该账号将无法登录系统。' : '恢复后该账号可以重新登录系统。'}`,
    confirmText: `确认${actionText}`,
    danger: nextDeleted,
  })
  if (!confirmed) return
  operatingUserId.value = String(user.id || '')
  try {
    await updateAdminUserDeleteState(user.id, nextDeleted)
    setStatus(`用户${actionText}成功。`, 'info')
    await Promise.all([fetchUsers(), fetchDashboard(), fetchLogs()])
  } catch (e) {
    setStatus(e?.response?.data?.message || `${actionText}用户失败`, 'error')
  } finally {
    operatingUserId.value = ''
  }
}

async function fetchConversations() {
  const requestId = ++conversationFetchSequence
  loadingConversations.value = true
  clearConversationSearchTimer()
  closeAuditDropdown()
  try {
    const params = {
      keyword: conversationKeyword.value || undefined,
      mode: conversationModeFilter.value || undefined,
      limit: 240,
    }
    if (conversationPinnedFilter.value === 'pinned') params.pinned = true
    if (conversationPinnedFilter.value === 'normal') params.pinned = false
    const result = await listAdminConversations(params)
    if (requestId !== conversationFetchSequence) {
      return
    }
    conversations.value = result
  } catch (e) {
    if (requestId !== conversationFetchSequence) {
      return
    }
    setStatus(e?.response?.data?.message || '读取会话列表失败', 'error')
  } finally {
    if (requestId === conversationFetchSequence) {
      loadingConversations.value = false
    }
  }
}

async function removeConversation(item) {
  if (!item || removingConversationId.value) return
  const confirmed = await openConfirmDialog({
    title: '删除会话',
    message: `确认删除会话「${item.title || item.conversationId}」吗？删除后无法恢复。`,
    confirmText: '确认删除',
    danger: true,
  })
  if (!confirmed) return
  removingConversationId.value = String(item.conversationId || '')
  try {
    await deleteAdminConversation(item.conversationId, item.userId)
    setStatus('会话删除成功。', 'info')
    await Promise.all([fetchConversations(), fetchDashboard(), fetchLogs()])
  } catch (e) {
    setStatus(e?.response?.data?.message || '删除会话失败', 'error')
  } finally {
    removingConversationId.value = ''
  }
}

async function clearLogs() {
  const confirmed = await openConfirmDialog({
    title: '清空系统日志',
    message: '确认清空全部系统日志吗？清空后不可恢复。',
    confirmText: '清空日志',
    danger: true,
  })
  if (!confirmed) return
  try {
    await clearAdminLogs()
    setStatus('系统日志已清空。', 'info')
    await Promise.all([fetchAllLogs(), fetchDashboard()])
  } catch (e) {
    setStatus(e?.response?.data?.message || '清空系统日志失败', 'error')
  }
}

async function refreshAll() {
  const logTask = activeSection.value === 'logs' ? fetchAllLogs() : fetchLogs()
  await Promise.all([fetchDashboard(), logTask, fetchDocuments(currentFile.value), fetchUsers(), fetchConversations()])
}

function formatLogOperator(item) {
  const operatorName = String(item?.operatorName || '').trim()
  const operatorId = String(item?.operatorId || '').trim()
  if (operatorName && operatorId && operatorName !== operatorId) {
    return `${operatorName}（${operatorId}）`
  }
  if (operatorName) {
    return operatorName
  }
  if (operatorId) {
    return operatorId
  }
  return '未知'
}

watch(activeSection, async (nextSection, previousSection) => {
  if (nextSection === 'logs' && nextSection !== previousSection) {
    await openLogsSection()
  }
})

watch(documentKeyword, () => {
  setDocumentPage(1)
})

watch(filteredDocuments, () => {
  setDocumentPage(currentDocumentPage.value)
})

watch([userKeyword, userRoleFilter, userStatusFilter], () => {
  if (activeSection.value !== 'users') {
    return
  }
  clearUserSearchTimer()
  if (typeof window === 'undefined') {
    void fetchUsers()
    return
  }
  userSearchTimer = window.setTimeout(() => {
    void fetchUsers()
  }, USER_SEARCH_DEBOUNCE_MS)
})

watch([conversationKeyword, conversationModeFilter, conversationPinnedFilter], () => {
  if (activeSection.value !== 'conversations') {
    return
  }
  clearConversationSearchTimer()
  if (typeof window === 'undefined') {
    void fetchConversations()
    return
  }
  conversationSearchTimer = window.setTimeout(() => {
    void fetchConversations()
  }, CONVERSATION_SEARCH_DEBOUNCE_MS)
})

onMounted(async () => {
  if (typeof document !== 'undefined') {
    document.addEventListener('pointerdown', handleAuditGlobalPointerDown)
  }
  if (!ensureAdmin()) {
    router.push('/assistant')
    return
  }
  await refreshAll()
})

onBeforeUnmount(() => {
  if (typeof document !== 'undefined') {
    document.removeEventListener('pointerdown', handleAuditGlobalPointerDown)
  }
  clearUserSearchTimer()
  clearConversationSearchTimer()
  clearUploadTaskTimer()
  if (confirmResolver) {
    confirmResolver(false)
    confirmResolver = null
  }
})
</script>

<template>
  <div class="admin-page">
    <header class="topbar">
      <div class="topbar-left">
        <button class="brand-home-btn" type="button" @click="goBack">
          <ProjectBrand size="small" />
          <span class="brand-home-text">返回助手</span>
        </button>
      </div>
      <div class="topbar-current">{{ sectionTitle }}</div>
    </header>

    <div class="admin-shell">
      <aside class="sidebar">
        <nav class="sidebar-nav">
          <button
            v-for="item in navItems"
            :key="item.key"
            class="nav-btn"
            :class="{ active: activeSection === item.key }"
            type="button"
            @click="activeSection = item.key"
          >
            <div class="nav-copy">
              <strong>{{ item.label }}</strong>
            </div>
          </button>
        </nav>
      </aside>

      <main class="workspace">
        <section v-if="activeSection === 'overview'" class="section-stack">
          <section class="workbench-hero">
            <article class="panel workbench-panel spotlight-panel" :class="overviewStatus.tone">
              <div class="panel-head knowledge-panel-head">
                <div>
                  <h3>欢迎回来</h3>
                </div>
                <span class="badge" :class="overviewStatus.badgeTone">{{ overviewStatus.title }}</span>
              </div>
              <div class="welcome-body">
                <div class="spotlight-copy">
                  <div class="spotlight-title">{{ overviewGreeting.title }}</div>
                  <p class="workbench-note">{{ overviewGreeting.description }}</p>
                  <div class="spotlight-chip-row">
                    <span class="chip">知识文档 {{ overviewSnapshot.knowledgeFileCount }} 份</span>
                    <span class="chip">累计会话 {{ overviewSnapshot.conversationCount }} 个</span>
                    <span class="chip">系统日志 {{ overviewSnapshot.logCount }} 条</span>
                  </div>
                </div>
                <div class="welcome-note-card">
                  <strong>{{ overviewStatus.title }}</strong>
                  <p>{{ overviewStatus.description }}</p>
                  <p>当前共有 {{ overviewSnapshot.userCount }} 个账号，知识库累计 {{ overviewSnapshot.knowledgeSectionCount }} 个片段。</p>
                </div>
              </div>
            </article>

            <article class="panel watch-panel">
              <div class="panel-head knowledge-panel-head">
                <div>
                  <h3>当前关注</h3>
                </div>
              </div>
              <div class="watch-list">
                <article v-for="item in overviewFocusList" :key="item.title" class="watch-item" :class="item.tone">
                  <div class="watch-head">
                    <strong>{{ item.title }}</strong>
                    <span class="badge" :class="item.tone">{{ item.tag }}</span>
                  </div>
                  <p>{{ item.desc }}</p>
                </article>
              </div>
            </article>
          </section>

          <section class="panel">
            <div class="panel-head">
              <div>
                <h3>核心概况</h3>
              </div>
            </div>
            <div class="pulse-grid">
              <article v-for="item in overviewPulseCards" :key="item.label" class="pulse-card">
                <span>{{ item.label }}</span>
                <strong>{{ item.value }}</strong>
                <p>{{ item.meta }}</p>
              </article>
            </div>
          </section>
        </section>

        <section v-else-if="activeSection === 'knowledge'" class="section-stack">
          <div class="knowledge-grid">
            <section class="panel">
              <div class="panel-head">
                <div>
                  <h3>文档列表</h3>
                </div>
                <div class="button-row knowledge-actions-row">
                  <button class="secondary-btn compact" type="button" @click="createNewDocument">新建</button>
                  <button class="primary-btn compact upload-trigger-btn" type="button" :disabled="uploadingDocument" @click="triggerUpload">
                    <span class="btn-icon" :class="{ spinning: uploadingDocument }" aria-hidden="true">
                      <svg v-if="!uploadingDocument" viewBox="0 0 24 24" fill="none">
                        <path d="M12 16V4" stroke="currentColor" stroke-linecap="round" stroke-linejoin="round" stroke-width="2" />
                        <path d="M7 9.5L12 4L17 9.5" stroke="currentColor" stroke-linecap="round" stroke-linejoin="round" stroke-width="2" />
                        <path d="M5 18.5H19" stroke="currentColor" stroke-linecap="round" stroke-linejoin="round" stroke-width="2" />
                      </svg>
                      <svg v-else viewBox="0 0 24 24" fill="none">
                        <path d="M12 3.5A8.5 8.5 0 1 1 3.5 12" stroke="currentColor" stroke-linecap="round" stroke-linejoin="round" stroke-width="2" />
                      </svg>
                    </span>
                    <span>{{ uploadButtonText }}</span>
                  </button>
                </div>
              </div>
              <input v-model="documentKeyword" class="field-control" type="text" placeholder="按文件名、标题或更新时间筛选文档" />
              <input ref="uploadInputRef" class="hidden-input" type="file" accept=".md,.markdown,.txt" @change="handleUpload" />
              <article
                v-if="uploadFeedback.visible"
                class="upload-progress-card"
                :class="{
                  success: uploadFeedback.status === 'success',
                  error: uploadFeedback.status === 'error',
                  active: uploadFeedback.status !== 'success' && uploadFeedback.status !== 'error',
                }"
              >
                <div class="upload-progress-head">
                  <div>
                    <strong>{{ uploadFeedback.fileName || '知识库文档' }}</strong>
                  </div>
                  <span class="badge" :class="uploadFeedback.status === 'error' ? 'danger' : uploadFeedback.status === 'success' ? 'success' : 'accent'">
                    {{ uploadPhaseLabel }}
                  </span>
                </div>
                <p>{{ uploadFeedback.message || '正在处理上传链路...' }}</p>
                <div class="upload-progress-meta">
                  <span>当前进度 {{ Math.max(0, Math.min(100, Number(uploadFeedback.progress || 0))) }}%</span>
                  <span v-if="uploadFeedback.taskId">任务 {{ uploadFeedback.taskId.slice(0, 8) }}</span>
                </div>
                <div class="upload-progress-track" aria-hidden="true">
                  <span class="upload-progress-fill" :style="{ width: `${Math.max(0, Math.min(100, Number(uploadFeedback.progress || 0)))}%` }"></span>
                </div>
              </article>
              <div class="list-stack">
                <button
                  v-for="item in pagedDocuments"
                  :key="item.fileName"
                  class="list-card"
                  :class="{ active: currentFile === item.fileName }"
                  type="button"
                  @click="selectDocument(item.fileName)"
                >
                  <strong>{{ item.title || item.fileName }}</strong>
                  <span>{{ item.fileName }}</span>
                  <span>片段数：{{ item.sectionCount || 0 }} · 更新时间：{{ item.updateTime || '未知' }}</span>
                </button>
                <div v-if="loadingDocuments" class="empty-box">知识库文档加载中...</div>
                <div v-else-if="!filteredDocuments.length" class="empty-box">{{ documentKeyword.trim() ? '没有匹配文档。' : '当前还没有知识库文档。' }}</div>
              </div>
              <div v-if="!loadingDocuments && filteredDocuments.length" class="knowledge-pagination">
                <span class="table-count">显示第 {{ documentRangeStart }} - {{ documentRangeEnd }} 条，共 {{ filteredDocuments.length }} 条</span>
                <div class="button-row knowledge-pagination-actions">
                  <button class="secondary-btn compact" type="button" :disabled="currentDocumentPage <= 1" @click="setDocumentPage(currentDocumentPage - 1)">上一页</button>
                  <span class="knowledge-pagination-page">第 {{ currentDocumentPage }} / {{ documentPageCount }} 页</span>
                  <button class="secondary-btn compact" type="button" :disabled="currentDocumentPage >= documentPageCount" @click="setDocumentPage(currentDocumentPage + 1)">下一页</button>
                </div>
              </div>
            </section>

            <section class="panel">
              <div class="panel-head">
                <div>
                  <h3>{{ editor.title || editor.fileName || '新建文档' }}</h3>
                </div>
                <div class="button-row">
                  <button class="danger-btn compact" type="button" :disabled="!currentFile" @click="deleteCurrentDocument">删除</button>
                  <button class="primary-btn compact" type="button" :disabled="savingDocument" @click="saveCurrentDocument">{{ savingDocument ? '保存中...' : '保存并重建' }}</button>
                </div>
              </div>
              <div class="chip-row">
                <span class="chip">当前文件：{{ editor.fileName || '未保存新文件' }}</span>
                <span class="chip">最近更新：{{ currentDocumentSummary?.updateTime || '尚未保存' }}</span>
              </div>
              <div class="form-grid">
                <input v-model="editor.fileName" class="field-control" type="text" placeholder="文件名，例如：情绪支持知识库.md" />
                <input v-model="editor.title" class="field-control" type="text" placeholder="标题，例如：情绪支持知识库" />
                <textarea v-model="editor.content" class="field-textarea" placeholder="# 标题&#10;&#10;#### 新问题&#10;请在这里填写内容"></textarea>
              </div>
            </section>
          </div>
        </section>

        <section v-else-if="activeSection === 'users'" class="section-stack">
          <section class="panel">
            <div class="panel-head">
              <div>
                <h3>用户管理</h3>
              </div>
              <button class="primary-btn compact" type="button" :disabled="loadingUsers" @click="fetchUsers">{{ loadingUsers ? '查询中...' : '查询用户' }}</button>
            </div>
            <div class="filter-grid users audit-filter-grid">
              <div class="audit-filter-card audit-filter-card-search">
                <span class="audit-filter-label">关键词检索</span>
                <input
                  v-model="userKeyword"
                  class="field-control audit-search-input"
                  type="text"
                  placeholder="按用户名或手机号搜索"
                  @keydown.enter.prevent="fetchUsers"
                />
                <span class="field-hint">关键词会匹配用户名和手机号，适合快速定位目标账号。</span>
              </div>
              <div class="audit-filter-card">
                <span class="audit-filter-label">角色范围</span>
                <div class="custom-select" :class="{ open: activeAuditDropdown === 'userRole' }">
                  <button class="custom-select-trigger" type="button" @click.stop="toggleAuditDropdown('userRole')">
                    <span class="custom-select-copy">
                      <strong>{{ selectedUserRoleOption.label }}</strong>
                      <span>{{ selectedUserRoleOption.meta }}</span>
                    </span>
                    <span class="custom-select-arrow" aria-hidden="true"></span>
                  </button>
                  <div v-if="activeAuditDropdown === 'userRole'" class="custom-select-menu">
                    <button
                      v-for="option in userRoleOptions"
                      :key="`user_role_${option.value || 'all'}`"
                      class="custom-select-option"
                      :class="{ active: userRoleFilter === option.value }"
                      type="button"
                      @click="selectUserRole(option.value)"
                    >
                      <span class="custom-select-option-title">{{ option.label }}</span>
                      <span class="custom-select-option-meta">{{ option.meta }}</span>
                    </button>
                  </div>
                </div>
              </div>
              <div class="audit-filter-card">
                <span class="audit-filter-label">账号状态</span>
                <div class="custom-select" :class="{ open: activeAuditDropdown === 'userStatus' }">
                  <button class="custom-select-trigger" type="button" @click.stop="toggleAuditDropdown('userStatus')">
                    <span class="custom-select-copy">
                      <strong>{{ selectedUserStatusOption.label }}</strong>
                      <span>{{ selectedUserStatusOption.meta }}</span>
                    </span>
                    <span class="custom-select-arrow" aria-hidden="true"></span>
                  </button>
                  <div v-if="activeAuditDropdown === 'userStatus'" class="custom-select-menu">
                    <button
                      v-for="option in userStatusOptions"
                      :key="`user_status_${option.value}`"
                      class="custom-select-option"
                      :class="{ active: userStatusFilter === option.value }"
                      type="button"
                      @click="selectUserStatus(option.value)"
                    >
                      <span class="custom-select-option-title">{{ option.label }}</span>
                      <span class="custom-select-option-meta">{{ option.meta }}</span>
                    </button>
                  </div>
                </div>
              </div>
            </div>
          </section>
          <section class="panel table-panel">
            <div class="table-head">
              <div>
                <h3>用户列表</h3>
              </div>
              <span class="table-count">共 {{ users.length }} 条</span>
            </div>
            <div v-if="loadingUsers" class="empty-box">用户列表加载中...</div>
            <div v-else-if="!users.length" class="empty-box">当前筛选条件下没有匹配用户。</div>
            <div v-else class="table-scroll">
              <table class="data-table user-data-table">
                <thead>
                  <tr>
                    <th>用户 ID</th>
                    <th>用户名</th>
                    <th>手机号</th>
                    <th>角色</th>
                    <th>状态</th>
                    <th>会话 / 消息</th>
                    <th>创建时间</th>
                    <th>操作</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="user in users" :key="user.id">
                    <td class="mono">{{ user.id }}</td>
                    <td class="user-name-cell">{{ user.userName || '未命名用户' }}</td>
                    <td class="user-phone-cell">{{ user.userPhone || '未填写' }}</td>
                    <td><span class="badge accent">{{ userRoleLabel(user.userRole) }}</span></td>
                    <td><span class="badge" :class="{ danger: user.deleted, success: !user.deleted }">{{ userStatusLabel(user) }}</span></td>
                    <td>{{ Number(user.conversationCount || 0) }} / {{ Number(user.messageCount || 0) }}</td>
                    <td>{{ formatDateTime(user.createTime) }}</td>
                    <td>
                      <div class="button-row">
                        <button class="secondary-btn compact" type="button" :disabled="operatingUserId === user.id" @click="switchUserRole(user)">{{ Number(user.userRole) === 1 ? '设为普通用户' : '设为管理员' }}</button>
                        <button class="danger-btn compact" :class="{ restore: user.deleted }" type="button" :disabled="operatingUserId === user.id" @click="toggleUserDelete(user)">{{ user.deleted ? '恢复用户' : '禁用用户' }}</button>
                      </div>
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>
          </section>
        </section>

        <section v-else-if="activeSection === 'conversations'" class="section-stack">
          <section class="panel">
            <div class="panel-head">
              <div>
                <h3>会话管理</h3>
              </div>
              <button class="primary-btn compact" type="button" :disabled="loadingConversations" @click="fetchConversations">{{ loadingConversations ? '查询中...' : '查询会话' }}</button>
            </div>
            <div class="filter-grid conversations audit-filter-grid">
              <div class="audit-filter-card audit-filter-card-search">
                <span class="audit-filter-label">关键词检索</span>
                <input v-model="conversationKeyword" class="field-control audit-search-input" type="text" placeholder="按标题、用户名搜索" @keydown.enter.prevent="fetchConversations" />
                <span class="field-hint">关键词仅匹配标题和用户名，摘要、用户 ID、会话 ID 都不参与搜索。</span>
              </div>
              <div class="audit-filter-card">
                <span class="audit-filter-label">会话模式</span>
                <div class="custom-select" :class="{ open: activeAuditDropdown === 'conversationMode' }">
                  <button class="custom-select-trigger" type="button" @click.stop="toggleAuditDropdown('conversationMode')">
                    <span class="custom-select-copy">
                      <strong>{{ selectedConversationModeOption.label }}</strong>
                      <span>{{ selectedConversationModeOption.meta }}</span>
                    </span>
                    <span class="custom-select-arrow" aria-hidden="true"></span>
                  </button>
                  <div v-if="activeAuditDropdown === 'conversationMode'" class="custom-select-menu">
                    <button
                      v-for="option in conversationModeOptions"
                      :key="`conversation_mode_${option.value || 'all'}`"
                      class="custom-select-option"
                      :class="{ active: conversationModeFilter === option.value }"
                      type="button"
                      @click="selectConversationMode(option.value)"
                    >
                      <span class="custom-select-option-title">{{ option.label }}</span>
                      <span class="custom-select-option-meta">{{ option.meta }}</span>
                    </button>
                  </div>
                </div>
              </div>
              <div class="audit-filter-card">
                <span class="audit-filter-label">置顶状态</span>
                <div class="custom-select" :class="{ open: activeAuditDropdown === 'conversationPinned' }">
                  <button class="custom-select-trigger" type="button" @click.stop="toggleAuditDropdown('conversationPinned')">
                    <span class="custom-select-copy">
                      <strong>{{ selectedConversationPinnedOption.label }}</strong>
                      <span>{{ selectedConversationPinnedOption.meta }}</span>
                    </span>
                    <span class="custom-select-arrow" aria-hidden="true"></span>
                  </button>
                  <div v-if="activeAuditDropdown === 'conversationPinned'" class="custom-select-menu">
                    <button
                      v-for="option in conversationPinnedOptions"
                      :key="`conversation_pinned_${option.value || 'all'}`"
                      class="custom-select-option"
                      :class="{ active: conversationPinnedFilter === option.value }"
                      type="button"
                      @click="selectConversationPinned(option.value)"
                    >
                      <span class="custom-select-option-title">{{ option.label }}</span>
                      <span class="custom-select-option-meta">{{ option.meta }}</span>
                    </button>
                  </div>
                </div>
              </div>
            </div>
          </section>
          <section class="panel table-panel">
            <div class="table-head">
              <div>
                <h3>会话列表</h3>
              </div>
              <span class="table-count">共 {{ conversations.length }} 条</span>
            </div>
            <div v-if="loadingConversations" class="empty-box">会话列表加载中...</div>
            <div v-else-if="!conversations.length" class="empty-box">当前筛选条件下没有匹配会话。</div>
            <div v-else class="table-scroll">
              <table class="data-table conversation-data-table">
                <thead>
                  <tr>
                    <th>会话标题</th>
                    <th>会话 ID</th>
                    <th>用户</th>
                    <th>模式</th>
                    <th>置顶</th>
                    <th>消息数</th>
                    <th>最近时间</th>
                    <th>会话摘要</th>
                    <th>操作</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="item in conversations" :key="`${item.conversationId}_${item.userId}`">
                    <td class="conversation-title-cell">
                      <strong>{{ item.title || '新对话' }}</strong>
                    </td>
                    <td class="mono">{{ item.conversationId }}</td>
                    <td>
                      <div class="conversation-user-cell">
                        <strong>{{ item.userName || '未知用户' }}</strong>
                        <span>ID：{{ item.userId || '无' }}</span>
                      </div>
                    </td>
                    <td>
                      <span class="audit-pill compact" :class="`tone-${conversationModeTone(item.mode || item.tag || '')}`">
                        <span class="audit-pill-dot"></span>
                        <span class="audit-pill-text">{{ conversationModeLabel(item.mode || item.tag || '') }}</span>
                      </span>
                    </td>
                    <td>
                      <span class="audit-pill compact" :class="item.pinned ? 'tone-pinned' : 'tone-normal'">
                        <span class="audit-pill-dot"></span>
                        <span class="audit-pill-text">{{ conversationPinnedLabel(item.pinned) }}</span>
                      </span>
                    </td>
                    <td>{{ Number(item.messageCount || 0) }}</td>
                    <td>{{ formatDateTime(item.lastTime) }}</td>
                    <td class="summary-cell">{{ item.summary || item.lastMessage || '无摘要' }}</td>
                    <td>
                      <button class="danger-btn compact" type="button" :disabled="removingConversationId === item.conversationId" @click="removeConversation(item)">{{ removingConversationId === item.conversationId ? '删除中...' : '删除会话' }}</button>
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>
          </section>
        </section>

        <section v-else class="section-stack">
          <section class="panel">
            <div class="panel-head">
              <div>
                <h3>系统日志</h3>
              </div>
              <div class="button-row">
                <button class="secondary-btn compact" type="button" :disabled="loadingLogs" @click="openLogsSection">{{ loadingLogs ? '刷新中...' : '刷新系统日志' }}</button>
                <button class="danger-btn compact" type="button" :disabled="loadingLogs" @click="clearLogs">清空日志</button>
              </div>
            </div>
            <div class="filter-grid logs">
              <select v-model="logLevel" class="field-control">
                <option value="">全部级别</option>
                <option value="INFO">INFO</option>
                <option value="WARN">WARN</option>
                <option value="ERROR">ERROR</option>
              </select>
              <input v-model="logCategory" class="field-control" type="text" placeholder="分类筛选（如 knowledge）" />
              <input v-model="logKeyword" class="field-control" type="text" placeholder="关键词筛选（摘要或详情）" />
              <button class="primary-btn compact" type="button" :disabled="loadingLogs" @click="fetchLogs">{{ loadingLogs ? '查询中...' : '查询系统日志' }}</button>
            </div>
          </section>
          <section class="panel table-panel">
            <div class="table-head">
              <div>
                <h3>日志列表</h3>
              </div>
              <span class="table-count">共 {{ logs.length }} 条</span>
            </div>
            <div v-if="loadingLogs" class="empty-box">系统日志加载中...</div>
            <div v-else-if="!logs.length" class="empty-box">当前没有可展示的系统日志。</div>
            <div v-else class="table-scroll">
              <table class="data-table">
                <thead>
                  <tr>
                    <th>级别</th>
                    <th>分类</th>
                    <th>摘要</th>
                    <th>操作人</th>
                    <th>详情</th>
                    <th>时间</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="item in logs" :key="item.id">
                    <td><span class="badge" :class="logTone(item.level)">{{ item.level || 'INFO' }}</span></td>
                    <td>{{ item.category || '未分类' }}</td>
                    <td>{{ item.summary || '无摘要' }}</td>
                    <td>{{ formatLogOperator(item) }}</td>
                    <td class="summary-cell">{{ item.displayDetail || item.detail || '无详情' }}</td>
                    <td>{{ item.createdAtLabel || '未知时间' }}</td>
                  </tr>
                </tbody>
              </table>
            </div>
          </section>
        </section>
      </main>
    </div>

    <ConfirmDialog
      :visible="confirmDialog.visible"
      :title="confirmDialog.title"
      :message="confirmDialog.message"
      :confirm-text="confirmDialog.confirmText"
      :cancel-text="confirmDialog.cancelText"
      :danger="confirmDialog.danger"
      @cancel="resolveConfirmDialog(false)"
      @confirm="resolveConfirmDialog(true)"
    />
  </div>
</template>

<style scoped>
.admin-page {
  --font-user-table: "Microsoft YaHei UI", "PingFang SC", "Noto Sans SC", "Source Han Sans SC", sans-serif;
  --font-conversation-table: "PingFang SC", "HarmonyOS Sans SC", "Noto Sans SC", "Source Han Sans SC", "Microsoft YaHei UI", sans-serif;
  position: relative;
  min-height: 100vh;
  width: 100%;
  padding: 0;
  overflow-x: hidden;
  background:
    radial-gradient(1200px 640px at 10% -10%, rgba(34, 211, 238, 0.18), transparent),
    radial-gradient(900px 580px at 100% 100%, rgba(245, 158, 11, 0.16), transparent),
    linear-gradient(180deg, #f4f8ff 0%, #edf3fb 100%);
  font-family: "Segoe UI Variable Text", "Microsoft YaHei UI", "PingFang SC", sans-serif;
}

.admin-page::before {
  content: '';
  position: fixed;
  inset: 0;
  pointer-events: none;
  background-image:
    linear-gradient(rgba(148, 163, 184, 0.12) 1px, transparent 1px),
    linear-gradient(90deg, rgba(148, 163, 184, 0.12) 1px, transparent 1px);
  background-size: 24px 24px;
  mask-image: radial-gradient(circle at 50% 32%, #000 0%, rgba(0, 0, 0, 0.78) 44%, transparent 82%);
}

.topbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16px;
  min-height: 72px;
  width: 100%;
  padding: 14px 24px;
  margin-bottom: 0;
  border-radius: 0;
  border: none;
  border-bottom: 1px solid rgba(148, 163, 184, 0.18);
  background: rgba(255, 255, 255, 0.72);
  backdrop-filter: blur(14px);
  box-shadow: 0 18px 40px rgba(15, 23, 42, 0.06);
  position: relative;
  z-index: 1;
}

.topbar-left {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.brand-home-btn {
  display: inline-flex;
  align-items: center;
  gap: 12px;
  padding: 8px 14px 8px 8px;
  border: 1px solid rgba(148, 163, 184, 0.18);
  border-radius: 20px;
  background: rgba(255, 255, 255, 0.88);
  cursor: pointer;
  box-shadow: 0 10px 24px rgba(15, 23, 42, 0.06);
  transition: transform 0.18s ease, box-shadow 0.18s ease, border-color 0.18s ease;
}

.brand-home-btn:hover {
  transform: translateY(-1px);
  border-color: rgba(36, 89, 216, 0.22);
  box-shadow: 0 14px 28px rgba(36, 89, 216, 0.12);
}

.brand-home-text {
  color: #2459d8;
  font-size: 13px;
  font-weight: 800;
  white-space: nowrap;
}

.topbar-current {
  color: #0f172a;
  font-size: 18px;
  font-weight: 800;
}

.admin-shell {
  position: relative;
  z-index: 1;
  display: grid;
  grid-template-columns: 280px minmax(0, 1fr);
  gap: 0;
  width: 100%;
  min-height: calc(100vh - 72px);
}

.sidebar {
  display: flex;
  flex-direction: column;
  gap: 16px;
  min-height: 100%;
  padding: 20px 18px;
  border-radius: 0;
  background: rgba(255, 255, 255, 0.72);
  border: none;
  border-right: 1px solid rgba(148, 163, 184, 0.18);
  backdrop-filter: blur(14px);
  box-shadow: inset -1px 0 0 rgba(255, 255, 255, 0.46);
}

.workspace {
  width: 100%;
  min-width: 0;
  padding: 24px 24px 32px;
  border-radius: 0;
  background: rgba(255, 255, 255, 0.42);
  backdrop-filter: blur(8px);
  box-shadow: none;
}

.sidebar-top,
.panel-head,
.entity-head {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: flex-start;
}

.sidebar-hero,
.sidebar-card,
.panel,
.entity-card {
  border-radius: 22px;
  border: 1px solid rgba(148, 163, 184, 0.14);
}

.sidebar-title {
  margin-top: 6px;
  color: #f8fafc;
  font-size: 20px;
  font-weight: 800;
}

.sidebar-badge {
  display: inline-flex;
  align-items: center;
  min-height: 28px;
  padding: 0 10px;
  border-radius: 999px;
  background: rgba(125, 211, 252, 0.14);
  color: #bae6fd;
  font-size: 11px;
  font-weight: 800;
  letter-spacing: 0.08em;
}

.sidebar-hero,
.sidebar-card {
  padding: 16px;
  background: rgba(255, 255, 255, 0.05);
}

.sidebar-hero h2,
.hero-panel h3,
.panel h3 {
  margin-top: 8px;
  color: #f8fafc;
  font-family: "Bahnschrift", "Segoe UI Variable Display", "Microsoft YaHei UI", sans-serif;
  font-weight: 900;
}

.sidebar-hero p,
.sidebar-card p,
.hero-copy p,
.entity-card p,
.list-card span,
.snippet,
.empty-box {
  line-height: 1.7;
}

.sidebar-hero p,
.sidebar-card p {
  margin-top: 10px;
  color: rgba(226, 232, 240, 0.72);
  font-size: 13px;
}

.sidebar-card.danger {
  border-color: rgba(239, 68, 68, 0.2);
  background: rgba(127, 29, 29, 0.18);
}

.sidebar-card.success {
  border-color: rgba(34, 197, 94, 0.22);
  background: rgba(20, 83, 45, 0.22);
}

.sidebar-card h3 {
  margin-top: 8px;
  color: #f8fafc;
  font-size: 20px;
}

.sidebar-nav,
.list-stack,
.log-stack {
  display: grid;
  gap: 10px;
}

.nav-btn,
.ghost-btn,
.primary-btn,
.secondary-btn,
.danger-btn,
.list-card,
.field-control,
.field-textarea {
  transition: transform 0.18s ease, box-shadow 0.18s ease, border-color 0.18s ease;
}

.nav-btn,
.ghost-btn,
.primary-btn,
.secondary-btn,
.danger-btn {
  min-height: 40px;
  border-radius: 14px;
  border: none;
  padding: 0 14px;
  font-size: 13px;
  font-weight: 800;
  cursor: pointer;
}

.nav-btn:hover,
.ghost-btn:hover,
.primary-btn:hover,
.secondary-btn:hover,
.danger-btn:hover,
.list-card:hover {
  transform: translateY(-1px);
}

.ghost-btn {
  border: 1px solid rgba(148, 163, 184, 0.16);
  background: rgba(255, 255, 255, 0.88);
  color: #0f172a;
}

.nav-btn {
  display: flex;
  align-items: center;
  gap: 12px;
  min-height: 56px;
  padding: 14px;
  border: 1px solid rgba(148, 163, 184, 0.18);
  background: rgba(255, 255, 255, 0.66);
  color: #0f172a;
  text-align: left;
  box-shadow: 0 10px 24px rgba(15, 23, 42, 0.04);
}

.nav-btn.active {
  border-color: rgba(36, 89, 216, 0.2);
  background: linear-gradient(135deg, rgba(36, 89, 216, 0.1), rgba(34, 211, 238, 0.12));
  box-shadow: 0 14px 28px rgba(36, 89, 216, 0.1);
}

.nav-copy {
  display: grid;
  gap: 0;
}

.nav-copy strong {
  color: #0f172a;
  font-size: 15px;
}

.primary-btn {
  background: linear-gradient(135deg, #0284c7 0%, #22d3ee 100%);
  color: #fff;
  box-shadow: 0 14px 28px rgba(14, 165, 233, 0.22);
}

.secondary-btn {
  border: 1px solid rgba(148, 163, 184, 0.2);
  background: rgba(255, 255, 255, 0.88);
  color: #0f172a;
}

.danger-btn {
  border: 1px solid rgba(248, 113, 113, 0.22);
  background: rgba(255, 244, 244, 0.9);
  color: #dc2626;
}

.danger-btn.restore {
  border-color: rgba(45, 212, 191, 0.22);
  background: rgba(236, 253, 245, 0.92);
  color: #0f766e;
}

.compact {
  min-height: 36px;
}

.tall {
  min-height: 52px;
}

.primary-btn:disabled,
.secondary-btn:disabled,
.danger-btn:disabled {
  opacity: 0.68;
  cursor: not-allowed;
  transform: none;
  box-shadow: none;
}

.panel h3,
.entity-card strong {
  color: #0f172a;
}

.hero-copy p,
.entity-card p,
.list-card span,
.snippet,
.empty-box {
  color: #64748b;
  font-size: 13px;
}

.button-row,
.chip-row {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.upload-trigger-btn {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

.knowledge-actions-row {
  flex-wrap: nowrap;
  align-items: center;
}

.knowledge-actions-row > button {
  flex: 0 0 auto;
  white-space: nowrap;
}

.knowledge-pagination {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-top: 14px;
  flex-wrap: wrap;
}

.knowledge-pagination-actions {
  align-items: center;
}

.knowledge-pagination-actions > button {
  width: auto;
  flex: 0 0 auto;
}

.knowledge-pagination-page {
  color: #475569;
  font-size: 12px;
  font-weight: 700;
}

.upload-progress-card {
  display: grid;
  gap: 10px;
  margin-top: 14px;
  padding: 16px 18px;
  border-radius: 18px;
  border: 1px solid rgba(148, 163, 184, 0.2);
  background:
    linear-gradient(135deg, rgba(255, 255, 255, 0.98), rgba(248, 250, 252, 0.92)),
    radial-gradient(circle at right top, rgba(14, 165, 233, 0.12), transparent 52%);
}

.upload-progress-card.active {
  border-color: rgba(14, 165, 233, 0.28);
}

.upload-progress-card.success {
  border-color: rgba(34, 197, 94, 0.28);
  background:
    linear-gradient(135deg, rgba(255, 255, 255, 0.98), rgba(240, 253, 244, 0.94)),
    radial-gradient(circle at right top, rgba(34, 197, 94, 0.12), transparent 52%);
}

.upload-progress-card.error {
  border-color: rgba(239, 68, 68, 0.24);
  background:
    linear-gradient(135deg, rgba(255, 255, 255, 0.98), rgba(254, 242, 242, 0.95)),
    radial-gradient(circle at right top, rgba(239, 68, 68, 0.1), transparent 52%);
}

.upload-progress-head,
.upload-progress-meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.upload-progress-meta {
  color: #64748b;
  font-size: 12px;
}

.upload-progress-track {
  width: 100%;
  height: 10px;
  overflow: hidden;
  border-radius: 999px;
  background: rgba(148, 163, 184, 0.18);
}

.upload-progress-fill {
  display: block;
  height: 100%;
  border-radius: inherit;
  background: linear-gradient(90deg, #0ea5e9, #0284c7);
  transition: width 0.28s ease;
}

.upload-progress-card.success .upload-progress-fill {
  background: linear-gradient(90deg, #22c55e, #16a34a);
}

.upload-progress-card.error .upload-progress-fill {
  background: linear-gradient(90deg, #ef4444, #dc2626);
}

.btn-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 16px;
  height: 16px;
  flex-shrink: 0;
}

.btn-icon svg {
  width: 100%;
  height: 100%;
}

.btn-icon.spinning {
  animation: admin-btn-spin 0.9s linear infinite;
}

.tag,
.badge,
.chip {
  display: inline-flex;
  align-items: center;
  min-height: 30px;
  padding: 0 12px;
  border-radius: 999px;
  font-size: 11px;
  font-weight: 800;
}

.tag,
.badge {
  background: rgba(148, 163, 184, 0.12);
  color: #0f172a;
}

.badge.accent {
  background: rgba(14, 165, 233, 0.12);
  color: #0369a1;
}

.badge.success,
.chip {
  background: rgba(15, 118, 110, 0.12);
  color: #0f766e;
}

.badge.warn {
  background: rgba(245, 158, 11, 0.12);
  color: #d97706;
}

.badge.danger {
  background: rgba(239, 68, 68, 0.12);
  color: #dc2626;
}

.knowledge-grid,
.filter-grid.users,
.filter-grid.conversations,
.filter-grid.logs,
.form-grid,
.card-grid,
.hero-mini-grid,
.pulse-grid,
.entry-grid,
.watch-list {
  display: grid;
  gap: 14px;
  width: 100%;
}

.workbench-panel {
  padding-bottom: 16px;
}

.workbench-hero {
  display: grid;
  grid-template-columns: minmax(0, 1.4fr) minmax(300px, 0.9fr);
  gap: 14px;
}

.spotlight-panel {
  position: relative;
  overflow: hidden;
  background:
    radial-gradient(circle at top right, rgba(14, 165, 233, 0.12), transparent 32%),
    linear-gradient(145deg, rgba(255, 255, 255, 0.96), rgba(247, 250, 255, 0.94));
}

.spotlight-panel.success {
  border-color: rgba(16, 185, 129, 0.18);
}

.spotlight-panel.danger {
  border-color: rgba(239, 68, 68, 0.18);
  background:
    radial-gradient(circle at top right, rgba(239, 68, 68, 0.1), transparent 30%),
    linear-gradient(145deg, rgba(255, 255, 255, 0.96), rgba(255, 247, 247, 0.94));
}

.spotlight-panel.busy {
  border-color: rgba(245, 158, 11, 0.18);
  background:
    radial-gradient(circle at top right, rgba(245, 158, 11, 0.1), transparent 30%),
    linear-gradient(145deg, rgba(255, 255, 255, 0.96), rgba(255, 252, 245, 0.94));
}

.welcome-body {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(260px, 0.72fr);
  gap: 18px;
  margin-top: 16px;
}

.spotlight-copy {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.spotlight-title {
  color: #0f172a;
  font-size: clamp(26px, 2.6vw, 38px);
  line-height: 1.08;
  font-family: "Bahnschrift", "Segoe UI Variable Display", "Microsoft YaHei UI", sans-serif;
  font-weight: 900;
}

.spotlight-chip-row {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.welcome-note-card,
.watch-item,
.pulse-card,
.entry-card {
  border-radius: 18px;
  border: 1px solid rgba(148, 163, 184, 0.14);
  background: rgba(255, 255, 255, 0.8);
  padding: 14px;
}

.welcome-note-card {
  display: flex;
  flex-direction: column;
  gap: 10px;
  background: rgba(255, 255, 255, 0.76);
}

.welcome-note-card strong,
.pulse-card strong,
.entry-head strong,
.watch-head strong {
  color: #0f172a;
  font-weight: 900;
}

.welcome-note-card strong {
  font-size: 22px;
}

.welcome-note-card p {
  color: #64748b;
  font-size: 13px;
  line-height: 1.7;
}

.watch-panel {
  background: linear-gradient(180deg, rgba(248, 250, 252, 0.98), rgba(241, 245, 249, 0.96));
}

.watch-list {
  margin-top: 16px;
  gap: 12px;
}

.watch-item {
  background: rgba(255, 255, 255, 0.84);
}

.watch-item.success {
  border-color: rgba(16, 185, 129, 0.18);
}

.watch-item.warn {
  border-color: rgba(245, 158, 11, 0.22);
}

.watch-item.danger {
  border-color: rgba(239, 68, 68, 0.22);
}

.watch-item.accent {
  border-color: rgba(14, 165, 233, 0.2);
}

.watch-head,
.entry-head {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: flex-start;
}

.watch-head strong {
  font-size: 16px;
}

.watch-item p {
  margin-top: 10px;
  color: #64748b;
  font-size: 12px;
  line-height: 1.7;
}

.workbench-note {
  margin-top: 14px;
  color: #64748b;
  font-size: 13px;
  line-height: 1.7;
}

.pulse-grid {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.pulse-card {
  background: rgba(255, 255, 255, 0.84);
}

.pulse-card span {
  color: #64748b;
  font-size: 12px;
  font-weight: 800;
}

.pulse-card strong {
  display: block;
  margin-top: 12px;
  font-size: 28px;
  font-family: "Bahnschrift", "Segoe UI Variable Display", "Microsoft YaHei UI", sans-serif;
}

.pulse-card p {
  margin-top: 10px;
  color: #64748b;
  font-size: 12px;
  line-height: 1.7;
}

.entry-grid {
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

.entry-card {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.entry-card .secondary-btn {
  align-self: flex-start;
}

.entry-head strong {
  display: block;
  margin-top: 8px;
  font-size: 20px;
  font-weight: 900;
}

.entry-meta {
  color: #475569;
  font-size: 12px;
  line-height: 1.7;
}

.section-stack {
  margin-top: 16px;
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.panel,
.entity-card {
  padding: 18px;
  background: rgba(255, 255, 255, 0.9);
}

.panel-head h3,
.entity-card strong {
  font-size: 22px;
}

.knowledge-grid {
  grid-template-columns: 340px minmax(0, 1fr);
}

.list-card {
  border: 1px solid rgba(148, 163, 184, 0.16);
  border-radius: 18px;
  padding: 14px;
  background: rgba(255, 255, 255, 0.88);
  text-align: left;
  cursor: pointer;
}

.list-card.active {
  border-color: rgba(14, 165, 233, 0.28);
  background: linear-gradient(135deg, rgba(240, 249, 255, 0.96), rgba(224, 242, 254, 0.92));
}

.list-card strong {
  color: #0f172a;
  font-size: 15px;
}

.field-control,
.field-textarea {
  width: 100%;
  border: 1px solid rgba(148, 163, 184, 0.2);
  border-radius: 16px;
  padding: 12px 14px;
  background: rgba(255, 255, 255, 0.9);
  color: #0f172a;
  font-size: 14px;
  outline: none;
}

.field-control:focus,
.field-textarea:focus {
  border-color: rgba(14, 165, 233, 0.42);
  box-shadow: 0 0 0 4px rgba(14, 165, 233, 0.08);
}

.field-stack {
  display: grid;
  gap: 6px;
}

.field-hint {
  color: #64748b;
  font-size: 12px;
  line-height: 1.5;
}

.audit-filter-label {
  color: #0f172a;
  font-size: 12px;
  font-weight: 800;
  letter-spacing: 0.05em;
}

.audit-filter-meta {
  color: #94a3b8;
  font-size: 12px;
  line-height: 1.4;
}

.form-grid {
  margin-top: 14px;
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.field-textarea {
  grid-column: 1 / -1;
  min-height: 540px;
  resize: vertical;
  line-height: 1.8;
  font-family: var(--font-code);
}

.filter-grid.users {
  margin-top: 14px;
  grid-template-columns: 1.4fr minmax(140px, 0.8fr) minmax(160px, 0.9fr);
}

.filter-grid.conversations {
  margin-top: 14px;
  grid-template-columns: minmax(280px, 1.55fr) minmax(200px, 0.9fr) minmax(200px, 0.9fr);
}

.audit-filter-grid {
  gap: 12px;
  align-items: stretch;
}

.audit-filter-card {
  display: grid;
  gap: 8px;
  padding: 14px 16px;
  border-radius: 18px;
  border: 1px solid rgba(148, 163, 184, 0.16);
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.96), rgba(248, 250, 252, 0.94));
  box-shadow: 0 10px 24px rgba(15, 23, 42, 0.04);
}

.audit-filter-card-search {
  background: linear-gradient(135deg, rgba(240, 249, 255, 0.96), rgba(248, 250, 252, 0.94));
  border-color: rgba(56, 189, 248, 0.18);
}

.audit-search-input,
.custom-select-trigger {
  min-height: 44px;
  border-radius: 14px;
}

.audit-search-input {
  background: rgba(255, 255, 255, 0.96);
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.8);
}

.audit-search-input::placeholder {
  color: #94a3b8;
}

.custom-select {
  position: relative;
}

.custom-select-trigger {
  width: 100%;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 10px 14px;
  border: 1px solid rgba(148, 163, 184, 0.2);
  background: rgba(255, 255, 255, 0.96);
  color: #0f172a;
  cursor: pointer;
  text-align: left;
  transition: border-color 0.18s ease, box-shadow 0.18s ease, transform 0.18s ease;
}

.custom-select.open .custom-select-trigger,
.custom-select-trigger:hover {
  border-color: rgba(14, 165, 233, 0.32);
  box-shadow: 0 0 0 4px rgba(14, 165, 233, 0.08);
}

.custom-select-copy {
  display: grid;
  gap: 4px;
  min-width: 0;
}

.custom-select-copy strong {
  color: #0f172a;
  font-size: 13px;
  font-weight: 800;
  line-height: 1.3;
}

.custom-select-copy span {
  color: #64748b;
  font-size: 12px;
  line-height: 1.35;
}

.custom-select-arrow {
  width: 10px;
  height: 10px;
  flex-shrink: 0;
  border-right: 2px solid #0284c7;
  border-bottom: 2px solid #0284c7;
  transform: rotate(45deg);
  transition: transform 0.18s ease;
}

.custom-select.open .custom-select-arrow {
  transform: rotate(225deg);
}

.custom-select-menu {
  position: absolute;
  top: calc(100% + 10px);
  left: 0;
  right: 0;
  z-index: 30;
  display: grid;
  gap: 6px;
  padding: 8px;
  border-radius: 18px;
  border: 1px solid rgba(148, 163, 184, 0.18);
  background: rgba(255, 255, 255, 0.98);
  box-shadow: 0 18px 40px rgba(15, 23, 42, 0.12);
  backdrop-filter: blur(12px);
}

.custom-select-option {
  width: 100%;
  display: grid;
  gap: 4px;
  padding: 10px 12px;
  border: 1px solid transparent;
  border-radius: 14px;
  background: transparent;
  cursor: pointer;
  text-align: left;
  transition: transform 0.16s ease, border-color 0.16s ease, background 0.16s ease, box-shadow 0.16s ease;
}

.custom-select-option:hover {
  transform: translateY(-1px);
  border-color: rgba(56, 189, 248, 0.22);
  background: linear-gradient(135deg, rgba(240, 249, 255, 0.98), rgba(236, 254, 255, 0.94));
  box-shadow: 0 10px 18px rgba(14, 165, 233, 0.08);
}

.custom-select-option.active {
  border-color: rgba(14, 165, 233, 0.26);
  background: linear-gradient(135deg, rgba(224, 242, 254, 0.98), rgba(236, 254, 255, 0.95));
}

.custom-select-option-title {
  color: #0f172a;
  font-size: 13px;
  font-weight: 800;
  line-height: 1.3;
}

.custom-select-option-meta {
  color: #64748b;
  font-size: 12px;
  line-height: 1.35;
}

.filter-grid.logs {
  margin-top: 14px;
  grid-template-columns: minmax(140px, 0.7fr) minmax(180px, 0.9fr) 1.4fr auto;
  align-items: center;
}

.card-grid {
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

.card-grid.two-col {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.table-panel {
  padding-top: 16px;
}

.table-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
}

.table-count {
  color: #64748b;
  font-size: 12px;
  font-weight: 700;
}

.table-scroll {
  width: 100%;
  overflow-x: auto;
  border: 1px solid rgba(148, 163, 184, 0.16);
  border-radius: 18px;
  background: rgba(255, 255, 255, 0.96);
}

.data-table {
  width: 100%;
  min-width: 1080px;
  border-collapse: collapse;
}

.data-table th,
.data-table td {
  padding: 14px 12px;
  border-bottom: 1px solid rgba(226, 232, 240, 0.9);
  text-align: left;
  vertical-align: top;
  color: #334155;
  font-size: 13px;
  line-height: 1.6;
}

.data-table th {
  background: #f8fafc;
  color: #475569;
  font-size: 12px;
  font-weight: 800;
}

.data-table tbody tr:hover {
  background: rgba(248, 250, 252, 0.9);
}

.user-data-table th,
.user-data-table td {
  font-family: var(--font-user-table);
}

.user-data-table td {
  font-size: 14px;
  color: #1e293b;
}

.conversation-data-table th,
.conversation-data-table td {
  font-family: var(--font-conversation-table);
}

.conversation-data-table td {
  color: #1e293b;
  font-size: 14px;
}

.conversation-data-table th {
  letter-spacing: 0.03em;
}

.user-data-table th {
  letter-spacing: 0.04em;
}

.user-name-cell {
  font-weight: 700;
  color: #0f172a;
}

.user-phone-cell {
  color: #475569;
}

.mono {
  font-family: Consolas, "Courier New", monospace;
  color: #475569;
  font-size: 12px;
}

.summary-cell {
  max-width: 400px;
  min-width: 250px;
  color: #475569;
  white-space: normal;
  word-break: break-word;
  line-height: 1.72;
}

.conversation-title-cell strong,
.conversation-user-cell strong {
  display: block;
  color: #0f172a;
  font-size: 14px;
  font-weight: 700;
}

.conversation-user-cell span {
  display: block;
  margin-top: 4px;
  color: #64748b;
  font-size: 12px;
}

.conversation-title-cell {
  min-width: 160px;
}

.conversation-user-cell {
  min-width: 116px;
}

.audit-pill {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  min-width: auto;
  max-width: 132px;
  padding: 6px 10px;
  border-radius: 999px;
  border: 1px solid rgba(148, 163, 184, 0.18);
  background: rgba(248, 250, 252, 0.92);
  white-space: nowrap;
}

.audit-pill-dot {
  width: 7px;
  height: 7px;
  border-radius: 999px;
  flex-shrink: 0;
  background: currentColor;
  box-shadow: 0 0 0 3px currentColor;
  opacity: 0.14;
}

.audit-pill-copy {
  display: grid;
  gap: 3px;
  min-width: 0;
}

.audit-pill-copy strong {
  color: inherit;
  font-size: 12px;
  font-weight: 800;
  line-height: 1.25;
}

.audit-pill-copy span {
  color: inherit;
  font-size: 11px;
  line-height: 1.35;
  opacity: 0.82;
}

.audit-pill.compact {
  justify-content: center;
}

.audit-pill-text {
  color: inherit;
  font-size: 12px;
  font-weight: 800;
  line-height: 1;
}

.audit-pill.tone-emotion {
  background: rgba(253, 242, 248, 0.92);
  border-color: rgba(244, 114, 182, 0.2);
  color: #be185d;
}

.audit-pill.tone-agent {
  background: rgba(239, 246, 255, 0.96);
  border-color: rgba(59, 130, 246, 0.2);
  color: #1d4ed8;
}

.audit-pill.tone-manus {
  background: rgba(245, 243, 255, 0.96);
  border-color: rgba(139, 92, 246, 0.2);
  color: #6d28d9;
}

.audit-pill.tone-auto {
  background: rgba(236, 253, 245, 0.96);
  border-color: rgba(16, 185, 129, 0.2);
  color: #047857;
}

.audit-pill.tone-neutral {
  background: rgba(248, 250, 252, 0.96);
  border-color: rgba(148, 163, 184, 0.2);
  color: #475569;
}

.audit-pill.tone-pinned {
  background: rgba(255, 247, 237, 0.96);
  border-color: rgba(251, 146, 60, 0.22);
  color: #c2410c;
}

.audit-pill.tone-normal {
  background: rgba(241, 245, 249, 0.96);
  border-color: rgba(148, 163, 184, 0.22);
  color: #475569;
}

.entity-card > span {
  color: #64748b;
  font-size: 12px;
}

.snippet {
  padding: 14px;
  border-radius: 16px;
  background: rgba(15, 23, 42, 0.03);
  border: 1px solid rgba(148, 163, 184, 0.12);
}

.empty-box {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 110px;
  border-radius: 20px;
  border: 1px dashed rgba(148, 163, 184, 0.22);
  background: rgba(15, 23, 42, 0.03);
  text-align: center;
}

.hidden-input {
  display: none;
}

@keyframes admin-btn-spin {
  to {
    transform: rotate(360deg);
  }
}

@media (max-width: 1260px) {
  .admin-shell,
  .knowledge-grid {
    grid-template-columns: 1fr;
  }

  .card-grid,
  .entry-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .workbench-hero,
  .welcome-body {
    grid-template-columns: 1fr;
  }

  .sidebar {
    border-right: none;
    border-bottom: 1px solid rgba(148, 163, 184, 0.12);
  }
}

@media (max-width: 960px) {
  .topbar,
  .admin-shell,
  .hero-mini-grid,
  .workbench-hero,
  .pulse-grid,
  .entry-grid,
  .form-grid,
  .filter-grid.users,
  .filter-grid.conversations,
  .filter-grid.logs,
  .card-grid,
  .card-grid.two-col {
    grid-template-columns: 1fr;
  }

  .panel-head,
  .entity-head {
    flex-direction: column;
  }

  .knowledge-panel-head {
    flex-direction: row;
    align-items: center;
  }

  .topbar-left,
  .topbar-current {
    width: 100%;
  }
}

@media (max-width: 720px) {
  .admin-page {
    padding: 0;
  }

  .topbar {
    padding: 14px 16px;
    border-radius: 0;
  }

  .workspace,
  .sidebar,
  .panel,
  .entity-card {
    border-radius: 22px;
  }

  .workspace {
    padding: 18px;
  }

  .ghost-btn,
  .primary-btn,
  .secondary-btn,
  .danger-btn {
    width: 100%;
  }

  .knowledge-actions-row > button {
    width: auto;
    flex: 1 1 0;
    min-width: 0;
  }

  .topbar-current,
  .button-row {
    width: 100%;
  }

  .knowledge-actions-row {
    width: auto;
  }

  .knowledge-pagination-actions {
    width: 100%;
  }

  .knowledge-pagination-actions > button {
    width: auto;
    flex: 1 1 0;
    min-width: 0;
  }

  .knowledge-pagination-page {
    width: 100%;
    text-align: center;
  }
}
</style>
