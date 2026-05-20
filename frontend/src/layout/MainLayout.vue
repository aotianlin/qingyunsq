<script setup lang="ts">
import { computed, h, onMounted, onUnmounted, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { useAuthStore } from '@/stores/auth';
import { getMe, logout as apiLogout } from '@/api/auth';
import {
  BookOutline,
  ChatbubblesOutline,
  CheckmarkCircleOutline,
  CompassOutline,
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
  TrailSignOutline,
} from '@vicons/ionicons5';
import { NAvatar, NDropdown, NIcon, NInput } from 'naive-ui';

const router = useRouter();
const route = useRoute();
const authStore = useAuthStore();
const mobileMenuVisible = ref(false);
const searchKeyword = ref('');
const quoteIndex = ref(0);
let quoteTimer: ReturnType<typeof setInterval> | undefined;

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

const showTitleIconOnly = computed(() => route.path.startsWith('/search'));

const quoteItems = [
  { text: '博观而约取，厚积而薄发。', source: '苏轼', icon: BookOutline },
  { text: '知不足而奋进，望远山而前行。', source: '校园札记', icon: TrailSignOutline },
  { text: '学而不思则罔，思而不学则殆。', source: '论语', icon: CompassOutline },
  { text: '日日行，不怕千万里。', source: '格言联璧', icon: StarOutline },
  { text: '把问题写清楚，答案就近了一半。', source: 'CampusForum', icon: SparklesOutline },
] as const;

const activeQuote = computed(() => quoteItems[quoteIndex.value]);

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
  quoteTimer = setInterval(() => {
    quoteIndex.value = (quoteIndex.value + 1) % quoteItems.length;
  }, 3200);

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

onUnmounted(() => {
  if (quoteTimer) {
    clearInterval(quoteTimer);
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
            <div
              v-if="showTitleIconOnly"
              class="page-icon-title"
              aria-label="名言"
            >
              <n-icon size="22">
                <BookOutline />
              </n-icon>
            </div>
            <h1
              v-else
              class="page-title"
            >
              {{ pageTitle }}
            </h1>
            <div class="quote-ticker">
              <transition
                name="quote-roll"
                mode="out-in"
              >
                <div
                  :key="activeQuote.text"
                  class="quote-line"
                >
                  <span class="quote-icon">
                    <n-icon size="14">
                      <component :is="activeQuote.icon" />
                    </n-icon>
                  </span>
                  <span class="quote-text">{{ activeQuote.text }}</span>
                  <span class="quote-source">{{ activeQuote.source }}</span>
                </div>
              </transition>
            </div>
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
  background:
    linear-gradient(180deg, var(--cf-surface-highlight), transparent 42%),
    linear-gradient(90deg, var(--cf-bg-glass-strong), var(--cf-bg-glass-soft));
  backdrop-filter: blur(var(--cf-backdrop-blur)) saturate(136%);
  -webkit-backdrop-filter: blur(var(--cf-backdrop-blur)) saturate(136%);
  border-right: 1px solid var(--cf-border-glass);
  box-shadow: 22px 0 72px color-mix(in srgb, var(--cf-text-primary) 10%, transparent), 1px 0 0
    color-mix(in srgb, #ffffff 40%, transparent) inset;
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
  background:
    linear-gradient(180deg, var(--cf-surface-highlight), transparent 54%),
    var(--cf-bg-readable);
  border: 1px solid var(--cf-border-glass);
  box-shadow: var(--cf-shadow-soft);
  backdrop-filter: blur(var(--cf-backdrop-blur)) saturate(130%);
  -webkit-backdrop-filter: blur(var(--cf-backdrop-blur)) saturate(130%);
  cursor: pointer;
  transition: transform 0.26s var(--cf-motion-ease), box-shadow 0.26s var(--cf-motion-ease), border-color 0.26s ease;
}

.profile-card:hover {
  transform: translate3d(0, -3px, 0);
  border-color: var(--cf-border-strong);
  box-shadow: var(--cf-shadow-card-hover);
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
  border: 1px solid transparent;
  background: transparent;
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 13px 14px;
  border-radius: 14px;
  color: var(--cf-text-secondary);
  cursor: pointer;
  transition: transform 0.24s var(--cf-motion-ease), box-shadow 0.24s var(--cf-motion-ease), color 0.22s ease,
    background 0.22s ease, border-color 0.22s ease;
  font-weight: 600;
}

.nav-item:hover {
  background: var(--cf-bg-glass-soft);
  border-color: var(--cf-border-glass);
  box-shadow: 0 14px 34px color-mix(in srgb, var(--cf-text-primary) 8%, transparent);
  color: var(--cf-text-primary);
  transform: translate3d(4px, -1px, 0);
}

.nav-item.active {
  background:
    linear-gradient(135deg, color-mix(in srgb, var(--cf-primary) 20%, transparent), color-mix(in srgb, var(--cf-secondary) 10%, transparent)),
    var(--cf-bg-glass-soft);
  border-color: color-mix(in srgb, var(--cf-primary) 32%, var(--cf-border-glass));
  box-shadow: 0 18px 46px color-mix(in srgb, var(--cf-primary) 18%, transparent);
  color: var(--cf-primary);
}

.sidebar-bottom {
  padding-top: 16px;
}

.publish-btn {
  width: 100%;
  box-shadow: var(--cf-shadow-glow);
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
  background:
    linear-gradient(180deg, var(--cf-surface-highlight), transparent 74%),
    linear-gradient(90deg, var(--cf-bg-glass-strong), var(--cf-bg-glass-soft));
  backdrop-filter: blur(var(--cf-backdrop-blur)) saturate(136%);
  -webkit-backdrop-filter: blur(var(--cf-backdrop-blur)) saturate(136%);
  border-bottom: 1px solid var(--cf-border-glass);
  box-shadow: 0 20px 70px color-mix(in srgb, var(--cf-text-primary) 9%, transparent), 0 -1px 0
    color-mix(in srgb, #ffffff 36%, transparent) inset;
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

.page-icon-title {
  width: 42px;
  height: 42px;
  border-radius: 16px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  color: var(--cf-primary);
  background:
    linear-gradient(180deg, color-mix(in srgb, var(--cf-primary) 18%, transparent), transparent),
    var(--cf-bg-glass);
  border: 1px solid color-mix(in srgb, var(--cf-primary) 32%, var(--cf-border-glass));
  box-shadow: 0 16px 38px color-mix(in srgb, var(--cf-primary) 18%, transparent), inset 0 1px 0 rgba(255, 255, 255, 0.34);
}

.quote-ticker {
  margin-top: 4px;
  min-height: 20px;
  overflow: hidden;
}

.quote-line {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 4px 10px 4px 8px;
  border-radius: 999px;
  background: var(--cf-bg-glass);
  border: 1px solid var(--cf-border-glass);
  color: var(--cf-text-muted);
  font-size: 12px;
  line-height: 1;
}

.quote-icon {
  width: 18px;
  height: 18px;
  border-radius: 999px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  color: var(--cf-primary);
  background: color-mix(in srgb, var(--cf-primary) 10%, transparent);
}

.quote-text {
  color: var(--cf-text-secondary);
}

.quote-source {
  padding-left: 4px;
  color: var(--cf-primary);
  white-space: nowrap;
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

.header-icon-btn:hover,
.mobile-close:hover,
.mobile-menu-btn:hover {
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

.quote-roll-enter-active,
.quote-roll-leave-active {
  transition: opacity 0.24s ease, transform 0.24s ease;
}

.quote-roll-enter-from,
.quote-roll-leave-to {
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
    background: color-mix(in srgb, var(--cf-bg-base) 44%, rgba(0, 0, 0, 0.4));
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
  .user-profile-trigger span {
    display: none;
  }

  .quote-ticker {
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
