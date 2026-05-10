<script setup lang="ts">
import { ref, computed, onMounted } from 'vue';
import { useRouter } from 'vue-router';
import { NCard, NButton, NTag, NSpace, NSpin, NEmpty } from 'naive-ui';
import { getChallenges } from '@/api/checkin';
import type { CheckinChallengeVO } from '@/types/checkin';

const router = useRouter();
const challenges = ref<CheckinChallengeVO[]>([]);
const loading = ref(false);
const filter = ref<'all' | 'active' | 'ended'>('active');

async function load() {
  loading.value = true;
  try {
    challenges.value = await getChallenges({ limit: 30 });
  } catch {
    challenges.value = [];
  }
  loading.value = false;
}

const filtered = computed(() => {
  const today = new Date().toISOString().split('T')[0];
  return challenges.value.filter((c) => {
    if (filter.value === 'active') return c.startDate <= today && c.endDate >= today;
    if (filter.value === 'ended') return c.endDate < today;
    return true;
  });
});

function goDetail(id: number) {
  router.push(`/checkin/${id}`);
}

function goCreate() {
  router.push('/checkin/new');
}

onMounted(load);
</script>

<template>
  <div class="challenges-page">
    <div class="page-header">
      <h2>自律打卡</h2>
      <NButton type="primary" @click="goCreate">创建挑战</NButton>
    </div>

    <NSpace class="filter-bar">
      <NButton
        :type="filter === 'all' ? 'primary' : 'default'"
        size="small"
        @click="filter = 'all'"
      >
        全部
      </NButton>
      <NButton
        :type="filter === 'active' ? 'primary' : 'default'"
        size="small"
        @click="filter = 'active'"
      >
        进行中
      </NButton>
      <NButton
        :type="filter === 'ended' ? 'primary' : 'default'"
        size="small"
        @click="filter = 'ended'"
      >
        已结束
      </NButton>
    </NSpace>

    <div v-if="filtered.length === 0 && !loading" class="empty">
      <NEmpty description="暂无打卡挑战" />
    </div>

    <div v-for="c in filtered" :key="c.id" class="challenge-card" @click="goDetail(c.id)">
      <NCard>
        <div class="card-header">
          <div class="challenge-name">
            {{ c.name }}
            <NTag :type="c.endDate < new Date().toISOString().split('T')[0] ? 'default' : 'success'" size="small">
              {{ c.endDate < new Date().toISOString().split('T')[0] ? '已结束' : '进行中' }}
            </NTag>
          </div>
        </div>
        <p v-if="c.description" class="challenge-desc">{{ c.description }}</p>
        <div class="card-footer">
          <span>{{ c.startDate }} ~ {{ c.endDate }}</span>
          <span>{{ c.memberCount }} 人参与</span>
          <span v-if="c.isMember" class="my-stats">
            已打卡 {{ c.myTotalDays }} 天 | 连续 {{ c.myConsecutiveDays }} 天
          </span>
        </div>
      </NCard>
    </div>

    <div v-if="loading" class="loading">
      <NSpin />
    </div>
  </div>
</template>

<style scoped>
.challenges-page {
  max-width: 720px;
  margin: 0 auto;
  padding: 24px 16px;
}
.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}
.page-header h2 { margin: 0; }
.filter-bar { margin-bottom: 20px; }
.empty { margin: 80px 0; }
.challenge-card {
  margin-bottom: 12px;
  cursor: pointer;
}
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}
.challenge-name {
  font-weight: 600;
  font-size: 16px;
  display: flex;
  align-items: center;
  gap: 8px;
}
.challenge-desc {
  color: #666;
  font-size: 14px;
  margin: 0 0 10px;
}
.card-footer {
  display: flex;
  gap: 16px;
  font-size: 13px;
  color: #999;
}
.my-stats { color: #18a058; font-weight: 500; }
.loading { text-align: center; padding: 24px; }
</style>
