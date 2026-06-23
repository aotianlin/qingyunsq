<script setup lang="ts">
import { useRouter } from 'vue-router';
import { NIcon } from 'naive-ui';
import {
  ArrowForwardOutline,
  BookOutline,
  ChatbubblesOutline,
  CloudDownloadOutline,
  CalendarOutline,
  SparklesOutline,
} from '@vicons/ionicons5';
import OfficialAnnouncementCarousel from '@/components/OfficialAnnouncementCarousel.vue';
import type { OfficialAnnouncementItem } from '@/types/announce';

const router = useRouter();

const officialAnnouncements: OfficialAnnouncementItem[] = [
  {
    id: 'home-official-welcome',
    title: '官方公告',
    summary: 'CampusForum 全新首页已升级，校园动态、学习资源与社区活动会在这里集中展示。',
    description: 'CampusForum 全新首页已升级，校园动态、学习资源与社区活动会在这里集中展示。',
    buttonText: '进入广场',
    badge: '官方公告',
    link: '/square',
  },
  {
    id: 'home-ai-ready',
    title: 'AI 助手已就绪',
    summary: '用 AI 摘要、帖子分析和学习建议，让校园讨论更轻盈、更高效。',
    description: '用 AI 摘要、帖子分析和学习建议，让校园讨论更轻盈、更高效。',
    buttonText: '打开 AI 助手',
    badge: '智能推荐',
    link: '/ai',
  },
  {
    id: 'home-checkin-reminder',
    title: '每日打卡提醒',
    summary: '完成今日学习打卡，连续记录你的成长节奏，并领取积分奖励。',
    description: '完成今日学习打卡，连续记录你的成长节奏，并领取积分奖励。',
    buttonText: '去打卡',
    badge: '校园活动',
    link: '/checkin',
  },
];

const modules = [
  {
    title: '广场讨论',
    text: '快速浏览校园帖子、问答和热门话题。',
    icon: ChatbubblesOutline,
  },
  {
    title: '学习圈',
    text: '按课程、兴趣和项目沉淀长期交流。',
    icon: BookOutline,
  },
  {
    title: '资源协作',
    text: '上传、检索和下载可复用学习资料。',
    icon: CloudDownloadOutline,
  },
];

function openAnnouncement(item: OfficialAnnouncementItem) {
  router.push(item.link || '/square');
}
</script>

