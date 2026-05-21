<script setup lang="ts">
import { ref, computed, onMounted } from 'vue';
import { useRouter } from 'vue-router';
import { NSpin, NIcon, NModal, NInput, NDatePicker, NButton, useMessage } from 'naive-ui';
import { 
  CheckmarkCircleOutline,
  FlameOutline,
  CalendarOutline,
  TrophyOutline,
  FootstepsOutline,
  TimeOutline,
  AddOutline,
  ChevronForwardOutline
} from '@vicons/ionicons5';
import { createChallenge, getChallenges } from '@/api/checkin';
import type { CheckinChallengeVO } from '@/types/checkin';

const router = useRouter();
const message = useMessage();
const challenges = ref<CheckinChallengeVO[]>([]);
const loading = ref(false);
const filter = ref<'all' | 'active' | 'ended'>('active');
const createVisible = ref(false);
const createName = ref('');
const createDescription = ref('');
const createRange = ref<[number, number] | null>(null);
const createSubmitting = ref(false);

const filters = [
  { key: 'all', label: '全部挑战', icon: TrophyOutline },
  { key: 'active', label: '进行中', icon: FlameOutline },
  { key: 'ended', label: '已结束', icon: TimeOutline },
] as const;

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
  const today = new Date();
  const end = new Date(today);
  end.setDate(today.getDate() + 30);
  createName.value = '';
  createDescription.value = '';
  createRange.value = [today.getTime(), end.getTime()];
  createVisible.value = true;
}

function formatDate(ts: number) {
  const date = new Date(ts);
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  return `${year}-${month}-${day}`;
}

function closeCreateModal() {
  if (createSubmitting.value) return;
  createVisible.value = false;
}

async function submitCreate() {
  if (!createName.value.trim() || !createRange.value) {
    message.warning('请填写挑战名称和日期范围');
    return;
  }

  const [start, end] = createRange.value;
  if (end < start) {
    message.warning('结束日期不能早于开始日期');
    return;
  }

  createSubmitting.value = true;
  try {
    const challenge = await createChallenge({
      name: createName.value.trim(),
      description: createDescription.value.trim() || undefined,
      startDate: formatDate(start),
      endDate: formatDate(end),
    });
    challenges.value = [challenge, ...challenges.value.filter((item) => item.id !== challenge.id)];
    createVisible.value = false;
    message.success('创建成功');
  } catch {
    message.error('创建失败');
  } finally {
    createSubmitting.value = false;
  }
}

onMounted(load);
</script>

