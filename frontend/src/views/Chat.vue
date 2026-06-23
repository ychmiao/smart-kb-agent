<template>
  <div class="chat-page">
    <!-- Breadcrumb -->
    <div class="page-breadcrumb">
      <el-button text :icon="ArrowLeft" @click="goBack">返回知识库</el-button>
      <el-divider direction="vertical" />
      <span class="breadcrumb-kb-name">{{ kbName }}</span>
      <el-divider direction="vertical" />
      <span class="breadcrumb-current">智能问答</span>
    </div>

    <div class="chat-layout">
      <!-- Conversation sidebar -->
      <div class="conversation-panel" v-if="!hideConversations">
        <div class="conv-header">
          <h3>对话列表</h3>
          <el-button
            text
            type="primary"
            size="small"
            :icon="Plus"
            @click="startNewConversation"
          >
            新建
          </el-button>
        </div>

        <!-- Loading -->
        <div v-if="convLoading" class="conv-loading">
          <el-skeleton :rows="4" animated />
        </div>

        <!-- Empty -->
        <div v-else-if="conversations.length === 0" class="conv-empty">
          <span>暂无对话</span>
        </div>

        <!-- List -->
        <div v-else class="conv-list">
          <div
            v-for="conv in conversations"
            :key="conv.id"
            class="conv-item"
            :class="{ active: conv.id === currentConversationId }"
            @click="switchConversation(conv.id)"
          >
            <div class="conv-item-title">{{ conv.title || '新对话' }}</div>
            <div class="conv-item-time">{{ formatDateTime(conv.updateTime) }}</div>
          </div>
        </div>
      </div>

      <!-- Chat main area -->
      <div class="chat-main">
        <!-- Messages area -->
        <div class="messages-area" ref="messagesRef">
          <!-- Rewrite banner: shows before AI response streaming -->
          <div v-if="showRewriteBanner" class="rewrite-banner">
            <el-tag size="small" type="warning" effect="light" hit>
              问题已重写为：{{ streamRewrite }}
            </el-tag>
          </div>

          <!-- Loading messages -->
          <div v-if="msgLoading" class="msg-loading">
            <el-skeleton :rows="6" animated />
          </div>

          <!-- Empty -->
          <div v-else-if="messages.length === 0" class="msg-empty">
            <el-empty
              description="开始提问，获取知识库中的答案"
              :image-size="80"
            >
              <template #image>
                <el-icon :size="60" color="#c0c4cc"><ChatLineSquare /></el-icon>
              </template>
            </el-empty>
            <p class="msg-empty-hint">
              您可以询问关于知识库中文档的任何问题
            </p>
          </div>

          <!-- Messages -->
          <div v-else class="message-list">
            <div
              v-for="(msg, index) in messages"
              :key="msg.id || `temp-${index}`"
              class="message-item"
              :class="msg.role"
            >
              <div class="message-avatar">
                <el-avatar :size="36" :icon="msg.role === 'user' ? User : Promotion" />
              </div>
              <div class="message-bubble">
                <div
                  v-if="msg.role === 'assistant' && msg.rewrittenQuery && msg.needRetrieval"
                  class="rewrite-badge"
                >
                  <el-tag size="small" type="info" effect="plain" hit>
                    检索查询: {{ msg.rewrittenQuery }}
                  </el-tag>
                </div>
                <div v-if="msg.role === 'assistant' && msg.sources && msg.sources.length > 0" class="source-badge">
                  <el-popover placement="bottom" :width="360" trigger="click">
                    <template #reference>
                      <el-tag size="small" type="success" effect="plain" hit style="cursor:pointer">
                        参考了 {{ msg.sources.length }} 个来源
                      </el-tag>
                    </template>
                    <div class="source-list">
                      <div v-for="(src, si) in msg.sources" :key="si" class="source-item">
                        <el-icon><Document /></el-icon>
                        <div class="source-info">
                          <div class="source-file">{{ src.fileName }}</div>
                          <div class="source-excerpt">{{ src.excerpt }}</div>
                        </div>
                      </div>
                    </div>
                  </el-popover>
                </div>
                <div class="message-content markdown-content" v-html="renderContent(msg.content)"></div>
                <div v-if="msg.content && !isStreaming && msg.role === 'assistant'" class="llm-tag">
                  <el-tag
                    size="small"
                    :type="msg.needRetrieval ? 'success' : 'info'"
                    effect="plain"
                    round
                  >
                    {{ msg.needRetrieval ? '📚 知识库回答' : '✨ 通用回答' }}
                    <span v-if="msg.llmProvider"> · {{ formatProvider(msg.llmProvider) }}</span>
                  </el-tag>
                </div>
                <div v-if="msg.role === 'assistant' && isStreaming && !msg.content" class="streaming-dots">
                  <span class="dot"></span>
                  <span class="dot"></span>
                  <span class="dot"></span>
                </div>
              </div>
            </div>
          </div>
        </div>

        <!-- Input area -->
        <div class="input-area">
          <el-input
            v-model="question"
            type="textarea"
            :rows="3"
            :disabled="isStreaming || waitingResponse"
            placeholder="输入您的问题，按 Enter 发送（Shift+Enter 换行）"
            @keydown.enter.exact.prevent="sendQuestion"
          />
          <div class="input-actions">
            <el-button
              v-if="isStreaming || waitingResponse"
              type="danger"
              :icon="CircleClose"
              @click="stopStreaming"
            >
              停止
            </el-button>
            <el-button
              v-else
              type="primary"
              :icon="Promotion"
              :disabled="!question.trim()"
              @click="sendQuestion"
            >
              发送
            </el-button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, nextTick, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  ArrowLeft,
  Plus,
  User,
  Promotion,
  Document,
  Loading,
  CircleClose,
  ChatLineSquare,
} from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { marked } from '@/utils/marked'
import { listKnowledgeBasesApi } from '@/api/knowledgeBase'
import {
  listConversationsApi,
  listMessagesApi,
  createChatStream,
  SseEventType,
  type SseEvent,
  type ConversationResponse,
  type MessageResponse,
  type SourceReference,
} from '@/api/chat'
import { formatDateTime } from '@/utils/format'

