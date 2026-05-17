<script setup lang="ts">
import { useRouter } from 'vue-router';
import { useAuthStore } from '@/stores/auth';
import { ChatbubblesOutline, CheckmarkCircleOutline, PeopleOutline, PlanetOutline, SchoolOutline, SparklesOutline } from '@vicons/ionicons5';

const router = useRouter();
const authStore = useAuthStore();

const stats = [
  { label: '入驻高校', value: '128+', icon: SchoolOutline },
  { label: '活跃学生', value: '56,231+', icon: PeopleOutline },
  { label: '优质帖子', value: '324,112+', icon: ChatbubblesOutline },
  { label: '学习圈', value: '12,421+', icon: PlanetOutline },
];

const featuredSpaces = [
  {
    name: '计算机科学社群',
    category: '科技',
    desc: '围绕算法、AI、系统设计与实习经验展开讨论，适合从新生到竞赛党全阶段加入。',
    members: '14.2k',
    accent: '#0058be',
    cover: 'linear-gradient(135deg, rgba(0,88,190,0.16), rgba(173,198,255,0.32))',
  },
  {
    name: 'UI/UX 爱好者',
    category: '设计',
    desc: '作品集互评、交互拆解、AIGC 设计流与 Figma 进阶技巧。',
    members: '8.5k',
    accent: '#0f766e',
    cover: 'linear-gradient(135deg, rgba(16,185,129,0.14), rgba(110,251,190,0.3))',
  },
  {
    name: '全球交流计划',
    category: '留学',
    desc: '留学申请、交换项目、语言考试与海外学习生活经验共享。',
    members: '12.1k',
    accent: '#b45309',
    cover: 'linear-gradient(135deg, rgba(245,158,11,0.14), rgba(255,220,198,0.34))',
  },
  {
    name: '本科科研网络',
    category: '科研',
    desc: '实验室招募、论文阅读、项目协作与导师沟通建议一站式交流。',
    members: '6.7k',
    accent: '#7c3aed',
    cover: 'linear-gradient(135deg, rgba(124,58,237,0.14), rgba(216,226,255,0.34))',
  },
];

const highlights = [
  {
    title: '跨校学习圈',
    text: '支持校园、学院、兴趣组三级组织形态，自由构建公开或私密学习圈层。',
  },
  {
    title: '任务打卡与积分成长',
    text: '通过挑战、排行榜、成就体系帮助用户长期沉淀学习习惯。',
  },
  {
    title: 'AI 助手深度增强',
    text: '提供摘要、问答、内容分析等能力，让内容生产与学习效率双向提升。',
  },
];

function goPrimary() {
  router.push(authStore.isLoggedIn ? '/square' : '/register');
}
</script>

