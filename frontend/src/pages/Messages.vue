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
const imageInputRef = ref<HTMLInputElement | null>(null)

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

function openImagePicker() {
  if (!activePeerId.value) {
    message.warning('请先选择一个会话')
    return
  }
  imageInputRef.value?.click()
}

async function handleImageSelected(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return
  if (!file.type.startsWith('image/')) {
    message.warning('请选择图片文件')
    input.value = ''
    return
  }
  textInput.value = `[图片] ${file.name}`
  input.value = ''
  await handleSend()
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
            <input
              ref="imageInputRef"
              type="file"
              accept="image/*"
              class="hidden-file-input"
              @change="handleImageSelected"
            />
            <button class="icon-btn" @click="openImagePicker">
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
  height: calc(100vh - 112px);
  min-height: 620px;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  gap: 18px;
  padding: 8px 0 24px;
  color: var(--cf-text-primary);
}

.header-banner,
.main-container,
.chat-container {
  width: min(100%, 1040px);
  margin: 0 auto;
}

.header-banner {
  min-height: 72px;
  padding: 14px 4px 0;
  display: flex;
  align-items: center;
  gap: 14px;

  .page-title {
    margin: 0;
    display: flex;
    align-items: center;
    gap: 10px;
    font-size: 26px;
    font-weight: 900;
    letter-spacing: 0;
  }
}

.title-icon {
  color: var(--cf-primary);
}

.action-btn {
  width: 42px;
  height: 42px;
  border: 1px solid var(--cf-border);
  border-radius: 12px;
  background: var(--cf-bg-readable);
  color: var(--cf-text-secondary);
  display: inline-grid;
  place-items: center;
  cursor: pointer;
  box-shadow: var(--cf-shadow-soft);
  transition: color 0.2s ease, border-color 0.2s ease, background 0.2s ease;

  &:hover {
    color: var(--cf-primary);
    border-color: color-mix(in srgb, var(--cf-primary) 42%, transparent);
    background: var(--cf-primary-soft);
  }
}

.main-container {
  min-height: 0;
  flex: 1;
  overflow-y: auto;
}

.list-card,
.chat-header,
.chat-input-area {
  background: var(--cf-card-bg);
  border: 1px solid var(--cf-card-border);
  border-radius: 20px;
  box-shadow: var(--cf-card-shadow);
  backdrop-filter: blur(24px) saturate(150%);
  -webkit-backdrop-filter: blur(24px) saturate(150%);
}

.list-card {
  min-height: 420px;
  padding: 8px;
}

