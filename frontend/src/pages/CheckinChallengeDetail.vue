<script setup lang="ts">
import { ref, onMounted, computed } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { NCard, NButton, NTag, NSpin, NInput, NEmpty, NIcon, useMessage } from 'naive-ui';
import { getChallengeById, checkin, getRecords, getLeaderboard, deleteChallenge, shareCheckinRecord } from '@/api/checkin';
import { useAuthStore } from '@/stores/auth';
import type { CheckinChallengeVO, CheckinRecordVO, LeaderboardEntry } from '@/types/checkin';
import {
  ArrowBackOutline,
  CalendarOutline,
  CheckmarkCircleOutline,
  PeopleOutline,
  SendOutline,
  ShareSocialOutline,
  TrashOutline,
  TrophyOutline,
} from '@vicons/ionicons5';

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
  } catch (e) {
    message.error(e instanceof Error ? e.message : '打卡失败');
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
      <div class="loading">
        <NSpin />
      </div>
    </template>

    <template v-else-if="challenge">
      <section class="detail-hero">
        <button
          class="back-button"
          @click="goBack"
        >
          <NIcon size="18"><ArrowBackOutline /></NIcon>
        </button>

        <div class="hero-copy">
          <div class="hero-kicker">
            <span>Check-in Challenge</span>
            <NTag
              :type="isActive ? 'success' : 'default'"
              size="small"
              round
            >
              {{ isActive ? '进行中' : '已结束' }}
            </NTag>
          </div>
          <h1>{{ challenge.name }}</h1>
          <p>{{ challenge.description || '这个挑战还没有填写说明，先从今天的一次打卡开始。' }}</p>
        </div>

        <div class="hero-actions">
          <NButton
            v-if="isCreator"
            type="error"
            secondary
            @click="handleDelete"
          >
            <template #icon>
              <NIcon><TrashOutline /></NIcon>
            </template>
            删除
          </NButton>
        </div>
      </section>

      <div class="detail-grid">
        <main class="main-column">
          <section class="summary-card soft-card">
            <div class="summary-item">
              <NIcon size="20"><CalendarOutline /></NIcon>
              <div>
                <span>挑战周期</span>
                <strong>{{ challenge.startDate }} ~ {{ challenge.endDate }}</strong>
              </div>
            </div>
            <div class="summary-item">
              <NIcon size="20"><PeopleOutline /></NIcon>
              <div>
                <span>参与人数</span>
                <strong>{{ challenge.memberCount }} 人</strong>
              </div>
            </div>
            <div class="summary-item">
              <NIcon size="20"><CheckmarkCircleOutline /></NIcon>
              <div>
                <span>创建者</span>
                <strong>{{ challenge.creator?.nickname || '未知' }}</strong>
              </div>
            </div>
          </section>

          <section
            v-if="challenge.isMember"
            class="my-stats soft-card"
          >
            <div class="stat-item">
              <span class="stat-num">{{ challenge.myTotalDays }}</span>
              <span class="stat-label">总打卡</span>
            </div>
            <div class="stat-item">
              <span class="stat-num">{{ challenge.myConsecutiveDays }}</span>
              <span class="stat-label">连续天数</span>
            </div>
            <div class="stat-note">
              保持节奏，今天的一条记录会让这个挑战更完整。
            </div>
          </section>

          <section
            v-if="isActive"
            class="checkin-card soft-card"
          >
            <div class="section-head">
              <div>
                <span class="section-kicker">Today</span>
                <h2>{{ challenge.isMember ? '今日打卡' : '首次打卡' }}</h2>
              </div>
            </div>
            <div class="checkin-form">
              <NInput
                v-model:value="checkinContent"
                type="textarea"
                :placeholder="challenge.isMember ? '记录今天的打卡内容...' : '写下你的第一条打卡...'"
                :autosize="{ minRows: 3, maxRows: 5 }"
              />
              <NButton
                type="primary"
                :loading="submitting"
                class="checkin-btn"
                @click="handleCheckin"
              >
                <template #icon>
                  <NIcon><SendOutline /></NIcon>
                </template>
                {{ challenge.isMember ? '打卡' : '加入并打卡' }}
              </NButton>
            </div>
          </section>

          <section class="records-card soft-card">
            <div class="section-head">
              <div>
                <span class="section-kicker">Recent</span>
                <h2>最近打卡</h2>
              </div>
              <span class="count-text">{{ records.length }} 条</span>
            </div>

            <div
              v-if="records.length === 0"
              class="no-data"
            >
              暂无记录
            </div>
            <div
              v-for="r in records"
              :key="r.id"
              class="record-item"
            >
              <div class="record-avatar">
                {{ r.user?.nickname?.charAt(0) || '?' }}
              </div>
              <div class="record-body">
                <div class="record-header">
                  <span class="record-user">{{ r.user?.nickname || '未知' }}</span>
                  <span class="record-date">{{ r.checkinDate }}</span>
                </div>
                <p
                  v-if="r.content"
                  class="record-content"
                >
                  {{ r.content }}
                </p>
                <div class="record-actions">
                  <NTag
                    v-if="r.aiCheck === 0"
                    type="warning"
                    size="tiny"
                  >
                    AI 提醒：内容可能不符合挑战主题
                  </NTag>
                  <NTag
                    v-else-if="r.aiCheck === 1"
                    type="success"
                    size="tiny"
                  >
                    AI 检测：内容符合主题
                  </NTag>
                  <NButton
                    v-if="r.userId === currentUserId"
                    size="tiny"
                    text
                    type="info"
                    @click="handleShare(r.id)"
                  >
                    <template #icon>
                      <NIcon><ShareSocialOutline /></NIcon>
                    </template>
                    分享到广场
                  </NButton>
                </div>
              </div>
            </div>
          </section>
        </main>

        <aside class="side-column">
          <section class="leaderboard-card soft-card">
            <div class="section-head">
              <div>
                <span class="section-kicker">Rank</span>
                <h2>排行榜</h2>
              </div>
              <NIcon size="22"><TrophyOutline /></NIcon>
            </div>
            <div
              v-if="leaderboard.length === 0"
              class="no-data"
            >
              暂无数据
            </div>
            <div
              v-for="(entry, i) in leaderboard"
              :key="entry.userId"
              class="lb-item"
            >
              <span
                class="lb-rank"
                :class="{ top: i < 3 }"
              >{{ i + 1 }}</span>
              <div class="lb-avatar">
                {{ entry.userName?.charAt(0) || '?' }}
              </div>
              <span class="lb-name">{{ entry.userName }}</span>
              <span class="lb-stats">{{ entry.totalDays }} 天</span>
              <small>连续 {{ entry.currentStreak }} 天</small>
            </div>
          </section>

          <section class="tip-card soft-card">
            <span>体验提示</span>
            <p>记录内容越具体，回看时越能看清自己的进步，也更适合分享到广场。</p>
          </section>
        </aside>
      </div>
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
  width: 100%;
  padding: 8px 0 40px;
  color: var(--cf-text-primary);
}

