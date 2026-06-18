<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import { NIcon, NSpin } from 'naive-ui';
import {
  BookmarkOutline,
  ChatbubblesOutline,
  FlameOutline,
  SparklesOutline,
  TimeOutline,
  DocumentTextOutline,
  HomeOutline,
  CalendarOutline,
  CashOutline,
  ChevronDownOutline,
  ChevronForwardOutline,
  StarOutline,
  BulbOutline,
  ThumbsUpOutline,
  ShareSocialOutline,
  EllipsisHorizontalOutline,
  PinOutline,
} from '@vicons/ionicons5';
import { getPosts, toggleReaction } from '@/api/posts';
import { getPostAiCardsBatch, getPostAiCard } from '@/api/ai';
import type { PostAiCard } from '@/types/ai';
import MentionText from '@/components/MentionText.vue';
import BackToTopButton from '@/components/BackToTopButton.vue';
import PostAiCardLine from '@/components/PostAiCardLine.vue';
import { copyTextToClipboard } from '@/utils/clipboard';
import type { PostVO } from '@/types/post';
import { useMessage } from 'naive-ui';
import { useAuthStore } from '@/stores/auth';

const router = useRouter();
const message = useMessage();
const authStore = useAuthStore();

const activeFilter = ref<'all' | 'follow' | 'qa' | 'share' | 'notice' | 'event'>('all');
const feedFilters = [
  { key: 'all', label: '全部' },
  { key: 'follow', label: '关注' },
  { key: 'qa', label: '问答' },
  { key: 'share', label: '分享' },
  { key: 'notice', label: '公告' },
  { key: 'event', label: '活动' },
] as const;

const sortOptions = [
  { key: 'latest', label: '最新发布', icon: HomeOutline },
  { key: 'trending', label: '热门讨论', icon: FlameOutline },
  { key: 'essence', label: '精选推荐', icon: StarOutline },
  { key: 'follow', label: '我的关注', icon: BookmarkOutline },
] as const;

const quickLinks = [
  { label: '我的帖子', icon: DocumentTextOutline, path: '/profile' },
  { label: '我的评论', icon: ChatbubblesOutline, path: '/profile' },
  { label: '我的收藏', icon: BookmarkOutline, path: '/profile' },
  { label: '浏览历史', icon: TimeOutline, path: '/profile' },
];

const guideItems = [
  ['最新发布', '快速追踪校园实时动态'],
  ['热门讨论', '查看评论互动最活跃的话题'],
  ['精选推荐', '聚焦优质内容与经验总结'],
  ['我的关注', '集中查看你关心的人与圈子'],
];

const hotTopics: Array<[string, number]> = [
  ['期末复习', 1287],
  ['校园生活', 987],
  ['学习方法', 756],
  ['考研经验', 645],
  ['实习求职', 543],
];

const myPoints = computed(() => authStore.user?.points ?? 0);
const posts = ref<PostVO[]>([]);
const aiCards = ref<Record<string, PostAiCard>>({});
const loading = ref(false);
const hasMore = ref(true);
const sort = ref<'latest' | 'trending' | 'essence' | 'follow'>('latest');
const topicExpanded = ref(false);
const squareScrollRef = ref<HTMLElement | null>(null);
const visibleSet = ref(new Set<number>());
const requestedSet = ref(new Set<number>());
const requestingSet = ref(new Set<number>());
const aiQueueProcessing = ref(false);
const sortLabel = computed(
  () => sortOptions.find((item) => item.key === sort.value)?.label || '最新',
);
const visibleHotTopics = computed(() => (topicExpanded.value ? hotTopics : hotTopics.slice(0, 5)));

function authorLevel(post: PostVO) {
  const base = (post.likeCount || 0) + (post.commentCount || 0) * 2;
  return Math.min(9, Math.max(1, Math.floor(base / 20) + 1));
}

