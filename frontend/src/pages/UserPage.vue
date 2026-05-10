<script setup lang="ts">
import { ref, onMounted } from 'vue';
import { useRoute } from 'vue-router';
import { NCard, NTag } from 'naive-ui';
import { getUserById } from '@/api/users';
import type { UserVO } from '@/types/user';

const route = useRoute();
const user = ref<UserVO | null>(null);
const loading = ref(true);

onMounted(async () => {
  const id = Number(route.params.id);
  if (id) {
    try {
      user.value = await getUserById(id);
    } catch {
      user.value = null;
    }
  }
  loading.value = false;
});
</script>

<template>
  <div class="user-page">
    <template v-if="loading">
      <p>加载中...</p>
    </template>
    <template v-else-if="user">
      <NCard class="profile-card">
        <div class="profile-header">
          <div class="avatar">{{ user.nickname.charAt(0) }}</div>
          <div class="info">
            <h2>{{ user.nickname }}</h2>
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
      </NCard>
    </template>
    <template v-else>
      <p class="not-found">用户不存在</p>
    </template>
  </div>
</template>

<style scoped>
.user-page {
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
.info h2 { margin: 0 0 4px; }
.info p { margin: 2px 0; color: #666; font-size: 14px; }
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
.stat-value { font-size: 24px; font-weight: bold; color: #18a058; }
.stat-label { font-size: 12px; color: #999; }
.bio {
  margin: 16px 0;
  padding: 12px;
  background: #f9f9f9;
  border-radius: 8px;
}
.not-found { text-align: center; color: #999; margin-top: 80px; }
</style>
