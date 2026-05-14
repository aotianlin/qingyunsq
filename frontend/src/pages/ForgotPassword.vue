<script setup lang="ts">
import { ref } from 'vue';
import { useRouter } from 'vue-router';
import { NButton, NInput, NCard, NForm, NFormItem, NSteps, NStep, useMessage } from 'naive-ui';
import { forgotPassword, resetPassword } from '@/api/auth';

const router = useRouter();
const message = useMessage();

const step = ref(1);
const email = ref('');
const token = ref('');
const newPassword = ref('');
const confirmPassword = ref('');
const loading = ref(false);

async function handleSendCode() {
  if (!email.value) {
    message.warning('请输入邮箱');
    return;
  }
  loading.value = true;
  try {
    const res = await forgotPassword(email.value);
    token.value = res.token;
    message.success('重置令牌已生成');
    step.value = 2;
  } catch {
    message.error('该邮箱未注册');
  } finally {
    loading.value = false;
  }
}

async function handleReset() {
  if (!newPassword.value || !confirmPassword.value) {
    message.warning('请填写新密码');
    return;
  }
  if (newPassword.value !== confirmPassword.value) {
    message.warning('两次密码不一致');
    return;
  }
  if (newPassword.value.length < 6) {
    message.warning('密码至少 6 位');
    return;
  }
  loading.value = true;
  try {
    await resetPassword(email.value, token.value, newPassword.value);
    message.success('密码重置成功，请重新登录');
    router.push('/login');
  } catch (e: any) {
    message.error(e?.response?.data?.message || '重置失败');
  } finally {
    loading.value = false;
  }
}
</script>

<template>
  <div class="forgot-page">
    <NCard title="找回密码" class="forgot-card">
      <NSteps :current="step" style="margin-bottom: 24px;">
        <NStep title="验证身份" />
        <NStep title="重置密码" />
      </NSteps>

      <NForm v-if="step === 1">
        <NFormItem label="注册邮箱">
          <NInput v-model:value="email" placeholder="请输入注册时使用的邮箱" />
        </NFormItem>
        <NButton type="primary" block :loading="loading" @click="handleSendCode">
          获取重置令牌
        </NButton>
      </NForm>

      <NForm v-else>
        <NFormItem label="新密码">
          <NInput v-model:value="newPassword" type="password" placeholder="请输入新密码" />
        </NFormItem>
        <NFormItem label="确认密码">
          <NInput v-model:value="confirmPassword" type="password" placeholder="请再次输入新密码" />
        </NFormItem>
        <NButton type="primary" block :loading="loading" @click="handleReset">
          重置密码
        </NButton>
        <NButton text block style="margin-top: 8px;" @click="step = 1">
          返回上一步
        </NButton>
      </NForm>

      <div class="link-text">
        <router-link to="/login">返回登录</router-link>
      </div>
    </NCard>
  </div>
</template>

<style scoped>
.forgot-page {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 100vh;
  background: #f5f5f5;
}
.forgot-card {
  width: 400px;
}
.link-text {
  margin-top: 16px;
  text-align: center;
}
</style>