.loading,
.empty {
  min-height: 360px;
  display: grid;
  place-items: center;
}

.detail-hero,
.soft-card {
  background: var(--cf-card-bg);
  border: 1px solid var(--cf-card-border);
  border-radius: 20px;
  box-shadow: var(--cf-card-shadow);
  backdrop-filter: blur(24px) saturate(150%);
}

.detail-hero {
  display: flex;
  align-items: center;
  gap: 18px;
  padding: 26px;
  margin-bottom: 24px;
}

.back-button {
  width: 44px;
  height: 44px;
  border: 1px solid var(--cf-border);
  border-radius: 14px;
  background: var(--cf-bg-elevated);
  color: var(--cf-text-secondary);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: all 0.22s var(--cf-motion-ease);
}

.back-button:hover {
  color: var(--cf-primary);
  border-color: var(--cf-primary);
  background: var(--cf-primary-soft);
  transform: translateY(-1px);
}

.hero-copy {
  flex: 1;
  min-width: 0;
}

.hero-kicker {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 8px;
  color: var(--cf-primary);
  font-size: 13px;
  font-weight: 800;
  letter-spacing: 0;
}

.hero-copy h1 {
  margin: 0;
  font-family: var(--cf-font-heading);
  font-size: 34px;
  line-height: 1.18;
}

.hero-copy p {
  margin: 10px 0 0;
  max-width: 760px;
  color: var(--cf-text-secondary);
  line-height: 1.8;
}

.hero-actions {
  display: flex;
  gap: 10px;
  flex-shrink: 0;
}

.detail-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 340px;
  gap: 24px;
  align-items: start;
}

