import { api } from './request'

export interface ChatStreamRequest {
  conversationId?: number
  kbId: number
  question: string
}

export interface ConversationResponse {
  id: number
  kbId: number
  title: string
  createTime: string
  updateTime: string
}

export interface MessageResponse {
  id: number
  role: 'user' | 'assistant'
  content: string
  rewrittenQuery: string | null
  needRetrieval: boolean
  llmProvider: string | null
  createTime: string
}

export interface SourceReference {
  docId: number
  fileName: string
  excerpt: string
}

/** SSE event types */
export enum SseEventType {
  Rewrite = 'rewrite',
  Token = 'token',
  Sources = 'sources',
  Error = 'error',
  Done = 'done',
}

export interface RewriteEvent {
  type: SseEventType.Rewrite
  needRetrieval: boolean
  rewrittenQuery: string
}

export interface TokenEvent {
  type: SseEventType.Token
  content: string
}

export interface SourcesEvent {
  type: SseEventType.Sources
  sources: SourceReference[]
}

export interface ErrorEvent {
  type: SseEventType.Error
  message: string
}

export interface DoneEvent {
  type: SseEventType.Done
}

export type SseEvent = RewriteEvent | TokenEvent | SourcesEvent | ErrorEvent | DoneEvent

/** List conversations */
export function listConversationsApi(kbId: number, page = 1, size = 20) {
  return api.get<ConversationResponse[]>('/chat/conversations', {
    params: { kbId, page, size },
  })
}

/** List messages in a conversation */
export function listMessagesApi(conversationId: number, page = 1, size = 50) {
  return api.get<MessageResponse[]>(`/chat/messages/${conversationId}`, {
    params: { page, size },
  })
}

/**
 * Send a chat question via SSE
 * Returns an AbortController for stopping the stream
 */
export function createChatStream(
  data: ChatStreamRequest,
  onEvent: (event: SseEvent) => void,
  onError?: (error: Error) => void,
  onComplete?: () => void,
): AbortController {
  const controller = new AbortController()
  const stored = localStorage.getItem('kb-auth')
  let accessToken = ''
  try {
    if (stored) accessToken = JSON.parse(stored).accessToken || ''
  } catch {
    // ignore
  }

  fetch('/api/chat/stream', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${accessToken}`,
    },
    body: JSON.stringify(data),
    signal: controller.signal,
  })
    .then(async (response) => {
      if (!response.ok) {
        throw new Error(`HTTP ${response.status}: ${response.statusText}`)
      }

      const reader = response.body?.getReader()
      if (!reader) {
        throw new Error('Response body is not readable')
      }

      const decoder = new TextDecoder()
      let buffer = ''

      const dispatchEvent = (eventBlock: string) => {
        const data = eventBlock
          .split(/\r?\n/)
          .filter((line) => line.startsWith('data:'))
          .map((line) => line.slice(5).replace(/^ /, ''))
          .join('\n')

        if (!data) return

        try {
          onEvent(JSON.parse(data) as SseEvent)
        } catch {
          console.warn('Failed to parse SSE event data:', data)
        }
      }

      const dispatchBufferedEvents = () => {
        let boundary = buffer.match(/\r?\n\r?\n/)
        while (boundary?.index !== undefined) {
          const eventBlock = buffer.slice(0, boundary.index)
          buffer = buffer.slice(boundary.index + boundary[0].length)
          dispatchEvent(eventBlock)
          boundary = buffer.match(/\r?\n\r?\n/)
        }
      }

      while (true) {
        const { done, value } = await reader.read()
        if (done) break

        buffer += decoder.decode(value, { stream: true })
        dispatchBufferedEvents()
      }

      buffer += decoder.decode()
      dispatchBufferedEvents()
      if (buffer.trim()) dispatchEvent(buffer)

      onComplete?.()
    })
    .catch((err) => {
      if (err.name !== 'AbortError') {
        onError?.(err)
      }
    })

  return controller
}
