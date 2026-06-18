<script setup lang="ts">
import { ref, onMounted, h } from 'vue';
import {
  NDataTable, NButton, NSelect, NInput, NSpace, NTag, NPopconfirm, useMessage,
} from 'naive-ui';
import type { DataTableColumns, SelectOption } from 'naive-ui';
import { getAdminSpaces, setSpaceStatus, adminDeleteSpace } from '@/api/admin';
import type { SpaceVO } from '@/types/space';

const message = useMessage();
const spaces = ref<SpaceVO[]>([]);
const loading = ref(false);
const hasMore = ref(true);

const keyword = ref('');
const categoryFilter = ref<string | null>(null);
const statusFilter = ref<number | null>(null);

const categoryOptions: SelectOption[] = [
  { label: '班级', value: 'CLASS' },
  { label: '专业', value: 'MAJOR' },
  { label: '社团', value: 'CLUB' },
];

const statusOptions: SelectOption[] = [
  { label: '正常', value: 1 },
  { label: '已禁用', value: 0 },
];

async function loadSpaces(reset = false) {
  if (loading.value) return;
  loading.value = true;
  try {
    const cursor = reset ? undefined : spaces.value[spaces.value.length - 1]?.id;
    const list = await getAdminSpaces({
      keyword: keyword.value || undefined,
      category: categoryFilter.value || undefined,
      status: statusFilter.value != null ? statusFilter.value : undefined,
      cursor,
      limit: 20,
    });
    if (reset) {
      spaces.value = list;
    } else {
      spaces.value.push(...list);
    }
    hasMore.value = list.length >= 20;
  } catch {
    // 忽略加载失败，保留当前列表状态
  }
  loading.value = false;
}

async function handleSetStatus(row: SpaceVO, status: number) {
  try {
    await setSpaceStatus(row.id, status);
    row.status = status;
    message.success(status === 1 ? '已启用' : '已禁用');
  } catch {
    message.error('操作失败');
  }
}

async function handleDismiss(row: SpaceVO) {
  try {
    await adminDeleteSpace(row.id);
    spaces.value = spaces.value.filter((s) => s.id !== row.id);
    message.success('空间已解散');
  } catch {
    message.error('操作失败');
  }
}

const columns: DataTableColumns<SpaceVO> = [
  { title: 'ID', key: 'id', width: 70 },
  { title: '名称', key: 'name', width: 160, ellipsis: { tooltip: true } },
  {
    title: '所属者', key: 'owner', width: 100,
    render(row) { return row.owner?.nickname || '未知'; },
  },
  {
    title: '分类', key: 'category', width: 70,
    render(row) {
      const map: Record<string, string> = { CLASS: '班级', MAJOR: '专业', CLUB: '社团' };
      return h(NTag, { size: 'small' }, { default: () => map[row.category] || row.category });
    },
  },
  { title: '成员', key: 'memberCount', width: 60 },
  { title: '帖子', key: 'postCount', width: 60 },
  {
    title: '状态', key: 'status', width: 70,
    render(row) {
      return h(NTag, { type: row.status === 1 ? 'success' : 'error', size: 'small' },
        { default: () => row.status === 1 ? '正常' : '已禁用' });
    },
  },
  {
    title: '操作', key: 'actions', width: 180,
    render(row) {
      return h(NSpace, null, {
        default: () => [
          row.status === 1
            ? h(NPopconfirm, { onPositiveClick: () => handleSetStatus(row, 0) }, {
                trigger: () => h(NButton, { size: 'tiny', type: 'warning' }, { default: () => '禁用' }),
                default: () => '确定禁用该空间？',
              })
            : h(NPopconfirm, { onPositiveClick: () => handleSetStatus(row, 1) }, {
                trigger: () => h(NButton, { size: 'tiny', type: 'success' }, { default: () => '启用' }),
                default: () => '确定启用该空间？',
              }),
          h(NPopconfirm, { onPositiveClick: () => handleDismiss(row) }, {
            trigger: () => h(NButton, { size: 'tiny', type: 'error' }, { default: () => '解散' }),
            default: () => '确定解散该空间？此操作不可撤销。',
          }),
        ],
      });
    },
  },
];

function search() {
  loadSpaces(true);
}

function loadMore() {
  if (!loading.value && hasMore.value) loadSpaces(false);
}

onMounted(() => loadSpaces(true));
</script>

<template>
  <div class="admin-page">
    <h2>
      空间管理
    </h2>

    <NSpace class="admin-filterbar">
      <NInput
        v-model:value="keyword"
        placeholder="搜索空间名称"
        style="width: 200px;"
        clearable
      />
      <NSelect
        v-model:value="categoryFilter"
        :options="categoryOptions"
        placeholder="分类"
        style="width: 100px;"
      />
      <NSelect
        v-model:value="statusFilter"
        :options="statusOptions"
        placeholder="状态"
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
      :data="spaces"
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
