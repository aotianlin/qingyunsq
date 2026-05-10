<script setup lang="ts">
import { ref } from 'vue';
import { useRouter } from 'vue-router';
import { NButton, NInput, NCard, NForm, NFormItem, useMessage } from 'naive-ui';
import { register } from '@/api/auth';

const router = useRouter();
const message = useMessage();

const email = ref('');
const password = ref('');
const confirmPassword = ref('');
const studentNo = ref('');
const nickname = ref('');
const loading = ref(false);

async function handleRegister() {
  if (!email.value || !password.value || !nickname.value) {
    message.warning('请填写必填项');
    return;
  }
  if (password.value !== confirmPassword.value) {
    message.warning('两次密码不一致');
    return;
  }
  if (password.value.length < 6) {
    message.warning('密码至少 6 位');
    return;
  }
  loading.value = true;
  try {
    await register({
      email: email.value,
      password: password.value,
      studentNo: studentNo.value || undefined,
      nickname: nickname.value,
    });
    message.success('注册成功，请登录');
    router.push('/login');
  } catch {
    message.error('注册失败');
  } finally {
    loading.value = false;
  }
}
</script>

<template>
  <div class="register-page">
    <NCard title="注册 CampusForum" class="register-card">
      <NForm>
        <NFormItem label="邮箱" required>
          <NInput v-model:value="email" placeholder="请输入邮箱" />
        </NFormItem>
        <NFormItem label="昵称" required>
          <NInput v-model:value="nickname" placeholder="取一个昵称吧" />
        </NFormItem>
        <NFormItem label="学号">
          <NInput v-model:value="studentNo" placeholder="选填" />
        </NFormItem>
        <NFormItem label="密码" required>
          <NInput v-model:value="password" type="password" placeholder="至少 6 位" />
        </NFormItem>
        <NFormItem label="确认密码" required>
          <NInput v-model:value="confirmPassword" type="password" placeholder="再次输入密码" />
        </NFormItem>
        <NButton type="primary" block :loading="loading" @click="handleRegister">
          注册
        </NButton>
      </NForm>
      <div class="link-text">
        <router-link to="/login">已有账号？去登录</router-link>
      </div>
    </NCard>
  </div>
</template>

<style scoped>
.register-page {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 100vh;
  background: #f5f5f5;
}
.register-card {
  width: 440px;
}
.link-text {
  margin-top: 16px;
  text-align: center;
}
</style>
