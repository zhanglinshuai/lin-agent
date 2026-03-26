<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import axios from 'axios'
import AuthForm from '@/components/AuthForm.vue'
import { openUnifiedSSE } from '@/services/sse'
import { clearAuthSession, emitAuthEvent } from '@/services/auth'

const router = useRouter()
const route = useRoute()
const UNIFIED_MODE = 'auto'

const input = ref('')
const chatId = ref('')
const messages = ref([])
const textareaRef = ref(null)
const messagesPanelRef = ref(null)
const showLoginModal = ref(false)
const showAvatarMenu = ref(false)
const showLogoutConfirm = ref(false)
const loggedIn = ref(false)
const topAvatarUrl = ref('')
const userName = ref('')
const history = ref([])
const loadingHistory = ref(false)
const activeConversationId = ref('')
const toastText = ref('')
const toastVisible = ref(false)
const uploadedFiles = ref([])
const uploadingFile = ref(false)
const uploadInputRef = ref(null)
const allowWebSearch = ref(false)

let currentStream = null
let flushTimer = null
let streamBuf = new Map()

const DEFAULT_AVATAR = `data:image/svg+xml;utf8,<svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 64 64'><rect fill='%23f4f5f7' width='64' height='64'/><circle cx='32' cy='24' r='12' fill='%239aa0a6'/><rect x='12' y='40' width='40' height='14' rx='7' fill='%239aa0a6'/></svg>`
const ASSISTANT_CACHE_KEY = 'assistant_conversation_cache_v1'
const ASSISTANT_ACTIVE_KEY = 'assistant_active_conversation_v1'

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
const composerTipText = computed(() => {
  if (allowWebSearch.value) {
    return '已开启联网搜索，涉及最新资料、外部信息或攻略时会优先调用搜索工具。'
  }
  return '想倾诉、想整理、想计划、想问清楚，都可以从这里开始。'
})

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

