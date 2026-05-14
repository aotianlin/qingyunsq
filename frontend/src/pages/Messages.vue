<script setup lang="ts">
import { ref, onMounted, nextTick, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { NButton, NInput, NCard, NTag, NSpace, NSpin, NEmpty, useMessage } from 'naive-ui'
import { listConversations, getConversation, sendMessage, markRead } from '@/api/messages'
import { useAuthStore } from '@/stores/auth'
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
    const peer = chatMessages.value.find(m => m.senderId === peerId || m.receiverId === peerId)
    activePeerName.value = peer?.sender?.nickname || '用户'
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

function peerName(msg: MessageVO): string {
  return msg.sender?.nickname || '用户'
}

// Poll for new messages every 5 seconds
let pollTimer: ReturnType<typeof setInterval> | null = null

onMounted(async () => {
  await loadConversations()
  const peerParam = route.query.peer
  if (peerParam) {
    loadChat(Number(peerParam))
  }
  pollTimer = setInterval(loadConversations, 5000)
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
  <div class="messages-page">
    <!-- 对话列表 -->
    <template v-if="!activePeerId">
      <NCard title="私信" class="conv-card">
        <template v-if="loading">
          <NSpin />
        </template>
        <template v-else-if="conversations.length === 0">
          <NEmpty description="暂无私信" />
        </template>
        <template v-else>
          <div
            v-for="msg in conversations"
            :key="msg.id"
            class="conv-item"
            @click="openChat(peerIdFromMsg(msg))"
          >
            <div class="conv-avatar">{{ peerName(msg).charAt(0) }}</div>
            <div class="conv-body">
              <div class="conv-header">
                <span class="conv-name">{{ peerName(msg) }}</span>
                <span class="conv-time">{{ new Date(msg.createdAt).toLocaleDateString() }}</span>
              </div>
              <p class="conv-preview">{{ msg.content || '[图片]' }}</p>
            </div>
            <NTag v-if="!msg.isRead && msg.senderId !== currentUserId" type="error" size="tiny">新</NTag>
          </div>
        </template>
      </NCard>
    </template>

    <!-- 聊天窗口 -->
    <template v-else>
      <NCard class="chat-card">
        <template #header>
          <div class="chat-header-bar">
            <NButton size="small" text @click="backToList">← 返回</NButton>
            <span class="chat-peer-name">{{ activePeerName }}</span>
          </div>
        </template>

        <div ref="chatContainer" class="chat-messages">
          <template v-if="chatLoading">
            <NSpin />
          </template>
          <template v-else-if="chatMessages.length === 0">
            <NEmpty description="暂无消息，发送一条吧" />
          </template>
          <template v-else>
            <div
              v-for="m in chatMessages"
              :key="m.id"
              class="chat-bubble"
              :class="{ mine: m.senderId === currentUserId }"
            >
              <div class="bubble-avatar">{{ m.sender?.nickname?.charAt(0) || '?' }}</div>
              <div class="bubble-content">
                <p v-if="m.content" class="bubble-text">{{ m.content }}</p>
                <img
                  v-if="m.imageUrl"
                  :src="m.imageUrl"
                  class="bubble-image"
                  style="max-width: 200px; border-radius: 8px;"
                />
                <span class="bubble-time">{{ new Date(m.createdAt).toLocaleTimeString() }}</span>
              </div>
            </div>
          </template>
        </div>

        <div class="chat-input-area">
          <NInput
            v-model:value="textInput"
            placeholder="输入消息..."
            @keydown.enter="handleSend"
          />
          <NButton type="primary" size="small" :loading="sending" @click="handleSend">发送</NButton>
        </div>
      </NCard>
    </template>
  </div>
</template>

<style scoped>
.messages-page {
  max-width: 680px;
  margin: 24px auto;
  padding: 0 16px;
}
.conv-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 8px;
  border-bottom: 1px solid #f0f0f0;
  cursor: pointer;
}
.conv-item:hover { background: #f9f9f9; }
.conv-avatar {
  width: 40px;
  height: 40px;
  border-radius: 50%;
  background: #18a058;
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 16px;
  flex-shrink: 0;
}
.conv-body { flex: 1; min-width: 0; }
.conv-header { display: flex; justify-content: space-between; margin-bottom: 2px; }
.conv-name { font-weight: 600; font-size: 14px; }
.conv-time { font-size: 12px; color: #999; }
.conv-preview { margin: 0; font-size: 13px; color: #666; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }

.chat-card { display: flex; flex-direction: column; height: calc(100vh - 120px); }
.chat-card :deep(.n-card__content) { flex: 1; display: flex; flex-direction: column; overflow: hidden; }
.chat-header-bar { display: flex; align-items: center; gap: 12px; }
.chat-peer-name { font-weight: 600; }

.chat-messages { flex: 1; overflow-y: auto; padding: 8px 0; }
.chat-bubble { display: flex; gap: 8px; margin-bottom: 16px; align-items: flex-start; }
.chat-bubble.mine { flex-direction: row-reverse; }
.chat-bubble.mine .bubble-content { align-items: flex-end; }
.bubble-avatar {
  width: 32px; height: 32px; border-radius: 50%;
  background: #18a058; color: #fff;
  display: flex; align-items: center; justify-content: center;
  font-size: 14px; flex-shrink: 0;
}
.chat-bubble.mine .bubble-avatar { background: #2080f0; }
.bubble-content { display: flex; flex-direction: column; max-width: 70%; }
.bubble-text {
  margin: 0; padding: 8px 12px; border-radius: 12px; background: #f0f0f0;
  font-size: 14px; line-height: 1.5; word-break: break-word;
}
.chat-bubble.mine .bubble-text { background: #18a058; color: #fff; }
.bubble-time { font-size: 11px; color: #bbb; margin-top: 2px; }

.chat-input-area { display: flex; gap: 8px; padding-top: 12px; border-top: 1px solid #f0f0f0; align-items: center; }
.chat-input-area :deep(.n-input) { flex: 1; }
</style>
