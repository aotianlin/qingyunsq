<script setup lang="ts">
import { h, onMounted, onUnmounted, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { useAuthStore } from '@/stores/auth';
import { getMe, logout as apiLogout } from '@/api/auth';
import { getUnreadCount as getNotifUnreadCount } from '@/api/notifications';
import { getUnreadCount as getMsgUnreadCount } from '@/api/messages';
import { useWebSocket } from '@/composables/useWebSocket';
import ThemeToggle from '@/components/ThemeToggle.vue';
import {
  AddOutline,
  BonfireOutline,
  ChatbubblesOutline,
  CheckmarkCircleOutline,
  DocumentTextOutline,
  LogOutOutline,
  NotificationsOutline,
  PersonOutline,
  PlanetOutline,
  SearchOutline,
  SparklesOutline,
  StarOutline,
} from '@vicons/ionicons5';
import { NAvatar, NBadge, NDropdown, NIcon, NInput } from 'naive-ui';

const router = useRouter();
const route = useRoute();
const authStore = useAuthStore();
const searchKeyword = ref('');
const headerScrolled = ref(false);
const notifUnread = ref(0);
const msgUnread = ref(0);

// 监听 WebSocket 事件，实时更新未读数。
useWebSocket((event) => {
  if (event.type === 'COMMENT' || event.type === 'LIKE' || event.type === 'REPLY'
      || event.type === 'MENTION' || event.type === 'ACCEPT' || event.type === 'JOIN'
      || event.type === 'TAG_SUBSCRIBE' || event.type === 'SYSTEM') {
    notifUnread.value++;
    showDesktopNotification(event.title || '新通知', event.content || '');
  } else if (event.type === 'MESSAGE') {
    msgUnread.value++;
    showDesktopNotification('新私信', event.content || '你收到了一条新消息');
  }
});

/** 请求浏览器通知权限并显示桌面弹窗。 */
function showDesktopNotification(title: string, body: string) {
  if (!('Notification' in window)) return;
  if (Notification.permission === 'granted') {
    new Notification(title, { body, icon: '/favicon.ico' });
  } else if (Notification.permission !== 'denied') {
    Notification.requestPermission().then((perm) => {
      if (perm === 'granted') {
        new Notification(title, { body, icon: '/favicon.ico' });
      }
    });
  }
}

const navLinks = [
  { name: '广场', path: '/square', icon: PlanetOutline },
  { name: '学习圈', path: '/spaces', icon: BonfireOutline },
  { name: '资源', path: '/resources', icon: DocumentTextOutline },
  { name: '打卡', path: '/checkin', icon: CheckmarkCircleOutline },
  { name: '积分中心', path: '/points', icon: StarOutline },
  { name: '小青知识库', path: '/ai', icon: SparklesOutline },
];

const userDropdownOptions = [
  {
    label: '个人中心',
    key: 'profile',
    icon: () => h(NIcon, null, { default: () => h(PersonOutline) }),
  },
  {
    label: '退出登录',
    key: 'logout',
    icon: () => h(NIcon, null, { default: () => h(LogOutOutline) }),
  },
];

onMounted(async () => {
  updateTopHeaderState();
  window.addEventListener('scroll', updateTopHeaderState, { passive: true });

  if (authStore.isLoggedIn && !authStore.user) {
    try {
      const user = await getMe();
      authStore.setUser(user);
    } catch {
      authStore.logout();
      router.push('/login');
    }
  }

  if (authStore.isLoggedIn) {
    notifUnread.value = await getNotifUnreadCount().catch(() => 0);
    msgUnread.value = Number(await getMsgUnreadCount().catch(() => 0));
    if ('Notification' in window && Notification.permission === 'default') {
      Notification.requestPermission();
    }
  }
});

onUnmounted(() => {
  window.removeEventListener('scroll', updateTopHeaderState);
});

async function handleDropdownSelect(key: string | number) {
  if (key === 'profile') {
    router.push('/profile');
    return;
  }

  if (key === 'logout') {
    try {
      await apiLogout();
    } finally {
      authStore.logout();
      router.push('/login');
    }
  }
}

function handleSearch() {
  const query = searchKeyword.value.trim();
  if (!query) return;
  const postId = extractPostIdFromSearchInput(query);
  if (postId) {
    router.push(`/posts/${postId}`);
    return;
  }
  router.push({ path: '/search', query: { q: query } });
}

function extractPostIdFromSearchInput(value: string) {
  // 允许用户粘贴站内帖子分享链接，优先精确跳转到帖子，避免把 URL 当普通关键词搜索。
  const directMatch = value.match(/(?:^|\s)(?:https?:\/\/[^\s/]+)?\/?posts\/(\d+)(?=$|[/?#\s])/i);
  if (directMatch) return Number(directMatch[1]);

  try {
    const url = new URL(value, window.location.origin);
    const pathMatch = url.pathname.match(/^\/posts\/(\d+)\/?$/i);
    if (pathMatch) return Number(pathMatch[1]);

    const postId = url.searchParams.get('postId');
    if (postId && /^\d+$/.test(postId)) return Number(postId);
  } catch {
    return null;
  }

  return null;
}

function navigate(path: string) {
  router.push(path);
}

function updateTopHeaderState() {
  headerScrolled.value = window.scrollY > 8;
}
</script>

<template>
  <div class="main-layout">
    <header
      class="top-header"
      :class="{ 'is-scrolled': headerScrolled }"
    >
      <div class="header-left">
        <div
          class="brand"
          @click="navigate('/')"
        >
          <div class="brand-icon">
            <n-icon size="20">
              <PlanetOutline />
            </n-icon>
          </div>
          <div class="brand-copy">
            <div class="brand-title">
              CampusForum
            </div>
            <div class="brand-subtitle">
              智慧校园社区
            </div>
          </div>
        </div>

        <nav
          class="top-nav"
          aria-label="主导航"
        >
          <button
            v-for="link in navLinks"
            :key="link.path"
            class="top-nav-item"
            :class="{ active: route.path.startsWith(link.path) }"
            :title="link.name"
            @click="navigate(link.path)"
          >
            <n-icon size="18">
              <component :is="link.icon" />
            </n-icon>
            <span>{{ link.name }}</span>
          </button>
        </nav>
      </div>

      <div class="header-right">
        <div class="search-cluster">
          <n-input
            v-model:value="searchKeyword"
            round
            placeholder="搜索帖子、用户、资源，或粘贴帖子链接"
            class="search-input"
            @keyup.enter="handleSearch"
          >
            <template #prefix>
              <n-icon>
                <SearchOutline />
              </n-icon>
            </template>
          </n-input>
        </div>

        <button
          class="publish-top-btn"
          title="发布新帖"
          @click="navigate('/posts/new')"
        >
          <n-icon size="18">
            <AddOutline />
          </n-icon>
          <span>发布</span>
        </button>

        <button
          class="header-icon-btn"
          title="私信"
          @click="navigate('/messages')"
        >
          <n-badge
            :value="msgUnread"
            :max="99"
            :show="msgUnread > 0"
          >
            <n-icon size="18">
              <ChatbubblesOutline />
            </n-icon>
          </n-badge>
        </button>

        <button
          class="header-icon-btn"
          title="通知"
          @click="navigate('/notifications')"
        >
          <n-badge
            :value="notifUnread"
            :max="99"
            :show="notifUnread > 0"
          >
            <n-icon size="18">
              <NotificationsOutline />
            </n-icon>
          </n-badge>
        </button>

        <ThemeToggle class="header-theme-toggle" />

        <n-dropdown
          v-if="authStore.user"
          :options="userDropdownOptions"
          @select="handleDropdownSelect"
        >
          <div class="user-profile-trigger">
            <n-avatar
              round
              size="small"
              :src="authStore.user.avatarUrl"
              :fallback-src="`https://api.dicebear.com/7.x/initials/svg?seed=${authStore.user.nickname}`"
            />
            <span>{{ authStore.user.nickname }}</span>
          </div>
        </n-dropdown>
      </div>
    </header>

    <div class="content-wrapper">
      <main class="page-content">
        <router-view v-slot="{ Component }">
          <transition
            name="fade"
            mode="out-in"
          >
            <component :is="Component" />
          </transition>
        </router-view>
      </main>
    </div>
  </div>
</template>

<style scoped lang="scss">
.main-layout {
  min-height: 100vh;
  background: transparent;
  display: flex;
  flex-direction: column;
}

.brand {
  display: flex;
  align-items: center;
  gap: 12px;
  cursor: pointer;
  transition: transform 0.24s var(--cf-motion-ease);
}

.brand:hover {
  transform: translate3d(2px, -1px, 0);
}

.brand-icon {
  width: 40px;
  height: 40px;
  border-radius: 14px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--cf-primary);
  color: var(--cf-text-inverse);
  box-shadow: 0 16px 34px color-mix(in srgb, var(--cf-primary) 28%, transparent), 0 1px 0
    rgba(255, 255, 255, 0.54) inset;
}

.brand-title {
  font-family: var(--cf-font-heading);
  font-size: 18px;
  line-height: 1.1;
  font-weight: 700;
  white-space: nowrap;
}

.brand-subtitle {
  color: var(--cf-text-muted);
  font-size: 12px;
  line-height: 1.2;
  white-space: nowrap;
}

.header-left {
  display: flex;
  align-items: center;
  min-width: 0;
  gap: 18px;
}

.top-nav {
  display: flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
  overflow-x: auto;
  scrollbar-width: none;
}

.top-nav::-webkit-scrollbar {
  display: none;
}

.top-nav-item {
  height: 42px;
  min-width: 42px;
  border: 1px solid transparent;
  background: transparent;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 0 12px;
  border-radius: var(--cf-radius-pill);
  color: var(--cf-text-secondary);
  cursor: pointer;
  transition: transform 0.24s var(--cf-motion-ease), box-shadow 0.24s var(--cf-motion-ease), color 0.22s ease,
    background 0.22s ease, border-color 0.22s ease;
  font-weight: 600;
  white-space: nowrap;
}

.top-nav-item span {
  font-size: 14px;
}

.top-nav-item:hover {
  background: var(--cf-bg-readable);
  border-color: var(--cf-border-strong);
  color: var(--cf-primary);
  transform: translate3d(0, -2px, 0);
  box-shadow: var(--cf-shadow-soft);
}

.top-nav-item.active {
  background:
    linear-gradient(135deg, color-mix(in srgb, var(--cf-primary) 20%, transparent), color-mix(in srgb, var(--cf-secondary) 10%, transparent)),
    var(--cf-bg-glass-soft);
  border-color: color-mix(in srgb, var(--cf-primary) 32%, var(--cf-border-glass));
  box-shadow: 0 18px 46px color-mix(in srgb, var(--cf-primary) 18%, transparent);
  color: var(--cf-primary);
}

.content-wrapper {
  width: 100%;
  min-height: calc(100vh - var(--cf-header-height));
  display: flex;
  flex-direction: column;
}

.top-header {
  position: sticky;
  top: 0;
  z-index: 20;
  height: var(--cf-header-height);
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
  padding: 0 24px;
  background:
    linear-gradient(180deg, var(--cf-surface-highlight), transparent 74%),
    linear-gradient(90deg, var(--cf-bg-glass-strong), var(--cf-bg-glass-soft));
  backdrop-filter: blur(var(--cf-backdrop-blur)) saturate(136%);
  -webkit-backdrop-filter: blur(var(--cf-backdrop-blur)) saturate(136%);
  border-bottom: 1px solid var(--cf-border-glass);
  box-shadow: 0 20px 70px color-mix(in srgb, var(--cf-text-primary) 9%, transparent), 0 -1px 0
    color-mix(in srgb, #ffffff 36%, transparent) inset;
  transition: background 0.26s ease, backdrop-filter 0.26s ease, border-color 0.26s ease, box-shadow 0.26s ease;
}

.top-header.is-scrolled {
  background:
    linear-gradient(180deg, color-mix(in srgb, var(--cf-surface-highlight) 72%, transparent), transparent 82%),
    color-mix(in srgb, var(--cf-bg-base) 88%, transparent);
  backdrop-filter: blur(18px) saturate(160%);
  -webkit-backdrop-filter: blur(18px) saturate(160%);
  border-bottom-color: color-mix(in srgb, var(--cf-border-strong) 42%, var(--cf-border-glass));
  box-shadow: 0 18px 54px color-mix(in srgb, var(--cf-text-primary) 18%, transparent), 0 -1px 0
    color-mix(in srgb, #ffffff 28%, transparent) inset;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-shrink: 0;
}

.header-theme-toggle {
  flex-shrink: 0;
}

.search-input {
  width: 360px;
}

.search-cluster {
  display: flex;
  align-items: center;
  gap: 8px;
}

.header-icon-btn {
  width: 40px;
  height: 40px;
  border-radius: 12px;
  background: var(--cf-bg-glass);
  border: 1px solid var(--cf-border-glass);
  box-shadow: 0 12px 30px color-mix(in srgb, var(--cf-text-primary) 7%, transparent);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  color: var(--cf-text-secondary);
  transition: transform 0.24s var(--cf-motion-ease), box-shadow 0.24s var(--cf-motion-ease), color 0.22s ease,
    background 0.22s ease, border-color 0.22s ease;
}

.header-icon-btn,
.publish-top-btn {
  border: none;
  cursor: pointer;
}

.publish-top-btn {
  height: 40px;
  padding: 0 14px;
  border-radius: var(--cf-radius-pill);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  color: var(--cf-text-inverse);
  background: var(--cf-primary);
  box-shadow: var(--cf-shadow-glow);
  font-weight: 700;
  transition: transform 0.24s var(--cf-motion-ease), box-shadow 0.24s var(--cf-motion-ease), background 0.22s ease;
}

.publish-top-btn:hover {
  background: var(--cf-primary-hover);
  transform: translate3d(0, -2px, 0);
  box-shadow: 0 18px 54px color-mix(in srgb, var(--cf-primary) 24%, transparent);
}

.header-icon-btn:hover {
  background: var(--cf-bg-readable);
  border-color: var(--cf-border-strong);
  color: var(--cf-primary);
  transform: translate3d(0, -2px, 0);
  box-shadow: var(--cf-shadow-soft);
}

.user-profile-trigger {
  height: 40px;
  padding: 0 12px 0 8px;
  border-radius: 999px;
  background: var(--cf-bg-glass);
  border: 1px solid var(--cf-border-glass);
  box-shadow: 0 12px 30px color-mix(in srgb, var(--cf-text-primary) 7%, transparent);
  display: inline-flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  color: var(--cf-text-primary);
  font-size: 14px;
  font-weight: 600;
  transition: transform 0.24s var(--cf-motion-ease), box-shadow 0.24s var(--cf-motion-ease), border-color 0.22s ease;
}

.user-profile-trigger:hover {
  transform: translate3d(0, -2px, 0);
  border-color: var(--cf-border-strong);
  box-shadow: var(--cf-shadow-soft);
}

.page-content {
  flex: 1;
  padding: 24px;
  overflow-x: hidden;
}

.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.18s ease, transform 0.18s ease;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
  transform: translateY(8px);
}

:deep(.n-input .n-input__input-el),
:deep(.n-input .n-input__placeholder) {
  font-size: 14px;
}

:deep(.search-input.n-input) {
  --n-color: var(--cf-bg-glass) !important;
  --n-color-focus: var(--cf-bg-readable) !important;
  --n-border: 1px solid var(--cf-border-glass) !important;
  --n-border-hover: 1px solid var(--cf-border-strong) !important;
  --n-border-focus: 1px solid var(--cf-border-strong) !important;
  --n-box-shadow-focus: 0 0 0 4px color-mix(in srgb, var(--cf-primary) 12%, transparent) !important;
  --n-text-color: var(--cf-text-primary) !important;
  --n-placeholder-color: var(--cf-text-muted) !important;
  --n-icon-color: var(--cf-text-muted) !important;
  box-shadow: 0 14px 34px color-mix(in srgb, var(--cf-text-primary) 7%, transparent);
  backdrop-filter: blur(var(--cf-backdrop-blur)) saturate(130%);
  -webkit-backdrop-filter: blur(var(--cf-backdrop-blur)) saturate(130%);
}

@media (max-width: 1100px) {
  .search-input {
    width: 280px;
  }

  .brand-copy {
    display: none;
  }
}

@media (max-width: 960px) {
  .top-header {
    height: auto;
    min-height: var(--cf-header-height);
    align-items: stretch;
    flex-direction: column;
    padding: 12px 16px;
    gap: 12px;
  }

  .header-left,
  .header-right {
    width: 100%;
  }

  .search-cluster {
    flex: 1;
  }

  .page-content {
    padding: 16px;
  }
}

@media (max-width: 720px) {
  .header-right {
    gap: 8px;
  }

  .user-profile-trigger span {
    display: none;
  }

  .top-nav-item span {
    display: none;
  }

  .search-input {
    width: 100%;
    min-width: 0;
    flex: 1;
  }

  .publish-top-btn span {
    display: none;
  }
}
</style>
