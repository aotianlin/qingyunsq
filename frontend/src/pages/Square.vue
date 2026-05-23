<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import { NEmpty, NIcon, NSpin, NTag } from 'naive-ui';
import {
  BookmarkOutline,
  ChatbubblesOutline,
  EyeOutline,
  FlameOutline,
  RibbonOutline,
  CopyOutline,
  SparklesOutline,
  TimeOutline,
} from '@vicons/ionicons5';
import { getPosts } from '@/api/posts';
import { getPostAiCardsBatch, getPostAiCard } from '@/api/ai';
import type { PostAiCard } from '@/types/ai';
import MentionText from '@/components/MentionText.vue';
import BackToTopButton from '@/components/BackToTopButton.vue';
import PostAiCardLine from '@/components/PostAiCardLine.vue';
import { copyTextToClipboard } from '@/utils/clipboard';
import type { PostVO } from '@/types/post';
import { useMessage } from 'naive-ui';

const router = useRouter();
const message = useMessage();
const posts = ref<PostVO[]>([]);
const aiCards = ref<Record<string, PostAiCard>>({});
const loading = ref(false);
const hasMore = ref(true);
const sort = ref<'latest' | 'trending' | 'essence' | 'follow'>('latest');
const squareScrollRef = ref<HTMLElement | null>(null);

async function loadAiCardsFor(postIds: number[]) {
  if (postIds.length === 0) return;
  try {
    const batch = await getPostAiCardsBatch(postIds);
    // 合并到 aiCards 而非替换，保证翻页时已有缓存不丢失
    aiCards.value = { ...aiCards.value, ...batch };
    // batch 返回中存在的 postId 标记为已请求过（避免 IntersectionObserver 再触发生成）
    for (const idStr of Object.keys(batch)) {
      requestedSet.value.add(Number(idStr));
    }
  } catch {
    // 静默：列表卡片是增强信息，失败时不显示即可
  }
}

// ===== AI 卡片懒加载请求队列 =====
// 用户滚动到帖子进入视口时：未缓存 + 未请求过 → 加入候选；单线程逐一调 LLM 生成。
// 滚动离开视口的未开始项自动跳过；滚动停止 → 没有新进入视口的项 → 队列自然停下。
const visibleSet = ref(new Set<number>());
const requestedSet = ref(new Set<number>());     // 已请求成功/失败过（不再重试）
const requestingSet = ref(new Set<number>());    // 正在请求中
const aiQueueProcessing = ref(false);

function handleAiCardVisible(postId: number) {
  visibleSet.value.add(postId);
  void processAiCardQueue();
}

function handleAiCardHidden(postId: number) {
  visibleSet.value.delete(postId);
}

async function processAiCardQueue() {
  if (aiQueueProcessing.value) return;
  // 从当前可见 + 未请求 + 不在请求中 的帖子里取一个
  const next = [...visibleSet.value].find(
    (id) => !aiCards.value[String(id)] && !requestedSet.value.has(id) && !requestingSet.value.has(id),
  );
  if (next === undefined) return;

  aiQueueProcessing.value = true;
  requestingSet.value.add(next);
  try {
    const card = await getPostAiCard(next); // 默认触发生成
    if (card) {
      aiCards.value = { ...aiCards.value, [String(next)]: card };
    }
  } catch {
    // 失败不重试，标记已请求避免反复打 LLM
  } finally {
    requestingSet.value.delete(next);
    requestedSet.value.add(next);
    aiQueueProcessing.value = false;
    // 200ms 节流，给后端喘息，并避免一瞬间触发太多
    if (visibleSet.value.size > 0) {
      setTimeout(() => void processAiCardQueue(), 200);
    }
  }
}

const sortOptions = [
  { key: 'latest', label: '最新发布', icon: TimeOutline },
  { key: 'trending', label: '热门讨论', icon: FlameOutline },
  { key: 'essence', label: '精华推荐', icon: RibbonOutline },
  { key: 'follow', label: '我的关注', icon: BookmarkOutline },
] as const;

