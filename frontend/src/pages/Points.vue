<script setup lang="ts">
import { computed, onMounted, ref, type CSSProperties } from 'vue';
import { useRouter } from 'vue-router';
import { NIcon, NModal, NSpin, useMessage } from 'naive-ui';
import {
  BagHandleOutline,
  BookOutline,
  CalendarOutline,
  CardOutline,
  ChatbubbleEllipsesOutline,
  CheckmarkCircleOutline,
  ChevronForwardOutline,
  ClipboardOutline,
  CreateOutline,
  DocumentTextOutline,
  GiftOutline,
  HeartOutline,
  HomeOutline,
  LogInOutline,
  MedalOutline,
  PeopleOutline,
  PersonAddOutline,
  PricetagOutline,
  RibbonOutline,
  SchoolOutline,
  ShieldCheckmarkOutline,
  ShirtOutline,
  SparklesOutline,
  StarOutline,
  StorefrontOutline,
  ThumbsUpOutline,
  TicketOutline,
  TimeOutline,
  TrophyOutline,
  WalletOutline,
} from '@vicons/ionicons5';
import { getBalance, getPointsLogs } from '@/api/points';
import type { PointsLogVO } from '@/types/points';
import type { Component } from 'vue';
import { useTheme } from '@/composables/useTheme';
import { useAuthStore } from '@/stores/auth';

const { isDarkTheme } = useTheme();
const authStore = useAuthStore();

type NavItem = {
  label: string;
  icon: Component;
  active?: boolean;
};

type TaskItem = {
  title: string;
  reward: number;
  icon: Component;
  color: string;
  done?: boolean;
  route?: string;
};

type RewardItem = {
  title: string;
  points: number;
  icon: Component;
  tone: string;
  visual: string;
};

type PerkItem = {
  title: string;
  icon: Component;
  enabled: boolean;
};

type RankItem = {
  name: string;
  role: string;
  points: number;
  color: string;
};
type ParticleStyle = CSSProperties & Record<'--dx' | '--dy' | '--rot', string>;
type Particle = { id: number; style: ParticleStyle };

const router = useRouter();
const message = useMessage();
const balance = ref(2450);
const logs = ref<PointsLogVO[]>([]);
const loading = ref(false);
const detailVisible = ref(false);
const rewardExpanded = ref(false);
const perkExpanded = ref(false);
const rankVisible = ref(false);
const rankPeriod = ref<'week' | 'month'>('week');
const boostActive = ref(false);

const particles = ref<Particle[]>([]);
const boostModalVisible = ref(false);
const boostTimeRemaining = ref('07天 00:00:00');
let boostTimerId: ReturnType<typeof setInterval> | null = null;

const checkinDone = ref(false);
const streakDays = ref(11);

const pendingPoints = 120;
const usedPoints = 1860;
const designBalance = 2450;
const currentLevel = 'LV5';
const nextLevel = 'LV6';
const nextLevelNeed = 550;
const displayBalance = computed(() => Math.max(balance.value, designBalance));
const levelProgress = computed(() => Math.min(100, Math.round((displayBalance.value / (displayBalance.value + nextLevelNeed)) * 100)));

const sideGroups: { title?: string; items: NavItem[] }[] = [
  {
    items: [{ label: '积分概览', icon: HomeOutline, active: true }],
  },
  {
    title: '获取积分',
    items: [
      { label: '每日任务', icon: ClipboardOutline },
      { label: '成长任务', icon: ShieldCheckmarkOutline },
      { label: '活动奖励', icon: SparklesOutline },
      { label: '邀请好友', icon: PeopleOutline },
    ],
  },
  {
    title: '积分商城',
    items: [
      { label: '精选推荐', icon: StarOutline },
      { label: '学习资料', icon: BookOutline },
      { label: '会员权益', icon: MedalOutline },
      { label: '周边礼品', icon: GiftOutline },
    ],
  },
  {
    title: '我的积分',
    items: [
      { label: '积分明细', icon: CardOutline },
      { label: '我的兑换', icon: BagHandleOutline },
      { label: '收货地址', icon: StorefrontOutline },
    ],
  },
];

const dailyTasks: TaskItem[] = [
  { title: '每日登录', reward: 10, icon: CalendarOutline, color: '#10b981', done: true },
  { title: '浏览 3 篇帖子', reward: 15, icon: DocumentTextOutline, color: '#637bff', route: '/square' },
  { title: '发布 1 条帖子', reward: 20, icon: CreateOutline, color: '#f59e0b', route: '/posts/new' },
  { title: '评论 2 次', reward: 10, icon: ChatbubbleEllipsesOutline, color: '#38bdf8', route: '/square' },
  { title: '点赞 5 次', reward: 10, icon: ThumbsUpOutline, color: '#ff6b96', route: '/square' },
  { title: '学习 30 分钟', reward: 20, icon: BookOutline, color: '#8b5cf6', route: '/resources' },
];

const rewards: RewardItem[] = [
  { title: '7 天会员权益', points: 800, icon: RibbonOutline, tone: 'gold', visual: 'crown' },
  { title: '网易云音乐会员月卡', points: 1200, icon: CardOutline, tone: 'dark', visual: 'member-card' },
  { title: '小米蓝牙耳机', points: 3200, icon: SparklesOutline, tone: 'blue', visual: 'earbuds' },
  { title: '极简双肩包', points: 2800, icon: BagHandleOutline, tone: 'slate', visual: 'backpack' },
  { title: '考研高频词汇书', points: 1500, icon: SchoolOutline, tone: 'mint', visual: 'booklet' },
  { title: '青云阁 定制马克杯', points: 1000, icon: GiftOutline, tone: 'cream', visual: 'mug' },
];

const checkinDays = ref([
  { day: '1天', points: 10, done: true },
  { day: '2天', points: 10, done: true },
  { day: '3天', points: 15, done: true },
  { day: '4天', points: 15, done: true },
  { day: '今天', points: 20, done: false, today: true },
  { day: '6天', points: 20, done: false },
  { day: '7天', points: 30, done: false, gift: true },
]);

const currentPerks: PerkItem[] = [
  { title: '积分加成 +20%', icon: StarOutline, enabled: true },
  { title: '专属勋章', icon: MedalOutline, enabled: true },
  { title: '优先客服', icon: ShieldCheckmarkOutline, enabled: true },
  { title: '生日礼券', icon: GiftOutline, enabled: true },
  { title: '积分加成 +25%', icon: SparklesOutline, enabled: false },
  { title: '专属皮肤', icon: ShirtOutline, enabled: false },
  { title: '线下活动优先', icon: TicketOutline, enabled: false },
  { title: '更多权益', icon: PricetagOutline, enabled: false },
];