function postTags(post: PostVO) {
  const tags = [...(post.tags || []), ...(post.topics || [])];
  return [...new Set(tags)].slice(0, 3);
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

function cycleSort() {
  const order = sortOptions.map((item) => item.key);
  const next = order[(order.indexOf(sort.value) + 1) % order.length];
  switchSort(next);
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

function handleGoPoints() {
  if (authStore.user?.role === 'GUEST') {
    message.warning('请先登录以查看积分明细');
    return;
  }
  router.push('/points');
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

onMounted(() => loadPosts(true));
</script>

<template>
  <div ref="squareScrollRef" class="square-page" @scroll="scrollToListEnd">
    <aside class="left-rail">
      <section class="apple-card nav-card">
        <button
          v-for="item in sortOptions"
          :key="item.key"
          class="rail-link"
          :class="{ active: sort === item.key }"
          @click="switchSort(item.key)"
        >
          <n-icon size="21">
            <component :is="item.icon" />
          </n-icon>
          <span>{{ item.label }}</span>
        </button>

        <div class="rail-divider" />
        <p class="rail-caption">我的空间</p>

        <button
          v-for="item in quickLinks"
          :key="item.label"
          class="rail-link compact"
          @click="handleQuickLink(item.path)"
        >
          <n-icon size="18">
            <component :is="item.icon" />
          </n-icon>
          <span>{{ item.label }}</span>
        </button>
      </section>

      <section class="apple-card stat-card check-card">
        <div>
          <h3>每日打卡</h3>
          <p>连续打卡 7 天可得额外奖励</p>
        </div>
        <div class="check-visual">
          <n-icon size="28">
            <CalendarOutline />
          </n-icon>
        </div>
        <div class="streak-row">
          <span>连续</span>
          <strong>3</strong>
          <span>天</span>
        </div>
        <button class="mini-primary" @click="handleGoCheckin">去打卡</button>
      </section>

      <section class="apple-card stat-card points-card">
        <h3>我的积分</h3>
        <strong>{{ myPoints }}</strong>
        <button @click="handleGoPoints">
          积分明细
          <n-icon size="14">
            <ChevronForwardOutline />
          </n-icon>
        </button>
        <n-icon class="coin-icon" size="58">
          <CashOutline />
        </n-icon>
      </section>
    </aside>

    <main class="feed-column">
      <section class="apple-card filter-bar">
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
        <button class="sort-pill" @click="cycleSort">
          {{ sortLabel }}
          <n-icon size="16">
            <ChevronDownOutline />
          </n-icon>
        </button>
      </section>

      <section v-if="posts.length === 0 && !loading" class="apple-card empty-card">
        <n-icon size="46">
          <ChatbubblesOutline />
        </n-icon>
        <h2>当前分类还没有内容</h2>
        <p>发一条动态，让校园广场热闹起来。</p>
        <button @click="goCreate">立即发帖</button>
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
              <span>LV{{ authorLevel(post) }}</span>
            </div>
            <p>{{ formatTime(post.createdAt) }} · 来自 Web</p>
          </div>
        </header>

        <h2 v-if="post.title">
          {{ post.title }}
        </h2>

        <div v-if="postTags(post).length" class="tag-row">
          <span v-for="tag in postTags(post)" :key="tag">{{ tag }}</span>
        </div>

        <p class="post-preview">
          <MentionText :text="postPreview(post.content)" />
        </p>

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
    </main>

    <aside class="right-rail">
      <section class="apple-card side-panel">
        <h3>
          <span class="soft-icon blue"
            ><n-icon size="18"><StarOutline /></n-icon
          ></span>
          浏览建议
        </h3>
        <ul>
          <li v-for="item in guideItems" :key="item[0]">
            <span />
            <p>
              <strong>{{ item[0] }}</strong
              >：{{ item[1] }}
            </p>
          </li>
        </ul>
      </section>

      <section class="apple-card side-panel prompt-panel">
        <h3>
          <span class="soft-icon yellow"
            ><n-icon size="18"><BulbOutline /></n-icon
          ></span>
          创作提示
        </h3>
        <p>清晰的标题、简洁的正文与恰当的话题标签，会显著提升内容曝光率与互动率。</p>
        <button @click="goCreate">现在发帖</button>
      </section>

      <section class="apple-card side-panel topics-panel">
        <div class="panel-title-row">
          <h3>
            <span class="soft-icon coral"
              ><n-icon size="18"><FlameOutline /></n-icon
            ></span>
            热门话题
          </h3>
          <button @click="showAllTopics">
            {{ topicExpanded ? '收起' : '查看全部' }}
            <n-icon size="12">
              <ChevronForwardOutline />
            </n-icon>
          </button>
        </div>

        <a v-for="[topic, num] in visibleHotTopics" :key="topic" @click="openTopic(topic)">
          <span
            ><n-icon size="15"><FlameOutline /></n-icon> # {{ topic }}</span
          >
          <strong>{{ num }}</strong>
        </a>
      </section>
    </aside>

    <BackToTopButton :target="squareScrollRef" />
  </div>
</template>

<style scoped>
.square-page {
  height: calc(100vh - 112px);
  min-height: 720px;
  overflow-y: auto;
  display: grid;
  grid-template-columns: 260px minmax(0, 1fr) 320px;
  gap: 28px;
  padding: 8px 0 40px;
  color: var(--cf-text-primary);
  background: var(--cf-page-bg);
  scrollbar-width: none;
}

.square-page::-webkit-scrollbar,
.left-rail::-webkit-scrollbar {
  display: none;
}

.left-rail,
.right-rail {
  position: sticky;
  top: 8px;
  align-self: start;
  display: flex;
  flex-direction: column;
  gap: 18px;
  max-height: calc(100vh - 132px);
  overflow-y: auto;
  scrollbar-width: none;
}

.feed-column {
  display: flex;
  flex-direction: column;
  gap: 18px;
  min-width: 0;
  padding: 18px;
  border: 1px solid var(--cf-column-border);
  border-radius: 22px;
  background: var(--cf-column-bg);
  box-shadow: var(--cf-column-shadow);
  backdrop-filter: blur(18px) saturate(135%);
  -webkit-backdrop-filter: blur(18px) saturate(135%);
}

.apple-card {
  background: var(--cf-card-bg);
  border: 1px solid var(--cf-card-border);
  border-radius: 20px;
  box-shadow: var(--cf-card-shadow);
  backdrop-filter: blur(24px) saturate(150%);
  -webkit-backdrop-filter: blur(24px) saturate(150%);
}

.nav-card {
  padding: 14px;
}

.rail-link {
  width: 100%;
  min-height: 48px;
  border: 0;
  border-radius: 13px;
  background: transparent;
  color: var(--cf-text-secondary);
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 0 14px;
  font-weight: 700;
  cursor: pointer;
  transition:
    background 0.2s ease,
    color 0.2s ease,
    transform 0.2s ease;
}

.rail-link:hover,
.rail-link.active {
  color: var(--cf-primary);
  background: rgba(0, 216, 191, 0.1);
}

.rail-link:hover {
  transform: translateX(2px);
}

.rail-link.compact {
  min-height: 40px;
  font-size: 14px;
  font-weight: 650;
}

.rail-divider {
  height: 1px;
  margin: 14px 2px 12px;
  background: var(--cf-border);
}

.rail-caption {
  margin: 0 0 8px;
  padding: 0 12px;
  color: var(--cf-text-muted);
  font-size: 12px;
}

.stat-card,
.side-panel {
  padding: 20px;
}

.stat-card h3,
.side-panel h3 {
  margin: 0;
  color: var(--cf-text-primary);
  font-size: 17px;
  line-height: 1.2;
}

.stat-card p {
  margin: 7px 0 0;
  color: var(--cf-text-muted);
  font-size: 13px;
}

.check-card {
  position: relative;
  overflow: hidden;
}

.check-visual {
  position: absolute;
  right: 20px;
  bottom: 24px;
  width: 58px;
  height: 58px;
  border-radius: 20px;
  display: grid;
  place-items: center;
  color: var(--cf-primary);
  background: linear-gradient(145deg, rgba(0, 216, 191, 0.18), rgba(56, 189, 248, 0.14));
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.9);
}

.streak-row {
  margin: 20px 0 14px;
  display: flex;
  align-items: flex-end;
  gap: 6px;
  color: var(--cf-text-primary);
  font-weight: 700;
}

.streak-row strong,
.points-card > strong {
  font-size: 32px;
  line-height: 0.9;
  letter-spacing: 0;
}

.mini-primary,
.prompt-panel button,
.empty-card button {
  border: 0;
  border-radius: 12px;
  background: var(--cf-primary);
  color: white;
  font-weight: 800;
  cursor: pointer;
  box-shadow: 0 14px 34px rgba(0, 216, 191, 0.24);
}

.mini-primary {
  height: 34px;
  padding: 0 18px;
}

.points-card {
  position: relative;
  overflow: hidden;
}

.points-card > strong {
  display: block;
  margin: 14px 0 10px;
}

.points-card button,
.panel-title-row button {
  border: 0;
  background: transparent;
  color: var(--cf-text-secondary);
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 0;
  cursor: pointer;
}

.coin-icon {
  position: absolute;
  right: 16px;
  bottom: 14px;
  color: #f5b52e;
  opacity: 0.82;
}

.filter-bar {
  min-height: 76px;
  padding: 0 2px 8px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
  background: transparent;
  border: 0;
  border-radius: 0;
  box-shadow: none;
  backdrop-filter: none;
  -webkit-backdrop-filter: none;
}

.filter-tabs {
  display: flex;
  gap: 8px;
  overflow-x: auto;
  scrollbar-width: none;
}

.filter-tabs::-webkit-scrollbar {
  display: none;
}

.filter-tabs button,
.sort-pill {
  height: 38px;
  border: 1px solid transparent;
  border-radius: 12px;
  background: transparent;
  color: var(--cf-text-secondary);
  padding: 0 18px;
  font-weight: 700;
  white-space: nowrap;
  cursor: pointer;
}

.filter-tabs button.active {
  color: var(--cf-primary);
  background: rgba(0, 216, 191, 0.1);
  border-color: rgba(0, 216, 191, 0.18);
}

.sort-pill {
  border-color: rgba(15, 23, 42, 0.08);
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.empty-card {
  min-height: 260px;
  display: grid;
  place-items: center;
  align-content: center;
  gap: 12px;
  text-align: center;
  color: var(--cf-text-muted);
}

.empty-card h2 {
  margin: 0;
  color: var(--cf-text-primary);
  font-size: 22px;
}

.empty-card p {
  margin: 0 0 8px;
}

.empty-card button {
  height: 42px;
  padding: 0 22px;
}

.feed-article {
  position: relative;
  padding: 22px 24px 18px;
  cursor: pointer;
  transition:
    transform 0.22s var(--cf-motion-ease),
    box-shadow 0.22s ease,
    border-color 0.22s ease;
}

.feed-article:hover {
  transform: translateY(-2px);
  border-color: rgba(0, 216, 191, 0.22);
  box-shadow:
    0 24px 70px rgba(15, 23, 42, 0.1),
    inset 0 1px 0 rgba(255, 255, 255, 0.86);
}

.post-tools {
  position: absolute;
  top: 20px;
  right: 20px;
  display: flex;
  gap: 8px;
}

.post-tools button,
.pin-badge {
  width: 32px;
  height: 32px;
  border: 0;
  border-radius: 11px;
  color: var(--cf-primary);
  background: rgba(0, 216, 191, 0.11);
  display: grid;
  place-items: center;
  cursor: pointer;
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
  background: linear-gradient(145deg, rgba(0, 216, 191, 0.16), rgba(56, 189, 248, 0.14));
  font-weight: 900;
}

.author-name {
  display: flex;
  align-items: center;
  gap: 8px;
}

.author-name strong {
  font-size: 15px;
}

.author-name span {
  padding: 2px 7px;
  border-radius: 999px;
  background: #16b981;
  color: white;
  font-size: 10px;
  font-weight: 900;
}

.post-author p {
  margin: 4px 0 0;
  color: var(--cf-text-muted);
  font-size: 12px;
}

.feed-article h2 {
  margin: 20px 82px 10px 0;
  font-size: 21px;
  line-height: 1.35;
  letter-spacing: 0;
}

.tag-row {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 12px;
}

.tag-row span {
  padding: 5px 10px;
  border-radius: 999px;
  background: rgba(0, 216, 191, 0.1);
  color: var(--cf-primary);
  font-size: 12px;
  font-weight: 800;
}

.post-preview {
  margin: 0;
  color: var(--cf-text-muted);
  font-size: 14px;
  line-height: 1.75;
}

.ai-line {
  margin-top: 14px;
}

.post-actions {
  margin-top: 16px;
  display: flex;
  align-items: center;
  gap: 24px;
}

.post-actions button {
  border: 0;
  background: transparent;
  color: var(--cf-text-muted);
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 4px;
  cursor: pointer;
  transition: color 0.2s ease;
}

.post-actions button:hover,
.post-actions button.active {
  color: var(--cf-primary);
}

.post-actions .more {
  margin-left: auto;
}

.side-panel {
  color: var(--cf-text-secondary);
}

.side-panel h3,
.panel-title-row {
  display: flex;
  align-items: center;
}

.side-panel h3 {
  gap: 10px;
  margin-bottom: 18px;
}

.soft-icon {
  width: 28px;
  height: 28px;
  border-radius: 10px;
  display: grid;
  place-items: center;
}

.soft-icon.blue {
  color: #38bdf8;
  background: rgba(56, 189, 248, 0.13);
}

.soft-icon.yellow {
  color: #f5a400;
  background: rgba(245, 164, 0, 0.13);
}

.soft-icon.coral {
  color: #ff5b5f;
  background: rgba(255, 91, 95, 0.12);
}

.side-panel ul {
  margin: 0;
  padding: 0;
  list-style: none;
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.side-panel li {
  display: flex;
  gap: 10px;
}

.side-panel li > span {
  width: 5px;
  height: 5px;
  margin-top: 8px;
  border-radius: 50%;
  background: var(--cf-text-muted);
}

.side-panel li p,
.prompt-panel p {
  margin: 0;
  font-size: 13px;
  line-height: 1.7;
}

.prompt-panel button {
  width: 100%;
  height: 46px;
  margin-top: 20px;
  font-size: 15px;
}

.panel-title-row {
  justify-content: space-between;
  margin-bottom: 14px;
}

.panel-title-row h3 {
  margin: 0;
}

.topics-panel a {
  min-height: 34px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  color: var(--cf-text-secondary);
  cursor: pointer;
}

.topics-panel a span {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  font-size: 13px;
  font-weight: 700;
}

.topics-panel a :deep(.n-icon) {
  color: #ff5b5f;
}

.topics-panel a strong {
  color: var(--cf-text-muted);
  font-size: 12px;
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

html[data-theme='dark'] .feed-article:hover {
  border-color: rgba(0, 245, 212, 0.32);
  box-shadow:
    0 24px 70px rgba(0, 0, 0, 0.48),
    inset 0 1px 0 rgba(255, 255, 255, 0.12);
}

@media (max-width: 1280px) {
  .square-page {
    grid-template-columns: 230px minmax(0, 1fr) 280px;
    gap: 20px;
  }
}

@media (max-width: 1080px) {
  .square-page {
    grid-template-columns: 220px minmax(0, 1fr);
  }

  .right-rail {
    display: none;
  }
}

@media (max-width: 760px) {
  .square-page {
    height: auto;
    min-height: calc(100vh - 96px);
    display: flex;
    flex-direction: column;
    padding: 0 0 28px;
  }

  .left-rail {
    position: static;
    max-height: none;
  }

  .nav-card {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .rail-divider,
  .rail-caption,
  .stat-card {
    display: none;
  }

  .filter-bar {
    align-items: stretch;
    flex-direction: column;
    min-height: auto;
  }

  .sort-pill {
    align-self: flex-start;
  }

  .feed-article {
    padding: 20px;
  }
}
</style>
