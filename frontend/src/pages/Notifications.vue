<script setup lang="ts">
import { ref, onMounted } from 'vue';
import { useRouter } from 'vue-router';
import {
  NSpin, NBadge, useMessage, NIcon
} from 'naive-ui';
import {
  HeartOutline, ChatbubbleOutline, ReturnDownBackOutline,
  CheckmarkCircleOutline, PeopleOutline, ArrowBackOutline,
  NotificationsOutline, CheckmarkDoneOutline
} from '@vicons/ionicons5';
import { getNotifications, getUnreadCount, markRead, markAllRead } from '@/api/notifications';
import type { NotificationVO } from '@/types/notification';
import type { Component } from 'vue';

const router = useRouter();
const message = useMessage();
const notifications = ref<NotificationVO[]>([]);
const unreadCount = ref(0);
const loading = ref(false);
const hasMore = ref(true);

const typeIcons: Record<string, Component> = {
  LIKE: HeartOutline,
  COMMENT: ChatbubbleOutline,
  REPLY: ReturnDownBackOutline,
  ACCEPT: CheckmarkCircleOutline,
  JOIN: PeopleOutline,
};

const typeColors: Record<string, string> = {
  LIKE: '#f43f5e',
  COMMENT: '#3b82f6',
  REPLY: '#10b981',
  ACCEPT: '#f59e0b',
  JOIN: '#8b5cf6',
};

const typeBgColors: Record<string, string> = {
  LIKE: 'rgba(244, 63, 94, 0.15)',
  COMMENT: 'rgba(59, 130, 246, 0.15)',
  REPLY: 'rgba(16, 185, 129, 0.15)',
  ACCEPT: 'rgba(245, 158, 11, 0.15)',
  JOIN: 'rgba(139, 92, 246, 0.15)',
};

function getTypeIcon(type: string): Component {
  return typeIcons[type] || ChatbubbleOutline;
}

function getTypeColor(type: string): string {
  return typeColors[type] || '#8b949e';
}

function getTypeBg(type: string): string {
  return typeBgColors[type] || 'rgba(139, 148, 158, 0.15)';
}

async function loadNotifications(reset = false) {
  if (loading.value) return;
  loading.value = true;
  try {
    const cursor = reset ? undefined : notifications.value[notifications.value.length - 1]?.id;
    const list = await getNotifications(cursor, 20);
    if (reset) {
      notifications.value = list;
    } else {
      notifications.value.push(...list);
    }
    hasMore.value = list.length >= 20;
  } catch {
    // ignore
  }
  loading.value = false;
}

async function loadUnreadCount() {
  try {
    unreadCount.value = await getUnreadCount();
  } catch {
    // ignore
  }
}

async function handleClick(notif: NotificationVO) {
  if (!notif.isRead) {
    try {
      await markRead(notif.id);
      notif.isRead = true;
      unreadCount.value = Math.max(0, unreadCount.value - 1);
    } catch {
      // ignore
    }
  }
  if (notif.redirectUrl) {
    router.push(notif.redirectUrl);
  }
}

async function handleMarkAllRead() {
  try {
    await markAllRead();
    notifications.value.forEach((n) => (n.isRead = true));
    unreadCount.value = 0;
    message.success('已全部标记为已读');
  } catch {
    message.error('操作失败');
  }
}

function loadMore() {
  if (!loading.value && hasMore.value) {
    loadNotifications(false);
  }
}

function handleScroll(e: Event) {
  const target = e.target as HTMLElement;
  if (target.scrollHeight - target.scrollTop - target.clientHeight < 100) {
    loadMore();
  }
}

onMounted(async () => {
  await loadNotifications(true);
  await loadUnreadCount();
});
</script>

