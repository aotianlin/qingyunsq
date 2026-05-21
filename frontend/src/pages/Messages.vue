<script setup lang="ts">
import { ref, onMounted, onUnmounted, nextTick, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { NInput, NTag, NSpin, useMessage, NIcon } from 'naive-ui'
import { 
  ChatbubblesOutline, 
  ArrowBackOutline, 
  SendOutline,
  ImageOutline
} from '@vicons/ionicons5'
import { listConversations, getConversation, sendMessage, markRead } from '@/api/messages'
import { useAuthStore } from '@/stores/auth'
import { useWebSocket } from '@/composables/useWebSocket'
import type { MessageVO } from '@/types/message'

const route = useRoute()
const router = useRouter()
const message = useMessage()
const authStore = useAuthStore()

const conversations = ref<MessageVO[]>([])
const chatMessages = ref<MessageVO[]>([])
const loading = ref(false)
const chatLoading = ref(false)
const textInput = ref('')
const sending = ref(false)
const activePeerId = ref<number | null>(null)
const activePeerName = ref('')
const chatContainer = ref<HTMLElement | null>(null)

const currentUserId = authStore.user?.id

async function loadConversations() {
  loading.value = true
  try {
    conversations.value = await listConversations()
  } catch { /* ignore */ }
  loading.value = false
}

async function loadChat(peerId: number) {
  chatLoading.value = true
  activePeerId.value = peerId
  try {
    chatMessages.value = await getConversation(peerId)
    // 正确获取对方昵称：从消息中找到对方发送的消息取 sender，或自己发送的消息取 receiver
    const peerMsg = chatMessages.value.find(m => m.senderId === peerId)
    const selfMsg = chatMessages.value.find(m => m.receiverId === peerId)
    activePeerName.value = peerMsg?.sender?.nickname || selfMsg?.receiver?.nickname || '用户'
    await markRead(peerId)
    await nextTick()
    scrollToBottom()
  } catch { /* ignore */ }
  chatLoading.value = false
}

function scrollToBottom() {
  if (chatContainer.value) {
    chatContainer.value.scrollTop = chatContainer.value.scrollHeight
  }
}

async function handleSend() {
  if (!textInput.value.trim() || !activePeerId.value) return
  sending.value = true
  try {
    const msg = await sendMessage(activePeerId.value, textInput.value)
    chatMessages.value.push(msg)
    textInput.value = ''
    await nextTick()
    scrollToBottom()
  } catch {
    message.error('发送失败')
  }
  sending.value = false
}

function openChat(peerId: number) {
  router.push({ query: { peer: peerId } })
}

function backToList() {
  router.push({ query: {} })
}

function peerIdFromMsg(msg: MessageVO): number {
  return msg.senderId === currentUserId ? msg.receiverId : msg.senderId
}

// 对话列表中显示对方的昵称（而非自己的）
function peerName(msg: MessageVO): string {
  // 如果当前用户是发送者，对方是接收者
  if (msg.senderId === currentUserId) {
    return msg.receiver?.nickname || '用户'
  }
  // 如果当前用户是接收者，对方是发送者
  return msg.sender?.nickname || '用户'
}

let pollTimer: ReturnType<typeof setInterval> | null = null

// 监听 WebSocket 消息事件，实时刷新对话
useWebSocket((event) => {
  if (event.type === 'MESSAGE') {
    // 收到新消息，刷新对话列表
    loadConversations()
    // 如果当前正在和发送者聊天，刷新聊天记录
    if (activePeerId.value && event.senderId === activePeerId.value) {
      loadChat(activePeerId.value)
    }
  }
})

onMounted(async () => {
  await loadConversations()
  const peerParam = route.query.peer
  if (peerParam) {
    loadChat(Number(peerParam))
  }
  // 保留低频轮询作为 WebSocket 断连时的兜底（30 秒）
  pollTimer = setInterval(loadConversations, 30000)
})

onUnmounted(() => {
  if (pollTimer) {
    clearInterval(pollTimer)
  }
})

watch(() => route.query.peer, (val) => {
  if (val) {
    loadChat(Number(val))
  } else {
    activePeerId.value = null
    chatMessages.value = []
  }
})
</script>

<template>
  <div class="messages-layout">
    <!-- 对话列表 -->
    <template v-if="!activePeerId">
      <div class="header-banner">
        <button
          class="action-btn back-btn"
          title="返回"
          @click="router.push('/square')"
        >
          <n-icon><ArrowBackOutline /></n-icon>
        </button>
        <h1 class="page-title gradient-text">
          <n-icon
            size="32"
            class="title-icon"
          >
            <ChatbubblesOutline />
          </n-icon>
          消息中心
        </h1>
      </div>

      <div class="main-container">
        <div class="glass-card list-card">
          <div
            v-if="loading"
            class="loading-state"
          >
            <n-spin size="large" />
          </div>
          <div
            v-else-if="conversations.length === 0"
            class="empty-state"
          >
            <n-icon
              size="64"
              color="#30363d"
            >
              <ChatbubblesOutline />
            </n-icon>
            <h3>暂无消息</h3>
            <p>去广场找人聊聊吧</p>
          </div>
          <div
            v-else
            class="conv-list"
          >
            <div
              v-for="msg in conversations"
              :key="msg.id"
              class="conv-item"
              @click="openChat(peerIdFromMsg(msg))"
            >
              <div class="conv-avatar">
                {{ peerName(msg).charAt(0) }}
              </div>
              <div class="conv-body">
                <div class="conv-header">
                  <span class="conv-name">{{ peerName(msg) }}</span>
                  <span class="conv-time">{{ new Date(msg.createdAt).toLocaleDateString() }}</span>
                </div>
                <div class="conv-footer">
                  <p class="conv-preview">
                    {{ msg.content || '[图片]' }}
                  </p>
                  <n-tag
                    v-if="!msg.isRead && msg.senderId !== currentUserId"
                    type="error"
                    size="small"
                    round
                    class="unread-tag"
                  >
                    新
                  </n-tag>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </template>

    <!-- 聊天窗口 -->
    <template v-else>
      <div class="chat-container">
        <div class="chat-header glass-card">
          <button
            class="action-btn back-btn"
            @click="backToList"
          >
            <n-icon><ArrowBackOutline /></n-icon>
          </button>
          <div class="chat-peer-info">
            <div class="peer-avatar">
              {{ activePeerName.charAt(0) }}
            </div>
            <span class="peer-name">{{ activePeerName }}</span>
          </div>
        </div>

        <div
          ref="chatContainer"
          class="chat-messages"
        >
          <div
            v-if="chatLoading"
            class="loading-state"
          >
            <n-spin size="large" />
          </div>
          <div
            v-else-if="chatMessages.length === 0"
            class="empty-state"
          >
            <n-icon
              size="64"
              color="#30363d"
            >
              <ChatbubblesOutline />
            </n-icon>
            <p>暂无消息，发送一条吧</p>
          </div>
          <div
            v-else
            class="message-list"
          >
            <div
              v-for="m in chatMessages"
              :key="m.id"
              class="chat-bubble-wrapper"
              :class="{ mine: m.senderId === currentUserId }"
            >
              <div
                v-if="m.senderId !== currentUserId"
                class="bubble-avatar"
              >
                {{ m.sender?.nickname?.charAt(0) || '?' }}
              </div>
              <div class="bubble-content">
                <div class="bubble-box">
                  <p
                    v-if="m.content"
                    class="bubble-text"
                  >
                    {{ m.content }}
                  </p>
                  <img
                    v-if="m.imageUrl"
                    :src="m.imageUrl"
                    class="bubble-image"
                  />
                </div>
                <span class="bubble-time">{{ new Date(m.createdAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }) }}</span>
              </div>
              <div
                v-if="m.senderId === currentUserId"
                class="bubble-avatar mine"
              >
                我
              </div>
            </div>
          </div>
        </div>

        <div class="chat-input-area glass-card">
          <div class="input-actions">
            <!-- 预留图片上传按钮 -->
            <button class="icon-btn">
              <n-icon size="22">
                <ImageOutline />
              </n-icon>
            </button>
          </div>
          <n-input
            v-model:value="textInput"
            type="textarea"
            :autosize="{ minRows: 1, maxRows: 4 }"
            placeholder="输入消息..."
            class="custom-input"
            @keydown.enter.prevent="handleSend"
          />
          <button
            class="neon-btn send-btn"
            :disabled="sending || !textInput.trim()"
            @click="handleSend"
          >
            <n-icon
              v-if="!sending"
              size="18"
            >
              <SendOutline />
            </n-icon>
            <n-spin
              v-else
              size="small"
              stroke="white"
            />
          </button>
        </div>
      </div>
    </template>
  </div>
