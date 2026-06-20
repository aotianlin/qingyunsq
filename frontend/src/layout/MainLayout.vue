<script setup lang="ts">
import { computed, h, nextTick, onMounted, onUnmounted, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { useAuthStore } from '@/stores/auth';
import { getMe, logout as apiLogout } from '@/api/auth';
import { getUnreadCount as getNotifUnreadCount } from '@/api/notifications';
import { getUnreadCount as getMsgUnreadCount } from '@/api/messages';
import { useWebSocket } from '@/composables/useWebSocket';
import ThemeToggle from '@/components/ThemeToggle.vue';
import {
  AlbumsOutline,
  ArrowBackOutline,
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
  BonfireOutline,
  AddOutline,
  TrashOutline,
  CopyOutline,
} from '@vicons/ionicons5';
import { NBadge, NDropdown, NIcon, useMessage } from 'naive-ui';
import { aiRagChat } from '@/api/ai';
import { copyTextToClipboard } from '@/utils/clipboard';
import type { AiCitation } from '@/types/ai';

type FloatingAiMessage = { role: 'user' | 'assistant'; content: string; citations?: AiCitation[] };
type FloatingAiPosition = { x: number; y: number };
type FloatingAiState = {
  version?: number;
  messages: FloatingAiMessage[];
  draft: string;
  open: boolean;
  hidden: boolean;
  bubble: boolean;
  float: boolean;
  position?: FloatingAiPosition | null;
};
type FloatingAiDragState = {
  pointerId: number;
  startClientX: number;
  startClientY: number;
  startX: number;
  startY: number;
  moved: boolean;
};

const FLOATING_AI_STATE_VERSION = 3;
const FLOATING_AI_DRAG_THRESHOLD = 4;
const robotBubbleItems = [
  '想快速了解一个帖子？问我就行',
  '资源、帖子、学习圈都可以帮你检索',
  '粘贴问题，我会结合站内内容回答',
  '需要整理重点、追问概要，都可以找我',
];

const router = useRouter();
const route = useRoute();
const authStore = useAuthStore();
const message = useMessage();
const searchKeyword = ref('');
const headerScrolled = ref(false);
const notifUnread = ref(0);
const msgUnread = ref(0);

// 鐩戝惉 WebSocket 浜嬩欢锛屽疄鏃舵洿鏂版湭璇绘暟
useWebSocket((event) => {
  if (
    event.type === 'COMMENT' ||
    event.type === 'LIKE' ||
    event.type === 'REPLY' ||
    event.type === 'MENTION' ||
    event.type === 'ACCEPT' ||
    event.type === 'JOIN' ||
    event.type === 'TAG_SUBSCRIBE' ||
    event.type === 'SYSTEM'
  ) {
    notifUnread.value++;
    showDesktopNotification(event.title || '新通知', event.content || '');
  } else if (event.type === 'MESSAGE') {
    msgUnread.value++;
    showDesktopNotification('新私信', event.content || '你收到了一条新消息');
  }
});

/** 璇锋眰娴忚鍣ㄩ€氱煡鏉冮檺骞舵樉绀烘闈㈠脊绐?*/
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
const floatingAiDragging = ref(false);
const floatingAiPosition = ref<FloatingAiPosition | null>(null);
const floatingAiDragState = ref<FloatingAiDragState | null>(null);
const floatingAiSuppressClick = ref(false);
const floatingAiRef = ref<HTMLElement | null>(null);
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

const floatingAiLastQuestion = computed(
  () => [...floatingAiMessages.value].reverse().find((item) => item.role === 'user')?.content || '',
);
const floatingAiHasHistory = computed(() =>
  floatingAiMessages.value.some((item) => item.role === 'user'),
);
const floatingAiPositionStyle = computed(() => {
  if (!floatingAiPosition.value || floatingAiHidden.value) return {};
  return {
    left: `${floatingAiPosition.value.x}px`,
    top: `${floatingAiPosition.value.y}px`,
    right: 'auto',
    bottom: 'auto',
  };
});
const robotBubbleLoopItems = computed(() =>
  [...robotBubbleItems, ...robotBubbleItems].map((text, index) => ({
    key: `${index}-${text}`,
    text,
  })),
);

const navLinks = [
  { name: '广场', path: '/square', icon: PlanetOutline, back: false },
  { name: '学习圈', path: '/spaces', icon: BonfireOutline, back: false },
  { name: '资源库', path: '/resources', icon: DocumentTextOutline, back: false },
  { name: '打卡', path: '/checkin', icon: CheckmarkCircleOutline, back: false },
  { name: 'AI 助手', path: '/ai', icon: SparklesOutline, back: false },
];

const isAiPage = computed(() => route.path.startsWith('/ai'));
const aiSections = ['chat', 'agents', 'plugins', 'knowledge'] as const;
type AiSection = (typeof aiSections)[number];
const currentAiSection = computed<AiSection>(() => {
  const section = route.query.section;
  return typeof section === 'string' && aiSections.includes(section as AiSection)
    ? (section as AiSection)
    : 'chat';
});
const aiNavLinks = [
  { name: '返回广场', path: '/square', icon: ArrowBackOutline, back: true },
  { name: '对话', path: '/ai?section=chat', icon: ChatbubblesOutline, section: 'chat', back: false },
  { name: '智能体', path: '/ai?section=agents', icon: SparklesOutline, section: 'agents', back: false },
  { name: '插件中心', path: '/ai?section=plugins', icon: SettingsOutline, section: 'plugins', back: false },
  { name: '知识库', path: '/ai?section=knowledge', icon: DocumentTextOutline, section: 'knowledge', back: false },
];
const displayedNavLinks = computed(() => (isAiPage.value ? aiNavLinks : navLinks));
const searchPlaceholder = computed(() =>
  isAiPage.value
    ? ({
        chat: '搜索问题、对话或插件',
        agents: '搜索智能体',
        plugins: '搜索插件名称或功能，例如：天气、翻译',
        knowledge: '搜索知识库、文档或内容',
      }[currentAiSection.value])
    : '搜索帖子、用户、资源或话题',
);
const primaryActionLabel = computed(() => '发布');
const primaryActionPath = computed(() => '/posts/new');
const brandSubtitle = computed(() => (isAiPage.value ? 'AI 助手' : '智慧校园社区'));

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
  window.addEventListener('resize', clampFloatingAiPosition);

  if (authStore.isLoggedIn && !authStore.user && localStorage.getItem('token') !== 'GUEST_TOKEN') {
    try {
      const user = await getMe();
      authStore.setUser(user);
    } catch {
      authStore.logout();
      router.push('/login');
    }
  }

  // 加载未读数
  if (authStore.isLoggedIn && authStore.user?.role !== 'GUEST') {
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
  window.removeEventListener('resize', clampFloatingAiPosition);
});