<template>
  <div class="home-page cf-page-shell">
    <section class="hero-section">
      <div class="hero-copy">
        <span class="cf-pill">Academic Pulse · 现代校园社区体验</span>
        <h1 class="hero-title">
          连接校园，
          <br>
          成就未来
        </h1>
        <p class="hero-desc">
          CampusForum 为高校学习社群打造清爽、高效、现代的交流环境，让知识分享、空间协作、打卡成长与 AI 助手自然融合。
        </p>
        <div class="hero-actions">
          <button
            class="cf-primary-btn"
            @click="goPrimary"
          >
            {{ authStore.isLoggedIn ? '进入广场' : '立即加入' }}
          </button>
          <button
            class="cf-secondary-btn"
            @click="router.push('/spaces')"
          >
            探索学习圈
          </button>
        </div>
        <div class="hero-mini-stats">
          <div
            v-for="item in stats.slice(0, 3)"
            :key="item.label"
            class="mini-stat"
          >
            <strong>{{ item.value }}</strong>
            <span>{{ item.label }}</span>
          </div>
        </div>
      </div>

      <div class="hero-visual cf-card">
        <div class="visual-banner">
          <div class="visual-badge">
            <n-icon size="16">
              <SparklesOutline />
            </n-icon>
            新一代学习社群体验
          </div>
          <div class="visual-grid">
            <div class="visual-panel wide">
              <div class="panel-label">
                热门讨论
              </div>
              <h3>本周最受关注：如何构建属于大学生的高质量知识网络？</h3>
              <p>围绕学习方法、资源协作和项目实战展开的高热度话题持续增长。</p>
            </div>
            <div class="visual-panel accent">
              <div class="panel-label">
                今日打卡
              </div>
              <strong>8,931</strong>
              <span>比昨日增长 18.6%</span>
            </div>
            <div class="visual-panel">
              <div class="panel-label">
                AI 学习助手
              </div>
              <p>摘要、问答、内容分析，多场景支持课件与帖子精读。</p>
            </div>
            <div class="visual-panel">
              <div class="panel-label">
                学习圈
              </div>
              <p>按课程、课题组、兴趣方向创建轻量空间，沉淀共享资料与讨论。</p>
            </div>
          </div>
        </div>
      </div>
    </section>

    <section class="stats-section cf-surface">
      <div
        v-for="stat in stats"
        :key="stat.label"
        class="stat-item"
      >
        <div class="stat-icon">
          <n-icon size="20">
            <component :is="stat.icon" />
          </n-icon>
        </div>
        <div>
          <strong>{{ stat.value }}</strong>
          <span>{{ stat.label }}</span>
        </div>
      </div>
    </section>

    <section class="spaces-section">
      <div class="section-head">
        <div>
          <h2 class="cf-section-title">
            精选学习圈
          </h2>
          <p class="cf-section-subtitle">
            参考 Stitch 已生成的风格重构，为不同学习兴趣提供更具呼吸感的展示布局。
          </p>
        </div>
        <button
          class="cf-ghost-btn"
          @click="router.push('/spaces')"
        >
          查看全部
        </button>
      </div>

      <div class="spaces-grid">
        <article
          v-for="space in featuredSpaces"
          :key="space.name"
          class="space-card cf-card"
          :style="{ background: space.cover }"
        >
          <div class="space-top">
            <span
              class="space-tag"
              :style="{ color: space.accent, background: 'rgba(255,255,255,0.72)' }"
            >{{ space.category }}</span>
            <span class="space-members">{{ space.members }} 成员</span>
          </div>
          <div>
            <h3>{{ space.name }}</h3>
            <p>{{ space.desc }}</p>
          </div>
        </article>
      </div>
    </section>

    <section class="highlights-section">
      <div class="section-head compact">
        <div>
          <h2 class="cf-section-title">
            为什么校园团队会选择 CampusForum
          </h2>
          <p class="cf-section-subtitle">
            对缺失页面的后续设计，也会沿用这一套蓝白、轻盈、信息卡片化的视觉语言。
          </p>
        </div>
      </div>

      <div class="highlight-grid">
        <article
          v-for="item in highlights"
          :key="item.title"
          class="highlight-card cf-card"
        >
          <div class="highlight-icon">
            <n-icon size="20">
              <CheckmarkCircleOutline />
            </n-icon>
          </div>
          <h3>{{ item.title }}</h3>
          <p>{{ item.text }}</p>
        </article>
      </div>
    </section>
  </div>
</template>

<style scoped lang="scss">
.home-page {
  display: flex;
  flex-direction: column;
  gap: 28px;
}

.hero-section {
  display: grid;
  grid-template-columns: minmax(0, 1.05fr) minmax(0, 0.95fr);
  gap: 28px;
  align-items: stretch;
}

.hero-copy,
.hero-visual {
  min-height: 520px;
}

.hero-copy {
  display: flex;
  flex-direction: column;
  justify-content: center;
  padding: 8px 0;
}

.hero-title {
  margin: 18px 0 16px;
  font-family: var(--cf-font-heading);
  font-size: clamp(44px, 5vw, 68px);
  line-height: 1.02;
  letter-spacing: -0.03em;
}

.hero-desc {
  margin: 0;
  max-width: 560px;
  color: var(--cf-text-secondary);
  font-size: 18px;
  line-height: 1.75;
}

.hero-actions {
  display: flex;
  gap: 12px;
  margin-top: 28px;
}

.hero-mini-stats {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 14px;
  margin-top: 34px;
}

