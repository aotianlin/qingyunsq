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
    <section class="form-hero">
      <div>
        <span>Spaces</span>
        <h1>创建学习圈</h1>
        <p>把同频的人聚在一起，建立清晰的主题、加入规则和学习目标。</p>
      </div>
      <NButton quaternary @click="cancel">
        返回
      </NButton>
    </section>

    <div class="form-grid">
      <NCard class="form-card" title="基础信息">
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

      <aside class="helper-card">
        <h3>创建建议</h3>
        <p>名称尽量具体，简介说明适合谁加入、会一起做什么，能减少无效申请。</p>
        <ul>
          <li>专业/班级空间适合课程资料和长期讨论。</li>
          <li>兴趣空间适合活动、打卡和经验分享。</li>
          <li>审核加入适合需要保持内容质量的学习圈。</li>
        </ul>
      </aside>
    </div>
  </div>
</template>

<style scoped lang="scss">
.create-page {
  min-height: calc(100vh - 112px);
  padding: 8px 0 40px;
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.form-hero,
.form-card,
.helper-card {
  background: var(--cf-card-bg);
  border: 1px solid var(--cf-card-border);
  border-radius: 20px;
  box-shadow: var(--cf-card-shadow);
  backdrop-filter: blur(24px) saturate(150%);
}

.form-hero {
  padding: 24px;
  display: flex;
  justify-content: space-between;
  gap: 18px;
  align-items: flex-start;

  span {
    color: var(--cf-primary);
    font-size: 13px;
    font-weight: 900;
  }

  h1 {
    margin: 8px 0;
    font-size: 28px;
    line-height: 1.2;
  }

  p {
    margin: 0;
    color: var(--cf-text-secondary);
  }
}

.form-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 320px;
  gap: 20px;
  align-items: start;
}

.form-card :deep(.n-card-header) {
  font-weight: 900;
}

.form {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.form label {
  font-size: 14px;
  color: var(--cf-text-secondary);
  font-weight: 800;
  margin-bottom: -6px;
}

.actions {
  display: flex;
  gap: 12px;
  margin-top: 10px;
}

.helper-card {
  padding: 22px;

  h3 {
    margin: 0 0 10px;
  }

  p,
  li {
    color: var(--cf-text-secondary);
    line-height: 1.7;
  }

  ul {
    margin: 16px 0 0;
    padding-left: 18px;
  }
}

@media (max-width: 900px) {
  .form-grid {
    grid-template-columns: 1fr;
  }
}
</style>