<template>
  <div
    class="notifications-layout"
    @scroll="handleScroll"
  >
    <div class="header-banner">
      <div class="banner-content">
        <button
          class="action-btn back-btn"
          title="返回"
          @click="router.back()"
        >
          <n-icon><ArrowBackOutline /></n-icon>
        </button>
        <h1 class="page-title gradient-text">
          <n-icon
            size="32"
            class="title-icon"
          >
            <NotificationsOutline />
          </n-icon>
          通知中心
        </h1>
        <n-badge
          v-if="unreadCount > 0"
          :value="unreadCount"
          type="error"
          class="unread-badge"
        />
      </div>
      <div class="header-actions">
        <button 
          class="neon-btn outline-btn" 
          :disabled="unreadCount === 0" 
          @click="handleMarkAllRead"
        >
          <n-icon style="margin-right: 6px;">
            <CheckmarkDoneOutline />
          </n-icon>
          全部已读
        </button>
      </div>
    </div>

    <div class="main-container">
      <div class="glass-card list-card">
        <div
          v-if="loading && notifications.length === 0"
          class="loading-state"
        >
          <n-spin size="large" />
          <p>加载中...</p>
        </div>
        
        <div
          v-else-if="notifications.length === 0"
          class="empty-state"
        >
          <n-icon
            size="64"
            color="#30363d"
          >
            <NotificationsOutline />
          </n-icon>
          <h3>暂无通知</h3>
          <p>什么时候才能收到你的消息呢？</p>
        </div>

        <div
          v-else
          class="notif-list"
        >
          <div
            v-for="notif in notifications"
            :key="notif.id"
            class="notif-item"
            :class="{ unread: !notif.isRead }"
            @click="handleClick(notif)"
          >
            <div
              class="notif-icon-wrap"
              :style="{ backgroundColor: getTypeBg(notif.type), color: getTypeColor(notif.type) }"
            >
              <n-badge
                :show="!notif.isRead"
                dot
                type="error"
                :offset="[2, 2]"
              >
                <n-icon size="24">
                  <component :is="getTypeIcon(notif.type)" />
                </n-icon>
              </n-badge>
            </div>
            
            <div class="notif-content">
              <div class="notif-header">
                <span class="notif-title">{{ notif.title }}</span>
                <span class="notif-time">{{ new Date(notif.createdAt).toLocaleString() }}</span>
              </div>
              <p class="notif-desc">
                {{ notif.content }}
              </p>
            </div>
          </div>
          
          <div
            v-if="loading && notifications.length > 0"
            class="loading-more"
          >
            <n-spin size="small" />
          </div>
          
          <div
            v-if="!hasMore && notifications.length > 0"
            class="end-state"
          >
            <div class="divider" />
            <span>— 到底了 —</span>
            <div class="divider" />
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped lang="scss">
.notifications-layout {
  height: 100vh;
  overflow-y: auto;
  background: transparent;
  background-image: radial-gradient(circle at 80% 20%, rgba(245, 158, 11, 0.08), transparent 40%);
}

.header-banner {
  max-width: 800px;
  width: 100%;
  margin: 0 auto;
  padding: 40px 24px 24px;
  display: flex;
  justify-content: space-between;
  align-items: center;

  .banner-content {
    display: flex;
    align-items: center;
    gap: 16px;

    .back-btn {
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
        transform: translateX(-2px);
      }
    }

    .page-title {
      font-size: 32px;
      font-weight: 800;
      margin: 0;
      display: flex;
      align-items: center;
      gap: 12px;
    }
    
    .unread-badge {
      margin-left: 8px;
    }
  }
}

.outline-btn {
  background: transparent !important;
  border: 1px solid var(--cf-border-light) !important;
  box-shadow: none !important;
  display: flex;
  align-items: center;
  
  &:hover:not(:disabled) {
    background: rgba(255, 255, 255, 0.05) !important;
    border-color: var(--cf-text-primary) !important;
    transform: none !important;
  }
  
  &:disabled {
    opacity: 0.5;
    cursor: not-allowed;
  }
}

.main-container {
  max-width: 800px;
  width: 100%;
  margin: 0 auto;
  padding: 0 24px 40px;
}

.list-card {
  padding: 12px 0;
  min-height: 400px;
}

.notif-list {
  display: flex;
  flex-direction: column;
}

.notif-item {
  display: flex;
  gap: 16px;
  padding: 20px 24px;
  cursor: pointer;
  transition: all 0.3s ease;
  border-left: 3px solid transparent;
  border-bottom: 1px solid rgba(255, 255, 255, 0.02);

  &:hover {
    background: rgba(255, 255, 255, 0.03);
  }

  &.unread {
    background: rgba(99, 102, 241, 0.05);
    border-left-color: var(--cf-primary);
    
    .notif-title {
      font-weight: 700;
      color: var(--cf-text-primary);
    }
  }

  .notif-icon-wrap {
    width: 48px;
    height: 48px;
    border-radius: 50%;
    display: flex;
    align-items: center;
    justify-content: center;
    flex-shrink: 0;
    transition: transform 0.3s;
  }

  &:hover .notif-icon-wrap {
    transform: scale(1.05);
  }

  .notif-content {
    flex: 1;
    min-width: 0;
    display: flex;
    flex-direction: column;
    justify-content: center;
    
    .notif-header {
      display: flex;
      justify-content: space-between;
      align-items: flex-start;
      margin-bottom: 6px;

      .notif-title {
        font-weight: 500;
        font-size: 16px;
        color: var(--cf-text-primary);
        line-height: 1.4;
      }
      
      .notif-time {
        font-size: 12px;
        color: var(--cf-text-secondary);
        white-space: nowrap;
        margin-left: 12px;
        margin-top: 2px;
      }
    }

    .notif-desc {
      margin: 0;
      font-size: 14px;
      color: var(--cf-text-secondary);
      line-height: 1.5;
      word-break: break-word;
      display: -webkit-box;
      -webkit-line-clamp: 2;
      -webkit-box-orient: vertical;
      overflow: hidden;
    }
  }
}

.loading-state, .empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 400px;
  color: var(--cf-text-secondary);
  
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

.loading-more {
  padding: 20px;
  display: flex;
  justify-content: center;
}

.end-state {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 24px;
  color: var(--cf-text-secondary);
  font-size: 14px;
  
  .divider {
    flex: 1;
    height: 1px;
    background: var(--cf-border);
  }
}
</style>
