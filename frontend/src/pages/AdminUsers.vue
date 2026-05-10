<script setup lang="ts">
import { ref, onMounted } from 'vue';
import { NCard, NButton, NDataTable, NSpace, useMessage } from 'naive-ui';
import type { DataTableColumns } from 'naive-ui';
import { getUserById } from '@/api/users';
import type { UserVO } from '@/types/user';

// 简化版：管理员输入用户 ID 查看并封禁/解禁
const message = useMessage();
const userId = ref<number | null>(null);
const user = ref<UserVO | null>(null);
const loading = ref(false);

async function banUser(id: number) {
  try {
    const { request } = await import('@/api/request');
    await request({ method: 'PUT', url: `/admin/users/${id}/ban` });
    message.success('用户已封禁');
    if (user.value) user.value.status = 0;
  } catch {
    message.error('操作失败');
  }
}

async function unbanUser(id: number) {
  try {
    const { request } = await import('@/api/request');
    await request({ method: 'PUT', url: `/admin/users/${id}/unban` });
    message.success('用户已解禁');
    if (user.value) user.value.status = 1;
  } catch {
    message.error('操作失败');
  }
}

async function searchUser() {
  if (!userId.value) return;
  loading.value = true;
  try {
    user.value = await getUserById(userId.value);
  } catch {
    user.value = null;
    message.error('用户不存在');
  }
  loading.value = false;
}
</script>

<template>
  <div class="admin-page">
    <NCard title="用户管理">
      <div class="search-bar">
        <input v-model.number="userId" placeholder="输入用户 ID" type="number" class="id-input" />
        <NButton :loading="loading" @click="searchUser">查询</NButton>
      </div>

      <div v-if="user" class="user-detail">
        <p><strong>{{ user.nickname }}</strong> (ID: {{ user.id }})</p>
        <p>邮箱: {{ user.email }} | 角色: {{ user.role }}</p>
        <p>状态:
          <span :class="user.status === 1 ? 'text-green' : 'text-red'">
            {{ user.status === 1 ? '正常' : '已封禁' }}
          </span>
        </p>
        <NSpace class="actions">
          <NButton v-if="user.status === 1" type="error" @click="banUser(user.id)">
            封禁
          </NButton>
          <NButton v-else type="success" @click="unbanUser(user.id)">
            解禁
          </NButton>
        </NSpace>
      </div>
    </NCard>
  </div>
</template>

<style scoped>
.admin-page {
  max-width: 600px;
  margin: 40px auto;
  padding: 0 16px;
}
.search-bar {
  display: flex;
  gap: 8px;
  margin-bottom: 20px;
}
.id-input {
  flex: 1;
  padding: 8px 12px;
  border: 1px solid #ddd;
  border-radius: 4px;
  font-size: 14px;
}
.user-detail {
  padding: 16px;
  background: #f9f9f9;
  border-radius: 8px;
}
.user-detail p { margin: 4px 0; }
.text-green { color: #18a058; font-weight: bold; }
.text-red { color: #d03050; font-weight: bold; }
.actions { margin-top: 12px; }
</style>
