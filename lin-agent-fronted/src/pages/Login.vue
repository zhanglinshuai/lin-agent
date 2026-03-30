<script setup>
import { useRouter } from 'vue-router'
import AuthForm from '@/components/AuthForm.vue'

const router = useRouter()

function handleAuthSuccess() {
  router.push('/assistant')
}

function goHome() {
  router.push('/')
}
</script>

<template>
  <div class="login-page">
    <div class="login-bg">
      <div class="bg-grid"></div>
      <div class="orb orb-cyan"></div>
      <div class="orb orb-amber"></div>
      <div class="beam"></div>
    </div>

    <header class="topbar">
      <button class="back-home" @click="goHome">返回首页</button>
    </header>

    <main class="login-shell">
      <section class="intro-panel">
        <div class="intro-badge">灵伴 AI</div>
        <h1 class="intro-title">登录后，在统一入口里完成情绪陪伴、任务协作与资料整理</h1>
        <p class="intro-desc">
          统一账号进入系统后，你可以直接使用灵伴，保存历史对话，并在同一个页面里完成情绪梳理、计划安排和任务执行。
        </p>
        <div class="intro-highlights">
          <div class="highlight-card">
            <div class="highlight-label">Unified</div>
            <div class="highlight-title">统一对话入口</div>
            <div class="highlight-text">不再区分多个页面，所有对话能力都从同一个助手入口开始。</div>
          </div>
          <div class="highlight-card">
            <div class="highlight-label">Memory</div>
            <div class="highlight-title">历史会话连续保留</div>
            <div class="highlight-text">统一保存聊天记录和会话摘要，让后续沟通可以自然衔接。</div>
          </div>
          <div class="highlight-card">
            <div class="highlight-label">Profile</div>
            <div class="highlight-title">用户中心闭环</div>
            <div class="highlight-text">注册、登录、资料维护、头像上传与退出登录全部打通。</div>
          </div>
        </div>
      </section>

      <section class="auth-panel">
        <div class="auth-card">
          <div class="auth-card-glow"></div>
          <div class="auth-header">
            <div class="auth-kicker">账号中心</div>
            <h2 class="auth-title">欢迎进入灵伴</h2>
            <p class="auth-subtitle">支持登录与注册，适配桌面浏览器、平板和移动端视口。</p>
          </div>
          <AuthForm initial-mode="login" @success="handleAuthSuccess" />
          <div class="doc-tip">
            接口文档：<a href="http://localhost:8080/api/doc.html#/home" target="_blank">user 接口</a>
          </div>
        </div>
      </section>
    </main>
  </div>
</template>