<template>
  <div class="challenges-page">
    <div class="header-banner">
      <div class="banner-content">
        <h1 class="page-title gradient-text">
          <n-icon
            size="32"
            class="title-icon"
          >
            <CheckmarkCircleOutline />
          </n-icon>
          自律打卡
        </h1>
        <p class="page-subtitle">
          每日坚持，记录你的点滴进步
        </p>
      </div>
      <div class="header-actions">
        <button
          class="neon-btn create-btn"
          @click="goCreate"
        >
          <n-icon><AddOutline /></n-icon> 创建挑战
        </button>
      </div>
    </div>

    <div class="main-container">
      <div class="sort-bar glass-card">
        <div 
          v-for="f in filters" 
          :key="f.key"
          class="sort-item"
          :class="{ active: filter === f.key }"
          @click="filter = f.key"
        >
          <n-icon size="18">
            <component :is="f.icon" />
          </n-icon>
          <span>{{ f.label }}</span>
        </div>
      </div>

      <div
        v-if="loading"
        class="loading-state"
      >
        <n-spin size="large" />
      </div>

      <div
        v-else-if="filtered.length === 0"
        class="empty-state glass-card"
      >
        <n-icon
          size="64"
          color="rgba(255,255,255,0.1)"
        >
          <FootstepsOutline />
        </n-icon>
        <h3>暂无挑战</h3>
        <p>当前分类下还没有任何打卡挑战哦</p>
        <button
          class="neon-btn mt-4"
          @click="goCreate"
        >
          发起第一个挑战
        </button>
      </div>

      <div
        v-else
        class="challenges-grid"
      >
        <div 
          v-for="c in filtered" 
          :key="c.id" 
          class="challenge-card glass-card" 
          @click="goDetail(c.id)"
        >
          <div class="card-header">
            <div class="icon-wrapper">
              <n-icon size="28">
                <CalendarOutline />
              </n-icon>
            </div>
            <div class="header-right">
              <div
                class="status-tag"
                :class="c.endDate < new Date().toISOString().split('T')[0] ? 'ended' : 'active'"
              >
                {{ c.endDate < new Date().toISOString().split('T')[0] ? '已结束' : '进行中' }}
              </div>
            </div>
          </div>
          
          <div class="card-body">
            <h3 class="challenge-name">
              {{ c.name }}
            </h3>
            <p class="challenge-desc">
              {{ c.description || '每日打卡，遇见更好的自己！' }}
            </p>
          </div>
          
          <div class="card-footer">
            <div class="time-range">
              <n-icon><TimeOutline /></n-icon>
              <span>{{ c.startDate }} ~ {{ c.endDate }}</span>
            </div>
            
            <div class="stats">
              <span class="member-count">{{ c.memberCount }} 人参与</span>
              <span
                v-if="c.isMember"
                class="my-stats"
              >
                已打卡 <span class="highlight">{{ c.myTotalDays }}</span> 天
              </span>
            </div>
          </div>

          <div class="hover-action">
            <span>查看详情</span>
            <n-icon><ChevronForwardOutline /></n-icon>
          </div>
        </div>
      </div>
    </div>

    <NModal
      v-model:show="createVisible"
      preset="card"
      class="create-challenge-modal"
      title="创建打卡挑战"
      :bordered="false"
      :mask-closable="!createSubmitting"
      @close="closeCreateModal"
    >
      <div class="create-form">
        <label>挑战名称</label>
        <NInput
          v-model:value="createName"
          placeholder="例如：每日背单词"
          maxlength="64"
          show-count
        />

        <label>日期范围</label>
        <NDatePicker
          v-model:value="createRange"
          type="daterange"
          clearable
          :is-date-disabled="(ts: number) => ts < Date.now() - 86400000"
        />

        <label>简介</label>
        <NInput
          v-model:value="createDescription"
          type="textarea"
          placeholder="简单描述一下挑战目标或规则"
          maxlength="500"
          show-count
          :autosize="{ minRows: 4, maxRows: 6 }"
        />

        <div class="create-actions">
          <NButton
            :disabled="createSubmitting"
            @click="closeCreateModal"
          >
            取消
          </NButton>
          <NButton
            type="primary"
            color="#fb923c"
            :loading="createSubmitting"
            @click="submitCreate"
          >
            创建挑战
          </NButton>
        </div>
      </div>
    </NModal>
  </div>
</template>

<style scoped lang="scss">
.challenges-page {
  height: 100%;
  overflow-y: auto;
  background: transparent;
}

.header-banner {
  max-width: 1200px;
  margin: 0 auto;
  padding: 40px 32px 24px;
  display: flex;
  justify-content: space-between;
  align-items: flex-end;

  .banner-content {
    .page-title {
      font-size: 36px;
      font-weight: 800;
      margin: 0 0 8px;
      display: flex;
      align-items: center;
      gap: 12px;
      background: linear-gradient(135deg, #fb923c 0%, #facc15 100%);
      -webkit-background-clip: text;
      -webkit-text-fill-color: transparent;
      
      .title-icon { color: #fb923c; -webkit-text-fill-color: initial; }
    }
    .page-subtitle {
      color: var(--cf-text-secondary);
      font-size: 16px;
      margin: 0;
    }
  }

  .header-actions {
    .create-btn {
      display: flex;
      align-items: center;
      gap: 6px;
      height: 40px;
      padding: 0 24px;
      border-radius: 20px;
      font-weight: bold;
      background: rgba(251, 146, 60, 0.2);
      color: #fb923c;
      border: 1px solid rgba(251, 146, 60, 0.5);
      box-shadow: 0 0 15px rgba(251, 146, 60, 0.1);
      
      &:hover {
        background: rgba(251, 146, 60, 0.3);
        box-shadow: 0 0 20px rgba(251, 146, 60, 0.4);
      }
    }
  }
}

.main-container {
  max-width: 1200px;
  margin: 0 auto;
  padding: 0 32px 40px;
}

.sort-bar {
  display: flex;
  padding: 8px;
  gap: 8px;
  background: rgba(22, 27, 34, 0.5);
  border-radius: 16px;
  margin-bottom: 24px;
  flex-wrap: wrap;

  .sort-item {
    flex: 1;
    min-width: 120px;
    display: flex;
    align-items: center;
    justify-content: center;
    gap: 8px;
    padding: 12px 16px;
    border-radius: 12px;
    color: var(--cf-text-secondary);
    cursor: pointer;
    font-weight: 500;
    transition: all 0.3s;

    &:hover {
      background: rgba(255, 255, 255, 0.05);
      color: var(--cf-text-primary);
    }

    &.active {
      background: rgba(251, 146, 60, 0.15);
      color: #fb923c;
    }
  }
}

.loading-state {
  padding: 60px;
  display: flex;
  justify-content: center;
}

.empty-state {
  padding: 80px 0;
  text-align: center;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  
  h3 { margin: 20px 0 8px; color: var(--cf-text-primary); font-size: 20px; }
  p { margin: 0; color: var(--cf-text-secondary); font-size: 15px; }
  .mt-4 { 
    margin-top: 24px; 
    background: rgba(251, 146, 60, 0.2);
    color: #fb923c;
    border-color: rgba(251, 146, 60, 0.5);
  }
}

.challenges-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(340px, 1fr));
  gap: 24px;
}