<template>
  <div class="home-page">
    <!-- Top Header -->
    <header class="home-nav">
      <button class="brand" @click="router.push('/')">
        <span class="brand-mark">CF</span>
        <span class="brand-text">CampusForum</span>
      </button>
      <nav>
        <button class="nav-btn-secondary" @click="router.push('/login')">登录</button>
        <button class="nav-btn-primary" @click="router.push('/register')">加入社区</button>
      </nav>
    </header>

    <section class="home-announcement">
      <OfficialAnnouncementCarousel :items="officialAnnouncements" @open="openAnnouncement" />
    </section>

    <!-- Main Content -->
    <main>
      <!-- Hero Section -->
      <section class="hero-section">
        <div class="hero-content">
          <span class="cf-pill mb-4">Campus Community</span>
          <h1 class="hero-headline">
            校园讨论、学习圈与
            <span class="gradient-text">资源协作</span>
            都在这里
          </h1>
          <p class="hero-sub">
            从广场交流到打卡挑战，从资料共享到 AI 辅助学习，CampusForum
            帮你把校园里的信息流整理成可参与、可沉淀的社区体验。
          </p>
          <div class="hero-actions">
            <button class="primary-action" @click="router.push('/register')">
              立即加入社区
              <NIcon size="18">
                <ArrowForwardOutline />
              </NIcon>
            </button>
            <button class="secondary-action" @click="router.push('/square')">进入广场</button>
          </div>
        </div>

        <!-- Big Hero Workspace Image -->
        <div class="hero-showcase cf-card">
          <img
            src="@/assets/images/hero_workspace.png"
            alt="CampusForum Workspace Mockup"
            class="showcase-img"
          />
        </div>
      </section>

      <!-- Feature Three-Column Grid -->
      <section class="feature-section">
        <div class="feature-grid">
          <article v-for="item in modules" :key="item.title" class="feature-card cf-card">
            <div class="feature-icon-wrapper">
              <NIcon size="24">
                <component :is="item.icon" />
              </NIcon>
            </div>
            <h2>{{ item.title }}</h2>
            <p>{{ item.text }}</p>
          </article>
        </div>
      </section>

      <!-- Hot Topics / Community Updates Section -->
      <section class="topics-section">
        <div class="section-header">
          <h2 class="cf-section-title">社区热门动态</h2>
          <p class="cf-section-subtitle">探索校园里正在发生的精彩讨论与知识分享</p>
        </div>

        <div class="topics-grid">
          <!-- Left: Big Featured Post Card -->
          <article class="featured-card cf-card">
            <div class="featured-img-container">
              <img
                src="@/assets/images/abstract_network.png"
                alt="Featured Topic Cover"
                class="featured-img"
              />
            </div>
            <div class="featured-content">
              <div class="tag-row">
                <span class="topic-tag tag-primary">学术探索</span>
                <span class="topic-tag tag-secondary">指南</span>
              </div>
              <h3 class="featured-title">如何在 CampusForum 中更高效地建立属于你的学术讨论圈</h3>
              <p class="featured-desc">
                在这里，你可以按课程、兴趣和项目创建特定的学习板块，邀请同伴一起讨论学习，沉淀知识。
              </p>
            </div>
          </article>

          <!-- Right: Stacked Mini Post Cards -->
          <div class="mini-cards-stack">
            <!-- Mini Card 1 -->
            <article class="mini-card cf-card">
              <div class="mini-card-header">
                <div class="mini-tag">
                  <NIcon size="16">
                    <SparklesOutline />
                  </NIcon>
                  <span>AI 助手</span>
                </div>
                <span class="mini-time">1 天前</span>
              </div>
              <h3 class="mini-title">AI 问答助手已全面上线</h3>
              <p class="mini-desc">提供智能提炼核心观点，帮您整理讨论方向。</p>
            </article>

            <!-- Mini Card 2 -->
            <article class="mini-card cf-card">
              <div class="mini-card-header">
                <div class="mini-tag tag-blue">
                  <NIcon size="16">
                    <CalendarOutline />
                  </NIcon>
                  <span>资源共享</span>
                </div>
                <span class="mini-time">3 天前</span>
              </div>
              <h3 class="mini-title">2026年数据结构复习考研包已发布</h3>
              <p class="mini-desc">收录了历年期末及考研真题的重难点分析。</p>
            </article>
          </div>
        </div>
      </section>

      <!-- Bottom CTA Banner Section -->
      <section class="bottom-cta-section">
        <div class="cta-banner cf-card">
          <div class="cta-content">
            <h2>开启你的校园协作之旅，与大家一同交流成长</h2>
            <p>加入 CampusForum，探索更高效的学习交流与学术研讨空间。</p>
            <button class="primary-action cta-btn" @click="router.push('/register')">
              立即加入社区
              <NIcon size="18">
                <ArrowForwardOutline />
              </NIcon>
            </button>
          </div>
        </div>
      </section>
    </main>

    <!-- Footer -->
    <footer class="home-footer">
      <div class="footer-grid">
        <div class="footer-brand">
          <span class="footer-logo">CF</span>
          <span class="footer-brand-name">CampusForum</span>
        </div>
        <div class="footer-links">
          <div class="link-group">
            <h4>探索</h4>
            <button @click="router.push('/square')">校园广场</button>
            <button @click="router.push('/spaces')">学习圈子</button>
          </div>
          <div class="link-group">
            <h4>资源</h4>
            <button @click="router.push('/resources')">学习资料</button>
            <button @click="router.push('/checkin')">打卡挑战</button>
          </div>
        </div>
      </div>
      <div class="footer-bottom">
        <p>© 2026 CampusForum. All Rights Reserved. 激发学术与校园社交的无限可能</p>
      </div>
    </footer>
  </div>
