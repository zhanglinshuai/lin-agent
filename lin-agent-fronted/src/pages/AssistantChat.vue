<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import axios from 'axios'
import DOMPurify from 'dompurify'
import { marked } from 'marked'
import AuthForm from '@/components/AuthForm.vue'
import EmotionReportDialog from '@/components/EmotionReportDialog.vue'
import ProjectBrand from '@/components/ProjectBrand.vue'
import UserProfileDialog from '@/components/UserProfileDialog.vue'
import { downloadPdfFile } from '@/services/pdf'
import { generateEmotionReport } from '@/services/report'
import { openUnifiedSSE } from '@/services/sse'
import { clearAuthSession, emitAuthEvent } from '@/services/auth'
import { getUserProfile, updateUserProfile, uploadUserAvatar } from '@/services/user'

const router = useRouter()
const route = useRoute()
const UNIFIED_MODE = 'auto'

const input = ref('')
const chatId = ref('')
const messages = ref([])
const textareaRef = ref(null)
const renameInputRef = ref(null)
const messagesPanelRef = ref(null)
const showLoginModal = ref(false)
const showLogoutConfirm = ref(false)
const showProfileMenu = ref(false)
const showProfileDialog = ref(false)
const showEmotionReportDialog = ref(false)
const showRenameConversationModal = ref(false)
const showDeleteConversationModal = ref(false)
const loggedIn = ref(false)
const topAvatarUrl = ref('')
const userName = ref('')
const userRole = ref('')
const userProfile = ref({
  id: '',
  userName: '',
  userPhone: '',
  userAvatar: '',
  userRole: '',
  createTime: '',
  updateTime: '',
  createTimeLabel: '',
  updateTimeLabel: '',
})
const history = ref([])
const loadingHistory = ref(false)
const historyKeyword = ref('')
const conversationMenuId = ref('')
const historyListRef = ref(null)
const conversationMenuDirection = ref('down')
const activeConversationId = ref('')
const toastText = ref('')
const toastVisible = ref(false)
const uploadedFiles = ref([])
const uploadingFile = ref(false)
const uploadInputRef = ref(null)
const allowWebSearch = ref(false)
const allowKnowledgeBase = ref(false)
const nowTick = ref(Date.now())
const shouldStickMessagesToBottom = ref(true)
const renameConversationId = ref('')
const renameConversationTitle = ref('')
const deleteConversationId = ref('')
const deleteConversationTitle = ref('')
const renamingConversation = ref(false)
const deletingConversation = ref(false)
const pinningConversationId = ref('')
const savingProfile = ref(false)
const uploadingProfileAvatar = ref(false)
const generatingEmotionReport = ref(false)
const emotionReport = ref(null)
const emotionReportMessageIndex = ref(-1)
const messageFeedbackMap = ref({})
const activeStreamMessageIndex = ref(-1)

let currentStream = null
let flushTimer = null
let historySearchTimer = null
let historyFetchToken = 0
let streamBuf = new Map()
let animatedStreamQueues = new Map()
let animatedStreamTimers = new Map()
let elapsedTimer = null
let processViewportSyncTimer = null

const processPanelRefs = new Map()
const conversationMenuTriggerRefs = new Map()

const DEFAULT_AVATAR = `data:image/svg+xml;utf8,<svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 64 64'><rect fill='%23f4f5f7' width='64' height='64'/><circle cx='32' cy='24' r='12' fill='%239aa0a6'/><rect x='12' y='40' width='40' height='14' rx='7' fill='%239aa0a6'/></svg>`
const ASSISTANT_CACHE_KEY = 'assistant_conversation_cache_v1'
const ASSISTANT_ACTIVE_KEY = 'assistant_active_conversation_v1'
const ASSISTANT_FEEDBACK_KEY = 'assistant_message_feedback_v1'
const PROCESS_AUTO_COLLAPSE_LENGTH = 1200
const PROCESS_AUTO_COLLAPSE_LINES = 24
const PROCESS_AUTO_COLLAPSE_SOURCES = 6
const MESSAGES_AUTO_SCROLL_THRESHOLD = 88
const PROCESS_COLLAPSE_TOP_THRESHOLD = 12
const CONVERSATION_TITLE_MAX_LENGTH = 18
const HISTORY_MENU_ESTIMATED_HEIGHT = 136

const quickStartList = [
  {
    title: '聊聊最近的情绪',
    desc: '最近状态有点乱，想先把情绪和压力梳理清楚。',
    prompt: '最近状态有点乱，想先把情绪和压力梳理清楚。',
  },
  {
    title: '处理关系困扰',
    desc: '我和对象沟通总是卡住，想聊聊怎么把话说得更好一些。',
    prompt: '我和对象沟通总是卡住，想聊聊怎么把话说得更好一些。',
  },
  {
    title: '安排接下来的一周',
    desc: '帮我把这周要做的事情整理成一个更轻松的安排。',
    prompt: '帮我把这周要做的事情整理成一个更轻松的安排。',
  },
  {
    title: '把想法理成步骤',
    desc: '我脑子里有很多想法，想先帮我理成清晰的步骤。',
    prompt: '我脑子里有很多想法，想先帮我理成清晰的步骤。',
  },
]

const canSend = computed(() => input.value.trim().length > 0)
const isGeneratingReply = computed(() => activeStreamMessageIndex.value >= 0)
const canTriggerSend = computed(() => isGeneratingReply.value || canSend.value)
const isAdminUser = computed(() => loggedIn.value && String(userRole.value || '') === '1')
const canSubmitConversationRename = computed(() => normalizeConversationTitleInput(renameConversationTitle.value).length > 0)
const composerTipText = computed(() => {
  if (allowWebSearch.value && allowKnowledgeBase.value) {
    return '已同时开启联网搜索和知识库，会优先补外部资料，并在可命中时附带内部知识片段。'
  }
  if (allowWebSearch.value) {
    return '已开启联网搜索，涉及最新资料、外部信息或攻略时会优先调用搜索工具。'
  }
  if (allowKnowledgeBase.value) {
    return '已开启知识库，命中内部资料时会在过程区展示知识库来源，便于判断是否走了 RAG。'
  }
  return '想倾诉、想整理、想计划、想问清楚，都可以从这里开始。'
})

const ANSWER_SECTION_TITLES = [
  '一句话结论',
  '核心内容',
  '下一步建议',
  '行动建议',
  '落地建议',
  '风险提示',
  '继续聊聊',
  '下次可以继续聊',
  '如果你愿意，下次可以继续聊',
  '如果你愿意，下一次我们可以继续聊',
]

function escapeRegExp(value) {
  return String(value || '').replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
}

const ANSWER_SECTION_TITLE_PATTERN = new RegExp(`^(${ANSWER_SECTION_TITLES.map(escapeRegExp).join('|')})(?:[：:]\\s*(.*))?$`)

function sanitizeLinkUrl(url) {
  const value = String(url || '').trim()
  if (!value) {
    return ''
  }
  if (/^(javascript|vbscript|data):/i.test(value)) {
    return ''
  }
  return value
}

function sanitizeRenderedHtml(html) {
  return DOMPurify.sanitize(String(html || ''), {
    USE_PROFILES: { html: true },
    ADD_ATTR: ['target', 'rel'],
  })
}

const markdownRenderer = new marked.Renderer()

markdownRenderer.link = function ({ href, title, tokens }) {
  const safeHref = sanitizeLinkUrl(href)
  const label = this.parser.parseInline(tokens)
  const titleAttr = title ? ` title="${escapeHtml(title)}"` : ''
  if (!safeHref) {
    return label
  }
  return `<a href="${escapeHtml(safeHref)}" target="_blank" rel="noopener noreferrer"${titleAttr}>${label}</a>`
}

markdownRenderer.image = function ({ href, title, text }) {
  const safeHref = sanitizeLinkUrl(href)
  if (!safeHref) {
    return escapeHtml(text || '')
  }
  const titleAttr = title ? ` title="${escapeHtml(title)}"` : ''
  return `<img src="${escapeHtml(safeHref)}" alt="${escapeHtml(text || '')}"${titleAttr} />`
}

markdownRenderer.html = function ({ text }) {
  return escapeHtml(text)
}

const MARKED_RENDER_OPTIONS = {
  gfm: true,
  breaks: true,
  renderer: markdownRenderer,
}

const ANSWER_MARKED_RENDER_OPTIONS = {
  gfm: true,
  breaks: true,
  renderer: markdownRenderer,
}

function genChatId() {
  if (crypto && crypto.randomUUID) return crypto.randomUUID()
  return Math.random().toString(36).slice(2)
}