function syncAuthState() {
  try {
    const uid = localStorage.getItem('user_id') || ''
    const name = localStorage.getItem('user_name') || ''
    const avatar = localStorage.getItem('user_avatar') || ''
    loggedIn.value = !!uid
    userName.value = String(name || '')
    topAvatarUrl.value = normalizeAvatarUrl(avatar)
  } catch (e) {
    loggedIn.value = false
    userName.value = ''
    topAvatarUrl.value = DEFAULT_AVATAR
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
    finalContent: String(item?.finalContent || ''),
    attachedFiles: Array.isArray(item?.attachedFiles) ? item.attachedFiles.map(file => String(file || '')) : [],
    loading: !!item?.loading,
    complete: !!item?.complete,
    thinkingCollapsed: item?.thinkingCollapsed !== false,
    routeMode: String(item?.routeMode || ''),
    reason: String(item?.reason || ''),
    sourcePrompt: String(item?.sourcePrompt || ''),
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

function restoreActiveConversationFromCache() {
  const conversationId = readActiveConversationId()
  if (!conversationId) return false
  const cachedMessages = getCachedConversation(conversationId)
  if (!cachedMessages || !cachedMessages.length) return false
  activeConversationId.value = conversationId
  chatId.value = conversationId
  messages.value = cachedMessages
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
}

function renderInline(s) {
  const formatUrlText = (u) => {
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

  const markdownLinks = []
  const withPlaceholders = s.replace(/\[([^\]]+)\]\((https?:\/\/[^\s)]+)\)/g, (m, t, u) => {
    const token = `@@MDLINK${markdownLinks.length}@@`
    markdownLinks.push(`<a href="${u}" target="_blank" rel="noopener noreferrer" title="${u}">${t || formatUrlText(u)}</a>`)
    return token
  })

  let rendered = withPlaceholders
      .replace(/(https?:\/\/[^\s)]+)/g, (m) => `<a href="${m}" target="_blank" rel="noopener noreferrer" title="${m}">${formatUrlText(m)}</a>`)
      .replace(/\*\*([^*]+)\*\*/g, '<strong>$1</strong>')
      .replace(/\*([^*]+)\*/g, '<em>$1</em>')
      .replace(/_([^_]+)_/g, '<em>$1</em>')
      .replace(/`([^`]+)`/g, '<code>$1</code>')
  markdownLinks.forEach((html, index) => {
    rendered = rendered.replace(`@@MDLINK${index}@@`, html)
  })
  return rendered
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
  let text = decodeJsonWrappedText(String(value || '').trim())
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

  return text.trim()
}

function renderMarkdown(md) {
  const src = normalizeReadableText(md)
  const text = escapeHtml(src)
  const lines = text.split(/\r?\n/)
  let html = ''
  let inCode = false
  let codeBuf = []
  let listType = ''
  let paragraphBuf = []
  let quoteBuf = []

  const flushParagraph = () => {
    if (!paragraphBuf.length) return
    html += '<p>' + paragraphBuf.map(line => renderInline(line)).join('<br/>') + '</p>'
    paragraphBuf = []
  }

  const closeList = () => {
    if (!listType) return
    html += listType === 'ol' ? '</ol>' : '</ul>'
    listType = ''
  }

  const flushQuote = () => {
    if (!quoteBuf.length) return
    html += '<blockquote>' + quoteBuf.map(line => `<p>${renderInline(line)}</p>`).join('') + '</blockquote>'
    quoteBuf = []
  }

  for (let i = 0; i < lines.length; i++) {
    const line = lines[i]
    if (/^```/.test(line)) {
      flushParagraph()
      closeList()
      flushQuote()
      if (!inCode) {
        inCode = true
        codeBuf = []
      } else {
        inCode = false
        html += '<pre><code>' + codeBuf.join('\n') + '</code></pre>'
        codeBuf = []
      }
      continue
    }
    if (inCode) {
      codeBuf.push(line)
      continue
    }
    if (line.trim() === '') {
      flushParagraph()
      closeList()
      flushQuote()
      continue
    }
    const title = line.match(/^(#{1,6})\s+(.*)$/)
    if (title) {
      flushParagraph()
      closeList()
      flushQuote()
      const level = title[1].length
      html += `<h${level}>` + renderInline(title[2]) + `</h${level}>`
      continue
    }
    if (/^(-{3,}|\*{3,}|_{3,})$/.test(line.trim())) {
      flushParagraph()
      closeList()
      flushQuote()
      html += '<hr/>'
      continue
    }
    const quote = line.match(/^>\s?(.*)$/)
    if (quote) {
      flushParagraph()
      closeList()
      quoteBuf.push(quote[1] || '')
      continue
    }
    if (quoteBuf.length) {
      flushQuote()
    }
    const orderedItem = line.match(/^\d+\.\s+(.*)$/)
    if (orderedItem) {
      flushParagraph()
      if (listType !== 'ol') {
        closeList()
        html += '<ol>'
        listType = 'ol'
      }
      html += '<li>' + renderInline(orderedItem[1]) + '</li>'
      continue
    }
    const unorderedItem = line.match(/^[-*]\s+(.*)$/)
    if (unorderedItem) {
      flushParagraph()
      if (listType !== 'ul') {
        closeList()
        html += '<ul>'
        listType = 'ul'
      }
      html += '<li>' + renderInline(unorderedItem[1]) + '</li>'
      continue
    }
    if (listType) {
      closeList()
    }
    paragraphBuf.push(line)
  }

  flushParagraph()
  closeList()
  flushQuote()
  if (inCode) html += '<pre><code>' + codeBuf.join('\n') + '</code></pre>'
  return html
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

function formatNowTime() {
  return formatSummaryTime(new Date())
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
    if (el) el.scrollTop = el.scrollHeight
  })
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