</template>

<style scoped>
.home-page {
  min-height: 100vh;
  background: var(--cf-page-bg);
  color: var(--cf-text-primary);
  transition:
    background-color 0.36s ease,
    color 0.36s ease;
}

/* Dark theme overrides */
html[data-theme='dark'] .home-page {
  background: var(--cf-page-bg);
}

.home-nav {
  width: min(1180px, calc(100% - 32px));
  height: 74px;
  margin: 0 auto;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 18px;
}

button {
  border: 0;
  background: transparent;
  cursor: pointer;
  color: inherit;
  font: inherit;
}

.brand {
  display: flex;
  align-items: center;
  gap: 12px;
  font-family: var(--cf-font-heading);
  font-weight: 900;
  font-size: 20px;
}

.brand-mark {
  width: 38px;
  height: 38px;
  border-radius: 13px;
  display: grid;
  place-items: center;
  background: var(--cf-primary);
  color: var(--cf-text-inverse);
  font-size: 13px;
  font-weight: 900;
  box-shadow: var(--cf-shadow-glow);
}

.brand-text {
  font-weight: 900;
  color: var(--cf-text-primary);
}

.home-nav nav {
  display: flex;
  align-items: center;
  gap: 12px;
}

.nav-btn-secondary,
.nav-btn-primary {
  height: 44px;
  padding: 0 18px;
  border-radius: 14px;
  font-weight: 800;
  font-size: 14px;
  transition: all 0.22s ease;
}

.nav-btn-secondary {
  color: var(--cf-text-secondary);
}

.nav-btn-secondary:hover {
  color: var(--cf-primary);
  background: var(--cf-bg-soft);
}

.nav-btn-primary {
  background: var(--cf-gradient-primary);
  color: var(--cf-text-inverse);
  box-shadow: var(--cf-shadow-glow);
}

.nav-btn-primary:hover {
  background: var(--cf-primary-hover);
  transform: translateY(-1px);
}

main {
  width: 100%;
}

.home-announcement {
  width: min(var(--cf-home-content-width, var(--cf-max-width)), calc(100% - var(--cf-home-page-gutter, var(--cf-content-padding))));
  margin: 0 auto;
}

/* Hero Section */
.hero-section {
  width: min(1180px, calc(100% - 32px));
  margin: 0 auto;
  padding: 80px 0 60px;
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
}

.hero-content {
  max-width: 800px;
  margin-bottom: 60px;
  display: flex;
  flex-direction: column;
  align-items: center;
}

.hero-headline {
  font-family: var(--cf-font-heading);
  font-size: clamp(36px, 6vw, 64px);
  font-weight: 900;
  line-height: 1.1;
  letter-spacing: -0.02em;
  margin-bottom: 24px;
}

.hero-sub {
  font-size: clamp(16px, 2.5vw, 19px);
  line-height: 1.8;
  color: var(--cf-text-secondary);
  margin-bottom: 36px;
  max-width: 680px;
}

.hero-actions {
  display: flex;
  justify-content: center;
  gap: 16px;
  flex-wrap: wrap;
}

.primary-action {
  height: 48px;
  padding: 0 24px;
  border-radius: 9px;
  font-weight: 800;
  font-size: 15px;
  background: var(--cf-gradient-primary);
  color: var(--cf-text-inverse);
  display: flex;
  align-items: center;
  gap: 8px;
  box-shadow: var(--cf-shadow-glow);
  transition: all 0.22s ease;
}

.primary-action:hover {
  background: var(--cf-gradient-primary);
  transform: scale(1.02);
  box-shadow: 0 14px 34px rgba(52, 208, 188, 0.26);
}

.secondary-action {
  height: 48px;
  padding: 0 24px;
  border-radius: 9px;
  font-weight: 800;
  font-size: 15px;
  border: 0;
  background: var(--cf-bg-glass);
  color: var(--cf-text-secondary);
  transition: all 0.22s ease;
}

