<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import { NIcon, NSpin, useMessage } from 'naive-ui';
import {
  BookmarkOutline,
  ChatbubblesOutline,
  EllipsisHorizontalOutline,
  FlameOutline,
  HomeOutline,
  ChevronDownOutline,
  DocumentTextOutline,
  PinOutline,
  ShareSocialOutline,
  SparklesOutline,
  StarOutline,
  ThumbsUpOutline,
  TimeOutline,
} from '@vicons/ionicons5';
import { getPosts, toggleReaction } from '@/api/posts';
import { getPostAiCardsBatch, getPostAiCard } from '@/api/ai';
import type { PostAiCard } from '@/types/ai';
import MentionText from '@/components/MentionText.vue';
import BackToTopButton from '@/components/BackToTopButton.vue';
import PostAiCardLine from '@/components/PostAiCardLine.vue';
import SidebarLeft from '@/components/SidebarLeft.vue';
import SidebarRight from '@/components/SidebarRight.vue';
import OfficialAnnouncementCarousel from '@/components/OfficialAnnouncementCarousel.vue';
import { copyTextToClipboard } from '@/utils/clipboard';
import type { PostVO } from '@/types/post';
import type {
  AiRecommendationItem,
  HotArticle,
  HotTopicItem,
  OfficialAnnouncementItem,
} from '@/types/announce';
import {
  cacheHotArticleForFallback,
  getAiRecommendations,
  getDailyHotArticles,
  getHotTopics,
  getOfficialAnnouncements,
  reportHotArticleClick,
} from '@/api/announce';
import { useAuthStore } from '@/stores/auth';
import aiRobotUrl from '@/assets/images/ai_robot.png';
import campusHeroUrl from '@/assets/images/campus_hero_3d.png';
import heroWorkspaceUrl from '@/assets/images/hero_workspace.png';

const router = useRouter();
const message = useMessage();
const authStore = useAuthStore();

const SIDEBAR_COLLAPSED_KEY = 'campus:square:left-sidebar-collapsed';
const SIDEBAR_GUIDE_KEY = 'campus:square:left-sidebar-guide-dismissed';

const activeFilter = ref<'all' | 'qa' | 'share' | 'notice' | 'event'>('all');
const feedFilters = [
  { key: 'all', label: '推荐' },
  { key: 'qa', label: '问答' },
  { key: 'share', label: '分享' },
  { key: 'notice', label: '公告' },
  { key: 'event', label: '活动' },
] as const;

const sortOptions = [
  { key: 'latest', label: '探索广场', icon: HomeOutline },
  { key: 'trending', label: '热门讨论', icon: FlameOutline },
  { key: 'essence', label: '精选推荐', icon: StarOutline },
  { key: 'follow', label: '我的关注', icon: BookmarkOutline },
] as const;

const sortLabels: Record<(typeof sortOptions)[number]['key'], string> = {
  latest: '最新发布',
  trending: '热门讨论',
  essence: '精选推荐',
  follow: '我的关注',
};
const sortDropdownOptions = sortOptions.map((item) => ({
  key: item.key,
  label: sortLabels[item.key],
}));

const quickLinks = [
  { label: '我的帖子', icon: DocumentTextOutline, path: '/profile' },
  { label: '我的评论', icon: ChatbubblesOutline, path: '/profile' },
  { label: '我的收藏', icon: BookmarkOutline, path: '/profile' },
  { label: '浏览历史', icon: TimeOutline, path: '/profile' },
];

const guideItems: Array<[string, string]> = [
  ['最新发布', '快速追踪校园实时动态'],
  ['热门讨论', '查看评论互动最活跃的话题'],
  ['精选推荐', '聚焦优质内容与经验总结'],
  ['我的关注', '集中查看你关心的人与圈子'],
];

const officialAnnouncements: OfficialAnnouncementItem[] = [
  {
    id: 'campus-notice-guide',
    title: '欢迎来到青云阁',
    summary: '这是一个充满活力的校园社区，在这里分享知识、交流想法、结识伙伴，共同成长。',
    buttonText: '立即探索',
    badge: '官方公告',
    meta: 'Campus Notice',
    imageUrl: aiRobotUrl,
    link: '/square',
  },
  {
    id: 'campus-ai-guide',
    title: 'AI 助手已就绪',
    summary: '使用 AI 摘要、帖子分析和学习建议，让校园讨论更高效。',
    buttonText: '打开 AI 助手',
    badge: '智能推荐',
    meta: 'AI Assistant',
    imageUrl: aiRobotUrl,
    link: '/ai',
  },
  {
    id: 'campus-checkin-guide',
    title: '每日打卡提醒',
    summary: '完成学习打卡可累计积分，也可以加入同学们的挑战计划。',
    buttonText: '去打卡',
    badge: '习惯养成',
    meta: 'Check-in',
    imageUrl: heroWorkspaceUrl,
    link: '/checkin',
  },
];

const fallbackHotPosts: HotArticle[] = [
  { id: 'daily-review-pack', postId: 900001, title: '期末复习资料大合集', heatLabel: '1.2k 热度', source: 'mock', hasArticle: true },
  { id: 'campus-life-list', title: '校园生活的 100 件小事', heatLabel: '856 热度', source: 'mock', hasArticle: false },
  { id: 'study-club-balance', title: '如何平衡学习与社团？', heatLabel: '642 热度', source: 'mock', hasArticle: false },
  { id: 'postgraduate-sharing', title: '考研经验分享会', heatLabel: '589 热度', source: 'mock', hasArticle: false },
  { id: 'campus-reading-list', title: '大学必读的 10 本书', heatLabel: '478 热度', source: 'mock', hasArticle: false },
];

const fallbackAiRecommendations: AiRecommendationItem[] = [
  {
    id: 'ai-final-review',
    title: '如何高效准备期末考试?',
    categoryLabel: '学习方法',
    viewCount: 642,
    thumbnailUrl: campusHeroUrl,
    targetUrl: '/search?q=期末复习&type=POST',
  },
  {
    id: 'ai-reading-list',
    title: '大学必读的 10 本书',
    categoryLabel: '校园生活',
    viewCount: 478,
    thumbnailUrl: heroWorkspaceUrl,
    targetUrl: '/search?q=大学必读&type=POST',
  },
  {
    id: 'ai-python-practice',
    title: 'Python 入门到实践',
    categoryLabel: '编程学习',
    viewCount: 856,
    thumbnailUrl: aiRobotUrl,
    targetUrl: '/search?q=Python&type=POST',
  },
];