function appendBlockText(origin, chunk) {
  const current = String(origin || '')
  const next = String(chunk || '')
  if (!current) return next
  if (!next) return current
  return current + next
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

function finishAssistantMessage(idx, fallbackText = '') {
  flushStreamBuffer()
  const msg = messages.value[idx]
  if (!msg) return
  if (!msg.finalContent && fallbackText) {
    msg.finalContent = fallbackText
  }
  msg.loading = false
  msg.complete = true
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
  const title = trimText(msg?.sourcePrompt || '', 18) || '新对话'
  const summary = trimText(msg?.finalContent || msg?.resultContent || msg?.thinkingContent || '', 72)
  const tag = normalizeRouteMode(msg?.routeMode || '')
  const nextItem = {
    conversationId,
    title,
    tag,
    summary,
    lastTime: formatNowTime(),
    messageCount: 0,
  }
  const rest = history.value.filter(item => String(item?.conversationId || '') !== conversationId)
  const current = history.value.find(item => String(item?.conversationId || '') === conversationId)
  if (current) {
    nextItem.title = current.title || nextItem.title
    nextItem.messageCount = Math.max(Number(current.messageCount || 0) + 2, 2)
  } else {
    nextItem.messageCount = 2
  }
  history.value = [nextItem, ...rest]
}

function handleUnifiedEvent(payload, idx) {
  const msg = messages.value[idx]
  if (!msg) return
  if (!payload || typeof payload !== 'object') {
    queueChunk(idx, String(payload || ''), 'finalContent')
    return
  }
  if (payload.type === 'route') {
    msg.routeMode = normalizeRouteMode(payload.tag || payload.mode || msg.routeMode || '', payload.label)
    msg.reason = String(payload.reason || '')
    return
  }
  if (payload.type === 'thinking') {
    msg.routeMode = normalizeRouteMode(payload.tag || payload.mode || msg.routeMode || '', payload.label)
    if (!msg.reason && payload.reason) {
      msg.reason = String(payload.reason)
    }
    queueChunk(idx, String(payload.content || ''), 'thinkingContent')
    return
  }
  if (payload.type === 'result') {
    queueChunk(idx, String(payload.content || ''), 'resultContent')
    return
  }
  if (payload.type === 'final') {
    msg.routeMode = normalizeRouteMode(payload.tag || payload.mode || msg.routeMode || '', payload.label)
    if (!msg.reason && payload.reason) {
      msg.reason = String(payload.reason)
    }
    queueChunk(idx, String(payload.content || ''), 'finalContent')
    return
  }
  if (payload.type === 'error') {
    msg.routeMode = normalizeRouteMode(payload.tag || payload.mode || msg.routeMode || '', payload.label)
    queueChunk(idx, String(payload.content || ''), 'finalContent')
    finishAssistantMessage(idx)
    return
  }
  if (payload.type === 'done') {
    finishAssistantMessage(idx)
  }
}

function toggleThinking(index) {
  const msg = messages.value[index]
  if (!msg) return
  msg.thinkingCollapsed = !msg.thinkingCollapsed
  persistActiveConversationCache()
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
    const fileName = String(res?.data?.data || '')
    if (fileName) {
      uploadedFiles.value = Array.from(new Set([...uploadedFiles.value, fileName]))
      showToast('文件上传成功')
    } else {
      showToast('文件上传失败')
    }
  } catch (error) {
    showToast(error?.response?.data?.message || '文件上传失败')
  } finally {
    uploadingFile.value = false
    if (e?.target) e.target.value = ''
  }
}

function removeUploadedFile(fileName) {
  uploadedFiles.value = uploadedFiles.value.filter(item => item !== fileName)
}

function send() {
  const text = input.value.trim()
  if (!text) return
  const sendingFiles = [...uploadedFiles.value]
  const sendingAllowWebSearch = allowWebSearch.value
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
    sourcePrompt: text,
    loading: true,
    complete: false,
    thinkingCollapsed: true,
    routeMode: UNIFIED_MODE,
    reason: '',
  })
  persistActiveConversationCache()
  const idx = messages.value.length - 1
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
      sendingFiles,
      (payload) => handleUnifiedEvent(payload, idx),
      () => finishAssistantMessage(idx),
      () => finishAssistantMessage(idx, '当前请求中断，请稍后再试。')
  )
  uploadedFiles.value = []
  scrollToBottom()
}

function handleEnter(e) {
  if (!e.shiftKey) {
    e.preventDefault()
    send()
  }
}

function startConversation() {
  currentStream?.close()
  flushStreamBuffer()
  messages.value = []
  chatId.value = genChatId()
  activeConversationId.value = chatId.value
  saveActiveConversationId(activeConversationId.value)
  uploadedFiles.value = []
  allowWebSearch.value = false
  input.value = ''
  resizeTextarea()
  focusComposer()
  persistActiveConversationCache()
}

function handleAuthSuccess(payload) {
  showLoginModal.value = false
  syncAuthState()
  fetchUserProfile()
  fetchConversationSummaries()
  showToast(payload?.registered ? '注册并登录成功' : '登录成功')
}

function toggleAvatarMenu() {
  showAvatarMenu.value = !showAvatarMenu.value
}

function openProfileCenter() {
  showAvatarMenu.value = false
  fetchUserProfile()
  showToast('已同步当前账号资料')
}

function openLogin() {
  showAvatarMenu.value = false
  showLoginModal.value = true
}

function openLogoutConfirm() {
  showAvatarMenu.value = false
  showLogoutConfirm.value = true
}

