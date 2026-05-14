<script setup lang="ts">
import { ref, onMounted } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { NTabs, NTabPane, NAvatar, NList, NListItem, NButton, useMessage } from 'naive-ui';
import { getUserFollowers, getUserFollowing, getFollowCounts } from '@/api/follows';
import type { UserVO } from '@/types/user';

const route = useRoute();
const router = useRouter();
const message = useMessage();

const userId = Number(route.params.id);
const userName = ref('');
const activeTab = ref('followers');
const followers = ref<UserVO[]>([]);
const following = ref<UserVO[]>([]);
const followerCount = ref(0);
const followingCount = ref(0);
const loading = ref(true);

onMounted(async () => {
  try {
    const [followerList, followingList, counts] = await Promise.all([
      getUserFollowers(userId),
      getUserFollowing(userId),
      getFollowCounts(userId),
    ]);
    followers.value = followerList;
    following.value = followingList;
    followerCount.value = counts.followers;
    followingCount.value = counts.following;
    if (followingList.length > 0) {
      userName.value = followingList[0].nickname || '';
    }
  } catch {
    message.error('加载失败');
  }
  loading.value = false;
});

function goToUser(id: number) {
  router.push(`/users/${id}`);
}
</script>

<template>
  <div class="follows-page">
    <h2 v-if="userName">{{ userName }} 的关注与粉丝</h2>
    <h2 v-else>关注与粉丝</h2>

    <NTabs v-model:value="activeTab">
      <NTabPane name="followers" :tab="`粉丝 (${followerCount})`">
        <template v-if="!loading && followers.length === 0">
          <p class="empty">暂无粉丝</p>
        </template>
        <NList v-else>
          <NListItem v-for="u in followers" :key="u.id">
            <div class="user-row" @click="goToUser(u.id)">
              <NAvatar :size="36" round>{{ u.nickname?.charAt(0) }}</NAvatar>
              <span class="nickname">{{ u.nickname }}</span>
              <span v-if="u.bio" class="bio">{{ u.bio }}</span>
            </div>
          </NListItem>
        </NList>
      </NTabPane>

      <NTabPane name="following" :tab="`关注 (${followingCount})`">
        <template v-if="!loading && following.length === 0">
          <p class="empty">暂未关注任何人</p>
        </template>
        <NList v-else>
          <NListItem v-for="u in following" :key="u.id">
            <div class="user-row" @click="goToUser(u.id)">
              <NAvatar :size="36" round>{{ u.nickname?.charAt(0) }}</NAvatar>
              <span class="nickname">{{ u.nickname }}</span>
              <span v-if="u.bio" class="bio">{{ u.bio }}</span>
            </div>
          </NListItem>
        </NList>
      </NTabPane>
    </NTabs>
  </div>
</template>

<style scoped>
.follows-page {
  max-width: 500px;
  margin: 24px auto;
  padding: 0 16px;
}
h2 {
  margin: 0 0 8px;
}
.empty {
  text-align: center;
  color: #999;
  margin-top: 32px;
}
.user-row {
  display: flex;
  align-items: center;
  gap: 12px;
  cursor: pointer;
}
.nickname {
  font-weight: 600;
}
.bio {
  color: #999;
  font-size: 13px;
}
</style>