.mini-stat {
  padding: 18px;
  border-radius: 18px;
  background: rgba(255, 255, 255, 0.66);
  border: 1px solid rgba(217, 226, 242, 0.75);
  backdrop-filter: blur(8px);

  strong {
    display: block;
    font-family: var(--cf-font-heading);
    font-size: 22px;
    margin-bottom: 6px;
  }

  span {
    color: var(--cf-text-muted);
    font-size: 13px;
  }
}

.hero-visual {
  padding: 24px;
}

.visual-banner {
  height: 100%;
  border-radius: 24px;
  background: linear-gradient(180deg, rgba(229, 238, 255, 0.8), rgba(255, 255, 255, 0.96));
  padding: 24px;
}

.visual-badge {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 8px 14px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.72);
  color: var(--cf-primary);
  font-size: 13px;
  font-weight: 700;
}

.visual-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
  margin-top: 22px;
  height: calc(100% - 48px);
}

.visual-panel {
  padding: 20px;
  border-radius: 20px;
  background: rgba(255, 255, 255, 0.76);
  border: 1px solid rgba(217, 226, 242, 0.75);
  display: flex;
  flex-direction: column;
  gap: 10px;
  justify-content: space-between;

  h3,
  strong {
    margin: 0;
    font-family: var(--cf-font-heading);
    color: var(--cf-text-primary);
  }

  p,
  span {
    margin: 0;
    color: var(--cf-text-secondary);
    line-height: 1.7;
  }

  &.wide {
    grid-column: span 2;
    min-height: 210px;
  }

  &.accent {
    background: linear-gradient(180deg, rgba(0, 88, 190, 0.08), rgba(255, 255, 255, 0.9));
  }
}

.panel-label {
  color: var(--cf-text-muted);
  font-size: 12px;
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 0.08em;
}

.stats-section {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
  padding: 18px;
}

.stat-item {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 16px;
  border-radius: 18px;
  background: linear-gradient(180deg, rgba(255,255,255,0.92), rgba(239,244,255,0.86));

  strong {
    display: block;
    font-family: var(--cf-font-heading);
    font-size: 22px;
    margin-bottom: 4px;
  }

  span {
    color: var(--cf-text-muted);
    font-size: 13px;
  }
}

.stat-icon {
  width: 44px;
  height: 44px;
  border-radius: 14px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--cf-primary-soft);
  color: var(--cf-primary);
}

.spaces-section,
.highlights-section {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.section-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-end;
  gap: 20px;
}

.section-head.compact {
  align-items: flex-start;
}

.spaces-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 18px;
}

.space-card {
  min-height: 220px;
  padding: 22px;
  display: flex;
  flex-direction: column;
  justify-content: space-between;

  h3 {
    margin: 0 0 10px;
    font-family: var(--cf-font-heading);
    font-size: 24px;
  }

  p {
    margin: 0;
    color: var(--cf-text-secondary);
    line-height: 1.75;
  }
}

.space-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
}

.space-tag,
.space-members {
  display: inline-flex;
  align-items: center;
  min-height: 34px;
  padding: 0 12px;
  border-radius: 999px;
  font-size: 13px;
  font-weight: 700;
}

.space-members {
  color: var(--cf-text-secondary);
  background: rgba(255,255,255,0.65);
}

.highlight-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 18px;
}

.highlight-card {
  padding: 22px;

  h3 {
    margin: 14px 0 10px;
    font-family: var(--cf-font-heading);
    font-size: 22px;
  }

  p {
    margin: 0;
    color: var(--cf-text-secondary);
    line-height: 1.72;
  }
}

.highlight-icon {
  width: 46px;
  height: 46px;
  border-radius: 14px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--cf-primary-soft);
  color: var(--cf-primary);
}

@media (max-width: 960px) {
  .hero-section,
  .stats-section,
  .spaces-grid,
  .highlight-grid {
    grid-template-columns: 1fr;
  }

  .hero-copy,
  .hero-visual {
    min-height: auto;
  }

  .hero-mini-stats,
  .visual-grid {
    grid-template-columns: 1fr;
  }

  .visual-panel.wide {
    grid-column: span 1;
  }

  .section-head {
    flex-direction: column;
    align-items: flex-start;
  }
}

@media (max-width: 640px) {
  .hero-title {
    font-size: 40px;
  }

  .hero-actions {
    flex-direction: column;
  }
}
</style>