async function loadPosts(reset = false) {
  if (loading.value) return;
  loading.value = true;
  try {
    const lastPost = posts.value[posts.value.length - 1];
    const list = await getPosts({
      scope: 'SQUARE',
      sort: sort.value,
      cursor: reset ? undefined : (sort.value === 'trending' ? lastPost?.commentCount : lastPost?.id),
      cursorId: reset ? undefined : lastPost?.id,
      limit: 10,
    });

    if (reset) {
      posts.value = list;
      aiCards.value = {}; // 切换 sort/refresh 时清空旧卡片缓存
      requestedSet.value.clear();
      visibleSet.value.clear();
    } else {
      posts.value.push(...list);
    }

    hasMore.value = sort.value === 'essence' ? false : list.length >= 10;

    // 批量拉取这一批新增帖子的 AI 卡片缓存
    void loadAiCardsFor(list.map((p) => p.id));
  } catch {
    if (reset) {
      posts.value = [];
    }
  } finally {
    loading.value = false;
  }
}

function switchSort(value: 'latest' | 'trending' | 'essence' | 'follow') {
  if (sort.value === value) return;
  sort.value = value;
  posts.value = [];
  hasMore.value = true;
  loadPosts(true);
}

function goDetail(id: number) {
  router.push(`/posts/${id}`);
}

function goCreate() {
  router.push('/posts/new');
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
  router.push({ path: '/ai', query: { mode: 'summary', postId: String(id) }, hash: '#ai-workspace' });
}

function stopCardClick(event: MouseEvent) {
  event.stopPropagation();
}

function postPreview(content: string) {
  const normalized = content.replace(/\s+/g, ' ').trim();
  return normalized.length > 180 ? `${normalized.slice(0, 180)}...` : normalized;
}

