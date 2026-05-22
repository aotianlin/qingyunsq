<script setup lang="ts">
import { computed, h, nextTick, onMounted, onUnmounted, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { useAuthStore } from '@/stores/auth';
import { getMe, logout as apiLogout } from '@/api/auth';
import { getUnreadCount as getNotifUnreadCount } from '@/api/notifications';
import { getUnreadCount as getMsgUnreadCount } from '@/api/messages';
import { useWebSocket } from '@/composables/useWebSocket';
import {
  ChatbubblesOutline,
  CheckmarkCircleOutline,
  ChevronDownOutline,
  DocumentTextOutline,
  ExpandOutline,
  LogOutOutline,
  NotificationsOutline,
  PersonOutline,
  PlanetOutline,
  SearchOutline,
  SendOutline,
  SettingsOutline,
  SparklesOutline,
  StarOutline,
  BonfireOutline,
  AddOutline,
  TrashOutline,
} from '@vicons/ionicons5';
import { NAvatar, NBadge, NDropdown, NIcon, NInput } from 'naive-ui';
import { aiRagChat } from '@/api/ai';
import type { AiCitation } from '@/types/ai';

type FloatingAiMessage = { role: 'user' | 'assistant'; content: string; citations?: AiCitation[] };
type FloatingAiState = {
  version?: number;
  messages: FloatingAiMessage[];
  draft: string;
  open: boolean;
  hidden: boolean;
  bubble: boolean;
  float: boolean;
};

const FLOATING_AI_STATE_VERSION = 3;
const robotBubbleItems = [
  '想快速了解一个帖子？问我就行',
  '资源、帖子、学习圈都可以帮你检索',
  '粘贴问题，我会结合站内内容回答',
  '需要整理重点、追问概念，都可以找我',
];

const router = useRouter();
const route = useRoute();
const authStore = useAuthStore();
const searchKeyword = ref('');
const headerScrolled = ref(false);
const notifUnread = ref(0);
const msgUnread = ref(0);

// 监听 WebSocket 事件，实时更新未读数
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

/** 请求浏览器通知权限并显示桌面弹窗 */
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

const floatingAiOpen = ref(false);
const floatingAiHidden = ref(false);
const floatingAiBubbleEnabled = ref(true);
const floatingAiFloatEnabled = ref(true);
const floatingAiContextVisible = ref(false);
const floatingAiSettingsVisible = ref(false);
const floatingAiQuestion = ref('');
const floatingAiLoading = ref(false);
const floatingAiStreamRef = ref<HTMLElement | null>(null);
const defaultFloatingAiMessage: FloatingAiMessage = {
  role: 'assistant',
  content: '我可以随时帮你检索站内内容、解释帖子或整理学习问题。',
};
const floatingAiMessages = ref<FloatingAiMessage[]>([defaultFloatingAiMessage]);

const floatingAiStorageKey = computed(() => {
  const token = authStore.token || localStorage.getItem('token') || 'guest';
  return `campus-floating-ai:${token.slice(-18)}`;
});

const floatingAiLastQuestion = computed(() => [...floatingAiMessages.value].reverse().find((item) => item.role === 'user')?.content || '');
const floatingAiHasHistory = computed(() => floatingAiMessages.value.some((item) => item.role === 'user'));
const robotBubbleLoopItems = computed(() => [...robotBubbleItems, ...robotBubbleItems].map((text, index) => ({ key: `${index}-${text}`, text })));

const navLinks = [
  { name: '广场', path: '/square', icon: PlanetOutline },
  { name: '学习圈', path: '/spaces', icon: BonfireOutline },
  { name: '资源', path: '/resources', icon: DocumentTextOutline },
  { name: '打卡', path: '/checkin', icon: CheckmarkCircleOutline },
  { name: '积分中心', path: '/points', icon: StarOutline },
  { name: 'AI 助手', path: '/ai', icon: SparklesOutline },
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
  restoreFloatingAiState();
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

  // 加载未读数
  if (authStore.isLoggedIn) {
    notifUnread.value = await getNotifUnreadCount().catch(() => 0);
    msgUnread.value = Number(await getMsgUnreadCount().catch(() => 0));
    // 请求桌面通知权限
    if ('Notification' in window && Notification.permission === 'default') {
      Notification.requestPermission();
    }
  }
});

onUnmounted(() => {
  saveFloatingAiState();
  window.removeEventListener('scroll', updateTopHeaderState);
});

