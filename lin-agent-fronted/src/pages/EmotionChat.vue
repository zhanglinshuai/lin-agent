<script setup>
import {ref, onMounted, onBeforeUnmount, computed, nextTick} from 'vue'
import { useRouter } from 'vue-router'
import {openEmotionSSE} from '@/services/sse'
import axios from 'axios'

const chatId = ref('')
const input = ref('')
const textareaRef = ref(null)
const messages = ref([])
let currentStream = null
const showSidebar = ref(true)
const showAvatarMenu = ref(false)
const history = ref([
  {title: '最近对话 1'},
  {title: '最近对话 2'},
  {title: '最近对话 3'},
])
const sidebarPeek = ref(false)
const hideCollapse = ref(false)
const peekHovering = ref(false)
const peekClosing = ref(false)
const showLoginModal = ref(false)
const loginAccount = ref('')
const loginPassword = ref('')
const loginLoading = ref(false)
const loginError = ref('')

const canSend = computed(() => input.value.trim().length > 0)
const messagesPanelRef = ref(null)
const sidebarRef = ref(null)
const userMessages = computed(() => messages.value.filter(m => m && m.role === 'user'))
const assistantMessages = computed(() => messages.value.filter(m => m && m.role === 'assistant'))
const router = useRouter()
const activeConversationId = ref('')

function genChatId() {
  if (crypto && crypto.randomUUID) return crypto.randomUUID()
  return Math.random().toString(36).slice(2)
}

function send() {
  const text = input.value.trim()
  if (!text) return
  try {
    const uid = localStorage.getItem('user_id')
    if (!uid) { showLoginModal.value = true; return }
    const userId = uid
    if (!chatId.value) chatId.value = genChatId()
    messages.value.push({role: 'user', content: text})
    messages.value.push({role: 'assistant', content: '', loading: true, complete: false})
    input.value = ''
    resizeTextarea()
    const idx = messages.value.length - 1
    currentStream?.close()
    currentStream = openEmotionSSE(
        text,
        chatId.value,
        userId,
        (chunk) => {
          messages.value[idx].content += chunk
          if (messages.value[idx].loading) messages.value[idx].loading = false
          nextTick(() => {
            const el = messagesPanelRef.value
            if (el) el.scrollTop = el.scrollHeight
          })
        },
        () => {
          const msg = messages.value[idx]
          if (msg && msg.role === 'assistant') { msg.loading = false; msg.complete = true }
        },
        () => {
          const msg = messages.value[idx]
          if (msg && msg.role === 'assistant') { msg.loading = false; msg.complete = true }
        }
    )
    nextTick(() => {
      const el = messagesPanelRef.value
      if (el) el.scrollTop = el.scrollHeight
    })
    return
  } catch(e) { showLoginModal.value = true; return }
}

onMounted(() => {
  chatId.value = genChatId()
  resizeTextarea()
  if (sidebarRef.value && sidebarRef.value.addEventListener) {
    sidebarRef.value.addEventListener('transitionend', onSidebarTransitionEnd)
  }
  fetchChatMemoryList()
})

onBeforeUnmount(() => {
  currentStream?.close()
  if (sidebarRef.value && sidebarRef.value.removeEventListener) {
    sidebarRef.value.removeEventListener('transitionend', onSidebarTransitionEnd)
  }
})

function handleEnter(e) {
  if (!e.shiftKey) {
    e.preventDefault()
    send()
  }
}

function resizeTextarea() {
  const el = textareaRef.value
  if (!el) return
  el.style.height = 'auto'
  const h = Math.min(el.scrollHeight, 160)
  el.style.height = h + 'px'
}

function toggleSidebar(instant = false) {
  if (showSidebar.value) {
    peekClosing.value = true
    sidebarPeek.value = false
    hideCollapse.value = false
    showSidebar.value = false
  } else {
    if (instant || sidebarPeek.value) {
      showSidebar.value = true
      sidebarPeek.value = false
      peekClosing.value = false
      hideCollapse.value = false
    } else {
      sidebarPeek.value = true
      setTimeout(() => {
        showSidebar.value = true
        sidebarPeek.value = false
        peekClosing.value = false
        hideCollapse.value = false
      }, 300)
    }
  }
}

