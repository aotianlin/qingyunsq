<script setup lang="ts">
import { ref, onMounted, h } from 'vue';
import {
  NDataTable, NButton, NSelect, NInput, NSpace, NModal, NTag, NPopconfirm, useMessage,
} from 'naive-ui';
import type { DataTableColumns } from 'naive-ui';
import { getAdminUsers, banUser, unbanUser, changeUserRole, batchSetUserStatus } from '@/api/admin';
import type { AdminUserItem } from '@/api/admin';

const message = useMessage();
const users = ref<AdminUserItem[]>([]);
const loading = ref(false);
const hasMore = ref(true);

const keyword = ref('');
const roleFilter = ref<string | null>(null);
const statusFilter = ref<number | null>(null);

const checkedRowKeys = ref<number[]>([]);

const roleModalShow = ref(false);
const roleModalUserId = ref<number | null>(null);
const selectedRole = ref('USER');

const roleOptions: any[] = [
  { label: '普通用户', value: 'USER' },
  { label: '管理员', value: 'TENANT_ADMIN' },
];

const statusOptions: any[] = [
  { label: '全部', value: null },
  { label: '正常', value: 1 },
  { label: '已封禁', value: 0 },
];

async function loadUsers(reset = false) {
  if (loading.value) return;
  loading.value = true;
  try {
    const cursor = reset ? undefined : users.value[users.value.length - 1]?.id;
    const list = await getAdminUsers({
      keyword: keyword.value || undefined,
      role: roleFilter.value || undefined,
      status: statusFilter.value != null ? statusFilter.value : undefined,
      cursor,
      limit: 20,
    });
    if (reset) {
      users.value = list;
    } else {
      users.value.push(...list);
    }
    hasMore.value = list.length >= 20;
  } catch {
    // ignore
  }
  loading.value = false;
}

async function handleBan(row: AdminUserItem) {
  try {
    await banUser(row.id);
    row.status = 0;
    message.success('已封禁');
  } catch {
    message.error('操作失败');
  }
}

async function handleUnban(row: AdminUserItem) {
  try {
    await unbanUser(row.id);
    row.status = 1;
    message.success('已解禁');
  } catch {
    message.error('操作失败');
  }
}

async function batchBan() {
  if (checkedRowKeys.value.length === 0) { message.warning('请先选择用户'); return; }
  try {
    await batchSetUserStatus(checkedRowKeys.value, 0);
    users.value.forEach(u => { if (checkedRowKeys.value.includes(u.id)) u.status = 0; });
    checkedRowKeys.value = [];
    message.success(`已封禁 ${checkedRowKeys.value.length} 个用户`);
  } catch { message.error('操作失败'); }
}

async function batchUnban() {
  if (checkedRowKeys.value.length === 0) { message.warning('请先选择用户'); return; }
  try {
    await batchSetUserStatus(checkedRowKeys.value, 1);
    users.value.forEach(u => { if (checkedRowKeys.value.includes(u.id)) u.status = 1; });
    checkedRowKeys.value = [];
    message.success('已解禁');
  } catch { message.error('操作失败'); }
}

function openRoleModal(row: AdminUserItem) {
  roleModalUserId.value = row.id;
  selectedRole.value = row.role;
  roleModalShow.value = true;
}

async function handleChangeRole() {
  if (roleModalUserId.value == null) return;
  try {
    await changeUserRole(roleModalUserId.value, selectedRole.value);
    const u = users.value.find((x) => x.id === roleModalUserId.value);
    if (u) u.role = selectedRole.value;
    message.success('角色已修改');
    roleModalShow.value = false;
  } catch {
    message.error('操作失败');
  }
}

const columns: DataTableColumns<AdminUserItem> = [
  { title: 'ID', key: 'id', width: 80 },
  { title: '昵称', key: 'nickname', width: 140 },
  { title: '邮箱', key: 'email', width: 200, ellipsis: { tooltip: true } },
  { title: '学号', key: 'studentNo', width: 120 },
  {
    title: '角色', key: 'role', width: 100,
    render(row) {
      const color = row.role === 'TENANT_ADMIN' ? 'warning' : row.role === 'SUPER_ADMIN' ? 'error' : 'default';
      return h(NTag, { type: color, size: 'small' }, { default: () => row.role });
    },
  },
  {
    title: '状态', key: 'status', width: 80,
    render(row) {
      return h(NTag, { type: row.status === 1 ? 'success' : 'error', size: 'small' },
        { default: () => row.status === 1 ? '正常' : '已封禁' });
    },
  },
  {
    title: '操作', key: 'actions', width: 220,
    render(row) {
      return h(NSpace, null, {
        default: () => [
          row.status === 1
            ? h(NPopconfirm, { onPositiveClick: () => handleBan(row) }, {
                trigger: () => h(NButton, { size: 'tiny', type: 'warning' }, { default: () => '封禁' }),
                default: () => '确定封禁该用户？',
              })
            : h(NPopconfirm, { onPositiveClick: () => handleUnban(row) }, {
                trigger: () => h(NButton, { size: 'tiny', type: 'success' }, { default: () => '解禁' }),
                default: () => '确定解禁该用户？',
              }),
          h(NButton, { size: 'tiny', onClick: () => openRoleModal(row) }, { default: () => '修改角色' }),
        ],
      });
    },
  },
];

function search() {
  loadUsers(true);
}

function loadMore() {
  if (!loading.value && hasMore.value) loadUsers(false);
}

onMounted(() => loadUsers(true));
</script>

<template>
  <div style="padding: 24px;">
    <h2 style="margin-bottom: 16px;">用户管理</h2>

    <NSpace style="margin-bottom: 16px;">
      <NInput v-model:value="keyword" placeholder="搜索昵称/邮箱/学号" style="width: 240px;" clearable />
      <NSelect v-model:value="roleFilter" :options="roleOptions" placeholder="角色" style="width: 120px;" clearable />
      <NSelect v-model:value="statusFilter" :options="statusOptions" placeholder="状态" style="width: 110px;" />
      <NButton type="primary" @click="search">搜索</NButton>
    </NSpace>

    <NSpace v-if="checkedRowKeys.length" style="margin-bottom: 12px;">
      <span>已选 {{ checkedRowKeys.length }} 项</span>
      <NButton size="small" type="warning" @click="batchBan">批量封禁</NButton>
      <NButton size="small" type="success" @click="batchUnban">批量解禁</NButton>
    </NSpace>

    <NDataTable
      :columns="columns"
      :data="users"
      :loading="loading"
      :bordered="false"
      :row-key="(row: AdminUserItem) => row.id"
      v-model:checked-row-keys="checkedRowKeys"
    />

    <div v-if="hasMore" style="text-align: center; padding: 16px;">
      <NButton text type="primary" :loading="loading" @click="loadMore">加载更多</NButton>
    </div>

    <NModal v-model:show="roleModalShow" title="修改角色" preset="dialog" positive-text="确认" @positive-click="handleChangeRole">
      <NSelect v-model:value="selectedRole" :options="roleOptions" />
    </NModal>
  </div>
</template>
