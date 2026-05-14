<script setup lang="ts">
import { ref, onMounted, computed } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { NCard, NButton, NTag, NSpace, NSpin, NInput, NEmpty, useMessage } from 'naive-ui';
import { getChallengeById, checkin, getRecords, getLeaderboard, deleteChallenge, shareCheckinRecord } from '@/api/checkin';
import { useAuthStore } from '@/stores/auth';
import type { CheckinChallengeVO, CheckinRecordVO, LeaderboardEntry } from '@/types/checkin';

const route = useRoute();
const router = useRouter();
const message = useMessage();
const authStore = useAuthStore();

const challenge = ref<CheckinChallengeVO | null>(null);
const records = ref<CheckinRecordVO[]>([]);
const leaderboard = ref<LeaderboardEntry[]>([]);
const loading = ref(true);
const checkinContent = ref('');
const submitting = ref(false);
const currentUserId = authStore.user?.id;

const today = new Date().toISOString().split('T')[0];
const isActive = computed(() => {
  if (!challenge.value) return false;
  return challenge.value.startDate <= today && challenge.value.endDate >= today;
});
const isCreator = computed(() => challenge.value?.creatorId === currentUserId);

async function load() {
  loading.value = true;
  try {
    const id = Number(route.params.id);
    challenge.value = await getChallengeById(id);
    records.value = await getRecords(id, undefined, 30);
    leaderboard.value = await getLeaderboard(id);
  } catch {
    challenge.value = null;
  }
  loading.value = false;
}

async function handleCheckin() {
  if (!challenge.value) return;
  submitting.value = true;
  try {
    await checkin(challenge.value.id, {
      content: checkinContent.value.trim() || undefined,
    });
    message.success('打卡成功');
    checkinContent.value = '';
    load();
  } catch (e: any) {
    message.error(e.message || '打卡失败');
  }
  submitting.value = false;
}

async function handleDelete() {
  if (!challenge.value) return;
  try {
    await deleteChallenge(challenge.value.id);
    message.success('挑战已删除');
    router.replace('/checkin');
  } catch {
    message.error('删除失败');
  }
}

async function handleShare(recordId: number) {
  try {
    const res = await shareCheckinRecord(recordId);
    message.success('已分享到广场');
    router.push(`/posts/${res.postId}`);
  } catch {
    message.error('分享失败');
  }
}

function goBack() {
  router.push('/checkin');
}

onMounted(load);
</script>

