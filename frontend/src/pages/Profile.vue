<script setup lang="ts">
import { ref, onMounted } from 'vue';
import { useRoute } from 'vue-router';
import { NCard, NButton, NTag, useMessage } from 'naive-ui';
import { getMe } from '@/api/auth';
import { useAuthStore } from '@/stores/auth';
import type { UserVO } from '@/types/user';

const route = useRoute();
const message = useMessage();
const authStore = useAuthStore();

const user = ref<UserVO | null>(null);
const loading = ref(true);

onMounted(async () => {
  try {
    // 当前查看自己的主页
    user.value = await getMe();
    authStore.setUser(user.value);
  } catch {
    message.error('获取用户信息失败');
  } finally {
    loading.value = false;
  }
});
</script>

<template>
  <div class="profile-page">
    <NCard class="profile-card">
      <template v-if="loading">
        <p>加载中...</p>
      </template>
      <template v-else-if="user">
        <div class="profile-header">
          <div class="avatar">{{ user.nickname.charAt(0) }}</div>
          <div class="info">
            <h2>{{ user.nickname }}</h2>
            <p>{{ user.email }}</p>
            <p v-if="user.studentNo">学号: {{ user.studentNo }}</p>
            <p v-if="user.college || user.major">
              {{ user.college }} {{ user.major }} {{ user.grade }}
            </p>
          </div>
        </div>
        <div class="stats">
          <div class="stat-item">
            <span class="stat-value">{{ user.points }}</span>
            <span class="stat-label">积分</span>
          </div>
        </div>
        <div v-if="user.bio" class="bio">
          <p>{{ user.bio }}</p>
        </div>
        <NTag :type="user.role === 'ADMIN' ? 'error' : 'info'">
          {{ user.role }}
        </NTag>
      </template>
    </NCard>
  </div>
</template>

<style scoped>
.profile-page {
  max-width: 600px;
  margin: 40px auto;
  padding: 0 16px;
}
.profile-header {
  display: flex;
  align-items: center;
  gap: 16px;
}
.avatar {
  width: 64px;
  height: 64px;
  border-radius: 50%;
  background: #18a058;
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 24px;
  font-weight: bold;
}
.info h2 {
  margin: 0 0 4px;
}
.info p {
  margin: 2px 0;
  color: #666;
  font-size: 14px;
}
.stats {
  display: flex;
  gap: 32px;
  margin: 20px 0;
}
.stat-item {
  display: flex;
  flex-direction: column;
  align-items: center;
}
.stat-value {
  font-size: 24px;
  font-weight: bold;
  color: #18a058;
}
.stat-label {
  font-size: 12px;
  color: #999;
}
.bio {
  margin: 16px 0;
  padding: 12px;
  background: #f9f9f9;
  border-radius: 8px;
}
</style>
