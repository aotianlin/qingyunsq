<script setup lang="ts">
import { computed, h, onMounted, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { useAuthStore } from '@/stores/auth';
import { getMe, logout as apiLogout } from '@/api/auth';
import {
  ChatbubblesOutline,
  CheckmarkCircleOutline,
  LogOutOutline,
  NotificationsOutline,
  PersonOutline,
  PlanetOutline,
  SearchOutline,
  SparklesOutline,
  StarOutline,
  BonfireOutline,
  MenuOutline,
  CloseOutline,
} from '@vicons/ionicons5';
import { NAvatar, NDropdown, NIcon, NInput } from 'naive-ui';

const router = useRouter();
const route = useRoute();
const authStore = useAuthStore();
const mobileMenuVisible = ref(false);
const searchKeyword = ref('');

const navLinks = [
  { name: '广场', path: '/square', icon: PlanetOutline },
  { name: '学习圈', path: '/spaces', icon: BonfireOutline },
  { name: '打卡', path: '/checkin', icon: CheckmarkCircleOutline },
  { name: '积分中心', path: '/points', icon: StarOutline },
  { name: 'AI 助手', path: '/ai', icon: SparklesOutline },
];

const pageTitle = computed(() => {
  const current = navLinks.find((item) => route.path.startsWith(item.path));
  if (current) return current.name;
  if (route.path.startsWith('/posts/new')) return '发布帖子';
  if (route.path.startsWith('/posts/')) return '帖子详情';
  if (route.path.startsWith('/resources')) return '资源中心';
  if (route.path.startsWith('/search')) return '搜索';
  if (route.path.startsWith('/notifications')) return '通知';
  if (route.path.startsWith('/messages')) return '私信';
  if (route.path.startsWith('/users/')) return '个人主页';
  if (route.path.startsWith('/profile')) return '我的资料';
  return 'CampusForum';
});

const pageSubtitle = computed(() => {
  if (route.path.startsWith('/profile')) return '管理您的个人资料、发帖与学习轨迹';
  if (route.path.startsWith('/square')) return '发现校园热门内容与精彩讨论';
  if (route.path.startsWith('/spaces')) return '加入感兴趣的学习圈，与同好交流';
  if (route.path.startsWith('/checkin')) return '坚持每天打卡，见证自己的成长';
  if (route.path.startsWith('/points')) return '查看您的积分明细与成长轨迹';
  if (route.path.startsWith('/ai')) return '使用 AI 助手提升您的学习与创作效率';
  return '保持高效、清爽、专注的校园交流体验';
});

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
  if (authStore.isLoggedIn && !authStore.user) {
    try {
      const user = await getMe();
      authStore.setUser(user);
    } catch {
      authStore.logout();
      router.push('/login');
    }
  }
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
  router.push({ path: '/search', query: { q: query } });
  mobileMenuVisible.value = false;
}

function navigate(path: string) {
  router.push(path);
  mobileMenuVisible.value = false;
}

function toggleMobileMenu() {
  mobileMenuVisible.value = !mobileMenuVisible.value;
}
</script>

<template>
  <div class="main-layout">
    <aside
      class="sidebar"
      :class="{ 'is-open': mobileMenuVisible }"
    >
      <div class="sidebar-header">
        <div
          class="brand"
          @click="navigate('/')"
        >
          <div class="brand-icon">
            <n-icon size="20">
              <PlanetOutline />
            </n-icon>
          </div>
          <div>
            <div class="brand-title">
              CampusForum
            </div>
            <div class="brand-subtitle">
              智慧校园社区
            </div>
          </div>
        </div>
        <button
          class="mobile-close"
          @click="toggleMobileMenu"
        >
          <n-icon size="20">
            <CloseOutline />
          </n-icon>
        </button>
      </div>

      <div
        v-if="authStore.user"
        class="profile-card"
        @click="navigate('/profile')"
      >
        <n-avatar
          round
          :size="52"
          :src="authStore.user.avatarUrl"
          :fallback-src="`https://api.dicebear.com/7.x/initials/svg?seed=${authStore.user.nickname}`"
        />
        <div class="profile-meta">
          <strong>{{ authStore.user.nickname }}</strong>
          <span>{{ authStore.user.email || '校园成员' }}</span>
        </div>
      </div>

      <nav class="nav-menu">
        <button
          v-for="link in navLinks"
          :key="link.path"
          class="nav-item"
          :class="{ active: route.path.startsWith(link.path) }"
          @click="navigate(link.path)"
        >
          <n-icon size="18">
            <component :is="link.icon" />
          </n-icon>
          <span>{{ link.name }}</span>
        </button>
      </nav>

      <div class="sidebar-bottom">
        <button
          class="cf-primary-btn publish-btn"
          @click="navigate('/posts/new')"
        >
          发布新帖
        </button>
      </div>
    </aside>

    <div
      v-if="mobileMenuVisible"
      class="mobile-mask"
      @click="toggleMobileMenu"
    />

    <div class="content-wrapper">
      <header class="top-header">
        <div class="header-title-group">
          <button
            class="mobile-menu-btn"
            @click="toggleMobileMenu"
          >
            <n-icon size="20">
              <MenuOutline />
            </n-icon>
          </button>
          <div>
            <h1 class="page-title">
              {{ pageTitle }}
            </h1>
            <p class="page-subtitle">
              {{ pageSubtitle }}
            </p>
          </div>
        </div>

        <div class="header-right">
          <n-input
            v-model:value="searchKeyword"
            round
            placeholder="搜索帖子、用户或学习圈"
            class="search-input"
            @keyup.enter="handleSearch"
          >
            <template #prefix>
              <n-icon>
                <SearchOutline />
              </n-icon>
            </template>
          </n-input>

          <button
            class="header-icon-btn"
            title="私信"
            @click="navigate('/messages')"
          >
            <n-icon size="18">
              <ChatbubblesOutline />
            </n-icon>
          </button>

          <button
            class="header-icon-btn"
            title="通知"
            @click="navigate('/notifications')"
          >
            <n-icon size="18">
              <NotificationsOutline />
            </n-icon>
          </button>

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
}

