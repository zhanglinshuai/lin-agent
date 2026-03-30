import axios from 'axios'

function unwrapResponse(raw) {
  if (raw && typeof raw === 'object' && 'data' in raw) {
    return raw.data
  }
  return raw
}

export async function getAdminDashboard() {
  const res = await axios.get('/api/admin/dashboard')
  return unwrapResponse(res.data)
}

export async function getAdminLogs(limit = 300) {
  const res = await axios.get('/api/admin/logs', { params: { limit } })
  return unwrapResponse(res.data)
}

export async function queryAdminLogs(params = {}) {
  const res = await axios.get('/api/admin/logs', { params })
  return unwrapResponse(res.data)
}

export async function clearAdminLogs() {
  const res = await axios.delete('/api/admin/logs')
  return unwrapResponse(res.data)
}

export async function listKnowledgeDocuments() {
  const res = await axios.get('/api/admin/knowledge/documents')
  return unwrapResponse(res.data)
}

export async function getKnowledgeDocument(fileName) {
  const res = await axios.get('/api/admin/knowledge/document', { params: { fileName } })
  return unwrapResponse(res.data)
}

export async function saveKnowledgeDocument(payload) {
  const res = await axios.post('/api/admin/knowledge/document', payload)
  return unwrapResponse(res.data)
}

export async function deleteKnowledgeDocument(fileName, rebuildIndex = true) {
  const res = await axios.delete('/api/admin/knowledge/document', { params: { fileName, rebuildIndex } })
  return unwrapResponse(res.data)
}

export async function rebuildKnowledgeIndex() {
  const res = await axios.post('/api/admin/knowledge/rebuild')
  return unwrapResponse(res.data)
}

export async function uploadKnowledgeDocument(file, rebuildIndex = true, onUploadProgress) {
  const formData = new FormData()
  formData.append('file', file)
  formData.append('rebuildIndex', rebuildIndex ? 'true' : 'false')
  const res = await axios.post('/api/admin/knowledge/upload', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
    onUploadProgress,
  })
  return unwrapResponse(res.data)
}

export async function getKnowledgeUploadTask(taskId) {
  const res = await axios.get('/api/admin/knowledge/task', { params: { taskId } })
  return unwrapResponse(res.data)
}

export async function listAdminUsers(params = {}) {
  const res = await axios.get('/api/admin/users', { params })
  return unwrapResponse(res.data)
}

export async function updateAdminUserRole(userId, userRole) {
  const res = await axios.post('/api/admin/user/role', { userId, userRole })
  return unwrapResponse(res.data)
}

export async function updateAdminUserDeleteState(userId, deleted) {
  const res = await axios.post('/api/admin/user/delete-state', { userId, deleted })
  return unwrapResponse(res.data)
}

export async function listAdminConversations(params = {}) {
  const res = await axios.get('/api/admin/conversations', { params })
  return unwrapResponse(res.data)
}

export async function deleteAdminConversation(conversationId, userId = '') {
  const res = await axios.delete('/api/admin/conversation', { params: { conversationId, userId } })
  return unwrapResponse(res.data)
}
