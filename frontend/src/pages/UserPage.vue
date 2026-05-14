<script setup lang="ts">
import { ref, onMounted } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { NCard, NButton, NSpace, useMessage } from 'naive-ui';
import { getUserById } from '@/api/users';
import { follow, unfollow, isFollowing, getFollowCounts } from '@/api/follows';
import { useAuthStore } from '@/stores/auth';
import type { UserVO } from '@/types/user';

const route = useRoute();
const router = useRouter();
const message = useMessage();
const authStore = useAuthStore();

const user = ref<UserVO | null>(null);
const loading = ref(true);
const following = ref(false);
const followLoading = ref(false);
const followerCount = ref(0);
const followingCount = ref(0);

const currentUserId = authStore.user?.id;

onMounted(async () => {
  const id = Number(route.params.id);
  if (!id) { loading.value = false; return; }
  try {
    const [u, counts] = await Promise.all([
      getUserById(id),
      getFollowCounts(id),
    ]);
    user.value = u;
    followerCount.value = counts.followers;
    followingCount.value = counts.following;

    if (currentUserId && currentUserId !== id) {
      following.value = await isFollowing(id);
    }
  } catch {
    user.value = null;
  }
  loading.value = false;
});

async function toggleFollow() {
  if (!user.value) return;
  followLoading.value = true;
  try {
    if (following.value) {
      await unfollow(user.value.id);
      following.value = false;
      followerCount.value--;
      message.success('已取消关注');
    } else {
      await follow(user.value.id);
      following.value = true;
      followerCount.value++;
      message.success('关注成功');
    }
  } catch {
    message.error('操作失败');
  } finally {
    followLoading.value = false;
  }
}
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
          <div class="stat-item clickable" @click="router.push(`/users/${user.id}/follows`)">
            <span class="stat-value">{{ followerCount }}</span>
            <span class="stat-label">粉丝</span>
          </div>
          <div class="stat-item clickable" @click="router.push(`/users/${user.id}/follows`)">
            <span class="stat-value">{{ followingCount }}</span>
            <span class="stat-label">关注</span>
          </div>
        </div>
        <div v-if="user.bio" class="bio">
          <p>{{ user.bio }}</p>
        </div>
        <NSpace v-if="currentUserId && currentUserId !== user.id" class="actions">
          <NButton
            :type="following ? 'default' : 'primary'"
            :loading="followLoading"
            @click="toggleFollow"
          >
            {{ following ? '已关注' : '关注' }}
          </NButton>
        </NSpace>
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
.stat-item.clickable { cursor: pointer; }
.bio {
  margin: 16px 0;
  padding: 12px;
  background: #f9f9f9;
  border-radius: 8px;
}
.actions { margin-top: 16px; }
.not-found { text-align: center; color: #999; margin-top: 80px; }
</style>
