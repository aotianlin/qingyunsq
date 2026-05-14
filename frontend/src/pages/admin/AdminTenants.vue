<script setup lang="ts">
import { ref, onMounted } from 'vue';
import { NButton, NTag, NModal, NInput, NForm, NFormItem, NSpace, useMessage } from 'naive-ui';
import { getTenants, createTenant, updateTenant, toggleTenantStatus, type TenantVO } from '@/api/tenants';

const message = useMessage();
const tenants = ref<TenantVO[]>([]);
const loading = ref(true);
const showCreate = ref(false);
const editingId = ref<number | null>(null);

const form = ref({ code: '', name: '', domain: '' });
const editForm = ref({ name: '', domain: '', logoUrl: '' });

async function load() {
  loading.value = true;
  try {
    tenants.value = await getTenants();
  } catch {
    message.error('加载失败');
  }
  loading.value = false;
}

async function handleCreate() {
  if (!form.value.code || !form.value.name) {
    message.warning('请填写编码和名称');
    return;
  }
  try {
    await createTenant(form.value);
    showCreate.value = false;
    form.value = { code: '', name: '', domain: '' };
    message.success('创建成功');
    await load();
  } catch {
    message.error('创建失败');
  }
}

async function handleToggleStatus(id: number) {
  try {
    await toggleTenantStatus(id);
    await load();
    message.success('状态已切换');
  } catch {
    message.error('操作失败');
  }
}

function openEdit(t: TenantVO) {
  editingId.value = t.id;
  editForm.value = { name: t.name, domain: t.domain || '', logoUrl: t.logoUrl || '' };
}

async function saveEdit() {
  if (editingId.value === null) return;
  try {
    await updateTenant(editingId.value, editForm.value);
    editingId.value = null;
    message.success('保存成功');
    await load();
  } catch {
    message.error('保存失败');
  }
}

onMounted(load);
</script>

<template>
  <div class="admin-tenants">
    <div class="header">
      <h2>租户管理</h2>
      <NButton type="primary" @click="showCreate = true">新建租户</NButton>
    </div>

    <table v-if="!loading" class="data-table">
      <thead>
        <tr>
          <th>ID</th>
          <th>编码</th>
          <th>名称</th>
          <th>域名</th>
          <th>状态</th>
          <th>创建时间</th>
          <th>操作</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="t in tenants" :key="t.id">
          <td>{{ t.id }}</td>
          <td>{{ t.code }}</td>
          <td>{{ t.name }}</td>
          <td>{{ t.domain || '-' }}</td>
          <td>
            <NTag :type="t.status === 1 ? 'success' : 'error'">
              {{ t.status === 1 ? '启用' : '停用' }}
            </NTag>
          </td>
          <td>{{ t.createdAt?.substring(0, 10) }}</td>
          <td>
            <NSpace>
              <NButton size="small" @click="openEdit(t)">编辑</NButton>
              <NButton size="small" @click="handleToggleStatus(t.id)">
                {{ t.status === 1 ? '停用' : '启用' }}
              </NButton>
            </NSpace>
          </td>
        </tr>
      </tbody>
    </table>
    <p v-else>加载中...</p>

    <!-- Create Modal -->
    <NModal v-model:show="showCreate" title="新建租户">
      <div class="modal-body">
        <NForm>
          <NFormItem label="编码（必填）">
            <NInput v-model:value="form.code" placeholder="如 hust" />
          </NFormItem>
          <NFormItem label="学校名称（必填）">
            <NInput v-model:value="form.name" placeholder="如 华中科技大学" />
          </NFormItem>
          <NFormItem label="域名">
            <NInput v-model:value="form.domain" placeholder="如 hust.campusforum.com" />
          </NFormItem>
        </NForm>
        <NSpace class="modal-actions">
          <NButton type="primary" @click="handleCreate">创建</NButton>
          <NButton @click="showCreate = false">取消</NButton>
        </NSpace>
      </div>
    </NModal>

    <!-- Edit Modal -->
    <NModal :show="editingId !== null" title="编辑租户" @update:show="val => { if (!val) editingId = null; }">
      <div class="modal-body">
        <NForm>
          <NFormItem label="名称">
            <NInput v-model:value="editForm.name" />
          </NFormItem>
          <NFormItem label="域名">
            <NInput v-model:value="editForm.domain" />
          </NFormItem>
          <NFormItem label="Logo URL">
            <NInput v-model:value="editForm.logoUrl" />
          </NFormItem>
        </NForm>
        <NSpace class="modal-actions">
          <NButton type="primary" @click="saveEdit">保存</NButton>
          <NButton @click="editingId = null">取消</NButton>
        </NSpace>
      </div>
    </NModal>
  </div>
</template>

<style scoped>
.admin-tenants {
  padding: 24px;
}
.header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}
.header h2 { margin: 0; }
.data-table {
  width: 100%;
  border-collapse: collapse;
  background: #fff;
  border-radius: 8px;
  overflow: hidden;
}
.data-table th,
.data-table td {
  padding: 12px 16px;
  text-align: left;
  border-bottom: 1px solid #eee;
}
.data-table th {
  background: #fafafa;
  font-weight: 600;
  font-size: 13px;
  color: #666;
}
.modal-body {
  padding: 16px 24px;
}
.modal-actions {
  margin-top: 16px;
}
</style>