const rankList: RankItem[] = [
  { name: '程序员小明', role: '算法达人', points: 2450, color: '#f59e0b' },
  { name: '设计师奶茶', role: '灵感收集者', points: 2180, color: '#00d8bf' },
  { name: '学霸本霸', role: '资料整理官', points: 1950, color: '#ff7aa2' },
  { name: '算法小能手', role: '答疑先锋', points: 1680, color: '#64748b' },
  { name: '运动达人', role: '打卡队长', points: 1460, color: '#38bdf8' },
];

const typeLabels: Record<string, string> = {
  LOGIN: '每日登录',
  POST: '发表帖子',
  LIKED: '收到点赞',
  ACCEPTED: '回答被采纳',
  CHECKIN: '每日打卡',
  BOUNTY: '悬赏支出',
};

const typeIcons: Record<string, Component> = {
  LOGIN: LogInOutline,
  POST: DocumentTextOutline,
  LIKED: HeartOutline,
  ACCEPTED: CheckmarkCircleOutline,
  CHECKIN: CalendarOutline,
  BOUNTY: GiftOutline,
};

const fallbackLogs = ref<PointsLogVO[]>([
  { id: -1, userId: 0, type: 'LOGIN', amount: 10, reference: '连续登录奖励', balance: 2450, createdAt: new Date().toISOString() },
  { id: -2, userId: 0, type: 'POST', amount: 20, reference: '发布学习笔记', balance: 2440, createdAt: new Date(Date.now() - 86400000).toISOString() },
  { id: -3, userId: 0, type: 'LIKED', amount: 5, reference: '帖子获得点赞', balance: 2420, createdAt: new Date(Date.now() - 172800000).toISOString() },
]);

const visibleLogs = computed(() => (logs.value.length > 0 ? logs.value.slice(0, 5) : fallbackLogs.value));
const visibleRewards = computed(() => (rewardExpanded.value ? rewards : rewards.slice(0, 4)));
const visiblePerks = computed(() => (perkExpanded.value ? currentPerks : currentPerks.slice(0, 4)));
const displayedRankList = computed(() =>
  rankList.map((item, index) => ({
    ...item,
    points: rankPeriod.value === 'month' ? item.points + (rankList.length - index) * 180 : item.points,
  })),
);

onMounted(async () => {
  loading.value = true;
  try {
    const [bal, logList] = await Promise.all([getBalance(), getPointsLogs(undefined, undefined, 20)]);
    balance.value = Number.isFinite(bal) ? bal : designBalance;
    logs.value = Array.isArray(logList) ? logList : [];
  } catch {
    logs.value = [];
  } finally {
    loading.value = false;
  }
});

function formatNumber(value: number) {
  return new Intl.NumberFormat('zh-CN').format(value);
}

function formatDate(value: string) {
  return new Date(value).toLocaleDateString('zh-CN', { month: '2-digit', day: '2-digit' });
}

function getTypeIcon(type: string): Component {
  return typeIcons[type] || StarOutline;
}

function go(path?: string) {
  if (path) router.push(path);
}

function handleSideItemClick(label: string) {
  if (label.includes('积分明细')) {
    detailVisible.value = true;
    return;
  }
  if (label.includes('每日任务') || label.includes('成长任务') || label.includes('活动奖励')) {
    scrollToSection('.task-grid');
    return;
  }
  if (label.includes('邀请')) {
    inviteFriend();
    return;
  }
  if (label.includes('精选') || label.includes('学习资料') || label.includes('会员') || label.includes('礼品') || label.includes('兑换')) {
    scrollToSection('.reward-grid');
    return;
  }
  message.info('已切换到对应积分模块');
}

function scrollToSection(selector: string) {
  document.querySelector(selector)?.scrollIntoView({ behavior: 'smooth', block: 'center' });
}

function triggerConfetti(clientX: number, clientY: number) {
  const colors = ['#00f5d4', '#7b61ff', '#ffd93d', '#ff7aa2', '#38bdf8'];
  const newParticles: Particle[] = Array.from({ length: 30 }).map((_, i) => {
    const angle = Math.random() * Math.PI * 2;
    const velocity = 50 + Math.random() * 100;
    const size = 6 + Math.random() * 8;
    return {
      id: Date.now() + i,
      style: {
        position: 'fixed',
        left: `${clientX}px`,
        top: `${clientY}px`,
        width: `${size}px`,
        height: `${size}px`,
        backgroundColor: colors[Math.floor(Math.random() * colors.length)],
        borderRadius: Math.random() > 0.5 ? '50%' : '3px',
        pointerEvents: 'none',
        zIndex: 9999,
        transform: 'translate(-50%, -50%)',
        animation: 'confetti-fall 1s cubic-bezier(0.25, 0.46, 0.45, 0.94) forwards',
        '--dx': `${Math.cos(angle) * velocity}px`,
        '--dy': `${Math.sin(angle) * velocity}px`,
        '--rot': `${Math.random() * 360}deg`
      }
    };
  });
  particles.value.push(...newParticles);
  setTimeout(() => {
    particles.value = particles.value.filter(p => !newParticles.includes(p));
  }, 1000);
}

function activateBoost() {
  if (boostActive.value) {
    message.info('积分加速中！剩余时间：' + boostTimeRemaining.value);
    return;
  }
  boostModalVisible.value = true;
}

function confirmActivateBoost() {
  if (displayBalance.value < 500) {
    message.warning('积分不足 500，先去完成任务攒一攒');
    return;
  }

  balance.value -= 500;
  fallbackLogs.value.unshift({
    id: Date.now(),
    userId: 0,
    type: 'BOUNTY',
    amount: -500,
    reference: '激活积分加速卡',
    balance: balance.value,
    createdAt: new Date().toISOString()
  });

  boostActive.value = true;
  boostModalVisible.value = false;
  message.success('积分加速卡开启成功！接下来 7 天内获取积分增加 50%');

  let totalSeconds = 7 * 24 * 60 * 60;
  if (boostTimerId) clearInterval(boostTimerId);
  boostTimerId = setInterval(() => {
    totalSeconds--;
    if (totalSeconds <= 0) {
      const timerId = boostTimerId;
      if (timerId) clearInterval(timerId);
      boostTimerId = null;
      boostActive.value = false;
      boostTimeRemaining.value = '已过期';
      return;
    }
    const days = Math.floor(totalSeconds / (24 * 3600));
    const hours = Math.floor((totalSeconds % (24 * 3600)) / 3600);
    const minutes = Math.floor((totalSeconds % 3600) / 60);
    const seconds = totalSeconds % 60;
    boostTimeRemaining.value = `${days}天 ${String(hours).padStart(2, '0')}:${String(minutes).padStart(2, '0')}:${String(seconds).padStart(2, '0')}`;
  }, 1000);
}

