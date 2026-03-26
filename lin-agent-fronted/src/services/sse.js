export function openUnifiedSSE(message, chatId, userId, mode, allowFileTool, allowWebSearchTool, uploadedFiles, onEvent, onDone, onError) {
    const params = new URLSearchParams({ message, chatId, userId, mode })
    if (allowFileTool) {
        params.set('allowFileTool', 'true')
    }
    if (allowWebSearchTool) {
        params.set('allowWebSearchTool', 'true')
    }
    if (uploadedFiles && uploadedFiles.length) {
        params.set('uploadedFiles', JSON.stringify(uploadedFiles))
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
    }
    es.onerror = (e) => {
        if (!closed) onError && onError(e)
        es.close()
    }
    const close = () => {
        closed = true
        es.close()
        onDone && onDone()
    }
    return { close }
}
