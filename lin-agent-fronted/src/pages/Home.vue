<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import ProjectBrand from '@/components/ProjectBrand.vue'

const router = useRouter()
const loggedIn = ref(false)
const currentUserName = ref('')

const samplePrompts = [
  {
    title: '先把它说出来',
    desc: '最近事情很多，我想先把脑子里的内容放下来，再慢慢理清楚。',
    prompt: '最近事情很多，我想先把脑子里的内容放下来，再慢慢理清楚。',
  },
  {
    title: '接着上次继续',
    desc: '上次聊到一半的事情，我想顺着之前的思路继续推进。',
    prompt: '上次聊到一半的事情，我想顺着之前的思路继续推进。',
  },
  {
    title: '整理一份资料',
    desc: '我手里有一些信息，想先抓重点，再决定下一步怎么做。',
    prompt: '我手里有一些信息，想先抓重点，再决定下一步怎么做。',
  },
  {
    title: '换个角度看看',
    desc: '有个问题卡住了，想听一个更清晰、更有层次的拆解方式。',
    prompt: '有个问题卡住了，想听一个更清晰、更有层次的拆解方式。',
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

const loginEntryText = computed(() => loggedIn.value ? '进入灵伴' : '登录')

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
      <div class="background-orb orb-left"></div>
      <div class="background-orb orb-right"></div>
      <div class="background-grid"></div>
      <div class="background-glow-line"></div>
    </div>

    <div class="page-shell">
      <header class="topbar">
        <div class="brand">
          <ProjectBrand />
        </div>

        <button class="login-entry" @click="goLoginEntry">
          <span>{{ loginEntryText }}</span>
          <span v-if="loggedIn && currentUserName" class="login-entry-user">{{ currentUserName }}</span>
        </button>
      </header>

      <main class="hero-stage">
        <section class="hero-copy">
          <div class="hero-kicker">Lingban Workspace</div>
          <h1 class="hero-title">让复杂的问题，在一个更安静的空间里继续往前走</h1>
          <p class="hero-desc">
            灵伴不是一次性的问答页面，而是一个能接住上下文、陪你继续推进、把信息慢慢理顺的对话工作台。
          </p>

          <div class="hero-actions">
            <button class="primary-action" @click="goAssistant">进入灵伴</button>
            <button class="secondary-action" @click="goAssistantWithPrompt(samplePrompts[0].prompt)">试一个示例</button>
          </div>

          <div class="hero-footnote">
            <span class="hero-footnote-line"></span>
            <span>同一入口里继续对话、整理资料、沉淀结果</span>
          </div>
        </section>

        <section class="hero-showcase">
          <div class="showcase-frame">
            <div class="showcase-topbar">
              <div class="showcase-dots">
                <span></span>
                <span></span>
                <span></span>
              </div>
              <div class="showcase-appname">灵伴工作台</div>
              <div class="showcase-badge">在线</div>
            </div>

            <div class="showcase-body">
              <div class="showcase-note">当前问题会在同一个上下文里持续展开</div>

              <div class="message-card user">
                我脑子里堆着几件事，想先判断应该从哪一件开始。
              </div>

              <div class="message-card assistant">
                可以，我们先把这些事逐个放下来，再一起找出最值得先处理的那个起点。
              </div>

              <div class="insight-panel">
                <div class="insight-panel-title">在这里，对话不会从零开始</div>
                <div class="insight-list">
                  <div class="insight-item">
                    <span class="insight-dot"></span>
                    <span>历史内容会自然接续，不需要反复重讲</span>
                  </div>
                  <div class="insight-item">
                    <span class="insight-dot"></span>
                    <span>资料、文件与问题可以放在同一个界面里一起参考</span>
                  </div>
                  <div class="insight-item">
                    <span class="insight-dot"></span>
                    <span>结果会被整理成更清晰、更容易执行的下一步</span>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </section>
      </main>

      <section class="scene-section">
        <div class="scene-header">
          <div>
            <div class="scene-kicker">Curated Scenes</div>
            <div class="scene-title">从一个具体的开场，进入更自然</div>
          </div>
          <div class="scene-desc">不需要把表达整理完整，先开口，剩下的交给对话慢慢展开。</div>
        </div>

        <div class="scene-grid">
          <button
              v-for="(item, index) in samplePrompts"
              :key="item.title"
              class="scene-card"
              @click="goAssistantWithPrompt(item.prompt)"
          >
            <div class="scene-card-order">{{ `0${index + 1}` }}</div>
            <div class="scene-card-title">{{ item.title }}</div>
            <div class="scene-card-text">{{ item.desc }}</div>
            <div class="scene-card-link">进入对话</div>
          </button>
        </div>
      </section>
    </div>
  </div>
</template>

<style scoped>
.home-page {
  position: relative;
  width: 100%;
  min-height: 100vh;
  overflow-x: hidden;
  background:
    radial-gradient(1200px 640px at 10% -10%, rgba(34, 211, 238, 0.18), transparent),
    radial-gradient(900px 580px at 100% 100%, rgba(245, 158, 11, 0.16), transparent),
    linear-gradient(180deg, #f4f8ff 0%, #edf3fb 100%);
}

.page-shell {
  --page-padding-x: clamp(18px, 3.4vw, 48px);
  position: relative;
  z-index: 1;
  width: 100%;
  padding: clamp(18px, 1.8vh, 26px) var(--page-padding-x) clamp(28px, 3vh, 44px);
  display: flex;
  flex-direction: column;
  gap: clamp(22px, 2.4vh, 30px);
}

.background-layer {
  position: absolute;
  inset: 0;
  pointer-events: none;
}

.background-orb {
  position: absolute;
  border-radius: 50%;
  filter: blur(80px);
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

.background-grid {
  position: absolute;
  inset: 0;
  background-image:
    linear-gradient(rgba(148, 163, 184, 0.12) 1px, transparent 1px),
    linear-gradient(90deg, rgba(148, 163, 184, 0.12) 1px, transparent 1px);
  background-size: 24px 24px;
  mask-image: radial-gradient(circle at 50% 32%, #000 0%, rgba(0, 0, 0, 0.78) 44%, transparent 82%);
}

.background-glow-line {
  position: absolute;
  inset: 0;
  background: linear-gradient(110deg, transparent 18%, rgba(255, 255, 255, 0.46) 48%, transparent 78%);
  transform: translateX(-44%);
  opacity: 0.5;
}

.topbar {
  width: 100%;
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
  transition: transform .18s ease, box-shadow .18s ease, background .18s ease, border-color .18s ease;
}

.login-entry:hover {
  transform: translateY(-1px);
  background: rgba(255, 255, 255, 0.94);
  border-color: rgba(36, 89, 216, 0.18);
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
  min-height: calc(100vh - 220px);
  display: grid;
  grid-template-columns: minmax(320px, 0.88fr) minmax(0, 1.12fr);
  gap: clamp(26px, 3vw, 44px);
  align-items: center;
}

.hero-copy {
  width: 100%;
  max-width: 560px;
}

.hero-kicker {
  display: inline-flex;
  align-items: center;
  min-height: 32px;
  border-radius: 999px;
  border: 1px solid rgba(36, 89, 216, 0.14);
  background: rgba(36, 89, 216, 0.08);
  color: #2459d8;
  font-size: 12px;
  font-weight: 800;
  letter-spacing: 0.12em;
  text-transform: uppercase;
  padding: 0 12px;
}

.hero-title {
  margin-top: 22px;
  font-family:
    'Iowan Old Style',
    'Baskerville Old Face',
    'Georgia',
    'Songti SC',
    serif;
  font-size: clamp(44px, 5.2vw, 82px);
  line-height: 0.98;
  font-weight: 700;
  letter-spacing: -0.045em;
  color: #0f172a;
}

.hero-desc {
  margin-top: 22px;
  max-width: 31ch;
  font-size: clamp(15px, 1.04vw, 18px);
  line-height: 1.88;
  color: #526074;
}

.hero-actions {
  margin-top: 28px;
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
}

.primary-action,
.secondary-action,
.prompt-chip,
.scene-card {
  border: none;
  cursor: pointer;
}

.primary-action,
.secondary-action {
  min-height: 50px;
  border-radius: 16px;
  font-size: 14px;
  font-weight: 800;
  padding: 0 20px;
  transition: transform .18s ease, box-shadow .18s ease, background .18s ease, border-color .18s ease;
}

.primary-action {
  background: linear-gradient(135deg, #2459d8 0%, #22d3ee 100%);
  color: #fff;
  box-shadow: 0 18px 32px rgba(36, 89, 216, 0.22);
}

.primary-action:hover {
  transform: translateY(-1px);
  box-shadow: 0 24px 38px rgba(36, 89, 216, 0.26);
}

.secondary-action {
  border: 1px solid rgba(148, 163, 184, 0.2);
  background: rgba(255, 255, 255, 0.82);
  color: #1f2937;
  box-shadow: 0 12px 22px rgba(15, 23, 42, 0.05);
}

.secondary-action:hover {
  transform: translateY(-1px);
  border-color: rgba(36, 89, 216, 0.22);
  background: rgba(239, 246, 255, 0.96);
}

.hero-footnote {
  margin-top: 24px;
  display: inline-flex;
  align-items: center;
  gap: 12px;
  color: #64748b;
  font-size: 13px;
}

.hero-footnote-line {
  width: 46px;
  height: 1px;
  background: linear-gradient(90deg, rgba(36, 89, 216, 0.9), rgba(36, 89, 216, 0));
}

.hero-showcase {
  position: relative;
  min-height: 580px;
}

.showcase-frame {
  position: relative;
  z-index: 1;
  width: min(100%, 860px);
  margin-left: auto;
  border: 1px solid rgba(148, 163, 184, 0.14);
  border-radius: 34px;
  background:
    radial-gradient(circle at top right, rgba(34, 211, 238, 0.16), transparent 28%),
    radial-gradient(circle at bottom left, rgba(36, 89, 216, 0.14), transparent 34%),
    linear-gradient(180deg, #0f172a 0%, #162033 100%);
  box-shadow: 0 34px 70px rgba(15, 23, 42, 0.2);
  overflow: hidden;
}

.showcase-frame::before {
  content: '';
  position: absolute;
  inset: 0;
  border-radius: inherit;
  border: 1px solid rgba(255, 255, 255, 0.04);
  pointer-events: none;
}

.showcase-frame::after {
  content: '';
  position: absolute;
  top: 0;
  left: 12%;
  right: 12%;
  height: 1px;
  background: linear-gradient(90deg, rgba(255,255,255,0), rgba(255,255,255,0.35), rgba(255,255,255,0));
  pointer-events: none;
}

.showcase-topbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 14px;
  padding: 16px 18px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);
  background: rgba(255, 255, 255, 0.02);
}

.showcase-dots {
  display: inline-flex;
  gap: 6px;
}

.showcase-dots span {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: rgba(226, 232, 240, 0.32);
}

.showcase-appname {
  color: rgba(226, 232, 240, 0.78);
  font-size: 13px;
  font-weight: 700;
}

.showcase-badge {
  display: inline-flex;
  align-items: center;
  min-height: 28px;
  border-radius: 999px;
  background: rgba(34, 211, 238, 0.16);
  color: #a5f3fc;
  font-size: 12px;
  font-weight: 800;
  padding: 0 10px;
}

.showcase-body {
  padding: clamp(22px, 2.4vw, 30px);
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.showcase-note {
  width: fit-content;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.08);
  color: rgba(226, 232, 240, 0.72);
  font-size: 12px;
  font-weight: 700;
  padding: 8px 12px;
}

.message-card {
  max-width: 82%;
  border-radius: 24px;
  padding: 16px 18px;
  font-size: 15px;
  line-height: 1.84;
}

.message-card.user {
  margin-left: auto;
  background: linear-gradient(135deg, #2459d8 0%, #22d3ee 100%);
  color: #fff;
}

.message-card.assistant {
  background: rgba(255, 255, 255, 0.06);
  color: rgba(241, 245, 249, 0.9);
  border: 1px solid rgba(255, 255, 255, 0.08);
}

.insight-panel {
  margin-top: 8px;
  border-radius: 26px;
  background:
    linear-gradient(180deg, rgba(255, 255, 255, 0.08) 0%, rgba(255, 255, 255, 0.04) 100%);
  border: 1px solid rgba(255, 255, 255, 0.08);
  padding: 18px;
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.03);
}

.insight-panel-title {
  font-size: 16px;
  font-weight: 800;
  color: #f8fafc;
}

.insight-list {
  margin-top: 12px;
  display: grid;
  gap: 10px;
}

.insight-item {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 13px;
  color: rgba(226, 232, 240, 0.74);
}

.insight-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #22d3ee;
}

.prompt-strip {
  margin-top: 16px;
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.prompt-chip {
  border: 1px solid rgba(148, 163, 184, 0.16);
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.74);
  color: #334155;
  font-size: 13px;
  font-weight: 700;
  padding: 10px 14px;
  transition: transform .18s ease, border-color .18s ease, background .18s ease;
}

.prompt-chip:hover {
  transform: translateY(-1px);
  border-color: rgba(36, 89, 216, 0.22);
  background: rgba(239, 246, 255, 0.96);
}

.scene-section {
  border: 1px solid rgba(148, 163, 184, 0.14);
  border-radius: 30px;
  background: rgba(255, 255, 255, 0.76);
  box-shadow: 0 20px 40px rgba(15, 23, 42, 0.06);
  backdrop-filter: blur(14px);
  padding: clamp(20px, 2.2vh, 26px);
}

.scene-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 14px;
  flex-wrap: wrap;
}

.scene-kicker {
  font-size: 12px;
  font-weight: 800;
  letter-spacing: 0.12em;
  text-transform: uppercase;
  color: #2459d8;
}

.scene-title {
  margin-top: 8px;
  font-family:
    'Iowan Old Style',
    'Baskerville Old Face',
    'Georgia',
    'Songti SC',
    serif;
  font-size: clamp(26px, 2.4vw, 34px);
  font-weight: 700;
  color: #0f172a;
}

.scene-desc {
  max-width: 34ch;
  font-size: 13px;
  line-height: 1.78;
  color: #64748b;
}

.scene-grid {
  margin-top: 18px;
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
}

.scene-card {
  min-height: 220px;
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  border-radius: 22px;
  border: 1px solid rgba(148, 163, 184, 0.16);
  background:
    linear-gradient(180deg, rgba(255, 255, 255, 0.98) 0%, rgba(248, 250, 252, 0.94) 100%);
  color: #334155;
  padding: 18px;
  text-align: left;
  transition: transform .18s ease, border-color .18s ease, box-shadow .18s ease, background .18s ease;
}

.scene-card:hover {
  transform: translateY(-1px);
  border-color: rgba(36, 89, 216, 0.22);
  box-shadow: 0 16px 28px rgba(15, 23, 42, 0.08);
  background:
    linear-gradient(180deg, rgba(255, 255, 255, 1) 0%, rgba(245, 249, 255, 0.96) 100%);
}

.scene-card-order {
  font-size: 12px;
  font-weight: 800;
  letter-spacing: 0.12em;
  text-transform: uppercase;
  color: #2459d8;
}

.scene-card-title {
  margin-top: 18px;
  font-size: 18px;
  font-weight: 800;
  color: #0f172a;
}

.scene-card-text {
  margin-top: 10px;
  font-size: 13px;
  line-height: 1.76;
  color: #64748b;
}

.scene-card-link {
  margin-top: auto;
  padding-top: 16px;
  font-size: 13px;
  font-weight: 800;
  color: #2459d8;
}

@media (max-width: 1180px) {
  .hero-stage {
    min-height: auto;
    grid-template-columns: 1fr;
  }

  .hero-copy,
  .hero-showcase,
  .showcase-frame {
    max-width: none;
    width: 100%;
  }

  .scene-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 760px) {
  .page-shell {
    padding-left: 14px;
    padding-right: 14px;
  }

  .topbar {
    align-items: flex-start;
  }

  .launch-surface {
    flex-direction: column;
    align-items: flex-start;
  }

  .hero-title {
    font-size: 38px;
    line-height: 1.06;
  }

  .showcase-frame {
    border-radius: 28px;
  }

  .message-card {
    max-width: 100%;
  }

  .scene-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 560px) {
  .topbar {
    flex-direction: column;
    align-items: stretch;
  }

  .login-entry {
    justify-content: center;
  }

  .hero-desc,
  .scene-desc {
    max-width: none;
  }

  .hero-actions {
    flex-direction: column;
  }

  .primary-action,
  .secondary-action {
    width: 100%;
  }

  .prompt-chip {
    width: 100%;
    text-align: center;
  }
}
</style>