function toggleAvatarMenu() {
  showAvatarMenu.value = !showAvatarMenu.value
}

function startConversation() {
  messages.value = []
  chatId.value = genChatId()
  activeConversationId.value = ''
}

function openSettings() {
}

function logout() {
  showToast('已退出登录')
  showAvatarMenu.value = false
  try { localStorage.removeItem('auth_token') } catch(e) {}
  try { localStorage.removeItem('user_id') } catch(e) {}
  try { localStorage.removeItem('user_name') } catch(e) {}
  showLoginModal.value = true
}

function onOpenSettings() {
  showToast('打开设置')
  showAvatarMenu.value = false
}

function onLogout() {
  showToast('已退出登录')
  showAvatarMenu.value = false
  try { localStorage.removeItem('auth_token') } catch(e) {}
  showLoginModal.value = true
  try { localStorage.removeItem('user_id') } catch(e) {}
  try { localStorage.removeItem('user_name') } catch(e) {}
}

async function openHistory(i) {
  const item = history.value[i]
  if (!item) return
  const raw = item.raw || {}
  let username = ''
  try { username = localStorage.getItem('user_name') || '' } catch(e) { username = '' }
  const cid = raw.conversationId
  if (!cid) {
    input.value = item.title
    resizeTextarea()
    return
  }
  activeConversationId.value = String(cid)
  chatId.value = activeConversationId.value
  let res
  try {
    res = await axios.get('/api/chat_memory/getConversation', { params: { conversationId: cid } })
  } catch(e) {
    showToast((e?.response?.data?.message) || '会话消息加载失败')
    return
  }
  const data = res && res.data
  const arr = Array.isArray(data) ? data : (Array.isArray(data?.data) ? data.data : [])
  if (!Array.isArray(arr)) return
  messages.value = arr.map((m) => {
    const text = String(m?.content ?? m?.message ?? m?.text ?? '')
    const mt = m?.message_type ?? m?.messageType ?? m?.type
    let isUser
    if (typeof mt === 'string') {
      const s = mt.toLowerCase()
      isUser = s === 'user' || s === 'u' || s === 'human' || s.includes('用户')
    } else if (typeof mt === 'number') {
      isUser = mt === 0
    } else {
      const sender = String(m?.role ?? m?.sender ?? '').toLowerCase()
      isUser = m?.isUser === true || sender.includes('user') || sender.includes('用户')
    }
    return { role: isUser ? 'user' : 'assistant', content: text, loading: false, complete: true }
  })
  nextTick(() => {
    const el = messagesPanelRef.value
    if (el) el.scrollTop = el.scrollHeight
  })
}

function onExpandHover(hover) {
  if (!showSidebar.value) {
    sidebarPeek.value = hover
    hideCollapse.value = hover
  }
}

function onPeekEnter() {
  peekHovering.value = true
  peekClosing.value = false
  sidebarPeek.value = true
  if (!showSidebar.value) hideCollapse.value = true
}

function onPeekLeave() {
  peekHovering.value = false
  peekClosing.value = true
  sidebarPeek.value = false
  setTimeout(() => {
    if (!peekHovering.value && !showSidebar.value) {
      peekClosing.value = false
      hideCollapse.value = false
    }
  }, 300)
}

function onSidebarTransitionEnd(e) {
  if (e && e.propertyName === 'transform' && !peekHovering.value) {
    peekClosing.value = false
    hideCollapse.value = false
  }
}

function onEdgeHover(e) {
  if (!showSidebar.value && e && e.clientX <= 24) onPeekEnter()
}

function onEdgeLeave() {
  if (!showSidebar.value) onPeekLeave()
}

const toastText = ref('')
const toastVisible = ref(false)

function showToast(text) {
  toastText.value = String(text || '')
  toastVisible.value = true
  setTimeout(() => { toastVisible.value = false }, 1500)
}

