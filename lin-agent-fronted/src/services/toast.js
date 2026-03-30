export const GLOBAL_TOAST_EVENT = 'lin-agent:toast'

export function showGlobalToast(text, options = {}) {
  const message = String(text || '').trim()
  if (!message) {
    return
  }
  const detail = {
    text: message,
    type: String(options.type || 'info'),
    duration: Number(options.duration || 2600),
  }
  try {
    window.dispatchEvent(new CustomEvent(GLOBAL_TOAST_EVENT, { detail }))
  } catch (e) {
  }
}

export function normalizeToastDetail(detail) {
  return {
    text: String(detail?.text || '').trim(),
    type: String(detail?.type || 'info'),
    duration: Math.max(1200, Number(detail?.duration || 2600)),
  }
}