.challenge-card {
  padding: 24px;
  display: flex;
  flex-direction: column;
  cursor: pointer;
  position: relative;
  overflow: hidden;
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  
  &:hover {
    transform: translateY(-4px);
    box-shadow: 0 12px 24px rgba(0, 0, 0, 0.2);
    border-color: rgba(251, 146, 60, 0.3);
    
    .hover-action {
      opacity: 1;
      transform: translateY(0);
    }
  }

  .card-header {
    display: flex;
    justify-content: space-between;
    align-items: flex-start;
    margin-bottom: 20px;

    .icon-wrapper {
      width: 50px;
      height: 50px;
      border-radius: 14px;
      display: flex;
      align-items: center;
      justify-content: center;
      background: rgba(251, 146, 60, 0.15);
      color: #fb923c;
    }

    .header-right {
      .status-tag {
        padding: 4px 12px;
        border-radius: 20px;
        font-size: 12px;
        font-weight: bold;
        border: 1px solid transparent;
        
        &.active {
          color: #10b981;
          background: rgba(16, 185, 129, 0.1);
          border-color: rgba(16, 185, 129, 0.3);
        }
        
        &.ended {
          color: var(--cf-text-secondary);
          background: rgba(255, 255, 255, 0.05);
          border-color: rgba(255, 255, 255, 0.1);
        }
      }
    }
  }

  .card-body {
    flex: 1;
    margin-bottom: 24px;

    .challenge-name {
      font-size: 18px;
      font-weight: 700;
      color: var(--cf-text-primary);
      margin: 0 0 12px;
      line-height: 1.3;
    }

    .challenge-desc {
      color: var(--cf-text-secondary);
      font-size: 14px;
      line-height: 1.6;
      margin: 0;
      display: -webkit-box;
      -webkit-line-clamp: 2;
      -webkit-box-orient: vertical;
      overflow: hidden;
    }
  }

  .card-footer {
    display: flex;
    flex-direction: column;
    gap: 12px;
    padding-top: 16px;
    border-top: 1px solid rgba(255, 255, 255, 0.05);

    .time-range {
      display: flex;
      align-items: center;
      gap: 6px;
      font-size: 13px;
      color: var(--cf-text-secondary);
    }

    .stats {
      display: flex;
      justify-content: space-between;
      align-items: center;
      font-size: 13px;
      
      .member-count {
        color: var(--cf-text-muted);
      }
      
      .my-stats {
        color: var(--cf-text-primary);
        background: rgba(255, 255, 255, 0.05);
        padding: 4px 10px;
        border-radius: 12px;
        
        .highlight {
          color: #fb923c;
          font-weight: bold;
          font-size: 14px;
        }
      }
    }
  }

  .hover-action {
    position: absolute;
    bottom: 0;
    left: 0;
    right: 0;
    padding: 12px;
    background: linear-gradient(to top, rgba(251, 146, 60, 0.9), rgba(251, 146, 60, 0));
    color: white;
    display: flex;
    justify-content: center;
    align-items: center;
    gap: 8px;
    font-size: 14px;
    font-weight: 500;
    opacity: 0;
    transform: translateY(100%);
    transition: all 0.3s ease;
  }
}

:global(.create-challenge-modal) {
  width: min(520px, calc(100vw - 32px));
  border-radius: 18px;
}

.create-form {
  display: flex;
  flex-direction: column;
  gap: 12px;

  label {
    color: var(--cf-text-secondary);
    font-size: 14px;
    font-weight: 600;
  }
}

.create-actions {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  margin-top: 8px;
}
</style>