</template>

<style scoped lang="scss">
.messages-layout {
  height: 100vh;
  overflow: hidden;
  background: transparent;
  background-image: radial-gradient(circle at 20% 80%, rgba(99, 102, 241, 0.08), transparent 40%);
  display: flex;
  flex-direction: column;
}

/* Common Header */
.header-banner {
  max-width: 800px;
  width: 100%;
  margin: 0 auto;
  padding: 40px 24px 24px;
  display: flex;
  align-items: center;
  gap: 16px;

  .page-title {
    font-size: 32px;
    font-weight: 800;
    margin: 0;
    display: flex;
    align-items: center;
    gap: 12px;
  }
}

.action-btn {
  width: 40px;
  height: 40px;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.05);
  border: 1px solid var(--cf-border);
  color: var(--cf-text-primary);
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  font-size: 20px;
  transition: all 0.3s;

  &:hover {
    background: rgba(255, 255, 255, 0.1);
    transform: translateY(-2px);
  }
}

/* List View */
.main-container {
  max-width: 800px;
  width: 100%;
  margin: 0 auto;
  padding: 0 24px 40px;
  flex: 1;
  overflow-y: auto;
}

.list-card {
  padding: 12px 0;
  min-height: 400px;
}

.conv-list {
  display: flex;
  flex-direction: column;
}

