<script setup>
import { computed, ref } from 'vue'
import { loginWithPassword, registerWithPassword, saveAuthSession, emitAuthEvent } from '@/services/auth'

const props = defineProps({
  initialMode: {
    type: String,
    default: 'login',
  },
})

const emit = defineEmits(['success'])

const mode = ref(props.initialMode === 'register' ? 'register' : 'login')
const account = ref('')
const password = ref('')
const confirmPassword = ref('')
const loading = ref(false)
const errorMsg = ref('')

const isRegister = computed(() => mode.value === 'register')
const submitText = computed(() => {
  if (loading.value) {
    return isRegister.value ? '注册中...' : '登录中...'
  }
  return isRegister.value ? '注册并登录' : '登录'
})

function switchMode(nextMode) {
  mode.value = nextMode
  errorMsg.value = ''
  password.value = ''
  confirmPassword.value = ''
}

function validate() {
  const userName = account.value.trim()
  if (!userName) {
    errorMsg.value = '请输入用户名'
    return false
  }
  if (userName.length > 10) {
    errorMsg.value = '用户名不能超过10个字符'
    return false
  }
  if (!password.value) {
    errorMsg.value = '请输入密码'
    return false
  }
  if (password.value.length < 6) {
    errorMsg.value = '密码长度不能少于6位'
    return false
  }
  if (isRegister.value) {
    if (!confirmPassword.value) {
      errorMsg.value = '请再次输入密码'
      return false
    }
    if (password.value !== confirmPassword.value) {
      errorMsg.value = '两次输入的密码不一致'
      return false
    }
  }
  errorMsg.value = ''
  return true
}

async function handleSubmit() {
  if (loading.value || !validate()) {
    return
  }

  const userName = account.value.trim()
  loading.value = true

  try {
    if (isRegister.value) {
      await registerWithPassword(userName, password.value, confirmPassword.value)
    }

    const session = await loginWithPassword(userName, password.value)
    saveAuthSession({
      token: session.token,
      userId: session.userId,
      userName,
    })
    emitAuthEvent('auth:login', {
      userId: session.userId,
      userName,
    })
    emit('success', {
      userId: session.userId,
      userName,
      registered: isRegister.value,
    })
  } catch (e) {
    errorMsg.value = e?.response?.data?.message || e?.message || (isRegister.value ? '注册失败' : '登录失败')
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="auth-form">
    <div class="auth-tabs">
      <button class="auth-tab" type="button" :class="{ active: !isRegister }" @click="switchMode('login')">登录</button>
      <button class="auth-tab" type="button" :class="{ active: isRegister }" @click="switchMode('register')">注册</button>
    </div>
    <div class="auth-hint" v-if="isRegister">注册成功后会自动登录并进入系统。</div>
    <div class="auth-fields">
      <label class="auth-label">用户名</label>
      <input class="auth-input" v-model="account" autocomplete="username" placeholder="请输入用户名" @keydown.enter="handleSubmit" />
      <label class="auth-label">密码</label>
      <input class="auth-input" type="password" v-model="password" autocomplete="current-password" placeholder="请输入密码" @keydown.enter="handleSubmit" />
      <template v-if="isRegister">
        <label class="auth-label">确认密码</label>
        <input class="auth-input" type="password" v-model="confirmPassword" autocomplete="new-password" placeholder="请再次输入密码" @keydown.enter="handleSubmit" />
      </template>
      <div class="auth-error" v-if="errorMsg">{{ errorMsg }}</div>
      <button class="auth-submit" :disabled="loading" @click="handleSubmit">{{ submitText }}</button>
    </div>
  </div>
</template>

<style scoped>
.auth-form {
  width: 100%;
}

.auth-tabs {
  display: flex;
  gap: 8px;
  margin-bottom: 14px;
  padding: 4px;
  border-radius: 12px;
  background: #f5f7fb;
}

.auth-tab {
  flex: 1;
  border: none;
  border-radius: 10px;
  background: transparent;
  color: #5b6472;
  font-size: 14px;
  font-weight: 600;
  padding: 10px 12px;
  cursor: pointer;
  transition: all .2s ease;
}

.auth-tab.active {
  background: #ffffff;
  color: #111827;
  box-shadow: 0 6px 18px rgba(15, 23, 42, 0.08);
}

.auth-hint {
  margin-bottom: 12px;
  font-size: 12px;
  line-height: 1.5;
  color: #5b6472;
}

.auth-fields {
  display: flex;
  flex-direction: column;
  gap: 9px;
}

.auth-label {
  font-size: 13px;
  color: #374151;
}

.auth-input {
  width: 100%;
  border: 1px solid #d7dce5;
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.94);
  padding: 12px 14px;
  font-size: 16px;
  outline: none;
  transition: border-color .2s ease, box-shadow .2s ease;
}

.auth-input:focus {
  border-color: #5b8def;
  box-shadow: 0 0 0 3px rgba(91, 141, 239, 0.12);
}

.auth-error {
  font-size: 13px;
  color: #dc2626;
}

.auth-submit {
  margin-top: 6px;
  border: none;
  border-radius: 12px;
  background: linear-gradient(135deg, #2f6cf6 0%, #4f8cff 100%);
  color: #fff;
  font-size: 14px;
  font-weight: 700;
  min-height: 46px;
  padding: 11px 14px;
  cursor: pointer;
  transition: transform .2s ease, box-shadow .2s ease, opacity .2s ease;
  box-shadow: 0 12px 24px rgba(47, 108, 246, 0.2);
}

.auth-submit:hover:not(:disabled) {
  transform: translateY(-1px);
}

.auth-submit:disabled {
  opacity: .7;
  cursor: not-allowed;
  box-shadow: none;
}

@media (max-width: 640px) {
  .auth-tabs {
    margin-bottom: 12px;
  }

  .auth-tab {
    font-size: 13px;
    padding: 9px 10px;
  }

  .auth-hint,
  .auth-label,
  .auth-error {
    font-size: 12px;
  }

  .auth-input {
    padding: 11px 12px;
  }
}
</style>