watch(
  [floatingAiMessages, floatingAiQuestion, floatingAiOpen, floatingAiHidden, floatingAiBubbleEnabled, floatingAiFloatEnabled],
  () => saveFloatingAiState(),
  { deep: true },
);

watch(
  () => floatingAiStorageKey.value,
  () => restoreFloatingAiState(),
);

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

function openFloatingAi() {
  floatingAiContextVisible.value = false;
  floatingAiSettingsVisible.value = false;
  floatingAiOpen.value = true;
  floatingAiHidden.value = false;
  void scrollFloatingAiToBottom();
}

function closeFloatingAi() {
  floatingAiOpen.value = false;
}

function showRobotSettingsMenu(event: MouseEvent) {
  event.preventDefault();
  floatingAiOpen.value = false;
  floatingAiContextVisible.value = true;
  floatingAiSettingsVisible.value = false;
}

function openRobotSettings() {
  floatingAiContextVisible.value = false;
  floatingAiSettingsVisible.value = true;
}

function closeRobotSettings() {
  floatingAiSettingsVisible.value = false;
}

function hideFloatingAiRobot() {
  floatingAiOpen.value = false;
  floatingAiContextVisible.value = false;
  floatingAiSettingsVisible.value = false;
  floatingAiHidden.value = true;
}

function restoreFloatingAiRobot() {
  floatingAiHidden.value = false;
  floatingAiOpen.value = false;
  floatingAiContextVisible.value = false;
  floatingAiSettingsVisible.value = false;
}

async function sendFloatingAiQuestion() {
  const question = floatingAiQuestion.value.trim();
  if (!question || floatingAiLoading.value) return;

  floatingAiQuestion.value = '';
  floatingAiMessages.value.push({ role: 'user', content: question });
  floatingAiLoading.value = true;
  void scrollFloatingAiToBottom();

  try {
    const history = floatingAiMessages.value.slice(-8).map((item) => ({
      role: item.role,
      content: item.content,
    }));
    const result = await aiRagChat(history);
    floatingAiMessages.value.push({
      role: 'assistant',
      content: result.reply || '暂时没有生成有效回复，请换一种问法再试。',
      citations: result.citations || [],
    });
  } catch {
    floatingAiMessages.value.push({
      role: 'assistant',
      content: '这次问答请求失败了，请稍后再试。',
    });
  } finally {
    floatingAiLoading.value = false;
    void scrollFloatingAiToBottom();
  }
}

function openCitation(citation: AiCitation) {
  if (!citation.url) return;
  router.push(citation.url);
  floatingAiOpen.value = false;
  floatingAiContextVisible.value = false;
  floatingAiSettingsVisible.value = false;
}

async function openAiWorkspace() {
  floatingAiOpen.value = false;
  floatingAiContextVisible.value = false;
  floatingAiSettingsVisible.value = false;
  floatingAiHidden.value = false;
  await router.push({
    path: '/ai',
    query: {
      mode: 'qa',
      entry: 'floating',
      focus: String(Date.now()),
    },
    hash: '#ai-workspace',
  });
}

function clearFloatingAi() {
  floatingAiMessages.value = [defaultFloatingAiMessage];
  floatingAiQuestion.value = '';
}

function restoreFloatingAiState() {
  if (!authStore.isLoggedIn) return;

  try {
    const raw = localStorage.getItem(floatingAiStorageKey.value);
    if (!raw) return;
    const state = JSON.parse(raw) as Partial<FloatingAiState>;
    if (Array.isArray(state.messages) && state.messages.length) {
      floatingAiMessages.value = state.messages
        .filter((item) => (item.role === 'user' || item.role === 'assistant') && typeof item.content === 'string')
        .slice(-24);
    }
    floatingAiQuestion.value = typeof state.draft === 'string' ? state.draft : '';
    floatingAiOpen.value = Boolean(state.open);
    floatingAiHidden.value = state.version === FLOATING_AI_STATE_VERSION ? Boolean(state.hidden) : false;
    floatingAiBubbleEnabled.value = state.version === FLOATING_AI_STATE_VERSION ? state.bubble !== false : true;
    floatingAiFloatEnabled.value = state.version === FLOATING_AI_STATE_VERSION ? state.float !== false : true;
  } catch {
    floatingAiMessages.value = [defaultFloatingAiMessage];
    floatingAiHidden.value = false;
    floatingAiBubbleEnabled.value = true;
    floatingAiFloatEnabled.value = true;
  }
}