.conv-item {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 16px 24px;
  cursor: pointer;
  transition: background 0.3s;

  &:hover {
    background: rgba(255, 255, 255, 0.03);
  }

  .conv-avatar {
    width: 48px;
    height: 48px;
    border-radius: 50%;
    background: linear-gradient(135deg, #38bdf8 0%, #818cf8 100%);
    color: white;
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: 20px;
    font-weight: bold;
    flex-shrink: 0;
  }

  .conv-body {
    flex: 1;
    min-width: 0;
    
    .conv-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-bottom: 4px;

      .conv-name {
        font-weight: 600;
        font-size: 16px;
        color: var(--cf-text-primary);
      }
      .conv-time {
        font-size: 12px;
        color: var(--cf-text-secondary);
      }
    }

    .conv-footer {
      display: flex;
      justify-content: space-between;
      align-items: center;
      
      .conv-preview {
        margin: 0;
        font-size: 14px;
        color: var(--cf-text-secondary);
        white-space: nowrap;
        overflow: hidden;
        text-overflow: ellipsis;
      }
      
      .unread-tag {
        font-weight: bold;
      }
    }
  }
}

/* Chat View */
.chat-container {
  max-width: 800px;
  width: 100%;
  margin: 0 auto;
  display: flex;
  flex-direction: column;
  flex: 1;
  height: 100%;
  padding: 24px;
  gap: 16px;
  /* 确保聊天容器占满可用高度，输入框固定在底部 */
  min-height: 0;
  overflow: hidden;
}

