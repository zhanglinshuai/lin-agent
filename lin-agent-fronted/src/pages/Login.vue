<script setup>
import { ref, computed } from 'vue'
import axios from 'axios'
import { useRouter } from 'vue-router'

const router = useRouter()
const account = ref('')
const password = ref('')
const loading = ref(false)
const errorMsg = ref('')

const canSubmit = computed(() => account.value.trim() && password.value.trim())

async function tryLoginOnce(url, payload) {
  try {
    const res = await axios.post(url, payload)
    return res
  } catch (e) {
    throw e
  }
}

async function handleSubmit() {
  errorMsg.value = ''
  if (!canSubmit.value || loading.value) return
  loading.value = true
  let res
  try {
    res = await tryLoginOnce('/api/user/login', { userName: account.value, userPassword: password.value })
  } catch (e) {
    errorMsg.value = (e?.response?.data?.message) || (e?.message) || '登录失败'
  }
  loading.value = false
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
    errorMsg.value = (raw && raw.message) || '登录失败：未返回用户ID或token'
    return
  }
  try {
    if (token) localStorage.setItem('auth_token', String(token))
    if (userId) localStorage.setItem('user_id', String(userId))
    localStorage.setItem('user_name', String(account.value))
  } catch(e) {}
  router.push('/')
}
</script>

<template>
  <div class="login-page">
    <div class="login-card">
      <div class="title">登录</div>
      <div class="form">
        <label class="label">用户名</label>
        <input class="input" v-model="account" placeholder="请输入用户名" />
        <label class="label">密码</label>
        <input class="input" type="password" v-model="password" placeholder="请输入密码" />
        <div class="error" v-if="errorMsg">{{ errorMsg }}</div>
        <button class="submit" :disabled="!canSubmit || loading" @click="handleSubmit">
          {{ loading ? '登录中...' : '登录' }}
        </button>
      </div>
      <div class="doc-tip">
        接口文档：<a href="http://localhost:8080/api/doc.html#/home" target="_blank">userLogin</a>
      </div>
    </div>
  </div>
  
  
</template>

<style scoped>
.login-page {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 100vh;
  padding: 24px 16px;
}
.login-card {
  width: min(420px, 96vw);
  border: 1px solid var(--color-border);
  border-radius: 16px;
  background: #fff;
  box-shadow: 0 30px 40px rgba(0, 0, 0, 0.06), 0 10px 24px rgba(0, 0, 0, 0.04);
  padding: 20px;
}
.title {
  font-size: 20px;
  font-weight: 800;
  margin-bottom: 12px;
}
.form {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.label {
  font-size: 13px;
  opacity: .8;
}
.input {
  border: 1px solid var(--color-border);
  border-radius: 8px;
  padding: 10px 12px;
  font-size: 15px;
  outline: none;
}
.error {
  color: #ef4444;
  font-size: 13px;
  min-height: 18px;
}
.submit {
  margin-top: 8px;
  padding: 10px 12px;
  border-radius: 999px;
  border: none;
  background: #2459d8;
  color: #fff;
  font-weight: 700;
}
.submit:disabled {
  opacity: .6;
  cursor: not-allowed;
}
.doc-tip { margin-top: 8px; font-size: 12px; opacity: .6; }
@media (max-width: 480px) {
  .login-card { border-radius: 12px; }
}
</style>