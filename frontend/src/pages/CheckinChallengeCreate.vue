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
    <NCard title="创建打卡挑战">
      <div class="form">
        <label>挑战名称</label>
        <NInput v-model:value="name" placeholder="例如：每日背单词" maxlength="64" />

        <label>简介</label>
        <NInput v-model:value="description" type="textarea" placeholder="简单描述一下挑战规则..." maxlength="500" />

        <label>日期范围</label>
        <NDatePicker v-model:value="range" type="daterange" :is-date-disabled="(ts: number) => ts < Date.now() - 86400000" />

        <div class="actions">
          <NButton type="primary" :loading="loading" @click="submit">创建</NButton>
          <NButton @click="cancel">取消</NButton>
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
