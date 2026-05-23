<script setup lang="ts">
import { ref, computed, onMounted } from 'vue';
import {
  NCard, NSelect, NInput, NButton, NSpace, NForm, NFormItem,
  useMessage,
  type FormInst, type FormRules,
} from 'naive-ui';
import { getTenants, updateTenantAiConfig, getTenantAiConfig } from '@/api/tenants';
import type { TenantVO } from '@/api/tenants';

const message = useMessage();
const tenants = ref<TenantVO[]>([]);
const selectedTenantId = ref<number | null>(null);
const loading = ref(false);
const saving = ref(false);

const form = ref({ provider: 'mock', baseUrl: '', apiKey: '', model: '' });
const formRef = ref<FormInst | null>(null);

const providerOptions = [
  { label: 'Mock (模拟AI)', value: 'mock' },
  { label: 'OpenAI 兼容', value: 'openai' },
];

// F5: mock 模式不校验；openai 模式 baseUrl/apiKey/model 必填，baseUrl 需 http(s) 前缀
const rules = computed<FormRules>(() => {
  if (form.value.provider !== 'openai') {
    return {} as FormRules;
  }
  return {
    baseUrl: [
      { required: true, message: '请填写 API Base URL', trigger: ['input', 'blur'] },
      {
        validator: (_rule, value: string) => /^https?:\/\/.+/.test(value || ''),
        message: '必须是 http:// 或 https:// 开头的 URL',
        trigger: ['input', 'blur'],
      },
    ],
    apiKey: [
      { required: true, message: '请填写 API Key', trigger: ['input', 'blur'] },
    ],
    model: [
      { required: true, message: '请填写 Model 名称', trigger: ['input', 'blur'] },
    ],
  };
});

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
  // F5: 调 service 之前先校验，失败时 naive-ui 自动在表单项下方展示错误
  try {
    await formRef.value?.validate();
  } catch {
    return;
  }
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
    <h2 style="margin-bottom: 16px;">
      AI 配置管理
    </h2>

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
      <NCard
        v-if="!loading"
        title="AI 服务配置"
      >
        <NForm
          ref="formRef"
          :model="form"
          :rules="rules"
          label-placement="top"
          style="width: 100%; max-width: 500px;"
        >
          <NFormItem
            label="AI Provider"
            path="provider"
          >
            <NSelect
              v-model:value="form.provider"
              :options="providerOptions"
            />
          </NFormItem>
          <NFormItem
            label="API Base URL"
            path="baseUrl"
          >
            <NInput
              v-model:value="form.baseUrl"
              placeholder="https://api.deepseek.com"
            />
          </NFormItem>
          <NFormItem
            label="API Key"
            path="apiKey"
          >
            <NInput
              v-model:value="form.apiKey"
              type="password"
              show-password-on="click"
              placeholder="sk-..."
            />
          </NFormItem>
          <NFormItem
            label="Model"
            path="model"
          >
            <NInput
              v-model:value="form.model"
              placeholder="deepseek-v4-flash"
            />
          </NFormItem>
          <NButton
            type="primary"
            :loading="saving"
            @click="save"
          >
            保存配置
          </NButton>
        </NForm>
      </NCard>
      <p v-else>
        加载中...
      </p>
    </template>
    <p
      v-else
      style="color: #999;"
    >
      请先选择租户
    </p>
  </div>
</template>