const route = useRoute()
const router = useRouter()

const kbId = computed(() => Number(route.params.kbId))
const kbName = ref('')
const hideConversations = ref(false)

// Conversations
const convLoading = ref(false)
const conversations = ref<ConversationResponse[]>([])
const currentConversationId = ref<number | null>(null)

// Messages
const msgLoading = ref(false)
const messages = ref<
  Array<
    MessageResponse & {
      sources?: SourceReference[]
      rewrittenQuery?: string | null
      needRetrieval?: boolean
    }
  >
>([])

// Streaming state
const isStreaming = ref(false)
const waitingResponse = ref(false)
const question = ref('')
const streamRewrite = ref('')
const showRewriteBanner = ref(false)
const streamSources = ref<SourceReference[]>([])
const currentAiMessage = ref<any>(null)
const abortController = ref<AbortController | null>(null)

const messagesRef = ref<HTMLElement | null>(null)

function goBack() {
  router.push('/kb')
}

async function fetchKbName() {
  try {
    const kbs = await listKnowledgeBasesApi()
    const kb = kbs.find((k) => k.id === kbId.value)
    if (kb) kbName.value = kb.name
  } catch {
    // ignore
  }
}

async function fetchConversations() {
  convLoading.value = true
  try {
    conversations.value = await listConversationsApi(kbId.value)
  } catch {
    // ignore
  } finally {
    convLoading.value = false
  }
}

async function fetchMessages(conversationId: number) {
  msgLoading.value = true
  try {
    messages.value = await listMessagesApi(conversationId)
  } catch {
    // ignore
  } finally {
    msgLoading.value = false
  }
}

function switchConversation(conversationId: number) {
  if (isStreaming.value || waitingResponse.value) return
  currentConversationId.value = conversationId
  fetchMessages(conversationId)
}

function startNewConversation() {
  if (isStreaming.value || waitingResponse.value) return
  currentConversationId.value = null
  messages.value = []
  question.value = ''
}