.chat-header {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 16px 24px;
  border-radius: 16px;

  .chat-peer-info {
    display: flex;
    align-items: center;
    gap: 12px;
    
    .peer-avatar {
      width: 36px;
      height: 36px;
      border-radius: 50%;
      background: linear-gradient(135deg, #38bdf8 0%, #818cf8 100%);
      color: white;
      display: flex;
      align-items: center;
      justify-content: center;
      font-weight: bold;
    }
    
    .peer-name {
      font-size: 18px;
      font-weight: 600;
      color: var(--cf-text-primary);
    }
  }
}

.chat-messages {
  flex: 1;
  overflow-y: auto;
  padding: 16px;
  min-height: 0;
  
  /* hide scrollbar for cleaner look */
  &::-webkit-scrollbar {
    width: 6px;
  }
  &::-webkit-scrollbar-thumb {
    background: rgba(255, 255, 255, 0.1);
    border-radius: 3px;
  }
}

.message-list {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.chat-bubble-wrapper {
  display: flex;
  gap: 12px;
  align-items: flex-end;
  
  &.mine {
    justify-content: flex-end;
  }

  .bubble-avatar {
    width: 36px;
    height: 36px;
    border-radius: 50%;
    background: linear-gradient(135deg, #38bdf8 0%, #818cf8 100%);
    color: white;
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: 14px;
    font-weight: bold;
    flex-shrink: 0;
    
    &.mine {
      background: var(--cf-gradient-primary);
    }
  }

  .bubble-content {
    display: flex;
    flex-direction: column;
    max-width: 70%;
    
    .bubble-box {
      padding: 12px 16px;
      border-radius: 16px;
      border-bottom-left-radius: 4px;
      background: rgba(255, 255, 255, 0.05);
      border: 1px solid var(--cf-border);
      
      .bubble-text {
        margin: 0;
        font-size: 15px;
        line-height: 1.5;
        color: var(--cf-text-primary);
        word-break: break-word;
      }
      
      .bubble-image {
        max-width: 100%;
        border-radius: 8px;
        margin-top: 8px;
      }
    }
    
    .bubble-time {
      font-size: 12px;
      color: var(--cf-text-secondary);
      margin-top: 6px;
      margin-left: 4px;
    }
  }
  
  &.mine .bubble-content {
    align-items: flex-end;
    
    .bubble-box {
      border-bottom-left-radius: 16px;
      border-bottom-right-radius: 4px;
      background: var(--cf-gradient-primary);
      border: none;
      box-shadow: 0 4px 15px rgba(99, 102, 241, 0.2);
      
      .bubble-text {
        color: white;
      }
    }
    
    .bubble-time {
      margin-left: 0;
      margin-right: 4px;
    }
  }
}

.chat-input-area {
  display: flex;
  align-items: flex-end;
  gap: 12px;
  padding: 16px;
  border-radius: 16px;
  flex-shrink: 0;
  
  .input-actions {
    display: flex;
    padding-bottom: 4px;
    
    .icon-btn {
      background: transparent;
      border: none;
      color: var(--cf-text-secondary);
      cursor: pointer;
      padding: 4px;
      border-radius: 8px;
      transition: all 0.3s;
      
      &:hover {
        color: var(--cf-text-primary);
        background: rgba(255, 255, 255, 0.05);
      }
    }
  }

  .custom-input {
    flex: 1;
    background: transparent;
    --n-border: none !important;
    --n-border-hover: none !important;
    --n-border-focus: none !important;
    --n-box-shadow-focus: none !important;
    --n-color: transparent !important;
    --n-color-focus: transparent !important;
    
    :deep(.n-input__textarea-el) {
      font-size: 15px;
      line-height: 1.5;
    }
  }

  .send-btn {
    width: 44px;
    height: 44px;
    padding: 0;
    border-radius: 12px;
    display: flex;
    align-items: center;
    justify-content: center;
    flex-shrink: 0;
    
    &:disabled {
      opacity: 0.5;
      cursor: not-allowed;
      transform: none;
      box-shadow: none;
    }
  }
}

.loading-state, .empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
  color: var(--cf-text-secondary);
  padding: 60px 0;
  
  h3 {
    margin: 16px 0 8px;
    color: var(--cf-text-primary);
    font-size: 18px;
  }
  
  p {
    margin: 0;
    font-size: 14px;
  }
}
</style>
