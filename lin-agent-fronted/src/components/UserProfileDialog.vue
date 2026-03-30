<script setup>
import { computed, ref, watch } from 'vue'

const props = defineProps({
  visible: {
    type: Boolean,
    default: false,
  },
  profile: {
    type: Object,
    default: () => ({}),
  },
  saving: {
    type: Boolean,
    default: false,
  },
  uploadingAvatar: {
    type: Boolean,
    default: false,
  },
})

const emit = defineEmits(['close', 'save', 'upload-avatar'])

const fileInputRef = ref(null)
const draft = ref({
  id: '',
  userName: '',
  userPhone: '',
  userAvatar: '',
  createTimeLabel: '',
  updateTimeLabel: '',
})

const canSubmit = computed(() => !props.saving && draft.value.userName.trim().length > 0)

watch(
  () => [props.visible, props.profile],
  ([visible]) => {
    if (!visible) {
      return
    }
    draft.value = {
      id: String(props.profile?.id || ''),
      userName: String(props.profile?.userName || ''),
      userPhone: String(props.profile?.userPhone || ''),
      userAvatar: String(props.profile?.userAvatar || ''),
      createTimeLabel: String(props.profile?.createTimeLabel || ''),
      updateTimeLabel: String(props.profile?.updateTimeLabel || ''),
    }
  },
  { immediate: true, deep: true },
)

function triggerAvatarUpload() {
  fileInputRef.value?.click()
}

function handleAvatarChange(event) {
  const file = event?.target?.files?.[0]
  if (!file) {
    return
  }
  emit('upload-avatar', file)
  if (event?.target) {
    event.target.value = ''
  }
}

function handleSave() {
  if (!canSubmit.value) {
    return
  }
  emit('save', {
    id: draft.value.id,
    userName: draft.value.userName.trim(),
    userPhone: draft.value.userPhone.trim(),
  })
}
</script>

<template>
  <div v-if="visible" class="profile-modal-mask" @click.self="emit('close')">
    <div class="profile-modal-card">
      <button class="profile-close" type="button" @click="emit('close')">关闭</button>

      <div class="profile-hero">
        <div class="profile-avatar-wrap">
          <img :src="draft.userAvatar" alt="avatar" class="profile-avatar" />
          <button class="profile-avatar-btn" type="button" @click="triggerAvatarUpload">
            {{ uploadingAvatar ? '上传中...' : '更换头像' }}
          </button>
          <input ref="fileInputRef" type="file" accept="image/*" style="display:none" @change="handleAvatarChange" />
        </div>

        <div class="profile-hero-copy">
          <div class="profile-kicker">个人主页</div>
          <h3 class="profile-title">{{ draft.userName || '未命名用户' }}</h3>
          <p class="profile-subtitle">在这里查看和维护你的基础资料，后续对话会同步使用最新信息。</p>
        </div>
      </div>

      <div class="profile-section">
        <div class="profile-section-title">基础资料</div>
        <div class="profile-grid">
          <label class="profile-field">
            <span class="profile-label">用户名</span>
            <input v-model="draft.userName" class="profile-input" maxlength="10" placeholder="请输入用户名" />
          </label>
          <label class="profile-field">
            <span class="profile-label">手机号</span>
            <input v-model="draft.userPhone" class="profile-input" maxlength="20" placeholder="请输入手机号" />
          </label>
        </div>
      </div>

      <div class="profile-section">
        <div class="profile-section-title">账号信息</div>
        <div class="profile-info-list">
          <div class="profile-info-item">
            <span class="profile-info-label">用户 ID</span>
            <span class="profile-info-value">{{ draft.id || '未分配' }}</span>
          </div>
          <div class="profile-info-item">
            <span class="profile-info-label">创建时间</span>
            <span class="profile-info-value">{{ draft.createTimeLabel || '暂无记录' }}</span>
          </div>
          <div class="profile-info-item">
            <span class="profile-info-label">最近更新</span>
            <span class="profile-info-value">{{ draft.updateTimeLabel || '暂无记录' }}</span>
          </div>
        </div>
      </div>

      <div class="profile-actions">
        <button class="profile-secondary" type="button" @click="emit('close')">取消</button>
        <button class="profile-primary" type="button" :disabled="!canSubmit" @click="handleSave">
          {{ saving ? '保存中...' : '保存资料' }}
        </button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.profile-modal-mask {
  position: fixed;
  inset: 0;
  z-index: 22;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 18px;
  background: rgba(15, 23, 42, 0.32);
  backdrop-filter: blur(10px);
}

