<script setup lang="ts">
import { computed, ref, onMounted, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { NTabs, NTabPane, NAvatar, NList, NListItem, useMessage } from 'naive-ui';
import { getUserById } from '@/api/users';
import { getUserFollowers, getUserFollowing, getFollowCounts } from '@/api/follows';
import type { UserVO } from '@/types/user';

const route = useRoute();
const router = useRouter();
const message = useMessage();

const userId = Number(route.params.id);
const userName = ref('');
const activeTab = ref<'followers' | 'following'>('followers');
const followers = ref<UserVO[]>([]);
const following = ref<UserVO[]>([]);
const followerCount = ref(0);
const followingCount = ref(0);
const loading = ref(true);
const pageTitle = computed(() => {
  if (!userName.value) return '关注与粉丝';
  return `${userName.value} 的关注与粉丝`;
});

function syncTabFromRoute() {
  const tab = route.query.tab;
  if (tab === 'following') {
    activeTab.value = 'following';
  } else {
    activeTab.value = 'followers';
  }
}

onMounted(async () => {
  try {
    const [followerList, followingList, counts, profile] = await Promise.all([
      getUserFollowers(userId),
      getUserFollowing(userId),
      getFollowCounts(userId),
      getUserById(userId),
    ]);
    followers.value = followerList;
    following.value = followingList;
    followerCount.value = counts.followers;
    followingCount.value = counts.following;
    userName.value = profile.nickname || '';
  } catch {
    message.error('加载失败');
  }
  loading.value = false;
  syncTabFromRoute();
});

function goToUser(id: number) {
  router.push(`/users/${id}`);
}

watch(
  () => route.query.tab,
  () => {
    syncTabFromRoute();
  },
);
</script>

<template>
  <div class="follows-page">
    <section class="follows-head">
      <div>
        <span>Connections</span>
        <h1>{{ pageTitle }}</h1>
      </div>
    </section>

    <NTabs v-model:value="activeTab" class="follows-card">
      <NTabPane
        name="followers"
        :tab="`粉丝 (${followerCount})`"
      >
        <template v-if="!loading && followers.length === 0">
          <p class="empty">
            暂无粉丝
          </p>
        </template>
        <NList v-else>
          <NListItem
            v-for="u in followers"
            :key="u.id"
          >
            <div
              class="user-row"
              @click="goToUser(u.id)"
            >
              <NAvatar
                :size="36"
                round
              >
                {{ u.nickname?.charAt(0) }}
              </NAvatar>
              <span class="nickname">{{ u.nickname }}</span>
              <span
                v-if="u.bio"
                class="bio"
              >{{ u.bio }}</span>
            </div>
          </NListItem>
        </NList>
      </NTabPane>

      <NTabPane
        name="following"
        :tab="`关注 (${followingCount})`"
      >
        <template v-if="!loading && following.length === 0">
          <p class="empty">
            暂未关注任何人
          </p>
        </template>
        <NList v-else>
          <NListItem
            v-for="u in following"
            :key="u.id"
          >
            <div
              class="user-row"
              @click="goToUser(u.id)"
            >
              <NAvatar
                :size="36"
                round
              >
                {{ u.nickname?.charAt(0) }}
              </NAvatar>
              <span class="nickname">{{ u.nickname }}</span>
              <span
                v-if="u.bio"
                class="bio"
              >{{ u.bio }}</span>
            </div>
          </NListItem>
        </NList>
      </NTabPane>
    </NTabs>
  </div>
</template>

<style scoped lang="scss">
.follows-page {
  min-height: calc(100vh - 112px);
  padding: 8px 0 40px;
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.follows-head,
.follows-card {
  width: min(100%, 880px);
  margin: 0 auto;
  background: var(--cf-card-bg);
  border: 1px solid var(--cf-card-border);
  border-radius: 20px;
  box-shadow: var(--cf-card-shadow);
  backdrop-filter: blur(24px) saturate(150%);
}

.follows-head {
  padding: 24px;

  span {
    color: var(--cf-primary);
    font-size: 13px;
    font-weight: 900;
  }

  h1 {
    margin: 8px 0 0;
    font-size: 28px;
    line-height: 1.25;
  }
}

.follows-card {
  padding: 10px 18px 18px;

  :deep(.n-tabs-tab) {
    font-weight: 850;
  }

  :deep(.n-list) {
    background: transparent;
  }

  :deep(.n-list-item) {
    padding: 0;
    border: 0;
  }
}

.empty {
  text-align: center;
  color: var(--cf-text-muted);
  margin: 40px 0;
}

.user-row {
  min-height: 66px;
  padding: 12px;
  border-radius: 14px;
  display: flex;
  align-items: center;
  gap: 12px;
  cursor: pointer;
  transition: background 0.2s ease;

  &:hover {
    background: color-mix(in srgb, var(--cf-primary) 7%, transparent);
  }
}

.nickname {
  font-weight: 850;
  color: var(--cf-text-primary);
}

.bio {
  min-width: 0;
  color: var(--cf-text-muted);
  font-size: 13px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
