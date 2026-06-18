<script setup lang="ts">
import { ref } from 'vue';
import { useRouter } from 'vue-router';
import { NButton, NInput, NDatePicker, NCard, useMessage } from 'naive-ui';
import { createChallenge } from '@/api/checkin';

const router = useRouter();
const message = useMessage();

const name = ref('');
const description = ref('');
const range = ref<[number, number] | null>(null);
const loading = ref(false);

async function submit() {
  if (!name.value.trim() || !range.value) {
    message.warning('请填写挑战名称和日期范围');
    return;
  }
  const [start, end] = range.value;
  const fmt = (ts: number) => new Date(ts).toISOString().split('T')[0];

  loading.value = true;
  try {
    const challenge = await createChallenge({
      name: name.value.trim(),
      description: description.value.trim() || undefined,
      startDate: fmt(start),
      endDate: fmt(end),
    });
    message.success('创建成功');
    router.push(`/checkin/${challenge.id}`);
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
        <span>Check-in</span>
        <h1>创建打卡挑战</h1>
        <p>设置明确周期和规则，让参与者知道每天要完成什么、什么时候结束。</p>
      </div>
      <NButton quaternary @click="cancel">
        返回
      </NButton>
    </section>

    <div class="form-grid">
      <NCard class="form-card" title="挑战信息">
        <div class="form">
          <label>挑战名称</label>
          <NInput
            v-model:value="name"
            placeholder="例如：每日背单词"
            maxlength="64"
          />

          <label>简介</label>
          <NInput
            v-model:value="description"
            type="textarea"
            placeholder="简单描述一下挑战规则..."
            maxlength="500"
          />

          <label>日期范围</label>
          <NDatePicker
            v-model:value="range"
            type="daterange"
            :is-date-disabled="(ts: number) => ts < Date.now() - 86400000"
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
        <h3>体验建议</h3>
        <p>好的挑战应该让人一眼知道目标、节奏和完成标准，避免参与后才发现规则不清。</p>
        <ul>
          <li>名称写行动，不要只写主题。</li>
          <li>周期不宜过长，先让用户容易坚持。</li>
          <li>简介里写清每天的打卡内容。</li>
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