const loadingHistory = ref(false)
async function fetchChatMemoryList() {
  let username = ''
  try { username = localStorage.getItem('user_name') || '' } catch(e) { username = '' }
  if (!username) return
  loadingHistory.value = true
  try {
    const res = await axios.get('/api/chat_memory/getChatMemoryList', { params: { username } })
    const payload = Array.isArray(res.data) ? res.data : (Array.isArray(res.data?.data) ? res.data.data : [])
    if (Array.isArray(payload)) {
      const getTs = (m) => {
        const v = m?.timestamp ?? m?.time ?? m?.createdAt ?? m?.createTime ?? m?.gmtCreate ?? m?.gmt_created
        if (typeof v === 'number') return v
        if (typeof v === 'string') return Date.parse(v) || 0
        return 0
      }
      const groups = payload.reduce((acc, it) => {
        const cid = it?.conversationId
        if (!cid) return acc
        const key = String(cid)
        const ts = getTs(it)
        const title = String(it?.conversationName || it?.title || it?.name || key)
        if (!acc[key]) {
          acc[key] = { conversationId: key, title, ts }
        } else {
          if (ts < acc[key].ts) {
            acc[key].ts = ts
            acc[key].title = title
          }
        }
        return acc
      }, {})
      const list = Object.values(groups)
        .sort((a,b) => (a.ts || 0) - (b.ts || 0))
        .map((g) => ({ title: g.title, raw: { conversationId: g.conversationId } }))
      history.value = list
    }
  } catch(e) {
    showToast((e?.response?.data?.message) || '会话列表加载失败')
  } finally {
    loadingHistory.value = false
  }
}

async function submitLogin() {
  loginError.value = ''
  if (!loginAccount.value.trim() || !loginPassword.value.trim()) {
    loginError.value = '请输入用户名和密码'
    return
  }
  if (loginLoading.value) return
  loginLoading.value = true
  let res
  try {
    res = await axios.post('/api/user/login', { userName: loginAccount.value, userPassword: loginPassword.value })
  } catch(e) {
    loginError.value = (e?.response?.data?.message) || (e?.message) || '登录失败'
  }
  loginLoading.value = false
  if (!res) return
  const raw = res.data
  let token = null
  let userId = null
  if (typeof raw === 'string') {
    userId = raw
  } else if (raw && typeof raw === 'object') {
    token = raw.token ?? raw.data?.token ?? null
    if (typeof raw.data === 'string') userId = raw.data
    userId = userId ?? raw.userId ?? raw.data?.userId ?? null
  }
  if (!token && !userId) {
    loginError.value = (raw && raw.message) || '登录失败：未返回用户ID或token'
    return
  }
  try {
    if (token) localStorage.setItem('auth_token', String(token))
    if (userId) localStorage.setItem('user_id', String(userId))
    localStorage.setItem('user_name', String(loginAccount.value))
  } catch(e) {}
  showLoginModal.value = false
  showToast('登录成功')
  fetchChatMemoryList()
}

function copyMessage(text, i) {
  const s = String(text || '')
  if (navigator && navigator.clipboard && navigator.clipboard.writeText) {
    navigator.clipboard.writeText(s)
  } else {
    const ta = document.createElement('textarea')
    ta.value = s
    document.body.appendChild(ta)
    ta.select()
    try { document.execCommand('copy') } catch(e) {}
    document.body.removeChild(ta)
  }
  showToast('已复制')
}

function escapeHtml(s) {
  return s
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;')
}

