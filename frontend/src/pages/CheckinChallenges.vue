<script setup lang="ts">
import { computed, onMounted, ref, type CSSProperties } from 'vue';
import { useRouter } from 'vue-router';
import { NButton, NDatePicker, NIcon, NInput, NModal, NSpin, useMessage } from 'naive-ui';
import {
  AddOutline,
  BarChartOutline,
  BookOutline,
  CalendarOutline,
  ChatbubblesOutline,
  CheckmarkCircleOutline,
  ChevronDownOutline,
  ChevronForwardOutline,
  FlameOutline,
  FootstepsOutline,
  PeopleOutline,
  SettingsOutline,
  ShareSocialOutline,
  StarOutline,
  SunnyOutline,
  ThumbsUpOutline,
  TimeOutline,
} from '@vicons/ionicons5';
import { createChallenge, getChallenges } from '@/api/checkin';
import type { CheckinChallengeVO } from '@/types/checkin';
import { useTheme } from '@/composables/useTheme';
import { useAuthStore } from '@/stores/auth';

type FeedTab = 'all' | 'follow' | 'friends' | 'group';
type ParticleStyle = CSSProperties & Record<'--dx' | '--dy' | '--rot', string>;
type Particle = { id: number; style: ParticleStyle };
type ChallengeCard = CheckinChallengeVO & {
  checkedToday?: boolean;
  progress?: number;
};

const router = useRouter();
const message = useMessage();
const { isDarkTheme } = useTheme();
const authStore = useAuthStore();

const challenges = ref<CheckinChallengeVO[]>([]);
const loading = ref(false);
const activeFeedTab = ref<FeedTab>('all');
const activeNavIndex = ref(0);
const feedSort = ref<'latest' | 'hot'>('latest');

const particles = ref<Particle[]>([]);
const mockDetailVisible = ref(false);
const selectedMockChallenge = ref<ChallengeCard | null>(null);
const likedActivities = ref(new Set<string>());
const dataDetailVisible = ref(false);
const rankExpanded = ref(false);
const calendarCursor = ref(new Date());
const createVisible = ref(false);
const createName = ref('');
const createDescription = ref('');
const createRange = ref<[number, number] | null>(null);
const createSubmitting = ref(false);

const navItems = [
  ['今日打卡', CalendarOutline],
  ['我的打卡', TimeOutline],
  ['打卡日历', CalendarOutline],
  ['打卡记录', BarChartOutline],
  ['打卡动态', FootstepsOutline],
  ['打卡小组', PeopleOutline],
  ['打卡设置', SettingsOutline],
] as const;

const fallbackChallenges: Array<CheckinChallengeVO & { checkedToday: boolean; progress?: number }> = [
  makeFallback(1, '早起', '06:30 前', 28, 28),
  makeFallback(2, '背单词', '50 个', 45, 45),
  makeFallback(3, '学习 2 小时', '120 分钟', 16, 16),
  makeFallback(4, '运动 30 分钟', '30 分钟', 7, 0),
  makeFallback(5, '每日阅读', '30 分钟', 23, 23),
];

const feedTabs: Array<{ key: FeedTab; label: string }> = [
  { key: 'all', label: '全部' },
  { key: 'follow', label: '关注' },
  { key: 'friends', label: '好友' },
  { key: 'group', label: '小组' },
];

const activityItems = [
  {
    author: '程序员小明',
    level: 5,
    meta: '刚刚 · 坚持打卡 128 天',
    title: '学习 2 小时',
    text: '今天学了 React 的状态管理，收获满满！',
    icon: BookOutline,
    color: 'green',
  },
  {
    author: '设计师奶茶',
    level: 4,
    meta: '10 分钟前 · 坚持打卡 68 天',
    title: '每日阅读',
    text: '阅读让人充实，思考让人深邃 ✨',
    icon: BookOutline,
    color: 'orange',
  },
];

const weekdayLabels = ['星期日', '星期一', '星期二', '星期三', '星期四', '星期五', '星期六'];

