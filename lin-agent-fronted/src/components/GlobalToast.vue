<script setup>
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { GLOBAL_TOAST_EVENT, normalizeToastDetail } from '@/services/toast'

const visible = ref(false)
const text = ref('')
const type = ref('info')

let hideTimer = null

function clearHideTimer() {
  if (hideTimer) {
    clearTimeout(hideTimer)
    hideTimer = null
  }
}

function handleToast(event) {
  const detail = normalizeToastDetail(event?.detail)
  if (!detail.text) {
    return
  }
  text.value = detail.text
  type.value = detail.type
  visible.value = true
  clearHideTimer()
  hideTimer = setTimeout(() => {
    visible.value = false
    hideTimer = null
  }, detail.duration)
}

onMounted(() => {
  window.addEventListener(GLOBAL_TOAST_EVENT, handleToast)
})

onBeforeUnmount(() => {
  window.removeEventListener(GLOBAL_TOAST_EVENT, handleToast)
  clearHideTimer()
})
</script>

<template>
  <transition name="global-toast-fade">
    <div v-if="visible" class="global-toast" :class="`type-${type}`">{{ text }}</div>
  </transition>
</template>

<style scoped>
.global-toast {
  position: fixed;
  top: 24px;
  left: 50%;
  z-index: 4000;
  transform: translateX(-50%);
  max-width: min(88vw, 520px);
  border-radius: 16px;
  padding: 12px 18px;
  font-size: 14px;
  line-height: 1.6;
  color: #fff;
  box-shadow: 0 18px 42px rgba(15, 23, 42, 0.22);
  backdrop-filter: blur(12px);
  word-break: break-word;
}

.global-toast.type-error {
  background: rgba(190, 24, 93, 0.92);
}

.global-toast.type-success {
  background: rgba(21, 128, 61, 0.9);
}

.global-toast.type-info {
  background: rgba(15, 23, 42, 0.88);
}

.global-toast-fade-enter-active,
.global-toast-fade-leave-active {
  transition: opacity 0.2s ease, transform 0.2s ease;
}

.global-toast-fade-enter-from,
.global-toast-fade-leave-to {
  opacity: 0;
  transform: translate(-50%, -8px);
}
</style>