function handleTaskAction(task: TaskItem) {
  if (task.done) {
    message.success('今日任务已完成');
    return;
  }
  go(task.route);
}

function toggleRewards() {
  rewardExpanded.value = !rewardExpanded.value;
  scrollToSection('.reward-grid');
}

function exchangeReward(reward: RewardItem) {
  if (displayBalance.value < reward.points) {
    message.warning('积分不足，先去完成任务攒一攒');
    return;
  }

  balance.value -= reward.points;

  fallbackLogs.value.unshift({
    id: Date.now(),
    userId: 0,
    type: 'BOUNTY',
    amount: -reward.points,
    reference: `兑换: ${reward.title}`,
    balance: balance.value,
    createdAt: new Date().toISOString()
  });

  message.success(`兑换成功：已兑换 ${reward.title}`);
}

async function inviteFriend() {
  const inviteLink = typeof window === 'undefined' ? '青云阁' : `${window.location.origin}/register?invite=me`;
  try {
    await navigator.clipboard?.writeText(inviteLink);
    message.success('邀请链接已复制');
  } catch {
    message.info('邀请入口已打开');
  }
}

function handleCheckinClick(event: MouseEvent) {
  if (checkinDone.value) {
    message.info('今天已经签到过了，明天继续保持');
    return;
  }

  triggerConfetti(event.clientX, event.clientY);

  checkinDone.value = true;
  streakDays.value = 12;
  checkinDays.value[4].done = true;
  balance.value += 20;

  fallbackLogs.value.unshift({
    id: Date.now(),
    userId: 0,
    type: 'CHECKIN',
    amount: 20,
    reference: '签到成功奖励',
    balance: balance.value,
    createdAt: new Date().toISOString()
  });

  message.success('签到成功！积分 +20');
}

function togglePerks() {
  perkExpanded.value = !perkExpanded.value;
}

function selectRankPeriod(period: 'week' | 'month') {
  rankPeriod.value = period;
}

function openRankModal() {
  rankVisible.value = true;
}
</script>