.sidebar {
  position: fixed;
  inset: 0 auto 0 0;
  width: var(--cf-sidebar-width);
  background: rgba(255, 255, 255, 0.94);
  backdrop-filter: blur(16px);
  border-right: 1px solid var(--cf-border);
  display: flex;
  flex-direction: column;
  padding: 20px;
  z-index: 30;
}

.sidebar-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.brand {
  display: flex;
  align-items: center;
  gap: 12px;
  cursor: pointer;
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
  box-shadow: 0 10px 24px rgba(0, 88, 190, 0.18);
}

.brand-title {
  font-family: var(--cf-font-heading);
  font-size: 18px;
  font-weight: 700;
}

.brand-subtitle {
  color: var(--cf-text-muted);
  font-size: 12px;
}

.mobile-close,
.mobile-menu-btn,
.header-icon-btn {
  border: none;
  background: transparent;
  cursor: pointer;
}

.mobile-close,
.mobile-menu-btn {
  display: none;
  width: 38px;
  height: 38px;
  border-radius: 12px;
  align-items: center;
  justify-content: center;
  color: var(--cf-text-secondary);
}

.profile-card {
  margin-top: 24px;
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 14px;
  border-radius: 18px;
  background: var(--cf-bg-soft);
  border: 1px solid rgba(194, 209, 234, 0.8);
  cursor: pointer;
}

.profile-meta {
  display: flex;
  flex-direction: column;
  gap: 4px;

  strong {
    font-size: 15px;
  }

  span {
    font-size: 12px;
    color: var(--cf-text-muted);
  }
}

.nav-menu {
  margin-top: 24px;
  display: flex;
  flex-direction: column;
  gap: 8px;
  flex: 1;
}

.nav-item {
  width: 100%;
  border: none;
  background: transparent;
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 13px 14px;
  border-radius: 14px;
  color: var(--cf-text-secondary);
  cursor: pointer;
  transition: all 0.22s ease;
  font-weight: 600;
}

.nav-item:hover {
  background: var(--cf-bg-soft);
  color: var(--cf-text-primary);
}

.nav-item.active {
  background: var(--cf-primary-soft);
  color: var(--cf-primary);
}

.sidebar-bottom {
  padding-top: 16px;
}

.publish-btn {
  width: 100%;
}

.content-wrapper {
  margin-left: var(--cf-sidebar-width);
  min-height: 100vh;
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
  background: rgba(248, 249, 255, 0.86);
  backdrop-filter: blur(16px);
  border-bottom: 1px solid rgba(217, 226, 242, 0.85);
}

.header-title-group {
  display: flex;
  align-items: center;
  gap: 14px;
}

.page-title {
  margin: 0;
  font-family: var(--cf-font-heading);
  font-size: 24px;
  line-height: 1.1;
}

.page-subtitle {
  margin: 4px 0 0;
  color: var(--cf-text-muted);
  font-size: 13px;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 10px;
}

.search-input {
  width: 280px;
}

.header-icon-btn {
  width: 40px;
  height: 40px;
  border-radius: 12px;
  background: var(--cf-bg-elevated);
  border: 1px solid var(--cf-border);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  color: var(--cf-text-secondary);
  transition: all 0.22s ease;
}

.header-icon-btn:hover,
.mobile-close:hover,
.mobile-menu-btn:hover {
  background: var(--cf-bg-soft);
  color: var(--cf-primary);
}

.user-profile-trigger {
  height: 40px;
  padding: 0 12px 0 8px;
  border-radius: 999px;
  background: var(--cf-bg-elevated);
  border: 1px solid var(--cf-border);
  display: inline-flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  color: var(--cf-text-primary);
  font-size: 14px;
  font-weight: 600;
}

.page-content {
  flex: 1;
  padding: 24px;
  overflow-x: hidden;
}

.mobile-mask {
  display: none;
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

@media (max-width: 1100px) {
  .search-input {
    width: 220px;
  }
}

@media (max-width: 960px) {
  .mobile-menu-btn,
  .mobile-close {
    display: inline-flex;
  }

  .sidebar {
    transform: translateX(-100%);
    transition: transform 0.24s ease;
    box-shadow: 0 20px 48px rgba(11, 28, 48, 0.12);
  }

  .sidebar.is-open {
    transform: translateX(0);
  }

  .content-wrapper {
    margin-left: 0;
  }

  .mobile-mask {
    display: block;
    position: fixed;
    inset: 0;
    background: rgba(11, 28, 48, 0.3);
    z-index: 25;
  }

  .top-header {
    padding: 0 16px;
  }

  .page-content {
    padding: 16px;
  }
}

@media (max-width: 720px) {
  .page-subtitle,
  .user-profile-trigger span {
    display: none;
  }

  .search-input {
    display: none;
  }

  .page-title {
    font-size: 20px;
  }
}
</style>