.main-column,
.side-column {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.summary-card {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
  padding: 18px;
}

.summary-item {
  min-width: 0;
  padding: 14px;
  border-radius: 16px;
  background: color-mix(in srgb, var(--cf-bg-soft) 78%, var(--cf-bg-card));
  border: 1px solid var(--cf-border);
  display: flex;
  align-items: center;
  gap: 12px;
  color: var(--cf-primary);
}

.summary-item div {
  min-width: 0;
}

.summary-item span,
.section-kicker,
.count-text,
.tip-card span {
  color: var(--cf-text-muted);
  font-size: 12px;
  font-weight: 800;
}

.summary-item strong {
  display: block;
  margin-top: 4px;
  color: var(--cf-text-primary);
  font-size: 14px;
  overflow-wrap: anywhere;
}

.my-stats {
  display: flex;
  align-items: center;
  gap: 18px;
  padding: 18px;
}

.stat-item {
  width: 118px;
  min-height: 88px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  border-radius: 16px;
  background: var(--cf-primary-soft);
  border: 1px solid color-mix(in srgb, var(--cf-primary) 22%, transparent);
}

.stat-num {
  font-size: 30px;
  font-weight: 900;
  color: var(--cf-primary);
  line-height: 1;
}

.stat-label {
  font-size: 12px;
  color: var(--cf-text-muted);
  margin-top: 8px;
}

.stat-note {
  flex: 1;
  color: var(--cf-text-secondary);
  line-height: 1.7;
}

.checkin-card,
.records-card,
.leaderboard-card,
.tip-card {
  padding: 22px;
}

.section-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 14px;
  margin-bottom: 18px;
}

.section-head h2 {
  margin: 4px 0 0;
  font-family: var(--cf-font-heading);
  font-size: 22px;
}

.checkin-form {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.checkin-btn {
  align-self: flex-end;
}

.checkin-form :deep(.n-input) {
  --n-border: 1px solid var(--cf-border) !important;
  --n-border-hover: 1px solid color-mix(in srgb, var(--cf-primary) 42%, transparent) !important;
  --n-border-focus: 1px solid var(--cf-primary) !important;
  --n-box-shadow-focus: 0 0 0 4px color-mix(in srgb, var(--cf-primary) 12%, transparent) !important;
}

.no-data {
  text-align: center;
  color: var(--cf-text-muted);
  padding: 30px 0;
}

.lb-item {
  display: grid;
  grid-template-columns: 28px 36px minmax(0, 1fr) auto;
  align-items: center;
  gap: 10px;
  padding: 12px 0;
  border-bottom: 1px solid var(--cf-border);
}

.lb-item:last-child,
.record-item:last-child {
  border-bottom: 0;
}

.lb-rank {
  text-align: center;
  font-weight: 900;
  color: var(--cf-text-muted);
}

.lb-rank.top {
  color: var(--cf-warning);
}

.lb-avatar {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  background: linear-gradient(135deg, var(--cf-primary), #00a88f);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 13px;
  font-weight: 800;
}

.lb-name {
  min-width: 0;
  font-size: 14px;
  font-weight: 700;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.lb-stats {
  font-size: 13px;
  color: var(--cf-primary);
  font-weight: 800;
}

.lb-item small {
  grid-column: 3 / 5;
  color: var(--cf-text-muted);
}

.record-item {
  display: flex;
  gap: 12px;
  padding: 14px 0;
  border-bottom: 1px solid var(--cf-border);
}

.record-avatar {
  width: 40px;
  height: 40px;
  border-radius: 50%;
  background: color-mix(in srgb, var(--cf-accent-sky) 72%, var(--cf-primary));
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 13px;
  font-weight: 800;
  flex-shrink: 0;
}

.record-body {
  flex: 1;
  min-width: 0;
}

.record-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  margin-bottom: 4px;
}

.record-user {
  font-size: 14px;
  font-weight: 800;
}

.record-date {
  font-size: 12px;
  color: var(--cf-text-muted);
  flex-shrink: 0;
}

.record-content {
  font-size: 14px;
  color: var(--cf-text-secondary);
  margin: 0;
  line-height: 1.7;
  overflow-wrap: anywhere;
}

.record-actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  margin-top: 8px;
}

.tip-card span {
  color: var(--cf-primary);
}

.tip-card p {
  margin: 8px 0 0;
  color: var(--cf-text-secondary);
  line-height: 1.8;
}

@media (max-width: 1080px) {
  .detail-grid {
    grid-template-columns: 1fr;
  }

  .side-column {
    display: grid;
    grid-template-columns: minmax(0, 1fr) 280px;
  }
}

@media (max-width: 760px) {
  .detail-page {
    padding-bottom: 28px;
  }

  .detail-hero {
    align-items: flex-start;
    flex-wrap: wrap;
    padding: 20px;
  }

  .hero-copy h1 {
    font-size: 28px;
  }

  .hero-actions {
    width: 100%;
  }

  .summary-card,
  .side-column {
    grid-template-columns: 1fr;
  }

  .my-stats {
    align-items: stretch;
    flex-direction: column;
  }

  .stat-item {
    width: 100%;
  }

  .record-header {
    align-items: flex-start;
    flex-direction: column;
  }
}
</style>