<template>
  <div class="points-page">
    <!-- Confetti Particles -->
    <div v-for="p in particles" :key="p.id" :style="p.style" />
    <aside class="points-sidebar glass-card">
      <div
        v-for="group in sideGroups"
        :key="group.title || 'overview'"
        class="side-group"
      >
        <p v-if="group.title" class="side-title">{{ group.title }}</p>
        <button
          v-for="item in group.items"
          :key="item.label"
          class="side-item"
          :class="{ active: item.active }"
          @click="handleSideItemClick(item.label)"
        >
          <n-icon size="18"><component :is="item.icon" /></n-icon>
          <span>{{ item.label }}</span>
        </button>
      </div>

      <div class="boost-card">
        <strong>积分加倍卡</strong>
        <span>开启后 7 天内积分获取 +50%</span>
        <button @click="activateBoost">{{ boostActive ? '已开启' : '立即开启' }}</button>
        <div class="coin-stack"><n-icon><WalletOutline /></n-icon></div>
      </div>
    </aside>

    <main class="points-main">
      <section class="hero-card glass-card" :class="{ boosted: boostActive }">
        <div class="hero-copy">
          <span class="eyebrow">我的积分</span>
          <div class="hero-balance" style="display: flex; align-items: center; gap: 8px;">
            <strong>{{ formatNumber(displayBalance) }}</strong>
            <span><n-icon><StarOutline /></n-icon></span>
            <span v-if="boostActive" style="color: #f59e0b; font-size: 11px; font-weight: 800; border: 1.5px solid #f59e0b; border-radius: 99px; padding: 2px 8px; margin-left: 6px; white-space: nowrap; animation: neon-pulse 1.5s infinite alternate; background: rgba(245, 158, 11, 0.08);">
              加速中 {{ boostTimeRemaining }}
            </span>
          </div>
          <div style="display: flex; align-items: center; gap: 12px; margin-top: 12px;">
            <button class="text-link" @click="detailVisible = true">
              积分明细
              <n-icon size="14"><ChevronForwardOutline /></n-icon>
            </button>
            <span style="color: var(--cf-text-muted); opacity: 0.4;">|</span>
            <button class="text-link" @click="inviteFriend">
              邀请好友
              <n-icon size="14"><ChevronForwardOutline /></n-icon>
            </button>
          </div>
        </div>

        <div class="hero-stats">
          <div>
            <span>可用积分</span>
            <strong>{{ formatNumber(displayBalance) }}</strong>
          </div>
          <div>
            <span>待发放积分</span>
            <strong>{{ pendingPoints }}</strong>
          </div>
          <div>
            <span>已用积分</span>
            <strong>{{ formatNumber(usedPoints) }}</strong>
          </div>
        </div>

        <div class="gift-illustration" aria-hidden="true">
          <div class="gift-lid" />
          <div class="gift-box">
            <n-icon><StarOutline /></n-icon>
          </div>
          <span class="coin coin-a" />
          <span class="coin coin-b" />
          <span class="spark spark-a" />
          <span class="spark spark-b" />
        </div>

        <div class="level-strip">
          <div class="level-label"><span>{{ currentLevel }}</span><strong>学霸达人</strong></div>
          <div class="progress-track"><span class="progress-fill" :style="{ width: levelProgress + '%' }" /></div>
          <p>再获得 {{ nextLevelNeed }} 积分可升级到 {{ nextLevel }}</p>
          <n-icon size="16"><ChevronForwardOutline /></n-icon>
        </div>
      </section>

      <section class="section-block">
        <div class="section-head">
          <div>
            <h2>每日任务</h2>
            <p>完成任务赚积分，每日 0 点刷新</p>
          </div>
          <button class="text-link" @click="scrollToSection('.task-grid')">
            全部任务
            <n-icon size="14"><ChevronForwardOutline /></n-icon>
          </button>
        </div>

        <div class="task-grid">
          <article
            v-for="task in dailyTasks"
            :key="task.title"
            class="task-card glass-card"
          >
            <div class="task-icon" :style="{ '--task-color': task.color }">
              <n-icon size="30"><component :is="task.icon" /></n-icon>
            </div>
            <strong>{{ task.title }}</strong>
            <span>+{{ task.reward }} 积分</span>
            <button
              :class="{ done: task.done }"
              @click="handleTaskAction(task)"
            >
              <n-icon v-if="task.done" size="16"><CheckmarkCircleOutline /></n-icon>
              {{ task.done ? '已完成' : '去完成' }}
            </button>
          </article>
        </div>
      </section>

      <section class="section-block">
        <div class="section-head">
          <div>
            <h2>精选兑换</h2>
            <p>精选好物，积分兑换精美礼品和权益</p>
          </div>
          <button class="text-link" @click="toggleRewards">
            {{ rewardExpanded ? '收起商品' : '更多商品' }}
            <n-icon size="14"><ChevronForwardOutline /></n-icon>
          </button>
        </div>

        <div class="reward-grid">
          <article
            v-for="reward in visibleRewards"
            :key="reward.title"
            class="reward-card glass-card"
          >
            <div class="reward-visual" :class="[reward.tone, reward.visual]">
              <span class="product-shape" />
            </div>
            <strong>{{ reward.title }}</strong>
            <span>{{ formatNumber(reward.points) }} 积分</span>
            <button @click="exchangeReward(reward)">兑换</button>
          </article>
        </div>
      </section>

      <!-- invite-card removed and integrated into hero-card as link -->
    </main>

    <aside class="points-right">
      <section class="right-card glass-card">
        <div class="right-head">
          <div>
            <h2>每日签到</h2>
            <p>连续签到得更多积分</p>
          </div>
        </div>
        <strong class="checkin-title">已连续签到 <b>{{ streakDays }}</b> 天</strong>
        <div class="checkin-grid">
          <div
            v-for="day in checkinDays"
            :key="day.day"
            class="checkin-day"
            :class="{ done: day.done, today: day.today }"
          >
            <span>+{{ day.points }}</span>
            <n-icon v-if="day.done" size="18"><CheckmarkCircleOutline /></n-icon>
            <n-icon v-else-if="day.gift" size="18"><GiftOutline /></n-icon>
            <i v-else />
            <small>{{ day.day }}</small>
          </div>
        </div>
        <button
          class="wide-primary"
          :class="{ disabled: checkinDone }"
          :disabled="checkinDone"
          @click="handleCheckinClick($event)"
        >
          {{ checkinDone ? '今日已签到' : '立即签到' }}
        </button>
      </section>

      <section class="right-card glass-card">
        <div class="right-head">
          <div>
            <h2>等级特权</h2>
          </div>
          <button class="text-link" @click="togglePerks">{{ perkExpanded ? '收起特权' : '查看全部特权' }}</button>
        </div>
        <div class="level-name"><span>{{ currentLevel }}</span><strong>学霸达人</strong></div>
        <p class="perk-caption">当前等级特权</p>
        <div class="perk-grid">
          <div
            v-for="perk in visiblePerks"
            :key="perk.title"
            :class="{ disabled: !perk.enabled }"
          >
            <n-icon size="22"><component :is="perk.icon" /></n-icon>
            <span>{{ perk.title }}</span>
          </div>
        </div>
      </section>

      <section class="right-card glass-card">
        <div class="right-head">
          <div>
            <h2>积分排行榜</h2>
          </div>
          <div class="rank-tabs">
            <button :class="{ active: rankPeriod === 'week' }" @click="selectRankPeriod('week')">本周</button>
            <button :class="{ active: rankPeriod === 'month' }" @click="selectRankPeriod('month')">本月</button>
          </div>
        </div>
        <div class="rank-list">
          <div
            v-for="(item, index) in displayedRankList"
            :key="item.name"
            class="rank-row"
          >
            <span class="rank-index" :style="{ '--rank-color': item.color }">{{ index + 1 }}</span>
            <span class="avatar" :style="{ '--avatar-color': item.color }">{{ item.name.slice(0, 1) }}</span>
            <div>
              <strong>{{ item.name }}</strong>
              <small>{{ item.role }}</small>
            </div>
            <b>{{ formatNumber(item.points) }}</b>
          </div>
        </div>
        <button class="rank-more" @click="openRankModal">
          查看完整排行榜
          <n-icon size="14"><ChevronForwardOutline /></n-icon>
        </button>
      </section>
    </aside>

    <n-modal
      v-model:show="detailVisible"
      preset="card"
      class="points-detail-modal"
      title="积分明细"
      transform-origin="center"
      :bordered="false"
      :style="{ width: 'min(92vw, 720px)' }"
    >
      <div class="detail-modal-head">
        <p>来自登录、发帖、点赞、打卡等积分记录</p>
        <n-spin v-if="loading" size="small" />
      </div>
      <div class="log-list modal-log-list">
        <div
          v-for="entry in visibleLogs"
          :key="entry.id"
          class="log-row"
        >
          <div class="log-left">
            <span class="log-icon"><n-icon><component :is="getTypeIcon(entry.type)" /></n-icon></span>
            <div>
              <strong>{{ typeLabels[entry.type] || entry.type || '积分变动' }}</strong>
              <p>{{ entry.reference || '系统结算' }}</p>
            </div>
          </div>
          <div class="log-right" :class="{ negative: entry.amount < 0 }">
            <strong>{{ entry.amount > 0 ? '+' : '-' }}{{ Math.abs(entry.amount) }}</strong>
            <span>{{ formatDate(entry.createdAt) }} · 结余 {{ formatNumber(entry.balance) }}</span>
          </div>
        </div>
      </div>
    </n-modal>

    <n-modal
      v-model:show="rankVisible"
      preset="card"
      class="points-detail-modal"
      title="完整排行榜"
      transform-origin="center"
      :bordered="false"
      :style="{ width: 'min(92vw, 620px)' }"
    >
      <div class="rank-list modal-rank-list">
        <div
          v-for="(item, index) in displayedRankList"
          :key="item.name"
          class="rank-row"
        >
          <span class="rank-index" :style="{ '--rank-color': item.color }">{{ index + 1 }}</span>
          <span class="avatar" :style="{ '--avatar-color': item.color }">{{ item.name.slice(0, 1) }}</span>
          <div>
            <strong>{{ item.name }}</strong>
            <small>{{ item.role }}</small>
          </div>
          <b>{{ formatNumber(item.points) }}</b>
        </div>
      </div>
    </n-modal>

    <n-modal
      v-model:show="boostModalVisible"
      preset="card"
      class="points-detail-modal"
      title="激活积分加倍卡"
      transform-origin="center"
      :bordered="false"
      :style="{ width: 'min(92vw, 420px)' }"
    >
      <div style="text-align: center; padding: 10px 0 10px;">
        <p style="margin: 0 0 24px; color: var(--cf-text-secondary); font-size: 15px; line-height: 1.6;">
          确定花费 <strong style="color: #f59e0b;">500 积分</strong> 激活 7 天双倍积分加速卡吗？<br />激活后，打卡和做任务的积分奖励将加成 50%！
        </p>
        <div style="display: flex; gap: 12px; justify-content: center;">
          <button class="cf-primary-btn" style="min-width: 100px; height: 38px; padding: 0 16px; border-radius: var(--cf-radius-md);" @click="confirmActivateBoost">确认激活</button>
          <button class="cf-secondary-btn" style="min-width: 100px; height: 38px; padding: 0 16px; border-radius: var(--cf-radius-md);" @click="boostModalVisible = false">取消</button>
        </div>
      </div>
    </n-modal>
  </div>