const visibleChallenges = computed(() =>
  ((challenges.value.length ? challenges.value : fallbackChallenges) as Array<CheckinChallengeVO & { checkedToday?: boolean; progress?: number }>).slice(0, 5)
);
const totalCompleted = computed(() => visibleChallenges.value.filter((item) => item.myTotalDays > 0).length);
const completionRate = computed(() => Math.round((totalCompleted.value / Math.max(visibleChallenges.value.length, 1)) * 100));
const totalDays = computed(() => visibleChallenges.value.reduce((sum, item) => sum + (item.myTotalDays || 0), 0));
const rankRows = computed(() => {
  const base = [
    ['程序员小明', 128],
    ['设计师奶茶', 98],
    ['学霸本霸', 76],
    ['晨读小队长', 62],
    ['算法练习生', 48],
  ] as const;
  return rankExpanded.value ? base : base.slice(0, 3);
});
const todayInfo = computed(() => {
  const date = new Date();
  return {
    year: date.getFullYear(),
    month: date.getMonth(),
    day: date.getDate(),
    weekday: weekdayLabels[date.getDay()],
  };
});
const heroDateLabel = computed(() => `${todayInfo.value.year} 年 ${todayInfo.value.month + 1} 月\n${todayInfo.value.day} 日 · ${todayInfo.value.weekday}`);
const calendarMonthLabel = computed(() => `${calendarCursor.value.getFullYear()}.${String(calendarCursor.value.getMonth() + 1).padStart(2, '0')}`);
const calendarDays = computed(() => {
  const year = calendarCursor.value.getFullYear();
  const month = calendarCursor.value.getMonth();
  const today = todayInfo.value;
  const firstDay = new Date(year, month, 1).getDay();
  const daysInMonth = new Date(year, month + 1, 0).getDate();
  const prevMonthDays = new Date(year, month, 0).getDate();
  const totalCells = Math.ceil((firstDay + daysInMonth) / 7) * 7;

  return Array.from({ length: totalCells }, (_, index) => {
    const currentDay = index - firstDay + 1;
    if (currentDay <= 0) {
      return { label: prevMonthDays + currentDay, current: false, today: false, hasRecord: false };
    }
    if (currentDay > daysInMonth) {
      return { label: currentDay - daysInMonth, current: false, today: false, hasRecord: false };
    }
    return {
      label: currentDay,
      current: true,
      today: year === today.year && month === today.month && currentDay === today.day,
      hasRecord: year === today.year && month === today.month && currentDay <= today.day && currentDay % 2 === 0,
    };
  });
});

async function load() {
  loading.value = true;
  try {
    challenges.value = await getChallenges({ limit: 30 });
  } catch {
    challenges.value = [];
  } finally {
    loading.value = false;
  }
}

function makeFallback(id: number, name: string, description: string, streak: number, total: number): CheckinChallengeVO & { checkedToday: boolean; progress?: number } {
  const today = new Date();
  const end = new Date(today);
  end.setDate(today.getDate() + 30);
  return {
    id: -id,
    spaceId: null,
    creatorId: 0,
    creator: null,
    name,
    description,
    startDate: formatDate(today.getTime()),
    endDate: formatDate(end.getTime()),
    rule: null,
    memberCount: 120 + id * 16,
    status: 1,
    isMember: true,
    myTotalDays: total,
    myConsecutiveDays: streak,
    createdAt: '',
    checkedToday: id !== 3 && id !== 4, // 1, 2, 5 are checked
    progress: id === 3 ? 60 : undefined,
  };
}

function iconFor(index: number) {
  return [SunnyOutline, BookOutline, BookOutline, FootstepsOutline, BookOutline][index] || CheckmarkCircleOutline;
}

function habitColor(index: number) {
  return ['sun', 'blue', 'green', 'runner', 'orange'][index] || 'green';
}

function goDetail(id: number) {
  if (id < 0) return;
  router.push(`/checkin/${id}`);
}

