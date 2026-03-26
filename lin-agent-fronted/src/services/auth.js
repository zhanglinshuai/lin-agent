import axios from 'axios'

export function parseAuthResponse(raw) {
  let token = null
  let userId = null

  if (typeof raw === 'string') {
    userId = raw
  } else if (raw && typeof raw === 'object') {
    token = raw.token ?? raw.data?.token ?? null
    if (typeof raw.data === 'string') {
      userId = raw.data
    }
    userId = userId ?? raw.userId ?? raw.data?.userId ?? null
  }

  return {
    token: token ? String(token) : '',
    userId: userId ? String(userId) : '',
  }
}

export async function loginWithPassword(userName, userPassword) {
  const res = await axios.post('/api/user/login', { userName, userPassword })
  const session = parseAuthResponse(res.data)
  if (!session.token && !session.userId) {
    throw new Error((res.data && res.data.message) || '登录失败：未返回用户ID或token')
  }
  return session
}

export async function registerWithPassword(username, userPassword, checkPassword) {
  const res = await axios.post('/api/user/register', { username, userPassword, checkPassword })
  const raw = res.data
  return {
    userId: typeof raw?.data === 'string' ? String(raw.data) : '',
    raw,
  }
}

export function saveAuthSession({ token = '', userId = '', userName = '', userAvatar = '' }) {
  try {
    if (token) localStorage.setItem('auth_token', String(token))
    if (userId) localStorage.setItem('user_id', String(userId))
    if (userName) localStorage.setItem('user_name', String(userName))
    if (userAvatar) {
      localStorage.setItem('user_avatar', String(userAvatar))
    }
  } catch (e) {
  }
}

export function clearAuthSession() {
  try { localStorage.removeItem('auth_token') } catch (e) {}
  try { localStorage.removeItem('user_id') } catch (e) {}
  try { localStorage.removeItem('user_name') } catch (e) {}
  try { localStorage.removeItem('user_avatar') } catch (e) {}
}

export function emitAuthEvent(name, detail = {}) {
  try { window.dispatchEvent(new CustomEvent(name, { detail })) } catch (e) {}
  try { document.dispatchEvent(new CustomEvent(name, { detail })) } catch (e) {}
}
