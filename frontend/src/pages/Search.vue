<script setup lang="ts">
import { ref, watch } from 'vue';
import { useRouter } from 'vue-router';
import { NInput, NButton, NTag, NSpace, NSpin, NEmpty, NCard } from 'naive-ui';
import { search } from '@/api/search';
import type { SearchResult } from '@/types/search';

const router = useRouter();
const keyword = ref('');
const searchType = ref('');
const results = ref<SearchResult[]>([]);
const loading = ref(false);
const searched = ref(false);

const types = [
  { key: '', label: '全部' },
  { key: 'POST', label: '帖子' },
  { key: 'USER', label: '用户' },
  { key: 'RESOURCE', label: '资源' },
  { key: 'SPACE', label: '空间' },
] as const;

async function doSearch() {
  if (!keyword.value.trim()) return;
  loading.value = true;
  searched.value = true;
  try {
    results.value = await search({ keyword: keyword.value.trim(), type: searchType.value || undefined });
  } catch {
    results.value = [];
  }
  loading.value = false;
}

function switchType(t: string) {
  searchType.value = t;
  if (searched.value) doSearch();
}

function goTo(result: SearchResult) {
  switch (result.type) {
    case 'POST': router.push(`/posts/${result.id}`); break;
    case 'USER': router.push(`/users/${result.id}`); break;
    case 'RESOURCE': router.push(`/resources/${result.id}`); break;
    case 'SPACE': router.push(`/spaces/${result.id}`); break;
  }
}

function formatSize(bytes: number): string {
  if (!bytes) return '';
  if (bytes < 1024) return bytes + ' B';
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB';
  return (bytes / (1024 * 1024)).toFixed(1) + ' MB';
}

const typeLabels: Record<string, string> = { POST: '帖子', USER: '用户', RESOURCE: '资源', SPACE: '空间' };
const typeColors: Record<string, string> = { POST: '#1890ff', USER: '#52c41a', RESOURCE: '#fa8c16', SPACE: '#722ed1' };
</script>

<template>
  <div class="search-page">
    <div class="search-header">
      <NInput
        v-model:value="keyword"
        size="large"
        placeholder="搜索帖子、用户、资源、空间..."
        clearable
        @keyup.enter="doSearch"
      >
        <template #suffix>
          <NButton type="primary" @click="doSearch">搜索</NButton>
        </template>
      </NInput>
    </div>

    <NSpace class="type-bar">
      <NButton
        v-for="t in types"
        :key="t.key"
        :type="searchType === t.key ? 'primary' : 'default'"
        size="small"
        @click="switchType(t.key)"
      >
        {{ t.label }}
      </NButton>
    </NSpace>

    <div v-if="loading" class="loading">
      <NSpin />
    </div>

    <div v-else-if="searched && results.length === 0" class="empty">
      <NEmpty description="未找到相关内容" />
    </div>

    <div v-else class="results">
      <div v-for="r in results" :key="`${r.type}-${r.id}`" class="result-card" @click="goTo(r)">
        <NCard>
          <div class="result-header">
            <NTag :color="{ color: typeColors[r.type] || '#999', textColor: '#fff' }" size="small">
              {{ typeLabels[r.type] || r.type }}
            </NTag>
            <h3 class="result-title">{{ r.title }}</h3>
          </div>
          <p v-if="r.description" class="result-desc">{{ r.description }}</p>
          <div class="result-meta">
            <NSpace>
              <span v-if="r.author">作者: {{ r.author.nickname }}</span>
              <span v-if="r.createdAt">{{ new Date(r.createdAt).toLocaleDateString() }}</span>
              <span v-if="r.likeCount !== undefined">{{ r.likeCount }} 赞</span>
              <span v-if="r.commentCount !== undefined">{{ r.commentCount }} 评论</span>
              <span v-if="r.viewCount !== undefined">{{ r.viewCount }} 浏览</span>
              <span v-if="r.downloadCount !== undefined">{{ r.downloadCount }} 下载</span>
              <span v-if="r.memberCount !== undefined">{{ r.memberCount }} 成员</span>
              <span v-if="r.postCount !== undefined">{{ r.postCount }} 帖子</span>
              <span v-if="r.fileSize">{{ formatSize(r.fileSize) }}</span>
              <NTag v-if="r.category" size="tiny">{{ r.category }}</NTag>
              <NTag v-if="r.fileType" size="tiny">{{ r.fileType }}</NTag>
            </NSpace>
          </div>
        </NCard>
      </div>
    </div>
  </div>
</template>

<style scoped>
.search-page {
  height: 100vh;
  overflow-y: auto;
  padding: 24px 16px;
}
.search-header {
  max-width: 680px;
  margin: 0 auto 16px;
}
.type-bar {
  max-width: 680px;
  margin: 0 auto 16px;
  display: flex;
}
.loading {
  text-align: center;
  padding: 48px;
}
.empty {
  max-width: 680px;
  margin: 80px auto;
}
.results {
  max-width: 680px;
  margin: 0 auto;
}
.result-card {
  margin-bottom: 12px;
  cursor: pointer;
}
.result-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}
.result-title {
  margin: 0;
  font-size: 16px;
}
.result-desc {
  color: #666;
  font-size: 14px;
  margin: 0 0 8px;
  line-height: 1.5;
}
.result-meta {
  font-size: 13px;
  color: #999;
}
</style>