async function sendQuestion() {
  const q = question.value.trim()
  if (!q || isStreaming.value || waitingResponse.value) return

  // Add user message
  const userMsg: MessageResponse = {
    id: Date.now(),
    role: 'user',
    content: q,
    rewrittenQuery: null,
    needRetrieval: false,
    llmProvider: null,
    createTime: new Date().toISOString(),
  }
  messages.value.push(userMsg)
  question.value = ''
  waitingResponse.value = true
  currentAiMessage.value = null
  streamRewrite.value = ''
  showRewriteBanner.value = false
  streamSources.value = []

  scrollToBottom()

  abortController.value = createChatStream(
    {
      conversationId: currentConversationId.value ?? undefined,
      kbId: kbId.value,
      question: q,
    },
    handleSseEvent,
    (err) => {
      waitingResponse.value = false
      isStreaming.value = false
      currentAiMessage.value = null
      ElMessage.error('请求失败: ' + err.message)
    },
    () => {
      // Complete
      waitingResponse.value = false
      isStreaming.value = false
      currentAiMessage.value = null
      abortController.value = null
      fetchConversations()
    },
  )

  waitingResponse.value = false
  isStreaming.value = true

  // Add a placeholder AI message that we update as tokens arrive
  const aiMsgId = -(Date.now())
  messages.value.push({
    id: aiMsgId,
    role: 'assistant' as const,
    content: '',
    rewrittenQuery: null,
    needRetrieval: false,
    llmProvider: null,
    sources: [] as SourceReference[],
    createTime: new Date().toISOString(),
  })
  // ⚡ Get the reactive proxy from the array so mutations trigger Vue re-render
  currentAiMessage.value = messages.value.find(m => m.id === aiMsgId) || null
}

function handleSseEvent(event: SseEvent) {
  const msg = currentAiMessage.value
  if (!msg) return

  switch (event.type) {
    case SseEventType.Rewrite:
      streamRewrite.value = event.rewrittenQuery
      showRewriteBanner.value = true
      msg.rewrittenQuery = event.rewrittenQuery
      msg.needRetrieval = event.needRetrieval
      break

    case SseEventType.Token:
      // Once the first token arrives, hide the rewrite banner if still visible
      if (showRewriteBanner.value) {
        showRewriteBanner.value = false
      }
      msg.content += event.content
      scrollToBottom()
      break

    case SseEventType.Sources:
      streamSources.value = event.sources
      msg.sources = event.sources
      break

    case SseEventType.Error:
      ElMessage.error(event.message)
      break

    case SseEventType.Done:
      // Message is already populated in-place; nothing more needed
      break
  }
}

function stopStreaming() {
  if (abortController.value) {
    abortController.value.abort()
    abortController.value = null
  }
  isStreaming.value = false
  waitingResponse.value = false

  // Mark partial response as interrupted
  const msg = currentAiMessage.value
  if (msg && msg.content) {
    msg.content += '\n\n*(已中断)*'
  }
  currentAiMessage.value = null
}

function scrollToBottom() {
  nextTick(() => {
    if (messagesRef.value) {
      messagesRef.value.scrollTop = messagesRef.value.scrollHeight
    }
  })
}

function renderContent(content: string): string {
  if (!content) return ''
  return marked(content)
}
function formatProvider(provider: string): string {
  const providerNames: Record<string, string> = {
    deepseek: 'DeepSeek',
    qwen: 'Qwen',
  }
  return providerNames[provider.toLowerCase()] || provider
}


// Scroll to bottom on new messages
watch(
  () => messages.value.length,
  () => scrollToBottom(),
)

onMounted(() => {
  fetchKbName()
  fetchConversations()
})

onUnmounted(() => {
  stopStreaming()
})
</script>

<style scoped>
.chat-page {
  height: calc(100vh - 48px);
  display: flex;
  flex-direction: column;
}

.page-breadcrumb {
  display: flex;
  align-items: center;
  margin-bottom: 12px;
  font-size: 14px;
  flex-shrink: 0;
}

.breadcrumb-kb-name {
  color: #1a1a2e;
  font-weight: 500;
}

.breadcrumb-current {
  color: #6b7280;
}

