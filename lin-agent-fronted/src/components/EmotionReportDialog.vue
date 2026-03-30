<script setup>
const props = defineProps({
  visible: {
    type: Boolean,
    default: false,
  },
  loading: {
    type: Boolean,
    default: false,
  },
  report: {
    type: Object,
    default: () => null,
  },
})

const emit = defineEmits(['close', 'export'])
</script>

<template>
  <div v-if="visible" class="report-mask" @click.self="emit('close')">
    <div class="report-shell">
      <div class="report-topbar">
        <div>
          <div class="report-kicker">Emotion Report</div>
          <div class="report-title">情感报告</div>
          <div class="report-subtitle">把当前对话整理成一份更适合留存和导出的结构化报告。</div>
        </div>
        <div class="report-actions">
          <button class="report-action secondary" type="button" @click="emit('close')">关闭</button>
          <button class="report-action primary" type="button" :disabled="loading || !report" @click="emit('export')">
            {{ loading ? '生成中...' : '导出 PDF' }}
          </button>
        </div>
      </div>

      <div class="report-preview-frame">
        <div v-if="loading" class="report-loading">
          <div class="report-loading-dot"></div>
          <div class="report-loading-text">正在生成情感报告...</div>
        </div>

        <div v-else-if="report" class="report-page">
          <div class="report-page-kicker">Lingban Emotional Care</div>
          <div class="report-page-title">{{ report.title || '未命名情感报告' }}</div>
          <div class="report-page-meta">生成时间：{{ report.generatedAt || '刚刚' }}</div>

          <section class="report-section">
            <div class="report-section-label">情绪概览</div>
            <p class="report-section-text">{{ report.snapshot || '暂无概览内容' }}</p>
          </section>

          <section class="report-grid">
            <div class="report-card">
              <div class="report-card-title">关注重点</div>
              <ul class="report-list">
                <li v-for="(item, index) in report.keyPoints || []" :key="`key-${index}`">{{ item }}</li>
              </ul>
            </div>

            <div class="report-card">
              <div class="report-card-title">下一步建议</div>
              <ul class="report-list">
                <li v-for="(item, index) in report.suggestions || []" :key="`suggestion-${index}`">{{ item }}</li>
              </ul>
            </div>
          </section>

          <section class="report-section">
            <div class="report-section-label">可立即尝试的行动</div>
            <ol class="report-action-list">
              <li v-for="(item, index) in report.actions || []" :key="`action-${index}`">{{ item }}</li>
            </ol>
          </section>

          <section v-if="report.closingMessage" class="report-closing">
            <div class="report-closing-mark">温柔收尾</div>
            <p>{{ report.closingMessage }}</p>
          </section>
        </div>

        <div v-else class="report-empty">
          <div class="report-empty-title">还没有可预览的报告</div>
          <div class="report-empty-text">先在对话里选择一条情感类答复，再生成报告。</div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.report-mask {
  position: fixed;
  inset: 0;
  z-index: 70;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 20px;
  background: rgba(15, 23, 42, 0.54);
  backdrop-filter: blur(10px);
}

