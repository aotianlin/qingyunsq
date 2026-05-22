<script setup lang="ts">
import { ref, computed } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { NLayout, NLayoutSider, NLayoutContent, NMenu, NButton } from 'naive-ui';
import { renderIcon } from '@/utils/render-icon';
import {
  GridOutline, PeopleOutline, DocumentTextOutline,
  AlbumsOutline, ClipboardOutline, FlagOutline, ShieldCheckmarkOutline,
  SchoolOutline, SettingsOutline, ArrowBackOutline,
} from '@vicons/ionicons5';
import type { MenuOption } from 'naive-ui';

const route = useRoute();
const router = useRouter();

const menuOptions: MenuOption[] = [
  { label: '仪表盘', key: '/admin', icon: renderIcon(GridOutline) },
  { label: '用户管理', key: '/admin/users', icon: renderIcon(PeopleOutline) },
  { label: '帖子管理', key: '/admin/posts', icon: renderIcon(DocumentTextOutline) },
  { label: '空间管理', key: '/admin/spaces', icon: renderIcon(AlbumsOutline) },
  { label: '审计日志', key: '/admin/audit-logs', icon: renderIcon(ClipboardOutline) },
  { label: '举报管理', key: '/admin/reports', icon: renderIcon(FlagOutline) },
  { label: '敏感词', key: '/admin/sensitive-words', icon: renderIcon(ShieldCheckmarkOutline) },
  { label: '租户管理', key: '/admin/tenants', icon: renderIcon(SchoolOutline) },
  { label: 'AI 配置', key: '/admin/ai-config', icon: renderIcon(SettingsOutline) },
];

const activeKey = computed(() => {
  const path = route.path;
  if (path === '/admin') return '/admin';
  if (path.startsWith('/admin/users')) return '/admin/users';
  if (path.startsWith('/admin/posts')) return '/admin/posts';
  if (path.startsWith('/admin/spaces')) return '/admin/spaces';
  if (path.startsWith('/admin/audit-logs')) return '/admin/audit-logs';
  if (path.startsWith('/admin/reports')) return '/admin/reports';
  if (path.startsWith('/admin/sensitive-words')) return '/admin/sensitive-words';
  if (path.startsWith('/admin/tenants')) return '/admin/tenants';
  if (path.startsWith('/admin/ai-config')) return '/admin/ai-config';
  return '/admin';
});

const collapsed = ref(false);

function handleMenuClick(key: string) {
  router.push(key);
}
</script>

<template>
  <NLayout
    class="admin-shell"
    has-sider
  >
    <NLayoutSider
      bordered
      collapse-mode="width"
      :collapsed-width="64"
      :width="200"
      :collapsed="collapsed"
      :content-style="{
        height: '100%',
        display: 'flex',
        flexDirection: 'column',
      }"
      @update:collapsed="collapsed = $event"
      class="admin-shell-sider"
    >
      <div class="sider-toggle">
        <NButton
          text
          size="small"
          @click="collapsed = !collapsed"
        >
          {{ collapsed ? '▶' : '◀' }}
        </NButton>
      </div>
      <div class="sider-menu">
        <NMenu
          :value="activeKey"
          :collapsed="collapsed"
          :collapsed-width="64"
          :collapsed-icon-size="22"
          :options="menuOptions"
          @update:value="handleMenuClick"
        />
      </div>
      <div class="sider-footer">
        <NButton
          text
          size="small"
          class="return-btn"
          @click="router.push('/')"
        >
          <template #icon>
            <ArrowBackOutline />
          </template>
          <span v-if="!collapsed">返回前台</span>
        </NButton>
      </div>
    </NLayoutSider>
    <NLayoutContent class="admin-shell-content">
      <router-view />
    </NLayoutContent>
  </NLayout>
</template>

<style scoped lang="scss">
.admin-shell {
  height: 100vh;
  overflow: hidden;
}

.admin-shell-sider {
  height: 100vh;
}

.sider-toggle {
  flex: 0 0 auto;
  padding: 16px 12px 8px;
  display: flex;
  justify-content: flex-end;
}

.sider-menu {
  flex: 1 1 auto;
  min-height: 0;
  overflow-y: auto;
  padding-bottom: 12px;
}

.sider-footer {
  flex: 0 0 auto;
  margin-top: auto;
  padding: 12px;
  border-top: 1px solid var(--cf-border);
  background: var(--cf-bg-readable);
}

.return-btn {
  width: 100%;
  justify-content: center;
}

.admin-shell-content {
  height: 100vh;
  min-width: 0;
  overflow: auto;
  background: var(--body-color);
}
</style>