const fallbackHotTopics: HotTopicItem[] = [
  { topic: '期末复习', heat: 1287 },
  { topic: '校园生活', heat: 987 },
  { topic: '学习方法', heat: 756 },
  { topic: '考研经验', heat: 645 },
  { topic: '实习求职', heat: 543 },
];

const posts = ref<PostVO[]>([]);
const hotPosts = ref<HotArticle[]>(fallbackHotPosts);
const announcements = ref<OfficialAnnouncementItem[]>(officialAnnouncements);
const aiRecommendations = ref<AiRecommendationItem[]>(fallbackAiRecommendations);
const hotTopics = ref<HotTopicItem[]>(fallbackHotTopics);
const aiCards = ref<Record<string, PostAiCard>>({});
const loading = ref(false);
const hasMore = ref(true);
const sort = ref<'latest' | 'trending' | 'essence' | 'follow'>('latest');
const topicExpanded = ref(false);
const leftCollapsed = ref(false);
const sidebarGuideVisible = ref(false);
const rightSidebarOpen = ref(false);
const squareScrollRef = ref<HTMLElement | null>(null);
const visibleSet = ref(new Set<number>());
const requestedSet = ref(new Set<number>());
const requestingSet = ref(new Set<number>());
const aiQueueProcessing = ref(false);
const sortLabel = computed(() => sortLabels[sort.value] || '最新发布');
const visibleHotTopics = computed(() => (topicExpanded.value ? hotTopics.value : hotTopics.value.slice(0, 5)));

function authorLevel(post: PostVO) {
  const base = (post.likeCount || 0) + (post.commentCount || 0) * 2;
  return Math.min(9, Math.max(1, Math.floor(base / 20) + 1));
}

function postTags(post: PostVO) {
  const tags = [...(post.tags || []), ...(post.topics || [])];
  return [...new Set(tags)].slice(0, 3);
}

function postVisual(post: PostVO) {
  const haystack = `${post.title || ''} ${postTags(post).join(' ')} ${post.content}`;
  if (/python|编程|代码|开发|算法/i.test(haystack)) return aiRobotUrl;
  if (/资源|资料|书|模板|notion|复习/i.test(haystack)) return heroWorkspaceUrl;
  if (post.isEssence || post.isPinned) return campusHeroUrl;
  return post.id % 3 === 0 ? heroWorkspaceUrl : '';
}

function postVisualLabel(post: PostVO) {
  const title = (post.title || postTags(post)[0] || '青云阁').trim();
  return title.length > 6 ? `${title.slice(0, 6)}...` : title;
}

function formatViews(value: number) {
  if (value >= 1000) return `${(value / 1000).toFixed(value >= 10000 ? 0 : 1)}k`;
  return String(value);
}

async function loadAiCardsFor(postIds: number[]) {
  if (postIds.length === 0) return;
  try {
    const batch = await getPostAiCardsBatch(postIds);
    aiCards.value = { ...aiCards.value, ...batch };
    for (const idStr of Object.keys(batch)) requestedSet.value.add(Number(idStr));
  } catch {
    // AI card is a progressive enhancement; the feed remains usable without it.
  }
}

function handleAiCardVisible(postId: number) {
  visibleSet.value.add(postId);
  void processAiCardQueue();
}

function handleAiCardHidden(postId: number) {
  visibleSet.value.delete(postId);
}

async function processAiCardQueue() {
  if (aiQueueProcessing.value) return;
  const next = [...visibleSet.value].find(
    (id) =>
      !aiCards.value[String(id)] && !requestedSet.value.has(id) && !requestingSet.value.has(id),
  );
  if (next === undefined) return;

  aiQueueProcessing.value = true;
  requestingSet.value.add(next);
  try {
    const card = await getPostAiCard(next);
    if (card) aiCards.value = { ...aiCards.value, [String(next)]: card };
  } catch {
    // Avoid repeated LLM calls while users scroll.
  } finally {
    requestingSet.value.delete(next);
    requestedSet.value.add(next);
    aiQueueProcessing.value = false;
    if (visibleSet.value.size > 0) {
      setTimeout(() => void processAiCardQueue(), 200);
    }
  }
}

async function loadPosts(reset = false) {
  if (loading.value) return;
  loading.value = true;
  try {
    const lastPost = posts.value[posts.value.length - 1];
    const list = await getPosts({
      scope: 'SQUARE',
      sort: sort.value,
      cursor: reset ? undefined : sort.value === 'trending' ? lastPost?.commentCount : lastPost?.id,
      cursorId: reset ? undefined : lastPost?.id,
      limit: 10,
    });

    if (reset) {
      posts.value = list;
      aiCards.value = {};
      requestedSet.value.clear();
      visibleSet.value.clear();
    } else {
      posts.value.push(...list);
    }

    hasMore.value = sort.value === 'essence' ? false : list.length >= 10;
    void loadAiCardsFor(list.map((p) => p.id));
  } catch {
    if (reset) posts.value = [];
  } finally {
    loading.value = false;
  }
}

function switchSort(value: 'latest' | 'trending' | 'essence' | 'follow') {
  if (sort.value === value) return;
  sort.value = value;
  posts.value = [];
  hasMore.value = true;
  void loadPosts(true);
}

function handleSidebarSort(value: string) {
  if (value === 'latest' || value === 'trending' || value === 'essence' || value === 'follow') {
    switchSort(value);
  }
}

function handleSortSelect(value: string | number) {
  if (value === 'latest' || value === 'trending' || value === 'essence' || value === 'follow') {
    switchSort(value);
  }
}

function openRightSidebar() {
  rightSidebarOpen.value = true;
}

function closeRightSidebar() {
  rightSidebarOpen.value = false;
}

function isTabletViewport() {
  return typeof window !== 'undefined' && window.innerWidth >= 768 && window.innerWidth < 1440;
}

