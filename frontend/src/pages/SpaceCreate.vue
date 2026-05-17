<script setup lang="ts">
import { ref } from 'vue';
import { useRouter } from 'vue-router';
import { NButton, NInput, NSelect, NCard, useMessage } from 'naive-ui';
import { createSpace } from '@/api/spaces';

const router = useRouter();
const message = useMessage();

const name = ref('');
const description = ref('');
const category = ref<string | null>(null);
const visibility = ref('PUBLIC');
const loading = ref(false);

const categoryOptions = [
  { label: '专业', value: 'MAJOR' },
  { label: '班级', value: 'CLASS' },
  { label: '社团', value: 'CLUB' },
  { label: '兴趣', value: 'INTEREST' },
];

const visibilityOptions = [
  { label: '公开（任何人可加入）', value: 'PUBLIC' },
  { label: '审核（需管理员审核）', value: 'REVIEW' },
];

async function submit() {
  if (!name.value.trim() || !category.value) {
    message.warning('请填写空间名称和分类');
    return;
  }
  loading.value = true;
  try {
    const space = await createSpace({
      name: name.value.trim(),
      description: description.value.trim() || undefined,
      category: category.value,
      visibility: visibility.value,
    });
    message.success('创建成功');
    router.push(`/spaces/${space.id}`);
  } catch {
    message.error('创建失败');
  }
  loading.value = false;
}

function cancel() {
  router.back();
}
</script>

<template>
  <div class="create-page">
    <NCard title="创建学习圈">
      <div class="form">
        <label>空间名称</label>
        <NInput
          v-model:value="name"
          placeholder="例如：Java 学习小组"
          maxlength="64"
        />

        <label>简介</label>
        <NInput
          v-model:value="description"
          type="textarea"
          placeholder="简单介绍一下空间..."
          maxlength="255"
        />

        <label>分类</label>
        <NSelect
          v-model:value="category"
          :options="categoryOptions"
          placeholder="选择分类"
        />

        <label>加入方式</label>
        <NSelect
          v-model:value="visibility"
          :options="visibilityOptions"
        />

        <div class="actions">
          <NButton
            type="primary"
            :loading="loading"
            @click="submit"
          >
            创建
          </NButton>
          <NButton @click="cancel">
            取消
          </NButton>
        </div>
      </div>
    </NCard>
  </div>
</template>

<style scoped>
.create-page {
  max-width: 520px;
  margin: 40px auto;
  padding: 0 16px;
}
.form {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.form label {
  font-size: 14px;
  color: #666;
  margin-bottom: -8px;
}
.actions {
  display: flex;
  gap: 12px;
  margin-top: 8px;
}
</style>