.profile-modal-card {
  position: relative;
  width: min(760px, 100%);
  max-height: calc(100dvh - 36px);
  overflow-y: auto;
  border-radius: 28px;
  background: rgba(255, 255, 255, 0.97);
  box-shadow: 0 30px 70px rgba(15, 23, 42, 0.18);
  padding: clamp(20px, 2vw, 28px);
}

.profile-close {
  position: absolute;
  top: 18px;
  right: 18px;
  border: none;
  border-radius: 999px;
  background: rgba(241, 245, 249, 0.96);
  color: #475569;
  font-size: 12px;
  font-weight: 800;
  padding: 8px 12px;
  cursor: pointer;
}

.profile-hero {
  display: flex;
  gap: 18px;
  align-items: center;
  padding-right: 88px;
}

.profile-avatar-wrap {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
}

.profile-avatar {
  width: 92px;
  height: 92px;
  border-radius: 28px;
  object-fit: cover;
  box-shadow: 0 20px 34px rgba(36, 89, 216, 0.16);
}

.profile-avatar-btn {
  border: none;
  border-radius: 999px;
  background: rgba(36, 89, 216, 0.1);
  color: #2459d8;
  font-size: 12px;
  font-weight: 800;
  padding: 8px 12px;
  cursor: pointer;
}

.profile-kicker {
  font-size: 12px;
  font-weight: 800;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: #2459d8;
}

.profile-title {
  margin-top: 10px;
  font-size: clamp(26px, 2.2vw, 34px);
  line-height: 1.1;
  font-weight: 900;
  color: #0f172a;
}

.profile-subtitle {
  margin-top: 10px;
  font-size: 14px;
  line-height: 1.8;
  color: #64748b;
}

.profile-section {
  margin-top: 22px;
  border-radius: 22px;
  background: rgba(248, 250, 252, 0.92);
  border: 1px solid rgba(148, 163, 184, 0.14);
  padding: 18px;
}

.profile-section-title {
  font-size: 15px;
  font-weight: 800;
  color: #0f172a;
}

.profile-grid {
  margin-top: 14px;
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
}

.profile-field {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.profile-label {
  font-size: 13px;
  font-weight: 700;
  color: #475569;
}

.profile-input {
  width: 100%;
  border: 1px solid rgba(148, 163, 184, 0.24);
  border-radius: 14px;
  background: rgba(255, 255, 255, 0.96);
  color: #0f172a;
  font-size: 14px;
  line-height: 1.5;
  padding: 12px 14px;
  outline: none;
  transition: border-color .18s ease, box-shadow .18s ease;
}

.profile-input:focus {
  border-color: rgba(36, 89, 216, 0.42);
  box-shadow: 0 0 0 4px rgba(36, 89, 216, 0.12);
}

.profile-info-list {
  margin-top: 14px;
  display: grid;
  gap: 12px;
}

.profile-info-item {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  align-items: center;
  padding: 12px 14px;
  border-radius: 16px;
  background: rgba(255, 255, 255, 0.94);
}

.profile-info-label {
  font-size: 13px;
  color: #64748b;
}

.profile-info-value {
  min-width: 0;
  text-align: right;
  font-size: 13px;
  font-weight: 700;
  color: #0f172a;
  word-break: break-all;
}

.profile-actions {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  margin-top: 22px;
}

.profile-secondary,
.profile-primary {
  border: none;
  border-radius: 14px;
  font-size: 14px;
  font-weight: 800;
  padding: 12px 18px;
  cursor: pointer;
}

.profile-secondary {
  background: rgba(241, 245, 249, 0.96);
  color: #1f2937;
}

.profile-primary {
  background: linear-gradient(135deg, #2459d8 0%, #22d3ee 100%);
  color: #fff;
  box-shadow: 0 16px 28px rgba(36, 89, 216, 0.18);
}

.profile-primary:disabled {
  opacity: 0.55;
  cursor: not-allowed;
  box-shadow: none;
}

@media (max-width: 720px) {
  .profile-modal-mask {
    padding: 14px;
    align-items: flex-end;
  }

  .profile-modal-card {
    width: 100%;
    max-height: min(88dvh, 760px);
    border-radius: 26px 26px 20px 20px;
  }

  .profile-hero {
    flex-direction: column;
    align-items: flex-start;
    padding-right: 0;
  }

  .profile-grid {
    grid-template-columns: 1fr;
  }

  .profile-info-item {
    flex-direction: column;
    align-items: flex-start;
  }

  .profile-info-value {
    text-align: left;
  }

  .profile-actions {
    flex-direction: column-reverse;
  }

  .profile-secondary,
  .profile-primary {
    width: 100%;
  }
}
</style>
