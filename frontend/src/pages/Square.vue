<script setup lang="ts">
import { ref, onMounted, computed } from 'vue';
import { useRouter } from 'vue-router';
import { NCard, NButton, NTag, NSpace, NSpin, NEmpty } from 'naive-ui';
import { getPosts } from '@/api/posts';
import { renderMentions } from '@/utils/mention';
import type { PostVO } from '@/types/post';

const router = useRouter();
const posts = ref<PostVO[]>([]);
const loading = ref(false);
const hasMore = ref(true);
const sort = ref<'latest' | 'trending' | 'essence' | 'follow'>('latest');

const sortOptions = [
  { key: 'latest', label: '最新' },
  { key: 'follow', label: '关注' },
  { key: 'trending', label: '最热' },
  { key: 'essence', label: '精华' },
] as const;

async function loadPosts(reset = false) {
  if (loading.value) return;
  loading.value = true;
  try {
    const cursor = reset ? undefined : posts.value[posts.value.length - 1]?.id;
    const cursorVal = sort.value === 'trending'
      ? (reset ? undefined : posts.value[posts.value.length - 1]?.likeCount)
      : cursor;
    const list = await getPosts({ scope: 'SQUARE', sort: sort.value, cursor: cursorVal, limit: 10 });
    if (reset) {
      posts.value = list;
    } else {
      posts.value.push(...list);
    }
    hasMore.value = list.length >= 10;
  } catch {
    // ignore
  }
  loading.value = false;
}

function switchSort(s: 'latest' | 'trending' | 'essence' | 'follow') {
  sort.value = s;
  posts.value = [];
  loadPosts(true);
}

function goDetail(id: number) {
  router.push(`/posts/${id}`);
}

const isAdmin = computed(() => {
  const role = localStorage.getItem('role');
  return role === 'TENANT_ADMIN' || role === 'SUPER_ADMIN';
});

function goCreate() {
  router.push('/posts/new');
}

function goAdmin() {
  router.push('/admin');
}

function scrollToListEnd(e: Event) {
  const target = e.target as HTMLElement;
  if (target.scrollHeight - target.scrollTop - target.clientHeight < 100) {
    loadPosts();
  }
}

onMounted(() => loadPosts(true));
</script>

<template>
  <div class="square" @scroll="scrollToListEnd">
    <div class="square-header">
      <h2>全校广场</h2>
      <NSpace>
        <NButton size="small" @click="router.push('/search')">搜索</NButton>
        <NButton size="small" @click="router.push('/messages')">私信</NButton>
        <NButton size="small" @click="router.push('/notifications')">通知</NButton>
        <NButton size="small" @click="router.push('/points')">积分</NButton>
        <NButton size="small" @click="router.push('/ai')">AI</NButton>
        <NButton v-if="isAdmin" secondary type="warning" size="small" @click="goAdmin">
          管理后台
        </NButton>
        <NButton type="primary" @click="goCreate">发帖</NButton>
      </NSpace>
    </div>

    <NSpace class="sort-bar">
      <NButton
        v-for="opt in sortOptions"
        :key="opt.key"
        :type="sort === opt.key ? 'primary' : 'default'"
        size="small"
        @click="switchSort(opt.key)"
      >
        {{ opt.label }}
      </NButton>
    </NSpace>

    <div v-if="posts.length === 0 && !loading" class="empty">
      <NEmpty description="暂无帖子，快去发帖吧" />
    </div>

    <div v-for="post in posts" :key="post.id" class="post-card" @click="goDetail(post.id)">
      <NCard>
        <div class="card-header">
          <div class="author-info">
            <span class="author-name">{{ post.author?.nickname || '匿名' }}</span>
            <span class="post-time">{{ new Date(post.createdAt).toLocaleDateString() }}</span>
          </div>
          <NTag v-if="post.isEssence === 1" type="warning" size="small">精华</NTag>
        </div>
        <h3 v-if="post.title" class="post-title">{{ post.title }}</h3>
        <p class="post-preview" v-html="renderMentions(post.content.slice(0, 200)) + (post.content.length > 200 ? '...' : '')"></p>
        <div class="card-footer">
          <NSpace>
            <span>{{ post.viewCount }} 浏览</span>
            <span>{{ post.likeCount }} 赞</span>
            <span>{{ post.commentCount }} 评论</span>
          </NSpace>
          <NSpace v-if="post.topics && post.topics.length">
            <NTag v-for="t in post.topics" :key="t" size="tiny">{{ t }}</NTag>
          </NSpace>
        </div>
      </NCard>
    </div>

    <div v-if="loading" class="loading">
      <NSpin />
    </div>
    <div v-if="!hasMore && posts.length > 0" class="end">
      <p>— 已加载全部 —</p>
    </div>
  </div>
</template>

<style scoped>
.square {
  height: 100vh;
  overflow-y: auto;
  padding: 24px 16px;
}
.square-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  max-width: 680px;
  margin: 0 auto 16px;
}
.square-header h2 {
  margin: 0;
}
.sort-bar {
  max-width: 680px;
  margin: 0 auto 16px;
  display: flex;
}
.empty {
  max-width: 680px;
  margin: 80px auto;
}
.post-card {
  max-width: 680px;
  margin: 0 auto 12px;
  cursor: pointer;
}
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}
.author-name {
  font-weight: 600;
  font-size: 14px;
  margin-right: 8px;
}
.post-time {
  color: #999;
  font-size: 12px;
}
.post-title {
  margin: 0 0 8px;
  font-size: 18px;
}
.post-preview {
  color: #666;
  font-size: 14px;
  margin: 0 0 12px;
  line-height: 1.6;
}
.post-preview :deep(.mention-link) {
  color: #18a058;
  text-decoration: none;
  font-weight: 500;
}
.card-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 13px;
  color: #999;
}
.loading {
  text-align: center;
  padding: 24px;
}
.end {
  text-align: center;
  color: #ccc;
  padding: 24px;
}
</style>