.conv-list {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.conv-item {
  min-height: 78px;
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 14px 16px;
  border-radius: 14px;
  cursor: pointer;
  transition: background 0.2s ease, transform 0.2s ease;

  &:hover {
    background: color-mix(in srgb, var(--cf-primary) 7%, transparent);
  }
}

.conv-avatar,
.peer-avatar,
.bubble-avatar {
  border-radius: 50%;
  color: #fff;
  background: linear-gradient(135deg, var(--cf-primary), #00a88f);
  display: grid;
  place-items: center;
  font-weight: 900;
  flex: 0 0 auto;
}

.conv-avatar {
  width: 46px;
  height: 46px;
  font-size: 18px;
}

.conv-body {
  flex: 1;
  min-width: 0;
}

.conv-header,
.conv-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.conv-name {
  color: var(--cf-text-primary);
  font-size: 16px;
  font-weight: 850;
}

.conv-time {
  color: var(--cf-text-muted);
  font-size: 12px;
  white-space: nowrap;
}

.conv-preview {
  margin: 6px 0 0;
  min-width: 0;
  color: var(--cf-text-secondary);
  font-size: 14px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.unread-tag {
  flex: 0 0 auto;
  font-weight: 900;
}

.chat-container {
  height: 100%;
  min-height: 0;
  display: flex;
  flex-direction: column;
  gap: 14px;
  overflow: hidden;
}

.chat-header {
  min-height: 68px;
  padding: 12px 16px;
  display: flex;
  align-items: center;
  gap: 14px;
  flex: 0 0 auto;
}

.chat-peer-info {
  display: flex;
  align-items: center;
  gap: 12px;
}

.peer-avatar {
  width: 38px;
  height: 38px;
}

.peer-name {
  font-size: 18px;
  font-weight: 850;
}

.chat-messages {
  min-height: 0;
  flex: 1 1 auto;
  overflow-y: auto;
  padding: 14px 8px;
}

.message-list {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.chat-bubble-wrapper {
  display: flex;
  align-items: flex-end;
  gap: 10px;

  &.mine {
    justify-content: flex-end;
  }
}

.bubble-avatar {
  width: 34px;
  height: 34px;
  font-size: 13px;

  &.mine {
    background: linear-gradient(135deg, #f97316, #ef4444);
  }
}

.bubble-content {
  max-width: min(620px, 72%);
  display: flex;
  flex-direction: column;
}

.bubble-box {
  padding: 12px 16px;
  border: 1px solid var(--cf-border);
  border-radius: 16px 16px 16px 6px;
  background: var(--cf-bg-readable);
  box-shadow: var(--cf-shadow-soft);
}

.bubble-text {
  margin: 0;
  color: var(--cf-text-primary);
  font-size: 15px;
  line-height: 1.6;
  word-break: break-word;
}

.bubble-image {
  max-width: 100%;
  margin-top: 8px;
  border-radius: 10px;
}

.bubble-time {
  margin-top: 6px;
  margin-left: 4px;
  color: var(--cf-text-muted);
  font-size: 12px;
}

.chat-bubble-wrapper.mine {
  .bubble-content {
    align-items: flex-end;
  }

  .bubble-box {
    border-color: transparent;
    border-radius: 16px 16px 6px 16px;
    background: linear-gradient(135deg, var(--cf-primary), #00a88f);
    box-shadow: 0 14px 34px rgba(0, 191, 168, 0.2);
  }

  .bubble-text {
    color: #fff;
  }

  .bubble-time {
    margin-left: 0;
    margin-right: 4px;
  }
}

.chat-input-area {
  min-height: 78px;
  padding: 12px;
  display: flex;
  align-items: flex-end;
  gap: 10px;
  flex: 0 0 auto;
}

.input-actions {
  padding-bottom: 6px;
}

.hidden-file-input {
  display: none;
}

.icon-btn,
.send-btn {
  border: 0;
  cursor: pointer;
  display: grid;
  place-items: center;
}

.icon-btn {
  width: 36px;
  height: 36px;
  border-radius: 10px;
  background: transparent;
  color: var(--cf-text-muted);

  &:hover {
    color: var(--cf-primary);
    background: var(--cf-primary-soft);
  }
}

.custom-input {
  flex: 1;
  --n-border: none !important;
  --n-border-hover: none !important;
  --n-border-focus: none !important;
  --n-box-shadow-focus: none !important;
  --n-color: transparent !important;
  --n-color-focus: transparent !important;

  :deep(.n-input__textarea-el) {
    font-size: 15px;
    line-height: 1.55;
  }
}

.send-btn {
  width: 42px;
  height: 42px;
  border-radius: 12px;
  color: #fff;
  background: var(--cf-primary);
  box-shadow: 0 12px 28px rgba(0, 191, 168, 0.24);

  &:disabled {
    opacity: 0.45;
    cursor: not-allowed;
    box-shadow: none;
  }
}

.loading-state,
.empty-state {
  min-height: 360px;
  padding: 48px 0;
  color: var(--cf-text-secondary);
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;

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

@media (max-width: 960px) {
  .messages-layout {
    height: auto;
    min-height: calc(100vh - 96px);
    overflow: visible;
    padding: 0 0 20px;
  }

  .header-banner,
  .main-container,
  .chat-container {
    width: 100%;
  }

  .chat-container {
    height: calc(100vh - 128px);
  }
}

@media (max-width: 560px) {
  .header-banner .page-title {
    font-size: 22px;
  }

  .conv-item {
    padding: 12px;
  }

  .bubble-content {
    max-width: 78%;
  }
}
</style>
