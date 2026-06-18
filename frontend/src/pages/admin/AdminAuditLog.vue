<script setup lang="ts">
import { ref, onMounted } from 'vue';
import { NDataTable, NButton, NSelect, NInput, NSpace } from 'naive-ui';
import type { DataTableColumns, SelectOption } from 'naive-ui';
import { getAuditLogs } from '@/api/admin';
import type { AuditLogVO } from '@/types/admin';

const logs = ref<AuditLogVO[]>([]);
const loading = ref(false);
const hasMore = ref(true);

const operatorId = ref<string | null>(null);
const actionFilter = ref<string | null>(null);

const actionOptions: SelectOption[] = [
  { label: '用户封禁', value: 'USER_BAN' },
  { label: '用户解禁', value: 'USER_UNBAN' },
  { label: '角色变更', value: 'USER_ROLE_CHANGE' },
  { label: '批量封禁/解禁', value: 'USER_BATCH_STATUS' },
  { label: '帖子置顶', value: 'POST_PIN' },
  { label: '帖子精华', value: 'POST_ESSENCE' },
  { label: '帖子状态', value: 'POST_STATUS' },
  { label: '帖子删除', value: 'POST_FORCE_DELETE' },
  { label: '空间状态', value: 'SPACE_STATUS' },
  { label: '空间解散', value: 'SPACE_DISMISS' },
  { label: '举报处理', value: 'REPORT_HANDLE' },
  { label: '批量举报处理', value: 'REPORT_BATCH_HANDLE' },
];

async function loadLogs(reset = false) {
  if (loading.value) return;
  loading.value = true;
  try {
    const cursor = reset ? undefined : logs.value[logs.value.length - 1]?.id;
    const list = await getAuditLogs({
      operatorId: operatorId.value ? Number(operatorId.value) : undefined,
      action: actionFilter.value || undefined,
      cursor,
      limit: 20,
    });
    if (reset) {
      logs.value = list;
    } else {
      logs.value.push(...list);
    }
    hasMore.value = list.length >= 20;
  } catch {
    // 忽略加载失败，保留当前列表状态
  }
  loading.value = false;
}

const columns: DataTableColumns<AuditLogVO> = [
  { title: 'ID', key: 'id', width: 70 },
  {
    title: '操作人', key: 'operatorName', width: 120,
    render(row) { return row.operatorName || `ID:${row.operatorId}`; },
  },
  { title: '操作', key: 'action', width: 140 },
  {
    title: '目标', key: 'targetId', width: 160,
    render(row) { return `${row.targetType || ''} #${row.targetId || '-'}`; },
  },
  { title: '详情', key: 'detail', width: 200, ellipsis: { tooltip: true } },
  { title: 'IP', key: 'ipAddress', width: 130 },
  {
    title: '时间', key: 'createdAt', width: 170,
    render(row) { return new Date(row.createdAt).toLocaleString(); },
  },
];

function search() {
  loadLogs(true);
}

function loadMore() {
  if (!loading.value && hasMore.value) loadLogs(false);
}

onMounted(() => loadLogs(true));
</script>

<template>
  <div class="admin-page">
    <h2>
      审计日志
    </h2>

    <NSpace class="admin-filterbar">
      <NInput
        v-model:value="operatorId"
        placeholder="操作人 ID"
        style="width: 140px;"
        clearable
      />
      <NSelect
        v-model:value="actionFilter"
        :options="actionOptions"
        placeholder="操作类型"
        style="width: 140px;"
        clearable
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
      :data="logs"
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
