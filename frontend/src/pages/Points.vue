<script setup lang="ts">
import { ref, onMounted } from 'vue';
import { useRouter } from 'vue-router';
import { NSpin, NIcon } from 'naive-ui';
import { 
  ArrowBackOutline,
  StarOutline,
  LogInOutline,
  DocumentTextOutline,
  HeartOutline,
  CheckmarkCircleOutline,
  CalendarOutline,
  GiftOutline,
  TrendingUpOutline,
  TrendingDownOutline,
  TrophyOutline
} from '@vicons/ionicons5';
import { getBalance, getPointsLogs } from '@/api/points';
import type { PointsLogVO } from '@/types/points';
import type { Component } from 'vue';

const router = useRouter();
const balance = ref(0);
const logs = ref<PointsLogVO[]>([]);
const loading = ref(false);

onMounted(async () => {
  loading.value = true;
  try {
    const [bal, logList] = await Promise.all([getBalance(), getPointsLogs()]);
    balance.value = bal;
    logs.value = logList;
  } catch { /* ignore */ }
  loading.value = false;
});

const typeLabels: Record<string, string> = {
  LOGIN: '每日登录',
  POST: '发表帖子',
  LIKED: '收到点赞',
  ACCEPTED: '回答被采纳',
  CHECKIN: '每日打卡',
  BOUNTY: '悬赏支出',
};

const typeColors: Record<string, string> = {
  LOGIN: '#10b981', // emerald
  POST: '#3b82f6', // blue
  LIKED: '#f43f5e', // rose
  ACCEPTED: '#8b5cf6', // violet
  CHECKIN: '#06b6d4', // cyan
  BOUNTY: '#ef4444', // red
};

const typeBgColors: Record<string, string> = {
  LOGIN: 'rgba(16, 185, 129, 0.15)',
  POST: 'rgba(59, 130, 246, 0.15)',
  LIKED: 'rgba(244, 63, 94, 0.15)',
  ACCEPTED: 'rgba(139, 92, 246, 0.15)',
  CHECKIN: 'rgba(6, 182, 212, 0.15)',
  BOUNTY: 'rgba(239, 68, 68, 0.15)',
};

const typeIcons: Record<string, Component> = {
  LOGIN: LogInOutline,
  POST: DocumentTextOutline,
  LIKED: HeartOutline,
  ACCEPTED: CheckmarkCircleOutline,
  CHECKIN: CalendarOutline,
  BOUNTY: GiftOutline,
};

function getTypeIcon(type: string): Component {
  return typeIcons[type] || StarOutline;
}
</script>

<template>
  <div class="points-layout">
    <div class="header-banner">
      <div class="banner-content">
        <button
          class="action-btn back-btn"
          title="返回"
          @click="router.back()"
        >
          <n-icon><ArrowBackOutline /></n-icon>
        </button>
        <h1 class="page-title gradient-text">
          <n-icon
            size="32"
            class="title-icon"
          >
            <TrophyOutline />
          </n-icon>
          我的积分
        </h1>
      </div>
    </div>

    <div class="main-container">
      <div class="balance-card glass-card">
        <div class="balance-content">
          <div class="balance-label">
            当前可用积分
          </div>
          <div class="balance-num">
            <span class="num">{{ balance }}</span>
            <span class="currency">PT</span>
          </div>
        </div>
        <div class="balance-bg-icon">
          <n-icon><StarOutline /></n-icon>
        </div>
      </div>

      <div class="logs-container glass-card">
        <div class="logs-header">
          <h3>积分明细</h3>
        </div>

        <div
          v-if="loading"
          class="loading-state"
        >
          <n-spin size="large" />
          <p>加载中...</p>
        </div>

        <div
          v-else-if="logs.length === 0"
          class="empty-state"
        >
          <n-icon
            size="64"
            color="#30363d"
          >
            <StarOutline />
          </n-icon>
          <h3>暂无记录</h3>
          <p>多参与社区互动赚取积分吧</p>
        </div>

        <div
          v-else
          class="logs-list"
        >
          <div
            v-for="entry in logs"
            :key="entry.id"
            class="log-item"
          >
            <div class="log-left">
              <div 
                class="log-icon-wrap" 
                :style="{ backgroundColor: typeBgColors[entry.type] || 'rgba(255,255,255,0.1)', color: typeColors[entry.type] || '#fff' }"
              >
                <n-icon size="20">
                  <component :is="getTypeIcon(entry.type)" />
                </n-icon>
              </div>
              <div class="log-info">
                <span class="log-title">{{ typeLabels[entry.type] || entry.type }}</span>
                <span class="log-ref">{{ entry.reference || '系统结算' }}</span>
              </div>
            </div>
            <div class="log-right">
              <div
                class="amount-wrap"
                :class="entry.amount > 0 ? 'positive' : 'negative'"
              >
                <n-icon size="14">
                  <TrendingUpOutline v-if="entry.amount > 0" />
                  <TrendingDownOutline v-else />
                </n-icon>
                <span class="log-amount">{{ Math.abs(entry.amount) }}</span>
              </div>
              <div class="balance-info">
                <span class="log-time">{{ new Date(entry.createdAt).toLocaleDateString() }}</span>
                <span class="log-balance">结余 {{ entry.balance }}</span>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped lang="scss">