function isDesktopViewport() {
  return typeof window !== 'undefined' && window.innerWidth >= 1440;
}

function applySidebarPreference() {
  if (typeof window === 'undefined') return;

  if (isTabletViewport()) {
    leftCollapsed.value = true;
    sidebarGuideVisible.value = false;
    return;
  }

  if (isDesktopViewport()) {
    leftCollapsed.value = window.localStorage.getItem(SIDEBAR_COLLAPSED_KEY) === '1';
  } else {
    leftCollapsed.value = false;
  }
  sidebarGuideVisible.value = false;
}

function dismissSidebarGuide() {
  sidebarGuideVisible.value = false;
  if (typeof window !== 'undefined') {
    window.localStorage.setItem(SIDEBAR_GUIDE_KEY, '1');
  }
}

function toggleLeftSidebar() {
  leftCollapsed.value = !leftCollapsed.value;
  if (typeof window !== 'undefined') {
    window.localStorage.setItem(SIDEBAR_COLLAPSED_KEY, leftCollapsed.value ? '1' : '0');
  }
  if (sidebarGuideVisible.value) dismissSidebarGuide();
}

function openAiPage() {
  router.push('/ai');
  closeRightSidebar();
}

function showTrendingFromSidebar() {
  switchSort('trending');
  closeRightSidebar();
}

function handleTopicFromSidebar(topic: string) {
  openTopic(topic);
  closeRightSidebar();
}

function openAnnouncement(item: OfficialAnnouncementItem) {
  if (item.link) router.push(item.link);
}

async function loadDailyHotPosts() {
  try {
    const res = await getDailyHotArticles({ limit: fallbackHotPosts.length });
    if (res.items.length > 0) hotPosts.value = res.items;
  } catch {
    hotPosts.value = fallbackHotPosts;
  }
}

async function loadAnnouncementBlocks() {
  try {
    const [announcementRes, recommendationRes, topicRes] = await Promise.all([
      getOfficialAnnouncements({ placement: 'SQUARE', limit: officialAnnouncements.length }),
      getAiRecommendations({ limit: fallbackAiRecommendations.length }),
      getHotTopics({ limit: fallbackHotTopics.length }),
    ]);
    if (announcementRes.items.length > 0) announcements.value = announcementRes.items;
    if (recommendationRes.items.length > 0) aiRecommendations.value = recommendationRes.items;
    if (topicRes.items.length > 0) hotTopics.value = topicRes.items;
  } catch {
    announcements.value = officialAnnouncements;
    aiRecommendations.value = fallbackAiRecommendations;
    hotTopics.value = fallbackHotTopics;
  }
}

function handleTopicClick(topic: HotArticle) {
  const articleId = topic.articleId ?? topic.postId;
  const target = articleId && topic.hasArticle !== false ? { ...topic, postId: articleId } : createTestArticle(topic);
  cacheHotArticleForFallback(target);
  void reportHotArticleClick({
    articleId: topic.id,
    postId: target.postId,
    source: topic.source,
  }).catch(() => {});

  router.push(`/posts/${target.postId ?? target.id}`);
  closeRightSidebar();
}

function openHotPost(item: HotArticle) {
  handleTopicClick(item);
}

function createTestArticle(item: HotArticle): HotArticle {
  console.info('未检测到文章，正在为您自动创建测试数据...');
  const postId = Date.now();
  return {
    ...item,
    id: String(postId),
    postId,
    hasArticle: true,
    source: 'front-mock',
    summary:
      item.summary ||
      `这是一篇围绕「${item.title}」生成的前端测试文章，用于在真实热点文章接入前保持完整的阅读跳转体验。`,
    publishedAt: new Date().toISOString(),
  };
}

function openAiRecommendation(item: AiRecommendationItem) {
  router.push(item.targetUrl);
  closeRightSidebar();
}

async function handlePostReaction(post: PostVO, type: 'LIKE' | 'COLLECT') {
  if (authStore.user?.role === 'GUEST') {
    message.warning('请先登录以使用点赞或收藏功能');
    return;
  }
  const field = type === 'LIKE' ? 'liked' : 'collected';
  const countField = type === 'LIKE' ? 'likeCount' : 'viewCount';
  const previous = post[field];
  const previousCount = post[countField];
  post[field] = !previous;
  post[countField] = Math.max(0, previousCount + (post[field] ? 1 : -1));
  try {
    const enabled = await toggleReaction(post.id, type);
    post[field] = enabled;
    post[countField] = Math.max(0, previousCount + (enabled === previous ? 0 : enabled ? 1 : -1));
  } catch {
    post[field] = previous;
    post[countField] = previousCount;
    message.error('操作失败，请稍后重试');
  }
}

function goComments(id: number) {
  router.push({ path: `/posts/${id}`, hash: '#comments' });
}

function openPostMenu(post: PostVO) {
  message.info(post.title ? `已打开「${post.title}」的更多操作` : '已打开更多操作');
}

function openTopic(topic: string) {
  activeFilter.value = 'all';
  router.push({ path: '/search', query: { q: topic, type: 'POST' } });
}

function showAllTopics() {
  topicExpanded.value = !topicExpanded.value;
}

function goDetail(id: number) {
  router.push(`/posts/${id}`);
}

function goCreate() {
  if (authStore.user?.role === 'GUEST') {
    message.warning('请先登录以进行发布');
    return;
  }
  router.push('/posts/new');
}

function handleGoCheckin() {
  if (authStore.user?.role === 'GUEST') {
    message.warning('请先登录以参与每日打卡');
    return;
  }
  router.push('/checkin');
}

function handleQuickLink(path: string) {
  if (authStore.user?.role === 'GUEST') {
    message.warning('请先登录以查看您的个人空间');
    return;
  }
  router.push(path);
}

function postLink(id: number) {
  if (typeof window === 'undefined') return `/posts/${id}`;
  return `${window.location.origin}/posts/${id}`;
}

async function copyPostLink(id: number) {
  const url = postLink(id);
  if (await copyTextToClipboard(url)) {
    message.success('帖子链接已复制');
  } else {
    message.warning(`复制失败，请手动复制：${url}`);
  }
}