<template>
  <div class="detail-page">
    <template v-if="loading">
      <div class="loading"><NSpin /></div>
    </template>

    <template v-else-if="challenge">
      <NCard class="challenge-info">
        <div class="info-header">
          <div>
            <h2>{{ challenge.name }}</h2>
            <NTag :type="isActive ? 'success' : 'default'" size="small">
              {{ isActive ? '进行中' : '已结束' }}
            </NTag>
          </div>
          <div class="header-actions">
            <NButton size="small" @click="goBack">返回列表</NButton>
            <NButton v-if="isCreator" type="error" size="small" @click="handleDelete">删除</NButton>
          </div>
        </div>
        <p v-if="challenge.description" class="challenge-desc">{{ challenge.description }}</p>
        <div class="stats">
          <span>{{ challenge.startDate }} ~ {{ challenge.endDate }}</span>
          <span>{{ challenge.memberCount }} 人参与</span>
          <span>创建者: {{ challenge.creator?.nickname || '未知' }}</span>
        </div>

        <!-- 我的统计 -->
        <div v-if="challenge.isMember" class="my-stats">
          <div class="stat-item">
            <span class="stat-num">{{ challenge.myTotalDays }}</span>
            <span class="stat-label">总打卡</span>
          </div>
          <div class="stat-item">
            <span class="stat-num">{{ challenge.myConsecutiveDays }}</span>
            <span class="stat-label">连续天数</span>
          </div>
        </div>
      </NCard>

      <!-- 今日打卡 -->
      <NCard v-if="isActive && challenge.isMember" title="今日打卡" class="checkin-card">
        <div class="checkin-form">
          <NInput
            v-model:value="checkinContent"
            type="textarea"
            placeholder="记录今天的打卡内容..."
            :autosize="{ minRows: 2, maxRows: 4 }"
          />
          <NButton type="primary" :loading="submitting" @click="handleCheckin" class="checkin-btn">
            打卡
          </NButton>
        </div>
      </NCard>
      <div v-else-if="isActive && !challenge.isMember" class="checkin-hint">
        <NCard>
          <p>开始你的第一次打卡，加入这个挑战吧！</p>
          <div class="checkin-form">
            <NInput
              v-model:value="checkinContent"
              type="textarea"
              placeholder="写下你的第一条打卡..."
              :autosize="{ minRows: 2, maxRows: 4 }"
            />
            <NButton type="primary" :loading="submitting" @click="handleCheckin" class="checkin-btn">
              首次打卡
            </NButton>
          </div>
        </NCard>
      </div>

      <!-- 排行榜 -->
      <NCard title="排行榜" class="leaderboard-card">
        <div v-if="leaderboard.length === 0" class="no-data">暂无数据</div>
        <div v-for="(entry, i) in leaderboard" :key="entry.userId" class="lb-item">
          <span class="lb-rank" :class="{ top: i < 3 }">{{ i + 1 }}</span>
          <div class="lb-avatar">{{ entry.userName?.charAt(0) || '?' }}</div>
          <span class="lb-name">{{ entry.userName }}</span>
          <span class="lb-stats">{{ entry.totalDays }} 天 | 连续 {{ entry.currentStreak }} 天</span>
        </div>
      </NCard>

      <!-- 打卡记录 -->
      <NCard title="最近打卡" class="records-card">
        <div v-if="records.length === 0" class="no-data">暂无记录</div>
        <div v-for="r in records" :key="r.id" class="record-item">
          <div class="record-avatar">{{ r.user?.nickname?.charAt(0) || '?' }}</div>
          <div class="record-body">
            <div class="record-header">
              <span class="record-user">{{ r.user?.nickname || '未知' }}</span>
              <span class="record-date">{{ r.checkinDate }}</span>
            </div>
            <p v-if="r.content" class="record-content">{{ r.content }}</p>
            <NButton
              v-if="r.userId === currentUserId"
              size="tiny"
              text
              type="info"
              @click="handleShare(r.id)"
            >
              分享到广场
            </NButton>
          </div>
        </div>
      </NCard>
    </template>

    <template v-else>
      <div class="empty">
        <NEmpty description="挑战不存在" />
      </div>
    </template>
  </div>
</template>

<style scoped>
.detail-page {
  max-width: 720px;
  margin: 40px auto;
  padding: 0 16px;
}
.loading { text-align: center; padding: 80px; }
.empty { margin: 80px 0; }
.info-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 12px;
}
.info-header h2 { margin: 0 0 4px; }
.header-actions { display: flex; gap: 8px; }
.challenge-desc { color: #666; margin-bottom: 12px; }
.stats { display: flex; gap: 16px; font-size: 14px; color: #999; margin-bottom: 16px; }
.my-stats {
  display: flex;
  gap: 24px;
  padding: 16px;
  background: #f6ffed;
  border-radius: 8px;
  margin-bottom: -8px;
}
.stat-item {
  display: flex;
  flex-direction: column;
  align-items: center;
}
.stat-num {
  font-size: 24px;
  font-weight: 700;
  color: #18a058;
}
.stat-label {
  font-size: 12px;
  color: #999;
  margin-top: 4px;
}
.checkin-card, .leaderboard-card, .records-card {
  margin-top: 20px;
}
.checkin-form {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.checkin-btn {
  align-self: flex-end;
}
.checkin-hint {
  margin-top: 20px;
}
.checkin-hint p {
  margin: 0 0 12px;
  color: #666;
  font-size: 14px;
}
.no-data { text-align: center; color: #999; padding: 24px; }
.lb-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 0;
  border-bottom: 1px solid #f0f0f0;
}
.lb-rank {
  width: 24px;
  text-align: center;
  font-weight: 600;
  color: #999;
}
.lb-rank.top { color: #f0a020; }
.lb-avatar {
  width: 32px; height: 32px;
  border-radius: 50%;
  background: #18a058;
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 13px;
}
.lb-name { flex: 1; font-size: 14px; }
.lb-stats { font-size: 13px; color: #18a058; }
.record-item {
  display: flex;
  gap: 10px;
  padding: 10px 0;
  border-bottom: 1px solid #f0f0f0;
}
.record-avatar {
  width: 32px; height: 32px;
  border-radius: 50%;
  background: #2080f0;
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 13px;
  flex-shrink: 0;
}
.record-body { flex: 1; min-width: 0; }
.record-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 4px;
}
.record-user { font-size: 14px; font-weight: 500; }
.record-date { font-size: 12px; color: #999; }
.record-content {
  font-size: 14px;
  color: #666;
  margin: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