function formatTime(value: string) {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return '刚刚';
  return date.toLocaleString('zh-CN', { month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' });
}

function scrollToListEnd(e: Event) {
  const target = e.target as HTMLElement;
  if (target.scrollHeight - target.scrollTop - target.clientHeight < 160) {
    loadPosts();
  }
}

onMounted(() => loadPosts(true));
</script>

<template>
  <div
    ref="squareScrollRef"
    class="square-page"
    @scroll="scrollToListEnd"
  >
    <section class="toolbar-row">
      <div class="sort-bar cf-surface">
        <button
          v-for="item in sortOptions"
          :key="item.key"
          class="sort-chip"
          :class="{ active: sort === item.key }"
          @click="switchSort(item.key)"
        >
          <n-icon size="16">
            <component :is="item.icon" />
          </n-icon>
          <span>{{ item.label }}</span>
        </button>
      </div>
    </section>

    <section class="feed-grid">
      <div class="feed-column">
        <div
          v-if="posts.length === 0 && !loading"
          class="empty-wrap cf-surface"
        >
          <n-empty description="当前分类下还没有内容">
            <template #icon>
              <n-icon size="44">
                <ChatbubblesOutline />
              </n-icon>
            </template>
            <template #extra>
              <button
                class="cf-primary-btn"
                @click="goCreate"
              >
                立即发帖
              </button>
            </template>
          </n-empty>
        </div>

        <article
          v-for="post in posts"
          :key="post.id"
          class="post-card cf-card"
          @click="goDetail(post.id)"
        >
          <div class="post-header">
            <div class="author-row">
              <div class="avatar-badge">
                {{ post.author?.nickname?.charAt(0)?.toUpperCase() || '匿' }}
              </div>
              <div>
                <div class="author-name">
                  {{ post.author?.nickname || '匿名用户' }}
                </div>
                <div class="author-meta">
                  {{ formatTime(post.createdAt) }}
                </div>
              </div>
            </div>
            <n-tag
              v-if="post.isEssence === 1"
              round
              type="warning"
              size="small"
            >
              精华
            </n-tag>
          </div>

          <div class="card-actions">
            <button
              class="card-action-btn"
              title="复制链接"
              @click.stop="copyPostLink(post.id)"
            >
              <n-icon size="16">
                <CopyOutline />
              </n-icon>
            </button>
            <button
              class="card-action-btn ai"
              title="AI 摘要"
              @click.stop="openAiSummary(post.id)"
            >
              <n-icon size="16">
                <SparklesOutline />
              </n-icon>
            </button>
          </div>

          <h3
            v-if="post.title"
            class="post-title"
          >
            {{ post.title }}
          </h3>

          <div class="post-content">
            <MentionText :text="postPreview(post.content)" />
          </div>

          <div
            v-if="post.topics?.length"
            class="topic-row"
          >
            <span
              v-for="topic in post.topics"
              :key="topic"
              class="topic-tag"
            ># {{ topic }}</span>
          </div>

          <PostAiCardLine
            :post-id="post.id"
            :card="aiCards[String(post.id)]"
            :post="post"
            @visible="handleAiCardVisible"
            @hidden="handleAiCardHidden"
          />

          <div class="post-footer">
            <span>
              <n-icon size="16"><EyeOutline /></n-icon>
              {{ post.viewCount }}
            </span>
            <span>
              <n-icon size="16"><BookmarkOutline /></n-icon>
              {{ post.likeCount }}
            </span>
            <span>
              <n-icon size="16"><ChatbubblesOutline /></n-icon>
              {{ post.commentCount }}
            </span>
          </div>
        </article>

        <div
          v-if="loading"
          class="loading-wrap"
        >
          <n-spin size="large" />
        </div>

        <div
          v-if="!hasMore && posts.length > 0"
          class="end-tip"
        >
          已经到底了，去发布你的第一条观点吧。
        </div>
      </div>

      <aside class="side-column">
        <div class="side-panel cf-surface">
          <h3>浏览建议</h3>
          <ul>
            <li>最新发布：快速追踪校园实时动态</li>
            <li>热门讨论：查看评论互动最活跃的话题</li>
            <li>精华推荐：聚焦优质内容与经验总结</li>
            <li>我的关注：集中查看你关心的人与圈子</li>
          </ul>
        </div>
        <div class="side-panel cf-surface accent-panel">
          <h3>创作提示</h3>
          <p>清晰的标题、简洁的正文与恰当的话题标签，会显著提升你的内容曝光率与互动率。</p>
          <button
            class="cf-primary-btn side-btn"
            @click="goCreate"
          >
            现在发帖
          </button>
        </div>
      </aside>
    </section>

    <BackToTopButton :target="squareScrollRef" />
  </div>
</template>

<style scoped lang="scss">
.square-page {
  height: calc(100vh - var(--cf-header-height) - 48px);
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 18px;
  padding-right: 4px;
  perspective: 1200px;
  position: relative;
}

.back-to-top-btn {
  position: fixed;
  bottom: 32px;
  right: 32px;
  width: 48px;
  height: 48px;
  border-radius: 50%;
  border: 1px solid var(--cf-border);
  background: var(--cf-bg-elevated);
  color: var(--cf-text-secondary);
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.12);
  z-index: 100;
  transition: all 0.3s;

  &:hover {
    color: var(--cf-primary);
    border-color: var(--cf-primary);
    transform: translateY(-2px);
    box-shadow: 0 6px 20px rgba(0, 0, 0, 0.16);
  }
}

.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.3s;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}

.toolbar-row {
  display: flex;
}

.sort-bar {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  padding: 12px;
  width: 100%;
}

.sort-chip {
  border: none;
  background: transparent;
  color: var(--cf-text-secondary);
  border-radius: 12px;
  padding: 10px 14px;
  display: inline-flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  font-weight: 600;
  transition: all 0.2s ease;
}

.sort-chip:hover {
  background: var(--cf-bg-glass-soft);
  color: var(--cf-text-primary);
}

.sort-chip.active {
  background: linear-gradient(180deg, var(--cf-primary-soft), var(--cf-bg-glass-soft));
  border: 1px solid color-mix(in srgb, var(--cf-primary) 22%, transparent);
  box-shadow: inset 0 1px 0 var(--cf-surface-highlight);
  color: var(--cf-primary);
}

