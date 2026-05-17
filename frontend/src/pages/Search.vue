<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue';
import type { Component } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { NEmpty, NIcon, NInput, NSpin } from 'naive-ui';
import { search } from '@/api/search';
import type { SearchResult } from '@/types/search';
import { ArrowBackOutline, ChatbubblesOutline, CloudDownloadOutline, DocumentTextOutline, EyeOutline, GridOutline, HeartOutline, PersonOutline, PlanetOutline, SearchOutline } from '@vicons/ionicons5';

const route = useRoute();
const router = useRouter();
const keyword = ref('');
const searchType = ref('');
const results = ref<SearchResult[]>([]);
const loading = ref(false);
const searched = ref(false);

const types = [
  { key: '', label: '全部', icon: GridOutline },
  { key: 'POST', label: '帖子', icon: ChatbubblesOutline },
  { key: 'USER', label: '用户', icon: PersonOutline },
  { key: 'RESOURCE', label: '资源', icon: DocumentTextOutline },
  { key: 'SPACE', label: '空间', icon: PlanetOutline },
] as const;

const typeLabels: Record<string, string> = { POST: '帖子', USER: '用户', RESOURCE: '资源', SPACE: '空间' };
const typeIcons: Record<string, Component> = {
  POST: ChatbubblesOutline,
  USER: PersonOutline,
  RESOURCE: DocumentTextOutline,
  SPACE: PlanetOutline,
};

const resultCount = computed(() => results.value.length);

async function doSearch() {
  if (!keyword.value.trim()) return;
  loading.value = true;
  searched.value = true;
  try {
    results.value = await search({ keyword: keyword.value.trim(), type: searchType.value || undefined });
    router.replace({ path: '/search', query: { ...(searchType.value ? { type: searchType.value } : {}), q: keyword.value.trim() } });
  } catch {
    results.value = [];
  } finally {
    loading.value = false;
  }
}

function switchType(type: string) {
  searchType.value = type;
  if (searched.value) {
    void doSearch();
  }
}

function goTo(result: SearchResult) {
  switch (result.type) {
    case 'POST':
      router.push(`/posts/${result.id}`);
      break;
    case 'USER':
      router.push(`/users/${result.id}`);
      break;
    case 'RESOURCE':
      router.push(`/resources/${result.id}`);
      break;
    case 'SPACE':
      router.push(`/spaces/${result.id}`);
      break;
  }
}

function formatSize(bytes: number) {
  if (!bytes) return '';
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}

function formatDate(value?: string) {
  if (!value) return '暂无时间';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return '暂无时间';
  return date.toLocaleDateString('zh-CN');
}

onMounted(() => {
  const q = String(route.query.q || '');
  const type = String(route.query.type || '');
  keyword.value = q;
  searchType.value = type;
  if (q.trim()) {
    void doSearch();
  }
});

watch(
  () => route.query.q,
  (value) => {
    const q = String(value || '');
    if (q && q !== keyword.value) {
      keyword.value = q;
      void doSearch();
    }
  },
);
</script>

