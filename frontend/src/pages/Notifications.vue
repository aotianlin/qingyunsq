<script setup lang="ts">
import { ref, onMounted } from 'vue';
import { useRouter } from 'vue-router';
import {
  NButton, NSpin, NEmpty, NList, NListItem, NBadge, useMessage,
} from 'naive-ui';
import {
  HeartOutline, ChatbubbleOutline, ReturnDownBackOutline,
  CheckmarkCircleOutline, PeopleOutline,
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
  LIKE: '#f56c6c',
  COMMENT: '#409eff',
  REPLY: '#67c23a',
  ACCEPT: '#e6a23c',
  JOIN: '#909399',
};

function getTypeIcon(type: string): Component {
  return typeIcons[type] || ChatbubbleOutline;
}

function getTypeColor(type: string): string {
  return typeColors[type] || '#909399';
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

onMounted(async () => {
  await loadNotifications(true);
  await loadUnreadCount();
});
</script>

<template>
  <div style="max-width: 700px; margin: 0 auto; padding: 24px 16px;">
    <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px;">
      <h2 style="margin: 0;">通知中心</h2>
      <NButton
        text
        type="primary"
        :disabled="unreadCount === 0"
        @click="handleMarkAllRead"
      >
        全部已读
      </NButton>
    </div>

    <NSpin :show="loading && notifications.length === 0">
      <div v-if="notifications.length > 0" style="max-height: 70vh; overflow-y: auto;">
        <NList>
          <NListItem
            v-for="notif in notifications"
            :key="notif.id"
            :style="{
              cursor: 'pointer',
              padding: '16px',
              backgroundColor: notif.isRead ? 'transparent' : 'var(--action-color)',
            }"
            @click="handleClick(notif)"
          >
            <div style="display: flex; align-items: flex-start; gap: 12px; width: 100%;">
              <div style="flex-shrink: 0; margin-top: 2px;">
                <NBadge :show="!notif.isRead" dot>
                  <component
                    :is="getTypeIcon(notif.type)"
                    :style="{ color: getTypeColor(notif.type), fontSize: '22px' }"
                  />
                </NBadge>
              </div>
              <div style="flex: 1; min-width: 0;">
                <div style="display: flex; justify-content: space-between; align-items: center;">
                  <span :style="{ fontWeight: notif.isRead ? '400' : '600' }">
                    {{ notif.title }}
                  </span>
                  <span style="font-size: 12px; color: #999; white-space: nowrap;">
                    {{ new Date(notif.createdAt).toLocaleDateString() }}
                  </span>
                </div>
                <p style="margin: 4px 0 0; color: #666; font-size: 14px; word-break: break-all;">
                  {{ notif.content }}
                </p>
              </div>
            </div>
          </NListItem>
        </NList>

        <div v-if="hasMore" style="text-align: center; padding: 16px;">
          <NButton text type="primary" :loading="loading" @click="loadMore">
            加载更多
          </NButton>
        </div>
      </div>

      <NEmpty v-else-if="!loading" description="暂无通知" />
    </NSpin>
  </div>
</template>