html[data-theme='dark'] .secondary-action {
  background: rgba(20, 20, 24, 0.76);
}

.secondary-action:hover {
  background: var(--cf-bg-soft);
  transform: translateY(-2px);
}

.hero-showcase {
  width: 100%;
  max-width: 1080px;
  border-radius: 28px;
  overflow: hidden;
  box-shadow: var(--cf-shadow-card);
}

.showcase-img {
  width: 100%;
  height: auto;
  display: block;
  transition: transform 0.5s ease;
}

.hero-showcase:hover .showcase-img {
  transform: scale(1.01);
}

/* Feature Grid */
.feature-section {
  width: min(1180px, calc(100% - 32px));
  margin: 80px auto;
}

.feature-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 24px;
}

.feature-card {
  padding: 36px 32px;
  border-radius: 24px;
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  text-align: left;
}

.feature-icon-wrapper {
  width: 52px;
  height: 52px;
  border-radius: 16px;
  display: grid;
  place-items: center;
  background: var(--cf-primary-soft);
  color: var(--cf-primary);
  margin-bottom: 24px;
}

.feature-card h2 {
  font-family: var(--cf-font-heading);
  font-size: 22px;
  font-weight: 800;
  margin-bottom: 12px;
  color: var(--cf-text-primary);
}

.feature-card p {
  color: var(--cf-text-secondary);
  line-height: 1.8;
  font-size: 15px;
}

/* Topics Section */
.topics-section {
  width: min(1180px, calc(100% - 32px));
  margin: 100px auto;
}

.section-header {
  text-align: center;
  margin-bottom: 48px;
}

.cf-section-title {
  font-size: clamp(28px, 4vw, 38px);
  font-weight: 900;
  margin-bottom: 12px;
}

.cf-section-subtitle {
  color: var(--cf-text-muted);
}

.topics-grid {
  display: grid;
  grid-template-columns: 1.2fr 0.8fr;
  gap: 32px;
}

.featured-card {
  border-radius: 24px;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.featured-img-container {
  width: 100%;
  height: 280px;
  overflow: hidden;
}

.featured-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  transition: transform 0.4s ease;
}

.featured-card:hover .featured-img {
  transform: scale(1.03);
}

.featured-content {
  padding: 32px;
  flex-grow: 1;
  display: flex;
  flex-direction: column;
}

.tag-row {
  display: flex;
  gap: 8px;
  margin-bottom: 16px;
}

.topic-tag {
  font-size: 12px;
  font-weight: 700;
  padding: 4px 10px;
  border-radius: 8px;
}

.tag-primary {
  background: var(--cf-primary-soft);
  color: var(--cf-primary);
}

.tag-secondary {
  background: var(--cf-secondary-soft);
  color: var(--cf-secondary);
}

.featured-title {
  font-family: var(--cf-font-heading);
  font-size: 24px;
  font-weight: 800;
  line-height: 1.4;
  margin-bottom: 12px;
  color: var(--cf-text-primary);
}

.featured-desc {
  color: var(--cf-text-secondary);
  line-height: 1.8;
  font-size: 15px;
}

.mini-cards-stack {
  display: flex;
  flex-direction: column;
  gap: 24px;
}

.mini-card {
  padding: 24px;
  border-radius: 20px;
  display: flex;
  flex-direction: column;
}

.mini-card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 14px;
}

.mini-tag {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  font-weight: 800;
  color: var(--cf-secondary);
  background: var(--cf-secondary-soft);
  padding: 4px 10px;
  border-radius: 8px;
}

.mini-tag.tag-blue {
  color: var(--cf-accent-sky);
  background: rgba(56, 189, 248, 0.12);
}

.mini-time {
  font-size: 12px;
  color: var(--cf-text-muted);
}

