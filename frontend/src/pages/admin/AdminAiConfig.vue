<script setup lang="ts">
import { ref, onMounted } from 'vue';
import { NCard, NSelect, NInput, NButton, NSpace, useMessage } from 'naive-ui';
import { getTenants, updateTenantAiConfig, getTenantAiConfig } from '@/api/tenants';
import type { TenantVO } from '@/api/tenants';

const message = useMessage();
const tenants = ref<TenantVO[]>([]);
const selectedTenantId = ref<number | null>(null);
const loading = ref(false);
const saving = ref(false);

const form = ref({ provider: 'mock', baseUrl: '', apiKey: '', model: '' });

const providerOptions = [
  { label: 'Mock (模拟AI)', value: 'mock' },
  { label: 'OpenAI 兼容', value: 'openai' },
];

onMounted(async () => {
  try {
    tenants.value = await getTenants();
  } catch { /* ignore */ }
});

async function selectTenant(id: number) {
  selectedTenantId.value = id;
  loading.value = true;
  try {
    const cfg = await getTenantAiConfig(id);
    form.value = {
      provider: cfg.provider || 'mock',
      baseUrl: cfg.baseUrl || '',
      apiKey: cfg.apiKey || '',
      model: cfg.model || '',
    };
  } catch {
    message.error('加载配置失败');
  }
  loading.value = false;
}

async function save() {
  if (selectedTenantId.value === null) return;
  saving.value = true;
  try {
    await updateTenantAiConfig(selectedTenantId.value, form.value);
    message.success('AI 配置已保存');
  } catch {
    message.error('保存失败');
  }
  saving.value = false;
}
</script>

<template>
  <div style="padding: 24px;">
    <h2 style="margin-bottom: 16px;">AI 配置管理</h2>

    <NSpace style="margin-bottom: 16px;">
      <NSelect
        v-model:value="selectedTenantId"
        :options="tenants.map(t => ({ label: t.name, value: t.id }))"
        placeholder="选择租户"
        style="width: 200px;"
        @update:value="selectTenant"
      />
    </NSpace>

    <template v-if="selectedTenantId">
      <NCard v-if="!loading" title="AI 服务配置">
        <NSpace vertical style="width: 100%; max-width: 500px;">
          <div>
            <label style="display:block; margin-bottom: 4px; font-size: 14px; color: #666;">AI Provider</label>
            <NSelect v-model:value="form.provider" :options="providerOptions" />
          </div>
          <div>
            <label style="display:block; margin-bottom: 4px; font-size: 14px; color: #666;">API Base URL</label>
            <NInput v-model:value="form.baseUrl" placeholder="https://api.deepseek.com/v1" />
          </div>
          <div>
            <label style="display:block; margin-bottom: 4px; font-size: 14px; color: #666;">API Key</label>
            <NInput v-model:value="form.apiKey" type="password" show-password-on="click" placeholder="sk-..." />
          </div>
          <div>
            <label style="display:block; margin-bottom: 4px; font-size: 14px; color: #666;">Model</label>
            <NInput v-model:value="form.model" placeholder="deepseek-chat" />
          </div>
          <NButton type="primary" :loading="saving" @click="save">保存配置</NButton>
        </NSpace>
      </NCard>
      <p v-else>加载中...</p>
    </template>
    <p v-else style="color: #999;">请先选择租户</p>
  </div>
</template>