async function confirmLogout() {
  try { await axios.post('/api/user/exit') } catch (e) {}
  clearAuthSession()
  emitAuthEvent('auth:logout')
  showLogoutConfirm.value = false
  showAvatarMenu.value = false
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

function openProfileOrLogin() {
  if (loggedIn.value) {
    openProfileCenter()
    return
  }
  openLogin()
}

async function fetchUserProfile() {
  let uid = ''
  try { uid = localStorage.getItem('user_id') || '' } catch (e) { uid = '' }
  if (!uid) return
  let res
  try {
    res = await axios.get('/api/user/getUserInfo', { params: { userId: uid } })
  } catch (e) {
    return
  }
  const raw = res.data && (res.data.data || res.data)
  if (raw && typeof raw === 'object') {
    userName.value = String(raw.userName || userName.value || '')
    topAvatarUrl.value = normalizeAvatarUrl(raw.userAvatar || '')
    try {
      localStorage.setItem('user_name', userName.value)
      localStorage.setItem('user_avatar', topAvatarUrl.value)
    } catch (e) {
    }
  }
}

async function fetchConversationSummaries() {
  let username = ''
  try { username = localStorage.getItem('user_name') || '' } catch (e) { username = '' }
  if (!username) {
    history.value = []
    return
  }
  loadingHistory.value = true
  try {
    const res = await axios.get('/api/chat_memory/getConversationSummaries', { params: { username } })
    const raw = Array.isArray(res.data) ? res.data : (Array.isArray(res.data?.data) ? res.data.data : [])
    history.value = raw.map(item => ({
      conversationId: String(item?.conversationId || ''),
      title: String(item?.title || '新对话'),
      tag: normalizeRouteMode(item?.tag || ''),
      summary: String(item?.summary || item?.lastMessage || ''),
      lastTime: formatSummaryTime(item?.lastTime),
      messageCount: Number(item?.messageCount || 0),
    }))
    if (activeConversationId.value && messages.value.length > 0 && String(chatId.value) === String(activeConversationId.value)) {
      const lastAssistant = [...messages.value].reverse().find(item => item?.role === 'assistant') || messages.value[messages.value.length - 1]
      syncActiveConversationSummary(lastAssistant)
    }
    if (!activeConversationId.value && history.value.length > 0) {
      await openHistory(history.value[0].conversationId)
    }
  } catch (e) {
    history.value = []
  } finally {
    loadingHistory.value = false
  }
}

async function openHistory(conversationId) {
  if (!conversationId) return
  saveActiveConversationId(conversationId)
  const cachedMessages = getCachedConversation(conversationId)
  if (cachedMessages && cachedMessages.length > 0) {
    activeConversationId.value = String(conversationId)
    chatId.value = activeConversationId.value
    messages.value = cachedMessages
    scrollToBottom()
    return
  }
  let res
  try {
    res = await axios.get('/api/chat_memory/getConversation', { params: { conversationId } })
  } catch (e) {
    showToast((e?.response?.data?.message) || '会话加载失败')
    return
  }
  const raw = Array.isArray(res.data) ? res.data : (Array.isArray(res.data?.data) ? res.data.data : [])
  activeConversationId.value = String(conversationId)
  chatId.value = activeConversationId.value
  let currentTag = ''
  messages.value = raw.map(item => {
    const type = String(item?.messageType ?? item?.message_type ?? '')
    const metadata = parseMetadata(item?.metadata)
    const metadataTag = normalizeRouteMode(metadata?.tag || metadata?.mode || '')
    if (metadataTag) {
      currentTag = metadataTag
    }
    return {
      role: type.toUpperCase() === 'USER' ? 'user' : 'assistant',
      content: String(item?.content || ''),
      thinkingContent: '',
      finalContent: type.toUpperCase() === 'USER' ? '' : String(item?.content || ''),
      attachedFiles: Array.isArray(metadata?.uploadedFiles) ? metadata.uploadedFiles.map(file => String(file || '')) : [],
      loading: false,
      complete: true,
      thinkingCollapsed: true,
      routeMode: type.toUpperCase() === 'USER' ? metadataTag : (metadataTag || currentTag),
    }
  })
  scrollToBottom()
}

function onAuthChange() {
  syncAuthState()
  if (loggedIn.value) {
    fetchUserProfile()
    fetchConversationSummaries()
  } else {
    history.value = []
  }
}

onMounted(() => {
  chatId.value = genChatId()
  syncAuthState()
  resizeTextarea()
  applyPrefillPrompt(route.query.prompt)
  restoreActiveConversationFromCache()
  if (loggedIn.value) {
    fetchUserProfile()
    fetchConversationSummaries()
  }
  window.addEventListener('auth:login', onAuthChange)
  window.addEventListener('auth:logout', onAuthChange)
  window.addEventListener('storage', onAuthChange)
})

onBeforeUnmount(() => {
  currentStream?.close()
  flushStreamBuffer()
  window.removeEventListener('auth:login', onAuthChange)
  window.removeEventListener('auth:logout', onAuthChange)
  window.removeEventListener('storage', onAuthChange)
})

watch(
    () => route.query.prompt,
    (value) => {
      applyPrefillPrompt(value)
    }
)
</script>

<template>
  <div class="assistant-page">
    <div class="assistant-bg">
      <div class="bg-grid"></div>
      <div class="bg-orb orb-left"></div>
      <div class="bg-orb orb-right"></div>
    </div>

    <div class="assistant-shell">
      <aside class="side-panel">
        <div class="brand-block">
          <div class="brand-badge">智能协同助理</div>
          <h1 class="brand-title">把你的想法发给我，我们一起慢慢理清楚</h1>
          <p class="brand-desc">你可以直接倾诉情绪、聊聊关系、整理计划，或者把一团乱麻的念头先说出来。</p>
        </div>

        <button class="primary-action" @click="startConversation">开启新对话</button>

        <div class="history-section">
          <div class="section-kicker">会话列表</div>
          <div class="section-title">当前用户的全部会话</div>
          <div v-if="loadingHistory" class="history-empty">正在加载会话...</div>
          <div v-else-if="!history.length" class="history-empty">还没有会话，先开始一段新的对话吧。</div>
          <div v-else class="history-list">
            <button
                v-for="item in history"
                :key="item.conversationId"
                class="history-card"
                :class="{ active: item.conversationId === activeConversationId }"
                @click="openHistory(item.conversationId)"
            >
              <div class="history-card-head">
                <div class="history-card-title-wrap">
                  <span class="history-card-title">{{ item.title }}</span>
                  <span
                      v-if="getRouteTagText(item.tag)"
                      class="history-card-tag"
                      :class="getRouteTagClass(item.tag)"
                  >
                    {{ getRouteTagText(item.tag) }}
                  </span>
                </div>
                <span class="history-card-time">{{ item.lastTime }}</span>
              </div>
              <div class="history-card-desc">{{ item.summary || '这段会话还没有可展示的摘要。' }}</div>
              <div class="history-card-meta">共 {{ item.messageCount || 0 }} 条消息</div>
            </button>
          </div>
        </div>

        <div class="side-actions">
          <button class="side-link" @click="goHome">返回首页</button>
          <button class="side-link" @click="openProfileOrLogin">{{ loggedIn ? '打开个人中心' : '登录 / 注册' }}</button>
        </div>

        <div class="profile-card">
          <div class="profile-avatar">
            <img :src="topAvatarUrl || DEFAULT_AVATAR" alt="avatar" />
          </div>
          <div class="profile-meta">
            <div class="profile-name">{{ loggedIn ? (userName || '已登录用户') : '未登录' }}</div>
            <div class="profile-text">{{ loggedIn ? '你的资料和对话能力已经就绪' : '登录后可同步资料、历史和个性化设置' }}</div>
          </div>
        </div>
      </aside>

      <main class="chat-panel">
        <header class="chat-header">
          <div>
            <div class="header-kicker">在线对话</div>
            <h2 class="header-title">开始对话</h2>
            <p class="header-desc">把你现在最想说的内容发出来。</p>
          </div>
          <div class="header-tools">
            <div class="avatar-wrap">
              <button class="avatar-btn" @click.stop="toggleAvatarMenu">
                <img :src="topAvatarUrl || DEFAULT_AVATAR" alt="avatar" />
              </button>
              <div v-if="showAvatarMenu" class="avatar-menu">
                <button v-if="loggedIn" class="avatar-menu-item" @click="openProfileCenter">个人中心</button>
                <button v-if="loggedIn" class="avatar-menu-item" @click="openLogoutConfirm">退出登录</button>
                <button v-else class="avatar-menu-item" @click="openLogin">登录 / 注册</button>
              </div>
            </div>
          </div>
        </header>

        <section ref="messagesPanelRef" class="messages-panel" @click="showAvatarMenu = false">
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
              <div v-if="msg.role === 'assistant' && msg.thinkingContent" class="message-block thinking">
                <button class="message-collapse" type="button" @click="toggleThinking(index)">
                  <span class="message-block-title">思考过程</span>
                  <span class="message-collapse-meta">
                    {{ msg.thinkingCollapsed ? '展开' : '收起' }}
                  </span>
                </button>
                <div v-if="!msg.thinkingCollapsed" class="message-text subtle" v-html="renderMarkdown(msg.thinkingContent)"></div>
              </div>
              <div v-if="msg.role === 'assistant'" class="message-block final">
                <div class="answer-shell">
                  <div class="answer-head">
                    <div class="answer-badge">答复</div>
                    <div class="answer-head-copy">
                      <div class="message-block-title final-title">最终答复</div>
                      <div class="answer-head-note">已经整理成更清晰、便于继续操作的结构</div>
                    </div>
                  </div>
                  <div class="answer-body">
                    <div v-if="msg.resultContent" class="answer-sidecar">
                      <div class="answer-sidecar-title">工具执行结果</div>
                      <div class="message-text subtle" v-html="renderMarkdown(msg.resultContent)"></div>
                    </div>
                    <div
                        class="message-text answer-text"
                        :class="{ loading: msg.loading && !msg.finalContent }"
                        v-html="renderMarkdown(msg.finalContent || (msg.loading ? '智能助手正在生成最终答复...' : ''))"
                    ></div>
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
                    v-html="renderMarkdown(msg.content)"
                ></div>
              </div>
            </div>
          </div>
        </section>

        <section class="composer-card">
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
              placeholder="把你现在最想说的内容发出来。Enter 发送，Shift+Enter 换行"
              @input="resizeTextarea"
              @keydown.enter="handleEnter"
          ></textarea>
          <div class="composer-footer">
            <div class="composer-left">
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
              <div class="composer-tip">{{ composerTipText }}</div>
            </div>
            <button class="send-btn" :disabled="!canSend" @click="send">发送</button>
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

.brand-badge {
  display: inline-flex;
  align-items: center;
  border-radius: 999px;
  background: rgba(36, 89, 216, 0.12);
  color: #2459d8;
  font-size: 12px;
  font-weight: 800;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  padding: 7px 10px;
}

.brand-title {
  margin-top: 14px;
  font-size: clamp(28px, 2vw, 36px);
  line-height: 1.18;
  font-weight: 900;
  color: #0f172a;
}

.brand-desc {
  margin-top: 12px;
  font-size: clamp(13px, 0.9vw, 14px);
  line-height: 1.82;
  color: #5b6472;
}

.side-actions {
  display: grid;
  gap: clamp(8px, 0.8vh, 10px);
}

.history-section {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.section-kicker {
  font-size: 12px;
  font-weight: 800;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: #2459d8;
}

.section-title {
  font-size: 16px;
  line-height: 1.55;
  font-weight: 800;
  color: #0f172a;
}

.history-list {
  display: grid;
  gap: 10px;
  max-height: 46vh;
  overflow-y: auto;
  padding-right: 2px;
}

.history-card {
  position: relative;
  border: 1px solid rgba(148, 163, 184, 0.16);
  border-radius: 18px;
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.98) 0%, rgba(248, 250, 252, 0.94) 100%);
  text-align: left;
  padding: 14px;
  cursor: pointer;
  transition: transform .18s ease, box-shadow .18s ease, border-color .18s ease, background .18s ease;
}