.report-shell {
  width: min(1120px, 100%);
  max-height: min(92vh, 920px);
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.report-topbar {
  display: flex;
  justify-content: space-between;
  gap: 20px;
  align-items: flex-start;
  border-radius: 26px;
  padding: 22px 24px;
  background: linear-gradient(135deg, #10223d 0%, #173763 100%);
  color: #f8fafc;
  box-shadow: 0 24px 60px rgba(15, 23, 42, 0.22);
}

.report-kicker {
  font-size: 12px;
  font-weight: 800;
  letter-spacing: 0.12em;
  text-transform: uppercase;
  color: rgba(186, 230, 253, 0.9);
}

.report-title {
  margin-top: 8px;
  font-size: 28px;
  font-weight: 700;
}

.report-subtitle {
  margin-top: 8px;
  max-width: 42ch;
  font-size: 13px;
  line-height: 1.8;
  color: rgba(226, 232, 240, 0.82);
}

.report-actions {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
}

.report-action {
  min-height: 44px;
  border: none;
  border-radius: 14px;
  padding: 0 16px;
  font-size: 14px;
  font-weight: 700;
  cursor: pointer;
}

.report-action:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.report-action.primary {
  background: linear-gradient(135deg, #f59e0b 0%, #fb7185 100%);
  color: #fff;
}

.report-action.secondary {
  background: rgba(255, 255, 255, 0.12);
  color: #fff;
}

.report-preview-frame {
  flex: 1;
  min-height: 0;
  overflow: auto;
  border-radius: 28px;
  padding: 26px;
  background:
    radial-gradient(circle at top right, rgba(251, 191, 36, 0.16), transparent 26%),
    linear-gradient(180deg, #f6f1e8 0%, #efe8dc 100%);
}

.report-loading,
.report-empty {
  min-height: 420px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  text-align: center;
  color: #475569;
}

.report-loading-dot {
  width: 16px;
  height: 16px;
  border-radius: 50%;
  background: linear-gradient(135deg, #f59e0b 0%, #fb7185 100%);
  box-shadow: 0 0 0 8px rgba(245, 158, 11, 0.12);
  animation: pulse 1s ease-in-out infinite;
}

.report-loading-text,
.report-empty-text {
  margin-top: 14px;
  font-size: 14px;
  line-height: 1.8;
}

.report-empty-title {
  font-size: 24px;
  font-weight: 700;
  color: #1f2937;
}

.report-page {
  width: min(100%, 820px);
  margin: 0 auto;
  background: #fffdf8;
  border-radius: 24px;
  padding: 34px 38px;
  box-shadow: 0 22px 54px rgba(113, 63, 18, 0.14);
  color: #2b3442;
}

.report-page-kicker {
  display: inline-flex;
  align-items: center;
  min-height: 30px;
  border-radius: 999px;
  padding: 0 12px;
  background: rgba(245, 158, 11, 0.12);
  color: #b45309;
  font-size: 12px;
  font-weight: 800;
  letter-spacing: 0.08em;
}

.report-page-title {
  margin-top: 18px;
  font-family: "Iowan Old Style", "Georgia", "Songti SC", serif;
  font-size: 34px;
  line-height: 1.2;
  font-weight: 700;
  color: #111827;
}

.report-page-meta {
  margin-top: 10px;
  color: #8b7355;
  font-size: 13px;
}

.report-section {
  margin-top: 24px;
}

.report-section-label,
.report-card-title {
  font-size: 14px;
  font-weight: 800;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: #92400e;
}

.report-section-text {
  margin-top: 12px;
  font-size: 15px;
  line-height: 1.92;
  color: #374151;
}

.report-grid {
  margin-top: 24px;
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
}

.report-card {
  border-radius: 18px;
  padding: 18px;
  background: linear-gradient(180deg, rgba(255, 247, 237, 0.92) 0%, rgba(255, 255, 255, 0.96) 100%);
  border: 1px solid rgba(251, 191, 36, 0.18);
}

.report-list,
.report-action-list {
  margin: 12px 0 0;
  padding-left: 20px;
  color: #374151;
  font-size: 14px;
  line-height: 1.86;
}

.report-closing {
  margin-top: 26px;
  border-radius: 18px;
  padding: 18px 20px;
  background: linear-gradient(135deg, #fff7ed 0%, #fffbeb 100%);
  color: #7c2d12;
}

.report-closing-mark {
  font-size: 12px;
  font-weight: 800;
  letter-spacing: 0.12em;
  text-transform: uppercase;
  color: #ea580c;
}

.report-closing p {
  margin-top: 10px;
  font-size: 14px;
  line-height: 1.88;
}

@keyframes pulse {
  0% { transform: scale(0.92); opacity: 0.78; }
  50% { transform: scale(1); opacity: 1; }
  100% { transform: scale(0.92); opacity: 0.78; }
}

@media (max-width: 860px) {
  .report-topbar {
    flex-direction: column;
  }

  .report-grid {
    grid-template-columns: 1fr;
  }

  .report-page {
    padding: 24px 20px;
  }
}
</style>