function renderInline(s) {
  return s
      .replace(/\*\*([^*]+)\*\*/g, '<strong>$1</strong>')
      .replace(/\*([^*]+)\*/g, '<em>$1</em>')
      .replace(/_([^_]+)_/g, '<em>$1</em>')
      .replace(/`([^`]+)`/g, '<code>$1</code>')
      .replace(/\[([^\]]+)\]\((https?:\/\/[^\s)]+)\)/g, '<a href="$2" target="_blank" rel="noopener noreferrer">$1</a>')
}

function renderMarkdown(md) {
  const src = String(md || '')
  const text = escapeHtml(src)
  const lines = text.split(/\r?\n/)
  let html = ''
  let inCode = false
  let codeBuf = []
  let inUl = false
  for (let i = 0; i < lines.length; i++) {
    const line = lines[i]
    if (/^```/.test(line)) {
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
    const m = line.match(/^(#{1,6})\s+(.*)$/)
    if (m) {
      if (inUl) {
        html += '</ul>'
        inUl = false
      }
      const level = m[1].length
      html += `<h${level}>` + renderInline(m[2]) + `</h${level}>`
      continue
    }
    const li = line.match(/^[-*]\s+(.*)$/)
    if (li) {
      if (!inUl) {
        inUl = true
        html += '<ul>'
      }
      html += '<li>' + renderInline(li[1]) + '</li>'
      continue
    }
    if (line.trim() === '') {
      if (inUl) {
        html += '</ul>'
        inUl = false
      }
      html += '<br/>'
      continue
    }
    if (inUl) {
      html += '</ul>'
      inUl = false
    }
    html += '<p>' + renderInline(line) + '</p>'
  }
  if (inUl) html += '</ul>'
  if (inCode) html += '<pre><code>' + codeBuf.join('\n') + '</code></pre>'
  return html
}
</script>

<template>
  <div class="chat-page">
    <div class="layout" :class="{ collapsed: !showSidebar }">
      <aside ref="sidebarRef" class="sidebar" :class="{ collapsed: !showSidebar, peek: sidebarPeek, closing: peekClosing }" @mouseenter="onPeekEnter" @mouseleave="onPeekLeave">
        <div class="sidebar-top">
          <div class="sidebar-header">情感大师</div>
          <button class="start-btn" @click="startConversation">
            <span class="btn-logo">
              <svg viewBox="0 0 24 24" aria-hidden="true"><circle cx="12" cy="12" r="11" fill="currentColor"
                                                                  opacity="0.15"/><line x1="12" y1="6" x2="12" y2="18"
                                                                                        stroke="currentColor"
                                                                                        stroke-width="2"
                                                                                        stroke-linecap="round"/><line
                  x1="6" y1="12" x2="18" y2="12" stroke="currentColor" stroke-width="2" stroke-linecap="round"/></svg>
            </span>
            <span>新对话</span>
          </button>
          <div class="switch-app">
            <router-link class="switch-link" to="/">
              <span class="switch-logo">🏠</span>
              <span class="switch-name">返回首页</span>
            </router-link>
          </div>
          <div class="switch-app">
            <router-link class="switch-link" to="/manus">
              <span class="switch-logo">🤖</span>
              <span class="switch-name">AI 超级智能体</span>
            </router-link>
          </div>
          <button class="collapse-btn icon-btn small" @click="toggleSidebar" v-show="!hideCollapse" title="收起侧栏">
            <svg viewBox="0 0 24 24" aria-hidden="true">
              <rect x="3" y="5" width="18" height="14" rx="2" fill="none" stroke="currentColor" stroke-width="2"/>
              <rect x="13" y="5" width="8" height="14" rx="2" fill="currentColor"/>
            </svg>
          </button>
        </div>
        <div class="sidebar-bottom">
          <div class="history-title">历史对话</div>
          <div class="history-list">
            <div
              class="history-item"
              v-for="(h, i) in history"
              :key="'h-'+i"
              :class="{ active: h?.raw && String(h.raw.conversationId) === activeConversationId }"
              @click="openHistory(i)"
            >{{ h.title }}</div>
          </div>
        </div>
      </aside>
      <div v-if="!showSidebar" class="hover-handle" @mouseenter="onPeekEnter"
           @mouseleave="onPeekLeave"></div>
        <div class="content" :class="{ empty: !messages.length }" @click="showAvatarMenu=false" @mousemove="onEdgeHover" @mouseleave="onEdgeLeave">
          <div class="content-top">
            <button v-if="!showSidebar" class="expand-btn icon-btn small" @click="toggleSidebar()"
                    @mouseenter="onExpandHover(true)" @mouseleave="onExpandHover(false)" title="展开侧栏">
              <svg viewBox="0 0 24 24" aria-hidden="true">
                <rect x="3" y="5" width="18" height="14" rx="2" fill="none" stroke="currentColor" stroke-width="2"/>
                <rect x="13" y="5" width="8" height="14" rx="2" fill="currentColor"/>
              </svg>
            </button>
            <div class="avatar" @click.stop="toggleAvatarMenu">🙂</div>
            <div v-if="showAvatarMenu" class="avatar-menu">
              <button class="menu-item" @click="onOpenSettings">设置</button>
              <button class="menu-item" @click="onLogout">退出登录</button>
            </div>
          </div>
          <div class="messages-panel" ref="messagesPanelRef" v-if="messages.length">
            <template v-for="(m, i) in messages" :key="'m-'+i">
              <div v-if="m.role==='assistant' && m.loading && !m.content" class="reply-card assistant loading">
                <div class="progress"><div class="bar"></div></div>
              </div>
              <div v-else-if="m.role==='assistant'" class="reply-card assistant">
                <div class="msg-content" v-html="renderMarkdown(m.content)"></div>
                <div class="msg-tools" v-if="m.complete">
                  <div class="copy-wrap">
                    <button class="copy-btn" @click="copyMessage(m.content, i)" title="复制">📋</button>
                    <div class="hover-tip">复制</div>
                  </div>
                </div>
              </div>
              <div v-else class="reply-card user" v-html="renderMarkdown(m.content)"></div>
            </template>
          </div>
          <div class="hero-page" v-else>
            <div class="hero-head">
              <div class="hero-logo">💖</div>
              <div class="hero-text">
                <div class="hero-title emotion">情感大师</div>
                <div class="hero-sub">你的 AI 情感大师，理解情绪与表达</div>
              </div>
            </div>
          </div>
          <div class="compose-card">
            <div class="placeholder">给 情感大师 发送消息</div>
            <div class="compose-area">
              <textarea
                  ref="textareaRef"
                  v-model="input"
                  placeholder="输入消息，Enter 发送，Shift+Enter 换行"
                  @input="resizeTextarea"
                  @keydown.enter="handleEnter"
              ></textarea>
              <div class="compose-footer">
                <div class="right">
                  <button class="send-round" :class="{ active: canSend }" :disabled="!canSend" @click="send">↑</button>
                </div>
              </div>
            </div>
          </div>
        </div>
        <div v-if="showLoginModal" class="modal-mask" @click.self="showLoginModal=false">
          <div class="modal-card">
            <div class="modal-title">登录</div>
            <div class="modal-form">
              <label class="modal-label">用户名</label>
              <input class="modal-input" v-model="loginAccount" placeholder="请输入用户名" />
              <label class="modal-label">密码</label>
              <input class="modal-input" type="password" v-model="loginPassword" placeholder="请输入密码" />
              <div class="modal-error" v-if="loginError">{{ loginError }}</div>
              <button class="modal-submit" :disabled="loginLoading || !loginAccount || !loginPassword" @click="submitLogin">{{ loginLoading ? '登录中...' : '登录' }}</button>
            </div>
          </div>
        </div>
        <div class="global-toast" v-if="toastVisible">{{ toastText }}</div>
      </div>
    </div>
</template>

<style scoped>
.chat-page {
  display: flex;
  width: 100%;
  max-width: 100vw;
}

.layout {
  --sidebar-w: 280px;
  display: grid;
  grid-template-columns: var(--sidebar-w) 1fr;
  width: 100%;
}

.layout.collapsed {
  grid-template-columns: 1fr;
}

.layout:not(.collapsed) .content {
  grid-column: 2 / 3;
}

.layout.collapsed .sidebar {
  position: absolute;
  left: 0;
  top: 0;
  bottom: 0;
  width: var(--sidebar-w);
  transform: translateX(-100%);
  transition: transform .28s cubic-bezier(0.22, 1, 0.36, 1), opacity .28s cubic-bezier(0.22, 1, 0.36, 1);
  will-change: transform, opacity;
  opacity: 0;
  background: #f4f5f7;
  z-index: 10;
}


.layout.collapsed .sidebar.peek {
  transform: translateX(0);
  top: 56px;
  bottom: auto;
  height: calc(100vh - 56px);
  opacity: 1;
}
.layout.collapsed .sidebar.closing {
  top: 56px;
  bottom: auto;
  height: calc(100vh - 56px);
}

.layout.collapsed .hover-handle {
  position: absolute;
  left: 0;
  top: 56px;
  bottom: 0;
  width: 24px;
  z-index: 9;
}
.layout.collapsed .sidebar.peek + .hover-handle,
.layout.collapsed .sidebar.closing + .hover-handle {
  pointer-events: none;
}

.layout.collapsed .sidebar.peek + .hover-handle {
  pointer-events: none;
}

.sidebar {
  border-right: 1px solid var(--color-border);
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  background: #f4f5f7;
}

.switch-app {
  margin-bottom: 12px;
}

.switch-link {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 10px;
  border: none;
  border-radius: 10px;
  background: #f4f5f7;
  color: inherit;
  text-decoration: none;
}

.switch-logo {
  font-size: 18px;
}

.switch-name {
  font-weight: 600;
}

.sidebar-top {
  position: relative;
  padding: 24px 16px;
  border-bottom: 1px solid var(--color-border);
}

.sidebar-header {
  font-weight: 700;
  margin-bottom: 10px;
}

.start-btn {
  width: 100%;
  padding: 10px 12px;
  border-radius: 10px;
  border: none;
  background: #eaf2ff;
  color: #2459d8;
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

.start-btn .btn-logo svg {
  width: 18px;
  height: 18px;
}

.start-btn .btn-logo svg {
  width: 18px;
  height: 18px;
}

.sidebar .start-btn:hover {
  background: linear-gradient(rgba(0, 0, 0, 0.06), rgba(0, 0, 0, 0.06)), #eaf2ff;
}

.sidebar .icon-btn:hover {
  background: linear-gradient(rgba(0, 0, 0, 0.06), rgba(0, 0, 0, 0.06)), #f4f5f7;
}

.sidebar .switch-link:hover {
  background: linear-gradient(rgba(0, 0, 0, 0.06), rgba(0, 0, 0, 0.06)), #f4f5f7;
}

.collapse-btn {
  position: absolute;
  right: 12px;
  top: 12px;
  width: 32px;
  height: 32px;
  border-radius: 6px;
  border: none;
  background: #f4f5f7;
  cursor: pointer;
}

.icon-btn {
  width: 32px;
  height: 32px;
  border-radius: 999px;
  border: none;
  background: #f4f5f7;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #2f6bff;
}

.icon-btn.small {
  width: 36px;
  height: 36px;
}

.icon-btn svg {
  width: 20px;
  height: 20px;
}

.icon-btn.sidebar {
  background: #f4f5f7;
  color: #2f6bff;
}

.sidebar-bottom {
  padding: 12px 16px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.history-title {
  font-size: 13px;
  opacity: .7;
}

.history-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
  margin-top: 6px;
}

.history-item {
  padding: 8px 10px;
  border-radius: 8px;
  border: none;
  background: #f4f5f7;
  cursor: pointer;
}

.history-item:hover {
  background: linear-gradient(rgba(0, 0, 0, 0.06), rgba(0, 0, 0, 0.06)), #f4f5f7;
}

.history-item.active {
  background: #e9ecef;
  font-weight: 600;
}

.content {
  padding: 24px 16px;
  display: flex;
  flex-direction: column;
  height: 100vh;
  gap: 16px;
  width: 100%;
}

.content-top {
  flex-shrink: 0;
}

.messages-panel {
  flex: 1 1 auto;
  min-height: 0;
}

.hero-page {
  flex: 0 0 auto;
}

.compose-card {
  flex-shrink: 0;
}

.content.empty {
  justify-content: center;
  align-items: center;
}

.content.empty .hero-page {
  padding: 0;
  margin-bottom: 16px;
}

.content.empty .compose-card {
  margin: 0;
}

.content.empty .content-top {
  display: flex;
  justify-content: flex-end;
}

.layout:not(.collapsed) .content.empty {
  width: calc(100vw - var(--sidebar-w));
}

.layout.collapsed .content.empty {
  width: 100vw;
}

.content:not(.empty) .compose-card {
  margin: 0 auto;
}

.content-top {
  position: fixed;
  top: 12px;
  left: 16px;
  right: 16px;
  display: flex;
  justify-content: flex-end;
  align-items: center;
  z-index: 1000;
}
.layout:not(.collapsed) .content-top {
  left: calc(var(--sidebar-w) + 16px);
}

.expand-btn {
  margin-right: auto;
  cursor: pointer;
  padding: 0;
  border: none;
  background: transparent;
}

.layout.collapsed .expand-btn {
  position: fixed;
  left: 12px;
  top: 12px;
  z-index: 11;
}

.expand-btn.icon-btn.small {
  width: 32px;
  height: 32px;
}

.expand-btn.icon-btn.small svg {
  width: 20px;
  height: 20px;
}

.avatar {
  width: 32px;
  height: 32px;
  border-radius: 999px;
  background: #f4f5f7;
  border: 1px solid var(--color-border);
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
}

.avatar-menu {
  position: absolute;
  right: 0;
  top: 40px;
  background: #fff;
  border: 1px solid var(--color-border);
  border-radius: 10px;
  box-shadow: 0 8px 20px rgba(0, 0, 0, 0.08);
  min-width: 140px;
  display: flex;
  flex-direction: column;
}

.menu-item {
  padding: 10px 12px;
  text-align: left;
  border: none;
  background: transparent;
}

.hero-page {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 48px 16px;
}

.hero-head {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 24px;
}

.hero-logo {
  font-size: 32px;
}

.hero-text {
  display: flex;
  flex-direction: column;
}

.hero-title {
  font-size: 24px;
  font-weight: 800;
  letter-spacing: .2px;
}

.hero-title.emotion {
  background: linear-gradient(90deg, #fb7185, #f59e0b);
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent;
}

.hero-sub {
  font-size: 13px;
  opacity: .7;
}

.compose-card {
  width: min(960px, 96vw);
  border: 1px solid var(--color-border);
  border-radius: 20px;
  background: #fff;
  box-shadow: 0 30px 40px rgba(0, 0, 0, 0.06), 0 10px 24px rgba(0, 0, 0, 0.04);
}

.placeholder {
  color: #9aa0a6;
  padding: 16px 20px 0;
}

.compose-area {
  padding: 0 20px 16px;
}

.compose-area textarea {
  width: 100%;
  min-height: 64px;
  max-height: 160px;
  border: none;
  outline: none;
  resize: none;
  font-size: 16px;
  padding: 8px 0;
  color: #111;
}

.compose-area textarea, .reply-card, .placeholder {
  font-family: Inter, 'Noto Sans SC', 'PingFang SC', 'Microsoft YaHei', 'Segoe UI', Roboto, system-ui, -apple-system, sans-serif;
}

.compose-footer {
  display: flex;
  align-items: center;
  justify-content: flex-end;
}

.actions {
  display: flex;
  gap: 10px;
  padding-top: 10px;
}

.chip {
  padding: 8px 12px;
  border-radius: 999px;
  border: 1px solid var(--color-border);
  background: #fafafa;
}

.right {
  display: flex;
  align-items: center;
  gap: 10px;
}

.icon {
  width: 34px;
  height: 34px;
  border-radius: 999px;
  background: #f4f5f7;
  border: 1px solid var(--color-border);
}

.send-round {
  width: 40px;
  height: 40px;
  border-radius: 999px;
  border: none;
  background: #d7dcf5;
  color: #fff;
  font-weight: 700;
}

.send-round.active {
  background: #2459d8;
}

.send-round:disabled {
  cursor: not-allowed;
  opacity: .7;
}

.replies {
  width: min(820px, 92vw);
  margin-top: 20px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.reply-card {
  border: none;
  border-radius: 14px;
  padding: 14px 16px;
  background: rgba(255, 255, 255, 0.6);
  white-space: pre-wrap;
  word-break: break-word;
  box-sizing: border-box;
}
.msg-tools {
  display: flex;
  justify-content: flex-start;
  margin-top: 6px;
}
.copy-btn {
  width: 28px;
  height: 28px;
  border-radius: 6px;
  border: none;
  background: #fff;
  color: #2f6bff;
  cursor: pointer;
}
.copy-btn:disabled {
  opacity: .5;
  cursor: not-allowed;
}
.copy-btn:hover:not(:disabled) {
  background: #f4f5f7;
}
.copy-wrap {
  position: relative;
  display: inline-block;
}
.hover-tip {
  position: absolute;
  bottom: 100%;
  left: 0;
  transform: translateY(-6px);
  background: #000;
  color: #fff;
  padding: 6px 8px;
  border-radius: 6px;
  font-size: 12px;
  white-space: nowrap;
  opacity: 0;
  pointer-events: none;
  transition: opacity .15s ease, transform .15s ease;
}
.copy-wrap:hover .hover-tip {
  opacity: 1;
  transform: translateY(-10px);
}
.global-toast {
  position: fixed;
  left: 50%;
  top: 12px;
  transform: translateX(-50%);
  padding: 10px 14px;
  border-radius: 8px;
  background: #22c55e;
  color: #fff;
  box-shadow: 0 10px 24px rgba(0,0,0,.12);
  z-index: 2000;
  font-size: 14px;
}
.reply-card.loading {
  background: rgba(255, 255, 255, 0.4);
}
.loader {
  display: inline-flex;
  gap: 6px;
  align-items: center;
}
.loader span {
  width: 6px;
  height: 6px;
  background: #d7dcf5;
  border-radius: 50%;
  animation: blink 1.2s infinite ease-in-out;
}
.loader span:nth-child(2) { animation-delay: 0.2s; }
.loader span:nth-child(3) { animation-delay: 0.4s; }
@keyframes blink {
  0%, 80%, 100% { opacity: 0.3; transform: scale(0.9); }
  40% { opacity: 1; transform: scale(1); }
}
.reply-card pre {
  background: #f4f5f7;
  padding: 10px;
  border-radius: 10px;
  overflow: auto;
}
.reply-card code {
  background: #f4f5f7;
  padding: 2px 6px;
  border-radius: 6px;
}
.reply-card h1, .reply-card h2, .reply-card h3, .reply-card h4, .reply-card h5, .reply-card h6 {
  margin: 0 0 8px;
  font-weight: 700;
}

.reply-card.assistant {
  align-self: flex-start;
}

.reply-card.user {
  align-self: flex-end;
  background: #f4f5f7;
}

.messages-panel {
  width: min(960px, 96vw);
  margin: 0 auto;
  display: flex;
  flex-direction: column;
  gap: 12px;
  overflow-y: auto;
  padding: 0 8px;
}
.messages-panel {
  -ms-overflow-style: none;
  scrollbar-width: none;
}
.messages-panel::-webkit-scrollbar {
  width: 0;
  height: 0;
}
.messages-panel::-webkit-scrollbar {
  display: none;
}
.modal-mask {
  position: fixed;
  left: 0;
  top: 0;
  right: 0;
  bottom: 0;
  background: rgba(0,0,0,.35);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 2001;
}
.modal-card {
  width: min(420px, 96vw);
  border: 1px solid var(--color-border);
  border-radius: 16px;
  background: #fff;
  box-shadow: 0 30px 40px rgba(0, 0, 0, 0.06), 0 10px 24px rgba(0, 0, 0, 0.04);
  padding: 20px;
}
.modal-title { font-size: 18px; font-weight: 800; margin-bottom: 10px; }
.modal-form { display: flex; flex-direction: column; gap: 8px; }
.modal-label { font-size: 13px; opacity: .8; }
.modal-input { border: 1px solid var(--color-border); border-radius: 8px; padding: 10px 12px; font-size: 15px; outline: none; }
.modal-error { color: #ef4444; font-size: 13px; min-height: 18px; }
.modal-submit { margin-top: 8px; padding: 10px 12px; border-radius: 999px; border: none; background: #2459d8; color: #fff; font-weight: 700; }
.modal-submit:disabled { opacity: .6; cursor: not-allowed; }
@media (max-width: 1280px) {
  .layout { --sidebar-w: 260px; }
}
@media (max-width: 1024px) {
  .layout { --sidebar-w: 240px; }
  .content { padding: 20px 12px; }
  .messages-panel, .compose-card { width: min(860px, 96vw); }
}
@media (max-width: 768px) {
  .layout { --sidebar-w: 84vw; }
  .content { padding: 16px 10px; gap: 12px; }
  .compose-card { border-radius: 16px; width: 96vw; }
  .messages-panel { width: 96vw; padding: 0 4px; }
  .expand-btn.icon-btn.small { width: 28px; height: 28px; }
  .avatar { width: 28px; height: 28px; }
  .avatar-menu { min-width: 120px; }
  .send-round { width: 36px; height: 36px; }
}
@media (max-width: 480px) {
  .compose-area textarea { font-size: 15px; }
  .placeholder { padding: 12px 16px 0; }
  .compose-area { padding: 0 16px 12px; }
  .hero-page { padding: 32px 12px; }
  .hero-title { font-size: 20px; }
  .hero-logo { font-size: 28px; }
}
</style>