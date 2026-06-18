<script setup lang="ts">
import { ref, onMounted, h } from 'vue';
import {
  NDataTable, NButton, NSelect, NInput, NSpace, NTag, NPopconfirm, useMessage,
} from 'naive-ui';
import type { DataTableColumns, SelectOption } from 'naive-ui';
import { getAdminPosts, togglePin, toggleEssence, setPostStatus } from '@/api/admin';
import type { PostVO } from '@/types/post';

const message = useMessage();
const posts = ref<PostVO[]>([]);
const loading = ref(false);
const hasMore = ref(true);

const keyword = ref('');
const statusFilter = ref<number | null>(null);
const scopeFilter = ref<string | null>(null);

type TagType = 'default' | 'success' | 'error' | 'warning' | 'info';

const statusOptions: SelectOption[] = [
  { label: '正常', value: 1 },
  { label: '隐藏', value: 2 },
];

const scopeOptions: SelectOption[] = [
  { label: '广场', value: 'SQUARE' },
  { label: '空间', value: 'SPACE' },
];

async function loadPosts(reset = false) {
  if (loading.value) return;
  loading.value = true;
  try {
    const cursor = reset ? undefined : posts.value[posts.value.length - 1]?.id;
    const list = await getAdminPosts({
      keyword: keyword.value || undefined,
      status: statusFilter.value != null ? statusFilter.value : undefined,
      scope: scopeFilter.value || undefined,
      cursor,
      limit: 20,
    });
    if (reset) {
      posts.value = list;
    } else {
      posts.value.push(...list);
    }
    hasMore.value = list.length >= 20;
  } catch {
    // 忽略加载失败，保留当前列表状态
  }
  loading.value = false;
}

async function handleTogglePin(row: PostVO) {
  try {
    await togglePin(row.id);
    row.isPinned = row.isPinned ? 0 : 1;
    message.success(row.isPinned ? '已置顶' : '已取消置顶');
  } catch {
    message.error('操作失败');
  }
}

async function handleToggleEssence(row: PostVO) {
  try {
    await toggleEssence(row.id);
    row.isEssence = row.isEssence ? 0 : 1;
    message.success(row.isEssence ? '已加精' : '已取消精华');
  } catch {
    message.error('操作失败');
  }
}

async function handleSetStatus(row: PostVO, status: number) {
  try {
    await setPostStatus(row.id, status);
    row.status = status;
    message.success('状态已更新');
  } catch {
    message.error('操作失败');
  }
}

const columns: DataTableColumns<PostVO> = [
  { title: 'ID', key: 'id', width: 70 },
  { title: '标题', key: 'title', width: 180, ellipsis: { tooltip: true } },
  {
    title: '作者', key: 'author', width: 100,
    render(row) { return row.author?.nickname || '未知'; },
  },
  {
    title: '范围', key: 'scope', width: 70,
    render(row) {
      return h(NTag, { type: row.scope === 'SPACE' ? 'info' : 'default', size: 'small' },
        { default: () => row.scope });
    },
  },
  {
    title: '状态', key: 'status', width: 70,
    render(row) {
      const map: Record<number, { label: string; type: TagType }> = {
        0: { label: '待审', type: 'default' },
        1: { label: '正常', type: 'success' },
        2: { label: '隐藏', type: 'error' },
      };
      const s = map[row.status] || { label: '未知', type: 'default' };
      return h(NTag, { type: s.type, size: 'small' }, { default: () => s.label });
    },
  },
  {
    title: '操作', key: 'actions', width: 300,
    render(row) {
      return h(NSpace, null, {
        default: () => [
          h(NButton, { size: 'tiny', onClick: () => handleTogglePin(row) },
            { default: () => row.isPinned ? '取消置顶' : '置顶' }),
          h(NButton, { size: 'tiny', onClick: () => handleToggleEssence(row) },
            { default: () => row.isEssence ? '取消精华' : '加精' }),
          row.status === 1
            ? h(NPopconfirm, { onPositiveClick: () => handleSetStatus(row, 2) }, {
                trigger: () => h(NButton, { size: 'tiny', type: 'warning' }, { default: () => '隐藏' }),
                default: () => '确定隐藏该帖子？',
              })
            : h(NPopconfirm, { onPositiveClick: () => handleSetStatus(row, 1) }, {
                trigger: () => h(NButton, { size: 'tiny', type: 'success' }, { default: () => '恢复' }),
                default: () => '确定恢复该帖子？',
              }),
        ],
      });
    },
  },
];

function search() {
  loadPosts(true);
}

function loadMore() {
  if (!loading.value && hasMore.value) loadPosts(false);
}

onMounted(() => loadPosts(true));
</script>

<template>
  <div class="admin-page">
    <h2>
      帖子管理
    </h2>

    <NSpace class="admin-filterbar">
      <NInput
        v-model:value="keyword"
        placeholder="搜索标题/内容"
        style="width: 240px;"
        clearable
      />
      <NSelect
        v-model:value="statusFilter"
        :options="statusOptions"
        placeholder="状态"
        style="width: 100px;"
      />
      <NSelect
        v-model:value="scopeFilter"
        :options="scopeOptions"
        placeholder="范围"
        style="width: 100px;"
      />
      <NButton
        type="primary"
        @click="search"
      >
        搜索
      </NButton>
    </NSpace>

    <NDataTable
      :columns="columns"
      :data="posts"
      :loading="loading"
      :bordered="false"
    />

    <div
      v-if="hasMore"
      style="text-align: center; padding: 16px;"
    >
      <NButton
        text
        type="primary"
        :loading="loading"
        @click="loadMore"
      >
        加载更多
      </NButton>
    </div>
  </div>
</template>
