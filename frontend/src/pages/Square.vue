<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import { NEmpty, NIcon, NSpin, NTag } from 'naive-ui';
import {
  AddOutline,
  BookmarkOutline,
  ChatbubblesOutline,
  EyeOutline,
  FlameOutline,
  RibbonOutline,
  SettingsOutline,
  TimeOutline,
} from '@vicons/ionicons5';
import { getPosts } from '@/api/posts';
import MentionText from '@/components/MentionText.vue';
import type { PostVO } from '@/types/post';

const router = useRouter();
const posts = ref<PostVO[]>([]);
const loading = ref(false);
const hasMore = ref(true);
const sort = ref<'latest' | 'trending' | 'essence' | 'follow'>('latest');

const sortOptions = [
  { key: 'latest', label: '最新发布', icon: TimeOutline },
  { key: 'trending', label: '热门讨论', icon: FlameOutline },
  { key: 'essence', label: '精华推荐', icon: RibbonOutline },
  { key: 'follow', label: '我的关注', icon: BookmarkOutline },
] as const;

const isAdmin = computed(() => {
  const role = localStorage.getItem('role');
  return role === 'TENANT_ADMIN' || role === 'SUPER_ADMIN';
});

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
    } else {
      posts.value.push(...list);
    }

    hasMore.value = sort.value === 'essence' ? false : list.length >= 10;
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

function goAdmin() {
  router.push('/admin');
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
    class="square-page"
    @scroll="scrollToListEnd"
  >
    <section class="hero-card cf-surface">
      <div>
        <span class="cf-pill">Campus Square</span>
        <h2 class="cf-section-title">
          探索真实校园热度
        </h2>
        <p class="cf-section-subtitle">
          参考 Stitch 广场页的清爽信息流布局，保留当前接口与滚动加载逻辑，用更轻的卡片层次重塑浏览体验。
        </p>
      </div>
      <div class="hero-actions">
        <button
          class="cf-primary-btn"
          @click="goCreate"
        >
          <n-icon size="18">
            <AddOutline />
          </n-icon>
          发布帖子
        </button>
        <button
          v-if="isAdmin"
          class="cf-secondary-btn"
          @click="goAdmin"
        >
          <n-icon size="18">
            <SettingsOutline />
          </n-icon>
          管理后台
        </button>
      </div>
    </section>

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
}

.hero-card {
  display: flex;
  justify-content: space-between;
  align-items: flex-end;
  gap: 18px;
  padding: 24px;
}

.hero-actions {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
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
  margin: 18px 0 10px;
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

  .hero-card {
    flex-direction: column;
    align-items: flex-start;
  }

  .side-column {
    grid-template-columns: 1fr;
  }
}
</style>
