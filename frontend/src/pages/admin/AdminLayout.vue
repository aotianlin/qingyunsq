<script setup lang="ts">
import { ref, computed, h, onMounted } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { NLayout, NLayoutSider, NLayoutContent, NMenu, NButton, NStatistic } from 'naive-ui';
import { renderIcon } from '@/utils/render-icon';
import {
  GridOutline, PeopleOutline, DocumentTextOutline,
  AlbumsOutline, ClipboardOutline, ArrowBackOutline,
} from '@vicons/ionicons5';
import type { MenuOption } from 'naive-ui';
import type { Component } from 'vue';
import { getUnreadCount } from '@/api/notifications';

const route = useRoute();
const router = useRouter();

const menuOptions: MenuOption[] = [
  { label: '仪表盘', key: '/admin', icon: renderIcon(GridOutline) },
  { label: '用户管理', key: '/admin/users', icon: renderIcon(PeopleOutline) },
  { label: '帖子管理', key: '/admin/posts', icon: renderIcon(DocumentTextOutline) },
  { label: '空间管理', key: '/admin/spaces', icon: renderIcon(AlbumsOutline) },
  { label: '审计日志', key: '/admin/audit-logs', icon: renderIcon(ClipboardOutline) },
];

const activeKey = computed(() => {
  const path = route.path;
  if (path === '/admin') return '/admin';
  if (path.startsWith('/admin/users')) return '/admin/users';
  if (path.startsWith('/admin/posts')) return '/admin/posts';
  if (path.startsWith('/admin/spaces')) return '/admin/spaces';
  if (path.startsWith('/admin/audit-logs')) return '/admin/audit-logs';
  return '/admin';
});

const collapsed = ref(false);

function handleMenuClick(key: string) {
  router.push(key);
}
</script>

<template>
  <NLayout style="min-height: 100vh;" has-sider>
    <NLayoutSider
      bordered
      collapse-mode="width"
      :collapsed-width="64"
      :width="200"
      :collapsed="collapsed"
      @update:collapsed="collapsed = $event"
    >
      <div style="padding: 16px 12px 8px; display: flex; justify-content: flex-end;">
        <NButton text size="small" @click="collapsed = !collapsed">
          {{ collapsed ? '▶' : '◀' }}
        </NButton>
      </div>
      <NMenu
        :value="activeKey"
        :collapsed="collapsed"
        :collapsed-width="64"
        :collapsed-icon-size="22"
        :options="menuOptions"
        @update:value="handleMenuClick"
      />
      <div style="position: absolute; bottom: 16px; left: 0; right: 0; padding: 0 12px;">
        <NButton
          text
          size="small"
          style="width: 100%;"
          @click="router.push('/')"
        >
          <template #icon>
            <ArrowBackOutline />
          </template>
          <span v-if="!collapsed">返回前台</span>
        </NButton>
      </div>
    </NLayoutSider>
    <NLayoutContent style="background: var(--body-color);">
      <router-view />
    </NLayoutContent>
  </NLayout>
</template>