.mini-title {
  font-family: var(--cf-font-heading);
  font-size: 18px;
  font-weight: 800;
  margin-bottom: 8px;
  color: var(--cf-text-primary);
}

.mini-desc {
  color: var(--cf-text-secondary);
  line-height: 1.6;
  font-size: 14px;
  margin: 0;
}

/* Bottom CTA Banner */
.bottom-cta-section {
  width: min(1180px, calc(100% - 32px));
  margin: 100px auto;
}

.cta-banner {
  border-radius: 28px;
  padding: 60px 40px;
  text-align: center;
  background:
    radial-gradient(circle at 10% 20%, rgba(52, 208, 188, 0.08), transparent 40%),
    radial-gradient(circle at 90% 80%, rgba(142, 185, 238, 0.08), transparent 40%),
    var(--cf-card-bg);
}

html[data-theme='dark'] .cta-banner {
  background:
    radial-gradient(circle at 10% 20%, rgba(93, 220, 205, 0.1), transparent 40%),
    radial-gradient(circle at 90% 80%, rgba(159, 191, 233, 0.08), transparent 40%),
    var(--cf-card-bg);
}

.cta-content {
  max-width: 700px;
  margin: 0 auto;
  display: flex;
  flex-direction: column;
  align-items: center;
}

.cta-content h2 {
  font-family: var(--cf-font-heading);
  font-size: clamp(24px, 4vw, 36px);
  font-weight: 900;
  line-height: 1.3;
  margin-bottom: 16px;
  color: var(--cf-text-primary);
}

.cta-content p {
  font-size: 16px;
  color: var(--cf-text-secondary);
  margin-bottom: 32px;
  line-height: 1.8;
}

.cta-btn {
  align-self: center;
}

/* Footer */
.home-footer {
  width: 100%;
  border-top: 0;
  background: var(--cf-bg-glass);
  backdrop-filter: blur(var(--cf-backdrop-blur));
  -webkit-backdrop-filter: blur(var(--cf-backdrop-blur));
  padding: 64px 32px 32px;
}

.footer-grid {
  width: min(1180px, 100%);
  margin: 0 auto 48px;
  display: grid;
  grid-template-columns: 1.5fr repeat(2, 1fr);
  gap: 40px;
}

.footer-brand {
  display: flex;
  align-items: center;
  gap: 12px;
}

.footer-logo {
  width: 36px;
  height: 36px;
  border-radius: 11px;
  background: var(--cf-primary);
  color: var(--cf-text-inverse);
  display: grid;
  place-items: center;
  font-size: 13px;
  font-weight: 900;
}

.footer-brand-name {
  font-family: var(--cf-font-heading);
  font-size: 18px;
  font-weight: 800;
  color: var(--cf-text-primary);
}

.footer-links {
  grid-column: span 2;
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 24px;
}

.link-group {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 12px;
}

.link-group h4 {
  font-size: 14px;
  font-weight: 800;
  color: var(--cf-text-primary);
  text-transform: uppercase;
  margin-bottom: 8px;
  letter-spacing: 0.05em;
}

.link-group button {
  font-size: 14px;
  color: var(--cf-text-muted);
  transition: color 0.2s ease;
}

.link-group button:hover {
  color: var(--cf-primary);
}

.footer-bottom {
  width: min(1180px, 100%);
  margin: 0 auto;
  border-top: 1px solid var(--cf-border);
  padding-top: 24px;
  text-align: center;
}

.footer-bottom p {
  font-size: 13px;
  color: var(--cf-text-muted);
  margin: 0;
}

/* Responsive adjustments */
@media (max-width: 960px) {
  .hero-section {
    padding-top: 40px;
  }

  .feature-grid {
    grid-template-columns: 1fr;
    gap: 18px;
  }

  .topics-grid {
    grid-template-columns: 1fr;
    gap: 24px;
  }

  .footer-grid {
    grid-template-columns: 1fr;
    gap: 32px;
  }

  .footer-links {
    grid-column: 1;
    grid-template-columns: 1fr 1fr;
  }
}
</style>
