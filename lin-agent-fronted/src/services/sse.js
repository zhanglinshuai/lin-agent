export function openUnifiedSSE(message, chatId, userId, mode, allowFileTool, allowWebSearchTool, allowKnowledgeBase, uploadedFiles, requestId, resume, onEvent, onDone, onError) {
    const params = new URLSearchParams({ message, chatId, userId, mode })
    if (allowFileTool) {
        params.set('allowFileTool', 'true')
    }
    if (allowWebSearchTool) {
        params.set('allowWebSearchTool', 'true')
    }
    if (allowKnowledgeBase) {
        params.set('allowKnowledgeBase', 'true')
    }
    if (uploadedFiles && uploadedFiles.length) {
        params.set('uploadedFiles', JSON.stringify(uploadedFiles))
    }
    if (requestId) {
        params.set('requestId', String(requestId))
    }
    if (resume) {
        params.set('resume', 'true')
    }
    const es = new EventSource(`/api/ai/assistant/chat/sse/emitter?${params.toString()}`)
    let closed = false
    es.onmessage = (e) => {
        let payload = e.data
        try {
            payload = JSON.parse(e.data)
        } catch (err) {
        }
        onEvent && onEvent(payload)
        const eventType = typeof payload === 'object' && payload
            ? String(payload.type || '').toLowerCase()
            : ''
        if (eventType === 'done') {
            closed = true
            onDone && onDone(payload)
            es.close()
            return
        }
        if (eventType === 'error') {
            closed = true
            es.close()
        }
    }
    es.onerror = (e) => {
        if (!closed) onError && onError(e)
        es.close()
    }
    const close = () => {
        closed = true
        es.close()
    }
    return { close, complete: () => onDone && onDone() }
}