watch(
  [
    floatingAiMessages,
    floatingAiQuestion,
    floatingAiOpen,
    floatingAiHidden,
    floatingAiBubbleEnabled,
    floatingAiFloatEnabled,
  ],
  () => saveFloatingAiState(),
  { deep: true },
);

watch(
  () => floatingAiStorageKey.value,
  () => restoreFloatingAiState(),
);

watch([floatingAiOpen, floatingAiHidden, floatingAiBubbleEnabled], () => {
  void nextTick(clampFloatingAiPosition);
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
  if (isAiPage.value) {
    router.push({
      path: '/ai',
      query: { ...route.query, q: query, focus: String(Date.now()) },
    });
    return;
  }
  const postId = extractPostIdFromSearchInput(query);
  if (postId) {
    router.push(`/posts/${postId}`);
    return;
  }
  router.push({ path: '/search', query: { q: query } });
}

function extractPostIdFromSearchInput(value: string) {
  // 鍏佽鐢ㄦ埛绮樿创绔欏唴甯栧瓙鍒嗕韩閾炬帴锛屼紭鍏堢簿纭烦杞埌甯栧瓙锛岄伩鍏嶆妸 URL 褰撴櫘閫氬叧閿瘝鎼滅储銆?
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
  const isGuest = authStore.user?.role === 'GUEST';
  const allowedGuestPaths = ['/', '/square', '/spaces', '/resources'];
  const isAllowed =
    allowedGuestPaths.includes(path) ||
    (path.startsWith('/posts/') && path !== '/posts/new') ||
    path.startsWith('/spaces/');

  if (isGuest && !isAllowed) {
    message.warning('该功能需要登录后使用，请先登录');
    router.push('/login');
    return;
  }
  router.push(path);
}

function isTopNavActive(link: { path: string; active?: boolean; back?: boolean; section?: string }) {
  if (link.back) return false;
  if (isAiPage.value) return link.section === currentAiSection.value;
  return route.path.startsWith(link.path);
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

function parseFloatingAiPosition(value: unknown): FloatingAiPosition | null {
  if (!value || typeof value !== 'object') return null;
  const position = value as Partial<FloatingAiPosition>;
  const x = Number(position.x);
  const y = Number(position.y);
  return Number.isFinite(x) && Number.isFinite(y) ? { x, y } : null;
}

function getFloatingAiBounds(width: number, height: number) {
  const margin = window.innerWidth <= 720 ? 14 : 24;
  return {
    minX: margin,
    minY: margin,
    maxX: Math.max(margin, window.innerWidth - width - margin),
    maxY: Math.max(margin, window.innerHeight - height - margin),
  };
}

function clampFloatingAiPoint(
  point: FloatingAiPosition,
  width?: number,
  height?: number,
): FloatingAiPosition {
  const rect = floatingAiRef.value?.getBoundingClientRect();
  const currentWidth = width ?? rect?.width ?? (floatingAiOpen.value ? 390 : 214);
  const currentHeight = height ?? rect?.height ?? (floatingAiOpen.value ? 560 : 202);
  const bounds = getFloatingAiBounds(currentWidth, currentHeight);
  return {
    x: Math.min(Math.max(point.x, bounds.minX), bounds.maxX),
    y: Math.min(Math.max(point.y, bounds.minY), bounds.maxY),
  };
}

function clampFloatingAiPosition() {
  if (!floatingAiPosition.value || floatingAiHidden.value) return;
  const nextPosition = clampFloatingAiPoint(floatingAiPosition.value);
  if (
    nextPosition.x !== floatingAiPosition.value.x ||
    nextPosition.y !== floatingAiPosition.value.y
  ) {
    floatingAiPosition.value = nextPosition;
    saveFloatingAiState();
  }
}

// 鏈哄櫒浜烘嫋鎷戒娇鐢ㄨ鍙ｅ潗鏍囨寔涔呭寲锛涜秴杩囬槇鍊兼墠鍒ゅ畾涓烘嫋鎷斤紝閬垮厤杞荤偣鎵撳紑鏃惰璇垽鎴愮Щ鍔ㄣ€?
function startFloatingAiDrag(event: PointerEvent) {
  if (floatingAiHidden.value || event.button !== 0) return;
  const target = event.target as HTMLElement | null;
  if (floatingAiOpen.value && target?.closest('button, textarea, input, a')) return;

  const rect = floatingAiRef.value?.getBoundingClientRect();
  if (!rect) return;

  floatingAiContextVisible.value = false;
  floatingAiSettingsVisible.value = false;
  floatingAiDragState.value = {
    pointerId: event.pointerId,
    startClientX: event.clientX,
    startClientY: event.clientY,
    startX: rect.left,
    startY: rect.top,
    moved: false,
  };
  floatingAiDragging.value = true;
  floatingAiPosition.value = clampFloatingAiPoint(
    { x: rect.left, y: rect.top },
    rect.width,
    rect.height,
  );
  const dragTarget = event.currentTarget as HTMLElement;
  if (dragTarget.setPointerCapture) {
    dragTarget.setPointerCapture(event.pointerId);
  }
}

function moveFloatingAiDrag(event: PointerEvent) {
  const drag = floatingAiDragState.value;
  if (!drag || drag.pointerId !== event.pointerId) return;

  const deltaX = event.clientX - drag.startClientX;
  const deltaY = event.clientY - drag.startClientY;
  const moved = Math.hypot(deltaX, deltaY) > FLOATING_AI_DRAG_THRESHOLD;
  drag.moved = drag.moved || moved;
  if (!drag.moved) return;

  event.preventDefault();
  floatingAiPosition.value = clampFloatingAiPoint({
    x: drag.startX + deltaX,
    y: drag.startY + deltaY,
  });
}

function endFloatingAiDrag(event: PointerEvent) {
  const drag = floatingAiDragState.value;
  if (!drag || drag.pointerId !== event.pointerId) return;

  floatingAiDragState.value = null;
  floatingAiDragging.value = false;
  const dragTarget = event.currentTarget as HTMLElement;
  if (dragTarget.hasPointerCapture?.(event.pointerId)) {
    dragTarget.releasePointerCapture(event.pointerId);
  }

  if (drag.moved) {
    floatingAiSuppressClick.value = true;
    window.setTimeout(() => {
      floatingAiSuppressClick.value = false;
    }, 120);
    saveFloatingAiState();
  }
}

function cancelFloatingAiDrag(event: PointerEvent) {
  const drag = floatingAiDragState.value;
  if (!drag || drag.pointerId !== event.pointerId) return;
  floatingAiDragState.value = null;
  floatingAiDragging.value = false;
}

function handleFloatingAiRobotClick(event: MouseEvent) {
  const target = event.target as HTMLElement | null;
  if (target?.closest('.floating-ai-context-menu, .floating-ai-settings')) return;
  if (floatingAiSuppressClick.value) {
    floatingAiSuppressClick.value = false;
    return;
  }
  openFloatingAi();
}

function showRobotSettingsMenu(event: MouseEvent) {
  event.preventDefault();
  if (floatingAiDragging.value) return;
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

async function copyFloatingAiMessage(content: string) {
  if (!content) return;
  if (await copyTextToClipboard(content)) {
    message.success('已复制');
  } else {
    message.warning('复制失败，请手动选中内容复制');
  }
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
        .filter(
          (item) =>
            (item.role === 'user' || item.role === 'assistant') && typeof item.content === 'string',
        )
        .slice(-24);
    }
    floatingAiQuestion.value = typeof state.draft === 'string' ? state.draft : '';
    floatingAiOpen.value = Boolean(state.open);
    floatingAiHidden.value =
      state.version === FLOATING_AI_STATE_VERSION ? Boolean(state.hidden) : false;
    floatingAiBubbleEnabled.value =
      state.version === FLOATING_AI_STATE_VERSION ? state.bubble !== false : true;
    floatingAiFloatEnabled.value =
      state.version === FLOATING_AI_STATE_VERSION ? state.float !== false : true;
    floatingAiPosition.value = parseFloatingAiPosition(state.position);
    void nextTick(clampFloatingAiPosition);
  } catch {
    floatingAiMessages.value = [defaultFloatingAiMessage];
    floatingAiHidden.value = false;
    floatingAiBubbleEnabled.value = true;
    floatingAiFloatEnabled.value = true;
    floatingAiPosition.value = null;
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
    position: floatingAiPosition.value,
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
    <nav class="apple-topbar" :class="{ 'is-scrolled': headerScrolled }">
      <div class="apple-topbar-inner">
        <button class="brand-lockup" @click="navigate('/square')">
          <span class="brand-mark" :class="{ ai: isAiPage }">
            <n-icon v-if="isAiPage" size="19">
              <SparklesOutline />
            </n-icon>
            <img v-else src="@/assets/images/logo.png" alt="青云阁" class="w-full h-full rounded-[12px] object-cover" />
          </span>
          <span class="brand-copy">
            <strong>青云阁</strong>
            <small>{{ brandSubtitle }}</small>
          </span>
        </button>

        <div class="apple-nav-links">
          <button
            v-for="link in displayedNavLinks"
            :key="link.path"
            :class="{ active: isTopNavActive(link), 'return-link': link.back }"
            @click="navigate(link.path)"
          >
            <n-icon size="17">
              <component :is="link.icon" />
            </n-icon>
            <span>{{ link.name }}</span>
          </button>
        </div>

        <div class="apple-actions">
          <label class="apple-search">
            <n-icon size="17"><SearchOutline /></n-icon>
            <input
              v-model="searchKeyword"
              type="text"
              :placeholder="searchPlaceholder"
              @keyup.enter="handleSearch"
            />
          </label>
          <button
            v-if="!isAiPage"
            class="publish-top-btn apple-publish"
            @click="navigate(primaryActionPath)"
          >
            <n-icon size="17">
              <AddOutline />
            </n-icon>
            <span>{{ primaryActionLabel }}</span>
          </button>
          <button class="apple-icon-btn" @click="navigate('/messages')">
            <n-icon size="22">
              <ChatbubblesOutline />
            </n-icon>
            <n-badge v-if="msgUnread > 0" :value="msgUnread" :max="99" class="apple-badge" />
          </button>
          <button class="apple-icon-btn" @click="navigate('/notifications')">
            <n-icon size="22">
              <NotificationsOutline />
            </n-icon>
            <n-badge v-if="notifUnread > 0" :value="notifUnread" :max="99" class="apple-badge" />
          </button>
          <ThemeToggle class="apple-theme" />
          <n-dropdown
            v-if="authStore.user"
            :options="userDropdownOptions"
            @select="handleDropdownSelect"
          >
            <div class="apple-user">
              <img
                :src="
                  authStore.user.avatarUrl ||
                  'https://api.dicebear.com/7.x/initials/svg?seed=' + authStore.user.nickname
                "
                alt="Avatar"
              />
              <span>{{ authStore.user.nickname }}</span>
              <n-icon size="15">
                <ChevronDownOutline />
              </n-icon>
            </div>
          </n-dropdown>
        </div>
      </div>
    </nav>

    <div class="content-wrapper pt-16">
      <main class="page-content">
        <router-view v-slot="{ Component }">
          <transition name="fade" mode="out-in">
            <component :is="Component" />
          </transition>
        </router-view>
      </main>
    </div>

    <div
      v-if="authStore.isLoggedIn && !isAiPage"
      ref="floatingAiRef"
      class="floating-ai"
      :class="{ open: floatingAiOpen, hidden: floatingAiHidden, dragging: floatingAiDragging }"
      :style="floatingAiPositionStyle"
    >
      <button
        v-if="floatingAiHidden"
        class="floating-ai-restore"
        title="显示 AI 助手"
        @click="restoreFloatingAiRobot"
      />

      <section v-else-if="floatingAiOpen" class="floating-ai-panel">
        <header
          class="floating-ai-head"
          @pointerdown="startFloatingAiDrag"
          @pointermove="moveFloatingAiDrag"
          @pointerup="endFloatingAiDrag"
          @pointercancel="cancelFloatingAiDrag"
        >
          <div>
            <span>RAG Assistant</span>
            <strong>站内智能问答</strong>
            <small v-if="floatingAiLastQuestion">最近：{{ floatingAiLastQuestion }}</small>
          </div>
          <div class="floating-actions">
            <button title="清空当前对话" :disabled="!floatingAiHasHistory" @click="clearFloatingAi">
              <n-icon size="16">
                <TrashOutline />
              </n-icon>
            </button>
            <button title="打开完整 AI 工作台" @click="openAiWorkspace">
              <n-icon size="16">
                <ExpandOutline />
              </n-icon>
            </button>
            <button title="关闭对话窗口" @click="closeFloatingAi">
              <n-icon size="16">
                <ChevronDownOutline />
              </n-icon>
            </button>
          </div>
        </header>

        <div ref="floatingAiStreamRef" class="floating-ai-stream">
          <article
            v-for="(item, index) in floatingAiMessages"
            :key="`${item.role}-${index}`"
            class="floating-message"
            :class="item.role"
          >
            <div class="floating-message-content">
              {{ item.content }}
            </div>
            <button
              v-if="item.role === 'assistant' && item.content"
              class="floating-message-copy"
              title="复制回答"
              @click="copyFloatingAiMessage(item.content)"
            >
              <n-icon size="13">
                <CopyOutline />
              </n-icon>
              <span>复制</span>
            </button>
            <div v-if="item.citations?.length" class="floating-citations">
              <button
                v-for="citation in item.citations"
                :key="`${citation.type}-${citation.id}`"
                @click="openCitation(citation)"
              >
                {{ citation.title }}
              </button>
            </div>
          </article>

          <div v-if="floatingAiLoading" class="floating-loading">
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
            <n-icon size="17">
              <SendOutline />
            </n-icon>
          </button>
        </div>
      </section>

      <div
        v-else
        class="floating-ai-robot-shell"
        :class="{ dragging: floatingAiDragging }"
        @pointerdown="startFloatingAiDrag"
        @pointermove="moveFloatingAiDrag"
        @pointerup="endFloatingAiDrag"
        @pointercancel="cancelFloatingAiDrag"
        @click="handleFloatingAiRobotClick"
        @contextmenu="showRobotSettingsMenu"
      >
        <div v-if="floatingAiBubbleEnabled" class="floating-ai-bubble" aria-hidden="true">
          <div class="bubble-track">
            <span v-for="item in robotBubbleLoopItems" :key="item.key">
              {{ item.text }}
            </span>
          </div>
        </div>
        <button
          type="button"
          class="floating-ai-robot"
          :class="{ floating: floatingAiFloatEnabled }"
          title="打开 AI 问答"
        >
          <span class="robot-illustration" aria-hidden="true">
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
          @click.stop
          @pointerdown.stop
        >
          <button title="设置机器人" @click="openRobotSettings">
            <n-icon size="16">
              <SettingsOutline />
            </n-icon>
            <span>设置</span>
          </button>
        </div>

        <section
          v-if="floatingAiSettingsVisible"
          class="floating-ai-settings"
          @click.stop
          @pointerdown.stop
        >
          <header>
            <strong>机器人设置</strong>
            <button title="关闭设置" @click="closeRobotSettings">×</button>
          </header>
          <label>
            <input v-model="floatingAiBubbleEnabled" type="checkbox" />
            <span>显示气泡提示</span>
          </label>
          <label>
            <input v-model="floatingAiFloatEnabled" type="checkbox" />
            <span>上下浮动</span>
          </label>
          <button class="settings-link-btn" @click="openAiWorkspace">打开 AI 工作台</button>
          <button class="settings-danger-btn" @click="hideFloatingAiRobot">隐藏</button>
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
  box-shadow:
    0 16px 34px color-mix(in srgb, var(--cf-primary) 28%, transparent),
    0 1px 0 rgba(255, 255, 255, 0.54) inset;
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
  transition:
    transform 0.24s var(--cf-motion-ease),
    box-shadow 0.24s var(--cf-motion-ease),
    color 0.22s ease,
    background 0.22s ease,
    border-color 0.22s ease;
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
    linear-gradient(
      135deg,
      color-mix(in srgb, var(--cf-primary) 20%, transparent),
      color-mix(in srgb, var(--cf-secondary) 10%, transparent)
    ),
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
  box-shadow:
    0 20px 70px color-mix(in srgb, var(--cf-text-primary) 9%, transparent),
    0 -1px 0 color-mix(in srgb, #ffffff 36%, transparent) inset;
  transition:
    background 0.26s ease,
    backdrop-filter 0.26s ease,
    border-color 0.26s ease,
    box-shadow 0.26s ease;
}

.top-header.is-scrolled {
  background:
    linear-gradient(
      180deg,
      color-mix(in srgb, var(--cf-surface-highlight) 72%, transparent),
      transparent 82%
    ),
    color-mix(in srgb, var(--cf-bg-base) 88%, transparent);
  backdrop-filter: blur(18px) saturate(160%);
  -webkit-backdrop-filter: blur(18px) saturate(160%);
  border-bottom-color: color-mix(in srgb, var(--cf-border-strong) 42%, var(--cf-border-glass));
  box-shadow:
    0 18px 54px color-mix(in srgb, var(--cf-text-primary) 18%, transparent),
    0 -1px 0 color-mix(in srgb, #ffffff 28%, transparent) inset;
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
  transition:
    transform 0.24s var(--cf-motion-ease),
    box-shadow 0.24s var(--cf-motion-ease),
    color 0.22s ease,
    background 0.22s ease,
    border-color 0.22s ease;
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
  transition:
    transform 0.24s var(--cf-motion-ease),
    box-shadow 0.24s var(--cf-motion-ease),
    background 0.22s ease;
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
  transition:
    transform 0.24s var(--cf-motion-ease),
    box-shadow 0.24s var(--cf-motion-ease),
    border-color 0.22s ease;
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

.apple-topbar {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  z-index: 50;
  padding: 0 32px;
  pointer-events: none;
  background: color-mix(in srgb, var(--cf-bg-card) 96%, transparent);
  border-bottom: 1px solid color-mix(in srgb, var(--cf-border) 72%, transparent);
  box-shadow: 0 8px 28px color-mix(in srgb, var(--cf-text-primary) 7%, transparent);
  backdrop-filter: blur(18px) saturate(150%);
  -webkit-backdrop-filter: blur(18px) saturate(150%);
}

.apple-topbar-inner {
  width: 100%;
  max-width: none;
  min-height: 82px;
  margin: 0 auto;
  padding: 0;
  border: 0;
  border-radius: 0;
  background: transparent;
  box-shadow: none;
  backdrop-filter: none;
  -webkit-backdrop-filter: none;
  display: flex;
  align-items: center;
  gap: 22px;
  pointer-events: auto;
}

.brand-lockup,
.apple-nav-links button,
.apple-icon-btn {
  border: 0;
  background: transparent;
  cursor: pointer;
}

.brand-lockup {
  display: inline-flex;
  align-items: center;
  gap: 12px;
  min-width: 222px;
  padding: 0;
  color: var(--cf-text-primary);
}

.brand-mark {
  width: 40px;
  height: 40px;
  border-radius: 12px;
  display: grid;
  place-items: center;
  color: white;
  background: linear-gradient(
    145deg,
    var(--cf-primary),
    color-mix(in srgb, var(--cf-primary) 72%, #38bdf8)
  );
  box-shadow: 0 16px 34px color-mix(in srgb, var(--cf-primary) 28%, transparent);
}

.brand-mark.ai {
  border-radius: 14px;
  background:
    radial-gradient(circle at 32% 30%, #ffffff 0 8%, transparent 9%),
    linear-gradient(145deg, #00d8bf, #0ea5a1);
}

.brand-copy {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 3px;
}

.brand-copy strong {
  font-size: 18px;
  line-height: 1;
  letter-spacing: 0;
}

.brand-copy small {
  color: var(--cf-text-muted);
  font-size: 11px;
  line-height: 1;
}

.apple-nav-links {
  flex: 1;
  min-width: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: clamp(24px, 3vw, 46px);
  padding: 0 12px;
}

.apple-nav-links button {
  height: 82px;
  position: relative;
  display: inline-flex;
  align-items: center;
  gap: 7px;
  color: var(--cf-text-secondary);
  font-size: 15px;
  font-weight: 750;
  white-space: nowrap;
  transition:
    color 0.2s ease,
    transform 0.2s ease;
}

.apple-nav-links button::after {
  content: '';
  position: absolute;
  left: 8px;
  right: 8px;
  bottom: 0;
  height: 3px;
  border-radius: 999px;
  background: var(--cf-primary);
  transform: scaleX(0);
  transition: transform 0.2s ease;
}

.apple-nav-links button:hover,
.apple-nav-links button.active {
  color: var(--cf-primary);
}

.apple-nav-links button.active::after {
  transform: scaleX(1);
}

.apple-nav-links :deep(.n-icon) {
  display: none;
}

.apple-nav-links button.return-link {
  gap: 6px;
}

.apple-nav-links button.return-link :deep(.n-icon) {
  display: inline-flex;
}

.apple-actions {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 12px;
  min-width: 0;
}

.apple-search {
  width: 284px;
  height: 40px;
  padding: 0 16px;
  border: 1px solid var(--cf-border);
  border-radius: 999px;
  background: color-mix(in srgb, var(--cf-bg-card) 76%, transparent);
  box-shadow:
    inset 0 1px 0 color-mix(in srgb, #ffffff 74%, transparent),
    0 12px 28px color-mix(in srgb, var(--cf-text-primary) 6%, transparent);
  display: flex;
  align-items: center;
  gap: 9px;
  color: var(--cf-text-muted);
}

.apple-search input {
  width: 100%;
  min-width: 0;
  border: 0;
  outline: 0;
  background: transparent;
  color: var(--cf-text-primary);
  font-size: 14px;
}

.apple-publish {
  height: 40px;
  padding: 0 17px;
  border-radius: 999px;
}

.apple-icon-btn {
  position: relative;
  width: 38px;
  height: 38px;
  border-radius: 50%;
  color: var(--cf-text-secondary);
  display: grid;
  place-items: center;
  transition:
    color 0.2s ease,
    background 0.2s ease;
}

.apple-icon-btn:hover {
  color: var(--cf-primary);
  background: var(--cf-primary-soft);
}

.apple-badge {
  position: absolute;
  top: -3px;
  right: -4px;
}

.apple-theme {
  width: 38px;
  height: 38px;
  display: grid;
  place-items: center;
}

.apple-user {
  height: 42px;
  padding: 0 10px 0 4px;
  border-radius: 999px;
  display: inline-flex;
  align-items: center;
  gap: 8px;
  color: var(--cf-text-primary);
  font-size: 14px;
  font-weight: 750;
  cursor: pointer;
}

.apple-user img {
  width: 34px;
  height: 34px;
  border-radius: 50%;
  object-fit: cover;
  border: 1px solid var(--cf-border);
}

.floating-ai {
  position: fixed;
  right: 24px;
  bottom: calc(24px + env(safe-area-inset-bottom));
  z-index: 35;
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 12px;
  pointer-events: none;
  touch-action: none;
  user-select: none;
}

.floating-ai.hidden {
  right: 0;
  align-items: flex-end;
}

.floating-ai.dragging {
  transition: none;
}

.floating-ai-panel,
.floating-ai-robot-shell,
.floating-ai-restore {
  pointer-events: auto;
}

.floating-ai.open {
  bottom: calc(24px + env(safe-area-inset-bottom));
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
  cursor: move;
  touch-action: none;
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
  user-select: text;
  -webkit-user-select: text;
  cursor: text;
}

.floating-message.user .floating-message-content {
  color: var(--cf-text-inverse);
  background: var(--cf-primary);
  border-color: color-mix(in srgb, var(--cf-primary) 60%, transparent);
}

.floating-message-copy {
  align-self: flex-start;
  display: inline-flex;
  align-items: center;
  gap: 4px;
  margin-top: 4px;
  padding: 2px 8px;
  font-size: 11px;
  line-height: 1.4;
  color: var(--cf-text-secondary);
  background: transparent;
  border: 1px solid var(--cf-border);
  border-radius: 10px;
  cursor: pointer;
  transition:
    color 0.15s ease,
    border-color 0.15s ease,
    background 0.15s ease;
}

.floating-message-copy:hover {
  color: var(--cf-primary);
  border-color: var(--cf-primary);
  background: color-mix(in srgb, var(--cf-primary) 8%, transparent);
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
  cursor: grab;
  touch-action: none;
}

.floating-ai-robot-shell.dragging {
  cursor: grabbing;
}

.floating-ai-robot-shell.dragging .floating-ai-robot,
.floating-ai.dragging .floating-ai-robot {
  animation: none;
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
  cursor: inherit;
  touch-action: none;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  transition:
    filter 0.24s ease,
    transform 0.24s var(--cf-motion-ease);
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
  transition:
    transform 0.2s var(--cf-motion-ease),
    border-color 0.2s ease,
    color 0.2s ease;
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
  transition:
    opacity 0.2s ease,
    transform 0.2s var(--cf-motion-ease);
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
  transition:
    opacity 0.18s ease,
    transform 0.18s ease;
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
    bottom: calc(14px + env(safe-area-inset-bottom));
  }

  .floating-ai.open {
    right: 14px;
    bottom: calc(14px + env(safe-area-inset-bottom));
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