function saveFloatingAiState() {
  if (!authStore.isLoggedIn) return;

  const state: FloatingAiState = {
    version: FLOATING_AI_STATE_VERSION,
    messages: floatingAiMessages.value.slice(-24),
    draft: floatingAiQuestion.value,
    open: floatingAiOpen.value,
    hidden: floatingAiHidden.value,
    bubble: floatingAiBubbleEnabled.value,
    float: floatingAiFloatEnabled.value,
  };
  localStorage.setItem(floatingAiStorageKey.value, JSON.stringify(state));
}

async function scrollFloatingAiToBottom() {
  await nextTick();
  if (floatingAiStreamRef.value) {
    floatingAiStreamRef.value.scrollTop = floatingAiStreamRef.value.scrollHeight;
  }
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
          <n-badge :value="msgUnread" :max="99" :show="msgUnread > 0">
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
          <n-badge :value="notifUnread" :max="99" :show="notifUnread > 0">
            <n-icon size="18">
              <NotificationsOutline />
            </n-icon>
          </n-badge>
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

    <div
      v-if="authStore.isLoggedIn"
      class="floating-ai"
      :class="{ open: floatingAiOpen, hidden: floatingAiHidden }"
    >
      <button
        v-if="floatingAiHidden"
        class="floating-ai-restore"
        title="显示 AI 助手"
        @click="restoreFloatingAiRobot"
      />

      <section
        v-else-if="floatingAiOpen"
        class="floating-ai-panel"
      >
        <header class="floating-ai-head">
          <div>
            <span>RAG Assistant</span>
            <strong>站内智能问答</strong>
            <small v-if="floatingAiLastQuestion">最近：{{ floatingAiLastQuestion }}</small>
          </div>
          <div class="floating-actions">
            <button
              title="清空当前对话"
              :disabled="!floatingAiHasHistory"
              @click="clearFloatingAi"
            >
              <n-icon size="16"><TrashOutline /></n-icon>
            </button>
            <button
              title="打开完整 AI 工作台"
              @click="openAiWorkspace"
            >
              <n-icon size="16"><ExpandOutline /></n-icon>
            </button>
            <button
              title="关闭对话窗口"
              @click="closeFloatingAi"
            >
              <n-icon size="16"><ChevronDownOutline /></n-icon>
            </button>
          </div>
        </header>

        <div
          ref="floatingAiStreamRef"
          class="floating-ai-stream"
        >
          <article
            v-for="(item, index) in floatingAiMessages"
            :key="`${item.role}-${index}`"
            class="floating-message"
            :class="item.role"
          >
            <div class="floating-message-content">
              {{ item.content }}
            </div>
            <div
              v-if="item.citations?.length"
              class="floating-citations"
            >
              <button
                v-for="citation in item.citations"
                :key="`${citation.type}-${citation.id}`"
                @click="openCitation(citation)"
              >
                {{ citation.title }}
              </button>
            </div>
          </article>

          <div
            v-if="floatingAiLoading"
            class="floating-loading"
          >
            <span />
            正在检索并组织回答
          </div>
        </div>

        <div class="floating-ai-input-row">
          <textarea
            v-model="floatingAiQuestion"
            placeholder="问课程、资源、帖子或平台使用问题"
            @keydown.enter.exact.prevent="sendFloatingAiQuestion"
          />
          <button
            :disabled="floatingAiLoading || !floatingAiQuestion.trim()"
            @click="sendFloatingAiQuestion"
          >
            <n-icon size="17"><SendOutline /></n-icon>
          </button>
        </div>
      </section>

      <div
        v-else
        class="floating-ai-robot-shell"
      >
        <div
          v-if="floatingAiBubbleEnabled"
          class="floating-ai-bubble"
          aria-hidden="true"
        >
          <div class="bubble-track">
            <span
              v-for="item in robotBubbleLoopItems"
              :key="item.key"
            >
              {{ item.text }}
            </span>
          </div>
        </div>
        <button
          class="floating-ai-robot"
          :class="{ floating: floatingAiFloatEnabled }"
          title="打开 AI 问答"
          @click="openFloatingAi"
          @contextmenu="showRobotSettingsMenu"
        >
          <span
            class="robot-illustration"
            aria-hidden="true"
          >
            <span class="robot-head">
              <span class="robot-visor">
                <span class="robot-eye primary" />
                <span class="robot-eye secondary" />
              </span>
            </span>
            <span class="robot-arm robot-arm-left">
              <span class="robot-hand">
                <span />
                <span />
                <span />
              </span>
            </span>
            <span class="robot-arm robot-arm-right" />
            <span class="robot-body">
              <span class="robot-core-light" />
            </span>
            <span class="robot-leg robot-leg-left" />
            <span class="robot-leg robot-leg-right" />
          </span>
          <span class="robot-orbit">
            <n-icon size="16">
              <SparklesOutline />
            </n-icon>
          </span>
        </button>
        <div
          v-if="floatingAiContextVisible"
          class="floating-ai-context-menu"
        >
          <button
            title="设置机器人"
            @click="openRobotSettings"
          >
            <n-icon size="16"><SettingsOutline /></n-icon>
            <span>设置</span>
          </button>
        </div>

        <section
          v-if="floatingAiSettingsVisible"
          class="floating-ai-settings"
        >
          <header>
            <strong>机器人设置</strong>
            <button
              title="关闭设置"
              @click="closeRobotSettings"
            >
              ×
            </button>
          </header>
          <label>
            <input
              v-model="floatingAiBubbleEnabled"
              type="checkbox"
            />
            <span>显示云朵提示</span>
          </label>
          <label>
            <input
              v-model="floatingAiFloatEnabled"
              type="checkbox"
            />
            <span>上下浮动</span>
          </label>
          <button
            class="settings-link-btn"
            @click="openAiWorkspace"
          >
            打开 AI 工作台
          </button>
          <button
            class="settings-danger-btn"
            @click="hideFloatingAiRobot"
          >
            隐藏
          </button>
        </section>
      </div>
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
  background: var(--cf-bg-glass-soft);
  border-color: var(--cf-border-glass);
  box-shadow: 0 14px 34px color-mix(in srgb, var(--cf-text-primary) 8%, transparent);
  color: var(--cf-text-primary);
  transform: translate3d(0, -1px, 0);
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

.header-icon-btn:hover,
.top-nav-item:hover {
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

.floating-ai {
  position: fixed;
  right: 24px;
  top: 52%;
  transform: translateY(-50%);
  z-index: 35;
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 12px;
  pointer-events: none;
}

.floating-ai.hidden {
  right: 0;
  align-items: flex-end;
}

.floating-ai-panel,
.floating-ai-robot-shell,
.floating-ai-restore {
  pointer-events: auto;
}

.floating-ai.open {
  top: auto;
  bottom: 24px;
  transform: none;
}

.floating-ai-panel {
  width: min(390px, calc(100vw - 32px));
  height: min(560px, calc(100vh - 112px));
  border-radius: 22px;
  background:
    linear-gradient(180deg, var(--cf-surface-highlight), transparent 46%),
    color-mix(in srgb, var(--cf-bg-readable) 94%, transparent);
  border: 1px solid color-mix(in srgb, var(--cf-primary) 18%, var(--cf-border-glass));
  box-shadow: 0 28px 90px color-mix(in srgb, var(--cf-text-primary) 22%, transparent);
  backdrop-filter: blur(22px) saturate(150%);
  -webkit-backdrop-filter: blur(22px) saturate(150%);
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.floating-ai-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 16px 16px 12px;
  border-bottom: 1px solid var(--cf-border);
}

.floating-ai-head span {
  display: block;
  color: var(--cf-primary);
  font-size: 12px;
  font-weight: 800;
  letter-spacing: 0;
}

.floating-ai-head strong {
  display: block;
  margin-top: 4px;
  color: var(--cf-text-primary);
  font-size: 17px;
  line-height: 1.2;
}

.floating-ai-head small {
  display: block;
  max-width: 210px;
  margin-top: 4px;
  color: var(--cf-text-muted);
  font-size: 12px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.floating-actions {
  display: flex;
  gap: 8px;
}

.floating-actions button {
  width: 34px;
  height: 34px;
  border-radius: 11px;
  border: 1px solid var(--cf-border);
  background: var(--cf-bg-glass-soft);
  color: var(--cf-text-secondary);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
}

.floating-actions button:disabled {
  cursor: not-allowed;
  opacity: 0.42;
}

.floating-actions button:hover {
  color: var(--cf-primary);
  border-color: var(--cf-border-strong);
  background: var(--cf-bg-readable);
}

.floating-ai-stream {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 14px 16px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.floating-message {
  display: flex;
  flex-direction: column;
  gap: 7px;
  max-width: 88%;
}

.floating-message.assistant {
  align-self: flex-start;
}

.floating-message.user {
  align-self: flex-end;
}

.floating-message-content {
  padding: 10px 12px;
  border-radius: 14px;
  border: 1px solid var(--cf-border);
  background: var(--cf-bg-glass-soft);
  color: var(--cf-text-primary);
  line-height: 1.65;
  font-size: 13px;
  white-space: pre-wrap;
  word-break: break-word;
}

.floating-message.user .floating-message-content {
  color: var(--cf-text-inverse);
  background: var(--cf-primary);
  border-color: color-mix(in srgb, var(--cf-primary) 60%, transparent);
}

.floating-citations {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.floating-citations button {
  max-width: 100%;
  border: 1px solid color-mix(in srgb, var(--cf-primary) 30%, var(--cf-border));
  background: color-mix(in srgb, var(--cf-primary-soft) 70%, var(--cf-bg-glass-soft));
  color: var(--cf-primary);
  border-radius: 999px;
  padding: 5px 8px;
  font-size: 12px;
  font-weight: 700;
  cursor: pointer;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.floating-loading {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  color: var(--cf-text-muted);
  font-size: 13px;
}

.floating-loading span {
  width: 9px;
  height: 9px;
  border-radius: 50%;
  background: var(--cf-primary);
  box-shadow: 0 0 0 0 color-mix(in srgb, var(--cf-primary) 38%, transparent);
  animation: floatingPulse 1.2s infinite;
}

.floating-ai-input-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 42px;
  gap: 10px;
  padding: 12px;
  border-top: 1px solid var(--cf-border);
  background: color-mix(in srgb, var(--cf-bg-glass-soft) 84%, transparent);
}

.floating-ai-input-row textarea {
  min-height: 48px;
  max-height: 120px;
  resize: vertical;
  border: 1px solid var(--cf-border);
  border-radius: 14px;
  background: var(--cf-bg-readable);
  color: var(--cf-text-primary);
  padding: 10px 12px;
  line-height: 1.5;
  font: inherit;
  outline: none;
}

.floating-ai-input-row textarea:focus {
  border-color: var(--cf-border-strong);
  box-shadow: 0 0 0 4px color-mix(in srgb, var(--cf-primary) 10%, transparent);
}

.floating-ai-input-row button {
  width: 42px;
  height: 42px;
  align-self: end;
  border: 0;
  border-radius: 14px;
  background: var(--cf-primary);
  color: var(--cf-text-inverse);
  box-shadow: var(--cf-shadow-glow);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
}

.floating-ai-input-row button:disabled {
  opacity: 0.5;
  cursor: not-allowed;
  box-shadow: none;
}

.floating-ai-robot-shell {
  position: relative;
  width: 214px;
  height: 202px;
  display: flex;
  align-items: center;
  justify-content: flex-end;
}

.floating-ai-bubble {
  position: absolute;
  right: 56px;
  top: 0;
  width: 168px;
  height: 52px;
  padding: 0 16px;
  border-radius: 24px 24px 8px 24px;
  background: color-mix(in srgb, var(--cf-bg-readable) 94%, transparent);
  border: 1px solid color-mix(in srgb, var(--cf-primary) 20%, var(--cf-border-glass));
  box-shadow: 0 18px 42px color-mix(in srgb, var(--cf-text-primary) 14%, transparent);
  backdrop-filter: blur(18px) saturate(145%);
  -webkit-backdrop-filter: blur(18px) saturate(145%);
  overflow: hidden;
  color: var(--cf-text-primary);
  pointer-events: none;
}

.floating-ai-bubble::after {
  content: '';
  position: absolute;
  right: 18px;
  bottom: -8px;
  width: 18px;
  height: 18px;
  border-right: 1px solid color-mix(in srgb, var(--cf-primary) 20%, var(--cf-border-glass));
  border-bottom: 1px solid color-mix(in srgb, var(--cf-primary) 20%, var(--cf-border-glass));
  background: color-mix(in srgb, var(--cf-bg-readable) 94%, transparent);
  transform: rotate(45deg);
}

.bubble-track {
  display: flex;
  flex-direction: column;
  animation: bubbleTextScroll 14s linear infinite;
}

.bubble-track span {
  height: 52px;
  display: flex;
  align-items: center;
  font-size: 13px;
  font-weight: 700;
  line-height: 1.35;
  white-space: normal;
}

.floating-ai-robot {
  position: relative;
  width: 104px;
  height: 132px;
  border: 0;
  border-radius: 34px;
  background: transparent;
  color: var(--cf-primary);
  box-shadow: none;
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  transition: filter 0.24s ease, transform 0.24s var(--cf-motion-ease);
}

.floating-ai-robot.floating {
  animation: robotFloat 3.2s ease-in-out infinite;
}

.floating-ai-robot::after {
  content: '';
  position: absolute;
  left: 20px;
  right: 12px;
  bottom: 1px;
  height: 14px;
  border-radius: 50%;
  background: color-mix(in srgb, var(--cf-primary) 22%, transparent);
  filter: blur(8px);
  opacity: 0.58;
}

.robot-illustration {
  position: relative;
  width: 96px;
  height: 124px;
  display: block;
  transform-origin: 50% 82%;
}

.robot-head {
  position: absolute;
  left: 18px;
  top: 2px;
  width: 58px;
  height: 48px;
  border-radius: 24px 24px 20px 20px;
  background:
    radial-gradient(circle at 22% 18%, #ffffff 0 14%, transparent 15%),
    linear-gradient(155deg, #ffffff 0%, #e8fbff 56%, #b9eaf8 100%);
  border: 1px solid color-mix(in srgb, var(--cf-primary) 18%, #ffffff);
  box-shadow:
    inset -7px -8px 14px color-mix(in srgb, var(--cf-primary) 13%, transparent),
    0 10px 22px color-mix(in srgb, var(--cf-text-primary) 13%, transparent);
  z-index: 4;
}

.robot-head::before {
  content: '';
  position: absolute;
  left: -7px;
  top: 18px;
  width: 9px;
  height: 19px;
  border-radius: 999px 0 0 999px;
  background: linear-gradient(180deg, #d9f7ff, #8fd5eb);
  box-shadow: inset -2px -2px 4px color-mix(in srgb, var(--cf-primary) 14%, transparent);
}

.robot-head::after {
  content: '';
  position: absolute;
  right: -7px;
  top: 18px;
  width: 9px;
  height: 19px;
  border-radius: 0 999px 999px 0;
  background: linear-gradient(180deg, #d9f7ff, #8fd5eb);
  box-shadow: inset 2px -2px 4px color-mix(in srgb, var(--cf-primary) 14%, transparent);
}

.robot-visor {
  position: absolute;
  left: 8px;
  top: 13px;
  width: 42px;
  height: 22px;
  border-radius: 999px;
  background:
    radial-gradient(circle at 72% 45%, #7ff6ff 0 8%, transparent 9%),
    linear-gradient(135deg, #092434 0%, #0b344d 58%, #0b5375 100%);
  box-shadow:
    inset 0 0 0 2px color-mix(in srgb, #6beeff 22%, transparent),
    0 0 16px color-mix(in srgb, var(--cf-primary) 32%, transparent);
}

.robot-eye {
  position: absolute;
  top: 8px;
  border-radius: 50%;
  background: #55cfff;
  box-shadow: 0 0 8px #4edcff;
}

.robot-eye.primary {
  left: 13px;
  width: 8px;
  height: 8px;
}

.robot-eye.secondary {
  right: 11px;
  width: 5px;
  height: 5px;
  opacity: 0.9;
}

.robot-body {
  position: absolute;
  left: 28px;
  top: 52px;
  width: 40px;
  height: 48px;
  border-radius: 18px 18px 15px 15px;
  background:
    radial-gradient(circle at 32% 24%, #ffffff 0 12%, transparent 13%),
    linear-gradient(155deg, #ffffff 0%, #e9fbff 52%, #aee7f8 100%);
  border: 1px solid color-mix(in srgb, var(--cf-primary) 16%, #ffffff);
  box-shadow:
    inset -7px -7px 12px color-mix(in srgb, var(--cf-primary) 12%, transparent),
    0 10px 20px color-mix(in srgb, var(--cf-text-primary) 12%, transparent);
  z-index: 3;
}

.robot-core-light {
  position: absolute;
  left: 50%;
  top: 15px;
  width: 9px;
  height: 9px;
  border-radius: 50%;
  background: var(--cf-primary);
  box-shadow: 0 0 12px color-mix(in srgb, var(--cf-primary) 72%, transparent);
  transform: translateX(-50%);
}

.robot-arm {
  position: absolute;
  top: 56px;
  width: 13px;
  height: 41px;
  border-radius: 999px;
  background: linear-gradient(180deg, #effcff, #9eddf0);
  border: 1px solid color-mix(in srgb, var(--cf-primary) 15%, #ffffff);
  z-index: 2;
}

.robot-arm-left {
  left: 14px;
  top: 49px;
  height: 36px;
  transform: rotate(-34deg);
  transform-origin: 50% 10%;
}

.robot-arm-right {
  right: 16px;
  transform: rotate(20deg);
  transform-origin: 50% 8%;
}

.robot-hand {
  position: absolute;
  left: -6px;
  top: -14px;
  width: 22px;
  height: 21px;
  border-radius: 10px;
  background: linear-gradient(180deg, #f8ffff, #9fe5f7);
  border: 1px solid color-mix(in srgb, var(--cf-primary) 18%, #ffffff);
  display: flex;
  align-items: flex-start;
  justify-content: center;
  gap: 1px;
  padding-top: 3px;
}

.robot-hand span {
  width: 3px;
  height: 8px;
  border-radius: 999px;
  background: color-mix(in srgb, var(--cf-primary) 48%, #ffffff);
}

.robot-leg {
  position: absolute;
  top: 93px;
  width: 16px;
  height: 28px;
  border-radius: 999px 999px 10px 10px;
  background: linear-gradient(180deg, #eaffff, #8fdcf2);
  border: 1px solid color-mix(in srgb, var(--cf-primary) 16%, #ffffff);
  z-index: 1;
}

.robot-leg::after {
  content: '';
  position: absolute;
  left: -4px;
  bottom: -6px;
  width: 25px;
  height: 11px;
  border-radius: 999px;
  background: linear-gradient(180deg, #f7ffff, #84d9ef);
  border: 1px solid color-mix(in srgb, var(--cf-primary) 18%, #ffffff);
}

.robot-leg-left {
  left: 29px;
  transform: rotate(8deg);
}

.robot-leg-right {
  right: 28px;
  transform: rotate(-8deg);
}

.robot-orbit {
  position: absolute;
  right: -5px;
  top: -5px;
  width: 26px;
  height: 26px;
  border-radius: 50%;
  background: var(--cf-bg-readable);
  color: var(--cf-primary);
  box-shadow: 0 10px 24px color-mix(in srgb, var(--cf-text-primary) 13%, transparent);
  display: inline-flex;
  align-items: center;
  justify-content: center;
}

.floating-ai-robot-shell:hover .floating-ai-robot,
.floating-ai-robot:focus-visible {
  filter: drop-shadow(0 18px 22px color-mix(in srgb, var(--cf-primary) 24%, transparent));
}

.floating-ai-context-menu {
  position: absolute;
  right: 104px;
  top: 72px;
  display: flex;
  flex-direction: column;
  gap: 8px;
  pointer-events: auto;
  z-index: 4;
}

.floating-ai-context-menu button {
  min-width: 96px;
  height: 38px;
  padding: 0 12px;
  border-radius: 999px;
  border: 1px solid color-mix(in srgb, var(--cf-primary) 22%, var(--cf-border-glass));
  background: color-mix(in srgb, var(--cf-bg-readable) 94%, transparent);
  color: var(--cf-text-primary);
  box-shadow: 0 14px 36px color-mix(in srgb, var(--cf-text-primary) 12%, transparent);
  backdrop-filter: blur(18px) saturate(142%);
  -webkit-backdrop-filter: blur(18px) saturate(142%);
  display: inline-flex;
  align-items: center;
  justify-content: flex-start;
  gap: 7px;
  cursor: pointer;
  font-weight: 700;
  white-space: nowrap;
  transition: transform 0.2s var(--cf-motion-ease), border-color 0.2s ease, color 0.2s ease;
}

.floating-ai-context-menu button:hover {
  color: var(--cf-primary);
  border-color: var(--cf-border-strong);
  transform: translate3d(-2px, 0, 0);
}

.floating-ai-settings {
  position: absolute;
  right: 108px;
  top: 56px;
  width: 214px;
  padding: 14px;
  border-radius: 18px;
  background: color-mix(in srgb, var(--cf-bg-readable) 96%, transparent);
  border: 1px solid color-mix(in srgb, var(--cf-primary) 18%, var(--cf-border-glass));
  box-shadow: 0 22px 56px color-mix(in srgb, var(--cf-text-primary) 18%, transparent);
  backdrop-filter: blur(20px) saturate(150%);
  -webkit-backdrop-filter: blur(20px) saturate(150%);
  pointer-events: auto;
  z-index: 5;
}

.floating-ai-settings header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
}

.floating-ai-settings header strong {
  color: var(--cf-text-primary);
  font-size: 15px;
}

.floating-ai-settings header button {
  width: 26px;
  height: 26px;
  border: 1px solid var(--cf-border);
  border-radius: 9px;
  background: var(--cf-bg-glass-soft);
  color: var(--cf-text-secondary);
  cursor: pointer;
}

.floating-ai-settings label {
  height: 36px;
  display: flex;
  align-items: center;
  gap: 9px;
  color: var(--cf-text-secondary);
  font-size: 13px;
  font-weight: 700;
}

.floating-ai-settings input {
  accent-color: var(--cf-primary);
}

.settings-link-btn,
.settings-danger-btn {
  width: 100%;
  height: 36px;
  margin-top: 8px;
  border-radius: 12px;
  border: 1px solid var(--cf-border);
  background: var(--cf-bg-glass-soft);
  color: var(--cf-text-primary);
  cursor: pointer;
  font-weight: 800;
}

.settings-link-btn:hover {
  color: var(--cf-primary);
  border-color: var(--cf-border-strong);
}

.settings-danger-btn {
  background: color-mix(in srgb, #8a8f98 18%, var(--cf-bg-glass-soft));
  color: var(--cf-text-secondary);
}

.settings-danger-btn:hover {
  border-color: color-mix(in srgb, #8a8f98 50%, var(--cf-border));
  color: var(--cf-text-primary);
}

.floating-ai-restore {
  width: 0;
  height: 0;
  padding: 0;
  border-top: 22px solid transparent;
  border-bottom: 22px solid transparent;
  border-right: 30px solid color-mix(in srgb, #8b9098 84%, var(--cf-text-secondary));
  border-left: 0;
  background: transparent;
  filter: drop-shadow(0 12px 18px color-mix(in srgb, var(--cf-text-primary) 18%, transparent));
  cursor: pointer;
  opacity: 0.72;
  transition: opacity 0.2s ease, transform 0.2s var(--cf-motion-ease);
}

.floating-ai-restore:hover {
  opacity: 1;
  transform: translateX(-3px);
}

@keyframes floatingPulse {
  0% {
    box-shadow: 0 0 0 0 color-mix(in srgb, var(--cf-primary) 38%, transparent);
  }
  70% {
    box-shadow: 0 0 0 9px color-mix(in srgb, var(--cf-primary) 0%, transparent);
  }
  100% {
    box-shadow: 0 0 0 0 color-mix(in srgb, var(--cf-primary) 0%, transparent);
  }
}

@keyframes robotFloat {
  0%,
  100% {
    transform: translate3d(0, -5px, 0);
  }
  50% {
    transform: translate3d(0, 7px, 0);
  }
}

@keyframes bubbleTextScroll {
  0% {
    transform: translateY(0);
  }
  100% {
    transform: translateY(-50%);
  }
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

  .floating-ai {
    right: 14px;
    top: 52%;
    bottom: auto;
  }

  .floating-ai.open {
    right: 14px;
    top: auto;
    bottom: 14px;
  }

  .floating-ai-panel {
    width: calc(100vw - 28px);
    height: min(520px, calc(100vh - 92px));
  }

  .floating-ai-robot-shell {
    width: 178px;
    height: 178px;
  }

  .floating-ai-robot {
    width: 96px;
    height: 124px;
    border-radius: 24px;
  }

  .floating-ai-bubble {
    right: 42px;
    width: 138px;
    height: 46px;
    padding: 0 12px;
  }

  .bubble-track span {
    height: 46px;
    font-size: 12px;
  }

  .floating-ai-context-menu {
    right: 94px;
    top: 68px;
  }

  .floating-ai-settings {
    right: 92px;
    top: 44px;
    width: min(208px, calc(100vw - 124px));
  }

  .floating-ai.hidden {
    right: 0;
  }
}
</style>