.history-card:hover {
  transform: translateY(-1px);
  border-color: rgba(36, 89, 216, 0.2);
  box-shadow: 0 14px 28px rgba(15, 23, 42, 0.08);
}

.history-card.active {
  border-color: rgba(36, 89, 216, 0.42);
  box-shadow: 0 16px 32px rgba(36, 89, 216, 0.16);
  background: linear-gradient(180deg, rgba(231, 241, 255, 0.98) 0%, rgba(244, 248, 255, 0.96) 100%);
}

.history-card.active::before {
  content: '';
  position: absolute;
  left: 0;
  top: 12px;
  bottom: 12px;
  width: 4px;
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
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.history-card-title {
  font-size: 15px;
  font-weight: 800;
  color: #0f172a;
}

.history-card-tag {
  display: inline-flex;
  align-items: center;
  height: 24px;
  border-radius: 999px;
  font-size: 11px;
  font-weight: 800;
  padding: 0 8px;
}

.history-card-time {
  flex: 0 0 auto;
  font-size: 11px;
  line-height: 1.4;
  color: #94a3b8;
}

.history-card-desc {
  margin-top: 10px;
  font-size: 13px;
  line-height: 1.72;
  color: #64748b;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  text-overflow: ellipsis;
  min-height: calc(1.72em * 2);
}

.history-card-meta {
  margin-top: 10px;
  font-size: 11px;
  color: #94a3b8;
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
.side-link,
.history-card,
.send-btn,
.secondary-btn,
.danger-btn,
.avatar-menu-item {
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

.side-link {
  border-radius: 14px;
  background: rgba(241, 245, 249, 0.88);
  color: #1f2937;
  font-size: clamp(13px, 0.9vw, 14px);
  font-weight: 700;
  padding: clamp(10px, 1.1vh, 12px) clamp(12px, 1vw, 14px);
  text-align: left;
}

.profile-card {
  margin-top: auto;
  display: flex;
  gap: 12px;
  align-items: center;
  border-radius: 18px;
  background: rgba(15, 23, 42, 0.04);
  padding: clamp(12px, 1.2vh, 14px);
}

.profile-avatar img,
.avatar-btn img {
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

.chat-header {
  display: flex;
  justify-content: space-between;
  gap: clamp(12px, 1vw, 20px);
  align-items: flex-start;
  margin-bottom: clamp(14px, 1.4vh, 18px);
}

.header-kicker {
  font-size: 12px;
  font-weight: 800;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: #2459d8;
}

.header-title {
  margin-top: 8px;
  font-size: clamp(26px, 1.8vw, 30px);
  line-height: 1.16;
  font-weight: 900;
  color: #0f172a;
}

.header-desc {
  margin-top: 10px;
  font-size: clamp(13px, 0.9vw, 14px);
  line-height: 1.78;
  color: #5b6472;
}

.header-tools {
  display: flex;
  gap: 10px;
  align-items: center;
  flex-wrap: wrap;
  justify-content: flex-end;
}

.avatar-wrap {
  position: relative;
}

.avatar-btn {
  width: 42px;
  height: 42px;
  border: 1px solid rgba(148, 163, 184, 0.2);
  border-radius: 50%;
  background: #fff;
  padding: 2px;
  cursor: pointer;
}

.avatar-menu {
  position: absolute;
  right: 0;
  top: calc(100% + 8px);
  min-width: 140px;
  border: 1px solid rgba(148, 163, 184, 0.16);
  border-radius: 16px;
  background: rgba(255, 255, 255, 0.96);
  box-shadow: 0 18px 34px rgba(15, 23, 42, 0.1);
  padding: 8px;
}

.avatar-menu-item {
  width: 100%;
  border-radius: 12px;
  background: transparent;
  color: #1f2937;
  font-size: 14px;
  font-weight: 700;
  padding: 10px 12px;
  text-align: left;
}

.avatar-menu-item:hover,
.side-link:hover,
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
  background: rgba(255, 255, 255, 0.92);
  color: #1f2937;
  border: 1px solid rgba(148, 163, 184, 0.18);
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

.history-card-tag.is-emotion {
  background: rgba(14, 165, 233, 0.12);
  color: #0369a1;
  border: 1px solid rgba(14, 165, 233, 0.18);
}

.history-card-tag.is-agent {
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
  background: rgba(248, 250, 252, 0.92);
  border: 1px solid rgba(148, 163, 184, 0.14);
}

.message-block.final {
  padding: 0;
  overflow: hidden;
  background: linear-gradient(180deg, rgba(255, 255, 255, 1) 0%, rgba(247, 250, 255, 0.98) 100%);
  border: 1px solid rgba(148, 163, 184, 0.14);
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.8);
}

.message-block-title {
  margin-bottom: 8px;
  font-size: 12px;
  font-weight: 800;
  letter-spacing: 0.04em;
  color: #64748b;
}

.message-block-title.final-title {
  color: #334155;
}

.answer-shell {
  display: flex;
  flex-direction: column;
}

.answer-head {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 14px 16px;
  background: linear-gradient(135deg, rgba(36, 89, 216, 0.08) 0%, rgba(34, 211, 238, 0.08) 100%);
  border-bottom: 1px solid rgba(148, 163, 184, 0.12);
}

.answer-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 42px;
  height: 28px;
  border-radius: 999px;
  background: linear-gradient(135deg, #2459d8 0%, #22d3ee 100%);
  color: #fff;
  font-size: 12px;
  font-weight: 800;
  letter-spacing: 0.06em;
}

.answer-head-copy {
  min-width: 0;
}

.answer-head-copy .message-block-title {
  margin-bottom: 2px;
}

.answer-head-note {
  font-size: 12px;
  line-height: 1.6;
  color: #64748b;
}

.answer-body {
  display: flex;
  flex-direction: column;
  gap: 14px;
  padding: 16px;
}

.answer-sidecar {
  border-radius: 16px;
  background: rgba(36, 89, 216, 0.05);
  border: 1px solid rgba(36, 89, 216, 0.1);
  padding: 12px 14px;
}

.answer-sidecar-title {
  margin-bottom: 8px;
  font-size: 12px;
  font-weight: 800;
  letter-spacing: 0.04em;
  color: #2459d8;
}

.answer-text {
  color: #1f2937;
}

.message-collapse {
  width: 100%;
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
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
  font-size: 12px;
  font-weight: 700;
  color: #94a3b8;
}

.message-text {
  white-space: pre-wrap;
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

.message-text :deep(li) {
  margin: 4px 0;
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

.answer-text :deep(h2) {
  margin-top: 18px;
  padding-top: 14px;
  border-top: 1px solid rgba(148, 163, 184, 0.14);
}

.answer-text :deep(h2:first-child) {
  margin-top: 0;
  padding-top: 0;
  border-top: none;
}

.answer-text :deep(code) {
  background: rgba(36, 89, 216, 0.08);
  color: #1d4ed8;
}

.answer-text :deep(pre) {
  background: linear-gradient(180deg, #f8fbff 0%, #f3f7fd 100%);
}

.composer-card {
  margin-top: 8px;
  border-radius: 24px;
  background: rgba(255, 255, 255, 0.92);
  border: 1px solid rgba(148, 163, 184, 0.18);
  padding: clamp(12px, 1.2vh, 16px);
  width: 100%;
}

.file-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 12px;
}

.file-chip {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  border-radius: 999px;
  background: rgba(36, 89, 216, 0.08);
  border: 1px solid rgba(36, 89, 216, 0.14);
  color: #2459d8;
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
  background: rgba(36, 89, 216, 0.12);
  color: #2459d8;
  cursor: pointer;
  line-height: 1;
  padding: 0;
}

.composer-input {
  width: 100%;
  min-height: clamp(72px, 10vh, 96px);
  max-height: clamp(160px, 24vh, 220px);
  resize: none;
  border: none;
  background: transparent;
  outline: none;
  font-size: clamp(14px, 0.95vw, 15px);
  line-height: 1.8;
  color: #1f2937;
}

.composer-footer {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: center;
  margin-top: clamp(10px, 1vh, 12px);
}

.composer-left {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.composer-tip {
  font-size: 12px;
  line-height: 1.6;
  color: #64748b;
}

.upload-btn,
.tool-toggle-btn {
  border: 1px solid rgba(148, 163, 184, 0.18);
  border-radius: 12px;
  background: rgba(248, 250, 252, 0.98);
  color: #334155;
  font-size: 13px;
  font-weight: 700;
  padding: 10px 14px;
  cursor: pointer;
}

.upload-btn:hover,
.tool-toggle-btn:hover {
  background: rgba(241, 245, 249, 0.98);
}

.tool-toggle-btn.active {
  border-color: rgba(36, 89, 216, 0.18);
  background: linear-gradient(135deg, rgba(36, 89, 216, 0.14) 0%, rgba(34, 211, 238, 0.16) 100%);
  color: #1d4ed8;
}

.send-btn {
  flex: 0 0 auto;
  border-radius: 14px;
  background: linear-gradient(135deg, #2459d8 0%, #22d3ee 100%);
  color: #fff;
  font-size: clamp(13px, 0.9vw, 14px);
  font-weight: 800;
  padding: clamp(10px, 1.1vh, 12px) clamp(14px, 1vw, 18px);
}

.send-btn:disabled {
  opacity: 0.55;
  cursor: not-allowed;
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

  .brand-title {
    font-size: 28px;
  }

  .history-card {
    padding: 12px;
  }

  .chat-panel {
    padding: 14px;
  }

  .header-title {
    font-size: 26px;
  }

  .message-card {
    max-width: 86%;
  }

  .composer-input {
    min-height: 64px;
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

  .chat-header,
  .composer-footer {
    flex-direction: column;
    align-items: stretch;
  }

  .header-tools {
    justify-content: flex-start;
  }

  .message-card {
    max-width: 100%;
  }

  .empty-title {
    font-size: 24px;
  }

  .brand-title {
    font-size: 28px;
  }

  .history-list,
  .empty-grid {
    grid-template-columns: 1fr;
  }
}
</style>