function openAiSummary(id: number) {
  if (authStore.user?.role === 'GUEST') {
    message.warning('请先登录以使用 AI 摘要功能');
    return;
  }
  router.push({
    path: '/ai',
    query: { mode: 'summary', postId: String(id) },
    hash: '#ai-workspace',
  });
}

function postPreview(content: string) {
  const normalized = content.replace(/\s+/g, ' ').trim();
  return normalized.length > 180 ? `${normalized.slice(0, 180)}...` : normalized;
}

function formatTime(value: string) {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return '刚刚';
  return date.toLocaleString('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  });
}

function scrollToListEnd(e: Event) {
  const target = e.target as HTMLElement;
  if (target.scrollHeight - target.scrollTop - target.clientHeight < 160) {
    void loadPosts();
  }
}

onMounted(() => {
  applySidebarPreference();
  window.addEventListener('resize', applySidebarPreference);
  void loadAnnouncementBlocks();
  void loadDailyHotPosts();
  void loadPosts(true);
});

onUnmounted(() => {
  window.removeEventListener('resize', applySidebarPreference);
});
</script>

<template>
  <div
    ref="squareScrollRef"
    class="square-page mx-auto grid h-[calc(100vh-86px)] min-h-[720px] w-full max-w-full grid-cols-1 gap-4 overflow-y-auto py-4 text-[color:var(--cf-text-primary)] md:gap-7 md:py-5 min-[1440px]:gap-9"
    :class="
      leftCollapsed
        ? 'md:grid-cols-[56px_minmax(0,1fr)] min-[1440px]:grid-cols-[56px_minmax(0,1fr)_360px]'
        : 'md:grid-cols-[220px_minmax(0,1fr)] min-[1440px]:grid-cols-[220px_minmax(0,1fr)_360px]'
    "
    @scroll="scrollToListEnd"
  >
    <SidebarLeft
      class="hidden md:flex"
      :sort-options="sortOptions"
      :sort="sort"
      :quick-links="quickLinks"
      :collapsed="leftCollapsed"
      :show-guide="sidebarGuideVisible"
      @switch-sort="handleSidebarSort"
      @quick-link="handleQuickLink"
      @checkin="handleGoCheckin"
      @toggle-collapse="toggleLeftSidebar"
      @dismiss-guide="dismissSidebarGuide"
    />

    <main class="feed-column">
      <section class="apple-card filter-bar">
        <button class="right-drawer-trigger md:inline-flex min-[1440px]:hidden" @click="openRightSidebar">
          辅助栏
        </button>
        <div class="filter-tabs">
          <button
            v-for="f in feedFilters"
            :key="f.key"
            :class="{ active: activeFilter === f.key }"
            @click="activeFilter = f.key"
          >
            {{ f.label }}
          </button>
        </div>
        <div class="sort-dropdown">
          <button class="sort-pill" type="button" aria-haspopup="listbox">
            {{ sortLabel }}
            <n-icon class="sort-chevron" size="16">
              <ChevronDownOutline />
            </n-icon>
          </button>
          <div class="sort-menu" role="listbox" aria-label="帖子排序">
            <button
              v-for="item in sortDropdownOptions"
              :key="item.key"
              class="sort-option"
              :class="{ active: sort === item.key }"
              type="button"
              role="option"
              :aria-selected="sort === item.key"
              @click="handleSortSelect(item.key)"
            >
              <span>{{ item.label }}</span>
            </button>
          </div>
        </div>
      </section>

      <OfficialAnnouncementCarousel
        :items="announcements"
        class="official-announcement-strip"
        @open="openAnnouncement"
      />

      <section v-if="posts.length === 0 && !loading" class="apple-card empty-card">
        <div class="empty-copy">
          <span class="notice-pill">
            <n-icon size="14"><SparklesOutline /></n-icon>
            官方公告
          </span>
          <h2>欢迎来到青云阁</h2>
          <p>这是一个充满活力的校园社区，在这里分享知识、交流想法、结识伙伴，共同成长。</p>
          <button class="apple-btn-primary" @click="goCreate">立即探索</button>
        </div>
        <div class="empty-illustration" aria-hidden="true">
          <svg viewBox="0 0 260 190" role="img">
            <defs>
              <linearGradient id="panelGradient" x1="0" x2="1" y1="0" y2="1">
                <stop offset="0" stop-color="#EFFFFB" />
                <stop offset="1" stop-color="#34D0BC" />
              </linearGradient>
              <linearGradient id="robotGradient" x1="0" x2="1" y1="0" y2="1">
                <stop offset="0" stop-color="#FFFFFF" />
                <stop offset="1" stop-color="#BCEEF6" />
              </linearGradient>
            </defs>
            <rect x="88" y="30" width="86" height="100" rx="16" fill="url(#panelGradient)" opacity=".72" transform="rotate(-10 131 80)" />
            <rect x="62" y="70" width="42" height="48" rx="12" fill="#DFF8FF" opacity=".72" transform="rotate(-8 83 94)" />
            <circle cx="52" cy="58" r="8" fill="#34D0BC" opacity=".38" />
            <circle cx="206" cy="46" r="13" fill="#DDF8F4" />
            <circle cx="197" cy="150" r="15" fill="#34D0BC" opacity=".58" />
            <ellipse cx="157" cy="160" rx="58" ry="12" fill="#34D0BC" opacity=".14" />
            <g transform="translate(126 48)">
              <rect x="18" y="0" width="70" height="58" rx="25" fill="url(#robotGradient)" />
              <rect x="31" y="20" width="44" height="24" rx="12" fill="#102636" />
              <circle cx="45" cy="32" r="4" fill="#34D0BC" />
              <circle cx="62" cy="32" r="4" fill="#34D0BC" />
              <rect x="35" y="61" width="38" height="50" rx="18" fill="url(#robotGradient)" />
              <circle cx="54" cy="84" r="5" fill="#34D0BC" />
              <path d="M26 72 C4 62 2 43 20 36" stroke="#BDEDF3" stroke-width="12" stroke-linecap="round" fill="none" />
              <path d="M80 72 C104 62 108 42 88 35" stroke="#BDEDF3" stroke-width="12" stroke-linecap="round" fill="none" />
              <rect x="38" y="107" width="12" height="28" rx="6" fill="#AEE6EF" />
              <rect x="59" y="107" width="12" height="28" rx="6" fill="#AEE6EF" />
            </g>
          </svg>
        </div>
      </section>

      <article
        v-for="post in posts"
        :key="post.id"
        class="apple-card feed-article"
        @click="goDetail(post.id)"
      >
        <div class="post-tools">
          <span v-if="post.isPinned === 1" class="pin-badge">
            <n-icon size="18"><PinOutline /></n-icon>
          </span>
          <button title="AI 摘要" @click.stop="openAiSummary(post.id)">
            <n-icon size="19">
              <SparklesOutline />
            </n-icon>
          </button>
        </div>

        <header class="post-author">
          <div v-if="!post.author?.avatarUrl" class="avatar-fallback">
            {{ post.author?.nickname?.charAt(0)?.toUpperCase() || '匿' }}
          </div>
          <img v-else :src="post.author.avatarUrl" alt="Avatar" />
          <div>
            <div class="author-name">
              <strong>{{ post.author?.nickname || '匿名用户' }}</strong>
              <span class="level-pill">LV{{ authorLevel(post) }}</span>
              <span v-if="postTags(post)[0]" class="identity-pill">{{ postTags(post)[0] }}</span>
            </div>
            <p>· {{ formatTime(post.createdAt) }}</p>
          </div>
        </header>

        <div class="post-body-row">
          <div class="post-copy">
            <h2 v-if="post.title">
              {{ post.title }}
            </h2>

            <p class="post-preview">
              <MentionText :text="postPreview(post.content)" />
            </p>

            <div v-if="postTags(post).length" class="tag-row">
              <span v-for="tag in postTags(post)" :key="tag">{{ tag }}</span>
            </div>
          </div>
          <div v-if="postVisual(post)" class="post-thumb-shell" aria-hidden="true">
            <span>{{ postVisualLabel(post) }}</span>
          </div>
        </div>

        <PostAiCardLine
          :post-id="post.id"
          :card="aiCards[String(post.id)]"
          :post="post"
          class="ai-line"
          @visible="handleAiCardVisible"
          @hidden="handleAiCardHidden"
        />

        <footer class="post-actions">
          <button :class="{ active: post.liked }" @click.stop="handlePostReaction(post, 'LIKE')">
            <n-icon size="20">
              <ThumbsUpOutline />
            </n-icon>
            {{ post.likeCount }}
          </button>
          <button @click.stop="goComments(post.id)">
            <n-icon size="20">
              <ChatbubblesOutline />
            </n-icon>
            {{ post.commentCount }}
          </button>
          <button
            :class="{ active: post.collected }"
            @click.stop="handlePostReaction(post, 'COLLECT')"
          >
            <n-icon size="20">
              <StarOutline />
            </n-icon>
            {{ post.viewCount }}
          </button>
          <button title="复制链接" @click.stop="copyPostLink(post.id)">
            <n-icon size="20">
              <ShareSocialOutline />
            </n-icon>
          </button>
          <button class="more" @click.stop="openPostMenu(post)">
            <n-icon size="20">
              <EllipsisHorizontalOutline />
            </n-icon>
          </button>
        </footer>
      </article>

      <div v-if="loading" class="loading-wrap">
        <n-spin size="large" />
      </div>
      <p v-if="!hasMore && posts.length > 0" class="end-text">
        已经到底了，去发布你的第一条观点吧。
      </p>

      <div class="mobile-sidebar-stack md:hidden">
        <SidebarLeft
          :sort-options="sortOptions"
          :sort="sort"
          :quick-links="quickLinks"
          mobile
          :show-mobile-nav="false"
          @switch-sort="handleSidebarSort"
          @quick-link="handleQuickLink"
          @checkin="handleGoCheckin"
        />
        <SidebarRight
          compact
          :guide-items="guideItems"
          :ai-recommendations="aiRecommendations"
          :hot-posts="hotPosts"
          :visible-hot-topics="visibleHotTopics"
          :topic-expanded="topicExpanded"
          @open-ai="openAiPage"
          @show-trending="showTrendingFromSidebar"
          @checkin="handleGoCheckin"
          @toggle-topics="showAllTopics"
          @open-topic="handleTopicFromSidebar"
          @open-hot-post="openHotPost"
          @open-ai-recommendation="openAiRecommendation"
        />
      </div>
    </main>

    <SidebarRight
      class="hidden min-[1440px]:flex"
      :guide-items="guideItems"
      :ai-recommendations="aiRecommendations"
      :hot-posts="hotPosts"
      :visible-hot-topics="visibleHotTopics"
      :topic-expanded="topicExpanded"
      @open-ai="openAiPage"
      @show-trending="showTrendingFromSidebar"
      @checkin="handleGoCheckin"
      @toggle-topics="showAllTopics"
      @open-topic="handleTopicFromSidebar"
      @open-hot-post="openHotPost"
      @open-ai-recommendation="openAiRecommendation"
    />

    <Teleport to="body">
      <div v-if="rightSidebarOpen" class="right-sidebar-modal" @click.self="closeRightSidebar">
        <section class="right-sidebar-sheet apple-card">
          <header>
            <h2>辅助边栏</h2>
            <button @click="closeRightSidebar">关闭</button>
          </header>
          <SidebarRight
            compact
            :guide-items="guideItems"
            :ai-recommendations="aiRecommendations"
            :hot-posts="hotPosts"
            :visible-hot-topics="visibleHotTopics"
            :topic-expanded="topicExpanded"
            @open-ai="openAiPage"
            @show-trending="showTrendingFromSidebar"
            @checkin="handleGoCheckin"
            @toggle-topics="showAllTopics"
            @open-topic="handleTopicFromSidebar"
            @open-hot-post="openHotPost"
            @open-ai-recommendation="openAiRecommendation"
          />
        </section>
      </div>
    </Teleport>

    <nav class="mobile-bottom-tabs md:hidden" aria-label="广场分类">
      <button
        v-for="item in sortOptions"
        :key="item.key"
        :class="{ active: sort === item.key }"
        @click="handleSidebarSort(item.key)"
      >
        <n-icon size="20">
          <component :is="item.icon" />
        </n-icon>
        <span>{{ item.label }}</span>
      </button>
    </nav>

    <BackToTopButton :target="squareScrollRef" />
  </div>
</template>

<style scoped>
.square-page {
  background: transparent;
  scrollbar-width: none;
  overflow-x: hidden;
}

.square-page::-webkit-scrollbar {
  display: none;
}

.feed-column {
  display: flex;
  flex-direction: column;
  gap: 14px;
  min-width: 0;
  padding: 0;
  border: 0;
  border-radius: 0;
  background: transparent;
  box-shadow: none;
  backdrop-filter: none;
  -webkit-backdrop-filter: none;
}

.apple-card {
  background: var(--cf-card-bg);
  border: 0;
  border-radius: 20px;
  box-shadow: var(--cf-card-shadow);
  backdrop-filter: blur(var(--cf-backdrop-blur));
  -webkit-backdrop-filter: blur(var(--cf-backdrop-blur));
}

.filter-bar {
  position: relative;
  z-index: 120;
  min-height: 46px;
  padding: 0;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
  background: transparent !important;
  border-radius: 0;
  box-shadow: none !important;
  backdrop-filter: none;
  -webkit-backdrop-filter: none;
}

.official-announcement-strip {
  position: relative;
  z-index: 1;
}

.right-drawer-trigger {
  height: 36px;
  align-items: center;
  justify-content: center;
  border: 0;
  border-radius: 9px;
  padding: 0 12px;
  color: var(--cf-primary);
  background: var(--cf-primary-soft);
  cursor: pointer;
  font-size: 13px;
  font-weight: 700;
  white-space: nowrap;
}

.filter-tabs {
  display: flex;
  gap: 12px;
  overflow-x: auto;
  scrollbar-width: none;
}

.filter-tabs::-webkit-scrollbar {
  display: none;
}

.filter-tabs button,
.sort-pill {
  height: 36px;
  border: 0;
  border-radius: 999px;
  background: transparent;
  color: var(--cf-text-secondary);
  padding: 0 clamp(14px, 1.5vw, 22px);
  font-weight: 800;
  white-space: nowrap;
  cursor: pointer;
}

.filter-tabs button.active {
  color: var(--cf-primary);
  background: var(--cf-primary-soft);
}

.filter-tabs button:hover,
.sort-pill:hover,
.right-drawer-trigger:hover {
  transform: translateY(-2px);
  box-shadow: var(--cf-shadow-card);
}

.sort-dropdown {
  position: relative;
  z-index: 130;
  flex: 0 0 auto;
}

.sort-pill {
  position: relative;
  z-index: 1;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  min-width: 112px;
  justify-content: center;
  background: rgba(255, 255, 255, 0.66);
  box-shadow:
    0 0 0 1px rgba(255, 255, 255, 0.62),
    0 8px 24px rgba(15, 23, 42, 0.055);
  backdrop-filter: blur(16px) saturate(145%);
  -webkit-backdrop-filter: blur(16px) saturate(145%);
  transition-property: background-color, box-shadow, color, transform;
}

.sort-dropdown:hover .sort-pill,
.sort-dropdown:focus-within .sort-pill {
  color: var(--cf-text-primary);
  background: rgba(255, 255, 255, 0.82);
}

.sort-chevron {
  color: var(--cf-text-muted);
  transition: transform 0.24s ease-out;
}

.sort-dropdown:hover .sort-chevron,
.sort-dropdown:focus-within .sort-chevron {
  transform: rotate(180deg);
}

.sort-menu {
  position: absolute;
  z-index: 131;
  top: calc(100% + 8px);
  right: 0;
  width: 144px;
  padding: 6px;
  border-radius: 18px;
  background:
    linear-gradient(135deg, rgba(255, 255, 255, 0.82), rgba(255, 255, 255, 0.54)),
    var(--cf-card-bg);
  box-shadow:
    0 0 0 1px rgba(255, 255, 255, 0.78),
    0 10px 30px rgba(15, 23, 42, 0.08);
  backdrop-filter: blur(22px) saturate(155%);
  -webkit-backdrop-filter: blur(22px) saturate(155%);
  opacity: 0;
  visibility: hidden;
  transform: translateY(8px);
  transition-property: opacity, transform, visibility;
  transition-duration: 0.22s;
  transition-timing-function: ease-out;
}

.sort-dropdown:hover .sort-menu,
.sort-dropdown:focus-within .sort-menu {
  opacity: 1;
  visibility: visible;
  transform: translateY(0);
}

.sort-option {
  width: 100%;
  min-height: 36px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  border: 0;
  border-radius: 12px;
  padding: 0 12px;
  background: transparent;
  color: var(--cf-text-secondary);
  cursor: pointer;
  font: inherit;
  font-size: 13px;
  font-weight: 650;
  text-align: left;
  transition-property: background-color, color;
}

.sort-option::after {
  content: '';
  width: 12px;
  height: 7px;
  border-left: 2px solid currentColor;
  border-bottom: 2px solid currentColor;
  opacity: 0;
  transform: translateY(-1px) rotate(-45deg);
}

.sort-option:hover,
.sort-option:focus-visible {
  color: var(--cf-text-primary);
  background: rgba(255, 255, 255, 0.54);
  outline: none;
}

.sort-option.active {
  color: var(--cf-primary);
  background: var(--cf-primary-soft);
}

.sort-option.active::after {
  opacity: 1;
}

html[data-theme='dark'] .sort-pill {
  background: rgba(22, 28, 38, 0.72);
  box-shadow:
    0 0 0 1px rgba(255, 255, 255, 0.08),
    0 10px 28px rgba(0, 0, 0, 0.28);
}

html[data-theme='dark'] .sort-menu {
  background:
    linear-gradient(135deg, rgba(30, 37, 50, 0.82), rgba(22, 28, 38, 0.62)),
    var(--cf-card-bg);
  box-shadow:
    0 0 0 1px rgba(255, 255, 255, 0.1),
    0 12px 32px rgba(0, 0, 0, 0.32);
}

html[data-theme='dark'] .sort-option:hover,
html[data-theme='dark'] .sort-option:focus-visible {
  background: rgba(255, 255, 255, 0.07);
}

.empty-card {
  position: relative;
  isolation: isolate;
  min-height: 260px;
  display: grid;
  grid-template-columns: minmax(0, 1fr) 320px;
  align-items: center;
  gap: 20px;
  padding: clamp(22px, 3vw, 36px);
  overflow: hidden;
  text-align: left;
  color: var(--cf-text-muted);
  background-color: #f7fafc;
  background-image:
    radial-gradient(at 0% 0%, rgba(167, 243, 208, 0.42) 0, transparent 52%),
    radial-gradient(at 100% 100%, rgba(52, 211, 153, 0.16) 0, transparent 54%),
    radial-gradient(at 80% 0%, rgba(209, 250, 229, 0.34) 0, transparent 42%),
    linear-gradient(135deg, rgba(255, 255, 255, 0.82), rgba(236, 253, 245, 0.7));
}

.empty-card::before,
.empty-card::after {
  content: '';
  position: absolute;
  inset: -28%;
  z-index: -1;
  pointer-events: none;
}

.empty-card::before {
  background-image:
    radial-gradient(circle at 18% 28%, rgba(52, 211, 153, 0.2), transparent 28%),
    radial-gradient(circle at 76% 22%, rgba(14, 165, 233, 0.14), transparent 30%),
    radial-gradient(circle at 62% 86%, rgba(167, 243, 208, 0.24), transparent 34%);
  filter: blur(18px);
  opacity: 0.9;
  animation: emptyMeshDrift 14s ease-in-out infinite alternate;
}

.empty-card::after {
  inset: 0;
  z-index: 0;
  background:
    linear-gradient(120deg, rgba(255, 255, 255, 0.62), rgba(255, 255, 255, 0.18) 42%, transparent 72%),
    radial-gradient(circle at 52% 46%, rgba(255, 255, 255, 0.58), transparent 34%);
  opacity: 0.72;
  mix-blend-mode: screen;
}

.empty-copy {
  position: relative;
  z-index: 1;
  max-width: 420px;
}

.notice-pill {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 5px 10px;
  border-radius: 9px;
  color: var(--cf-primary);
  background: var(--cf-primary-soft);
  font-size: 12px;
  font-weight: 600;
}

.empty-card h2 {
  margin: 18px 0 8px;
  color: var(--cf-text-primary);
  font-size: clamp(22px, 2.2vw, 26px);
  font-weight: 700;
  line-height: 1.25;
}

.empty-card p {
  margin: 0 0 20px;
  max-width: 390px;
  color: var(--cf-text-secondary);
}

.empty-card button {
  height: 42px;
  border: 0;
  border-radius: 9px;
  padding: 0 24px;
  background: var(--cf-primary-soft);
  color: var(--cf-primary);
  font-weight: 700;
  cursor: pointer;
  box-shadow: var(--cf-shadow-card);
}

.empty-card button:hover {
  background: color-mix(in srgb, var(--cf-primary) 18%, transparent);
  box-shadow: var(--cf-shadow-card-hover);
}

.empty-illustration {
  position: relative;
  z-index: 1;
  justify-self: end;
  width: min(100%, 260px);
}

.empty-illustration svg {
  width: 100%;
  height: auto;
  display: block;
}

html[data-theme='dark'] .empty-card {
  background-color: rgba(9, 20, 24, 0.88);
  background-image:
    radial-gradient(at 0% 0%, rgba(45, 212, 191, 0.22) 0, transparent 52%),
    radial-gradient(at 100% 100%, rgba(16, 185, 129, 0.16) 0, transparent 54%),
    radial-gradient(at 78% 0%, rgba(34, 211, 238, 0.12) 0, transparent 44%),
    linear-gradient(135deg, rgba(15, 23, 42, 0.84), rgba(6, 78, 59, 0.42));
}

html[data-theme='dark'] .empty-card::after {
  opacity: 0.28;
}

@keyframes emptyMeshDrift {
  from {
    opacity: 0.78;
    transform: translate3d(-1.8%, -1.2%, 0) scale(1);
  }

  to {
    opacity: 1;
    transform: translate3d(2.4%, 1.8%, 0) scale(1.06);
  }
}

@media (prefers-reduced-motion: reduce) {
  .empty-card::before {
    animation: none;
  }
}

.feed-article {
  position: relative;
  padding: 18px 20px;
  cursor: pointer;
  border: 1px solid rgba(26, 29, 36, 0.04);
}

.feed-article:hover {
  transform: translateY(-2px);
  box-shadow: var(--cf-shadow-card-hover);
}

.post-tools {
  position: absolute;
  top: 16px;
  right: 16px;
  display: flex;
  gap: 8px;
}

.post-tools button,
.pin-badge {
  width: 32px;
  height: 32px;
  border: 0;
  border-radius: 10px;
  color: var(--cf-primary);
  background: var(--cf-primary-soft);
  display: grid;
  place-items: center;
  cursor: pointer;
}

.post-tools button:hover {
  transform: translateY(-2px) scale(1.02);
}

.post-author {
  padding-right: 82px;
  display: flex;
  align-items: center;
  gap: 12px;
}

.post-author img,
.avatar-fallback {
  width: 44px;
  height: 44px;
  border-radius: 50%;
  object-fit: cover;
}

.avatar-fallback {
  display: grid;
  place-items: center;
  color: var(--cf-primary);
  background: var(--cf-primary-soft);
  font-weight: 800;
}

.author-name {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.author-name strong {
  color: var(--cf-text-primary);
  font-size: 15px;
  font-weight: 600;
}

.level-pill,
.identity-pill {
  padding: 2px 7px;
  border-radius: 999px;
  background: var(--cf-primary-soft);
  color: var(--cf-primary);
  font-size: 10px;
  font-weight: 800;
}

.identity-pill {
  font-size: 12px;
  font-weight: 700;
}

.post-author p {
  margin: 4px 0 0;
  color: var(--cf-text-muted);
  font-size: 12px;
}

.post-body-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 132px;
  gap: 18px;
  align-items: center;
  margin-top: 14px;
}

.post-copy {
  min-width: 0;
}

.feed-article h2 {
  margin: 0 0 12px;
  color: var(--cf-text-primary);
  font-family: var(--cf-font-heading);
  font-size: clamp(1.18rem, 1.55vw, 1.25rem);
  font-weight: 600;
  line-height: 1.35;
  letter-spacing: 0;
  text-wrap: balance;
}

.tag-row {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 14px;
}

.tag-row span {
  padding: 5px 10px;
  border-radius: 9px;
  background: var(--cf-primary-soft);
  color: var(--cf-primary);
  font-size: 12px;
  font-weight: 700;
}

.post-preview {
  margin: 0;
  color: var(--cf-text-secondary);
  font-size: 0.95rem;
  line-height: 1.7;
  text-wrap: pretty;
}

.post-thumb-shell {
  width: 132px;
  aspect-ratio: 1.25;
  flex: 0 0 auto;
  display: grid;
  place-items: center;
  overflow: hidden;
  border-radius: 18px;
  background: #f5f5f7;
  box-shadow: inset 0 0 0 1px rgba(0, 0, 0, 0.05);
}

.post-thumb-shell span {
  max-inline-size: 82%;
  color: #6e6e73;
  font-size: 13px;
  font-weight: 600;
  line-height: 1.35;
  text-align: center;
}

.ai-line {
  margin-top: 14px;
}

.post-actions {
  margin-top: 16px;
  display: flex;
  align-items: center;
  gap: 16px;
}

.post-actions button {
  min-height: 34px;
  border: 0;
  border-radius: 10px;
  background: transparent;
  color: var(--cf-text-muted);
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 4px 8px;
  cursor: pointer;
}

.post-actions button:hover,
.post-actions button.active {
  color: var(--cf-primary);
  background: var(--cf-primary-soft);
  transform: translateY(-2px);
}

.post-actions .more {
  margin-left: auto;
}

.loading-wrap {
  display: flex;
  justify-content: center;
  padding: 28px;
}

.end-text {
  margin: 0;
  padding: 18px;
  text-align: center;
  color: var(--cf-text-muted);
  font-size: 13px;
}

.mobile-sidebar-stack {
  display: flex;
  flex-direction: column;
  gap: 12px;
  min-width: 0;
  padding-top: 2px;
}

.mobile-bottom-tabs {
  position: fixed;
  right: 12px;
  bottom: calc(10px + env(safe-area-inset-bottom));
  left: 12px;
  z-index: 80;
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 6px;
  padding: 8px;
  border-radius: 20px;
  background: var(--cf-card-bg);
  box-shadow: var(--cf-shadow-card-hover);
  backdrop-filter: blur(var(--cf-backdrop-blur));
  -webkit-backdrop-filter: blur(var(--cf-backdrop-blur));
}

.mobile-bottom-tabs button {
  min-width: 0;
  min-height: 48px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 3px;
  border: 0;
  border-radius: 14px;
  color: var(--cf-text-muted);
  background: transparent;
  cursor: pointer;
  font-size: 11px;
  font-weight: 600;
  line-height: 1.2;
  transition: all 0.2s ease-out;
}

.mobile-bottom-tabs button span {
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.mobile-bottom-tabs button.active,
.mobile-bottom-tabs button:hover {
  color: var(--cf-primary);
  background: var(--cf-primary-soft);
}

@media (min-width: 768px) {
  .mobile-sidebar-stack {
    display: none;
  }

  .mobile-bottom-tabs {
    display: none;
  }
}

.right-sidebar-modal {
  position: fixed;
  inset: 0;
  z-index: 3000;
  display: flex;
  justify-content: flex-end;
  padding: clamp(12px, 3vw, 24px);
  background: rgba(26, 29, 36, 0.18);
  backdrop-filter: blur(8px);
  -webkit-backdrop-filter: blur(8px);
}

.right-sidebar-sheet {
  width: min(420px, 100%);
  max-height: calc(100vh - clamp(24px, 6vw, 48px));
  display: flex;
  flex-direction: column;
  gap: 16px;
  padding: clamp(16px, 2.8vw, 22px);
  overflow: hidden;
}

.right-sidebar-sheet > header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex: 0 0 auto;
}

.right-sidebar-sheet h2 {
  margin: 0;
  color: var(--cf-text-primary);
  font-size: 17px;
  font-weight: 600;
}

.right-sidebar-sheet > header button {
  height: 34px;
  border: 0;
  border-radius: 9px;
  padding: 0 12px;
  background: var(--cf-primary-soft);
  color: var(--cf-primary);
  cursor: pointer;
  font-size: 13px;
  font-weight: 700;
}

.right-sidebar-sheet :deep(.sidebar-right) {
  min-height: 0;
  max-height: none;
}

@media (max-width: 767px) {
  .square-page {
    height: auto;
    min-height: calc(100vh - 96px);
    padding: 12px 0 calc(96px + env(safe-area-inset-bottom));
    overflow-y: visible;
  }

  .filter-bar {
    align-items: stretch;
    flex-direction: column;
    gap: 10px;
    min-height: auto;
  }

  .filter-tabs {
    width: 100%;
  }

  .filter-tabs button,
  .sort-pill,
  .right-drawer-trigger {
    height: 34px;
    font-size: 12px;
  }

  .sort-pill {
    align-self: flex-start;
  }

  .post-author {
    padding-right: 0;
  }

  .post-tools {
    position: static;
    justify-content: flex-end;
    margin-bottom: 10px;
  }

  .feed-article h2 {
    margin-right: 0;
  }

  .feed-article {
    padding: 16px;
  }

  .post-actions {
    flex-wrap: wrap;
    gap: 10px;
  }

  .post-actions .more {
    margin-left: 0;
  }

  .empty-card {
    grid-template-columns: 1fr;
    padding: 22px;
  }

  .empty-illustration {
    justify-self: center;
  }

  .right-sidebar-modal {
    align-items: flex-end;
    justify-content: center;
    padding: 10px;
  }

  .right-sidebar-sheet {
    width: 100%;
    max-height: 86vh;
    border-radius: 20px;
  }
}

@media (max-width: 420px) {
  .feed-column {
    padding: 0;
  }

  .filter-tabs button,
  .sort-pill,
  .right-drawer-trigger {
    padding-inline: 10px;
  }
}
</style>
