<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'

const router = useRouter()
const loggedIn = ref(false)
const currentUserName = ref('')

const promiseList = [
  '情绪支持',
  '关系沟通',
  '任务规划',
  '资料整理',
  '智能问答',
]

const samplePrompts = [
  {
    title: '梳理情绪',
    desc: '最近情绪有点乱，帮我梳理一下我现在最需要处理的事情。',
    prompt: '最近情绪有点乱，帮我梳理一下我现在最需要处理的事情。',
  },
  {
    title: '改善沟通',
    desc: '我和对象沟通总是卡住，想聊聊怎么把话说得更好一些。',
    prompt: '我和对象沟通总是卡住，想聊聊怎么把话说得更好一些。',
  },
  {
    title: '安排一周',
    desc: '帮我把这周要做的事情整理成一个更轻松的安排。',
    prompt: '帮我把这周要做的事情整理成一个更轻松的安排。',
  },
  {
    title: '理清步骤',
    desc: '我脑子里有很多想法，想先帮我理成清晰的步骤。',
    prompt: '我脑子里有很多想法，想先帮我理成清晰的步骤。',
  },
]

function goAssistant() {
  router.push('/assistant')
}

function goAssistantWithPrompt(prompt) {
  router.push({
    path: '/assistant',
    query: {
      prompt,
    },
  })
}

function syncAuthState() {
  try {
    const userId = localStorage.getItem('user_id') || ''
    const userName = localStorage.getItem('user_name') || ''
    loggedIn.value = !!userId
    currentUserName.value = String(userName || '')
  } catch (e) {
    loggedIn.value = false
    currentUserName.value = ''
  }
}

const loginEntryText = computed(() => loggedIn.value ? '个人中心' : '登录')

function goLoginEntry() {
  if (loggedIn.value) {
    router.push('/assistant')
    return
  }
  router.push('/login')
}

onMounted(() => {
  syncAuthState()
  window.addEventListener('auth:login', syncAuthState)
  window.addEventListener('auth:logout', syncAuthState)
  window.addEventListener('storage', syncAuthState)
})

onBeforeUnmount(() => {
  window.removeEventListener('auth:login', syncAuthState)
  window.removeEventListener('auth:logout', syncAuthState)
  window.removeEventListener('storage', syncAuthState)
})
</script>

<template>
  <div class="home-page">
    <div class="background-layer">
      <div class="background-glow glow-left"></div>
      <div class="background-glow glow-right"></div>
      <div class="background-noise"></div>
    </div>

    <div class="page-shell">
      <header class="topbar">
        <div class="brand">
          <div class="brand-mark">AI</div>
          <div class="brand-copy">
            <div class="brand-name">智能协同助理</div>
            <div class="brand-sub">像和一个可靠的助手聊天一样开始</div>
          </div>
        </div>

        <button class="login-entry" @click="goLoginEntry">
          <span>{{ loginEntryText }}</span>
          <span v-if="loggedIn && currentUserName" class="login-entry-user">{{ currentUserName }}</span>
        </button>
      </header>

      <main class="hero-stage">
        <section class="hero-copy">
          <div class="hero-badge">AI Assistant</div>
          <h1 class="hero-title">把问题说出来，得到更有条理、也更有温度的回应</h1>
          <p class="hero-desc">
            不管你是想梳理情绪、聊聊关系困扰，还是需要规划安排、整理资料、寻找答案，
            都可以从这里直接开始。智能协同助理会陪你把模糊的问题变成清晰的下一步。
          </p>

          <div class="hero-actions">
            <button class="primary-action" @click="goAssistant">立即开始对话</button>
          </div>

          <div class="promise-row">
            <div v-for="item in promiseList" :key="item" class="promise-chip">{{ item }}</div>
          </div>
        </section>

        <section class="hero-preview">
          <div class="preview-frame">
            <div class="preview-topbar">
              <div class="preview-dots">
                <span></span>
                <span></span>
                <span></span>
              </div>
              <div class="preview-appname">智能协同助理</div>
            </div>

            <div class="preview-body">
              <div class="preview-card intro-card">
                <div class="intro-kicker">欢迎使用</div>
                <div class="intro-title">今天想一起解决什么问题？</div>
                <div class="intro-text">你可以从情绪、关系、计划、任务或资料整理开始。</div>
              </div>

              <div class="preview-message user">
                最近状态有点乱，既想梳理情绪，也想把接下来一周安排好。
              </div>

              <div class="preview-message assistant">
                可以，我们先一起把你现在最在意的感受说清楚，再把这一周的安排拆成几个容易执行的小步骤。
              </div>

              <div class="preview-input">
                <span class="preview-placeholder">输入你的问题或目标...</span>
                <span class="preview-send">发送</span>
              </div>
            </div>
          </div>
        </section>
      </main>

      <section class="sample-panel">
        <div class="sample-header">
          <div>
            <div class="sample-kicker">快捷开始</div>
            <div class="sample-title">从一个真实场景开始，会更容易进入状态</div>
          </div>
          <div class="sample-desc">点击任意卡片，问题会自动带入对话框。</div>
        </div>
        <div class="sample-list">
          <button
              v-for="item in samplePrompts"
              :key="item.title"
              class="sample-card"
              @click="goAssistantWithPrompt(item.prompt)"
          >
            <div class="sample-card-head">
              <span class="sample-card-title">{{ item.title }}</span>
              <span class="sample-card-arrow">→</span>
            </div>
            <div class="sample-card-text">{{ item.desc }}</div>
          </button>
        </div>
      </section>
    </div>
  </div>