.feed-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 300px;
  gap: 18px;
  align-items: start;
}

.feed-column {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.post-card {
  padding: 22px;
  cursor: pointer;
  box-shadow: var(--cf-shadow-float);
  transform-style: preserve-3d;
  position: relative;
}

.post-card:hover {
  transform: translate3d(0, -6px, 0) rotateX(0.7deg);
}

.post-header {
  display: flex;
  justify-content: space-between;
  gap: 14px;
  align-items: flex-start;
}

.card-actions {
  position: absolute;
  top: 18px;
  right: 18px;
  display: flex;
  gap: 8px;
}

.card-action-btn {
  width: 34px;
  height: 34px;
  border-radius: 10px;
  border: 1px solid var(--cf-border);
  background: var(--cf-bg-glass-soft);
  color: var(--cf-text-secondary);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  box-shadow: inset 0 1px 0 var(--cf-surface-highlight);
  transition: transform 0.2s ease, background 0.2s ease, color 0.2s ease;
}

.card-action-btn:hover {
  transform: translateY(-1px);
  background: var(--cf-bg-glass);
  color: var(--cf-primary);
}

.card-action-btn.ai {
  color: var(--cf-primary);
}

.author-row {
  display: flex;
  gap: 12px;
  align-items: center;
}

.avatar-badge {
  width: 44px;
  height: 44px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--cf-primary-soft);
  color: var(--cf-primary);
  font-family: var(--cf-font-heading);
  font-weight: 700;
}

.author-name {
  font-weight: 700;
}

.author-meta {
  margin-top: 4px;
  color: var(--cf-text-muted);
  font-size: 13px;
}

.post-title {
  margin: 18px 80px 10px 0;
  font-family: var(--cf-font-heading);
  font-size: 24px;
  line-height: 1.3;
}

.post-content {
  color: var(--cf-text-secondary);
  line-height: 1.8;
}

.topic-row {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 16px;
}

.topic-tag {
  padding: 6px 12px;
  border-radius: 999px;
  background: var(--cf-bg-glass-soft);
  border: 1px solid var(--cf-border);
  color: var(--cf-primary);
  font-size: 13px;
  font-weight: 600;
}

.post-footer {
  display: flex;
  gap: 18px;
  align-items: center;
  margin-top: 18px;
  padding-top: 16px;
  border-top: 1px solid var(--cf-border);
  color: var(--cf-text-muted);

  span {
    display: inline-flex;
    align-items: center;
    gap: 6px;
  }
}

.side-column {
  display: flex;
  flex-direction: column;
  gap: 16px;
  position: sticky;
  top: 0;
}

.side-panel {
  padding: 20px;
  box-shadow: var(--cf-shadow-float);

  h3 {
    margin: 0 0 12px;
    font-family: var(--cf-font-heading);
    font-size: 22px;
  }

  p,
  li {
    color: var(--cf-text-secondary);
    line-height: 1.75;
  }

  ul {
    margin: 0;
    padding-left: 18px;
  }
}

.accent-panel {
  background: linear-gradient(180deg, var(--cf-primary-soft), var(--cf-bg-glass-soft));
  border-color: color-mix(in srgb, var(--cf-primary) 28%, var(--cf-border));
}

@media (prefers-reduced-motion: no-preference) {
  .post-card:nth-child(2n) {
    animation-delay: 0.04s;
  }

  .post-card:nth-child(3n) {
    animation-delay: 0.08s;
  }
}

.side-btn {
  margin-top: 12px;
  width: 100%;
}

.empty-wrap,
.loading-wrap,
.end-tip {
  display: flex;
  justify-content: center;
  align-items: center;
  padding: 28px;
}

.end-tip {
  color: var(--cf-text-muted);
  font-size: 14px;
}

@media (max-width: 1100px) {
  .feed-grid {
    grid-template-columns: 1fr;
  }

  .side-column {
    position: static;
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 768px) {
  .square-page {
    height: auto;
  }

  .side-column {
    grid-template-columns: 1fr;
  }
}
</style>