<template>
  <div class="search-page">
    <section class="search-head cf-surface">
      <button class="back-btn" @click="router.back()">
        <n-icon size="18"><ArrowBackOutline /></n-icon>
      </button>
      <div class="search-head-copy">
        <span class="cf-pill">Search</span>
        <h1 class="cf-section-title">
          全站搜索
        </h1>
        <p class="cf-section-subtitle">
          已补上从全局搜索框携带 `q` 参数进入后的自动检索能力，并统一为新的浅色检索工作台。
        </p>
      </div>
    </section>

    <section class="search-bar cf-card">
      <n-input
        v-model:value="keyword"
        size="large"
        placeholder="搜索帖子、用户、资源或学习圈"
        clearable
        @keyup.enter="doSearch"
      >
        <template #prefix>
          <n-icon><SearchOutline /></n-icon>
        </template>
      </n-input>
      <button class="cf-primary-btn" @click="doSearch">
        搜索
      </button>
    </section>

    <section class="filter-bar cf-surface">
      <button
        v-for="item in types"
        :key="item.key"
        class="filter-chip"
        :class="{ active: searchType === item.key }"
        @click="switchType(item.key)"
      >
        <n-icon size="16">
          <component :is="item.icon" />
        </n-icon>
        {{ item.label }}
      </button>
    </section>

    <section class="result-head" v-if="searched && !loading">
      <strong>找到 {{ resultCount }} 条结果</strong>
      <span>{{ keyword }}</span>
    </section>

    <div v-if="loading" class="loading-wrap">
      <n-spin size="large" />
    </div>

    <div v-else-if="searched && results.length === 0" class="empty-wrap cf-surface">
      <n-empty description="未找到相关内容，换个关键词试试" />
    </div>

    <div v-else class="result-list">
      <article
        v-for="item in results"
        :key="`${item.type}-${item.id}`"
        class="result-card cf-card"
        @click="goTo(item)"
      >
        <div class="result-top">
          <span class="type-tag">
            <n-icon size="14"><component :is="typeIcons[item.type] || GridOutline" /></n-icon>
            {{ typeLabels[item.type] || item.type }}
          </span>
          <span class="result-date">{{ formatDate(item.createdAt) }}</span>
        </div>

        <h3>{{ item.title }}</h3>
        <p v-if="item.description">{{ item.description }}</p>

        <div class="meta-row">
          <span v-if="item.author">
            <n-icon size="15"><PersonOutline /></n-icon>
            {{ item.author.nickname }}
          </span>
          <span v-if="item.likeCount !== undefined">
            <n-icon size="15"><HeartOutline /></n-icon>
            {{ item.likeCount }}
          </span>
          <span v-if="item.commentCount !== undefined">
            <n-icon size="15"><ChatbubblesOutline /></n-icon>
            {{ item.commentCount }}
          </span>
          <span v-if="item.viewCount !== undefined">
            <n-icon size="15"><EyeOutline /></n-icon>
            {{ item.viewCount }}
          </span>
          <span v-if="item.downloadCount !== undefined">
            <n-icon size="15"><CloudDownloadOutline /></n-icon>
            {{ item.downloadCount }}
          </span>
          <span v-if="item.fileSize">
            {{ formatSize(item.fileSize) }}
          </span>
        </div>
      </article>
    </div>
  </div>
</template>

<style scoped lang="scss">
.search-page {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.search-head {
  padding: 22px;
  display: flex;
  gap: 16px;
  align-items: flex-start;
}

.search-head-copy {
  flex: 1;
}

.back-btn {
  width: 40px;
  height: 40px;
  border-radius: 12px;
  border: 1px solid var(--cf-border);
  background: var(--cf-bg-elevated);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  color: var(--cf-text-secondary);
}

.search-bar {
  padding: 18px;
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 12px;
}

.filter-bar {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  padding: 12px;
}

.filter-chip {
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
}

.filter-chip.active {
  background: var(--cf-primary-soft);
  color: var(--cf-primary);
}

.result-head {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  color: var(--cf-text-secondary);
  font-size: 14px;

  strong {
    color: var(--cf-text-primary);
  }
}

.result-list {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.result-card {
  padding: 22px;
  cursor: pointer;

  h3 {
    margin: 12px 0 10px;
    font-family: var(--cf-font-heading);
    font-size: 24px;
  }

  p {
    margin: 0;
    color: var(--cf-text-secondary);
    line-height: 1.8;
  }
}

.result-top {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: center;
}

.type-tag {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 6px 12px;
  border-radius: 999px;
  background: var(--cf-primary-soft);
  color: var(--cf-primary);
  font-size: 13px;
  font-weight: 700;
}

.result-date {
  color: var(--cf-text-muted);
  font-size: 13px;
}

.meta-row {
  display: flex;
  flex-wrap: wrap;
  gap: 14px;
  margin-top: 16px;
  color: var(--cf-text-muted);
  font-size: 13px;

  span {
    display: inline-flex;
    align-items: center;
    gap: 6px;
  }
}

.loading-wrap,
.empty-wrap {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 180px;
}

.empty-wrap {
  padding: 24px;
}

@media (max-width: 720px) {
  .search-bar {
    grid-template-columns: 1fr;
  }
}
</style>
