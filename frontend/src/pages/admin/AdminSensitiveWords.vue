<script setup lang="ts">
import { ref, onMounted, h } from 'vue';
import { NDataTable, NTag, NButton, NInput, NModal, NSpace, NSelect, useMessage } from 'naive-ui';
import type { DataTableColumns, SelectOption } from 'naive-ui';
import { request } from '@/api/request';

interface SensitiveWord {
  id: number;
  word: string;
  level: number;
  createdAt: string;
}

const message = useMessage();
const words = ref<SensitiveWord[]>([]);
const loading = ref(false);
const addModalShow = ref(false);
const addWord = ref('');
const addLevel = ref(1);

const levelOptions: SelectOption[] = [
  { label: '低', value: 1 },
  { label: '中', value: 2 },
  { label: '高', value: 3 },
];

function levelName(level: number) {
  if (level === 1) return '低';
  if (level === 2) return '中';
  return '高';
}

function levelType(level: number): 'info' | 'warning' | 'error' {
  if (level === 1) return 'info';
  if (level === 2) return 'warning';
  return 'error';
}

const columns: DataTableColumns<SensitiveWord> = [
  { title: 'ID', key: 'id', width: 60 },
  { title: '敏感词', key: 'word', width: 150 },
  {
    title: '等级',
    key: 'level',
    width: 80,
    render: (row) => h(NTag, { type: levelType(row.level), size: 'small' }, () => levelName(row.level)),
  },
  { title: '添加时间', key: 'createdAt', width: 150, render: (row) => new Date(row.createdAt).toLocaleDateString() },
  {
    title: '操作',
    key: 'actions',
    width: 80,
    render: (row) =>
      h(NButton, { size: 'tiny', type: 'error', onClick: () => handleDelete(row.id) }, () => '删除'),
  },
];

async function loadWords() {
  loading.value = true;
  try {
    const res = await request<SensitiveWord[]>({ method: 'GET', url: '/admin/sensitive-words' });
    words.value = res.data;
  } catch {
    // 忽略加载失败，保留当前列表状态
  }
  loading.value = false;
}

async function handleAdd() {
  if (!addWord.value.trim()) return;
  try {
    await request({ method: 'POST', url: '/admin/sensitive-words', data: { word: addWord.value.trim(), level: addLevel.value } });
    message.success('已添加');
    addModalShow.value = false;
    addWord.value = '';
    addLevel.value = 1;
    await loadWords();
  } catch {
    message.error('添加失败');
  }
}

async function handleDelete(id: number) {
  try {
    await request({ method: 'DELETE', url: `/admin/sensitive-words/${id}` });
    message.success('已删除');
    await loadWords();
  } catch {
    message.error('删除失败');
  }
}

onMounted(loadWords);
</script>

<template>
  <div class="admin-sw">
    <div class="page-header">
      <h2>敏感词管理</h2>
      <NButton
        type="primary"
        size="small"
        @click="addModalShow = true"
      >
        添加
      </NButton>
    </div>

    <NDataTable
      :columns="columns"
      :data="words"
      :loading="loading"
      :bordered="false"
    />

    <NModal
      v-model:show="addModalShow"
      title="添加敏感词"
    >
      <div style="padding: 16px; width: 360px;">
        <NInput
          v-model:value="addWord"
          placeholder="输入敏感词"
          style="margin-bottom: 12px;"
        />
        <NSelect
          v-model:value="addLevel"
          :options="levelOptions"
          placeholder="敏感等级"
        />
        <NSpace style="margin-top: 16px; justify-content: flex-end;">
          <NButton @click="addModalShow = false">
            取消
          </NButton>
          <NButton
            type="primary"
            @click="handleAdd"
          >
            确认
          </NButton>
        </NSpace>
      </div>
    </NModal>
  </div>
</template>

<style scoped>
.admin-sw {
  min-height: 100%;
  padding: 28px 32px 40px;
}
.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 18px;
}
.page-header h2 {
  margin: 0;
}
</style>
