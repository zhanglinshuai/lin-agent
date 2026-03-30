import axios from 'axios'

function unwrapResponse(raw) {
  if (raw && typeof raw === 'object' && 'data' in raw) {
    return raw.data
  }
  return raw
}

export async function generateEmotionReport(payload) {
  const res = await axios.post('/api/ai/emotion/report', payload)
  return unwrapResponse(res.data)
}
