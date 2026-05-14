<script setup lang="ts">
import { ref, onMounted, h } from 'vue';
import { NDataTable, NTag, NSelect, NButton, NSpace, NModal, NInput, useMessage } from 'naive-ui';
import type { DataTableColumns } from 'naive-ui';
import { getReports, handleReport, batchHandleReports } from '@/api/report';
import type { ReportVO } from '@/types/report';

const message = useMessage();
const reports = ref<ReportVO[]>([]);
const loading = ref(false);

const typeFilter = ref<string | null>(null);
const statusFilter = ref<number | null>(null);

const typeOptions: any[] = [
  { label: '全部类型', value: null },
  { label: '帖子', value: 'POST' },
  { label: '评论', value: 'COMMENT' },
  { label: '资源', value: 'RESOURCE' },
  { label: '用户', value: 'USER' },
];

const statusOptions: any[] = [
  { label: '全部状态', value: null },
  { label: '待处理', value: 0 },
  { label: '已处理', value: 1 },
  { label: '已驳回', value: 2 },
];

const checkedRowKeys = ref<number[]>([]);

const handleModalShow = ref(false);
const handleReportId = ref<number | null>(null);
const handleNote = ref('');
const handleAction = ref<number>(1);

function statusLabel(status: number) {
  if (status === 0) return '待处理';
  if (status === 1) return '已处理';
  return '已驳回';
}

function statusType(status: number): 'warning' | 'success' | 'error' {
  if (status === 0) return 'warning';
  if (status === 1) return 'success';
  return 'error';
}

const targetTypeLabels: Record<string, string> = {
  POST: '帖子',
  COMMENT: '评论',
  RESOURCE: '资源',
  USER: '用户',
};

const columns: DataTableColumns<ReportVO> = [
  { title: 'ID', key: 'id', width: 60 },
  { title: '举报人', key: 'reporterName', width: 100 },
  {
    title: '目标类型',
    key: 'targetType',
    width: 80,
    render: (row) => targetTypeLabels[row.targetType] || row.targetType,
  },
  { title: '目标ID', key: 'targetId', width: 80 },
  { title: '原因', key: 'reason', width: 100 },
  {
    title: '描述',
    key: 'description',
    width: 150,
    ellipsis: { tooltip: true },
    render: (row) => row.description || '-',
  },
  {
    title: '状态',
    key: 'status',
    width: 80,
    render: (row) => h(NTag, { type: statusType(row.status), size: 'small' }, () => statusLabel(row.status)),
  },
  { title: '时间', key: 'createdAt', width: 120, render: (row) => new Date(row.createdAt).toLocaleDateString() },
  {
    title: '操作',
    key: 'actions',
    width: 100,
    render: (row) => {
      if (row.status !== 0) return h('span', { style: { color: '#999' } }, '已处理');
      return h(NSpace, { size: 'small' }, {
        default: () => [
          h(NButton, { size: 'tiny', type: 'success', onClick: () => openHandle(row.id, 1) }, () => '处理'),
          h(NButton, { size: 'tiny', type: 'error', onClick: () => openHandle(row.id, 2) }, () => '驳回'),
        ],
      });
    },
  },
];

function openHandle(id: number, action: number) {
  handleReportId.value = id;
  handleAction.value = action;
  handleNote.value = '';
  handleModalShow.value = true;
}

async function confirmHandle() {
  if (handleReportId.value == null) return;
  try {
    await handleReport(handleReportId.value, {
      status: handleAction.value,
      note: handleNote.value || undefined,
    });
    message.success(handleAction.value === 1 ? '已处理' : '已驳回');
    handleModalShow.value = false;
    await loadReports(true);
  } catch {
    message.error('操作失败');
  }
}

async function loadReports(reset = false) {
  if (loading.value) return;
  loading.value = true;
  try {
    const cursor = reset ? undefined : reports.value[reports.value.length - 1]?.id;
    const list = await getReports({
      cursor,
      limit: 20,
      targetType: typeFilter.value || undefined,
      status: statusFilter.value != null ? statusFilter.value : undefined,
    });
    if (reset) reports.value = list;
    else reports.value.push(...list);
  } catch {
    // ignore
  }
  loading.value = false;
}

async function batchHandle(action: number) {
  if (checkedRowKeys.value.length === 0) { message.warning('请先选择举报'); return; }
  try {
    await batchHandleReports(checkedRowKeys.value, action, '批量处理');
    reports.value.forEach(r => { if (checkedRowKeys.value.includes(r.id)) r.status = action; });
    checkedRowKeys.value = [];
    message.success(`已处理 ${checkedRowKeys.value.length} 条`);
  } catch { message.error('操作失败'); }
}

function onFilterChange() {
  reports.value = [];
  loadReports(true);
}

function scrollToListEnd(e: Event) {
  const target = e.target as HTMLElement;
  if (target.scrollHeight - target.scrollTop - target.clientHeight < 100) {
    loadReports();
  }
}

onMounted(() => loadReports(true));
</script>

<template>
  <div class="admin-reports" @scroll="scrollToListEnd">
    <div class="page-header">
      <h2>举报管理</h2>
    </div>

    <NSpace class="filters">
      <NSelect
        v-model:value="typeFilter"
        :options="typeOptions"
        size="small"
        style="width: 120px"
        @update:value="onFilterChange"
      />
      <NSelect
        v-model:value="statusFilter"
        :options="statusOptions"
        size="small"
        style="width: 120px"
        @update:value="onFilterChange"
      />
    </NSpace>

    <NSpace v-if="checkedRowKeys.length" style="margin-bottom: 12px;">
      <span>已选 {{ checkedRowKeys.length }} 项</span>
      <NButton size="small" type="success" @click="batchHandle(1)">批量处理</NButton>
      <NButton size="small" type="error" @click="batchHandle(2)">批量驳回</NButton>
    </NSpace>

    <NDataTable
      :columns="columns"
      :data="reports"
      :loading="loading"
      :bordered="false"
      :row-key="(row: ReportVO) => row.id"
      v-model:checked-row-keys="checkedRowKeys"
    />

    <NModal v-model:show="handleModalShow" title="处理举报">
      <div style="padding: 16px; width: 400px;">
        <NInput
          v-model:value="handleNote"
          type="textarea"
          placeholder="处理备注（可选）"
          :autosize="{ minRows: 3, maxRows: 6 }"
        />
        <NSpace style="margin-top: 16px; justify-content: flex-end;">
          <NButton @click="handleModalShow = false">取消</NButton>
          <NButton type="primary" @click="confirmHandle">确认</NButton>
        </NSpace>
      </div>
    </NModal>
  </div>
</template>

<style scoped>
.admin-reports {
  height: 100vh;
  overflow-y: auto;
  padding: 24px;
}
.page-header {
  margin-bottom: 16px;
}
.page-header h2 {
  margin: 0;
}
.filters {
  margin-bottom: 16px;
}
</style>