.points-layout {
  height: 100vh;
  overflow-y: auto;
  background: transparent;
  background-image: radial-gradient(circle at 50% 20%, rgba(245, 158, 11, 0.08), transparent 50%);
}

.header-banner {
  max-width: 800px;
  width: 100%;
  margin: 0 auto;
  padding: 40px 24px 24px;
  display: flex;
  justify-content: space-between;
  align-items: center;

  .banner-content {
    display: flex;
    align-items: center;
    gap: 16px;

    .back-btn {
      width: 40px;
      height: 40px;
      border-radius: 50%;
      background: rgba(255, 255, 255, 0.05);
      border: 1px solid var(--cf-border);
      color: var(--cf-text-primary);
      display: flex;
      align-items: center;
      justify-content: center;
      cursor: pointer;
      font-size: 20px;
      transition: all 0.3s;

      &:hover {
        background: rgba(255, 255, 255, 0.1);
        transform: translateX(-2px);
      }
    }

    .page-title {
      font-size: 32px;
      font-weight: 800;
      margin: 0;
      display: flex;
      align-items: center;
      gap: 12px;
    }
  }
}

.main-container {
  max-width: 800px;
  width: 100%;
  margin: 0 auto;
  padding: 0 24px 40px;
  display: flex;
  flex-direction: column;
  gap: 24px;
}

.balance-card {
  position: relative;
  overflow: hidden;
  padding: 40px;
  border-radius: 24px;
  background: linear-gradient(135deg, rgba(245, 158, 11, 0.2) 0%, rgba(217, 119, 6, 0.05) 100%);
  border: 1px solid rgba(245, 158, 11, 0.2);
  
  .balance-content {
    position: relative;
    z-index: 2;
    
    .balance-label {
      font-size: 16px;
      color: var(--cf-text-secondary);
      margin-bottom: 8px;
    }
    
    .balance-num {
      display: flex;
      align-items: baseline;
      gap: 8px;
      color: var(--cf-text-primary);
      
      .num {
        font-size: 56px;
        font-weight: 800;
        line-height: 1;
        background: linear-gradient(135deg, #fbbf24 0%, #f59e0b 100%);
        -webkit-background-clip: text;
        -webkit-text-fill-color: transparent;
      }
      
      .currency {
        font-size: 20px;
        font-weight: 600;
        color: #fbbf24;
        opacity: 0.8;
      }
    }
  }
  
  .balance-bg-icon {
    position: absolute;
    right: -20px;
    bottom: -40px;
    font-size: 200px;
    color: rgba(245, 158, 11, 0.05);
    z-index: 1;
    transform: rotate(-15deg);
  }
}

.logs-container {
  padding: 0;
  overflow: hidden;
  
  .logs-header {
    padding: 24px;
    border-bottom: 1px solid var(--cf-border);
    
    h3 {
      margin: 0;
      font-size: 18px;
      color: var(--cf-text-primary);
    }
  }
}

.logs-list {
  display: flex;
  flex-direction: column;
}

.log-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 20px 24px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.02);
  transition: background 0.3s;

  &:hover {
    background: rgba(255, 255, 255, 0.02);
  }

  .log-left {
    display: flex;
    align-items: center;
    gap: 16px;

    .log-icon-wrap {
      width: 44px;
      height: 44px;
      border-radius: 12px;
      display: flex;
      align-items: center;
      justify-content: center;
      flex-shrink: 0;
    }

    .log-info {
      display: flex;
      flex-direction: column;
      gap: 4px;

      .log-title {
        font-weight: 600;
        font-size: 16px;
        color: var(--cf-text-primary);
      }
      
      .log-ref {
        font-size: 13px;
        color: var(--cf-text-secondary);
      }
    }
  }

  .log-right {
    display: flex;
    flex-direction: column;
    align-items: flex-end;
    gap: 4px;

    .amount-wrap {
      display: flex;
      align-items: center;
      gap: 4px;
      font-weight: 700;
      font-size: 18px;
      
      &.positive { color: #10b981; }
      &.negative { color: #ef4444; }
    }

    .balance-info {
      display: flex;
      align-items: center;
      gap: 8px;
      font-size: 12px;
      color: var(--cf-text-secondary);
      
      .log-balance {
        background: rgba(255, 255, 255, 0.05);
        padding: 2px 8px;
        border-radius: 10px;
      }
    }
  }
}

.loading-state, .empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 80px 0;
  color: var(--cf-text-secondary);
  
  h3 {
    margin: 16px 0 8px;
    color: var(--cf-text-primary);
    font-size: 18px;
  }
  
  p {
    margin: 0;
    font-size: 14px;
  }
}
</style>
