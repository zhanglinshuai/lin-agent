<script setup>
import { onBeforeUnmount, watch } from 'vue'

const props = defineProps({
  visible: {
    type: Boolean,
    default: false,
  },
  title: {
    type: String,
    default: '请确认操作',
  },
  message: {
    type: String,
    default: '',
  },
  confirmText: {
    type: String,
    default: '确认',
  },
  cancelText: {
    type: String,
    default: '取消',
  },
  danger: {
    type: Boolean,
    default: false,
  },
})

const emit = defineEmits(['confirm', 'cancel'])

function handleKeydown(event) {
  if (event?.key === 'Escape') {
    emit('cancel')
  }
}

watch(
  () => props.visible,
  (visible) => {
    if (visible) {
      window.addEventListener('keydown', handleKeydown)
      return
    }
    window.removeEventListener('keydown', handleKeydown)
  },
  { immediate: true },
)

onBeforeUnmount(() => {
  window.removeEventListener('keydown', handleKeydown)
})
</script>

<template>
  <transition name="confirm-dialog-fade">
    <div v-if="visible" class="confirm-mask" @click.self="emit('cancel')">
      <div class="confirm-card" :class="{ danger }">
        <div class="confirm-badge">{{ danger ? '高风险操作' : '操作确认' }}</div>
        <h3 class="confirm-title">{{ title }}</h3>
        <p class="confirm-message">{{ message }}</p>
        <div class="confirm-actions">
          <button class="confirm-btn ghost" type="button" @click="emit('cancel')">{{ cancelText }}</button>
          <button class="confirm-btn solid" :class="{ danger }" type="button" @click="emit('confirm')">{{ confirmText }}</button>
        </div>
      </div>
    </div>
  </transition>
</template>

<style scoped>
.confirm-mask {
  position: fixed;
  inset: 0;
  z-index: 3200;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 20px;
  background: rgba(2, 6, 23, 0.58);
  backdrop-filter: blur(10px);
}

.confirm-card {
  width: min(100%, 420px);
  border-radius: 28px;
  border: 1px solid rgba(148, 163, 184, 0.22);
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.98), rgba(244, 248, 255, 0.96));
  box-shadow: 0 30px 80px rgba(15, 23, 42, 0.28);
  padding: 24px;
}

.confirm-card.danger {
  border-color: rgba(239, 68, 68, 0.22);
  background: linear-gradient(180deg, rgba(255, 250, 250, 0.98), rgba(254, 242, 242, 0.96));
}

.confirm-badge {
  display: inline-flex;
  align-items: center;
  min-height: 28px;
  border-radius: 999px;
  background: rgba(14, 165, 233, 0.12);
  color: #0369a1;
  padding: 0 12px;
  font-size: 12px;
  font-weight: 800;
  letter-spacing: 0.04em;
}

.confirm-card.danger .confirm-badge {
  background: rgba(239, 68, 68, 0.12);
  color: #b91c1c;
}

.confirm-title {
  margin-top: 16px;
  color: #0f172a;
  font-size: 26px;
  line-height: 1.2;
  font-weight: 900;
  font-family: "Bahnschrift", "Segoe UI Variable Display", "Microsoft YaHei UI", sans-serif;
}

.confirm-message {
  margin-top: 12px;
  color: #475569;
  font-size: 14px;
  line-height: 1.8;
}

.confirm-actions {
  margin-top: 24px;
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}

.confirm-btn {
  min-width: 108px;
  min-height: 44px;
  border-radius: 14px;
  border: none;
  padding: 0 18px;
  font-size: 14px;
  font-weight: 800;
  cursor: pointer;
  transition: transform 0.18s ease, box-shadow 0.18s ease, background 0.18s ease;
}

.confirm-btn:hover {
  transform: translateY(-1px);
}

.confirm-btn.ghost {
  border: 1px solid rgba(148, 163, 184, 0.24);
  background: rgba(255, 255, 255, 0.9);
  color: #334155;
}

.confirm-btn.solid {
  background: linear-gradient(135deg, #0f766e 0%, #0ea5e9 100%);
  color: #fff;
  box-shadow: 0 14px 26px rgba(14, 165, 233, 0.22);
}

.confirm-btn.solid.danger {
  background: linear-gradient(135deg, #b91c1c 0%, #ef4444 100%);
  box-shadow: 0 14px 26px rgba(239, 68, 68, 0.24);
}

.confirm-dialog-fade-enter-active,
.confirm-dialog-fade-leave-active {
  transition: opacity 0.2s ease, transform 0.2s ease;
}

.confirm-dialog-fade-enter-from,
.confirm-dialog-fade-leave-to {
  opacity: 0;
}

.confirm-dialog-fade-enter-from .confirm-card,
.confirm-dialog-fade-leave-to .confirm-card {
  transform: translateY(10px) scale(0.98);
}

@media (max-width: 640px) {
  .confirm-card {
    border-radius: 22px;
    padding: 20px;
  }

  .confirm-title {
    font-size: 22px;
  }

  .confirm-actions {
    flex-direction: column-reverse;
  }

  .confirm-btn {
    width: 100%;
  }
}
</style>