function handleCardClick(challenge: ChallengeCard) {
  if (challenge.id < 0) {
    selectedMockChallenge.value = challenge;
    mockDetailVisible.value = true;
  } else {
    goDetail(challenge.id);
  }
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

function handleCheckin(challenge: ChallengeCard, event: MouseEvent) {
  if (challenge.checkedToday) {
    message.info('今天已经打过卡了，明天继续加油！');
    return;
  }

  triggerConfetti(event.clientX, event.clientY);

  challenge.checkedToday = true;
  challenge.myTotalDays = (challenge.myTotalDays || 0) + 1;
  challenge.myConsecutiveDays = (challenge.myConsecutiveDays || 0) + 1;
  if (challenge.progress !== undefined) {
    challenge.progress = 100;
  }

  if (authStore.user) {
    authStore.user.points = (authStore.user.points || 0) + 20;
  }

  message.success('打卡成功！积分 +20');
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

function handleNav(index: number) {
  activeNavIndex.value = index;
  const targetMap: Record<number, string> = {
    0: '.today-section',
    1: '.habits-panel',
    2: '.calendar-card',
    3: '.data-card',
    4: '.feed-section',
    5: '.rank-card',
    6: '.data-card',
  };
  document.querySelector(targetMap[index])?.scrollIntoView({ behavior: 'smooth', block: 'start' });
}

function manageHabits() {
  activeNavIndex.value = 1;
  message.info('已定位到我的习惯，点击任意习惯可查看详情');
}

function editTodayHabits() {
  goCreate();
}

function toggleFeedSort() {
  feedSort.value = feedSort.value === 'latest' ? 'hot' : 'latest';
}

function handleActivityAction(action: 'like' | 'comment' | 'share', author: string) {
  if (action === 'like') {
    const next = new Set(likedActivities.value);
    if (next.has(author)) {
      next.delete(author);
      message.info('已取消点赞');
    } else {
      next.add(author);
      message.success('已点赞');
    }
    likedActivities.value = next;
    return;
  }
  if (action === 'comment') {
    activeFeedTab.value = 'all';
    message.info('评论入口已打开，进入对应打卡详情可参与讨论');
    return;
  }
  message.success('已打开分享入口，可分享到广场动态');
}

function shiftCalendar(delta: number) {
  const next = new Date(calendarCursor.value);
  next.setMonth(next.getMonth() + delta);
  calendarCursor.value = next;
}

function formatDate(ts: number) {
  const date = new Date(ts);
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  return `${year}-${month}-${day}`;
}

function closeCreateModal() {
  if (!createSubmitting.value) createVisible.value = false;
}

async function submitCreate() {
  if (!createName.value.trim() || !createRange.value) {
    message.warning('请填写打卡名称和日期范围');
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
  <div class="checkin-page">
    <!-- Confetti Particles -->
    <div v-for="p in particles" :key="p.id" :style="p.style" />
    <aside class="checkin-left">
      <section class="apple-card nav-card">
        <button
          v-for="(item, index) in navItems"
          :key="item[0]"
          class="left-link"
          :class="{ active: index === activeNavIndex }"
          @click="handleNav(index)"
        >
          <n-icon size="18"><component :is="item[1]" /></n-icon>
          {{ item[0] }}
        </button>
      </section>

      <section class="apple-card habits-panel">
        <div class="panel-head">
          <h3>我的习惯</h3>
          <button @click="manageHabits">管理</button>
        </div>
        <div v-for="(challenge, index) in visibleChallenges" :key="challenge.id" class="habit-row">
          <span class="habit-icon" :class="habitColor(index)">
            <n-icon size="21"><component :is="iconFor(index)" /></n-icon>
          </span>
          <div>
            <strong>{{ challenge.name }}</strong>
            <p>已坚持 {{ challenge.myConsecutiveDays || challenge.myTotalDays || 0 }} 天</p>
          </div>
        </div>
        <button class="add-habit" @click="goCreate">
          <n-icon size="17"><AddOutline /></n-icon>
          添加习惯
        </button>
      </section>
    </aside>

    <main class="checkin-main">
      <section class="hero-card">
        <div>
          <h1>今日打卡</h1>
          <p>每天进步一点点，未来遇见更好的自己 ✨</p>
          <div class="hero-stats">
            <div><strong>{{ visibleChallenges.length }}</strong><span>今日目标</span></div>
            <div><strong>{{ totalCompleted }}</strong><span>已完成</span></div>
            <div><strong>{{ completionRate }}%</strong><span>完成度</span></div>
            <div><strong>+120</strong><span>今日积分</span></div>
          </div>
        </div>
        <div class="hero-date">{{ heroDateLabel }}</div>
        <div class="student-illustration">
          <span class="head" />
          <span class="body" />
          <span class="desk" />
          <span class="plant" />
        </div>
      </section>

      <section class="today-section">
        <div class="section-title-row">
          <h2>今日习惯打卡</h2>
          <button @click="editTodayHabits">编辑</button>
        </div>
        <div v-if="loading" class="loading-state"><n-spin size="large" /></div>
        <div v-else class="habit-cards">
          <article
            v-for="(challenge, index) in visibleChallenges"
            :key="challenge.id"
            class="habit-card"
            @click="handleCardClick(challenge)"
          >
            <span class="habit-big-icon" :class="habitColor(index)">
              <n-icon size="34"><component :is="iconFor(index)" /></n-icon>
            </span>
            <h3>{{ challenge.name }}</h3>
            <p>{{ challenge.description || '30 分钟' }}</p>
            <div
              class="check-state"
              :class="{ progress: challenge.progress !== undefined && !challenge.checkedToday, pending: !challenge.checkedToday && challenge.progress === undefined }"
              @click.stop="handleCheckin(challenge, $event)"
            >
              <template v-if="challenge.progress !== undefined && !challenge.checkedToday">
                <b>{{ challenge.progress }}%</b>
              </template>
              <n-icon v-else-if="challenge.checkedToday" size="28"><CheckmarkCircleOutline /></n-icon>
            </div>
            <strong class="state-text" :style="{ color: challenge.checkedToday ? 'var(--cf-primary)' : 'var(--cf-text-muted)' }">
              {{ challenge.checkedToday ? '已打卡' : challenge.progress !== undefined ? '进行中' : '未打卡' }}
            </strong>
            <small>已坚持 {{ challenge.myConsecutiveDays }} 天</small>
          </article>
        </div>
      </section>

      <section class="feed-section">
        <div class="feed-head">
          <div>
            <h2>打卡动态</h2>
            <button
              v-for="tab in feedTabs"
              :key="tab.key"
              :class="{ active: activeFeedTab === tab.key }"
              @click="activeFeedTab = tab.key"
            >
              {{ tab.label }}
            </button>
          </div>
          <button class="sort-btn" @click="toggleFeedSort">{{ feedSort === 'latest' ? '最新' : '热门' }} <n-icon size="15"><ChevronDownOutline /></n-icon></button>
        </div>

        <article v-for="item in activityItems" :key="item.author" class="feed-card">
          <header>
            <span class="avatar">{{ item.author.slice(0, 1) }}</span>
            <div>
              <strong>{{ item.author }} <small>LV{{ item.level }}</small></strong>
              <p>{{ item.meta }}</p>
            </div>
          </header>
          <div class="feed-body">
            <div>
              <h3>完成了 <span>{{ item.title }}</span> 打卡</h3>
              <p>{{ item.text }} 💪</p>
            </div>
            <div class="mini-check-card">
              <span class="habit-icon" :class="item.color">
                <n-icon size="21"><component :is="item.icon" /></n-icon>
              </span>
              <strong>{{ item.title }}</strong>
              <small>120 分钟</small>
              <em><n-icon size="13"><CheckmarkCircleOutline /></n-icon> 已打卡</em>
            </div>
          </div>
          <footer>
            <button :class="{ active: likedActivities.has(item.author) }" @click="handleActivityAction('like', item.author)"><n-icon size="18"><ThumbsUpOutline /></n-icon>点赞</button>
            <button @click="handleActivityAction('comment', item.author)"><n-icon size="18"><ChatbubblesOutline /></n-icon>评论</button>
            <button @click="handleActivityAction('share', item.author)"><n-icon size="18"><ShareSocialOutline /></n-icon>分享</button>
          </footer>
        </article>
      </section>
    </main>

    <aside class="checkin-right">
      <section class="apple-card data-card">
        <div class="panel-head">
          <h3>我的打卡数据</h3>
          <button @click="dataDetailVisible = true">查看详情 <n-icon size="12"><ChevronForwardOutline /></n-icon></button>
        </div>
        <div class="data-numbers">
          <div><strong>128</strong><span>连续打卡天数</span></div>
          <div><strong>{{ totalDays }}</strong><span>累计打卡天数</span></div>
          <div><strong>85%</strong><span>打卡完成率</span></div>
        </div>
        <svg class="chart" viewBox="0 0 300 120" role="img" aria-label="打卡趋势">
          <path d="M12 88 L56 64 L96 82 L136 62 L174 52 L218 86 L256 66 L292 42" fill="none" stroke="#00bfa8" stroke-width="3" />
          <path d="M12 88 L56 64 L96 82 L136 62 L174 52 L218 86 L256 66 L292 42 L292 112 L12 112 Z" fill="rgba(0,216,191,.10)" />
          <g fill="#00bfa8">
            <circle cx="12" cy="88" r="4" /><circle cx="56" cy="64" r="4" /><circle cx="96" cy="82" r="4" />
            <circle cx="136" cy="62" r="4" /><circle cx="174" cy="52" r="4" /><circle cx="218" cy="86" r="4" />
            <circle cx="256" cy="66" r="4" /><circle cx="292" cy="42" r="4" />
          </g>
        </svg>
      </section>

      <section class="apple-card calendar-card">
        <div class="calendar-head">
          <h3>打卡日历</h3>
          <div><button @click="shiftCalendar(-1)">‹</button><strong>{{ calendarMonthLabel }}</strong><button @click="shiftCalendar(1)">›</button></div>
        </div>
        <div class="week-row"><span>日</span><span>一</span><span>二</span><span>三</span><span>四</span><span>五</span><span>六</span></div>
        <div class="days-grid">
          <span v-for="(day, index) in calendarDays" :key="`${day.label}-${index}`" :class="{ muted: !day.current, active: day.today }">
            {{ day.label }}
            <i v-if="day.hasRecord" />
          </span>
        </div>
      </section>

      <section class="apple-card rank-card">
        <div class="panel-head">
          <h3>打卡排行榜</h3>
          <button @click="rankExpanded = !rankExpanded">{{ rankExpanded ? '收起' : '查看全部' }} <n-icon size="12"><ChevronForwardOutline /></n-icon></button>
        </div>
        <div v-for="([name, days], index) in rankRows" :key="name" class="rank-row">
          <span>{{ index + 1 }}</span>
          <b>{{ name.slice(0, 1) }}</b>
          <strong>{{ name }}</strong>
          <em>连续 {{ days }} 天</em>
        </div>
      </section>

      <section class="quote-card">
        <span>“</span>
        <p>自律给我自由，<br />坚持成就更好的自己。</p>
        <strong>— 青云阁</strong>
      </section>
    </aside>

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
        <NInput v-model:value="createName" placeholder="例如：每日背单词" maxlength="64" show-count />
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
          <NButton :disabled="createSubmitting" @click="closeCreateModal">取消</NButton>
          <NButton type="primary" :loading="createSubmitting" @click="submitCreate">创建打卡</NButton>
        </div>
      </div>
    </NModal>

    <NModal
      v-model:show="dataDetailVisible"
      preset="card"
      title="我的打卡数据"
      class="checkin-data-modal"
      style="width: min(520px, calc(100vw - 32px));"
    >
      <div class="data-detail-list">
        <article>
          <strong>{{ totalDays }}</strong>
          <span>累计打卡天数</span>
        </article>
        <article>
          <strong>{{ completionRate }}%</strong>
          <span>今日目标完成度</span>
        </article>
        <article>
          <strong>{{ visibleChallenges.length }}</strong>
          <span>正在进行的习惯</span>
        </article>
      </div>
    </NModal>

    <!-- Mock Challenge Detail Modal -->
    <NModal v-model:show="mockDetailVisible" preset="card" class="resource-modal" :title="selectedMockChallenge?.name + ' 打卡挑战'" :bordered="false" style="width: min(720px, calc(100vw - 32px));">
      <div v-if="selectedMockChallenge">
        <div class="detail-meta" style="margin-bottom: 20px;">
          <div class="detail-tags">
            <NTag type="success" size="small">进行中</NTag>
            <NTag type="info" size="small">积分 +20</NTag>
          </div>
          <div class="modal-actions">
            <NButton type="primary" :disabled="selectedMockChallenge.checkedToday" @click="handleCheckin(selectedMockChallenge, $event)">
              {{ selectedMockChallenge.checkedToday ? '今日已打卡' : '立即打卡' }}
            </NButton>
          </div>
        </div>

        <p class="resource-desc" style="font-size: 15px; margin-bottom: 24px; color: var(--cf-text-secondary);">
          {{ selectedMockChallenge.description || '精选打卡习惯，帮助你建立健康的日常作息与生活习惯。' }}
        </p>

        <div class="info-grid">
          <div><span style="color: var(--cf-text-muted); font-size: 12px; display: block;">打卡人数</span><strong>{{ selectedMockChallenge.memberCount }} 人</strong></div>
          <div><span style="color: var(--cf-text-muted); font-size: 12px; display: block;">累计打卡</span><strong>{{ selectedMockChallenge.myTotalDays }} 次</strong></div>
          <div><span style="color: var(--cf-text-muted); font-size: 12px; display: block;">连续天数</span><strong>{{ selectedMockChallenge.myConsecutiveDays }} 天</strong></div>
          <div><span style="color: var(--cf-text-muted); font-size: 12px; display: block;">今日状态</span><strong>{{ selectedMockChallenge.checkedToday ? '已打卡' : '未打卡' }}</strong></div>
        </div>

        <div class="modal-lists-grid" style="display: grid; grid-template-columns: 1fr 1fr; gap: 20px; margin-top: 24px;">
          <!-- Left: History -->
          <section class="preview-section" style="margin: 0;">
            <div class="preview-title" style="margin-bottom: 12px; font-size: 15px; font-weight: bold;">打卡日志 (最近3天)</div>
            <div style="display: flex; flex-direction: column; gap: 8px;">
              <div v-for="dayOffset in [0, 1, 2]" :key="dayOffset" style="display: flex; justify-content: space-between; align-items: center; padding: 10px 12px; border: 1px solid var(--cf-border); border-radius: 8px; background: var(--cf-bg-soft);">
                <span style="font-size: 13px; font-weight: 500;">
                  {{ formatDate(Date.now() - dayOffset * 86400000) }}
                </span>
                <span :style="{ color: (dayOffset === 0 ? selectedMockChallenge.checkedToday : true) ? 'var(--cf-primary)' : 'var(--cf-text-muted)', fontSize: '12px', fontWeight: 'bold' }">
                  {{ (dayOffset === 0 ? selectedMockChallenge.checkedToday : true) ? '已打卡 (+20)' : '未打卡' }}
                </span>
              </div>
            </div>
          </section>

          <!-- Right: Comments -->
          <section class="preview-section" style="margin: 0;">
            <div class="preview-title" style="margin-bottom: 12px; font-size: 15px; font-weight: bold;">大家在讨论</div>
            <div style="display: flex; flex-direction: column; gap: 10px;">
              <div v-for="comment in [
                { user: '学霸学长', text: '今天又是充满活力的一天，打卡！' },
                { user: '代码诗人', text: '自律给我自由，加油冲！' }
              ]" :key="comment.user" style="padding: 10px; border-radius: 8px; background: var(--cf-bg-soft); border: 1px solid var(--cf-border);">
                <div style="display: flex; justify-content: space-between; margin-bottom: 4px; align-items: center;">
                  <strong style="font-size: 12px;">{{ comment.user }}</strong>
                  <span style="font-size: 11px; color: var(--cf-text-muted);">刚刚</span>
                </div>
                <p style="margin: 0; font-size: 12px; color: var(--cf-text-secondary);">{{ comment.text }}</p>
              </div>
            </div>
          </section>
        </div>
      </div>
    </NModal>
  </div>
</template>

<style scoped>
.checkin-page {
  min-height: calc(100vh - 112px);
  display: grid;
  grid-template-columns: 260px minmax(0, 1fr) 360px;
  gap: 28px;
  padding: 8px 0 40px;
  color: var(--cf-text-primary);
  background: var(--cf-page-bg);
}

.checkin-left,
.checkin-right {
  position: sticky;
  top: 8px;
  align-self: start;
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.apple-card,
.hero-card,
.habit-card,
.feed-card,
.quote-card {
  background: var(--cf-card-bg);
  border: 1px solid var(--cf-card-border);
  border-radius: 20px;
  box-shadow: var(--cf-card-shadow);
  backdrop-filter: blur(24px) saturate(150%);
}

.nav-card,
.habits-panel,
.data-card,
.calendar-card,
.rank-card {
  padding: 20px;
}

.left-link {
  width: 100%;
  min-height: 42px;
  border: 0;
  border-radius: 11px;
  background: transparent;
  color: var(--cf-text-secondary);
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 0 12px;
  font-weight: 760;
  cursor: pointer;
}

.left-link.active,
.left-link:hover {
  color: var(--cf-primary);
  background: rgba(0, 216, 191, 0.09);
}

.panel-head,
.section-title-row,
.feed-head,
.calendar-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.panel-head h3,
.section-title-row h2,
.feed-head h2,
.calendar-head h3 {
  margin: 0;
}

.panel-head button,
.section-title-row button,
.sort-btn {
  border: 0;
  background: transparent;
  color: var(--cf-text-muted);
  font-weight: 760;
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.habit-row {
  min-height: 58px;
  display: flex;
  align-items: center;
  gap: 12px;
}

.habit-row strong,
.habit-row p {
  margin: 0;
}

.habit-row p {
  margin-top: 4px;
  color: var(--cf-text-muted);
  font-size: 12px;
}

.habit-icon,
.habit-big-icon {
  border-radius: 12px;
  display: grid;
  place-items: center;
  color: white;
}

.habit-icon {
  width: 34px;
  height: 34px;
}

.sun { background: linear-gradient(145deg, #ffb22e, #ffd36c); }
.blue { background: linear-gradient(145deg, #5ca6ff, #91c7ff); }
.green { background: linear-gradient(145deg, #21b575, #72d8a9); }
.runner { background: linear-gradient(145deg, #6c8cff, #94b0ff); }
.orange { background: linear-gradient(145deg, #ff7f3f, #ffaf72); }

.add-habit {
  width: 100%;
  height: 38px;
  margin-top: 10px;
  border: 1px solid var(--cf-border);
  border-radius: 10px;
  background: var(--cf-bg-glass);
  color: var(--cf-text-secondary);
  font-weight: 780;
}

.checkin-main {
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 28px;
}

.hero-card {
  min-height: 220px;
  position: relative;
  overflow: hidden;
  padding: 34px 36px;
  background:
    linear-gradient(100deg, rgba(255, 255, 255, 0.92) 0%, rgba(243, 248, 255, 0.94) 62%, rgba(226, 242, 255, 0.8) 100%);
}

.hero-card h1 {
  margin: 0 0 8px;
  font-size: 24px;
}

.hero-card p {
  margin: 0;
  color: var(--cf-text-muted);
}

.hero-stats {
  display: grid;
  grid-template-columns: repeat(4, 130px);
  gap: 0;
  margin-top: 46px;
}

.hero-stats div {
  border-right: 1px solid var(--cf-border);
  text-align: center;
}

.hero-stats div:last-child {
  border-right: 0;
}

.hero-stats strong {
  display: block;
  font-size: 28px;
}

.hero-stats span {
  color: var(--cf-text-muted);
  font-size: 13px;
}

.hero-date {
  position: absolute;
  right: 246px;
  top: 28px;
  color: var(--cf-text-muted);
  font-size: 12px;
  line-height: 1.6;
  white-space: pre-line;
}

.student-illustration {
  position: absolute;
  right: 36px;
  bottom: 10px;
  width: 230px;
  height: 190px;
}

.student-illustration .head {
  position: absolute;
  right: 72px;
  top: 24px;
  width: 66px;
  height: 66px;
  border-radius: 50%;
  background: linear-gradient(145deg, #ffd2b5, #ffb58f);
  box-shadow: inset -10px 12px 0 rgba(21, 30, 43, 0.88);
}

.student-illustration .body {
  position: absolute;
  right: 50px;
  top: 82px;
  width: 112px;
  height: 92px;
  border-radius: 42px 42px 20px 20px;
  background: linear-gradient(145deg, #28bf7b, #88ddb1);
}

.student-illustration .desk {
  position: absolute;
  left: 18px;
  right: 4px;
  bottom: 20px;
  height: 18px;
  border-radius: 999px;
  background: #e9eef5;
}

.student-illustration .plant {
  position: absolute;
  right: 0;
  bottom: 38px;
  width: 42px;
  height: 80px;
  border-radius: 50% 50% 8px 8px;
  background: linear-gradient(145deg, #7bd98b, #f5fbf7);
}

.today-section,
.feed-section {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.habit-cards {
  display: grid;
  grid-template-columns: repeat(5, minmax(128px, 1fr));
  gap: 20px;
}

.habit-card {
  min-height: 214px;
  padding: 22px 14px;
  text-align: center;
}

.habit-big-icon {
  width: 42px;
  height: 42px;
  margin: 0 auto 14px;
}

.habit-card h3 {
  margin: 0 0 6px;
  font-size: 16px;
}

.habit-card p,
.habit-card small {
  color: var(--cf-text-muted);
  font-size: 13px;
}

.check-state {
  width: 48px;
  height: 48px;
  margin: 22px auto 8px;
  border-radius: 50%;
  color: white;
  background: var(--cf-primary);
  display: grid;
  place-items: center;
  box-shadow: 0 12px 28px rgba(0, 216, 191, 0.22);
}

.check-state.progress {
  color: var(--cf-primary);
  background: conic-gradient(var(--cf-primary) 60%, #e5edf3 0);
  position: relative;
}

.check-state.progress::after {
  content: '';
  position: absolute;
  inset: 6px;
  border-radius: 50%;
  background: var(--cf-bg-card);
}

.check-state.progress b {
  position: relative;
  z-index: 1;
  font-size: 12px;
}

.check-state.pending {
  background: var(--cf-bg-card);
  border: 3px solid var(--cf-border);
  box-shadow: none;
}

.state-text {
  display: block;
  color: var(--cf-primary);
  font-size: 13px;
}

.feed-head > div {
  display: flex;
  align-items: center;
  gap: 28px;
}

.feed-head button {
  border: 0;
  background: transparent;
  color: var(--cf-text-secondary);
  font-weight: 800;
}

.feed-head button.active {
  height: 38px;
  padding: 0 18px;
  border-radius: 11px;
  color: var(--cf-primary);
  background: rgba(0, 216, 191, 0.1);
  border: 1px solid rgba(0, 216, 191, 0.18);
}

.feed-card {
  padding: 22px;
}

.feed-card header,
.feed-body,
.feed-card footer,
.mini-check-card {
  display: flex;
  align-items: center;
}

.feed-card header {
  gap: 12px;
}

.avatar {
  width: 40px;
  height: 40px;
  border-radius: 50%;
  display: grid;
  place-items: center;
  color: white;
  background: linear-gradient(145deg, #1f2937, #64748b);
  font-weight: 900;
}

.feed-card header strong {
  display: block;
}

.feed-card header small {
  padding: 2px 6px;
  border-radius: 999px;
  background: #52d5bd;
  color: white;
}

.feed-card header p {
  margin: 4px 0 0;
  color: var(--cf-text-muted);
  font-size: 12px;
}

.feed-body {
  justify-content: space-between;
  gap: 24px;
  margin-top: 18px;
}

.feed-body h3 {
  margin: 0 0 10px;
  font-size: 16px;
}

.feed-body h3 span {
  color: var(--cf-primary);
}

.feed-body p {
  margin: 0;
  color: var(--cf-text-muted);
}

.mini-check-card {
  width: 164px;
  min-height: 80px;
  border-radius: 14px;
  background: var(--cf-bg-soft);
  justify-content: center;
  flex-direction: column;
  gap: 3px;
}

.mini-check-card em {
  color: var(--cf-primary);
  font-size: 12px;
  font-style: normal;
  display: inline-flex;
  align-items: center;
  gap: 3px;
}

.feed-card footer {
  gap: 30px;
  margin-top: 16px;
}

.feed-card footer button {
  border: 0;
  background: transparent;
  color: var(--cf-text-muted);
  display: inline-flex;
  align-items: center;
  gap: 6px;
  cursor: pointer;
}

.feed-card footer button.active {
  color: var(--cf-primary);
}

.data-numbers {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12px;
  margin: 22px 0;
  text-align: center;
}

.data-numbers strong {
  display: block;
  font-size: 24px;
}

.data-numbers span {
  color: var(--cf-text-muted);
  font-size: 12px;
}

.chart {
  width: 100%;
  height: 130px;
}

.calendar-head div {
  display: flex;
  align-items: center;
  gap: 14px;
}

.calendar-head button {
  border: 0;
  background: transparent;
  color: var(--cf-text-secondary);
  font-size: 20px;
}

.week-row,
.days-grid {
  display: grid;
  grid-template-columns: repeat(7, 1fr);
  text-align: center;
}

.week-row {
  margin: 22px 0 10px;
  color: var(--cf-text-muted);
  font-size: 13px;
}

.days-grid span {
  min-height: 30px;
  position: relative;
  border-radius: 50%;
  display: grid;
  place-items: center;
  color: var(--cf-text-secondary);
  font-weight: 700;
}

.days-grid span.muted {
  color: #a8b2c1;
}

.days-grid span.active {
  width: 30px;
  height: 30px;
  margin: 0 auto;
  color: white;
  background: var(--cf-primary);
}

.days-grid i {
  position: absolute;
  bottom: 2px;
  width: 4px;
  height: 4px;
  border-radius: 50%;
  background: var(--cf-primary);
}

.rank-row {
  min-height: 42px;
  display: grid;
  grid-template-columns: 22px 32px minmax(0, 1fr) 78px;
  align-items: center;
  gap: 8px;
}

.rank-row > span {
  color: #ffb22e;
  font-weight: 900;
}

.rank-row b {
  width: 28px;
  height: 28px;
  border-radius: 50%;
  display: grid;
  place-items: center;
  color: white;
  background: linear-gradient(145deg, #1f2937, #64748b);
}

.rank-row strong,
.rank-row em {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 13px;
}

.rank-row em {
  color: var(--cf-text-muted);
  font-style: normal;
}

.quote-card {
  padding: 30px;
  background: linear-gradient(145deg, rgba(234, 244, 255, 0.96), rgba(255, 255, 255, 0.92));
}

.quote-card span {
  color: var(--cf-primary);
  font-size: 48px;
  line-height: 0.6;
}

.quote-card p {
  margin: 8px 0 18px;
  font-weight: 900;
  line-height: 1.7;
}

.quote-card strong {
  display: block;
  color: #3b74c8;
  text-align: right;
}

.loading-state {
  padding: 40px;
  text-align: center;
}

.create-form {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.create-form label {
  color: var(--cf-text-secondary);
  font-weight: 800;
}

.data-detail-list {
  display: grid;
  gap: 12px;
}

.data-detail-list article {
  padding: 16px;
  border: 1px solid var(--cf-border);
  border-radius: 16px;
  background: var(--cf-bg-soft);
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.data-detail-list strong {
  color: var(--cf-primary);
  font-size: 26px;
}

.data-detail-list span {
  color: var(--cf-text-secondary);
  font-weight: 800;
}

.create-actions {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  margin-top: 8px;
}

:global(.create-challenge-modal.n-card) {
  width: min(520px, calc(100vw - 32px));
  border-radius: 18px;
}

@media (max-width: 1320px) {
  .checkin-page {
    grid-template-columns: 230px minmax(0, 1fr) 320px;
    gap: 20px;
  }

  .habit-cards {
    grid-template-columns: repeat(3, minmax(150px, 1fr));
  }
}

@media (max-width: 1080px) {
  .checkin-page {
    grid-template-columns: 220px minmax(0, 1fr);
  }

  .checkin-right {
    display: none;
  }
}

@media (max-width: 760px) {
  .checkin-page {
    display: flex;
    flex-direction: column;
  }

  .checkin-left,
  .checkin-right {
    position: static;
  }

  .habits-panel {
    display: none;
  }

  .hero-stats,
  .habit-cards {
    grid-template-columns: 1fr 1fr;
  }

  .student-illustration,
  .hero-date {
    display: none;
  }

  .feed-body {
    align-items: flex-start;
    flex-direction: column;
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

html[data-theme='dark'] .hero-card {
  background: linear-gradient(100deg, rgba(30, 41, 59, 0.92) 0%, rgba(15, 23, 42, 0.94) 62%, rgba(9, 14, 26, 0.8) 100%);
}
html[data-theme='dark'] .student-illustration .desk {
  background: #1e293b;
}
html[data-theme='dark'] .quote-card {
  background: linear-gradient(145deg, rgba(15, 23, 42, 0.86), rgba(30, 41, 59, 0.9));
  strong {
    color: var(--cf-primary);
  }
}
html[data-theme='dark'] .check-state.progress {
  background: conic-gradient(var(--cf-primary) 60%, rgba(255,255,255,0.06) 0);
}
</style>
