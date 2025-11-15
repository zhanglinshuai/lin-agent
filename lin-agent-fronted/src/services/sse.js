export function openEmotionSSE(message, chatId, userId, onChunk, onDone, onError) {
    const params = new URLSearchParams({message, chatId, userId})
    const es = new EventSource(`/api/ai/emotion/chat/sse/emitter?${params.toString()}`)
    let closed = false
    es.onmessage = (e) => {
        onChunk && onChunk(e.data)
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
    return {close}
}

export function openManusSSE(message, onChunk, onDone, onError) {
    const params = new URLSearchParams({message})
    const es = new EventSource(`/api/ai/manus/chat?${params.toString()}`)
    let closed = false
    es.onmessage = (e) => {
        onChunk && onChunk(e.data)
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
    return {close}
}