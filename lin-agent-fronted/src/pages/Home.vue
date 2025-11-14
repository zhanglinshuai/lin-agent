<script setup>
import {useRouter} from 'vue-router'

const router = useRouter()

function goEmotion() {
  router.push('/emotion')
}

function goManus() {
  router.push('/manus')
}
</script>

<template>
  <div class="home">
    <div class="hero">
      <div class="hero-logo">✨</div>
      <h1 class="hero-title">AI 应用中心</h1>
      <p class="hero-desc">基于 Vue3 + SSE
        的实时对话体验，涵盖情感交流与多工具协作。支持侧边栏预览、历史对话复用与统一美观的界面风格。</p>
    </div>
    <div class="cards">
      <div class="card" @click="goEmotion">
        <div class="badge badge-1">Emotion</div>
        <h3>AI 情感大师</h3>
        <p>识别情绪、共情回应、疗愈陪伴</p>
      </div>
      <div class="card" @click="goManus">
        <div class="badge badge-2">Manus</div>
        <h3>AI 超级智能体</h3>
        <p>多工具协作、联网搜索、任务执行</p>
      </div>
    </div>
  </div>

</template>

<style scoped>
.home {
  min-height: 100vh;
  height: 100vh;
  width: 100%;
  padding: 40px 16px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  background: radial-gradient(1200px 600px at 50% -100px, rgba(99, 102, 241, 0.12), transparent),
  linear-gradient(180deg, rgba(0, 0, 0, 0.03), transparent);
}

.hero {
  max-width: 860px;
  text-align: center;
  margin-bottom: 28px;
}

.hero-logo {
  font-size: 32px;
}

.hero-title {
  font-size: 28px;
  font-weight: 800;
  letter-spacing: .3px;
  margin-top: 6px;
}

.hero-desc {
  margin-top: 8px;
  color: #6b7280;
}

.cards {
  width: 100%;
  max-width: 860px;
  display: grid;
  grid-template-columns: repeat(2, minmax(280px, 1fr));
  gap: 20px;
  justify-content: center;
}

.card {
  position: relative;
  border: 1px solid var(--color-border);
  border-radius: 16px;
  padding: 22px;
  cursor: pointer;
  background: #fff;
  box-shadow: 0 20px 30px rgba(0, 0, 0, 0.06);
}

.card:hover {
  border-color: var(--color-border-hover);
  box-shadow: 0 28px 44px rgba(0, 0, 0, 0.08);
  transform: translateY(-2px);
  transition: all .25s ease;
}

.card h3 {
  font-size: 18px;
  font-weight: 700;
}

.card p {
  font-size: 14px;
  color: #6b7280;
}

.badge {
  position: absolute;
  top: -12px;
  right: 12px;
  padding: 4px 10px;
  border-radius: 999px;
  font-size: 12px;
  color: #fff;
}

.badge-1 {
  background: linear-gradient(90deg, #8b5cf6, #22d3ee);
}

.badge-2 {
  background: linear-gradient(90deg, #f97316, #fb7185);
}
</style>