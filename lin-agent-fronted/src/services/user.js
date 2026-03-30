import axios from 'axios'

function unwrapResponse(raw) {
  if (raw && typeof raw === 'object' && 'data' in raw) {
    return raw.data
  }
  return raw
}

export async function getUserProfile(userId) {
  const res = await axios.get('/api/user/getUserInfo', { params: { userId } })
  return unwrapResponse(res.data)
}

export async function updateUserProfile(payload) {
  const res = await axios.post('/api/user/updateUserInfo', payload)
  return unwrapResponse(res.data)
}

export async function uploadUserAvatar({ file, userId }) {
  const formData = new FormData()
  formData.append('file', file)
  formData.append('userId', userId)
  const res = await axios.post('/api/user/uploadAvatar', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  })
  return String(unwrapResponse(res.data) || '')
}
