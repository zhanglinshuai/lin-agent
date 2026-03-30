import axios from 'axios'
import { clearAuthSession, emitAuthEvent } from '@/services/auth'

let initialized = false
let handledAuthError = false

function isBlobResponse(config) {
  const responseType = String(config?.responseType || '').toLowerCase()
  return responseType === 'blob'
}

function isApiEnvelope(payload) {
  return !!(payload && typeof payload === 'object' && 'code' in payload && 'message' in payload)
}

function buildBackendError(response, payload) {
  const message = typeof payload?.message === 'string' && payload.message.trim()
    ? payload.message.trim()
    : '请求失败'
  const error = new Error(message)
  error.name = 'BackendBusinessError'
  error.response = response
  error.backendCode = payload?.code
  error.backendMessage = message
  return error
}

function isAuthErrorCode(code) {
  return Number(code) === 4010 || Number(code) === 40101
}

function syncClientAuthState(payload) {
  if (!isAuthErrorCode(payload?.code) || handledAuthError) {
    return
  }
  handledAuthError = true
  try {
    clearAuthSession()
    emitAuthEvent('auth:logout', {
      reason: typeof payload?.message === 'string' ? payload.message : '',
    })
  } finally {
    setTimeout(() => {
      handledAuthError = false
    }, 0)
  }
}

async function unwrapBlobBusinessError(response) {
  const contentType = String(response?.headers?.['content-type'] || '').toLowerCase()
  if (!contentType.includes('application/json')) {
    return response
  }
  const blob = response?.data
  if (!(blob instanceof Blob)) {
    return response
  }
  const text = await blob.text()
  if (!text) {
    return response
  }
  let payload = null
  try {
    payload = JSON.parse(text)
  } catch (e) {
    return response
  }
  if (!isApiEnvelope(payload)) {
    return response
  }
  if (Number(payload.code) !== 0) {
    response.data = payload
    syncClientAuthState(payload)
    throw buildBackendError(response, payload)
  }
  response.data = payload.data
  return response
}

export function setupAxiosInterceptors() {
  if (initialized) {
    return
  }
  initialized = true
  axios.defaults.withCredentials = true
  axios.interceptors.response.use(
    async (response) => {
      if (isBlobResponse(response?.config)) {
        return unwrapBlobBusinessError(response)
      }
      const payload = response?.data
      if (!isApiEnvelope(payload)) {
        return response
      }
      if (Number(payload.code) !== 0) {
        syncClientAuthState(payload)
        throw buildBackendError(response, payload)
      }
      response.data = payload.data
      return response
    },
    (error) => Promise.reject(error),
  )
}