</template>

<style scoped lang="scss">
.points-page {
  width: 100%;
  margin: 0;
  padding: 8px 0 40px;
  min-height: calc(100vh - 112px);
  display: grid;
  grid-template-columns: 260px minmax(0, 1fr) 360px;
  gap: 28px;
  color: var(--cf-text-primary);
  background: var(--cf-page-bg);
}

.glass-card {
  background: var(--cf-card-bg);
  border: 1px solid var(--cf-card-border);
  border-radius: 20px;
  box-shadow: var(--cf-card-shadow);
  backdrop-filter: blur(24px) saturate(150%);
  -webkit-backdrop-filter: blur(24px) saturate(150%);
}

button {
  border: 0;
  background: transparent;
  color: inherit;
  cursor: pointer;
}

.points-sidebar,
.points-right {
  position: sticky;
  top: 8px;
  align-self: start;
  max-height: calc(100vh - 132px);
  overflow-y: auto;
  scrollbar-width: none;
}

.points-sidebar::-webkit-scrollbar,
.points-right::-webkit-scrollbar {
  display: none;
}

.points-sidebar {
  padding: 18px 14px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.side-group {
  padding-bottom: 12px;
  border-bottom: 1px solid var(--cf-border);

  &:last-of-type {
    border-bottom: 0;
  }
}

.side-title {
  margin: 10px 10px 8px;
  color: var(--cf-text-muted);
  font-size: 13px;
}

.side-item {
  width: 100%;
  min-height: 40px;
  padding: 0 12px;
  border-radius: 10px;
  display: flex;
  align-items: center;
  gap: 10px;
  color: var(--cf-text-secondary);
  font-weight: 700;
  transition: background 0.2s ease, color 0.2s ease;

  &:hover,
  &.active {
    color: var(--cf-primary);
    background: color-mix(in srgb, var(--cf-primary) 10%, transparent);
  }
}

.boost-card {
  position: relative;
  min-height: 172px;
  margin-top: auto;
  padding: 20px 16px;
  border-radius: 14px;
  overflow: hidden;
  background:
    radial-gradient(circle at 78% 72%, rgba(245, 158, 11, 0.22), transparent 28%),
    linear-gradient(135deg, color-mix(in srgb, var(--cf-primary) 12%, var(--cf-bg-readable)), var(--cf-bg-glass-soft));
  border: 1px solid var(--cf-border);

  strong,
  span {
    position: relative;
    z-index: 1;
    display: block;
  }

  span {
    margin-top: 8px;
    color: var(--cf-text-secondary);
    font-size: 13px;
    line-height: 1.5;
  }

  button {
    position: relative;
    z-index: 1;
    margin-top: 20px;
    height: 34px;
    padding: 0 14px;
    border-radius: 9px;
    border: 1px solid color-mix(in srgb, var(--cf-primary) 42%, transparent);
    color: var(--cf-primary);
    font-weight: 800;
    background: var(--cf-bg-readable);
  }
}

.coin-stack {
  position: absolute;
  right: 16px;
  bottom: 16px;
  width: 68px;
  height: 68px;
  border-radius: 50%;
  background: linear-gradient(135deg, #ffd166, #f59e0b);
  color: #fff8db;
  display: grid;
  place-items: center;
  font-size: 34px;
  box-shadow: 0 18px 36px rgba(245, 158, 11, 0.28);
}

.points-main {
  min-width: 0;
  padding-top: 18px;
  display: flex;
  flex-direction: column;
  gap: 28px;
}

.hero-card {
  position: relative;
  min-height: 258px;
  padding: 32px 36px;
  overflow: hidden;
  display: grid;
  grid-template-columns: 190px minmax(250px, 1fr) 260px;
  gap: 24px;
  align-items: start;
  background:
    radial-gradient(circle at 78% 32%, color-mix(in srgb, var(--cf-primary) 22%, transparent), transparent 28%),
    linear-gradient(135deg, color-mix(in srgb, var(--cf-primary) 12%, var(--cf-bg-card)), color-mix(in srgb, #dffdf7 68%, var(--cf-bg-card)));
}

.eyebrow {
  display: block;
  margin-bottom: 14px;
  color: var(--cf-text-primary);
  font-size: 18px;
  font-weight: 900;
}

.hero-balance {
  display: flex;
  align-items: center;
  gap: 10px;

  strong {
    font-size: 42px;
    line-height: 1;
    letter-spacing: 0;
  }

  span {
    width: 24px;
    height: 24px;
    border-radius: 50%;
    display: grid;
    place-items: center;
    color: #fff;
    background: linear-gradient(135deg, #ffd166, #f59e0b);
  }
}

.text-link {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 0;
  color: var(--cf-text-muted);
  font-size: 13px;
  font-weight: 800;

  &:hover {
    color: var(--cf-primary);
  }
}

.hero-stats {
  margin-top: 42px;
  display: grid;
  grid-template-columns: repeat(3, 1fr);

  div {
    padding: 0 22px;
    border-left: 1px solid var(--cf-border);
  }

  span,
  strong {
    display: block;
  }

  span {
    color: var(--cf-text-muted);
    font-size: 13px;
  }

  strong {
    margin-top: 8px;
    font-size: 20px;
  }
}

.gift-illustration {
  position: relative;
  height: 170px;
}

.gift-box,
.gift-lid {
  position: absolute;
  left: 62px;
  width: 108px;
  border-radius: 18px;
  background: linear-gradient(145deg, #12e5c5, #02a88f);
  box-shadow: 0 22px 54px rgba(0, 168, 143, 0.28);
}

.gift-box {
  bottom: 8px;
  height: 92px;
  display: grid;
  place-items: center;
  color: #ffd166;
  font-size: 56px;

  &::before,
  &::after {
    content: '';
    position: absolute;
    background: rgba(255, 255, 255, 0.44);
  }

  &::before {
    inset: 0 auto 0 48%;
    width: 12px;
  }

  &::after {
    left: 18px;
    right: 18px;
    top: 38px;
    height: 10px;
    border-radius: 999px;
  }
}

.gift-lid {
  bottom: 96px;
  height: 38px;
  transform: rotate(28deg) translate(16px, -12px);
}

.coin,
.spark {
  position: absolute;
  display: block;
}

.coin {
  width: 22px;
  height: 22px;
  border-radius: 50%;
  background: linear-gradient(135deg, #ffe08a, #f59e0b);
}

.coin-a {
  left: 26px;
  top: 22px;
}

.coin-b {
  right: 26px;
  top: 76px;
}

.spark {
  width: 10px;
  height: 10px;
  border-radius: 3px;
  background: #ff7aa2;
  transform: rotate(30deg);
}

.spark-a {
  right: 8px;
  top: 34px;
}

.spark-b {
  left: 18px;
  bottom: 28px;
  background: #38bdf8;
}

.level-strip {
  position: absolute;
  left: 36px;
  right: auto;
  bottom: 24px;
  width: 572px;
  min-height: 58px;
  padding: 12px 16px;
  border-radius: 10px;
  display: grid;
  grid-template-columns: 145px minmax(150px, 1fr) max-content 16px;
  align-items: center;
  gap: 14px;
  background: color-mix(in srgb, var(--cf-bg-card) 86%, transparent);
  border: 1px solid var(--cf-border-glass);
  box-shadow: var(--cf-shadow-soft);
}

.level-label {
  display: flex;
  align-items: center;
  gap: 8px;

  span {
    padding: 3px 8px;
    border-radius: 999px;
    background: color-mix(in srgb, var(--cf-primary) 35%, transparent);
    color: var(--cf-primary);
    font-size: 12px;
    font-weight: 900;
  }
}

.progress-track {
  height: 8px;
  border-radius: 999px;
  overflow: hidden;
  background: var(--cf-bg-muted);

  .progress-fill {
    display: block;
    height: 100%;
    border-radius: inherit;
    background: linear-gradient(90deg, var(--cf-primary), #22c55e);
  }
}

.level-strip p {
  margin: 0;
  color: var(--cf-text-muted);
  font-size: 13px;
}

.section-block {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.section-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;

  > div {
    display: flex;
    align-items: baseline;
    gap: 14px;
    min-width: 0;
  }

  h2,
  p {
    margin: 0;
  }

  h2 {
    font-size: 20px;
    line-height: 1.2;
  }

  p {
    color: var(--cf-text-muted);
    font-size: 13px;
  }

  &.compact {
    padding: 22px 24px 4px;
  }
}

.task-grid {
  display: grid;
  grid-template-columns: repeat(6, minmax(0, 1fr));
  gap: 16px;
}

.task-card {
  min-height: 192px;
  padding: 20px 14px 16px;
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
  gap: 8px;

  strong {
    font-size: 14px;
  }

  span {
    color: var(--cf-text-muted);
    font-size: 13px;
  }

  button {
    min-width: 86px;
    height: 34px;
    margin-top: auto;
    border-radius: 9px;
    border: 1px solid color-mix(in srgb, var(--cf-primary) 42%, transparent);
    color: var(--cf-primary);
    font-weight: 900;
    display: inline-flex;
    align-items: center;
    justify-content: center;
    gap: 4px;

    &.done {
      border-color: transparent;
      color: var(--cf-text-muted);
    }
  }
}

.task-icon {
  width: 58px;
  height: 58px;
  border-radius: 50%;
  display: grid;
  place-items: center;
  color: var(--task-color);
  background: color-mix(in srgb, var(--task-color) 15%, transparent);
}

.reward-grid {
  display: grid;
  grid-template-columns: repeat(6, minmax(0, 1fr));
  gap: 16px;
}

.reward-card {
  overflow: hidden;
  padding: 0 14px 16px;
  text-align: center;

  strong {
    min-height: 40px;
    margin-top: 12px;
    display: block;
    font-size: 13px;
    line-height: 1.45;
  }

  span {
    display: block;
    margin-top: 4px;
    color: #ef4444;
    font-size: 12px;
    font-weight: 900;
  }

  button {
    min-width: 86px;
    height: 34px;
    margin-top: 12px;
    border-radius: 9px;
    border: 1px solid color-mix(in srgb, var(--cf-primary) 40%, transparent);
    color: var(--cf-primary);
    font-weight: 900;
  }
}

.reward-visual {
  position: relative;
  height: 104px;
  margin: 0 -14px;
  display: grid;
  place-items: center;
  background: var(--visual-bg, var(--cf-primary-soft));
  overflow: hidden;

  &.gold {
    --visual-bg: linear-gradient(135deg, #fff7df, #ffe8a3);
  }

  &.dark {
    --visual-bg: linear-gradient(135deg, #101827, #353b49);
  }

  &.blue {
    --visual-bg: linear-gradient(135deg, #edf5ff, #dce9ff);
  }

  &.slate {
    --visual-bg: linear-gradient(135deg, #f5f7fb, #dfe6ef);
  }

  &.mint {
    --visual-bg: linear-gradient(135deg, #f0fffb, #dff7ef);
  }

  &.cream {
    --visual-bg: linear-gradient(135deg, #fffaf0, #f3ead8);
  }
}

.product-shape,
.product-shape::before,
.product-shape::after {
  position: absolute;
  content: '';
  display: block;
}

.crown .product-shape {
  width: 66px;
  height: 48px;
  bottom: 20px;
  background: linear-gradient(145deg, #ffd166, #f59e0b);
  clip-path: polygon(0 82%, 10% 24%, 34% 58%, 50% 4%, 66% 58%, 90% 24%, 100% 82%, 88% 100%, 12% 100%);
  filter: drop-shadow(0 16px 18px rgba(245, 158, 11, 0.3));

  &::after {
    left: 26px;
    bottom: 8px;
    width: 14px;
    height: 14px;
    border-radius: 50%;
    background: rgba(255, 255, 255, 0.82);
  }
}

.member-card .product-shape {
  width: 94px;
  height: 56px;
  border-radius: 7px;
  background: linear-gradient(145deg, #111827, #333846);
  box-shadow: 0 18px 30px rgba(15, 23, 42, 0.34);

  &::before {
    left: 12px;
    top: 14px;
    width: 18px;
    height: 18px;
    border-radius: 50%;
    background: linear-gradient(135deg, #ff6b35, #d92f1f);
  }

  &::after {
    right: 12px;
    bottom: 12px;
    width: 28px;
    height: 4px;
    border-radius: 999px;
    background: rgba(255, 255, 255, 0.72);
  }
}

.earbuds .product-shape {
  width: 88px;
  height: 54px;
  bottom: 18px;
  border-radius: 22px;
  background: linear-gradient(180deg, #ffffff, #edf3fb);
  box-shadow: 0 18px 32px rgba(71, 85, 105, 0.18);

  &::before,
  &::after {
    top: -22px;
    width: 20px;
    height: 42px;
    border-radius: 999px;
    background: linear-gradient(180deg, #ffffff, #dce7f3);
    box-shadow: 0 8px 18px rgba(71, 85, 105, 0.12);
  }

  &::before {
    left: 24px;
    transform: rotate(-12deg);
  }

  &::after {
    right: 22px;
    transform: rotate(10deg);
  }
}

.backpack .product-shape {
  width: 66px;
  height: 74px;
  bottom: 14px;
  border-radius: 18px 18px 12px 12px;
  background: linear-gradient(160deg, #1f2937, #111827);
  box-shadow: 0 18px 32px rgba(15, 23, 42, 0.28);

  &::before {
    left: 18px;
    top: -10px;
    width: 30px;
    height: 22px;
    border: 4px solid #334155;
    border-bottom: 0;
    border-radius: 18px 18px 0 0;
  }

  &::after {
    left: 14px;
    right: 14px;
    bottom: 16px;
    height: 3px;
    border-radius: 999px;
    background: #475569;
  }
}

.booklet .product-shape {
  width: 58px;
  height: 76px;
  bottom: 12px;
  border-radius: 5px;
  background:
    linear-gradient(90deg, rgba(0, 168, 143, 0.14) 0 8px, transparent 8px),
    linear-gradient(180deg, #ffffff, #eefaf4);
  border: 1px solid rgba(0, 168, 143, 0.22);
  box-shadow: 0 18px 28px rgba(0, 168, 143, 0.12);

  &::before {
    left: 16px;
    top: 18px;
    width: 32px;
    height: 5px;
    border-radius: 999px;
    background: #00a88f;
    box-shadow: 0 13px 0 rgba(0, 168, 143, 0.36), 0 26px 0 rgba(0, 168, 143, 0.22);
  }
}

.mug .product-shape {
  width: 62px;
  height: 66px;
  bottom: 16px;
  border-radius: 8px 8px 14px 14px;
  background: linear-gradient(180deg, #ffffff, #edf2f7);
  box-shadow: 0 18px 28px rgba(71, 85, 105, 0.16);

  &::before {
    right: -22px;
    top: 16px;
    width: 28px;
    height: 28px;
    border: 7px solid #d9e3ed;
    border-left: 0;
    border-radius: 0 999px 999px 0;
  }

  &::after {
    left: 17px;
    top: 24px;
    width: 28px;
    height: 12px;
    border-radius: 999px;
    background: var(--cf-primary);
  }
}

.invite-card {
  position: relative;
  min-height: 96px;
  padding: 22px 28px;
  overflow: hidden;
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: linear-gradient(135deg, #fff6e7, color-mix(in srgb, var(--cf-bg-card) 78%, #fff6e7));

  h2,
  p {
    margin: 0;
  }

  h2 {
    font-size: 20px;
  }

  p {
    margin-top: 8px;
    color: var(--cf-text-secondary);
  }

  button {
    position: relative;
    z-index: 1;
    height: 38px;
    padding: 0 18px;
    border-radius: 10px;
    border: 1px solid color-mix(in srgb, var(--cf-warning) 32%, transparent);
    color: var(--cf-warning);
    font-weight: 900;
    background: var(--cf-bg-readable);
    display: inline-flex;
    align-items: center;
    gap: 7px;
  }
}

.invite-gift {
  position: absolute;
  right: 82px;
  bottom: -28px;
  font-size: 100px;
  color: #f97316;
  opacity: 0.28;
}

.log-list {
  padding: 0 24px 18px;
}

.modal-log-list {
  max-height: 420px;
  overflow-y: auto;
  padding: 0;
}

.detail-modal-head {
  min-height: 24px;
  margin: -4px 0 8px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;

  p {
    margin: 0;
    color: var(--cf-text-muted);
    font-size: 13px;
  }
}

:deep(.points-detail-modal.n-card) {
  border-radius: 20px;
  background: var(--cf-card-bg);
  box-shadow: var(--cf-card-shadow);
}

.log-row {
  min-height: 66px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  border-bottom: 1px solid var(--cf-border);

  &:last-child {
    border-bottom: 0;
  }
}

.log-left {
  display: flex;
  align-items: center;
  gap: 12px;

  p {
    margin: 4px 0 0;
    color: var(--cf-text-muted);
    font-size: 12px;
  }
}

.log-icon {
  width: 38px;
  height: 38px;
  border-radius: 12px;
  display: grid;
  place-items: center;
  color: var(--cf-primary);
  background: var(--cf-primary-soft);
}

.log-right {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 4px;

  strong {
    color: #10b981;
  }

  span {
    color: var(--cf-text-muted);
    font-size: 12px;
  }

  &.negative strong {
    color: #ef4444;
  }
}

.points-right {
  margin-top: 18px;
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.right-card {
  padding: 22px;
}

.right-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;

  h2,
  p {
    margin: 0;
  }

  h2 {
    font-size: 18px;
  }

  p {
    margin-top: 6px;
    color: var(--cf-text-muted);
    font-size: 13px;
  }
}

.checkin-title {
  display: block;
  margin-top: 20px;
  font-size: 15px;

  b {
    font-size: 22px;
  }
}

.checkin-grid {
  margin-top: 16px;
  display: grid;
  grid-template-columns: repeat(7, 1fr);
  gap: 8px;
}

.checkin-day {
  min-width: 0;
  height: 78px;
  padding: 8px 4px;
  border-radius: 9px;
  border: 1px solid var(--cf-border);
  background: var(--cf-bg-soft);
  color: var(--cf-text-muted);
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: space-between;
  font-weight: 800;

  span {
    font-size: 12px;
  }

  small {
    font-size: 12px;
    font-weight: 700;
  }

  i {
    width: 15px;
    height: 15px;
    border-radius: 50%;
    background: #cbd5e1;
  }

  &.done {
    color: var(--cf-primary);
    background: color-mix(in srgb, var(--cf-primary) 8%, var(--cf-bg-soft));
  }

  &.today {
    border-color: #f59e0b;
    color: #d97706;
    background: #fff8e6;
  }
}

.wide-primary {
  width: 100%;
  height: 44px;
  margin-top: 20px;
  border-radius: 10px;
  background: var(--cf-primary);
  color: var(--cf-text-inverse);
  font-weight: 900;
  box-shadow: var(--cf-shadow-glow);
}

.level-name {
  margin-top: 18px;
  display: flex;
  align-items: center;
  gap: 10px;

  span {
    padding: 4px 10px;
    border-radius: 999px;
    color: #fff;
    background: linear-gradient(135deg, #75e6c3, #0bb69e);
    font-weight: 900;
    font-size: 12px;
  }
}

.perk-caption {
  margin: 20px 0 12px;
  color: var(--cf-text-secondary);
  font-size: 13px;
  font-weight: 800;
}

.perk-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px 10px;

  div {
    min-width: 0;
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 8px;
    text-align: center;
    color: #f59e0b;
  }

  span {
    color: var(--cf-text-secondary);
    font-size: 12px;
    line-height: 1.35;
  }

  .disabled {
    color: #cbd5e1;
    opacity: 0.78;
  }
}

.rank-tabs {
  display: flex;
  gap: 4px;
  padding: 3px;
  border-radius: 10px;
  background: var(--cf-bg-soft);

  button {
    height: 28px;
    padding: 0 9px;
    border-radius: 8px;
    color: var(--cf-text-muted);
    font-size: 12px;
    font-weight: 800;

    &.active {
      color: var(--cf-primary);
      background: var(--cf-primary-soft);
    }
  }
}

.rank-list {
  margin-top: 16px;
}

.rank-row {
  min-height: 46px;
  display: grid;
  grid-template-columns: 24px 34px minmax(0, 1fr) max-content;
  gap: 10px;
  align-items: center;

  strong,
  small {
    display: block;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  small {
    margin-top: 3px;
    color: var(--cf-text-muted);
    font-size: 12px;
  }

  b {
    color: var(--cf-text-secondary);
    font-size: 13px;
  }
}

.rank-index {
  width: 22px;
  height: 22px;
  border-radius: 50%;
  display: grid;
  place-items: center;
  color: #fff;
  background: var(--rank-color);
  font-size: 12px;
  font-weight: 900;
}

.avatar {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  display: grid;
  place-items: center;
  color: #fff;
  background: linear-gradient(135deg, var(--avatar-color), color-mix(in srgb, var(--avatar-color) 68%, #ffffff));
  font-size: 13px;
  font-weight: 900;
}

.rank-more {
  width: 100%;
  margin-top: 14px;
  padding-top: 14px;
  border-top: 1px solid var(--cf-border);
  color: var(--cf-text-muted);
  font-weight: 800;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
}

.modal-rank-list {
  margin-top: 0;
  max-height: 420px;
  overflow-y: auto;
}

html[data-theme='dark'] {
  .hero-card {
    background:
      radial-gradient(circle at 78% 32%, color-mix(in srgb, var(--cf-primary) 18%, transparent), transparent 28%),
      linear-gradient(135deg, color-mix(in srgb, var(--cf-primary) 12%, var(--cf-bg-card)), var(--cf-bg-card));
  }

  .invite-card {
    background: linear-gradient(135deg, rgba(245, 158, 11, 0.12), var(--cf-bg-card));
  }

  .checkin-day.today {
    background: rgba(245, 158, 11, 0.13);
    color: #f59e0b;
  }
}

.hero-card.boosted {
  box-shadow: 0 0 24px rgba(245, 158, 11, 0.35), var(--cf-card-shadow);
  border-color: rgba(245, 158, 11, 0.5);
}

@keyframes neon-pulse {
  0% {
    box-shadow: 0 0 4px rgba(245, 158, 11, 0.2), inset 0 0 2px rgba(245, 158, 11, 0.1);
  }
  100% {
    box-shadow: 0 0 12px rgba(245, 158, 11, 0.6), inset 0 0 6px rgba(245, 158, 11, 0.3);
  }
}

@keyframes confetti-fall {
  0% {
    transform: translate(-50%, -50%) translate(0, 0) rotate(0deg);
    opacity: 1;
  }
  100% {
    transform: translate(-50%, -50%) translate(var(--dx), var(--dy)) rotate(var(--rot));
    opacity: 0;
  }
}

@media (max-width: 1320px) {
  .points-page {
    grid-template-columns: 220px minmax(0, 1fr);
  }

  .points-right {
    grid-column: 1 / -1;
    position: static;
    display: grid;
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }

  .hero-card {
    grid-template-columns: 190px minmax(260px, 1fr);
  }

  .gift-illustration {
    display: none;
  }
}

@media (max-width: 1080px) {
  .points-page {
    grid-template-columns: 1fr;
  }

  .points-sidebar {
    position: static;
    display: block;
  }

  .side-group {
    display: flex;
    flex-wrap: wrap;
    gap: 8px;
  }

  .side-title,
  .boost-card {
    display: none;
  }

  .side-item {
    width: auto;
  }

  .task-grid,
  .reward-grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}

@media (max-width: 760px) {
  .points-page {
    padding: 18px 14px 32px;
  }

  .hero-card {
    min-height: 0;
    padding: 24px;
    grid-template-columns: 1fr;
  }

  .hero-stats {
    margin-top: 0;
    grid-template-columns: repeat(3, 1fr);

    div {
      padding: 0 10px;
    }

    strong {
      font-size: 16px;
    }
  }

  .level-strip {
    position: static;
    margin-top: 4px;
    grid-template-columns: 1fr;
  }

  .task-grid,
  .reward-grid,
  .points-right {
    grid-template-columns: 1fr;
  }

  .section-head {
    align-items: flex-start;
  }

  .invite-card {
    align-items: flex-start;
    flex-direction: column;
    gap: 18px;
  }

  .checkin-grid {
    grid-template-columns: repeat(4, 1fr);
  }
}
</style>
