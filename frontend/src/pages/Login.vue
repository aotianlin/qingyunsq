<script setup lang="ts">
import { ref } from 'vue';
import { useRouter } from 'vue-router';
import { NButton, NInput, NCard, NForm, NFormItem, useMessage } from 'naive-ui';
import { login } from '@/api/auth';
import { useAuthStore } from '@/stores/auth';

const router = useRouter();
const message = useMessage();
const authStore = useAuthStore();

const email = ref('');
const password = ref('');
const loading = ref(false);

async function handleLogin() {
  if (!email.value || !password.value) {
    message.warning('请填写邮箱和密码');
    return;
  }
  loading.value = true;
  try {
    const res = await login({ email: email.value, password: password.value });
    authStore.setToken(res.token);
    authStore.setUser(res.user);
    message.success('登录成功');
    router.push('/');
  } catch {
    message.error('登录失败，请检查邮箱和密码');
  } finally {
    loading.value = false;
  }
}
</script>

<template>
  <div class="login-page">
    <NCard title="登录 CampusForum" class="login-card">
      <NForm>
        <NFormItem label="邮箱">
          <NInput v-model:value="email" placeholder="请输入邮箱" />
        </NFormItem>
        <NFormItem label="密码">
          <NInput v-model:value="password" type="password" placeholder="请输入密码" />
        </NFormItem>
        <NButton type="primary" block :loading="loading" @click="handleLogin">
          登录
        </NButton>
      </NForm>
      <div class="link-text">
        <router-link to="/register">没有账号？立即注册</router-link>
      </div>
    </NCard>
  </div>
</template>

<style scoped>
.login-page {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 100vh;
  background: #f5f5f5;
}
.login-card {
  width: 400px;
}
.link-text {
  margin-top: 16px;
  text-align: center;
}
</style>
