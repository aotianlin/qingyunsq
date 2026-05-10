<script setup lang="ts">
import { ref, onMounted } from 'vue';
import { NCard, NGrid, NGridItem, NSpin, NStatistic } from 'naive-ui';
import { getDashboard } from '@/api/admin';
import type { DashboardVO } from '@/types/admin';

const stats = ref<DashboardVO | null>(null);
const loading = ref(true);

onMounted(async () => {
  try {
    stats.value = await getDashboard();
  } catch {
    // ignore
  }
  loading.value = false;
});
</script>

<template>
  <div style="padding: 24px;">
    <h2 style="margin-bottom: 24px;">仪表盘</h2>
    <NSpin :show="loading">
      <NGrid v-if="stats" :cols="4" :x-gap="16" :y-gap="16">
        <NGridItem>
          <NCard>
            <NStatistic label="用户总数" :value="stats.userCount" />
          </NCard>
        </NGridItem>
        <NGridItem>
          <NCard>
            <NStatistic label="帖子总数" :value="stats.postCount" />
          </NCard>
        </NGridItem>
        <NGridItem>
          <NCard>
            <NStatistic label="空间总数" :value="stats.spaceCount" />
          </NCard>
        </NGridItem>
        <NGridItem>
          <NCard>
            <NStatistic label="评论总数" :value="stats.commentCount" />
          </NCard>
        </NGridItem>
        <NGridItem>
          <NCard>
            <NStatistic label="今日新增用户" :value="stats.todayUserCount" />
          </NCard>
        </NGridItem>
        <NGridItem>
          <NCard>
            <NStatistic label="今日新增帖子" :value="stats.todayPostCount" />
          </NCard>
        </NGridItem>
      </NGrid>
    </NSpin>
  </div>
</template>