.chat-layout {
  display: flex;
  flex: 1;
  gap: 16px;
  overflow: hidden;
}

/* Conversation panel */
.conversation-panel {
  width: 260px;
  flex-shrink: 0;
  background: #fff;
  border-radius: 12px;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.conv-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px;
  border-bottom: 1px solid #f0f0f0;
}

.conv-header h3 {
  font-size: 15px;
  font-weight: 600;
  color: #1a1a2e;
}

.conv-loading,
.conv-empty {
  padding: 24px 16px;
  text-align: center;
  color: #9ca3af;
  font-size: 14px;
}

.conv-list {
  flex: 1;
  overflow-y: auto;
  padding: 8px;
}

.conv-item {
  padding: 10px 12px;
  border-radius: 8px;
  cursor: pointer;
  transition: background 0.15s;
  margin-bottom: 4px;
}

.conv-item:hover {
  background-color: #f3f4f6;
}

.conv-item.active {
  background-color: #eef2ff;
}

.conv-item-title {
  font-size: 14px;
  color: #374151;
  font-weight: 500;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.conv-item-time {
  font-size: 12px;
  color: #9ca3af;
  margin-top: 2px;
}

/* Chat main */
.chat-main {
  flex: 1;
  display: flex;
  flex-direction: column;
  background: #fff;
  border-radius: 12px;
  overflow: hidden;
}

.messages-area {
  flex: 1;
  overflow-y: auto;
  padding: 24px;
}

.msg-loading {
  padding: 20px;
}

.rewrite-banner {
  text-align: center;
  padding: 8px 16px;
  margin-bottom: 12px;
  animation: fadeSlideDown 0.25s ease-out;
}

@keyframes fadeSlideDown {
  from {
    opacity: 0;
    transform: translateY(-8px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.msg-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 400px;
}

.msg-empty-hint {
  color: #9ca3af;
  font-size: 14px;
  margin-top: 8px;
}

.message-list {
  max-width: 800px;
  margin: 0 auto;
}

.message-item {
  display: flex;
  gap: 12px;
  margin-bottom: 24px;
}

.message-item.user {
  flex-direction: row-reverse;
}

.message-avatar {
  flex-shrink: 0;
}

.message-bubble {
  max-width: 85%;
}

.message-item.user .message-bubble {
  background-color: #667eea;
  color: #fff;
  padding: 12px 16px;
  border-radius: 16px 16px 4px 16px;
}

.message-item.assistant .message-bubble {
  background-color: #f3f4f6;
  color: #374151;
  padding: 12px 16px;
  border-radius: 16px 16px 16px 4px;
}

.rewrite-badge {
  margin-bottom: 8px;
}

.source-badge {
  margin-bottom: 8px;
}

.source-list {
  max-height: 240px;
  overflow-y: auto;
}

.source-item {
  display: flex;
  gap: 8px;
  padding: 8px 0;
  border-bottom: 1px solid #f0f0f0;
}

.source-item:last-child {
  border-bottom: none;
}

.source-info {
  flex: 1;
  min-width: 0;
}

.source-file {
  font-weight: 500;
  font-size: 13px;
  color: #374151;
}

.source-excerpt {
  font-size: 12px;
  color: #6b7280;
  margin-top: 2px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.llm-tag {
  margin-top: 8px;
  text-align: right;
}

/* Streaming dots */
.streaming-dots {
  display: inline-flex;
  gap: 4px;
  margin-top: 8px;
}

.streaming-dots .dot {
  width: 6px;
  height: 6px;
  background: #667eea;
  border-radius: 50%;
  animation: bounce 1.4s infinite ease-in-out both;
}

.streaming-dots .dot:nth-child(1) {
  animation-delay: -0.32s;
}
.streaming-dots .dot:nth-child(2) {
  animation-delay: -0.16s;
}

@keyframes bounce {
  0%, 80%, 100% {
    transform: scale(0);
  }
  40% {
    transform: scale(1);
  }
}

/* Input area */
.input-area {
  padding: 16px 24px;
  border-top: 1px solid #f0f0f0;
}

.input-actions {
  display: flex;
  justify-content: flex-end;
  margin-top: 8px;
}
</style>