function normalizeAvatarUrl(u) {
  const s = String(u || '').trim()
  if (!s) return DEFAULT_AVATAR
  let p = s.replace(/\\/g, '/')
  if (p.startsWith('/static/')) return '/api' + p
  if (/^https?:\/\//i.test(p)) return p
  let m = p.match(/\/static\/avatar\/([^\/?#]+)/)
  if (m) return '/api/static/avatar/' + m[1]
  m = p.match(/\/upload\/avatar\/([^\/?#]+)$/)
  if (m) return '/api/static/avatar/' + m[1]
  m = p.match(/(?:^|\/)(avatar_[^\/?#]+)$/)
  if (m) return '/api/static/avatar/' + m[1]
  return DEFAULT_AVATAR
}

function currentUserId() {
  try {
    return String(localStorage.getItem('user_id') || '')
  } catch (e) {
    return ''
  }
}

function currentUsername() {
  try {
    return String(localStorage.getItem('user_name') || '')
  } catch (e) {
    return ''
  }
}

function formatProfileDate(value) {
  if (!value) {
    return ''
  }
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) {
    return String(value)
  }
  return date.toLocaleString('zh-CN', {
    hour12: false,
  }).replace(/\//g, '-')
}

function normalizeUserProfile(raw) {
  const source = raw && typeof raw === 'object' ? raw : {}
  const avatarUrl = normalizeAvatarUrl(source.userAvatar || '')
  return {
    id: String(source.id || currentUserId() || ''),
    userName: String(source.userName || ''),
    userPhone: String(source.userPhone || ''),
    userAvatar: avatarUrl,
    userRole: source.userRole === undefined || source.userRole === null ? '' : String(source.userRole),
    createTime: source.createTime || '',
    updateTime: source.updateTime || '',
    createTimeLabel: formatProfileDate(source.createTime),
    updateTimeLabel: formatProfileDate(source.updateTime),
  }
}

function applyUserProfile(raw) {
  const nextProfile = normalizeUserProfile(raw)
  userProfile.value = nextProfile
  userName.value = nextProfile.userName
  userRole.value = String(nextProfile.userRole || '')
  topAvatarUrl.value = nextProfile.userAvatar || DEFAULT_AVATAR
  try {
    if (nextProfile.userName) {
      localStorage.setItem('user_name', nextProfile.userName)
    }
    if (nextProfile.userRole !== undefined && nextProfile.userRole !== null) {
      localStorage.setItem('user_role', String(nextProfile.userRole))
    }
    if (topAvatarUrl.value) {
      localStorage.setItem('user_avatar', topAvatarUrl.value)
    }
  } catch (e) {
  }
  return nextProfile
}

function resetUserProfile() {
  userProfile.value = normalizeUserProfile({})
  userRole.value = ''
}

function syncAuthState() {
  try {
    const uid = localStorage.getItem('user_id') || ''
    const name = localStorage.getItem('user_name') || ''
    const role = localStorage.getItem('user_role') || ''
    const avatar = localStorage.getItem('user_avatar') || ''
    loggedIn.value = !!uid
    userName.value = String(name || '')
    userRole.value = String(role || '')
    topAvatarUrl.value = normalizeAvatarUrl(avatar)
  } catch (e) {
    loggedIn.value = false
    userName.value = ''
    userRole.value = ''
    topAvatarUrl.value = DEFAULT_AVATAR
  }
  if (!loggedIn.value) {
    resetUserProfile()
  }
}

function readConversationCache() {
  try {
    const raw = localStorage.getItem(ASSISTANT_CACHE_KEY)
    if (!raw) return {}
    return JSON.parse(raw)
  } catch (e) {
    return {}
  }
}

function writeConversationCache(cache) {
  try {
    localStorage.setItem(ASSISTANT_CACHE_KEY, JSON.stringify(cache))
  } catch (e) {
  }
}

function readFeedbackCache() {
  try {
    const raw = localStorage.getItem(ASSISTANT_FEEDBACK_KEY)
    if (!raw) return {}
    return JSON.parse(raw)
  } catch (e) {
    return {}
  }
}

function writeFeedbackCache(cache) {
  try {
    localStorage.setItem(ASSISTANT_FEEDBACK_KEY, JSON.stringify(cache))
  } catch (e) {
  }
}

function loadMessageFeedback() {
  const userId = currentUserId()
  if (!userId) {
    messageFeedbackMap.value = {}
    return
  }
  const all = readFeedbackCache()
  const current = all[userId]
  messageFeedbackMap.value = current && typeof current === 'object' ? current : {}
}

function persistMessageFeedback() {
  const userId = currentUserId()
  if (!userId) {
    return
  }
  const all = readFeedbackCache()
  all[userId] = messageFeedbackMap.value
  writeFeedbackCache(all)
}

function saveActiveConversationId(conversationId) {
  try {
    if (!conversationId) {
      localStorage.removeItem(ASSISTANT_ACTIVE_KEY)
      return
    }
    localStorage.setItem(ASSISTANT_ACTIVE_KEY, String(conversationId))
  } catch (e) {
  }
}

function readActiveConversationId() {
  try {
    return String(localStorage.getItem(ASSISTANT_ACTIVE_KEY) || '')
  } catch (e) {
    return ''
  }
}

function serializeMessagesForCache(list) {
  return (list || []).map(item => ({
    role: String(item?.role || ''),
    content: String(item?.content || ''),
    thinkingContent: String(item?.thinkingContent || ''),
    resultContent: String(item?.resultContent || ''),
    finalContent: String(item?.finalContent || ''),
    webSearchEnabled: !!item?.webSearchEnabled,
    knowledgeBaseEnabled: !!item?.knowledgeBaseEnabled,
    attachedFiles: Array.isArray(item?.attachedFiles) ? item.attachedFiles.map(file => String(file || '')) : [],
    loading: !!item?.loading,
    complete: !!item?.complete,
    thinkingCollapsed: item?.thinkingCollapsed !== false,
    processCollapseTouched: !!item?.processCollapseTouched,
    resultCollapsed: item?.resultCollapsed !== false,
    startedAt: Number(item?.startedAt || 0),
    completedAt: Number(item?.completedAt || 0),
    elapsedSeconds: Number(item?.elapsedSeconds || 0),
    routeMode: String(item?.routeMode || ''),
    reason: String(item?.reason || ''),
    sourcePrompt: String(item?.sourcePrompt || ''),
    requestId: String(item?.requestId || ''),
    lastEventSeq: Number(item?.lastEventSeq || 0),
  }))
}

function persistActiveConversationCache() {
  const conversationId = String(activeConversationId.value || chatId.value || '')
  const userId = currentUserId()
  if (!conversationId || !userId) return
  saveActiveConversationId(conversationId)
  const cache = readConversationCache()
  cache[conversationId] = {
    userId,
    updatedAt: Date.now(),
    messages: serializeMessagesForCache(messages.value),
  }
  writeConversationCache(cache)
}

function getCachedConversation(conversationId) {
  const userId = currentUserId()
  if (!conversationId || !userId) return null
  const cache = readConversationCache()
  const record = cache[String(conversationId)]
  if (!record || String(record.userId || '') !== userId) {
    return null
  }
  return Array.isArray(record.messages) ? record.messages : null
}

function normalizeConversationMessages(list) {
  let currentTag = ''
  return (list || []).map(item => {
    const role = String(item?.role || '').toLowerCase() === 'assistant' ? 'assistant' : 'user'
    const routeMode = normalizeRouteMode(item?.routeMode || currentTag || '')
    const webSearchEnabled = role === 'assistant' ? !!item?.webSearchEnabled : false
    const knowledgeBaseEnabled = role === 'assistant' ? !!item?.knowledgeBaseEnabled : false
    const visibleResultContent = (webSearchEnabled || knowledgeBaseEnabled) ? String(item?.resultContent || '') : ''
    if (routeMode) {
      currentTag = routeMode
    }
    return {
      role,
      content: String(item?.content || ''),
      thinkingContent: String(item?.thinkingContent || ''),
      resultContent: String(item?.resultContent || ''),
      finalContent: role === 'assistant' ? String(item?.finalContent || item?.content || '') : '',
      webSearchEnabled,
      knowledgeBaseEnabled,
      attachedFiles: Array.isArray(item?.attachedFiles) ? item.attachedFiles.map(file => String(file || '')) : [],
      loading: role === 'assistant' ? !!item?.loading : false,
      complete: role === 'assistant' ? !!item?.complete : true,
      thinkingCollapsed: resolveProcessCollapsed(item?.thinkingCollapsed, item?.thinkingContent, visibleResultContent, item?.processCollapseTouched),
      processCollapseTouched: !!item?.processCollapseTouched,
      resultCollapsed: item?.resultCollapsed !== false,
      startedAt: Number(item?.startedAt || 0),
      completedAt: Number(item?.completedAt || 0),
      elapsedSeconds: Number(item?.elapsedSeconds || 0),
      routeMode: role === 'assistant' ? currentTag : routeMode,
      reason: String(item?.reason || ''),
      sourcePrompt: String(item?.sourcePrompt || ''),
      requestId: String(item?.requestId || ''),
      lastEventSeq: Number(item?.lastEventSeq || 0),
    }
  })
}

function restoreActiveConversationFromCache() {
  const conversationId = readActiveConversationId()
  if (!conversationId) return false
  const cachedMessages = getCachedConversation(conversationId)
  if (!cachedMessages || !cachedMessages.length) return false
  activeConversationId.value = conversationId
  chatId.value = conversationId
  messages.value = normalizeConversationMessages(cachedMessages)
  return true
}

function findPendingAssistantMessage() {
  for (let i = messages.value.length - 1; i >= 0; i--) {
    const msg = messages.value[i]
    if (msg?.role === 'assistant' && msg?.loading) {
      return { msg, index: i }
    }
  }
  return null
}

function resetAssistantStreamState(msg) {
  if (!msg) return
  msg.thinkingContent = ''
  msg.resultContent = ''
  msg.finalContent = ''
  msg.loading = true
  msg.complete = false
  msg.completedAt = 0
  msg.elapsedSeconds = 0
  msg.lastEventSeq = 0
}

function resumePendingConversationFromCache() {
  const pending = findPendingAssistantMessage()
  if (!pending) {
    return false
  }
  const { msg, index } = pending
  const uid = currentUserId()
  const conversationId = String(activeConversationId.value || chatId.value || '')
  const prompt = String(msg?.sourcePrompt || '').trim()
  if (!uid || !conversationId || !prompt) {
    return false
  }
  const requestId = String(msg?.requestId || genChatId())
  msg.requestId = requestId
  currentStream?.close()
  activeStreamMessageIndex.value = index
  currentStream = openUnifiedSSE(
      prompt,
      conversationId,
      uid,
      UNIFIED_MODE,
      Array.isArray(msg?.attachedFiles) && msg.attachedFiles.length > 0,
      !!msg?.webSearchEnabled,
      !!msg?.knowledgeBaseEnabled,
      Array.isArray(msg?.attachedFiles) ? msg.attachedFiles : [],
      requestId,
      true,
      (payload) => handleUnifiedEvent(payload, index),
      () => finishAssistantMessage(index),
      (error) => handleAssistantStreamError(index, error)
  )
  persistActiveConversationCache()
  return true
}

function mapServerConversationMessages(raw) {
  let currentTag = ''
  return (raw || []).map(item => {
    const type = String(item?.messageType ?? item?.message_type ?? '')
    const metadata = parseMetadata(item?.metadata)
    const metadataTag = normalizeRouteMode(metadata?.tag || metadata?.mode || '')
    const role = type.toUpperCase() === 'USER' ? 'user' : 'assistant'
    const resultContent = role === 'assistant' ? String(metadata?.resultContent || '') : ''
    const webSearchEnabled = role === 'assistant' ? resolveWebSearchEnabled(metadata) : false
    const knowledgeBaseEnabled = role === 'assistant' ? resolveKnowledgeBaseEnabled(metadata) : false
    const routeMode = role === 'assistant' ? (metadataTag || currentTag) : metadataTag
    const thinkingContent = role === 'assistant'
      ? resolvePersistedThinkingContent(metadata, routeMode, webSearchEnabled, knowledgeBaseEnabled, resultContent)
      : ''
    const visibleResultContent = (webSearchEnabled || knowledgeBaseEnabled) ? resultContent : ''
    if (metadataTag) {
      currentTag = metadataTag
    }
    const content = role === 'user'
        ? String(metadata?.originalMessage || recoverOriginalUserMessage(item?.content) || '')
        : String(item?.content || '')
    return {
      role,
      content,
      thinkingContent,
      resultContent,
      finalContent: role === 'assistant' ? String(metadata?.finalContent || item?.content || '') : '',
      webSearchEnabled,
      knowledgeBaseEnabled,
      attachedFiles: Array.isArray(metadata?.uploadedFiles) ? metadata.uploadedFiles.map(file => String(file || '')) : [],
      loading: false,
      complete: true,
      thinkingCollapsed: resolveProcessCollapsed(undefined, thinkingContent, visibleResultContent),
      processCollapseTouched: false,
      resultCollapsed: true,
      startedAt: 0,
      completedAt: 0,
      elapsedSeconds: Number(metadata?.elapsedSeconds || 0),
      routeMode,
      reason: role === 'assistant' ? String(metadata?.reason || '') : '',
      sourcePrompt: role === 'assistant' ? String(metadata?.sourcePrompt || '') : '',
      requestId: '',
      lastEventSeq: 0,
    }
  })
}

async function hydrateConversationFromServer(conversationId, silent = false) {
  if (!conversationId) return false
  let res
  try {
    res = await axios.get('/api/chat_memory/getConversation', { params: { conversationId } })
  } catch (e) {
    if (!silent) {
      showToast(extractBackendErrorMessage(e, '会话加载失败'))
    }
    return false
  }
  const targetConversationId = String(conversationId)
  if (activeConversationId.value && String(activeConversationId.value) !== targetConversationId) {
    return false
  }
  const raw = Array.isArray(res.data) ? res.data : (Array.isArray(res.data?.data) ? res.data.data : [])
  activeConversationId.value = targetConversationId
  chatId.value = targetConversationId
  messages.value = normalizeConversationMessages(mapServerConversationMessages(raw))
  persistActiveConversationCache()
  scrollToBottomForced()
  return true
}

function resizeTextarea() {
  const el = textareaRef.value
  if (!el) return
  el.style.height = 'auto'
  el.style.height = Math.min(el.scrollHeight, 180) + 'px'
}

function focusComposer() {
  nextTick(() => {
    const el = textareaRef.value
    if (el && el.focus) {
      el.focus()
    }
  })
}

function triggerUpload() {
  uploadInputRef.value && uploadInputRef.value.click && uploadInputRef.value.click()
}

function toggleWebSearch() {
  allowWebSearch.value = !allowWebSearch.value
}

function toggleKnowledgeBase() {
  allowKnowledgeBase.value = !allowKnowledgeBase.value
}

function applyPrefillPrompt(prompt) {
  const text = String(prompt || '').trim()
  if (!text) return
  input.value = text
  nextTick(() => {
    resizeTextarea()
    focusComposer()
  })
}

function escapeHtml(s) {
  return String(s || '')
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;')
      .replace(/'/g, '&#39;')
}

function formatUrlText(u) {
  try {
    const url = new URL(u)
    const segs = url.pathname.split('/').map(x => {
      try { return decodeURIComponent(x) } catch (e) { return x }
    }).filter(Boolean)
    const path = segs.length ? ('/' + segs.join('/')) : ''
    const text = url.host + path
    return text.length > 64 ? (text.slice(0, 63) + '…') : text
  } catch (e) {
    try {
      const d = decodeURIComponent(u)
      return d.length > 64 ? (d.slice(0, 63) + '…') : d
    } catch (e2) {
      return u
    }
  }
}

function removeConversationCache(conversationId) {
  const normalizedConversationId = String(conversationId || '').trim()
  if (!normalizedConversationId) return
  const cache = readConversationCache()
  if (!Object.prototype.hasOwnProperty.call(cache, normalizedConversationId)) {
    return
  }
  delete cache[normalizedConversationId]
  writeConversationCache(cache)
}

function createHtmlToken(tokens, html) {
  const token = `@@HTMLTOKEN${tokens.length}@@`
  tokens.push(html)
  return token
}

function restoreHtmlTokens(text, tokens) {
  let restored = String(text || '')
  tokens.forEach((html, index) => {
    restored = restored.replaceAll(`@@HTMLTOKEN${index}@@`, html)
  })
  return restored
}

function trimOuterBlankLines(value) {
  return String(value || '')
      .replace(/^\uFEFF/, '')
      .replace(/^(?:[ \t]*\r?\n)+/, '')
      .replace(/(?:\r?\n[ \t]*)+$/, '')
}

function trimLineEndPreservingHardBreak(line) {
  const value = String(line || '').replace(/\r/g, '')
  if (/ {2,}$/.test(value)) {
    return value
  }
  return value.replace(/[ \t]+$/g, '')
}

function renderInline(s) {
  const source = String(s || '')
  if (!source) return ''
  const rendered = marked.parseInline(source, MARKED_RENDER_OPTIONS)
  return sanitizeRenderedHtml(typeof rendered === 'string' ? rendered : '')
}

function decodeJsonWrappedText(text) {
  const value = String(text || '')
  if (!(value.startsWith('"') && value.endsWith('"'))) {
    return value
  }
  try {
    const parsed = JSON.parse(value)
    return typeof parsed === 'string' ? parsed : value
  } catch (e) {
    return value
  }
}

function normalizeReadableText(value) {
  let text = decodeJsonWrappedText(trimOuterBlankLines(String(value || '')))
  if (!text) return ''

  text = text
      .replace(/\\r\\n/g, '\n')
      .replace(/\\n/g, '\n')
      .replace(/\\t/g, '    ')
      .replace(/\\"/g, '"')
      .replace(/\\\\/g, '\\')

  text = decodeJsonWrappedText(text)

  if (text.startsWith('"') && text.endsWith('"') && text.includes('\n') && text.length > 1) {
    text = text.slice(1, -1)
  }

  text = text
      .replace(/(^|\n)工具执行结果(?=【工具\d+：)/g, '$1## 工具执行结果\n')
      .replace(/【工具(\d+)：([^】]+)】/g, '\n### 工具$1：$2\n')
      .replace(/(##\s*工具执行结果)(?!\n)/g, '$1\n')
      .replace(/(##\s*工具执行结果)\s*(?=\S)/g, '$1\n')
      .replace(/(##\s*最终答复)(?!\n)/g, '$1\n')
      .replace(/(##\s*最终答复)\s*(?=\S)/g, '$1\n')
      .replace(/(^|\n)"(?=(文件名：|文件路径：|文件内容：|#|##\s*最终答复))/g, '$1')
      .replace(/"\s*(?=##\s*最终答复)/g, '\n')
      .replace(/\n{3,}/g, '\n\n')

  return trimOuterBlankLines(text)
}

function stripMarkdownMarkers(text) {
  return String(text || '')
      .replace(/!\[([^\]]*)\]\(([^)]+)\)/g, '$1')
      .replace(/\[([^\]]+)\]\(([^)]+)\)/g, '$1')
      .replace(/[`*_~]/g, '')
      .trim()
}

function isStandaloneListGroupLabel(text) {
  return /[：:]$/.test(stripMarkdownMarkers(text))
}

function isLikelyInlineListStart(text) {
  const value = String(text || '').trimStart()
  return /^([-*+]\s+|\d+\.\s+|\*\*|__|[A-Za-z\u4e00-\u9fa5（(【\[])/.test(value)
}

function splitInlineListFragments(text) {
  const source = String(text || '')
  if (!source.includes(' - ')) {
    return [source.trim()].filter(Boolean)
  }
  const fragments = []
  let current = ''
  let inCode = false
  let asteriskStrongOpen = false
  let underscoreStrongOpen = false

  for (let i = 0; i < source.length; i++) {
    const currentChar = source[i]
    const nextChar = source[i + 1]

    if (currentChar === '`') {
      inCode = !inCode
      current += currentChar
      continue
    }

    if (!inCode && currentChar === '*' && nextChar === '*') {
      asteriskStrongOpen = !asteriskStrongOpen
      current += '**'
      i++
      continue
    }

    if (!inCode && currentChar === '_' && nextChar === '_') {
      underscoreStrongOpen = !underscoreStrongOpen
      current += '__'
      i++
      continue
    }

    const inStrong = asteriskStrongOpen || underscoreStrongOpen
    if (!inCode && !inStrong && source.slice(i, i + 3) === ' - ' && isLikelyInlineListStart(source.slice(i + 3))) {
      const normalized = current.trim()
      if (normalized) {
        fragments.push(normalized)
      }
      current = ''
      i += 2
      continue
    }

    current += currentChar
  }

  const normalized = current.trim()
  if (normalized) {
    fragments.push(normalized)
  }
  return fragments.length ? fragments : [source.trim()].filter(Boolean)
}

function expandInlineMarkdownListLine(line) {
  const source = String(line || '')
  if (!source.includes(' - ')) {
    return source
  }

  const marker = getMarkdownListMarker(source)
  const baseText = marker ? marker.content : source.trim()
  const fragments = splitInlineListFragments(baseText)
  if (fragments.length <= 1) {
    return source
  }

  if (!marker) {
    return [fragments[0], ...fragments.slice(1).map(item => `- ${item}`)].join('\n')
  }

  const markerPrefix = marker.prefix || `${' '.repeat(marker.indent)}${marker.type === 'ol' ? `${marker.markerText || '1'}.` : '-'} `
  const childIndent = ' '.repeat(Math.max(marker.indent + 2, String(marker.prefix || '').length))
  const nestedIndent = `${childIndent}  `
  const lines = [`${markerPrefix}${fragments[0]}`]
  let inGroup = false

  fragments.slice(1).forEach(fragment => {
    const isGroup = isStandaloneListGroupLabel(fragment)
    const prefix = inGroup && !isGroup ? `${nestedIndent}- ` : `${childIndent}- `
    lines.push(`${prefix}${fragment}`)
    inGroup = isGroup
  })

  return lines.join('\n')
}

function isMarkdownListLine(line) {
  return /^(\s*)([-*+]|\d+\.)\s+/.test(String(line || ''))
}

function getMarkdownListIndent(line) {
  const matched = String(line || '').match(/^(\s*)(?:[-*+]|\d+\.)\s+/)
  return matched ? matched[1].length : -1
}

function isMarkdownHeadingLine(line) {
  return /^ {0,3}#{1,6}\s+/.test(String(line || ''))
}

function isMarkdownFenceLine(line) {
  return /^```|^~~~/.test(String(line || ''))
}

function isMarkdownBlockquoteLine(line) {
  return /^>\s?/.test(String(line || ''))
}

function isMarkdownTableLine(line) {
  return /^\s*\|.*\|\s*$/.test(String(line || ''))
}

function isMarkdownLikeBlockLine(line) {
  return isMarkdownHeadingLine(line)
      || isMarkdownHr(line)
      || isMarkdownFenceLine(line)
      || isMarkdownBlockquoteLine(line)
      || isMarkdownListLine(line)
      || isMarkdownTableLine(line)
}

const CODE_DIRECTIVE_HEADING_PATTERN = /^(?:include|define|ifdef|ifndef|endif|pragma|region|endregion)\b/i

function normalizeMarkdownStructuralLine(line, mode = 'default') {
  let value = String(line || '')
      .replace(/^\uFEFF+/, '')
      .replace(/[\u200B-\u200D\u2060]/g, '')
      .replace(/\u00A0/g, ' ')
      .replace(/\u3000/g, ' ')
  value = trimLineEndPreservingHardBreak(value)

  if (!value.trim()) {
    return ''
  }
  if (isMarkdownHr(value) || isMarkdownTableLine(value) || isMarkdownTableDivider(value)) {
    return value
  }

  value = value
      .replace(/^(\s*)[•●▪◦·‣]\s*(.+)$/, '$1- $2')
      .replace(/^(\s*)(\d+)[)）、】【、]\s*(.+)$/, '$1$2. $3')

  const headingMatch = value.match(/^(\s*)([＃#]{1,6})\s*(.+)$/)
  if (headingMatch) {
    const indent = headingMatch[1]
    const hashes = String(headingMatch[2] || '').replace(/＃/g, '#')
    let rest = String(headingMatch[3] || '').trimStart()
    if (mode !== 'answer' && mode !== 'tool' && CODE_DIRECTIVE_HEADING_PATTERN.test(rest)) {
      return `${indent}${hashes}${rest}`
    }
    rest = rest.replace(/^\\?#{1,6}\s+/, '')
    if (!rest) {
      return `${indent}${hashes}`
    }
    return `${indent}${hashes} ${rest}`
  }

  const blockquoteMatch = value.match(/^(\s*)([>＞]+)\s*(.+)$/)
  if (blockquoteMatch) {
    const indent = blockquoteMatch[1]
    const markers = String(blockquoteMatch[2] || '').replace(/＞/g, '>')
    const rest = String(blockquoteMatch[3] || '').trimStart()
    return rest ? `${indent}${markers} ${rest}` : `${indent}${markers}`
  }

  const bulletMatch = value.match(/^(\s*)([-*+])\s*(.+)$/)
  if (bulletMatch) {
    return `${bulletMatch[1]}${bulletMatch[2]} ${String(bulletMatch[3] || '').trimStart()}`
  }

  const orderedMatch = value.match(/^(\s*)(\d+)\.\s*(.+)$/)
  if (orderedMatch) {
    return `${orderedMatch[1]}${orderedMatch[2]}. ${String(orderedMatch[3] || '').trimStart()}`
  }

  return value
}

function normalizeMarkdownSource(text, mode = 'default') {
  const lines = String(text || '').split(/\r?\n/)
  const normalizedLines = []
  let inFence = false
  let fenceChar = ''

  lines.forEach(rawLine => {
    const line = String(rawLine || '').replace(/\r/g, '')
    const trimmedStart = line.trimStart()
    const fenceMatch = trimmedStart.match(/^(```+|~~~+)/)
    if (fenceMatch) {
      const currentFenceChar = fenceMatch[1][0]
      if (!inFence) {
        inFence = true
        fenceChar = currentFenceChar
      } else if (currentFenceChar === fenceChar) {
        inFence = false
        fenceChar = ''
      }
      normalizedLines.push(trimLineEndPreservingHardBreak(line.replace(/^\uFEFF+/, '')))
      return
    }
    if (inFence) {
      normalizedLines.push(line.replace(/^\uFEFF+/, ''))
      return
    }
    normalizedLines.push(normalizeMarkdownStructuralLine(line, mode))
  })

  return normalizedLines.join('\n')
}

function normalizeOrderedMarkerSpacing(line, mode = 'default') {
  return normalizeMarkdownStructuralLine(line, mode)
}

function normalizeAnswerMarkdownLine(line) {
  const value = normalizeOrderedMarkerSpacing(
      String(line || '')
          .replace(/\u00A0/g, ' ')
          .replace(/\t/g, '    ')
          .replace(/\r/g, '')
  )
  const trimmed = value.trim()
  if (!trimmed) {
    return ''
  }
  if (!isMarkdownHeadingLine(trimmed)) {
    const sectionMatch = trimmed.match(ANSWER_SECTION_TITLE_PATTERN)
    if (sectionMatch) {
      const sectionBody = String(sectionMatch[2] || '').trim()
      return sectionBody ? `## ${sectionMatch[1]}\n${sectionBody}` : `## ${sectionMatch[1]}`
    }
  }
  return expandInlineMarkdownListLine(value)
}

function normalizeMarkdownLineBreaks(lines) {
  const normalizedLines = []
  const sourceLines = Array.isArray(lines) ? lines : String(lines || '').split(/\r?\n/)

  sourceLines.forEach(rawLine => {
    const line = trimLineEndPreservingHardBreak(rawLine)
    const trimmed = line.trim()

    if (!trimmed) {
      if (normalizedLines.length && normalizedLines[normalizedLines.length - 1] !== '') {
        normalizedLines.push('')
      }
      return
    }

    const previousNonBlank = [...normalizedLines].reverse().find(item => String(item || '').trim()) || ''
    const lastLine = normalizedLines[normalizedLines.length - 1]
    const currentIsTopLevelList = isMarkdownListLine(line) && getMarkdownListIndent(line) === 0
    const previousIsTopLevelList = isMarkdownListLine(previousNonBlank) && getMarkdownListIndent(previousNonBlank) === 0

    if (previousNonBlank && lastLine !== '') {
      if (isMarkdownHeadingLine(line) || isMarkdownFenceLine(line) || isMarkdownBlockquoteLine(line) || isMarkdownHr(line)) {
        normalizedLines.push('')
      } else if (currentIsTopLevelList && !previousIsTopLevelList && !isMarkdownHeadingLine(previousNonBlank)) {
        normalizedLines.push('')
      } else if (!isMarkdownLikeBlockLine(line) && previousIsTopLevelList) {
        normalizedLines.push('')
      }
    }

    normalizedLines.push(line)
  })

  return normalizedLines.join('\n').replace(/\n{3,}/g, '\n\n').trim()
}

function normalizeStructuredAnswerText(value, mode = 'default') {
  let text = String(value || '')
  if (!text) return ''
  if (mode === 'answer') {
    text = normalizeMarkdownLineBreaks(
        normalizeMarkdownSource(
            text
            .replace(/：---/g, '：\n---')
            .replace(/([：:。！？.!?；;）)】])\s*(\d+\.)/g, '$1\n$2')
            ,
            'answer'
        )
            .split(/\r?\n/)
            .flatMap(line => normalizeAnswerMarkdownLine(line).split('\n'))
    )
  }
  if (mode === 'tool') {
    text = normalizeMarkdownLineBreaks(
        normalizeMarkdownSource(
            text
            .replace(/(搜索词：)/g, '\n$1')
            .replace(/(搜索提供方：)/g, '\n$1')
            .replace(/(搜索摘要：)/g, '\n$1')
            .replace(/(结果\s+\d+：)/g, '\n\n$1')
            ,
            'tool'
        )
            .split(/\r?\n/)
    )
  }
  if (mode !== 'answer' && mode !== 'tool') {
    text = normalizeMarkdownLineBreaks(
        normalizeMarkdownSource(text, mode)
    )
  }
  return normalizeMarkdownSource(text, mode).trim()
}

function looksLikeMarkdownDocument(text) {
  const lines = String(text || '').split(/\r?\n/)
  return lines.some(line => {
    const value = String(line || '')
    return isMarkdownHeadingLine(value)
        || isMarkdownFenceLine(value)
        || isMarkdownBlockquoteLine(value)
        || isMarkdownListLine(value)
        || isMarkdownTableLine(value)
        || isMarkdownHr(value)
        || /\[[^\]]+\]\((https?:\/\/|\/)/.test(value)
        || /(^|[^*])\*\*[^*\n]+\*\*/.test(value)
        || /(^|[^_])__[^_\n]+__/.test(value)
  })
}

function prepareAnswerMarkdownSource(text) {
  let value = trimOuterBlankLines(String(text || ''))
  if (!value) {
    return ''
  }
  value = value
      .replace(/^\s*#{1,6}\s*最终答复\s*$/gm, '')
      .replace(/^\s*最终答复[：:]?\s*$/gm, '')
      .replace(/\n{3,}/g, '\n\n')
  value = trimOuterBlankLines(value)
  value = normalizeMarkdownSource(value, 'answer')
      .replace(/\n{3,}/g, '\n\n')
  value = trimOuterBlankLines(value)
  if (!value) {
    return ''
  }
  if (looksLikeMarkdownDocument(value)) {
    return value
  }
  return normalizeStructuredAnswerText(value, 'answer')
}

function stripAnswerWrapperText(text) {
  return String(text || '')
      .replace(/^\s*#{1,6}\s*最终答复\s*$/gm, '')
      .replace(/^\s*最终答复[：:]?\s*$/gm, '')
      .replace(/\n{3,}/g, '\n\n')
      .trim()
}

function isMarkdownHr(line) {
  return /^ {0,3}(-{3,}|\*{3,}|_{3,})\s*$/.test(String(line || ''))
}

function isMarkdownHeading(line) {
  return /^ {0,3}#{1,6}\s+/.test(String(line || ''))
}

function getMarkdownListMarker(line) {
  let matched = String(line || '').match(/^(\s*)([-*+])\s+(.*)$/)
  if (matched) {
    return {
      type: 'ul',
      indent: matched[1].length,
      markerText: matched[2],
      prefix: `${matched[1]}${matched[2]} `,
      content: matched[3],
    }
  }
  matched = String(line || '').match(/^(\s*)(\d+)\.\s+(.*)$/)
  if (matched) {
    return {
      type: 'ol',
      indent: matched[1].length,
      markerText: matched[2],
      prefix: `${matched[1]}${matched[2]}. `,
      content: matched[3],
    }
  }
  return null
}

function isMarkdownTableDivider(line) {
  return /^\s*\|?(?:\s*:?-{3,}:?\s*\|)+\s*:?-{3,}:?\s*\|?\s*$/.test(String(line || ''))
}

function parseMarkdownTableCells(line) {
  return String(line || '')
      .trim()
      .replace(/^\|/, '')
      .replace(/\|$/, '')
      .split('|')
      .map(cell => String(cell || '').trim())
}

function renderMarkdownTable(lines, startIndex) {
  if (startIndex + 1 >= lines.length) {
    return null
  }
  const headerLine = lines[startIndex]
  const dividerLine = lines[startIndex + 1]
  if (!headerLine.includes('|') || !isMarkdownTableDivider(dividerLine)) {
    return null
  }
  const headerCells = parseMarkdownTableCells(headerLine)
  if (!headerCells.length) {
    return null
  }
  const dividerCells = parseMarkdownTableCells(dividerLine)
  const rows = []
  let cursor = startIndex + 2
  while (cursor < lines.length && String(lines[cursor] || '').trim() && lines[cursor].includes('|')) {
    rows.push(parseMarkdownTableCells(lines[cursor]))
    cursor++
  }
  let html = '<table><thead><tr>'
  html += headerCells.map(cell => `<th>${renderInline(cell)}</th>`).join('')
  html += '</tr></thead>'
  if (rows.length) {
    html += '<tbody>'
    rows.forEach(row => {
      html += '<tr>'
      for (let i = 0; i < headerCells.length; i++) {
        html += `<td>${renderInline(row[i] || '')}</td>`
      }
      html += '</tr>'
    })
    html += '</tbody>'
  }
  html += '</table>'
  return { html, nextIndex: cursor - 1 }
}

function renderMarkdownList(lines, startIndex, mode = 'default') {
  const firstMarker = getMarkdownListMarker(lines[startIndex])
  if (!firstMarker) {
    return null
  }
  const items = []
  let cursor = startIndex
  let currentItem = null
  while (cursor < lines.length) {
    const line = lines[cursor]
    const marker = getMarkdownListMarker(line)
    if (marker && marker.type === firstMarker.type && marker.indent === firstMarker.indent) {
      if (currentItem) {
        items.push(currentItem)
      }
      currentItem = {
        parts: [marker.content],
      }
      cursor++
      continue
    }
    if (!String(line || '').trim()) {
      if (currentItem) {
        const nextLine = lines[cursor + 1]
        const nextIndent = String(nextLine || '').match(/^(\s*)/)?.[1]?.length || 0
        if (String(nextLine || '').trim() && nextIndent > firstMarker.indent) {
          currentItem.parts.push('')
          cursor++
          continue
        }
      }
      break
    }
    const indent = String(line || '').match(/^(\s*)/)?.[1]?.length || 0
    if (indent > firstMarker.indent) {
      const normalizedLine = String(line || '').startsWith(' '.repeat(firstMarker.indent + 2))
          ? String(line || '').slice(firstMarker.indent + 2)
          : String(line || '').trim()
      currentItem?.parts.push(normalizedLine)
      cursor++
      continue
    }
    break
  }
  if (currentItem) {
    items.push(currentItem)
  }
  const tag = firstMarker.type === 'ol' ? 'ol' : 'ul'
  let html = `<${tag}>`
  items.forEach(item => {
    const itemContent = item.parts.join('\n').trim()
    let itemHtml = renderMarkdown(itemContent, mode)
    if (/^<p>[\s\S]*<\/p>$/.test(itemHtml)) {
      itemHtml = itemHtml.replace(/^<p>([\s\S]*)<\/p>$/, '$1')
    }
    html += `<li>${itemHtml}</li>`
  })
  html += `</${tag}>`
  return { html, nextIndex: cursor - 1 }
}

function renderMarkdownBlockquote(lines, startIndex, mode) {
  const quoteLines = []
  let cursor = startIndex
  while (cursor < lines.length) {
    const line = String(lines[cursor] || '')
    const matched = line.match(/^>\s?(.*)$/)
    if (matched) {
      quoteLines.push(matched[1] || '')
      cursor++
      continue
    }
    if (!line.trim()) {
      quoteLines.push('')
      cursor++
      continue
    }
    break
  }
  if (!quoteLines.length) {
    return null
  }
  return {
    html: `<blockquote>${renderMarkdown(quoteLines.join('\n'), mode)}</blockquote>`,
    nextIndex: cursor - 1,
  }
}

function renderMarkdownCodeBlock(lines, startIndex) {
  const matched = String(lines[startIndex] || '').match(/^```([\w-]*)\s*$/)
  if (!matched) {
    return null
  }
  const language = String(matched[1] || '').trim()
  const codeLines = []
  let cursor = startIndex + 1
  while (cursor < lines.length && !/^```/.test(String(lines[cursor] || ''))) {
    codeLines.push(String(lines[cursor] || ''))
    cursor++
  }
  const languageClass = language ? ` class="language-${escapeHtml(language)}"` : ''
  return {
    html: `<pre><code${languageClass}>${escapeHtml(codeLines.join('\n'))}</code></pre>`,
    nextIndex: cursor < lines.length ? cursor : lines.length - 1,
  }
}

function renderMarkdownParagraph(lines, startIndex, mode) {
  const parts = []
  let cursor = startIndex
  while (cursor < lines.length) {
    const line = String(lines[cursor] || '')
    if (!line.trim()) {
      break
    }
    if (
        /^```/.test(line)
        || isMarkdownHeading(line)
        || isMarkdownHr(line)
        || /^>\s?/.test(line)
        || getMarkdownListMarker(line)
        || renderMarkdownTable(lines, cursor)
    ) {
      break
    }
    if (mode === 'answer' && /^(一句话结论|核心内容|下一步建议|行动建议|落地建议|风险提示|继续聊聊|下次可以继续聊|如果你愿意[，,]?下次可以继续聊|如果你愿意[，,]?下一次我们可以继续聊)\s*$/.test(line.trim())) {
      break
    }
    parts.push(line)
    cursor++
  }
  return {
    html: `<p>${parts.map(part => renderInline(part)).join('<br/>')}</p>`,
    nextIndex: cursor - 1,
  }
}

function renderMarkdown(md, mode = 'default') {
  const readableText = normalizeReadableText(md)
  const src = mode === 'answer'
      ? prepareAnswerMarkdownSource(readableText)
      : normalizeStructuredAnswerText(readableText, mode)
  if (!src) return ''
  const rendered = marked.parse(src, MARKED_RENDER_OPTIONS)
  return sanitizeRenderedHtml(typeof rendered === 'string' ? rendered : '')
}

function normalizeAnswerSectionTitle(title) {
  const text = String(title || '')
      .replace(/^#{1,6}\s*/, '')
      .replace(/^(\d+)\.(\S)/, '$1. $2')
      .trim()
  return text
}

function isAdviceSectionTitle(title) {
  return ['下一步建议', '行动建议', '落地建议', '风险提示'].includes(String(title || '').trim())
}

function isFollowupSectionTitle(title) {
  const text = String(title || '').replace(/[：:]/g, '').trim()
  return ['继续聊聊', '下次可以继续聊', '如果你愿意，下次可以继续聊', '如果你愿意，下一次我们可以继续聊'].includes(text)
}

function normalizeSpecialSectionTitle(title) {
  return isFollowupSectionTitle(title) ? '继续聊聊' : String(title || '').trim()
}

function splitAnswerItemLine(line) {
  let text = String(line || '').trim()
  if (!text) return []
  if (text.startsWith('- ')) {
    text = text.slice(2).trim()
  }
  return text
      .split(/-\s+(?=(?:重点|关键信息|这是什么|为什么重要|建议动作|建议|风险提示|适用场景|可以怎么做|先做什么)[：:])/)
      .flatMap(item => String(item || '').split(/-\s+(?=(?:[^-：:\n]{2,24}[：:]))/))
      .flatMap(item => {
        const normalized = String(item || '').trim()
        if (!/^\d+\.\s*/.test(normalized)) {
          return [normalized]
        }
        return normalized.split(/(?=\d+\.\s)/)
      })
      .map(item => String(item || '').trim())
      .filter(Boolean)
}

function parseAnswerStructure(value) {
  const raw = normalizeReadableText(value)
  if (!raw) return null
  let text = raw
      .replace(/##\s*一句话结论\s*/g, '\n## 一句话结论\n')
      .replace(/##\s*(核心内容|下一步建议|行动建议|落地建议|风险提示|继续聊聊|下次可以继续聊|如果你愿意[，,]?下次可以继续聊|如果你愿意[，,]?下一次我们可以继续聊)\s*/g, '\n## $1\n')
      .replace(/###\s*(\d+\.)/g, '\n### $1')
      .replace(/(^|\n)###\s*(\d+)\.([^\n\-：:]{2,36})-\s+/g, '$1### $2. $3\n- ')
      .replace(/([^\n])-\s+(?=[A-Za-z\u4e00-\u9fa5])/g, '$1\n- ')
      .replace(/^##\s*一句话结论[：:]?\s*$/gm, '一句话结论')
      .replace(/^##\s*(核心内容|下一步建议|行动建议|落地建议|风险提示|继续聊聊|下次可以继续聊|如果你愿意[，,]?下次可以继续聊|如果你愿意[，,]?下一次我们可以继续聊)[：:]?\s*$/gm, '$1')
      .replace(/^#{3,6}\s*/gm, '')
      .replace(/(^|\n)(\d+)\.(\S)/g, '$1$2. $3')
      .replace(/\n{3,}/g, '\n\n')
      .trim()
  const lines = text.split(/\r?\n/).map(line => String(line || '').trim()).filter(Boolean)
  if (!lines.length) return null

  let summary = ''
  let waitingSummary = false
  const sections = []
  const leadParagraphs = []
  let currentSection = null

  const pushCurrentSection = () => {
    if (!currentSection) return
    currentSection.items = currentSection.items.filter(Boolean)
    if (currentSection.title || currentSection.items.length) {
      sections.push(currentSection)
    }
    currentSection = null
  }

  for (let i = 0; i < lines.length; i++) {
    const line = lines[i]
    if (!summary && /^一句话结论[：:]?\s*$/.test(line)) {
      waitingSummary = true
      continue
    }
    if (!summary && line.startsWith('一句话结论')) {
      summary = line.replace(/^一句话结论[：:]?\s*/, '').trim()
      waitingSummary = false
      continue
    }
    if (waitingSummary) {
      summary = line.replace(/^一句话结论[：:]?\s*/, '').trim()
      waitingSummary = false
      continue
    }
    const inlineSectionTitle = line.match(/^(核心内容|下一步建议|行动建议|落地建议|风险提示|继续聊聊|下次可以继续聊|如果你愿意[，,]?下次可以继续聊|如果你愿意[，,]?下一次我们可以继续聊)[：:]?\s*(.+)?$/)
    if (inlineSectionTitle) {
      const sectionTitle = normalizeSpecialSectionTitle(inlineSectionTitle[1])
      const sectionBody = String(inlineSectionTitle[2] || '').trim()
      if (sectionTitle === '核心内容') {
        pushCurrentSection()
        if (sectionBody) {
          leadParagraphs.push(sectionBody)
        }
        continue
      }
      pushCurrentSection()
      currentSection = { title: sectionTitle, items: [] }
      if (sectionBody) {
        currentSection.items.push(...splitAnswerItemLine(sectionBody))
      }
      continue
    }
    if (line === '核心内容') {
      pushCurrentSection()
      continue
    }
    if (isAdviceSectionTitle(line) || isFollowupSectionTitle(line)) {
      pushCurrentSection()
      currentSection = { title: normalizeSpecialSectionTitle(line), items: [] }
      continue
    }
    if (currentSection && (isAdviceSectionTitle(currentSection.title) || isFollowupSectionTitle(currentSection.title)) && /^\d+\.\s*/.test(line)) {
      currentSection.items.push(...splitAnswerItemLine(line))
      continue
    }
    if (/^\d+\.\s*.+/.test(line)) {
      pushCurrentSection()
      currentSection = {
        title: normalizeAnswerSectionTitle(line),
        items: [],
      }
      continue
    }
    if (currentSection) {
      currentSection.items.push(...splitAnswerItemLine(line))
      continue
    }
    leadParagraphs.push(line)
  }
  pushCurrentSection()

  if (!summary && leadParagraphs.length) {
    summary = leadParagraphs.shift()
  }
  if (!summary && !sections.length) {
    return null
  }
  return {
    summary,
    leadParagraphs,
    sections,
  }
}

function renderAnswerItem(item) {
  const text = String(item || '').trim()
  if (!text) return ''
  const ordered = text.match(/^(\d+)\.\s*(.+)$/)
  if (ordered) {
    return `<span class="answer-point-order">${ordered[1]}</span><span class="answer-point-text">${renderInline(ordered[2])}</span>`
  }
  const matched = text.match(/^(重点|关键信息|这是什么|为什么重要|建议动作|建议|风险提示|适用场景|可以怎么做|先做什么)[：:]\s*(.+)$/)
  if (!matched) {
    return renderInline(text)
  }
  return `<span class="answer-point-label">${matched[1]}</span><span class="answer-point-text">${renderInline(matched[2])}</span>`
}

function isMeaningfulAnswerNode(node) {
  if (!node) return false
  if (node.nodeType === 1) return true
  if (node.nodeType !== 3) return false
  return String(node.textContent || '').trim().length > 0
}

function isAnswerHeadingNode(node) {
  if (!node || node.nodeType !== 1) return false
  const tagName = String(node.tagName || '').toUpperCase()
  return tagName === 'H1' || tagName === 'H2' || tagName === 'H3'
}

function buildAnswerSegments(nodes) {
  const segments = []
  const hasHeading = nodes.some(node => isAnswerHeadingNode(node))
  if (!hasHeading) {
    nodes.forEach(node => {
      segments.push([node.cloneNode(true)])
    })
    return segments
  }
  let current = []
  nodes.forEach(node => {
    if (isAnswerHeadingNode(node) && current.length) {
      segments.push(current)
      current = []
    }
    current.push(node.cloneNode(true))
  })
  if (current.length) {
    segments.push(current)
  }
  return segments
}

function decorateAnswerSegments(html) {
  const source = String(html || '').trim()
  if (!source || typeof document === 'undefined') return source
  const container = document.createElement('div')
  container.innerHTML = source
  const nodes = Array.from(container.childNodes).filter(isMeaningfulAnswerNode)
  if (nodes.length <= 1) return source
  const segments = buildAnswerSegments(nodes)
  if (segments.length <= 1) return source
  const decorated = document.createElement('div')
  decorated.className = 'answer-segment-stack'
  segments.forEach((segment, index) => {
    const section = document.createElement('section')
    section.className = 'answer-segment'
    segment.forEach(node => section.appendChild(node))
    decorated.appendChild(section)
    if (index < segments.length - 1) {
      const divider = document.createElement('div')
      divider.className = 'answer-segment-divider'
      divider.setAttribute('aria-hidden', 'true')
      divider.innerHTML = '<span class="answer-segment-divider-line"></span><span class="answer-segment-divider-dot"></span><span class="answer-segment-divider-line"></span>'
      decorated.appendChild(divider)
    }
  })
  return decorated.innerHTML
}

function renderAnswerContent(md) {
  const readableText = normalizeReadableText(md)
  const markdownSource = prepareAnswerMarkdownSource(readableText)
  if (!markdownSource) {
    return ''
  }
  const rendered = marked.parse(markdownSource, ANSWER_MARKED_RENDER_OPTIONS)
  const sanitizedHtml = sanitizeRenderedHtml(typeof rendered === 'string' ? rendered : '')
  return decorateAnswerSegments(sanitizedHtml)
}

function isEmotionAssistantMessage(msg) {
  const routeMode = String(msg?.routeMode || '').toLowerCase()
  return ['emotion', 'emotion_support', 'relationship_guidance', 'stress_relief'].includes(routeMode)
}

function findPreviousUserMessage(index) {
  for (let i = Number(index) - 1; i >= 0; i--) {
    const candidate = messages.value[i]
    if (candidate?.role === 'user') {
      return String(candidate.content || '')
    }
  }
  return ''
}

function closeEmotionReportDialog() {
  showEmotionReportDialog.value = false
}

function buildEmotionReportBodyText(report) {
  const source = report && typeof report === 'object' ? report : {}
  const keyPoints = Array.isArray(source.keyPoints) ? source.keyPoints : []
  const suggestions = Array.isArray(source.suggestions) ? source.suggestions : []
  const actions = Array.isArray(source.actions) ? source.actions : []
  return [
    `情绪概览\n${String(source.snapshot || '暂无概览内容')}`,
    `关注重点\n${keyPoints.map((item, index) => `${index + 1}. ${String(item || '')}`).join('\n')}`,
    `建议清单\n${suggestions.map((item, index) => `${index + 1}. ${String(item || '')}`).join('\n')}`,
    `行动建议\n${actions.map((item, index) => `${index + 1}. ${String(item || '')}`).join('\n')}`,
    source.closingMessage ? `收尾鼓励\n${String(source.closingMessage || '')}` : '',
  ].filter(Boolean).join('\n\n')
}

async function exportEmotionReportPdf() {
  if (!emotionReport.value) {
    return
  }
  try {
    await downloadPdfFile({
      fileName: `情感报告-${new Date().getTime()}.pdf`,
      title: String(emotionReport.value.title || '情感报告'),
      subtitle: `情感对话结构化报告 · 生成时间 ${emotionReport.value.generatedAt || new Date().toLocaleString('zh-CN', { hour12: false })}`,
      content: buildEmotionReportBodyText(emotionReport.value),
    })
  } catch (e) {
    showToast(extractBackendErrorMessage(e, 'PDF 导出失败'))
  }
}

async function exportAssistantAnswerPdf(index) {
  const msg = messages.value[index]
  if (!msg || !msg.finalContent) {
    return
  }
  const routeText = getRouteTagText(msg.routeMode) || '统一助手'
  try {
    await downloadPdfFile({
      fileName: `${routeText}答复记录-${new Date().getTime()}.pdf`,
      title: `${routeText}答复记录`,
      subtitle: `会话 ID：${String(activeConversationId.value || chatId.value || '')} · 导出自灵伴对话工作台`,
      content: String(msg.finalContent || ''),
    })
  } catch (e) {
    showToast(extractBackendErrorMessage(e, 'PDF 导出失败'))
  }
}

async function openEmotionReportForMessage(index) {
  const msg = messages.value[index]
  if (!msg || !isEmotionAssistantMessage(msg) || !msg.finalContent) {
    return
  }
  generatingEmotionReport.value = true
  emotionReportMessageIndex.value = index
  emotionReport.value = null
  showEmotionReportDialog.value = true
  try {
    const report = await generateEmotionReport({
      conversationId: String(activeConversationId.value || chatId.value || ''),
      userMessage: findPreviousUserMessage(index),
      assistantMessage: String(msg.finalContent || ''),
    })
    emotionReport.value = report
  } catch (e) {
    emotionReport.value = null
    showToast(extractBackendErrorMessage(e, '生成情感报告失败'))
  } finally {
    generatingEmotionReport.value = false
  }
}

function buildAssistantFeedbackKey(index, msg) {
  const conversationId = String(activeConversationId.value || chatId.value || 'default')
  const requestId = String(msg?.requestId || '')
  const contentAnchor = String(msg?.finalContent || '').slice(0, 64)
  return `${conversationId}::${index}::${requestId || contentAnchor}`
}

function getAssistantFeedbackState(index, msg) {
  const key = buildAssistantFeedbackKey(index, msg)
  const raw = messageFeedbackMap.value[key]
  if (!raw || typeof raw !== 'object') {
    return { liked: false, disliked: false }
  }
  return {
    liked: !!raw.liked,
    disliked: !!raw.disliked,
  }
}

function isAssistantLiked(index) {
  return getAssistantFeedbackState(index, messages.value[index]).liked
}

function isAssistantDisliked(index) {
  return getAssistantFeedbackState(index, messages.value[index]).disliked
}

function toggleAssistantLike(index) {
  const msg = messages.value[index]
  if (!msg || !msg.finalContent) {
    return
  }
  const key = buildAssistantFeedbackKey(index, msg)
  const current = getAssistantFeedbackState(index, msg)
  const nextLiked = !current.liked
  messageFeedbackMap.value = {
    ...messageFeedbackMap.value,
    [key]: {
      liked: nextLiked,
      disliked: nextLiked ? false : current.disliked,
    },
  }
  persistMessageFeedback()
  showToast(nextLiked ? '感谢反馈，已点赞' : '已取消点赞')
}

function toggleAssistantDislike(index) {
  const msg = messages.value[index]
  if (!msg || !msg.finalContent) {
    return
  }
  const key = buildAssistantFeedbackKey(index, msg)
  const current = getAssistantFeedbackState(index, msg)
  const nextDisliked = !current.disliked
  messageFeedbackMap.value = {
    ...messageFeedbackMap.value,
    [key]: {
      liked: nextDisliked ? false : current.liked,
      disliked: nextDisliked,
    },
  }
  persistMessageFeedback()
  showToast(nextDisliked ? '已收到你的改进反馈' : '已取消点踩')
}

async function copyText(text) {
  const content = String(text || '')
  if (!content) {
    return false
  }
  if (navigator?.clipboard?.writeText) {
    try {
      await navigator.clipboard.writeText(content)
      return true
    } catch (e) {
    }
  }
  try {
    const textarea = document.createElement('textarea')
    textarea.value = content
    textarea.style.position = 'fixed'
    textarea.style.top = '-9999px'
    textarea.style.opacity = '0'
    document.body.appendChild(textarea)
    textarea.focus()
    textarea.select()
    const copied = document.execCommand('copy')
    document.body.removeChild(textarea)
    return !!copied
  } catch (e) {
    return false
  }
}

async function copyAssistantAnswer(index) {
  const msg = messages.value[index]
  if (!msg || !msg.finalContent) {
    return
  }
  const copied = await copyText(msg.finalContent)
  showToast(copied ? '已复制答复内容' : '复制失败，请手动复制')
}

function formatSummaryTime(value) {
  if (!value) return ''
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return ''
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  const hour = String(date.getHours()).padStart(2, '0')
  const minute = String(date.getMinutes()).padStart(2, '0')
  return `${month}-${day} ${hour}:${minute}`
}

function trimText(value, max = 72) {
  const text = String(value || '').replace(/\s+/g, ' ').trim()
  if (!text) return ''
  if (text.length <= max) return text
  return text.slice(0, max) + '...'
}

function normalizeHistoryKeyword(value) {
  return String(value || '').replace(/\s+/g, ' ').trim()
}

function normalizeConversationSummaryItem(item) {
  return {
    conversationId: String(item?.conversationId || ''),
    title: String(item?.title || '新对话'),
    tag: normalizeRouteMode(item?.tag || item?.mode || ''),
    summary: trimText(item?.summary || '', 84),
    lastMessage: trimText(item?.lastMessage || '', 84),
    lastTime: formatSummaryTime(item?.lastTime),
    lastTimeValue: toTimestamp(item?.lastTime),
    messageCount: Number(item?.messageCount || 0),
    pinned: !!item?.pinned,
  }
}

function sortConversationSummaries(list) {
  return [...(list || [])].sort((left, right) => {
    const pinnedDiff = Number(!!right?.pinned) - Number(!!left?.pinned)
    if (pinnedDiff !== 0) {
      return pinnedDiff
    }
    return Number(right?.lastTimeValue || 0) - Number(left?.lastTimeValue || 0)
  })
}

function clearHistorySearchTask() {
  if (historySearchTimer) {
    clearTimeout(historySearchTimer)
    historySearchTimer = null
  }
}

function scheduleHistorySearch() {
  clearHistorySearchTask()
  if (!loggedIn.value) {
    return
  }
  historySearchTimer = setTimeout(() => {
    fetchConversationSummaries()
  }, 260)
}

function getConversationLocalTitle(conversationId, fallbackMessages = messages.value) {
  const normalizedConversationId = String(conversationId || activeConversationId.value || chatId.value || '')
  if (normalizedConversationId) {
    const currentHistory = history.value.find(item => String(item?.conversationId || '') === normalizedConversationId)
    if (currentHistory?.title) {
      return trimText(String(currentHistory.title), 18)
    }
  }
  return '新对话'
}

function formatNowTime() {
  return formatSummaryTime(new Date())
}

function toTimestamp(value) {
  if (!value) return 0
  const time = new Date(value).getTime()
  return Number.isNaN(time) ? 0 : time
}

function parseMetadata(raw) {
  if (!raw) return {}
  if (typeof raw === 'object') return raw
  try {
    return JSON.parse(String(raw))
  } catch (e) {
    return {}
  }
}

function resolveWebSearchEnabled(metadata) {
  if (metadata && typeof metadata.webSearchEnabled === 'boolean') {
    return metadata.webSearchEnabled
  }
  if (metadata && typeof metadata.allowWebSearch === 'boolean') {
    return metadata.allowWebSearch
  }
  return false
}

function resolveKnowledgeBaseEnabled(metadata) {
  if (metadata && typeof metadata.knowledgeBaseEnabled === 'boolean') {
    return metadata.knowledgeBaseEnabled
  }
  if (metadata && typeof metadata.allowKnowledgeBase === 'boolean') {
    return metadata.allowKnowledgeBase
  }
  return false
}

function buildProcessThinkingFallback(routeMode, webSearchEnabled, knowledgeBaseEnabled, hasResultContent) {
  const normalizedMode = String(routeMode || '').toLowerCase()
  if (['emotion', 'emotion_support', 'relationship_guidance', 'stress_relief'].includes(normalizedMode)) {
    if (knowledgeBaseEnabled || hasResultContent) {
      return '我先看看相关资料里有没有更贴近你这类处境的内容，再陪你把最在意的部分慢慢理清。'
    }
    return '我先接住你现在的感受，再陪你把最在意的部分慢慢理清。'
  }
  if (normalizedMode === 'manus') {
    return '我先把你的目标拆成几个清晰步骤，再一步步帮你推进。'
  }
  if (webSearchEnabled || hasResultContent) {
    return '我先补充一下相关资料，再把重点整理成清晰可用的答复。'
  }
  if (knowledgeBaseEnabled) {
    return '我先看看知识库里有没有能直接帮上你的内容，再把重点整理清楚。'
  }
  if (['agent', 'task_planning', 'information_lookup', 'content_organizing', 'general_assistance'].includes(normalizedMode)) {
    return '我先把你的问题拆开，抓住重点后再继续回答你。'
  }
  return ''
}

function resolvePersistedThinkingContent(metadata, routeMode, webSearchEnabled, knowledgeBaseEnabled, resultContent) {
  const thinkingContent = String(metadata?.thinkingContent || '').trim()
  if (thinkingContent) {
    return thinkingContent
  }
  const hasResultContent = String(resultContent || '').trim().length > 0
  return buildProcessThinkingFallback(routeMode, webSearchEnabled, knowledgeBaseEnabled, hasResultContent)
}

function recoverOriginalUserMessage(rawContent) {
  const text = String(rawContent || '')
  if (!text.startsWith('用户原始需求：')) {
    return text
  }
  const matched = text.match(/^用户原始需求：([\s\S]*?)(?:\n当前已开启联网搜索|\n当前可用文件：|$)/)
  if (!matched) {
    return text
  }
  return String(matched[1] || '').trim() || text
}

function getRouteTagText(routeMode) {
  const mode = String(routeMode || '').toLowerCase()
  if (mode === 'emotion_support') return '情绪支持'
  if (mode === 'relationship_guidance') return '关系沟通'
  if (mode === 'stress_relief') return '压力疏导'
  if (mode === 'task_planning') return '计划安排'
  if (mode === 'information_lookup') return '信息检索'
  if (mode === 'content_organizing') return '内容整理'
  if (mode === 'general_assistance') return '综合协助'
  if (mode === 'emotion') return '情绪支持'
  if (mode === 'agent') return '任务协作'
  return ''
}

function normalizeRouteMode(mode, label = '') {
  const normalizedMode = String(mode || '').toLowerCase()
  if ([
    'emotion',
    'agent',
    'emotion_support',
    'relationship_guidance',
    'stress_relief',
    'task_planning',
    'information_lookup',
    'content_organizing',
    'general_assistance',
  ].includes(normalizedMode)) {
    return normalizedMode
  }
  const normalizedLabel = String(label || '')
  if (normalizedLabel.includes('情感')) return 'emotion_support'
  if (normalizedLabel.includes('任务')) return 'agent'
  return ''
}

function getRouteTagClass(routeMode) {
  const mode = String(routeMode || '').toLowerCase()
  if (['emotion', 'emotion_support', 'relationship_guidance', 'stress_relief'].includes(mode)) {
    return 'is-emotion'
  }
  if (['agent', 'task_planning', 'information_lookup', 'content_organizing', 'general_assistance'].includes(mode)) {
    return 'is-agent'
  }
  return ''
}

function extractBackendErrorMessage(error, fallback = '操作失败，请稍后重试') {
  if (!error) {
    return fallback
  }
  if (typeof error.backendMessage === 'string' && error.backendMessage.trim()) {
    return error.backendMessage.trim()
  }
  const responseData = error?.response?.data
  if (typeof responseData === 'string') {
    const text = responseData.trim()
    if (text) {
      try {
        const parsed = JSON.parse(text)
        if (parsed && typeof parsed === 'object' && typeof parsed.message === 'string' && parsed.message.trim()) {
          return parsed.message.trim()
        }
      } catch (e) {
      }
      return text
    }
  }
  if (responseData && typeof responseData === 'object') {
    if (typeof responseData.message === 'string' && responseData.message.trim()) {
      return responseData.message.trim()
    }
    if (typeof responseData.error === 'string' && responseData.error.trim()) {
      return responseData.error.trim()
    }
  }
  if (typeof error.message === 'string' && error.message.trim()) {
    return error.message.trim()
  }
  return fallback
}

function showToast(text) {
  toastText.value = String(text || '')
  toastVisible.value = true
  setTimeout(() => {
    toastVisible.value = false
  }, 1600)
}

function scrollToBottom() {
  nextTick(() => {
    const el = messagesPanelRef.value
    if (!el || !shouldStickMessagesToBottom.value) return
    el.scrollTop = el.scrollHeight
    scheduleProcessViewportSync()
  })
}

function scrollToBottomForced() {
  shouldStickMessagesToBottom.value = true
  nextTick(() => {
    const el = messagesPanelRef.value
    if (!el) return
    el.scrollTop = el.scrollHeight
    scheduleProcessViewportSync()
  })
}

function isMessagesPanelNearBottom() {
  const el = messagesPanelRef.value
  if (!el) return true
  const distance = el.scrollHeight - el.scrollTop - el.clientHeight
  return distance <= MESSAGES_AUTO_SCROLL_THRESHOLD
}

function setProcessPanelRef(index, el) {
  if (el) {
    processPanelRefs.set(Number(index), el)
    return
  }
  processPanelRefs.delete(Number(index))
}

function syncProcessPanelViewportState() {
  processViewportSyncTimer = null
  const panel = messagesPanelRef.value
  if (!panel) return
  const panelRect = panel.getBoundingClientRect()
  let changed = false
  processPanelRefs.forEach((el, index) => {
    const msg = messages.value[index]
    // 自动收起只在用户还没手动干预时生效，避免用户展开后又被立刻收回去。
    if (!msg || msg.role !== 'assistant' || msg.thinkingCollapsed || msg.processCollapseTouched) {
      return
    }
    const rect = el.getBoundingClientRect()
    if (rect.top < panelRect.top - PROCESS_COLLAPSE_TOP_THRESHOLD) {
      msg.thinkingCollapsed = true
      changed = true
    }
  })
  if (changed) {
    persistActiveConversationCache()
  }
}

function scheduleProcessViewportSync() {
  if (processViewportSyncTimer) {
    cancelAnimationFrame(processViewportSyncTimer)
  }
  processViewportSyncTimer = requestAnimationFrame(() => {
    syncProcessPanelViewportState()
  })
}

function handleMessagesScroll() {
  shouldStickMessagesToBottom.value = isMessagesPanelNearBottom()
  scheduleProcessViewportSync()
}

function flushStreamBuffer() {
  if (flushTimer) {
    clearTimeout(flushTimer)
    flushTimer = null
  }
  for (const [key, chunk] of streamBuf.entries()) {
    const [idxText, field] = String(key).split('::')
    const idx = Number(idxText)
    const msg = messages.value[idx]
    if (msg && chunk && field) {
      msg[field] = appendBlockText(msg[field], chunk)
    }
  }
  streamBuf.clear()
  persistActiveConversationCache()
  scrollToBottom()
}

function flushAnimatedStreamQueue(key) {
  const queue = animatedStreamQueues.get(key)
  if (queue && queue.length) {
    const [idxText, field] = String(key).split('::')
    const idx = Number(idxText)
    while (queue.length) {
      queueChunk(idx, queue.shift(), field)
    }
  }
  const timer = animatedStreamTimers.get(key)
  if (timer) {
    clearTimeout(timer)
    animatedStreamTimers.delete(key)
  }
  animatedStreamQueues.delete(key)
}

function flushAllAnimatedStreamQueues() {
  const keys = Array.from(animatedStreamQueues.keys())
  keys.forEach(key => flushAnimatedStreamQueue(key))
}

function appendBlockText(origin, chunk) {
  const current = String(origin || '')
  const next = String(chunk || '')
  if (!current) return next
  if (!next) return current
  return current + next
}

function getVisibleResultContent(msg) {
  if (!msg || (!msg.webSearchEnabled && !msg.knowledgeBaseEnabled)) {
    return ''
  }
  return String(msg.resultContent || '')
}

function stripSearchEvidenceMarkers(text) {
  return String(text || '')
      .replace(/\[\[SEARCH_META\]\][\s\S]*?\[\[\/SEARCH_META\]\]/g, '')
      .replace(/\[\[SEARCH_SOURCE\]\][\s\S]*?\[\[\/SEARCH_SOURCE\]\]/g, '')
      .replace(/\[\[KB_META\]\][\s\S]*?\[\[\/KB_META\]\]/g, '')
      .replace(/\[\[KB_SOURCE\]\][\s\S]*?\[\[\/KB_SOURCE\]\]/g, '')
      .trim()
}

function splitIncomingStreamContent(content, field) {
  const text = String(content || '')
  if (!text) return []
  const chunkSize = field === 'resultContent' ? 18 : 14
  const pieces = []
  const segments = text.split(/(?<=[。！？.!?；;：:\n])/)
  segments.forEach(segment => {
    const value = String(segment || '')
    if (!value) return
    let remaining = value
    while (remaining.length > chunkSize) {
      pieces.push(remaining.slice(0, chunkSize))
      remaining = remaining.slice(chunkSize)
    }
    if (remaining) {
      pieces.push(remaining)
    }
  })
  return pieces.length ? pieces : [text]
}

function drainAnimatedStreamQueue(key) {
  const queue = animatedStreamQueues.get(key)
  if (!queue || !queue.length) {
    animatedStreamQueues.delete(key)
    animatedStreamTimers.delete(key)
    return
  }
  const [idxText, field] = String(key).split('::')
  const idx = Number(idxText)
  queueChunk(idx, queue.shift(), field)
  if (!queue.length) {
    animatedStreamQueues.delete(key)
    animatedStreamTimers.delete(key)
    return
  }
  const timer = setTimeout(() => {
    drainAnimatedStreamQueue(key)
  }, 65)
  animatedStreamTimers.set(key, timer)
}

function queueAnimatedChunk(idx, chunk, field) {
  const pieces = splitIncomingStreamContent(chunk, field)
  if (!pieces.length) return
  const key = `${idx}::${field}`
  const queue = animatedStreamQueues.get(key) || []
  queue.push(...pieces)
  animatedStreamQueues.set(key, queue)
  if (animatedStreamTimers.has(key)) {
    return
  }
  drainAnimatedStreamQueue(key)
}

function getResultSourceCount(text) {
  const evidence = parseSearchEvidence(text)
  return evidence?.sources?.length || 0
}

function getProcessLineCount(thinkingContent, resultContent) {
  const text = `${String(thinkingContent || '')}\n${stripSearchEvidenceMarkers(String(resultContent || ''))}`
  return text.split(/\r?\n/).map(line => String(line || '').trim()).filter(Boolean).length
}

function shouldAutoCollapseProcess(thinkingContent, resultContent) {
  const text = `${String(thinkingContent || '')}\n${String(resultContent || '')}`.trim()
  if (!text) return false
  const sourceCount = getResultSourceCount(resultContent)
  const lineCount = getProcessLineCount(thinkingContent, resultContent)
  return text.length > PROCESS_AUTO_COLLAPSE_LENGTH
      || lineCount > PROCESS_AUTO_COLLAPSE_LINES
      || sourceCount > PROCESS_AUTO_COLLAPSE_SOURCES
}

function resolveProcessCollapsed(currentValue, thinkingContent, resultContent, touched = false) {
  if (touched && typeof currentValue === 'boolean') {
    return currentValue
  }
  return shouldAutoCollapseProcess(thinkingContent, resultContent)
}

function hasProcessPanel(msg) {
  return !!(msg && (msg.loading || String(msg.thinkingContent || '').trim() || getVisibleResultContent(msg).trim()))
}

function shouldShowSearchEvidence(msg) {
  return !!(msg && (msg.webSearchEnabled || msg.knowledgeBaseEnabled) && (getVisibleResultContent(msg).trim() || shouldShowSearchPlaceholder(msg)))
}

function shouldShowThinkingPlaceholder(msg) {
  return !!(msg && msg.loading && !String(msg.thinkingContent || '').trim())
}

function shouldShowSearchPlaceholder(msg) {
  if (!msg || (!msg.webSearchEnabled && !msg.knowledgeBaseEnabled) || !msg.loading || String(msg.resultContent || '').trim()) {
    return false
  }
  const thinkingText = String(msg.thinkingContent || '')
  return /联网搜索|搜索工具|外部资料|最新信息|核验事实|补充信息|知识库|内部资料|检索/.test(thinkingText)
}

function getThinkingPlaceholderText(msg) {
  if (!msg?.loading) {
    return ''
  }
  return '正在分析你的问题，过程信息会实时展示在这里。'
}

function getProcessPlaceholderText(msg) {
  if (shouldShowSearchPlaceholder(msg)) {
    if (msg?.webSearchEnabled && msg?.knowledgeBaseEnabled) {
      return String(msg?.thinkingContent || '').trim()
          ? '正在同时补充外部资料和知识库内容，命中的来源会继续展示在这里。'
          : '正在分析你的问题，并同步检索外部资料与知识库。'
    }
    if (msg?.knowledgeBaseEnabled) {
      return String(msg?.thinkingContent || '').trim()
          ? '正在继续检索知识库，命中的片段会接着展示在这里。'
          : '正在分析你的问题，并同步检索知识库。'
    }
    return String(msg?.thinkingContent || '').trim()
        ? '正在继续补充外部资料，命中的来源会接着展示在这里。'
        : '正在分析你的问题，并同步补充外部资料。'
  }
  return getThinkingPlaceholderText(msg)
}

function getElapsedSeconds(msg) {
  if (!msg) return 0
  const fixedSeconds = Number(msg.elapsedSeconds || 0)
  if (fixedSeconds > 0) {
    return fixedSeconds
  }
  const startedAt = Number(msg.startedAt || 0)
  if (!startedAt) {
    return 0
  }
  const endAt = Number(msg.completedAt || 0) || Number(nowTick.value || Date.now())
  if (endAt <= startedAt) {
    return 0
  }
  return Math.max(1, Math.ceil((endAt - startedAt) / 1000))
}

function formatElapsedText(seconds) {
  const value = Number(seconds || 0)
  if (value <= 0) {
    return ''
  }
  if (value < 60) {
    return `用时 ${value} 秒`
  }
  const minutes = Math.floor(value / 60)
  const remainSeconds = value % 60
  if (!remainSeconds) {
    return `用时 ${minutes} 分钟`
  }
  return `用时 ${minutes} 分 ${remainSeconds} 秒`
}

function getProcessElapsedLabel(msg) {
  const elapsedText = formatElapsedText(getElapsedSeconds(msg))
  if (!elapsedText) {
    return ''
  }
  if (msg?.loading && !msg?.complete) {
    return `已用时 ${elapsedText.replace(/^用时\s*/, '')}`
  }
  return `已完成，${elapsedText}`
}

function getProcessStatusLabel(msg) {
  if (msg?.loading && !msg?.complete) {
    return '思考中'
  }
  return '思考完成'
}

function shouldShowProcessSummary(msg) {
  return !!(msg && !msg.thinkingCollapsed && getProcessSummary(msg))
}

function getProcessSummary(msg) {
  if (!msg) return ''
  const summaryParts = []
  const visibleResultContent = getVisibleResultContent(msg)
  const sourceCount = getResultSourceCount(visibleResultContent)
  const lineCount = getProcessLineCount(msg.thinkingContent, visibleResultContent)
  if (msg.loading && !String(msg.thinkingContent || '').trim() && !visibleResultContent.trim()) {
    return '思考过程会随着回答逐步展开'
  }
  if (sourceCount > 0) {
    summaryParts.push(`已整理 ${sourceCount} 条资料来源`)
    if (lineCount > 0) {
      summaryParts.push(`共 ${lineCount} 行思考内容`)
    }
    return summaryParts.join(' · ')
  }
  if (String(msg.thinkingContent || '').trim()) {
    summaryParts.push(`共 ${lineCount} 行思考内容`)
    if (msg.loading) {
      summaryParts.push('仍在继续补充')
    }
    return summaryParts.join(' · ')
  }
  return summaryParts.join(' · ')
}

function renderProcessContent(msg) {
  if (!msg) {
    return ''
  }
  const parts = []
  if (String(msg.thinkingContent || '').trim()) {
    parts.push(`<div class="process-stream-part">${renderMarkdown(msg.thinkingContent, 'thought')}</div>`)
  }
  const visibleResultContent = getVisibleResultContent(msg)
  if (visibleResultContent.trim()) {
    parts.push(`<div class="process-stream-part tool-result-text">${renderSearchEvidenceContent(visibleResultContent)}</div>`)
  }
  if (!parts.length && msg.loading) {
    return `<div class="process-placeholder">${escapeHtml(getProcessPlaceholderText(msg))}</div>`
  }
  if ((shouldShowSearchPlaceholder(msg) || shouldShowThinkingPlaceholder(msg)) && parts.length) {
    parts.push(`<div class="process-stream-tip">${escapeHtml(getProcessPlaceholderText(msg))}</div>`)
  }
  return parts.join('')
}

function parseSearchEvidence(text) {
  const normalized = normalizeReadableText(text)
  if (!normalized) {
    return null
  }
  const structuredEvidence = parseStructuredSearchEvidence(normalized)
  if (structuredEvidence) {
    return structuredEvidence
  }
  if (!normalized.includes('搜索词：') && !normalized.includes('结果 1：')) {
    return null
  }
  const evidence = {
    searchQuery: '',
    provider: '',
    summary: '',
    sources: [],
  }
  const queryMatch = normalized.match(/(?:^|\n)搜索词：([^\n]+)/)
  if (queryMatch) {
    evidence.searchQuery = String(queryMatch[1] || '').trim()
  }
  const providerMatch = normalized.match(/(?:^|\n)搜索提供方：([^\n]+)/)
  if (providerMatch) {
    evidence.provider = String(providerMatch[1] || '').trim()
  }
  const cleaned = normalized.replace(/\n###\s*工具\d+：[\s\S]*$/g, '')
  const sourceBlockPattern = /(?:^|\n)结果\s+\d+：\s*([\s\S]*?)(?=(?:\n结果\s+\d+：|$))/g
  let matched
  while ((matched = sourceBlockPattern.exec(cleaned)) !== null) {
    const block = String(matched[1] || '').trim()
    if (!block) continue
    const titleMatch = block.match(/(?:^|\n)标题：([^\n]+)/)
    const urlMatch = block.match(/(?:^|\n)链接：([^\n]+)/)
    const snippetMatch = block.match(/(?:^|\n)摘要：([\s\S]*)/)
    const source = normalizeSearchEvidenceSource({
      title: String(titleMatch?.[1] || '').trim(),
      url: String(urlMatch?.[1] || '').trim(),
      snippet: String(snippetMatch?.[1] || '').trim(),
    })
    if (source) {
      evidence.sources.push(source)
    }
  }
  if (!evidence.sources.length && cleaned.includes('结果 1：')) {
    const segments = cleaned.split(/\n(?=结果\s+\d+：)/).map(item => String(item || '').trim()).filter(Boolean)
    segments.forEach(segment => {
      if (!/^结果\s+\d+：/.test(segment)) {
        return
      }
      const lines = segment.split(/\r?\n/).map(line => String(line || '').trim()).filter(Boolean)
      const source = { title: '', url: '', snippet: '' }
      lines.forEach(line => {
        if (line.startsWith('标题：')) {
          source.title = line.replace('标题：', '').trim()
        } else if (line.startsWith('链接：')) {
          source.url = line.replace('链接：', '').trim()
        } else if (line.startsWith('摘要：')) {
          source.snippet = line.replace('摘要：', '').trim()
        } else if (source.snippet) {
          source.snippet = `${source.snippet} ${line}`.trim()
        }
      })
      const normalizedSource = normalizeSearchEvidenceSource(source)
      if (normalizedSource) {
        evidence.sources.push(normalizedSource)
      }
    })
  }
  if (!evidence.searchQuery && !evidence.provider && !evidence.sources.length) {
    return null
  }
  return evidence
}

function isFailedSearchSourceText(value) {
  const text = String(value || '').replace(/\s+/g, ' ').trim()
  if (!text) {
    return false
  }
  const lowerText = text.toLowerCase()
  return text.startsWith('搜索失败')
      || text.startsWith('请求失败')
      || text.startsWith('接口调用异常')
      || text.startsWith('无结果或失败')
      || text.startsWith('未返回可用内容')
      || text.startsWith('未配置')
      || text.startsWith('跳过：')
      || text.startsWith('跳过:')
      || text.startsWith('解析失败')
      || text.startsWith('调用失败')
      || lowerText.startsWith('error:')
      || lowerText.startsWith('request failed')
      || lowerText.startsWith('search failed')
}

function normalizeSearchEvidenceSource(source) {
  const item = {
    title: String(source?.title || '').trim(),
    url: String(source?.url || '').trim(),
    snippet: String(source?.snippet || source?.summary || '').trim(),
  }
  if (!item.title && !item.url && !item.snippet) {
    return null
  }
  if (isFailedSearchSourceText(item.title) || isFailedSearchSourceText(item.snippet)) {
    return null
  }
  return item
}

function extractStructuredEvidencePayloads(text, markerName) {
  const pattern = new RegExp(`\\[\\[${markerName}\\]\\]([\\s\\S]*?)\\[\\[\\/${markerName}\\]\\]`, 'g')
  return Array.from(String(text || '').matchAll(pattern)).map(match => String(match?.[1] || ''))
}

function parseStructuredSearchEvidence(text) {
  const metaPayloads = [
    ...extractStructuredEvidencePayloads(text, 'SEARCH_META'),
    ...extractStructuredEvidencePayloads(text, 'KB_META'),
  ]
  const sourcePayloads = [
    ...extractStructuredEvidencePayloads(text, 'SEARCH_SOURCE'),
    ...extractStructuredEvidencePayloads(text, 'KB_SOURCE'),
  ]
  if (!metaPayloads.length && !sourcePayloads.length) {
    return null
  }
  const evidence = {
    searchQuery: '',
    provider: '',
    summary: '',
    sources: [],
  }
  metaPayloads.forEach(payload => {
    try {
      const meta = JSON.parse(payload)
      if (!evidence.searchQuery) {
        evidence.searchQuery = String(meta?.query || meta?.actualQuery || '').trim()
      }
      if (!evidence.provider) {
        evidence.provider = String(meta?.provider || '').trim()
      }
      if (!evidence.summary) {
        evidence.summary = String(meta?.summary || '').trim()
      }
    } catch (e) {
    }
  })
  sourcePayloads.forEach(payload => {
    try {
      const source = JSON.parse(String(payload || '{}'))
      const item = normalizeSearchEvidenceSource({
        title: String(source?.title || '').trim(),
        url: String(source?.url || '').trim(),
        snippet: String(source?.summary || source?.snippet || '').trim(),
      })
      if (item) {
        evidence.sources.push(item)
      }
    } catch (e) {
    }
  })
  if (!evidence.searchQuery && !evidence.provider && !evidence.summary && !evidence.sources.length) {
    return null
  }
  return evidence
}

function getSourceHostLabel(url) {
  const value = String(url || '').trim()
  if (!value) return ''
  try {
    const normalizedUrl = /^https?:\/\//i.test(value) ? value : `https://${value}`
    return new URL(normalizedUrl).host.replace(/^www\./i, '')
  } catch (e) {
    return value.replace(/^https?:\/\//i, '').split('/')[0]
  }
}

function formatSourceSnippetHtml(snippet) {
  const normalized = normalizeReadableText(snippet)
  if (!normalized) return ''
  const seedSegments = normalized
      .split(/\r?\n|(?<=[。！？!?；;])/)
      .map(item => String(item || '').trim())
      .filter(Boolean)
      .map(item => item.replace(/^[-*•]\s*/, '').replace(/^\d+\.\s*/, '').trim())
      .filter(Boolean)
  const segments = seedSegments
  const merged = []
  segments.forEach(segment => {
    const current = merged[merged.length - 1]
    if (!current) {
      merged.push(segment)
      return
    }
    if (current.length < 22 && segment.length < 22 && merged.length < 4) {
      merged[merged.length - 1] = `${current} ${segment}`.trim()
      return
    }
    merged.push(segment)
  })
  const previewItems = (merged.length ? merged : [normalized]).slice(0, 3)
  return '<ul class="search-source-snippet-list">' + previewItems
      .map(item => `<li>${renderInline(item)}</li>`)
      .join('') + '</ul>'
}

function renderSearchEvidenceContent(text) {
  const evidence = parseSearchEvidence(text)
  if (!evidence) {
    return renderMarkdown(text, 'tool')
  }
  let html = '<div class="search-evidence">'
  if (evidence.provider) {
    html += '<div class="search-evidence-meta">'
    if (evidence.provider) {
      html += `<span class="search-evidence-chip">${renderInline(`来源：${evidence.provider}`)}</span>`
    }
    html += '</div>'
  }
  if (evidence.summary) {
    html += `<div class="search-evidence-summary">${renderMarkdown(evidence.summary, 'tool')}</div>`
  }
  if (evidence.sources.length) {
    html += '<div class="search-evidence-title">来源列表</div>'
    html += '<div class="search-evidence-sources">'
    html += evidence.sources.map((source, index) => {
      const title = renderInline(source.title || `来源 ${index + 1}`)
      const host = getSourceHostLabel(source.url)
      const hostBadge = host ? `<span class="search-source-host">${renderInline(host)}</span>` : ''
      const snippet = source.snippet
          ? `<div class="search-source-snippet">${formatSourceSnippetHtml(source.snippet)}</div>`
          : ''
      const link = source.url
          ? `<a class="search-source-link" href="${escapeHtml(source.url)}" target="_blank" rel="noopener noreferrer">查看来源</a>`
          : ''
      return `<article class="search-source-item"><div class="search-source-index">${index + 1}</div><div class="search-source-body"><div class="search-source-head"><div class="search-source-title">${title}</div>${hostBadge}</div>${snippet}<div class="search-source-actions">${link}</div></div></article>`
    }).join('')
    html += '</div>'
  }
  html += '</div>'
  return html
}

function queueChunk(idx, chunk, field) {
  const key = `${idx}::${field}`
  const prev = streamBuf.get(key) || ''
  streamBuf.set(key, prev + chunk)
  if (!flushTimer) {
    flushTimer = setTimeout(() => {
      flushStreamBuffer()
    }, 50)
  }
}

function findLatestGeneratingAssistantIndex() {
  for (let i = messages.value.length - 1; i >= 0; i--) {
    const msg = messages.value[i]
    if (msg?.role === 'assistant' && msg?.loading && !msg?.complete) {
      return i
    }
  }
  return -1
}

function stopCurrentGeneration() {
  const idx = activeStreamMessageIndex.value >= 0 ? activeStreamMessageIndex.value : findLatestGeneratingAssistantIndex()
  if (idx < 0) {
    return
  }
  const stream = currentStream
  currentStream = null
  activeStreamMessageIndex.value = -1
  stream?.close?.()
  finishAssistantMessage(idx, '已停止生成')
  showToast('已停止生成')
}

function handleAssistantStreamError(idx, error) {
  const msg = messages.value[idx]
  if (msg?.complete) {
    return
  }
  const message = extractBackendErrorMessage(error, '请求中断，请检查网络或登录状态后重试')
  finishAssistantMessage(idx, message)
  showToast(message)
}

function finishAssistantMessage(idx, fallbackText = '') {
  flushAllAnimatedStreamQueues()
  flushStreamBuffer()
  const msg = messages.value[idx]
  if (!msg || msg.complete) return
  if (!msg.finalContent && fallbackText) {
    msg.finalContent = fallbackText
  }
  if (!msg.completedAt) {
    msg.completedAt = Date.now()
  }
  if (!msg.elapsedSeconds) {
    msg.elapsedSeconds = getElapsedSeconds(msg)
  }
  if (!msg.processCollapseTouched) {
    msg.thinkingCollapsed = shouldAutoCollapseProcess(msg.thinkingContent, getVisibleResultContent(msg))
  }
  msg.loading = false
  msg.complete = true
  if (activeStreamMessageIndex.value === idx) {
    activeStreamMessageIndex.value = -1
    currentStream = null
  }
  persistActiveConversationCache()
  syncActiveConversationSummary(msg)
  fetchConversationSummaries()
  setTimeout(() => {
    fetchConversationSummaries()
  }, 600)
}

function syncActiveConversationSummary(msg) {
  const conversationId = String(activeConversationId.value || chatId.value || '')
  if (!conversationId) return
  const title = getConversationLocalTitle(conversationId, messages.value)
  const tag = normalizeRouteMode(msg?.routeMode || '')
  const previewText = trimText(msg?.finalContent || msg?.content || '', 84)
  const nextItem = {
    conversationId,
    title,
    tag,
    summary: previewText,
    lastMessage: previewText,
    lastTime: formatNowTime(),
    lastTimeValue: Date.now(),
    messageCount: 0,
    pinned: false,
  }
  const rest = history.value.filter(item => String(item?.conversationId || '') !== conversationId)
  const current = history.value.find(item => String(item?.conversationId || '') === conversationId)
  if (current) {
    nextItem.title = current.title || nextItem.title
    nextItem.summary = previewText || current.summary || ''
    nextItem.lastMessage = previewText || current.lastMessage || ''
    nextItem.messageCount = Math.max(Number(current.messageCount || 0) + 2, 2)
    nextItem.pinned = !!current.pinned
  } else {
    nextItem.messageCount = 2
  }
  history.value = sortConversationSummaries([nextItem, ...rest])
}

function handleUnifiedEvent(payload, idx) {
  const msg = messages.value[idx]
  if (!msg) return
  if (!payload || typeof payload !== 'object') {
    queueChunk(idx, String(payload || ''), 'finalContent')
    return
  }
  const eventSeq = Number(payload.eventSeq || 0)
  if (payload.resumeRestarted && Number(msg.lastEventSeq || 0) > 0) {
    resetAssistantStreamState(msg)
  }
  if (eventSeq > 0 && Number(msg.lastEventSeq || 0) >= eventSeq) {
    return
  }
  if (payload.requestId && !msg.requestId) {
    msg.requestId = String(payload.requestId)
  }
  if (payload.type === 'route') {
    msg.routeMode = normalizeRouteMode(payload.tag || payload.mode || msg.routeMode || '', payload.label)
    msg.reason = String(payload.reason || '')
    if (eventSeq > 0) {
      msg.lastEventSeq = eventSeq
    }
    return
  }
  if (payload.type === 'thinking') {
    msg.routeMode = normalizeRouteMode(payload.tag || payload.mode || msg.routeMode || '', payload.label)
    if (!msg.reason && payload.reason) {
      msg.reason = String(payload.reason)
    }
    if (eventSeq > 0) {
      msg.lastEventSeq = eventSeq
    }
    queueAnimatedChunk(idx, String(payload.content || ''), 'thinkingContent')
    return
  }
  if (payload.type === 'result') {
    if (eventSeq > 0) {
      msg.lastEventSeq = eventSeq
    }
    queueAnimatedChunk(idx, String(payload.content || ''), 'resultContent')
    return
  }
  if (payload.type === 'final') {
    msg.routeMode = normalizeRouteMode(payload.tag || payload.mode || msg.routeMode || '', payload.label)
    if (!msg.reason && payload.reason) {
      msg.reason = String(payload.reason)
    }
    if (eventSeq > 0) {
      msg.lastEventSeq = eventSeq
    }
    queueChunk(idx, String(payload.content || ''), 'finalContent')
    return
  }
  if (payload.type === 'error') {
    msg.routeMode = normalizeRouteMode(payload.tag || payload.mode || msg.routeMode || '', payload.label)
    if (eventSeq > 0) {
      msg.lastEventSeq = eventSeq
    }
    const errorText = String(payload.content || '').trim()
    queueChunk(idx, errorText, 'finalContent')
    finishAssistantMessage(idx)
    if (errorText) {
      showToast(errorText)
    }
    return
  }
  if (payload.type === 'done') {
    if (eventSeq > 0) {
      msg.lastEventSeq = eventSeq
    }
    finishAssistantMessage(idx)
  }
}

function toggleThinking(index) {
  const msg = messages.value[index]
  if (!msg) return
  msg.thinkingCollapsed = !msg.thinkingCollapsed
  msg.processCollapseTouched = true
  persistActiveConversationCache()
  scheduleProcessViewportSync()
}

async function handleFileUpload(e) {
  const file = e?.target?.files?.[0]
  if (!file || uploadingFile.value) return
  const formData = new FormData()
  formData.append('file', file)
  uploadingFile.value = true
  try {
    const res = await axios.post('/api/ai/assistant/file/upload', formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
    })
    const fileName = String(res?.data || '')
    if (fileName) {
      uploadedFiles.value = Array.from(new Set([...uploadedFiles.value, fileName]))
      showToast('文件上传成功')
    } else {
      showToast('文件上传失败')
    }
  } catch (error) {
    showToast(extractBackendErrorMessage(error, '文件上传失败'))
  } finally {
    uploadingFile.value = false
    if (e?.target) e.target.value = ''
  }
}

function removeUploadedFile(fileName) {
  uploadedFiles.value = uploadedFiles.value.filter(item => item !== fileName)
}

function send() {
  if (isGeneratingReply.value) {
    stopCurrentGeneration()
    return
  }
  const text = input.value.trim()
  if (!text) return
  const sendingFiles = [...uploadedFiles.value]
  const sendingAllowWebSearch = allowWebSearch.value
  const sendingAllowKnowledgeBase = allowKnowledgeBase.value
  const requestId = genChatId()
  let uid = ''
  try {
    uid = localStorage.getItem('user_id') || ''
  } catch (e) {
    uid = ''
  }
  if (!uid) {
    showLoginModal.value = true
    return
  }
  if (!chatId.value) {
    chatId.value = genChatId()
  }
  activeConversationId.value = chatId.value
  messages.value.push({
    role: 'user',
    content: text,
    attachedFiles: sendingFiles,
  })
  messages.value.push({
    role: 'assistant',
    content: '',
    thinkingContent: '',
    resultContent: '',
    finalContent: '',
    webSearchEnabled: sendingAllowWebSearch,
    knowledgeBaseEnabled: sendingAllowKnowledgeBase,
    sourcePrompt: text,
    loading: true,
    complete: false,
    thinkingCollapsed: false,
    processCollapseTouched: false,
    resultCollapsed: true,
    startedAt: Date.now(),
    completedAt: 0,
    elapsedSeconds: 0,
    routeMode: UNIFIED_MODE,
    reason: '',
    requestId,
    lastEventSeq: 0,
  })
  persistActiveConversationCache()
  const idx = messages.value.length - 1
  activeStreamMessageIndex.value = idx
  input.value = ''
  resizeTextarea()
  currentStream?.close()
  currentStream = openUnifiedSSE(
      text,
      chatId.value,
      uid,
      UNIFIED_MODE,
      sendingFiles.length > 0,
      sendingAllowWebSearch,
      sendingAllowKnowledgeBase,
      sendingFiles,
      requestId,
      false,
      (payload) => handleUnifiedEvent(payload, idx),
      () => finishAssistantMessage(idx),
      (error) => handleAssistantStreamError(idx, error)
  )
  uploadedFiles.value = []
  scrollToBottomForced()
}

function handleEnter(e) {
  if (!e.shiftKey) {
    e.preventDefault()
    send()
  }
}

async function startConversation() {
  currentStream?.close()
  currentStream = null
  activeStreamMessageIndex.value = -1
  flushStreamBuffer()
  closeConversationMenu()
  messages.value = []
  chatId.value = genChatId()
  activeConversationId.value = chatId.value
  saveActiveConversationId(activeConversationId.value)
  uploadedFiles.value = []
  allowWebSearch.value = false
  allowKnowledgeBase.value = false
  showEmotionReportDialog.value = false
  emotionReport.value = null
  emotionReportMessageIndex.value = -1
  input.value = ''
  resizeTextarea()
  focusComposer()
  persistActiveConversationCache()
  shouldStickMessagesToBottom.value = true
  const uid = currentUserId()
  if (uid) {
    try {
      await axios.post('/api/chat_memory/createConversation', {
        conversationId: chatId.value,
        title: '新对话',
      })
      fetchConversationSummaries()
    } catch (e) {
      showToast(extractBackendErrorMessage(e, '创建会话失败'))
    }
  }
}

function handleAuthSuccess(payload) {
  showLoginModal.value = false
  showProfileMenu.value = false
  syncAuthState()
  fetchUserProfile()
  fetchConversationSummaries()
  showToast(payload?.registered ? '注册并登录成功' : '登录成功')
}

function toggleProfileMenu() {
  if (!loggedIn.value) {
    showProfileMenu.value = false
    openLogin()
    return
  }
  showProfileMenu.value = !showProfileMenu.value
}

function closeProfileMenu() {
  showProfileMenu.value = false
}

async function openProfileHome() {
  closeProfileMenu()
  if (!loggedIn.value) {
    openLogin()
    return
  }
  await fetchUserProfile()
  showProfileDialog.value = true
}

function openAdminConsole() {
  closeProfileMenu()
  if (!isAdminUser.value) {
    showToast('当前账号不是管理员，无法进入后台')
    return
  }
  router.push('/admin')
}

function openLogin() {
  closeProfileMenu()
  showLoginModal.value = true
}

function openLogoutConfirm() {
  closeProfileMenu()
  showLogoutConfirm.value = true
}

async function confirmLogout() {
  try { await axios.post('/api/user/exit') } catch (e) {}
  clearAuthSession()
  emitAuthEvent('auth:logout')
  showLogoutConfirm.value = false
  showProfileDialog.value = false
  showEmotionReportDialog.value = false
  emotionReport.value = null
  startConversation()
  syncAuthState()
  history.value = []
  activeConversationId.value = ''
  showToast('已退出登录')
}

function cancelLogout() {
  showLogoutConfirm.value = false
}

function goHome() {
  router.push('/')
}

function closeProfileDialog() {
  showProfileDialog.value = false
}

async function fetchUserProfile() {
  const uid = currentUserId()
  if (!uid) {
    resetUserProfile()
    return null
  }
  try {
    const raw = await getUserProfile(uid)
    return applyUserProfile(raw)
  } catch (e) {
    showToast(extractBackendErrorMessage(e, '读取个人资料失败'))
    return null
  }
}

async function submitProfileForm(payload) {
  if (!loggedIn.value || savingProfile.value) {
    return
  }
  savingProfile.value = true
  try {
    const nextProfile = await updateUserProfile(payload)
    applyUserProfile(nextProfile)
    showProfileDialog.value = false
    fetchConversationSummaries()
    showToast('个人资料已更新')
  } catch (e) {
    showToast(extractBackendErrorMessage(e, '个人资料更新失败'))
  } finally {
    savingProfile.value = false
  }
}

async function handleProfileAvatarUpload(file) {
  const uid = currentUserId()
  if (!uid || !file || uploadingProfileAvatar.value) {
    return
  }
  uploadingProfileAvatar.value = true
  try {
    const avatarPath = await uploadUserAvatar({ file, userId: uid })
    if (avatarPath) {
      applyUserProfile({
        ...userProfile.value,
        userAvatar: avatarPath,
        updateTime: new Date().toISOString(),
      })
      await fetchUserProfile()
      showToast('头像已更新')
    } else {
      showToast('头像上传失败')
    }
  } catch (e) {
    showToast(extractBackendErrorMessage(e, '头像上传失败'))
  } finally {
    uploadingProfileAvatar.value = false
  }
}

async function fetchConversationSummaries() {
  let username = ''
  try { username = localStorage.getItem('user_name') || '' } catch (e) { username = '' }
  if (!username) {
    historyFetchToken += 1
    closeConversationMenu()
    history.value = []
    return
  }
  const keyword = normalizeHistoryKeyword(historyKeyword.value)
  const requestToken = ++historyFetchToken
  loadingHistory.value = true
  try {
    const params = { username }
    if (keyword) {
      params.keyword = keyword
    }
    const res = await axios.get('/api/chat_memory/getConversationSummaries', { params })
    if (requestToken !== historyFetchToken || currentUsername() !== username || normalizeHistoryKeyword(historyKeyword.value) !== keyword) {
      return
    }
    const raw = Array.isArray(res.data) ? res.data : (Array.isArray(res.data?.data) ? res.data.data : [])
    history.value = sortConversationSummaries(raw.map(item => normalizeConversationSummaryItem(item)))
    if (!activeConversationId.value && history.value.length > 0) {
      const preferredConversationId = readActiveConversationId()
      const preferredHistory = history.value.find(item => String(item?.conversationId || '') === preferredConversationId)
      await openHistory((preferredHistory || history.value[0]).conversationId)
    }
  } catch (e) {
    if (requestToken === historyFetchToken) {
      history.value = []
      showToast(extractBackendErrorMessage(e, '会话列表加载失败'))
    }
  } finally {
    if (requestToken === historyFetchToken) {
      loadingHistory.value = false
    }
  }
}

function updateConversationPinLocally(conversationId, pinned) {
  const normalizedConversationId = String(conversationId || '')
  if (!normalizedConversationId) {
    return
  }
  history.value = sortConversationSummaries(history.value.map(item => {
    if (String(item?.conversationId || '') !== normalizedConversationId) {
      return item
    }
    return {
      ...item,
      pinned: !!pinned,
    }
  }))
}

function clearHistoryKeyword() {
  if (!historyKeyword.value) {
    return
  }
  historyKeyword.value = ''
}

function submitHistorySearch() {
  clearHistorySearchTask()
  fetchConversationSummaries()
}

function setConversationMenuTriggerRef(conversationId, el) {
  const normalizedConversationId = String(conversationId || '')
  if (!normalizedConversationId) {
    return
  }
  if (el) {
    conversationMenuTriggerRefs.set(normalizedConversationId, el)
    return
  }
  conversationMenuTriggerRefs.delete(normalizedConversationId)
}

function updateConversationMenuDirection(conversationId) {
  conversationMenuDirection.value = 'down'
  const normalizedConversationId = String(conversationId || '')
  if (!normalizedConversationId) {
    return
  }
  const listElement = historyListRef.value
  const triggerElement = conversationMenuTriggerRefs.get(normalizedConversationId)
  if (!listElement || !triggerElement) {
    return
  }
  const listRect = listElement.getBoundingClientRect()
  const triggerRect = triggerElement.getBoundingClientRect()
  const spaceBelow = listRect.bottom - triggerRect.bottom
  const spaceAbove = triggerRect.top - listRect.top
  if (spaceBelow < HISTORY_MENU_ESTIMATED_HEIGHT && spaceAbove > spaceBelow) {
    conversationMenuDirection.value = 'up'
  }
}

function isConversationMenuUpward(conversationId) {
  return conversationMenuId.value === String(conversationId || '') && conversationMenuDirection.value === 'up'
}

function closeConversationMenu() {
  conversationMenuId.value = ''
  conversationMenuDirection.value = 'down'
}

function toggleConversationMenu(conversationId) {
  const normalizedConversationId = String(conversationId || '')
  if (!normalizedConversationId) {
    return
  }
  if (conversationMenuId.value === normalizedConversationId) {
    closeConversationMenu()
    return
  }
  conversationMenuId.value = normalizedConversationId
  nextTick(() => updateConversationMenuDirection(normalizedConversationId))
}

async function toggleConversationPin(item) {
  const conversationId = String(item?.conversationId || '')
  const username = currentUsername()
  if (!conversationId || !username) {
    showToast('请先登录后再设置置顶')
    return
  }
  if (pinningConversationId.value === conversationId) {
    return
  }
  const nextPinned = !item?.pinned
  pinningConversationId.value = conversationId
  try {
    await axios.post('/api/chat_memory/pinConversation', {
      username,
      conversationId,
      pinned: nextPinned,
    })
    closeConversationMenu()
    updateConversationPinLocally(conversationId, nextPinned)
    showToast(nextPinned ? '会话已置顶' : '已取消置顶')
    fetchConversationSummaries()
  } catch (e) {
    showToast(extractBackendErrorMessage(e, nextPinned ? '会话置顶失败' : '取消置顶失败'))
  } finally {
    if (pinningConversationId.value === conversationId) {
      pinningConversationId.value = ''
    }
  }
}

async function openHistory(conversationId) {
  if (!conversationId) return
  closeConversationMenu()
  activeConversationId.value = String(conversationId)
  chatId.value = activeConversationId.value
  saveActiveConversationId(conversationId)
  const cachedMessages = getCachedConversation(conversationId)
  if (cachedMessages && cachedMessages.length > 0) {
    messages.value = normalizeConversationMessages(cachedMessages)
    scrollToBottom()
    hydrateConversationFromServer(conversationId, true)
    return
  }
  await hydrateConversationFromServer(conversationId)
}

function normalizeConversationTitleInput(title) {
  const normalizedTitle = String(title || '')
      .replace(/[\r\n\t]+/g, ' ')
      .replace(/\s{2,}/g, ' ')
      .replace(/^(标题|会话标题)\s*[:：]\s*/, '')
      .trim()
      .replace(/[。！？；;：:，,]+$/g, '')
      .trim()
  if (!normalizedTitle) {
    return ''
  }
  if (normalizedTitle.length <= CONVERSATION_TITLE_MAX_LENGTH) {
    return normalizedTitle
  }
  return normalizedTitle.slice(0, CONVERSATION_TITLE_MAX_LENGTH).trim()
}

function updateConversationTitleLocally(conversationId, title) {
  const normalizedConversationId = String(conversationId || '')
  const normalizedTitle = String(title || '').trim()
  if (!normalizedConversationId || !normalizedTitle) return
  history.value = history.value.map(item => {
    if (String(item?.conversationId || '') !== normalizedConversationId) {
      return item
    }
    return {
      ...item,
      title: normalizedTitle,
    }
  })
}

function openRenameConversationModal(item) {
  const conversationId = String(item?.conversationId || '')
  const username = currentUsername()
  if (!conversationId || !username) {
    showToast('请先登录后再重命名会话')
    return
  }
  closeConversationMenu()
  renameConversationId.value = conversationId
  renameConversationTitle.value = String(item?.title || '新对话')
  showRenameConversationModal.value = true
  nextTick(() => {
    renameInputRef.value?.focus?.()
    renameInputRef.value?.select?.()
  })
}

function closeRenameConversationModal(force = false) {
  if (renamingConversation.value && !force) return
  showRenameConversationModal.value = false
  renameConversationId.value = ''
  renameConversationTitle.value = ''
}

async function submitRenameConversation() {
  const conversationId = String(renameConversationId.value || '')
  const username = currentUsername()
  const normalizedTitle = normalizeConversationTitleInput(renameConversationTitle.value)
  if (!conversationId || !username) {
    showToast('请先登录后再重命名会话')
    return
  }
  if (!normalizedTitle) {
    showToast('会话名称不能为空')
    return
  }
  renamingConversation.value = true
  try {
    await axios.post('/api/chat_memory/renameConversation', {
      username,
      conversationId,
      title: normalizedTitle,
    })
    updateConversationTitleLocally(conversationId, normalizedTitle)
    persistActiveConversationCache()
    renamingConversation.value = false
    closeRenameConversationModal(true)
    showToast('会话名称已更新')
    fetchConversationSummaries()
  } catch (e) {
    showToast(extractBackendErrorMessage(e, '会话重命名失败'))
  } finally {
    renamingConversation.value = false
  }
}

function openDeleteConversationModal(item) {
  const conversationId = String(item?.conversationId || '')
  const username = currentUsername()
  const title = String(item?.title || '当前会话')
  if (!conversationId || !username) {
    showToast('请先登录后再删除会话')
    return
  }
  closeConversationMenu()
  deleteConversationId.value = conversationId
  deleteConversationTitle.value = title
  showDeleteConversationModal.value = true
}

function closeDeleteConversationModal(force = false) {
  if (deletingConversation.value && !force) return
  showDeleteConversationModal.value = false
  deleteConversationId.value = ''
  deleteConversationTitle.value = ''
}

async function confirmDeleteConversation() {
  const conversationId = String(deleteConversationId.value || '')
  const username = currentUsername()
  if (!conversationId || !username) {
    showToast('请先登录后再删除会话')
    return
  }
  deletingConversation.value = true
  try {
    await axios.post('/api/chat_memory/deleteConversation', {
      username,
      conversationId,
    })
    currentStream?.close()
    removeConversationCache(conversationId)
    const nextHistory = history.value.filter(historyItem => String(historyItem?.conversationId || '') !== conversationId)
    history.value = nextHistory
    if (String(activeConversationId.value || '') === conversationId) {
      if (nextHistory.length > 0) {
        await openHistory(nextHistory[0].conversationId)
      } else {
        startConversation()
      }
    }
    deletingConversation.value = false
    closeDeleteConversationModal(true)
    showToast('会话已删除')
    fetchConversationSummaries()
  } catch (e) {
    showToast(extractBackendErrorMessage(e, '会话删除失败'))
  } finally {
    deletingConversation.value = false
  }
}

function onAuthChange() {
  syncAuthState()
  if (loggedIn.value) {
    loadMessageFeedback()
    fetchUserProfile()
    fetchConversationSummaries()
  } else {
    messageFeedbackMap.value = {}
    closeProfileMenu()
    showProfileDialog.value = false
    history.value = []
  }
}

onMounted(() => {
  chatId.value = genChatId()
  syncAuthState()
  loadMessageFeedback()
  resizeTextarea()
  applyPrefillPrompt(route.query.prompt)
  elapsedTimer = setInterval(() => {
    nowTick.value = Date.now()
  }, 1000)
  const restoredFromCache = restoreActiveConversationFromCache()
  if (loggedIn.value) {
    fetchUserProfile()
    fetchConversationSummaries()
    const resumedPendingConversation = restoredFromCache ? resumePendingConversationFromCache() : false
    if (restoredFromCache && activeConversationId.value && !resumedPendingConversation) {
      hydrateConversationFromServer(activeConversationId.value, true)
    }
  }
  window.addEventListener('auth:login', onAuthChange)
  window.addEventListener('auth:logout', onAuthChange)
  window.addEventListener('storage', onAuthChange)
})

onBeforeUnmount(() => {
  currentStream?.close()
  currentStream = null
  activeStreamMessageIndex.value = -1
  flushAllAnimatedStreamQueues()
  flushStreamBuffer()
  clearHistorySearchTask()
  if (processViewportSyncTimer) {
    cancelAnimationFrame(processViewportSyncTimer)
    processViewportSyncTimer = null
  }
  processPanelRefs.clear()
  if (elapsedTimer) {
    clearInterval(elapsedTimer)
    elapsedTimer = null
  }
  window.removeEventListener('auth:login', onAuthChange)
  window.removeEventListener('auth:logout', onAuthChange)
  window.removeEventListener('storage', onAuthChange)
})

watch(
    historyKeyword,
    () => {
      scheduleHistorySearch()
    }
)

watch(
    () => route.query.prompt,
    (value) => {
      applyPrefillPrompt(value)
    }
)
</script>

<template>
  <div class="assistant-page" @click="closeProfileMenu(); closeConversationMenu()">
    <div class="assistant-bg">
      <div class="bg-grid"></div>
      <div class="bg-orb orb-left"></div>
      <div class="bg-orb orb-right"></div>
    </div>

    <div class="assistant-shell">
      <aside class="side-panel">
        <div class="brand-block">
          <ProjectBrand size="large" />
        </div>

        <button class="back-home-btn" @click="goHome">
          <span class="back-home-icon-wrap" aria-hidden="true">
            <span class="back-home-icon"></span>
          </span>
          <span class="back-home-copy">
            <span class="back-home-label">返回首页</span>
            <span class="back-home-sub">回到灵伴首页</span>
          </span>
        </button>

        <button class="primary-action" @click="startConversation">开启新对话</button>

        <div class="history-section">
          <div class="section-title">历史对话</div>
          <div class="history-search">
            <div class="history-search-input-wrap">
              <input
                  v-model="historyKeyword"
                  class="history-search-input"
                  type="text"
                  placeholder="搜索历史对话"
                  @keydown.enter.prevent="submitHistorySearch"
              />
              <button
                  v-if="historyKeyword"
                  class="history-search-clear"
                  type="button"
                  @click="clearHistoryKeyword"
              >
                清空
              </button>
            </div>
          </div>
          <div v-if="loadingHistory" class="history-empty">正在加载会话...</div>
          <div v-else-if="!history.length" class="history-empty">
            {{ historyKeyword.trim() ? '没有找到匹配的会话，换个关键词试试。' : '还没有会话，先开始一段新的对话吧。' }}
          </div>
          <div v-else ref="historyListRef" class="history-list">
            <div
                v-for="item in history"
                :key="item.conversationId"
                class="history-card"
                :class="{ active: item.conversationId === activeConversationId }"
            >
              <button class="history-card-main" type="button" @click="openHistory(item.conversationId)">
                <div class="history-card-head">
                  <div class="history-card-title-wrap">
                    <span class="history-card-title">{{ item.title }}</span>
                  </div>
                  <span class="history-card-time">{{ item.lastTime }}</span>
                </div>
                <div class="history-card-meta">
                  <span v-if="item.pinned" class="history-card-status">已置顶</span>
                  <span>共 {{ item.messageCount || 0 }} 条消息</span>
                </div>
              </button>
              <div class="history-card-tools" @click.stop>
                <button
                    class="history-card-more"
                    type="button"
                    :ref="el => setConversationMenuTriggerRef(item.conversationId, el)"
                    :aria-expanded="conversationMenuId === item.conversationId"
                    @click="toggleConversationMenu(item.conversationId)"
                >
                  ···
                </button>
                <div
                    v-if="conversationMenuId === item.conversationId"
                    class="history-card-menu"
                    :class="{ 'is-upward': isConversationMenuUpward(item.conversationId) }"
                >
                  <button
                      class="history-card-menu-item"
                      type="button"
                      :disabled="pinningConversationId === item.conversationId"
                      @click="toggleConversationPin(item)"
                  >
                    {{ pinningConversationId === item.conversationId ? '处理中...' : (item.pinned ? '取消置顶' : '置顶') }}
                  </button>
                  <button class="history-card-menu-item" type="button" @click="openRenameConversationModal(item)">重命名</button>
                  <button class="history-card-menu-item is-danger" type="button" @click="openDeleteConversationModal(item)">删除</button>
                </div>
              </div>
            </div>
          </div>
        </div>

        <div class="profile-entry" @click.stop>
          <button class="profile-card" type="button" @click="toggleProfileMenu">
            <div class="profile-avatar">
              <img :src="topAvatarUrl || DEFAULT_AVATAR" alt="avatar" />
            </div>
            <div class="profile-meta">
              <div class="profile-name">{{ loggedIn ? (userName || '已登录用户') : '未登录' }}</div>
              <div class="profile-text">{{ loggedIn ? '点击查看个人主页或退出登录' : '点击登录后保存资料与历史对话' }}</div>
            </div>
            <span class="profile-trigger" :class="{ active: showProfileMenu }" aria-hidden="true"></span>
          </button>

          <div v-if="showProfileMenu" class="profile-menu">
            <template v-if="loggedIn">
              <button v-if="isAdminUser" class="profile-menu-item" type="button" @click="openAdminConsole">管理后台</button>
              <button class="profile-menu-item" type="button" @click="openProfileHome">个人主页</button>
              <button class="profile-menu-item is-danger" type="button" @click="openLogoutConfirm">退出登录</button>
            </template>
            <button v-else class="profile-menu-item" type="button" @click="openLogin">登录 / 注册</button>
          </div>
        </div>
      </aside>

      <main class="chat-panel">
        <section ref="messagesPanelRef" class="messages-panel" @scroll="handleMessagesScroll">
          <div v-if="!messages.length" class="empty-state">
            <div class="empty-badge">欢迎开始</div>
            <h3 class="empty-title">不用想得太完整，先把你现在最想说的那一句发出来</h3>
            <p class="empty-desc">
              你可以先说情绪，也可以先说计划；可以先描述困扰，也可以直接提出目标。
              只要开始，我们就能一起把它慢慢理清楚。
            </p>
            <div class="empty-grid">
              <button
                  v-for="item in quickStartList"
                  :key="'empty-' + item.title"
                  class="empty-card"
                  @click="applyPrefillPrompt(item.prompt)"
              >
                <div class="empty-card-title">{{ item.title }}</div>
                <div class="empty-card-desc">{{ item.desc }}</div>
              </button>
            </div>
          </div>

          <div v-for="(msg, index) in messages" :key="index" class="message-row" :class="msg.role">
            <div class="message-card" :class="msg.role">
              <div
                  v-if="msg.role === 'assistant' && getRouteTagText(msg.routeMode)"
                  class="message-tag"
                  :class="getRouteTagClass(msg.routeMode)"
              >
                {{ getRouteTagText(msg.routeMode) }}
              </div>
              <div v-if="msg.role === 'assistant' && hasProcessPanel(msg)" :ref="el => setProcessPanelRef(index, el)" class="message-block thinking process-panel">
                <button class="message-collapse" type="button" @click="toggleThinking(index)">
                  <span class="process-head-main">
                    <span class="process-brand" aria-hidden="true">
                      <span class="process-brand-mark">
                        <span class="process-brand-orbit"></span>
                        <span class="process-brand-core"></span>
                        <span class="process-brand-spark process-brand-spark-a"></span>
                        <span class="process-brand-spark process-brand-spark-b"></span>
                      </span>
                      <span class="process-brand-text">Lin Think</span>
                    </span>
                    <span v-if="getProcessElapsedLabel(msg)" class="process-elapsed">{{ getProcessElapsedLabel(msg) }}</span>
                    <span class="message-collapse-meta" :aria-label="msg.thinkingCollapsed ? '展开思考区域' : '收起思考区域'">
                      <span class="process-toggle-icon" :class="{ collapsed: msg.thinkingCollapsed }" aria-hidden="true"></span>
                    </span>
                  </span>
                  <span class="process-head-copy">
                    <span class="message-block-title">{{ getProcessStatusLabel(msg) }}</span>
                    <span v-if="shouldShowProcessSummary(msg)" class="process-head-note">{{ getProcessSummary(msg) }}</span>
                  </span>
                </button>
                <div v-if="!msg.thinkingCollapsed" class="process-body">
                  <div class="message-text subtle process-stream" v-html="renderProcessContent(msg)"></div>
                </div>
              </div>
              <div v-if="msg.role === 'assistant'" class="message-block final">
                <div class="message-block-title answer-block-title">最终答复</div>
                <div class="answer-stage" :class="{ loading: msg.loading && !msg.finalContent }">
                  <div v-if="msg.loading && !msg.finalContent" class="answer-loading-shell" aria-label="正在整理最终答复">
                    <div class="answer-loading-badge">
                      <span class="answer-loading-dot"></span>
                      <span class="answer-loading-text">正在整理最终答复</span>
                    </div>
                    <div class="answer-loading-lines">
                      <span class="answer-loading-line is-long"></span>
                      <span class="answer-loading-line is-medium"></span>
                      <span class="answer-loading-line is-short"></span>
                    </div>
                  </div>
                  <div
                      v-else
                      class="message-text answer-text"
                      v-html="renderAnswerContent(msg.finalContent || '')"
                  ></div>
                </div>
                <div v-if="msg.finalContent && !msg.loading" class="message-action-row">
                  <div class="message-action-logos">
                    <button
                        class="message-action-logo"
                        :class="{ 'is-active': isAssistantLiked(index) }"
                        type="button"
                        title="点赞"
                        aria-label="点赞"
                        @click="toggleAssistantLike(index)"
                    >
                      <svg class="message-action-logo-svg" viewBox="0 0 24 24" aria-hidden="true">
                        <path d="M8 10V20" />
                        <path d="M8 10L11.3 4.4C11.6 3.8 12.2 3.5 12.9 3.5H13C14.1 3.5 15 4.4 15 5.5V9.5H18.1C19.4 9.5 20.3 10.7 20 11.9L18.3 18.9C18 19.8 17.2 20.4 16.3 20.4H8" />
                        <rect x="3.5" y="10" width="4.5" height="10.5" rx="1.2" />
                      </svg>
                    </button>
                    <button
                        class="message-action-logo"
                        :class="{ 'is-active': isAssistantDisliked(index) }"
                        type="button"
                        title="点踩"
                        aria-label="点踩"
                        @click="toggleAssistantDislike(index)"
                    >
                      <svg class="message-action-logo-svg" viewBox="0 0 24 24" aria-hidden="true">
                        <path d="M8 4V14" />
                        <path d="M8 14L11.3 19.6C11.6 20.2 12.2 20.5 12.9 20.5H13C14.1 20.5 15 19.6 15 18.5V14.5H18.1C19.4 14.5 20.3 13.3 20 12.1L18.3 5.1C18 4.2 17.2 3.6 16.3 3.6H8" />
                        <rect x="3.5" y="3.5" width="4.5" height="10.5" rx="1.2" />
                      </svg>
                    </button>
                    <button
                        class="message-action-logo"
                        type="button"
                        title="复制"
                        aria-label="复制"
                        @click="copyAssistantAnswer(index)"
                    >
                      <svg class="message-action-logo-svg" viewBox="0 0 24 24" aria-hidden="true">
                        <rect x="9" y="8" width="10.5" height="12" rx="2" />
                        <path d="M6.5 15.8H6C4.9 15.8 4 14.9 4 13.8V5.5C4 4.4 4.9 3.5 6 3.5H13.5C14.6 3.5 15.5 4.4 15.5 5.5V6" />
                      </svg>
                    </button>
                    <button
                        v-if="isEmotionAssistantMessage(msg)"
                        class="message-action-logo"
                        type="button"
                        :disabled="generatingEmotionReport && emotionReportMessageIndex === index"
                        title="情感报告"
                        aria-label="情感报告"
                        @click="openEmotionReportForMessage(index)"
                    >
                      <svg
                          class="message-action-logo-svg"
                          :class="{ spinning: generatingEmotionReport && emotionReportMessageIndex === index }"
                          aria-hidden="true"
                          viewBox="0 0 24 24"
                      >
                        <path d="M6.2 4.5H13.5L18 9V19.5C18 20.1 17.6 20.5 17 20.5H6.2C5.6 20.5 5.2 20.1 5.2 19.5V5.5C5.2 4.9 5.6 4.5 6.2 4.5Z" />
                        <path d="M13.5 4.5V9H18" />
                        <path d="M8.2 14.2C9 13.4 10 13 11 13.2C11.8 13.4 12.3 14 13 14.1C13.9 14.3 14.5 13.9 15 13.4" />
                        <path d="M8.2 16.8C9 16 10 15.6 11 15.8C11.8 16 12.3 16.6 13 16.7C13.9 16.9 14.5 16.5 15 16" />
                      </svg>
                    </button>
                    <button
                        class="message-action-logo"
                        type="button"
                        title="导出 PDF"
                        aria-label="导出 PDF"
                        @click="exportAssistantAnswerPdf(index)"
                    >
                      <svg class="message-action-logo-svg" viewBox="0 0 24 24" aria-hidden="true">
                        <path d="M6.2 3.5H13.5L18 8V19.5C18 20.1 17.6 20.5 17 20.5H6.2C5.6 20.5 5.2 20.1 5.2 19.5V4.5C5.2 3.9 5.6 3.5 6.2 3.5Z" />
                        <path d="M13.5 3.5V8H18" />
                        <path d="M11.6 10.8V16.1" />
                        <path d="M9.4 13.9L11.6 16.1L13.8 13.9" />
                      </svg>
                    </button>
                  </div>
                </div>
              </div>
              <div
                  v-else
                  class="user-message-body"
              >
                <div v-if="msg.attachedFiles && msg.attachedFiles.length" class="message-attachments">
                  <div v-for="file in msg.attachedFiles" :key="file" class="message-file-chip">
                    <span class="message-file-chip-name">{{ file }}</span>
                  </div>
                </div>
                <div
                    class="message-text"
                    v-html="renderMarkdown(msg.content, 'default')"
                ></div>
              </div>
            </div>
          </div>
        </section>

        <section class="composer-card">
          <div class="composer-surface">
            <div v-if="uploadedFiles.length" class="file-list">
              <div v-for="file in uploadedFiles" :key="file" class="file-chip">
                <span class="file-chip-name">{{ file }}</span>
                <button class="file-chip-remove" type="button" @click="removeUploadedFile(file)">×</button>
              </div>
            </div>
            <textarea
                ref="textareaRef"
                v-model="input"
                class="composer-input"
                placeholder="先写一句你现在最在意的事，比如目标、困扰、限制条件，或者你已经想到的方案。"
                @input="resizeTextarea"
                @keydown.enter="handleEnter"
            ></textarea>
            <div class="composer-bottom">
              <div class="composer-meta">
                <div class="composer-tip">{{ composerTipText }}</div>
                <div class="composer-shortcut">Enter 发送/停止 · Shift+Enter 换行</div>
              </div>
              <div class="composer-actions">
                <button
                    class="tool-toggle-btn"
                    :class="{ active: allowKnowledgeBase }"
                    type="button"
                    @click="toggleKnowledgeBase"
                >
                  {{ allowKnowledgeBase ? '知识库已开' : '知识库' }}
                </button>
                <button
                    class="tool-toggle-btn"
                    :class="{ active: allowWebSearch }"
                    type="button"
                    @click="toggleWebSearch"
                >
                  {{ allowWebSearch ? '联网搜索已开' : '联网搜索' }}
                </button>
                <button class="upload-btn" type="button" @click="triggerUpload">{{ uploadingFile ? '上传中...' : '上传文件' }}</button>
                <input ref="uploadInputRef" type="file" accept=".txt,.md,.json,.csv" style="display:none" @change="handleFileUpload" />
                <button
                    class="send-btn"
                    :class="{ sending: isGeneratingReply }"
                    :disabled="!canTriggerSend"
                    :title="isGeneratingReply ? '停止生成' : '发送'"
                    :aria-label="isGeneratingReply ? '停止生成' : '发送'"
                    @click="send"
                >
                  <span class="send-btn-icon" aria-hidden="true">
                    <svg v-if="isGeneratingReply" class="send-btn-stop-svg" viewBox="0 0 24 24">
                      <circle cx="12" cy="12" r="8.2"></circle>
                      <rect x="9" y="9" width="6" height="6" rx="1.8"></rect>
                    </svg>
                    <svg v-else class="send-btn-send-svg" viewBox="0 0 24 24">
                      <path d="M4.5 12.2L18.7 5.6C19.6 5.2 20.5 6.1 20.1 7L13.5 21.2C13.1 22.1 11.8 22.1 11.5 21.1L9.5 15.2L3.6 13.2C2.6 12.9 2.6 11.6 3.5 11.2L17.1 5"></path>
                      <path d="M9.4 15.1L20 6"></path>
                    </svg>
                  </span>
                </button>
              </div>
            </div>
          </div>
        </section>
      </main>
    </div>

    <div v-if="showLoginModal" class="modal-mask" @click.self="showLoginModal = false">
      <div class="modal-card">
        <div class="modal-title">登录或注册</div>
        <AuthForm initial-mode="login" @success="handleAuthSuccess" />
      </div>
    </div>

    <UserProfileDialog
      :visible="showProfileDialog"
      :profile="userProfile"
      :saving="savingProfile"
      :uploading-avatar="uploadingProfileAvatar"
      @close="closeProfileDialog"
      @save="submitProfileForm"
      @upload-avatar="handleProfileAvatarUpload"
    />

    <EmotionReportDialog
      :visible="showEmotionReportDialog"
      :loading="generatingEmotionReport"
      :report="emotionReport"
      @close="closeEmotionReportDialog"
      @export="exportEmotionReportPdf"
    />

    <div v-if="showLogoutConfirm" class="modal-mask" @click.self="cancelLogout">
      <div class="modal-card">
        <div class="modal-title">确认退出登录？</div>
        <div class="modal-text">退出后仍然可以重新登录，当前本地登录态会被清空。</div>
        <div class="modal-actions">
          <button class="secondary-btn" @click="cancelLogout">取消</button>
          <button class="danger-btn" @click="confirmLogout">确认退出</button>
        </div>
      </div>
    </div>

    <div v-if="showRenameConversationModal" class="modal-mask" @click.self="closeRenameConversationModal()">
      <div class="modal-card">
        <div class="modal-title">重命名会话</div>
        <div class="modal-text">修改后会同步更新当前会话在列表中的显示名称。</div>
        <input
            ref="renameInputRef"
            v-model="renameConversationTitle"
            class="modal-input"
            type="text"
            maxlength="18"
            placeholder="请输入新的会话名称"
            @keyup.enter="submitRenameConversation"
        />
        <div class="modal-hint">最多 18 个字符</div>
        <div class="modal-actions">
          <button class="secondary-btn" @click="closeRenameConversationModal()">取消</button>
          <button class="primary-action modal-primary-btn" :disabled="!canSubmitConversationRename || renamingConversation" @click="submitRenameConversation">
            {{ renamingConversation ? '提交中...' : '确认修改' }}
          </button>
        </div>
      </div>
    </div>

    <div v-if="showDeleteConversationModal" class="modal-mask" @click.self="closeDeleteConversationModal()">
      <div class="modal-card">
        <div class="modal-title">确认删除会话？</div>
        <div class="modal-text">你将删除“{{ deleteConversationTitle || '当前会话' }}”的全部聊天记录，删除后无法恢复。</div>
        <div class="modal-actions">
          <button class="secondary-btn" @click="closeDeleteConversationModal()">取消</button>
          <button class="danger-btn" :disabled="deletingConversation" @click="confirmDeleteConversation">
            {{ deletingConversation ? '删除中...' : '确认删除' }}
          </button>
        </div>
      </div>
    </div>

    <div v-if="toastVisible" class="toast">{{ toastText }}</div>
  </div>
</template>

<style scoped>
.assistant-page {
  position: relative;
  width: 100%;
  min-height: 100dvh;
  overflow-x: hidden;
  overflow-y: hidden;
  background:
    radial-gradient(1200px 640px at 10% -10%, rgba(34, 211, 238, 0.18), transparent),
    radial-gradient(900px 580px at 100% 100%, rgba(245, 158, 11, 0.16), transparent),
    linear-gradient(180deg, #f4f8ff 0%, #edf3fb 100%);
}

.assistant-bg {
  position: absolute;
  inset: 0;
  pointer-events: none;
}

.bg-grid {
  position: absolute;
  inset: 0;
  background-image:
    linear-gradient(rgba(148, 163, 184, 0.12) 1px, transparent 1px),
    linear-gradient(90deg, rgba(148, 163, 184, 0.12) 1px, transparent 1px);
  background-size: 24px 24px;
  mask-image: radial-gradient(circle at 50% 32%, #000 0%, rgba(0, 0, 0, 0.78) 44%, transparent 82%);
}

.bg-orb {
  position: absolute;
  border-radius: 50%;
  filter: blur(70px);
  opacity: 0.48;
}

.orb-left {
  top: -120px;
  left: -60px;
  width: 360px;
  height: 360px;
  background: radial-gradient(circle, rgba(34, 211, 238, 0.5), rgba(34, 211, 238, 0));
}

.orb-right {
  right: -90px;
  bottom: -100px;
  width: 420px;
  height: 420px;
  background: radial-gradient(circle, rgba(99, 102, 241, 0.36), rgba(99, 102, 241, 0));
}

.assistant-shell {
  position: relative;
  z-index: 1;
  width: 100%;
  max-width: none;
  margin: 0;
  height: 100dvh;
  display: grid;
  grid-template-columns: clamp(280px, 24vw, 360px) minmax(0, 1fr);
  gap: clamp(12px, 1.2vw, 18px);
  padding: clamp(10px, 1.2vh, 18px) clamp(10px, 1vw, 18px);
}

.side-panel,
.chat-panel {
  border: 1px solid rgba(148, 163, 184, 0.2);
  background: rgba(255, 255, 255, 0.76);
  box-shadow: 0 22px 50px rgba(15, 23, 42, 0.08);
  backdrop-filter: blur(14px);
}

.side-panel {
  border-radius: 28px;
  padding: clamp(16px, 1.8vh, 24px) clamp(14px, 1.2vw, 20px);
  display: flex;
  flex-direction: column;
  gap: clamp(12px, 1.2vh, 18px);
  min-height: 0;
  overflow: hidden;
}

.brand-block {
  display: flex;
  align-items: flex-start;
}

.history-section {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.history-search {
  display: flex;
}

.history-search-input-wrap {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
  padding: 8px 10px;
  border-radius: 16px;
  background: rgba(255, 255, 255, 0.9);
  border: 1px solid rgba(148, 163, 184, 0.16);
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.7);
}

.history-search-input-wrap:focus-within {
  border-color: rgba(36, 89, 216, 0.3);
  box-shadow: 0 0 0 4px rgba(36, 89, 216, 0.08);
}

.history-search-input {
  width: 100%;
  border: none;
  background: transparent;
  outline: none;
  color: #0f172a;
  font-size: 13px;
  line-height: 1.6;
}

.history-search-input::placeholder {
  color: #94a3b8;
}

.history-search-clear {
  flex: 0 0 auto;
  border: none;
  border-radius: 999px;
  background: rgba(36, 89, 216, 0.08);
  color: #1d4ed8;
  font-size: 12px;
  font-weight: 700;
  padding: 6px 10px;
  cursor: pointer;
}

.section-title {
  font-size: 16px;
  line-height: 1.55;
  font-weight: 800;
  color: #0f172a;
}

.history-list {
  display: flex;
  flex-direction: column;
  gap: 0;
  max-height: 46vh;
  overflow-y: auto;
  padding-right: 10px;
  padding-bottom: 72px;
}

.history-card {
  position: relative;
  display: flex;
  align-items: flex-start;
  gap: 10px;
  padding: 4px 0;
  border-bottom: 1px solid rgba(148, 163, 184, 0.12);
  transition: background .18s ease;
}

.history-card:hover {
  background: rgba(248, 250, 252, 0.7);
}

.history-card.active {
  background: rgba(231, 241, 255, 0.78);
}

.history-card-main {
  flex: 1;
  min-width: 0;
  padding: 12px 10px 12px 14px;
  border: none;
  border-radius: 14px;
  background: transparent;
  text-align: left;
  transition: background .18s ease;
}

.history-card-main:hover {
  background: rgba(255, 255, 255, 0.72);
}

.history-card.active::before {
  content: '';
  position: absolute;
  left: 0;
  top: 8px;
  bottom: 8px;
  width: 3px;
  border-radius: 999px;
  background: linear-gradient(180deg, #2459d8 0%, #22d3ee 100%);
}

.history-card-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 10px;
}

.history-card-title-wrap {
  min-width: 0;
  flex: 1;
}

.history-card-title {
  font-size: 15px;
  font-weight: 800;
  color: #0f172a;
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.history-card-time {
  flex: 0 0 auto;
  font-size: 11px;
  line-height: 1.4;
  color: #94a3b8;
}

.history-card-meta {
  margin-top: 8px;
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 11px;
  color: #94a3b8;
}

.history-card-status {
  color: #b45309;
  font-weight: 700;
}

.history-card-tools {
  position: relative;
  flex: 0 0 auto;
  padding-top: 8px;
}

.history-card-more {
  width: 34px;
  height: 34px;
  border: none;
  border-radius: 10px;
  background: transparent;
  color: #64748b;
  font-size: 18px;
  line-height: 1;
  cursor: pointer;
  transition: background .18s ease, color .18s ease;
}

.history-card-more:hover {
  background: rgba(241, 245, 249, 0.92);
  color: #1d4ed8;
}

.history-card-menu {
  position: absolute;
  top: 42px;
  right: 8px;
  z-index: 6;
  min-width: 132px;
  padding: 6px;
  border-radius: 14px;
  background: rgba(255, 255, 255, 0.98);
  border: 1px solid rgba(148, 163, 184, 0.18);
  box-shadow: 0 18px 36px rgba(15, 23, 42, 0.12);
}

.history-card-menu.is-upward {
  top: auto;
  bottom: 42px;
}

.history-card-menu-item {
  width: 100%;
  min-height: 34px;
  display: flex;
  align-items: center;
  padding: 0 10px;
  border: none;
  border-radius: 10px;
  background: transparent;
  color: #334155;
  font-size: 12px;
  font-weight: 700;
  text-align: left;
  cursor: pointer;
}

.history-card-menu-item:hover {
  background: rgba(241, 245, 249, 0.94);
  color: #1d4ed8;
}

.history-card-menu-item:disabled {
  opacity: 0.58;
  cursor: not-allowed;
}

.history-card-menu-item.is-danger:hover {
  background: rgba(254, 242, 242, 0.96);
  color: #dc2626;
}

.history-empty {
  border-radius: 16px;
  background: rgba(248, 250, 252, 0.94);
  border: 1px dashed rgba(148, 163, 184, 0.18);
  color: #64748b;
  font-size: 13px;
  line-height: 1.7;
  padding: 14px;
}

.primary-action,
.profile-card,
.profile-menu-item,
.back-home-btn,
.history-card-main,
.history-card-more,
.history-card-menu-item,
.send-btn,
.secondary-btn,
.danger-btn {
  border: none;
  cursor: pointer;
}

.primary-action {
  border-radius: 14px;
  background: linear-gradient(135deg, #2459d8 0%, #22d3ee 100%);
  color: #fff;
  font-size: clamp(14px, 0.95vw, 15px);
  font-weight: 800;
  padding: clamp(11px, 1.3vh, 13px) clamp(12px, 1vw, 16px);
  box-shadow: 0 16px 32px rgba(36, 89, 216, 0.22);
}

.back-home-btn {
  position: relative;
  display: inline-flex;
  align-items: center;
  justify-content: flex-start;
  gap: 12px;
  min-height: 58px;
  border-radius: 20px;
  border: 1px solid rgba(148, 163, 184, 0.14);
  background:
    linear-gradient(180deg, rgba(255, 255, 255, 0.9) 0%, rgba(248, 250, 252, 0.9) 100%);
  color: #0f172a;
  font-size: 14px;
  font-weight: 800;
  padding: 0 16px 0 14px;
  box-shadow: 0 14px 28px rgba(15, 23, 42, 0.06);
  overflow: hidden;
  transition: transform .18s ease, box-shadow .18s ease, border-color .18s ease, background .18s ease, color .18s ease;
}

.back-home-btn::before {
  content: '';
  position: absolute;
  inset: 0 auto 0 0;
  width: 4px;
  border-radius: 999px;
  background: linear-gradient(180deg, #2459d8 0%, #22d3ee 100%);
}

.back-home-btn::after {
  content: '';
  position: absolute;
  inset: -30% auto auto 42%;
  width: 120px;
  height: 120px;
  border-radius: 50%;
  background: radial-gradient(circle, rgba(34, 211, 238, 0.18), rgba(34, 211, 238, 0));
  opacity: 0;
  transition: opacity .18s ease;
}

.back-home-btn:hover {
  transform: translateY(-1px);
  border-color: rgba(36, 89, 216, 0.2);
  background:
    linear-gradient(180deg, rgba(239, 246, 255, 0.98) 0%, rgba(248, 251, 255, 0.96) 100%);
  color: #1d4ed8;
  box-shadow: 0 20px 36px rgba(15, 23, 42, 0.1);
}

.back-home-btn:hover::after {
  opacity: 1;
}

.back-home-icon-wrap {
  position: relative;
  z-index: 1;
  width: 34px;
  height: 34px;
  display: grid;
  place-items: center;
  flex: 0 0 34px;
  border-radius: 12px;
  background: linear-gradient(135deg, rgba(36, 89, 216, 0.14) 0%, rgba(34, 211, 238, 0.18) 100%);
}

.back-home-copy {
  position: relative;
  z-index: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  align-items: flex-start;
}

.back-home-label {
  font-size: 14px;
  font-weight: 800;
  line-height: 1.2;
}

.back-home-sub {
  margin-top: 3px;
  font-size: 11px;
  font-weight: 600;
  line-height: 1.3;
  color: #64748b;
}

.back-home-icon {
  width: 10px;
  height: 10px;
  border-left: 2px solid currentColor;
  border-bottom: 2px solid currentColor;
  transform: rotate(45deg);
  transition: transform .18s ease;
}

.back-home-btn:hover .back-home-icon {
  transform: translateX(-2px) rotate(45deg);
}

.back-home-btn:hover .back-home-sub {
  color: #475569;
}

.profile-entry {
  position: relative;
  margin-top: auto;
}

.profile-card {
  width: 100%;
  display: flex;
  gap: 12px;
  align-items: center;
  text-align: left;
  border: 1px solid rgba(148, 163, 184, 0.14);
  border-radius: 18px;
  background: rgba(15, 23, 42, 0.04);
  padding: clamp(12px, 1.2vh, 14px);
  transition: transform .18s ease, box-shadow .18s ease, border-color .18s ease, background .18s ease;
}

.profile-card:hover {
  transform: translateY(-1px);
  border-color: rgba(36, 89, 216, 0.22);
  background: rgba(255, 255, 255, 0.72);
  box-shadow: 0 16px 28px rgba(15, 23, 42, 0.08);
}

.profile-avatar img {
  width: 100%;
  height: 100%;
  display: block;
  object-fit: cover;
  border-radius: 50%;
}

.profile-avatar {
  width: 46px;
  height: 46px;
  flex: 0 0 46px;
}

.profile-meta {
  min-width: 0;
  flex: 1;
}

.profile-name {
  font-size: 15px;
  font-weight: 800;
  color: #0f172a;
}

.profile-text {
  font-size: 12px;
  line-height: 1.62;
  color: #64748b;
}

.profile-trigger {
  width: 10px;
  height: 10px;
  flex: 0 0 10px;
  border-right: 2px solid #94a3b8;
  border-bottom: 2px solid #94a3b8;
  transform: rotate(45deg);
  transition: transform .18s ease, border-color .18s ease;
}

.profile-trigger.active {
  transform: rotate(-135deg) translate(-2px, -2px);
  border-color: #2459d8;
}

.profile-menu {
  position: absolute;
  left: 0;
  right: 0;
  bottom: calc(100% + 12px);
  display: grid;
  gap: 8px;
  padding: 10px;
  border-radius: 18px;
  border: 1px solid rgba(148, 163, 184, 0.16);
  background: rgba(255, 255, 255, 0.96);
  box-shadow: 0 20px 38px rgba(15, 23, 42, 0.12);
  backdrop-filter: blur(16px);
}

.profile-menu-item {
  width: 100%;
  border-radius: 12px;
  background: rgba(248, 250, 252, 0.94);
  color: #1f2937;
  font-size: 14px;
  font-weight: 700;
  padding: 11px 12px;
  text-align: left;
  transition: background .18s ease, color .18s ease;
}

.profile-menu-item:hover {
  background: rgba(239, 246, 255, 0.96);
  color: #1d4ed8;
}

.profile-menu-item.is-danger {
  color: #b91c1c;
  background: rgba(254, 242, 242, 0.96);
}

.profile-menu-item.is-danger:hover {
  background: rgba(254, 226, 226, 0.98);
  color: #991b1b;
}

.chat-panel {
  border-radius: 30px;
  padding: clamp(16px, 1.8vh, 24px) clamp(14px, 1.2vw, 22px);
  display: flex;
  flex-direction: column;
  height: calc(100dvh - clamp(20px, 2.4vh, 36px));
  min-width: 0;
  min-height: 0;
  overflow: hidden;
}

.secondary-btn:hover {
  background: rgba(241, 245, 249, 0.96);
}

.messages-panel {
  flex: 1;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: clamp(10px, 1vh, 14px);
  width: 100%;
  padding: clamp(8px, 0.9vh, 10px) 0 clamp(12px, 1.4vh, 18px);
  min-height: 0;
}

.empty-state {
  width: 100%;
  max-width: none;
  margin: auto 0;
  align-self: stretch;
  text-align: left;
  padding: clamp(28px, 4.2vh, 40px) clamp(18px, 2.4vw, 30px);
}

.empty-badge {
  display: inline-flex;
  align-items: center;
  height: 34px;
  border-radius: 999px;
  background: rgba(36, 89, 216, 0.12);
  color: #2459d8;
  font-size: 13px;
  font-weight: 800;
  padding: 0 14px;
}

.empty-title {
  margin-top: 16px;
  font-size: 30px;
  line-height: 1.24;
  font-weight: 900;
  color: #0f172a;
  max-width: 18ch;
}

.empty-desc {
  margin-top: 14px;
  max-width: 72ch;
  font-size: 15px;
  line-height: 1.85;
  color: #5b6472;
}

.empty-grid {
  margin-top: 22px;
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.empty-card {
  border: 1px solid rgba(148, 163, 184, 0.16);
  border-radius: 18px;
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.98) 0%, rgba(248, 250, 252, 0.94) 100%);
  text-align: left;
  padding: 14px;
  cursor: pointer;
  transition: transform .18s ease, box-shadow .18s ease, border-color .18s ease, background .18s ease;
}

.empty-card:hover {
  transform: translateY(-1px);
  border-color: rgba(36, 89, 216, 0.18);
  box-shadow: 0 14px 28px rgba(15, 23, 42, 0.08);
}

.empty-card-title {
  font-size: 15px;
  font-weight: 800;
  color: #0f172a;
}

.empty-card-desc {
  margin-top: 8px;
  font-size: 13px;
  line-height: 1.7;
  color: #64748b;
}

.message-row {
  display: flex;
}

.message-row.user {
  justify-content: flex-end;
}

.message-row.assistant {
  justify-content: flex-start;
}

.message-card {
  max-width: min(92%, 980px);
  border-radius: 22px;
  padding: clamp(12px, 1.2vh, 16px) clamp(14px, 1vw, 18px);
  box-shadow: 0 16px 34px rgba(15, 23, 42, 0.06);
}

.message-card.user {
  background: linear-gradient(135deg, #2459d8 0%, #22d3ee 100%);
  color: #fff;
}

.message-card.assistant {
  width: min(100%, 920px);
  max-width: 100%;
  background: transparent;
  color: #1f2937;
  border: none;
  border-radius: 0;
  box-shadow: none;
  padding: 4px 0 10px;
}

.message-tag {
  display: inline-flex;
  align-items: center;
  height: 28px;
  margin-bottom: 10px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 800;
  padding: 0 10px;
}

.message-tag.is-emotion {
  background: rgba(14, 165, 233, 0.12);
  color: #0369a1;
  border: 1px solid rgba(14, 165, 233, 0.18);
}

.message-tag.is-agent {
  background: rgba(99, 102, 241, 0.12);
  color: #4338ca;
  border: 1px solid rgba(99, 102, 241, 0.18);
}

.message-block {
  border-radius: 18px;
  padding: 12px 14px;
}

.message-block + .message-block {
  margin-top: 12px;
}

.message-block.thinking,
.message-block.result {
  background: transparent;
  border: none;
}

.process-panel {
  position: relative;
  padding: 4px 0 10px 16px;
  background: transparent;
  border: none;
  border-radius: 0;
}

.process-panel::before {
  content: '';
  position: absolute;
  left: 0;
  top: 0;
  bottom: 2px;
  width: 3px;
  border-radius: 999px;
  background: linear-gradient(180deg, rgba(36, 89, 216, 0.7) 0%, rgba(34, 211, 238, 0.55) 100%);
}

.process-head-note {
  font-size: 12px;
  line-height: 1.6;
  color: #94a3b8;
  white-space: normal;
}

.process-elapsed {
  font-size: 12px;
  line-height: 1.6;
  color: #64748b;
  white-space: nowrap;
}

.process-head-main {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
}

.process-head-copy {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 4px;
  min-width: 0;
  margin-top: 8px;
}

.process-brand {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
}

.process-brand-mark {
  position: relative;
  width: 24px;
  height: 24px;
  flex: 0 0 24px;
  border-radius: 50%;
  background: radial-gradient(circle at 35% 35%, rgba(34, 211, 238, 0.92), rgba(36, 89, 216, 0.88));
  box-shadow: 0 10px 22px rgba(36, 89, 216, 0.18);
}

.process-brand-orbit {
  position: absolute;
  inset: 3px;
  border: 1px solid rgba(255, 255, 255, 0.44);
  border-radius: 50%;
}

.process-brand-core {
  position: absolute;
  top: 8px;
  left: 8px;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.96);
}

.process-brand-spark {
  position: absolute;
  width: 4px;
  height: 4px;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.9);
}

.process-brand-spark-a {
  top: 4px;
  right: 5px;
}

.process-brand-spark-b {
  bottom: 5px;
  left: 4px;
}

.process-brand-text {
  font-size: 13px;
  line-height: 1;
  font-weight: 800;
  letter-spacing: 0.04em;
  color: #0f172a;
  white-space: nowrap;
}

.process-body {
  display: flex;
  flex-direction: column;
  gap: 12px;
  margin-top: 12px;
  padding: 10px 0 0 2px;
  border-top: none;
}

.process-placeholder {
  padding: 12px 14px;
  border-radius: 14px;
  background: rgba(255, 255, 255, 0.88);
  border: 1px dashed rgba(148, 163, 184, 0.28);
  color: #64748b;
  font-size: 13px;
  line-height: 1.8;
}

.process-stream {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.process-stream :deep(.process-stream-part + .process-stream-part) {
  margin-top: 2px;
}

.process-stream :deep(.process-stream-tip) {
  padding-left: 2px;
  color: #64748b;
  font-size: 13px;
  line-height: 1.8;
}

.message-block.final {
  padding: 4px 0 0;
  margin-top: 16px;
  overflow: visible;
  background: transparent;
  border: none;
  box-shadow: none;
}

.message-block-title {
  margin-bottom: 8px;
  font-size: 12px;
  font-weight: 800;
  letter-spacing: 0.04em;
  color: #64748b;
}

.answer-block-title {
  color: #1d4ed8;
}

.answer-stage {
  min-height: 56px;
}

.message-action-row {
  margin-top: 16px;
  display: flex;
  align-items: center;
  justify-content: flex-start;
  gap: 12px;
  flex-wrap: wrap;
}

.message-action-logos {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
  justify-content: flex-start;
}

.message-action-logo {
  width: 34px;
  height: 34px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border: 1px solid rgba(148, 163, 184, 0.3);
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.96);
  color: #334155;
  cursor: pointer;
  transition: transform .18s ease, background .18s ease, color .18s ease, box-shadow .18s ease, border-color .18s ease;
}

.message-action-logo:hover {
  transform: translateY(-1px);
  background: rgba(248, 250, 252, 0.98);
  border-color: rgba(59, 130, 246, 0.45);
  color: #1d4ed8;
  box-shadow: 0 8px 18px rgba(36, 89, 216, 0.14);
}

.message-action-logo:disabled {
  opacity: 0.58;
  cursor: not-allowed;
  transform: none;
  box-shadow: none;
}

.message-action-logo.is-active {
  background: rgba(239, 246, 255, 0.96);
  border-color: rgba(37, 99, 235, 0.35);
  color: #1d4ed8;
  box-shadow: 0 0 0 3px rgba(37, 99, 235, 0.12);
}

.message-action-logo-svg {
  width: 16px;
  height: 16px;
  stroke: currentColor;
  fill: none;
  stroke-width: 1.9;
  stroke-linecap: round;
  stroke-linejoin: round;
}

.message-action-logo-svg.spinning {
  animation: actionIconSpin 1s linear infinite;
}

.answer-text {
  color: #1f2937;
  font-size: clamp(15px, 1vw, 16px);
  line-height: 1.95;
  letter-spacing: 0.01em;
  max-width: 820px;
}

.answer-text :deep(.answer-segment-stack) {
  display: flex;
  flex-direction: column;
}

.answer-text :deep(.answer-segment) {
  display: flex;
  flex-direction: column;
}

.answer-text :deep(.answer-segment-divider) {
  display: flex;
  align-items: center;
  gap: 10px;
  margin: 10px 0 18px;
  color: rgba(29, 78, 216, 0.32);
}

.answer-text :deep(.answer-segment-divider-line) {
  flex: 1;
  height: 1px;
  background: linear-gradient(90deg, rgba(148, 163, 184, 0), rgba(148, 163, 184, 0.45), rgba(148, 163, 184, 0));
}

.answer-text :deep(.answer-segment-divider-dot) {
  width: 6px;
  height: 6px;
  border-radius: 999px;
  background: currentColor;
  box-shadow: 0 0 0 4px rgba(29, 78, 216, 0.08);
  flex-shrink: 0;
}

.answer-loading-shell {
  display: flex;
  flex-direction: column;
  gap: 14px;
  max-width: 760px;
  padding: 16px 18px;
  border-radius: 18px;
  background: linear-gradient(180deg, rgba(248, 251, 255, 0.96) 0%, rgba(255, 255, 255, 0.98) 100%);
  border: 1px solid rgba(148, 163, 184, 0.14);
  box-shadow: 0 12px 24px rgba(15, 23, 42, 0.04);
}

.answer-loading-badge {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  width: fit-content;
  min-height: 28px;
  padding: 0 12px;
  border-radius: 999px;
  background: rgba(36, 89, 216, 0.08);
  color: #2459d8;
  font-size: 12px;
  font-weight: 800;
  letter-spacing: 0.03em;
}

.answer-loading-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: currentColor;
  animation: answerPulse 1.15s ease-in-out infinite;
}

.answer-loading-text {
  white-space: nowrap;
}

.answer-loading-lines {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.answer-loading-line {
  position: relative;
  display: block;
  height: 11px;
  overflow: hidden;
  border-radius: 999px;
  background: rgba(148, 163, 184, 0.14);
}

.answer-loading-line::after {
  content: '';
  position: absolute;
  inset: 0;
  transform: translateX(-100%);
  background: linear-gradient(90deg, rgba(255, 255, 255, 0), rgba(36, 89, 216, 0.18), rgba(255, 255, 255, 0));
  animation: answerShimmer 1.5s ease-in-out infinite;
}

.answer-loading-line.is-long {
  width: min(100%, 620px);
}

.answer-loading-line.is-medium {
  width: min(84%, 520px);
}

.answer-loading-line.is-short {
  width: min(62%, 400px);
}

.answer-text :deep(p) {
  margin-bottom: 14px;
}

.process-stream :deep(.tool-result-text) {
  font-size: 13px;
  line-height: 1.85;
}

.process-stream :deep(.search-evidence) {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.process-stream :deep(.search-evidence-meta) {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.process-stream :deep(.search-evidence-chip) {
  display: inline-flex;
  align-items: center;
  padding: 4px 10px;
  border-radius: 999px;
  background: rgba(36, 89, 216, 0.08);
  color: #2459d8;
  font-size: 12px;
  font-weight: 700;
}

.process-stream :deep(.search-evidence-title) {
  font-size: 12px;
  font-weight: 800;
  color: #475569;
  margin-bottom: 8px;
}

.process-stream :deep(.search-evidence-summary) {
  padding: 12px 14px;
  border-radius: 14px;
  background: rgba(255, 255, 255, 0.92);
  border: 1px solid rgba(148, 163, 184, 0.14);
}

.process-stream :deep(.search-evidence-summary p) {
  margin: 0;
}

.process-stream :deep(.search-evidence-sources) {
  display: grid;
  grid-template-columns: minmax(0, 1fr);
  gap: 12px;
}

.process-stream :deep(.search-source-item) {
  display: flex;
  gap: 12px;
  padding: 14px;
  border-radius: 16px;
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.96) 0%, rgba(248, 250, 252, 0.92) 100%);
  border: 1px solid rgba(148, 163, 184, 0.16);
  box-shadow: 0 10px 22px rgba(15, 23, 42, 0.04);
}

.process-stream :deep(.search-source-index) {
  width: 24px;
  height: 24px;
  flex: 0 0 24px;
  border-radius: 10px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, rgba(36, 89, 216, 0.12) 0%, rgba(34, 211, 238, 0.12) 100%);
  color: #2459d8;
  font-size: 12px;
  font-weight: 800;
}

.process-stream :deep(.search-source-body) {
  min-width: 0;
  flex: 1;
}

.process-stream :deep(.search-source-head) {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
}

.process-stream :deep(.search-source-title) {
  font-size: 13px;
  font-weight: 700;
  color: #0f172a;
  line-height: 1.65;
}

.process-stream :deep(.search-source-host) {
  display: inline-flex;
  align-items: center;
  padding: 3px 8px;
  border-radius: 999px;
  background: rgba(15, 23, 42, 0.05);
  color: #475569;
  font-size: 11px;
  font-weight: 700;
  line-height: 1.4;
}

.process-stream :deep(.search-source-snippet) {
  margin: 8px 0 0;
  color: #64748b;
  font-size: 12px;
  line-height: 1.8;
}

.process-stream :deep(.search-source-snippet-list) {
  margin: 0;
  padding-left: 18px;
  list-style: disc;
}

.process-stream :deep(.search-source-snippet-list li) {
  margin: 0 0 6px;
  color: #64748b;
  line-height: 1.8;
}

.process-stream :deep(.search-source-snippet-list li:last-child) {
  margin-bottom: 0;
}

.process-stream :deep(.search-source-actions) {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-top: 10px;
}

.process-stream :deep(.search-source-meta) {
  margin-top: 6px;
  font-size: 12px;
  color: #64748b;
  line-height: 1.6;
}

.process-stream :deep(.search-source-link) {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-height: 30px;
  padding: 0 12px;
  border-radius: 999px;
  background: rgba(36, 89, 216, 0.08);
  border: 1px solid rgba(36, 89, 216, 0.12);
  color: #2459d8;
  font-size: 12px;
  font-weight: 700;
  text-decoration: none;
  word-break: break-all;
}

.process-stream :deep(.search-source-link:hover) {
  background: rgba(36, 89, 216, 0.12);
}

.message-collapse {
  width: 100%;
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 8px;
  border: none;
  background: transparent;
  padding: 0;
  cursor: pointer;
}

.message-collapse .message-block-title {
  margin-bottom: 0;
}

.message-collapse-meta {
  flex: 0 0 auto;
  width: 28px;
  height: 28px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 999px;
  background: rgba(15, 23, 42, 0.04);
  border: 1px solid rgba(148, 163, 184, 0.16);
  color: #94a3b8;
}

.process-toggle-icon {
  width: 9px;
  height: 9px;
  border-right: 2px solid #64748b;
  border-bottom: 2px solid #64748b;
  transform: rotate(-135deg);
  transition: transform 0.18s ease;
}

.process-toggle-icon.collapsed {
  transform: rotate(45deg);
}

.message-text {
  white-space: normal;
  word-break: break-word;
  font-size: clamp(14px, 0.95vw, 15px);
  line-height: 1.8;
}

.message-text.loading {
  opacity: 0.88;
}

.message-text.subtle {
  color: #475569;
}

.user-message-body {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.message-attachments {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.message-file-chip {
  display: inline-flex;
  align-items: center;
  max-width: min(100%, 260px);
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.18);
  border: 1px solid rgba(255, 255, 255, 0.26);
  color: rgba(255, 255, 255, 0.96);
  font-size: 12px;
  font-weight: 700;
  padding: 6px 10px;
}

.message-file-chip-name {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.message-text :deep(p) {
  margin: 0 0 10px;
}

.message-text :deep(p:last-child) {
  margin-bottom: 0;
}

.message-text :deep(h1),
.message-text :deep(h2),
.message-text :deep(h3),
.message-text :deep(h4),
.message-text :deep(h5),
.message-text :deep(h6) {
  margin: 0 0 10px;
  line-height: 1.35;
  font-weight: 800;
}

.message-text :deep(ul) {
  margin: 0 0 10px;
  padding-left: 20px;
}

.message-text :deep(ol) {
  margin: 0 0 10px;
  padding-left: 22px;
}

.message-text :deep(table) {
  width: 100%;
  border-collapse: collapse;
  margin: 12px 0;
  font-size: 13px;
  line-height: 1.7;
  overflow: hidden;
  border-radius: 14px;
  border: 1px solid rgba(148, 163, 184, 0.18);
}

.message-text :deep(th),
.message-text :deep(td) {
  padding: 10px 12px;
  text-align: left;
  vertical-align: top;
  border-bottom: 1px solid rgba(148, 163, 184, 0.14);
}

.message-text :deep(th) {
  background: rgba(248, 250, 252, 0.92);
  color: #334155;
  font-weight: 800;
}

.message-text :deep(tr:last-child td) {
  border-bottom: none;
}

.message-text :deep(li) {
  margin: 4px 0;
}

.message-text :deep(li > p) {
  margin-bottom: 8px;
}

.message-text :deep(li > p:last-child) {
  margin-bottom: 0;
}

.message-text :deep(li > ul),
.message-text :deep(li > ol) {
  margin-top: 8px;
  margin-bottom: 0;
}

.message-text :deep(blockquote) {
  margin: 0 0 12px;
  padding: 12px 14px;
  border-left: 4px solid rgba(36, 89, 216, 0.24);
  border-radius: 0 14px 14px 0;
  background: rgba(248, 250, 252, 0.96);
  color: #475569;
}

.message-text :deep(blockquote p) {
  margin-bottom: 8px;
}

.message-text :deep(blockquote p:last-child) {
  margin-bottom: 0;
}

.message-text :deep(hr) {
  height: 1px;
  border: none;
  margin: 16px 0;
  background: linear-gradient(90deg, rgba(148, 163, 184, 0), rgba(148, 163, 184, 0.3), rgba(148, 163, 184, 0));
}

.message-text :deep(a) {
  color: #2459d8;
  text-decoration: underline;
  word-break: break-all;
}

.message-text :deep(a:hover) {
  filter: brightness(1.05);
}

.message-text :deep(code) {
  background: rgba(15, 23, 42, 0.06);
  padding: 2px 6px;
  border-radius: 6px;
  font-size: 0.94em;
}

.message-text :deep(strong) {
  color: #0f172a;
  font-weight: 800;
  background: linear-gradient(180deg, rgba(36, 89, 216, 0) 55%, rgba(36, 89, 216, 0.12) 55%);
}

.message-text :deep(del) {
  color: #94a3b8;
}

.message-text :deep(pre) {
  margin: 10px 0;
  border-radius: 14px;
  background: #f8fafc;
  border: 1px solid rgba(148, 163, 184, 0.16);
  padding: 12px 14px;
  overflow: auto;
}

.message-text :deep(pre code) {
  background: transparent;
  padding: 0;
  border-radius: 0;
}

.answer-text :deep(h1),
.answer-text :deep(h2),
.answer-text :deep(h3) {
  color: #0f172a;
}

.answer-text :deep(h1) {
  font-size: 22px;
  margin-bottom: 16px;
}

.answer-text :deep(h2) {
  margin-top: 18px;
  padding-top: 14px;
  border-top: 1px solid rgba(148, 163, 184, 0.14);
  font-size: 19px;
}

.answer-text :deep(h2:first-child) {
  margin-top: 0;
  padding-top: 0;
  border-top: none;
}

.answer-text :deep(h3) {
  font-size: 17px;
  margin-top: 8px;
  margin-bottom: 14px;
}

.answer-text :deep(h4) {
  font-size: 16px;
  margin-top: 18px;
  margin-bottom: 10px;
  color: #1e293b;
  font-weight: 800;
}

.answer-text :deep(ol) {
  margin: 0;
  padding-left: 24px;
}

.answer-text :deep(ol li) {
  margin: 0 0 12px;
  padding-left: 4px;
}

.answer-text :deep(ul) {
  margin: 10px 0 0;
  padding-left: 20px;
}

.answer-text :deep(ul li) {
  margin: 8px 0;
}

.answer-text :deep(code) {
  background: rgba(36, 89, 216, 0.08);
  color: #1d4ed8;
}

.answer-text :deep(strong) {
  color: #0f172a;
  font-weight: 800;
  background: linear-gradient(180deg, rgba(36, 89, 216, 0) 55%, rgba(36, 89, 216, 0.12) 55%);
}

.answer-text :deep(pre) {
  background: linear-gradient(180deg, #f8fbff 0%, #f3f7fd 100%);
}

@keyframes answerShimmer {
  0% {
    transform: translateX(-100%);
  }
  100% {
    transform: translateX(100%);
  }
}

@keyframes answerPulse {
  0%, 100% {
    opacity: 0.35;
    transform: scale(0.88);
  }
  50% {
    opacity: 1;
    transform: scale(1);
  }
}

@keyframes actionIconSpin {
  0% {
    transform: rotate(0deg);
  }
  100% {
    transform: rotate(360deg);
  }
}

.process-stream :deep(h3) {
  font-size: 14px;
  margin-top: 18px;
  margin-bottom: 10px;
  color: #334155;
}

.process-stream :deep(h3:first-child) {
  margin-top: 0;
}

.process-stream :deep(p) {
  margin-bottom: 12px;
}

.composer-card {
  margin-top: 8px;
  border-radius: 22px;
  background: rgba(255, 255, 255, 0.94);
  border: 1px solid rgba(148, 163, 184, 0.18);
  box-shadow: 0 10px 24px rgba(15, 23, 42, 0.06);
  padding: 10px 12px 12px;
  width: 100%;
  transition: border-color .18s ease, box-shadow .18s ease;
}

.composer-card:focus-within {
  border-color: rgba(36, 89, 216, 0.22);
  box-shadow: 0 12px 28px rgba(36, 89, 216, 0.08);
}

.composer-surface {
  width: 100%;
}

.composer-surface {
  border-radius: 16px;
  background: #fff;
  border: 1px solid rgba(148, 163, 184, 0.14);
  padding: 10px 10px 8px;
}

.file-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 8px;
}

.file-chip {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  border-radius: 999px;
  background: rgba(248, 250, 252, 0.95);
  border: 1px solid rgba(148, 163, 184, 0.16);
  color: #334155;
  font-size: 12px;
  font-weight: 700;
  padding: 6px 10px;
}

.file-chip-name {
  max-width: 180px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.file-chip-remove {
  width: 18px;
  height: 18px;
  border: none;
  border-radius: 999px;
  background: rgba(148, 163, 184, 0.14);
  color: #64748b;
  cursor: pointer;
  line-height: 1;
  padding: 0;
}

.composer-input {
  width: 100%;
  min-height: clamp(84px, 11vh, 110px);
  max-height: clamp(160px, 24vh, 220px);
  resize: none;
  border: none;
  background: transparent;
  outline: none;
  color: #0f172a;
  caret-color: #2459d8;
  font-family: var(--font-sans);
  font-size: clamp(14px, 0.96vw, 15px);
  font-weight: 400;
  line-height: 1.75;
  letter-spacing: 0.01em;
}

.composer-input::placeholder {
  color: #94a3b8;
  font-weight: 400;
}

.composer-bottom {
  display: flex;
  justify-content: space-between;
  gap: 10px;
  align-items: flex-end;
  margin-top: 8px;
  padding-top: 8px;
  border-top: 1px solid rgba(148, 163, 184, 0.12);
}

.composer-meta {
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 4px;
  flex: 1;
}

.composer-actions {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 8px;
  flex-wrap: wrap;
  flex: 0 0 auto;
}

.composer-tip {
  font-size: 12px;
  line-height: 1.6;
  color: #64748b;
}

.composer-shortcut {
  font-size: 11px;
  line-height: 1.5;
  color: #94a3b8;
}

.upload-btn,
.tool-toggle-btn {
  border: 1px solid rgba(148, 163, 184, 0.18);
  border-radius: 12px;
  background: rgba(248, 250, 252, 0.98);
  color: #334155;
  font-size: 13px;
  font-weight: 700;
  padding: 9px 12px;
  cursor: pointer;
  transition: background .18s ease, border-color .18s ease, color .18s ease;
}

.upload-btn:hover,
.tool-toggle-btn:hover {
  background: rgba(241, 245, 249, 0.98);
  border-color: rgba(36, 89, 216, 0.16);
}

.tool-toggle-btn.active {
  border-color: rgba(36, 89, 216, 0.16);
  background: rgba(36, 89, 216, 0.08);
  color: #1d4ed8;
}

.send-btn {
  flex: 0 0 auto;
  width: 48px;
  height: 48px;
  border: none;
  border-radius: 999px;
  background:
    radial-gradient(circle at 30% 30%, rgba(255, 255, 255, 0.34), transparent 46%),
    linear-gradient(135deg, #2459d8 0%, #1d4ed8 52%, #0f3aa8 100%);
  color: #fff;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 0;
  box-shadow: 0 14px 28px rgba(29, 78, 216, 0.28);
  cursor: pointer;
  transition: background .18s ease, box-shadow .18s ease, opacity .18s ease, transform .18s ease;
}

.send-btn:not(:disabled):hover {
  box-shadow: 0 18px 34px rgba(29, 78, 216, 0.34);
  transform: translateY(-1px);
}

.send-btn.sending {
  background:
    radial-gradient(circle at 30% 30%, rgba(255, 255, 255, 0.24), transparent 44%),
    linear-gradient(135deg, #f97316 0%, #ef4444 55%, #b91c1c 100%);
  box-shadow: 0 14px 28px rgba(239, 68, 68, 0.3);
}

.send-btn:disabled {
  opacity: 0.55;
  cursor: not-allowed;
  box-shadow: none;
  transform: none;
}

.send-btn-icon {
  width: 20px;
  height: 20px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
}

.send-btn-icon svg {
  width: 20px;
  height: 20px;
  fill: none;
  stroke: currentColor;
  stroke-width: 1.9;
  stroke-linecap: round;
  stroke-linejoin: round;
}

.send-btn-send-svg {
  transform: translateX(1px);
}

.send-btn-stop-svg rect {
  fill: currentColor;
  stroke: none;
}

.modal-mask {
  position: fixed;
  inset: 0;
  z-index: 20;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 16px;
  background: rgba(15, 23, 42, 0.28);
  backdrop-filter: blur(8px);
}

.modal-card {
  width: min(440px, 100%);
  border-radius: 24px;
  background: rgba(255, 255, 255, 0.96);
  box-shadow: 0 26px 60px rgba(15, 23, 42, 0.16);
  padding: 22px;
}

.modal-title {
  font-size: 24px;
  font-weight: 900;
  color: #0f172a;
  margin-bottom: 12px;
}

.modal-text {
  font-size: 14px;
  line-height: 1.8;
  color: #5b6472;
}

.modal-input {
  width: 100%;
  margin-top: 14px;
  border: 1px solid rgba(148, 163, 184, 0.24);
  border-radius: 14px;
  background: rgba(248, 250, 252, 0.96);
  color: #0f172a;
  font-size: 14px;
  line-height: 1.5;
  padding: 12px 14px;
  outline: none;
  transition: border-color .18s ease, box-shadow .18s ease, background .18s ease;
}

.modal-input:focus {
  border-color: rgba(36, 89, 216, 0.4);
  box-shadow: 0 0 0 4px rgba(36, 89, 216, 0.12);
  background: rgba(255, 255, 255, 0.98);
}

.modal-hint {
  margin-top: 8px;
  font-size: 12px;
  color: #94a3b8;
}

.modal-actions {
  display: flex;
  gap: 12px;
  justify-content: flex-end;
  margin-top: 18px;
}

.secondary-btn,
.danger-btn {
  border-radius: 12px;
  font-size: 14px;
  font-weight: 800;
  padding: 11px 16px;
}

.secondary-btn {
  background: rgba(241, 245, 249, 0.96);
  color: #1f2937;
}

.danger-btn {
  background: #ef4444;
  color: #fff;
}

.modal-primary-btn {
  min-width: 104px;
  box-shadow: none;
}

.secondary-btn:disabled,
.danger-btn:disabled,
.modal-primary-btn:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}

.toast {
  position: fixed;
  left: 50%;
  bottom: 26px;
  transform: translateX(-50%);
  z-index: 24;
  border-radius: 999px;
  background: rgba(15, 23, 42, 0.9);
  color: #fff;
  font-size: 13px;
  font-weight: 700;
  padding: 10px 16px;
  box-shadow: 0 16px 28px rgba(15, 23, 42, 0.2);
}

@media (max-width: 1120px) {
  .assistant-page {
    overflow-y: auto;
  }

  .assistant-shell {
    height: auto;
    grid-template-columns: 1fr;
  }

  .side-panel {
    padding: 18px;
  }

  .chat-panel {
    min-height: auto;
    height: auto;
  }

  .empty-state {
    text-align: center;
    align-self: center;
  }

  .empty-title,
  .empty-desc {
    max-width: none;
  }

  .empty-grid {
    grid-template-columns: 1fr;
  }
}

@media (min-width: 1121px) and (max-height: 860px) {
  .assistant-shell {
    gap: 16px;
    padding: 14px;
  }

  .side-panel {
    padding: 14px 12px;
    gap: 12px;
  }

  .history-card {
    padding: 12px;
  }

  .chat-panel {
    padding: 14px;
  }

  .message-card {
    max-width: 86%;
  }

  .composer-input {
    min-height: 78px;
  }
}

@media (max-width: 760px) {
  .assistant-shell {
    padding: 14px;
    gap: 14px;
  }

  .side-panel,
  .chat-panel {
    border-radius: 22px;
  }

  .composer-bottom {
    flex-direction: column;
    align-items: stretch;
  }

  .composer-card {
    border-radius: 20px;
  }

  .composer-surface {
    border-radius: 14px;
    padding: 10px 10px 8px;
  }

  .composer-input {
    min-height: 92px;
  }

  .composer-actions {
    justify-content: flex-start;
  }

  .message-card {
    max-width: 100%;
  }

  .message-action-row {
    flex-direction: column;
    align-items: flex-start;
  }

  .message-action-logos {
    width: 100%;
    justify-content: flex-start;
  }

  .empty-title {
    font-size: 24px;
  }

  .history-list,
  .empty-grid {
    grid-template-columns: 1fr;
  }
}
</style>