<style scoped>
.login-page {
  position: relative;
  min-height: 100dvh;
  overflow: hidden;
  padding: 28px;
  background:
    radial-gradient(1100px 560px at 8% -10%, rgba(34, 211, 238, 0.18), transparent),
    radial-gradient(900px 520px at 100% 100%, rgba(245, 158, 11, 0.16), transparent),
    linear-gradient(135deg, #eef5ff 0%, #f8fbff 45%, #f4f7fc 100%);
}

.login-bg {
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
  background-size: 26px 26px;
  mask-image: radial-gradient(circle at 50% 35%, #000 0%, rgba(0, 0, 0, 0.78) 45%, transparent 85%);
}

.orb {
  position: absolute;
  border-radius: 50%;
  filter: blur(70px);
  opacity: 0.5;
}

.orb-cyan {
  top: -120px;
  left: -40px;
  width: 360px;
  height: 360px;
  background: radial-gradient(circle, rgba(34, 211, 238, 0.48), rgba(34, 211, 238, 0));
}

.orb-amber {
  right: -80px;
  bottom: -120px;
  width: 420px;
  height: 420px;
  background: radial-gradient(circle, rgba(245, 158, 11, 0.42), rgba(245, 158, 11, 0));
}

.beam {
  position: absolute;
  inset: 0;
  background: linear-gradient(110deg, transparent 20%, rgba(255, 255, 255, 0.5) 48%, transparent 78%);
  transform: translateX(-45%);
  opacity: 0.42;
}

.topbar {
  position: relative;
  z-index: 2;
  display: flex;
  justify-content: flex-end;
  margin-bottom: 20px;
}

.back-home {
  border: 1px solid rgba(255, 255, 255, 0.34);
  border-radius: 999px;
  background: rgba(15, 23, 42, 0.08);
  color: #0f172a;
  font-size: 14px;
  font-weight: 700;
  padding: 10px 16px;
  cursor: pointer;
  backdrop-filter: blur(10px);
  box-shadow: 0 10px 24px rgba(15, 23, 42, 0.08);
  transition: transform 0.18s ease, background 0.18s ease, box-shadow 0.18s ease;
}

.back-home:hover {
  transform: translateY(-1px);
  background: rgba(255, 255, 255, 0.72);
  box-shadow: 0 14px 28px rgba(15, 23, 42, 0.12);
}

.login-shell {
  position: relative;
  z-index: 2;
  width: min(1180px, 100%);
  margin: 0 auto;
  display: grid;
  grid-template-columns: minmax(0, 1.15fr) minmax(360px, 430px);
  gap: 28px;
  align-items: stretch;
}

.intro-panel {
  min-height: calc(100dvh - 120px);
  display: flex;
  flex-direction: column;
  justify-content: center;
  padding: clamp(20px, 4vw, 48px);
}

.intro-badge {
  align-self: flex-start;
  border: 1px solid rgba(37, 99, 235, 0.12);
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.62);
  color: #2459d8;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  padding: 8px 12px;
  backdrop-filter: blur(10px);
}

.intro-title {
  margin-top: 18px;
  font-size: clamp(34px, 5vw, 58px);
  line-height: 1.05;
  font-weight: 900;
  color: #0f172a;
  max-width: 11ch;
}

.intro-desc {
  margin-top: 18px;
  max-width: 640px;
  font-size: clamp(15px, 1.6vw, 18px);
  line-height: 1.8;
  color: #526074;
}

.intro-highlights {
  margin-top: 28px;
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 16px;
}

.highlight-card {
  min-height: 180px;
  border: 1px solid rgba(148, 163, 184, 0.18);
  border-radius: 22px;
  background: rgba(255, 255, 255, 0.62);
  box-shadow: 0 18px 40px rgba(15, 23, 42, 0.08);
  backdrop-filter: blur(14px);
  padding: 18px;
}

.highlight-label {
  display: inline-flex;
  align-items: center;
  height: 28px;
  border-radius: 999px;
  background: linear-gradient(135deg, rgba(36, 89, 216, 0.12), rgba(34, 211, 238, 0.18));
  color: #2459d8;
  font-size: 12px;
  font-weight: 800;
  padding: 0 10px;
}

.highlight-title {
  margin-top: 16px;
  font-size: 18px;
  line-height: 1.35;
  font-weight: 800;
  color: #111827;
}

.highlight-text {
  margin-top: 10px;
  font-size: 14px;
  line-height: 1.75;
  color: #5b6472;
}

.auth-panel {
  display: flex;
  align-items: center;
  justify-content: center;
}

.auth-card {
  position: relative;
  width: min(100%, 430px);
  border: 1px solid rgba(148, 163, 184, 0.22);
  border-radius: 28px;
  background: rgba(255, 255, 255, 0.88);
  box-shadow: 0 26px 60px rgba(15, 23, 42, 0.12);
  backdrop-filter: blur(16px);
  padding: 26px;
  overflow: hidden;
}

.auth-card-glow {
  position: absolute;
  inset: -30% auto auto 50%;
  width: 260px;
  height: 260px;
  transform: translateX(-50%);
  border-radius: 50%;
  background: radial-gradient(circle, rgba(34, 211, 238, 0.18), rgba(34, 211, 238, 0));
  pointer-events: none;
}

.auth-header {
  position: relative;
  z-index: 1;
  margin-bottom: 18px;
}

.auth-kicker {
  font-size: 12px;
  font-weight: 800;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: #2459d8;
}

.auth-title {
  margin-top: 10px;
  font-size: clamp(26px, 3vw, 34px);
  line-height: 1.1;
  font-weight: 900;
  color: #0f172a;
}

.auth-subtitle {
  margin-top: 10px;
  font-size: 14px;
  line-height: 1.7;
  color: #5b6472;
}

.doc-tip {
  margin-top: 18px;
  font-size: 13px;
  line-height: 1.6;
  color: #6b7280;
}

.doc-tip a {
  color: #2563eb;
  text-decoration: none;
}

.doc-tip a:hover {
  text-decoration: underline;
}

@media (max-width: 1080px) {
  .login-page {
    padding: 22px 18px;
  }

  .login-shell {
    grid-template-columns: 1fr;
    max-width: 760px;
  }

  .intro-panel {
    min-height: auto;
    padding: 18px 8px 0;
  }

  .intro-title {
    max-width: 16ch;
  }

  .auth-panel {
    justify-content: stretch;
  }

  .auth-card {
    width: 100%;
  }
}

@media (max-width: 780px) {
  .intro-highlights {
    grid-template-columns: 1fr;
  }

  .highlight-card {
    min-height: auto;
  }
}

@media (max-width: 640px) {
  .login-page {
    padding: 16px 12px 22px;
  }

  .topbar {
    margin-bottom: 14px;
  }

  .back-home {
    padding: 8px 14px;
    font-size: 13px;
  }

  .intro-panel {
    padding: 10px 2px 0;
  }

  .intro-title {
    margin-top: 14px;
    max-width: none;
  }

  .intro-desc {
    margin-top: 14px;
  }

  .intro-highlights {
    margin-top: 20px;
    gap: 12px;
  }

  .auth-card {
    border-radius: 22px;
    padding: 20px 16px 18px;
  }
}
</style>