</template>

<style scoped>
.home-page {
  position: relative;
  min-height: 100vh;
  overflow-x: hidden;
  overflow-y: visible;
  background:
    linear-gradient(180deg, #f8fbff 0%, #eef4fb 54%, #edf3fa 100%);
}

.page-shell {
  position: relative;
  z-index: 1;
  width: min(1460px, 100%);
  margin: 0 auto;
  min-height: 100vh;
  padding: clamp(14px, 1.4vh, 22px) clamp(18px, 3.6vw, 40px);
  display: flex;
  flex-direction: column;
}

.background-layer {
  position: absolute;
  inset: 0;
  pointer-events: none;
}

.background-glow {
  position: absolute;
  border-radius: 50%;
  filter: blur(90px);
  opacity: 0.45;
}

.glow-left {
  top: -160px;
  left: -120px;
  width: 420px;
  height: 420px;
  background: radial-gradient(circle, rgba(34, 211, 238, 0.3), rgba(34, 211, 238, 0));
}

.glow-right {
  right: -140px;
  bottom: -160px;
  width: 460px;
  height: 460px;
  background: radial-gradient(circle, rgba(59, 130, 246, 0.22), rgba(59, 130, 246, 0));
}

.background-noise {
  position: absolute;
  inset: 0;
  background-image:
    linear-gradient(rgba(148, 163, 184, 0.08) 1px, transparent 1px),
    linear-gradient(90deg, rgba(148, 163, 184, 0.08) 1px, transparent 1px);
  background-size: 30px 30px;
  mask-image: radial-gradient(circle at 50% 38%, #000 0%, rgba(0, 0, 0, 0.7) 48%, transparent 85%);
}

.topbar {
  width: 100%;
  margin: 0;
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16px;
}

.brand {
  display: flex;
  align-items: center;
  gap: 12px;
}

.brand-mark {
  width: 44px;
  height: 44px;
  display: grid;
  place-items: center;
  border-radius: 14px;
  background: linear-gradient(135deg, #2459d8 0%, #22d3ee 100%);
  color: #fff;
  font-size: 15px;
  font-weight: 900;
  box-shadow: 0 16px 28px rgba(36, 89, 216, 0.2);
}

.brand-name {
  font-size: 18px;
  font-weight: 900;
  color: #0f172a;
}

.brand-sub {
  font-size: 12px;
  color: #64748b;
}

.login-entry {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  border: 1px solid rgba(255, 255, 255, 0.38);
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.78);
  color: #0f172a;
  font-size: 14px;
  font-weight: 700;
  padding: 10px 16px;
  box-shadow: 0 12px 24px rgba(15, 23, 42, 0.06);
  backdrop-filter: blur(12px);
  cursor: pointer;
  transition: transform .18s ease, box-shadow .18s ease, background .18s ease;
}

.login-entry:hover {
  transform: translateY(-1px);
  background: rgba(255, 255, 255, 0.94);
  box-shadow: 0 16px 28px rgba(15, 23, 42, 0.1);
}

.login-entry-user {
  max-width: 96px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  border-radius: 999px;
  background: rgba(36, 89, 216, 0.12);
  color: #2459d8;
  font-size: 12px;
  font-weight: 800;
  padding: 4px 10px;
}

.hero-stage {
  width: 100%;
  flex: 1 0 auto;
  margin: clamp(16px, 2.2vh, 28px) 0 0;
  display: grid;
  grid-template-columns: 56% 44%;
  gap: clamp(22px, 3vw, 56px);
  align-items: center;
  padding-bottom: clamp(8px, 1.2vh, 16px);
}

.hero-copy {
  width: 100%;
  max-width: none;
  padding-right: clamp(0px, 0.8vw, 12px);
}

.hero-badge {
  display: inline-flex;
  align-items: center;
  height: 32px;
  border-radius: 999px;
  background: rgba(36, 89, 216, 0.1);
  color: #2459d8;
  font-size: 12px;
  font-weight: 800;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  padding: 0 12px;
}

.hero-title {
  margin-top: 18px;
  font-size: clamp(40px, 4.4vw, 82px);
  line-height: 1.04;
  font-weight: 900;
  letter-spacing: -0.04em;
  color: #0f172a;
}

.hero-desc {
  margin-top: 20px;
  max-width: 90%;
  font-size: clamp(15px, 1vw, 18px);
  line-height: 1.82;
  color: #526074;
}

.hero-actions {
  margin-top: clamp(20px, 2.2vh, 28px);
}

.primary-action {
  border: none;
  border-radius: 16px;
  background: linear-gradient(135deg, #2459d8 0%, #22d3ee 100%);
  color: #fff;
  font-size: 15px;
  font-weight: 800;
  padding: 14px 24px;
  cursor: pointer;
  box-shadow: 0 18px 34px rgba(36, 89, 216, 0.22);
  transition: transform .18s ease, box-shadow .18s ease, filter .18s ease;
}

.primary-action:hover {
  transform: translateY(-1px);
  filter: brightness(1.03);
  box-shadow: 0 22px 38px rgba(36, 89, 216, 0.26);
}

.promise-row {
  margin-top: clamp(18px, 2vh, 24px);
  display: flex;
  flex-wrap: wrap;
  gap: clamp(8px, 0.8vw, 12px);
}

.promise-chip {
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.84);
  border: 1px solid rgba(148, 163, 184, 0.14);
  color: #334155;
  font-size: 13px;
  font-weight: 700;
  padding: clamp(8px, 0.9vh, 11px) clamp(12px, 0.95vw, 16px);
  box-shadow: 0 10px 20px rgba(15, 23, 42, 0.05);
}

.hero-preview {
  display: flex;
  justify-content: flex-end;
  align-items: center;
  width: 100%;
}

.preview-frame {
  width: 100%;
  max-width: none;
  border: 1px solid rgba(148, 163, 184, 0.16);
  border-radius: 28px;
  background: rgba(255, 255, 255, 0.8);
  box-shadow: 0 24px 48px rgba(15, 23, 42, 0.08);
  backdrop-filter: blur(16px);
  overflow: hidden;
}

.preview-topbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  padding: clamp(12px, 1.4vh, 16px) clamp(14px, 1.1vw, 18px);
  border-bottom: 1px solid rgba(148, 163, 184, 0.12);
  background: rgba(248, 250, 252, 0.9);
}

.preview-dots {
  display: inline-flex;
  gap: 6px;
}

.preview-dots span {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: rgba(148, 163, 184, 0.65);
}

.preview-appname {
  font-size: 13px;
  font-weight: 700;
  color: #475569;
}

.preview-body {
  padding: clamp(14px, 1.8vh, 20px) clamp(14px, 1.2vw, 18px);
  display: flex;
  flex-direction: column;
  gap: clamp(12px, 1.3vh, 16px);
}

.preview-card {
  border-radius: 20px;
  padding: clamp(14px, 1.4vh, 16px);
}

.intro-card {
  background: linear-gradient(180deg, rgba(239, 246, 255, 0.92) 0%, rgba(248, 250, 252, 0.92) 100%);
  border: 1px solid rgba(148, 163, 184, 0.12);
}

.intro-kicker {
  font-size: 12px;
  font-weight: 800;
  color: #2459d8;
}

.intro-title {
  margin-top: 8px;
  font-size: clamp(16px, 1.1vw, 20px);
  font-weight: 800;
  color: #0f172a;
}

.intro-text {
  margin-top: 8px;
  font-size: 13px;
  line-height: 1.7;
  color: #64748b;
}

.preview-message {
  max-width: 86%;
  border-radius: 18px;
  padding: clamp(10px, 1.15vh, 14px) clamp(12px, 0.95vw, 15px);
  font-size: clamp(13px, 0.88vw, 15px);
  line-height: 1.8;
}

.preview-message.user {
  margin-left: auto;
  background: linear-gradient(135deg, #2459d8 0%, #22d3ee 100%);
  color: #fff;
}

.preview-message.assistant {
  background: rgba(248, 250, 252, 0.98);
  color: #334155;
  border: 1px solid rgba(148, 163, 184, 0.12);
}

.preview-input {
  margin-top: 4px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  border-radius: 18px;
  background: rgba(248, 250, 252, 0.96);
  border: 1px solid rgba(148, 163, 184, 0.12);
  padding: clamp(10px, 1.1vh, 14px) clamp(12px, 0.95vw, 14px);
}

.preview-placeholder {
  font-size: 13px;
  color: #94a3b8;
}

.preview-send {
  border-radius: 999px;
  background: rgba(36, 89, 216, 0.1);
  color: #2459d8;
  font-size: 12px;
  font-weight: 800;
  padding: 7px 10px;
}

.sample-panel {
  margin-top: clamp(10px, 1.8vh, 18px);
  border: 1px solid rgba(148, 163, 184, 0.14);
  border-radius: 24px;
  background: rgba(255, 255, 255, 0.7);
  box-shadow: 0 18px 34px rgba(15, 23, 42, 0.06);
  backdrop-filter: blur(12px);
  padding: clamp(16px, 1.8vh, 22px) clamp(16px, 1.6vw, 22px);
}

.sample-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.sample-kicker {
  font-size: 12px;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  font-weight: 800;
  color: #2459d8;
}

.sample-title {
  margin-top: 6px;
  font-size: 20px;
  font-weight: 800;
  color: #0f172a;
}

.sample-desc {
  font-size: 13px;
  color: #64748b;
  line-height: 1.6;
}

.sample-list {
  margin-top: 14px;
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
}

.sample-card {
  text-align: left;
  border: 1px solid rgba(148, 163, 184, 0.16);
  border-radius: 18px;
  background:
    linear-gradient(180deg, rgba(255, 255, 255, 0.98) 0%, rgba(248, 250, 252, 0.94) 100%);
  color: #334155;
  padding: 14px;
  cursor: pointer;
  transition: transform .18s ease, box-shadow .18s ease, border-color .18s ease, background .18s ease;
}

.sample-card:hover {
  transform: translateY(-1px);
  background: rgba(255, 255, 255, 1);
  border-color: rgba(36, 89, 216, 0.22);
  box-shadow: 0 14px 28px rgba(15, 23, 42, 0.1);
}

.sample-card-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 10px;
}

.sample-card-title {
  font-size: 15px;
  font-weight: 800;
  color: #0f172a;
}

.sample-card-arrow {
  width: 28px;
  height: 28px;
  display: grid;
  place-items: center;
  border-radius: 999px;
  background: rgba(36, 89, 216, 0.1);
  color: #2459d8;
  font-size: 16px;
  font-weight: 800;
}

.sample-card-text {
  margin-top: 10px;
  font-size: 13px;
  line-height: 1.7;
  color: #64748b;
}

@media (max-width: 1080px) {
  .hero-stage {
    min-height: auto;
    grid-template-columns: 1fr;
    gap: 22px;
    padding-bottom: 0;
  }

  .hero-copy {
    max-width: none;
  }

  .hero-preview {
    justify-content: stretch;
  }

  .sample-list {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 720px) {
  .home-page {
    padding: 16px 14px 24px;
  }

  .topbar {
    align-items: flex-start;
  }

  .hero-stage {
    margin-top: 20px;
    gap: 16px;
  }

  .brand-mark {
    width: 40px;
    height: 40px;
    border-radius: 12px;
    font-size: 14px;
  }

  .brand-name {
    font-size: 16px;
  }

  .hero-title {
    font-size: 36px;
    line-height: 1.08;
  }

  .hero-desc {
    font-size: 15px;
    line-height: 1.85;
  }

  .preview-frame {
    border-radius: 24px;
    max-width: none;
  }

  .preview-message {
    max-width: 100%;
    font-size: 13px;
  }

  .sample-panel {
    margin-top: 14px;
  }

  .login-entry {
    padding: 9px 12px;
    font-size: 13px;
  }

  .login-entry-user {
    max-width: 72px;
    padding: 4px 8px;
  }
}

@media (max-width: 560px) {
  .topbar {
    flex-direction: column;
    align-items: stretch;
  }

  .brand {
    justify-content: flex-start;
  }

  .login-entry {
    justify-content: center;
  }

  .hero-title {
    font-size: 30px;
  }

  .hero-desc {
    font-size: 14px;
  }

  .primary-action {
    width: 100%;
  }

  .promise-row {
    gap: 8px;
  }

  .promise-chip {
    font-size: 12px;
    padding: 9px 12px;
  }

  .preview-topbar,
  .preview-body {
    padding-left: 14px;
    padding-right: 14px;
  }

  .sample-header {
    align-items: flex-start;
  }

  .sample-title {
    font-size: 18px;
  }

  .sample-list {
    grid-template-columns: 1fr;
  }

  .sample-card {
    width: 100%;
    text-align: left;
  }
}

@media (min-width: 1081px) and (max-height: 900px) {
  .hero-stage {
    margin-top: 18px;
    align-items: start;
  }

  .hero-title {
    font-size: clamp(34px, 4.5vw, 52px);
  }

  .hero-desc {
    margin-top: 16px;
    font-size: 15px;
    line-height: 1.74;
  }

  .hero-actions {
    margin-top: 20px;
  }

  .promise-row {
    margin-top: 18px;
    gap: 8px;
  }

  .promise-chip {
    padding: 8px 12px;
    font-size: 12px;
  }

  .preview-topbar {
    padding: 12px 16px;
  }

  .preview-body {
    padding: 16px;
    gap: 12px;
  }

  .intro-title {
    font-size: 16px;
  }

  .preview-message {
    padding: 11px 13px;
    font-size: 13px;
    line-height: 1.65;
  }
}

@media (min-width: 1081px) and (max-height: 760px) {
  .home-page {
    padding-top: 14px;
    padding-bottom: 14px;
  }

  .brand-mark {
    width: 40px;
    height: 40px;
  }

  .brand-name {
    font-size: 16px;
  }

  .brand-sub {
    font-size: 11px;
  }

  .login-entry {
    padding: 8px 14px;
    font-size: 13px;
  }

  .hero-stage {
    margin-top: 14px;
    gap: 16px;
  }

  .hero-title {
    font-size: 32px;
    line-height: 1.08;
  }

  .hero-desc {
    margin-top: 14px;
    font-size: 14px;
    line-height: 1.68;
  }

  .primary-action {
    padding: 12px 18px;
    font-size: 14px;
  }

  .preview-frame {
    width: min(100%, 400px);
  }
}
</style>
