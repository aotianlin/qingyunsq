<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { NButton, NEmpty, NIcon, NSpin, NTag, useMessage } from 'naive-ui';
import {
  CalendarOutline,
  ChatbubblesOutline,
  EyeOutline,
  HeartOutline,
  MedalOutline,
  PaperPlaneOutline,
  PersonAddOutline,
  PodiumOutline,
  ReaderOutline,
  SchoolOutline,
  SparklesOutline,
  TrophyOutline,
} from '@vicons/ionicons5';
import { getUserById } from '@/api/users';
import { follow, getFollowCounts, isFollowing, unfollow } from '@/api/follows';
import { getPosts } from '@/api/posts';
import { getUserAchievements } from '@/api/achievement';
import { useAuthStore } from '@/stores/auth';
import MentionText from '@/components/MentionText.vue';
import type { AchievementVO } from '@/types/achievement';
import type { PostVO } from '@/types/post';
import type { UserVO } from '@/types/user';

interface ProfileParticle {
  x: number;
  y: number;
  baseX: number;
  baseY: number;
  vx: number;
  vy: number;
  size: number;
  alpha: number;
  layer: number;
  phase: number;
}

const route = useRoute();
const router = useRouter();
const message = useMessage();
const authStore = useAuthStore();

const user = ref<UserVO | null>(null);
const loading = ref(true);
const following = ref(false);
const followLoading = ref(false);
const followerCount = ref(0);
const followingCount = ref(0);
const recentPosts = ref<PostVO[]>([]);
const achievements = ref<AchievementVO[]>([]);
const canvasRef = ref<HTMLCanvasElement | null>(null);

const currentUserId = computed(() => authStore.user?.id);
const isSelf = computed(() => !!user.value && currentUserId.value === user.value.id);
const campusLine = computed(() => {
  if (!user.value) return '校园资料待同步';
  return [user.value.college, user.value.major, user.value.grade].filter(Boolean).join(' / ') || '校园资料待完善';
});
const avatarText = computed(() => user.value?.nickname?.charAt(0)?.toUpperCase() || 'U');
const awardedAchievements = computed(() => achievements.value.filter((item) => item.awarded));
const featuredAchievements = computed(() => {
  const unlocked = awardedAchievements.value;
  return (unlocked.length > 0 ? unlocked : achievements.value).slice(0, 5);
});
const achievementProgress = computed(() => {
  if (achievements.value.length === 0) return '0/0';
  return `${awardedAchievements.value.length}/${achievements.value.length}`;
});
const profileCompleteness = computed(() => {
  if (!user.value) return 0;
  const fields = [user.value.avatarUrl, user.value.bio, user.value.college, user.value.major, user.value.grade];
  return Math.round((fields.filter(Boolean).length / fields.length) * 100);
});
const recentImpact = computed(() => recentPosts.value.reduce((sum, post) => sum + post.likeCount + post.commentCount, 0));

let particles: ProfileParticle[] = [];
let animationFrameId = 0;
let resizeObserver: ResizeObserver | null = null;
const pointer = {
  active: false,
  x: 0,
  y: 0,
};
const canvasSize = {
  width: 0,
  height: 0,
  dpr: 1,
};

async function refreshFollowState(targetId = user.value?.id) {
  if (!targetId || !currentUserId.value || currentUserId.value === targetId) {
    following.value = false;
    return;
  }

  try {
    following.value = await isFollowing(targetId);
  } catch {
    following.value = false;
  }
}

async function loadUser() {
  const id = Number(route.params.id);
  if (!id) {
    user.value = null;
    recentPosts.value = [];
    achievements.value = [];
    loading.value = false;
    return;
  }

  loading.value = true;
  try {
    const [profile, counts, posts, userAchievements] = await Promise.all([
      getUserById(id),
      getFollowCounts(id).catch(() => ({ followers: 0, following: 0 })),
      getPosts({ scope: 'SQUARE', authorId: id, sort: 'latest', limit: 6 }).catch(() => []),
      getUserAchievements(id).catch(() => []),
    ]);

    user.value = profile;
    followerCount.value = counts.followers;
    followingCount.value = counts.following;
    recentPosts.value = posts;
    achievements.value = userAchievements;
    await refreshFollowState(id);
  } catch {
    user.value = null;
    recentPosts.value = [];
    achievements.value = [];
  } finally {
    loading.value = false;
  }
}

function goFollows(tab: 'followers' | 'following') {
  if (!user.value) return;
  router.push({ path: `/users/${user.value.id}/follows`, query: { tab } });
}

function goMessage() {
  if (!user.value) return;
  router.push(`/messages?peer=${user.value.id}`);
}

function goProfileEdit() {
  router.push('/profile');
}

function goPost(postId: number) {
  router.push(`/posts/${postId}`);
}

async function toggleFollow() {
  if (!user.value) return;
  if (!currentUserId.value) {
    router.push('/login');
    return;
  }

  followLoading.value = true;
  try {
    if (following.value) {
      await unfollow(user.value.id);
      following.value = false;
      followerCount.value = Math.max(0, followerCount.value - 1);
      message.success('已取消关注');
    } else {
      await follow(user.value.id);
      following.value = true;
      followerCount.value += 1;
      message.success('关注成功');
    }
  } catch {
    message.error('操作失败');
  } finally {
    followLoading.value = false;
  }
}

function formatDate(value?: string | null) {
  if (!value) return '暂无记录';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return '暂无记录';
  return date.toLocaleDateString('zh-CN', { year: 'numeric', month: 'short', day: 'numeric' });
}

function formatPostTime(value: string) {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return '刚刚';
  return date.toLocaleString('zh-CN', { month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' });
}

function postTitle(post: PostVO) {
  return post.title || '无标题动态';
}

function postPreview(content: string) {
  const normalized = content.replace(/\s+/g, ' ').trim();
  return normalized.length > 140 ? `${normalized.slice(0, 140)}...` : normalized;
}

function trackPointer(event: PointerEvent) {
  const canvas = canvasRef.value;
  if (!canvas) return;
  const rect = canvas.getBoundingClientRect();
  pointer.active = true;
  pointer.x = event.clientX - rect.left;
  pointer.y = event.clientY - rect.top;
}

function leavePointer() {
  pointer.active = false;
}

function createParticles(width: number, height: number) {
  const count = Math.min(140, Math.max(72, Math.round((width * height) / 11500)));
  particles = Array.from({ length: count }, (_, index) => {
    const columnCount = Math.ceil(Math.sqrt(count * 1.8));
    const row = Math.floor(index / columnCount);
    const column = index % columnCount;
    const jitterX = (Math.random() - 0.5) * 42;
    const jitterY = (Math.random() - 0.5) * 36;
    const baseX = ((column + 0.5) / columnCount) * width + jitterX;
    const verticalBand = 0.18 + ((row % 9) / 9) * 0.58;
    const wave = Math.sin(column * 0.72 + row * 0.43) * 34;
    const baseY = verticalBand * height + wave + jitterY;

    return {
      x: baseX,
      y: baseY,
      baseX,
      baseY,
      vx: 0,
      vy: 0,
      size: 1.1 + Math.random() * 1.8,
      alpha: 0.35 + Math.random() * 0.5,
      layer: index % 3,
      phase: Math.random() * Math.PI * 2,
    };
  });
}

function syncCanvasSize(context: CanvasRenderingContext2D) {
  const canvas = canvasRef.value;
  if (!canvas) return;
  const rect = canvas.getBoundingClientRect();
  const width = Math.max(320, rect.width);
  const height = Math.max(560, rect.height);
  const dpr = Math.min(window.devicePixelRatio || 1, 2);
  const changed = width !== canvasSize.width || height !== canvasSize.height || dpr !== canvasSize.dpr;

  if (!changed) return;

  canvasSize.width = width;
  canvasSize.height = height;
  canvasSize.dpr = dpr;
  canvas.width = Math.floor(width * dpr);
  canvas.height = Math.floor(height * dpr);
  context.setTransform(dpr, 0, 0, dpr, 0, 0);
  createParticles(width, height);
}

function drawProfileParticles(context: CanvasRenderingContext2D) {
  syncCanvasSize(context);
  const { width, height } = canvasSize;
  context.clearRect(0, 0, width, height);
  context.save();
  context.globalCompositeOperation = 'lighter';

  const now = performance.now() * 0.001;

  // 背景粒子采用“锚点回弹 + 鼠标吸引 + 轻微切向速度”的方式，让节点在聚拢后自然发散回原位。
  for (const particle of particles) {
    const anchorY = particle.baseY + Math.sin(now * 0.8 + particle.phase) * 9;
    particle.vx += (particle.baseX - particle.x) * 0.014;
    particle.vy += (anchorY - particle.y) * 0.014;

    if (pointer.active) {
      const dx = pointer.x - particle.x;
      const dy = pointer.y - particle.y;
      const distance = Math.hypot(dx, dy) || 1;
      const radius = 240;
      if (distance < radius) {
        const force = (1 - distance / radius) ** 2;
        particle.vx += (dx / distance) * force * 0.22;
        particle.vy += (dy / distance) * force * 0.22;
        particle.vx += (-dy / distance) * force * 0.035;
        particle.vy += (dx / distance) * force * 0.035;
      }
    }

    particle.vx *= 0.86;
    particle.vy *= 0.86;
    particle.x += particle.vx;
    particle.y += particle.vy;
  }

  for (let i = 0; i < particles.length; i += 1) {
    const a = particles[i];
    for (let j = i + 1; j < particles.length; j += 1) {
      const b = particles[j];
      const distance = Math.hypot(a.x - b.x, a.y - b.y);
      if (distance > 112) continue;
      const alpha = (1 - distance / 112) * 0.18;
      context.strokeStyle = `rgba(99, 102, 241, ${alpha})`;
      context.lineWidth = 1;
      context.beginPath();
      context.moveTo(a.x, a.y);
      context.lineTo(b.x, b.y);
      context.stroke();
    }
  }

  for (const particle of particles) {
    const pulse = (Math.sin(now * 1.7 + particle.phase) + 1) * 0.5;
    const radius = particle.size + pulse * 0.8;
    const color = particle.layer === 0 ? '99, 102, 241' : particle.layer === 1 ? '139, 92, 246' : '56, 189, 248';
    context.fillStyle = `rgba(${color}, ${0.28 + particle.alpha * 0.34})`;
    context.beginPath();
    context.arc(particle.x, particle.y, radius, 0, Math.PI * 2);
    context.fill();
  }

  context.restore();
  animationFrameId = requestAnimationFrame(() => drawProfileParticles(context));
}

function initProfileParticles() {
  const canvas = canvasRef.value;
  if (!canvas) return;
  const context = canvas.getContext('2d');
  if (!context) return;

  resizeObserver?.disconnect();
  resizeObserver = new ResizeObserver(() => syncCanvasSize(context));
  resizeObserver.observe(canvas);
  syncCanvasSize(context);
  animationFrameId = requestAnimationFrame(() => drawProfileParticles(context));
}

function cleanupProfileParticles() {
  if (animationFrameId) {
    cancelAnimationFrame(animationFrameId);
  }
  resizeObserver?.disconnect();
  resizeObserver = null;
  particles = [];
}

onMounted(() => {
  loadUser();
});

onUnmounted(cleanupProfileParticles);

watch(() => route.params.id, loadUser);

watch(currentUserId, () => {
  refreshFollowState();
});
</script>

<template>
  <div
    class="user-page"
    @pointermove="trackPointer"
    @pointerleave="leavePointer"
  >
    <canvas
      ref="canvasRef"
      class="profile-particles"
      aria-hidden="true"
    />

    <div class="profile-content">
      <template v-if="loading">
        <div class="loading-state glass-surface">
          <NSpin size="large" />
          <span>正在加载主页</span>
        </div>
      </template>

      <template v-else-if="user">
        <section class="profile-hero glass-surface">
          <div class="hero-main">
            <div class="avatar-frame">
              <img
                v-if="user.avatarUrl"
                :src="user.avatarUrl"
                :alt="user.nickname"
              />
              <span v-else>{{ avatarText }}</span>
            </div>

            <div class="identity-block">
              <div class="identity-eyebrow">
                <NIcon>
                  <SchoolOutline />
                </NIcon>
                <span>{{ campusLine }}</span>
              </div>
              <h1>{{ user.nickname }}</h1>
              <p class="profile-bio">
                {{ user.bio || '这个人还没有留下简介，但主页动态会记录正在发生的校园连接。' }}
              </p>

              <div class="hero-actions">
                <NButton
                  v-if="isSelf"
                  type="primary"
                  size="large"
                  @click="goProfileEdit"
                >
                  编辑资料
                </NButton>
                <template v-else-if="currentUserId">
                  <NButton
                    :type="following ? 'default' : 'primary'"
                    size="large"
                    :loading="followLoading"
                    @click="toggleFollow"
                  >
                    <template #icon>
                      <NIcon>
                        <PersonAddOutline />
                      </NIcon>
                    </template>
                    {{ following ? '已关注' : '关注' }}
                  </NButton>
                  <NButton
                    size="large"
                    secondary
                    @click="goMessage"
                  >
                    <template #icon>
                      <NIcon>
                        <PaperPlaneOutline />
                      </NIcon>
                    </template>
                    发私信
                  </NButton>
                </template>
              </div>
            </div>
          </div>

          <div class="signal-panel">
            <div class="panel-title">
              <NIcon>
                <PodiumOutline />
              </NIcon>
              <span>活跃档案</span>
            </div>

            <div class="stats-grid">
              <button
                type="button"
                class="stat-cell clickable"
                @click="goFollows('followers')"
              >
                <strong>{{ followerCount }}</strong>
                <span>粉丝</span>
              </button>
              <button
                type="button"
                class="stat-cell clickable"
                @click="goFollows('following')"
              >
                <strong>{{ followingCount }}</strong>
                <span>关注</span>
              </button>
              <div class="stat-cell">
                <strong>{{ recentPosts.length }}</strong>
                <span>近期动态</span>
              </div>
            </div>

            <div class="completion-box">
              <div class="completion-top">
                <span>资料完整度</span>
                <strong>{{ profileCompleteness }}%</strong>
              </div>
              <div class="progress-track">
                <div
                  class="progress-fill"
                  :style="{ width: `${profileCompleteness}%` }"
                />
              </div>
            </div>
          </div>
        </section>

        <div class="profile-grid">
          <section class="activity-panel glass-surface">
            <div class="section-heading">
              <div>
                <span class="section-kicker">动态轨迹</span>
                <h2>最近发布</h2>
              </div>
              <div class="impact-pill">
                <NIcon>
                  <SparklesOutline />
                </NIcon>
                {{ recentImpact }} 次互动
              </div>
            </div>

            <div
              v-if="recentPosts.length"
              class="post-list"
            >
              <article
                v-for="post in recentPosts"
                :key="post.id"
                class="post-item"
                @click="goPost(post.id)"
              >
                <div class="post-item-top">
                  <h3>{{ postTitle(post) }}</h3>
                  <time>{{ formatPostTime(post.createdAt) }}</time>
                </div>
                <p class="post-preview">
                  <MentionText :text="postPreview(post.content)" />
                </p>
                <div class="post-meta">
                  <span>
                    <NIcon><EyeOutline /></NIcon>
                    {{ post.viewCount }}
                  </span>
                  <span>
                    <NIcon><HeartOutline /></NIcon>
                    {{ post.likeCount }}
                  </span>
                  <span>
                    <NIcon><ChatbubblesOutline /></NIcon>
                    {{ post.commentCount }}
                  </span>
                  <NTag
                    v-if="post.isEssence === 1"
                    size="small"
                    type="warning"
                    round
                  >
                    精华
                  </NTag>
                </div>
              </article>
            </div>

            <NEmpty
              v-else
              description="暂时还没有公开动态"
              class="empty-block"
            >
              <template #icon>
                <NIcon>
                  <ReaderOutline />
                </NIcon>
              </template>
            </NEmpty>
          </section>

          <aside class="side-column">
            <section class="about-panel glass-surface">
              <div class="section-heading compact">
                <div>
                  <span class="section-kicker">校园身份</span>
                  <h2>资料卡</h2>
                </div>
              </div>

              <div class="info-list">
                <div class="info-row">
                  <span>学院</span>
                  <strong>{{ user.college || '未填写' }}</strong>
                </div>
                <div class="info-row">
                  <span>专业</span>
                  <strong>{{ user.major || '未填写' }}</strong>
                </div>
                <div class="info-row">
                  <span>年级</span>
                  <strong>{{ user.grade || '未填写' }}</strong>
                </div>
                <div class="info-row">
                  <span>加入时间</span>
                  <strong>{{ formatDate(user.createdAt) }}</strong>
                </div>
              </div>
            </section>

            <section class="achievement-panel glass-surface">
              <div class="section-heading compact">
                <div>
                  <span class="section-kicker">成长徽章</span>
                  <h2>成就墙</h2>
                </div>
                <div class="achievement-count">
                  <NIcon>
                    <TrophyOutline />
                  </NIcon>
                  {{ achievementProgress }}
                </div>
              </div>

              <div
                v-if="featuredAchievements.length"
                class="achievement-list"
              >
                <div
                  v-for="achievement in featuredAchievements"
                  :key="achievement.id"
                  class="achievement-item"
                  :class="{ locked: !achievement.awarded }"
                >
                  <div class="achievement-icon">
                    <NIcon>
                      <MedalOutline />
                    </NIcon>
                  </div>
                  <div class="achievement-copy">
                    <strong>{{ achievement.name }}</strong>
                    <span>{{ achievement.description }}</span>
                  </div>
                  <NTag
                    size="small"
                    :type="achievement.awarded ? 'success' : 'default'"
                    round
                  >
                    {{ achievement.awarded ? '已解锁' : '待解锁' }}
                  </NTag>
                </div>
              </div>

              <NEmpty
                v-else
                description="暂无成就数据"
                class="empty-block compact-empty"
              >
                <template #icon>
                  <NIcon>
                    <MedalOutline />
                  </NIcon>
                </template>
              </NEmpty>
            </section>

            <section class="calendar-panel glass-surface">
              <div class="mini-metric">
                <NIcon>
                  <CalendarOutline />
                </NIcon>
                <div>
                  <span>最近登录</span>
                  <strong>{{ formatDate(user.lastLoginAt) }}</strong>
                </div>
              </div>
            </section>
          </aside>
        </div>
      </template>

      <template v-else>
        <div class="not-found glass-surface">
          <NEmpty description="用户不存在或已不可访问" />
        </div>
      </template>
    </div>
  </div>
</template>

<style scoped lang="scss">
.user-page {
  position: relative;
  min-height: calc(100vh - 112px);
  padding: 8px 0 40px;
  color: var(--cf-text-primary);
  background: var(--cf-page-bg);
  isolation: isolate;
  overflow: visible;
}

.profile-particles {
  display: none;
}

.profile-content {
  position: relative;
  z-index: 1;
  max-width: none;
  margin: 0 auto;
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.glass-surface {
  background: var(--cf-card-bg);
  border: 1px solid var(--cf-card-border);
  border-radius: 20px;
  box-shadow: var(--cf-card-shadow);
  backdrop-filter: blur(24px) saturate(150%);
  -webkit-backdrop-filter: blur(24px) saturate(150%);
}

.loading-state,
.not-found {
  min-height: 360px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12px;
  color: var(--cf-text-secondary);
}

.profile-hero {
  position: relative;
  display: grid;
  grid-template-columns: minmax(0, 1fr) 360px;
  gap: 30px;
  padding: 30px;
  overflow: hidden;
}

.profile-hero::before {
  display: none;
}

.hero-main,
.signal-panel {
  position: relative;
  z-index: 1;
}

.hero-main {
  display: flex;
  gap: 24px;
  align-items: center;
  min-width: 0;
}

.avatar-frame {
  width: 108px;
  height: 108px;
  flex: 0 0 108px;
  border-radius: 50%;
  padding: 4px;
  background: linear-gradient(135deg, var(--cf-primary), #00a88f);
  box-shadow: 0 18px 42px rgba(0, 191, 168, 0.18);
}

.avatar-frame img,
.avatar-frame span {
  width: 100%;
  height: 100%;
  border-radius: inherit;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--cf-bg-base);
  color: #fff;
  object-fit: cover;
  font-size: 42px;
  font-weight: 800;
}

.avatar-frame span {
  color: var(--cf-primary);
}

.identity-block {
  min-width: 0;
}

.identity-eyebrow {
  display: flex;
  align-items: center;
  gap: 8px;
  color: var(--cf-text-secondary);
  font-size: 14px;
  margin-bottom: 10px;
  overflow-wrap: anywhere;
}

.identity-block h1 {
  margin: 0;
  font-size: clamp(34px, 5vw, 54px);
  line-height: 1.05;
  letter-spacing: 0;
  color: var(--cf-text-primary);
  overflow-wrap: anywhere;
}

.profile-bio {
  max-width: 660px;
  margin: 16px 0 0;
  color: var(--cf-text-secondary);
  font-size: 15px;
  line-height: 1.7;
  overflow-wrap: anywhere;
}

.hero-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  margin-top: 24px;
}

.signal-panel {
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  gap: 22px;
  padding-left: 26px;
  border-left: 1px solid var(--cf-border);
}

.panel-title,
.impact-pill,
.achievement-count,
.mini-metric {
  display: flex;
  align-items: center;
  gap: 8px;
}

.panel-title {
  color: var(--cf-text-primary);
  font-weight: 700;
}

.stats-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
}

.stat-cell {
  min-height: 82px;
  border: 1px solid var(--cf-border);
  border-radius: 14px;
  background: color-mix(in srgb, var(--cf-primary) 5%, var(--cf-bg-card));
  color: var(--cf-text-primary);
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  justify-content: center;
  padding: 14px;
  text-align: left;
  font: inherit;
}

.stat-cell strong {
  font-size: 28px;
  line-height: 1;
  color: var(--cf-text-primary);
}

.stat-cell span {
  margin-top: 8px;
  color: var(--cf-text-secondary);
  font-size: 13px;
}

.stat-cell.clickable {
  cursor: pointer;
  transition: border-color 0.2s ease, background 0.2s ease, transform 0.2s ease;
}

.stat-cell.clickable:hover {
  border-color: color-mix(in srgb, var(--cf-primary) 42%, transparent);
  background: color-mix(in srgb, var(--cf-primary) 9%, var(--cf-bg-card));
  transform: translateY(-2px);
}

.completion-box {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.completion-top {
  display: flex;
  justify-content: space-between;
  color: var(--cf-text-secondary);
  font-size: 13px;
}

.completion-top strong {
  color: var(--cf-text-primary);
}

.progress-track {
  height: 8px;
  border-radius: 999px;
  background: var(--cf-bg-muted);
  overflow: hidden;
}

.progress-fill {
  height: 100%;
  border-radius: inherit;
  background: linear-gradient(90deg, var(--cf-primary), var(--cf-primary-alt));
  box-shadow: var(--cf-progress-shadow);
  transition: width 0.3s ease;
}

.profile-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 360px;
  gap: 20px;
  align-items: start;
}

.activity-panel,
.about-panel,
.achievement-panel,
.calendar-panel {
  padding: 24px;
}

.side-column {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.section-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 20px;
}

.section-heading.compact {
  margin-bottom: 18px;
}

.section-kicker {
  color: var(--cf-primary);
  font-size: 12px;
  font-weight: 700;
}

.section-heading h2 {
  margin: 4px 0 0;
  font-size: 22px;
  line-height: 1.25;
  letter-spacing: 0;
}

.impact-pill,
.achievement-count {
  flex: 0 0 auto;
  height: 32px;
  padding: 0 12px;
  border-radius: 999px;
  background: var(--cf-primary-soft);
  color: var(--cf-primary);
  font-size: 13px;
}

.post-list {
  display: flex;
  flex-direction: column;
}

.post-item {
  padding: 18px 0;
  border-top: 1px solid var(--cf-border);
  cursor: pointer;
  transition: border-color 0.2s ease, transform 0.2s ease;
}

.post-item:first-child {
  border-top: 0;
  padding-top: 0;
}

.post-item:last-child {
  padding-bottom: 0;
}

.post-item:hover {
  border-color: color-mix(in srgb, var(--cf-primary) 34%, transparent);
  transform: translateX(4px);
}

.post-item-top {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  align-items: flex-start;
}

.post-item h3 {
  margin: 0;
  color: var(--cf-text-primary);
  font-size: 17px;
  line-height: 1.4;
  overflow-wrap: anywhere;
}

.post-item time {
  flex: 0 0 auto;
  color: var(--cf-text-muted);
  font-size: 12px;
}

.post-preview {
  margin: 10px 0 14px;
  color: var(--cf-text-secondary);
  line-height: 1.65;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  overflow-wrap: anywhere;
}

.post-preview :deep(.mention-link) {
  color: var(--cf-primary);
  text-decoration: none;
}

.post-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 14px;
  align-items: center;
  color: var(--cf-text-secondary);
  font-size: 13px;
}

.post-meta span {
  display: inline-flex;
  align-items: center;
  gap: 5px;
}

.info-list {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.info-row {
  display: flex;
  justify-content: space-between;
  gap: 18px;
  padding-bottom: 14px;
  border-bottom: 1px solid var(--cf-border);
}

.info-row:last-child {
  padding-bottom: 0;
  border-bottom: 0;
}

.info-row span {
  color: var(--cf-text-secondary);
}

.info-row strong {
  color: var(--cf-text-primary);
  text-align: right;
  overflow-wrap: anywhere;
}
.achievement-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.achievement-item {
  display: grid;
  grid-template-columns: 38px minmax(0, 1fr) auto;
  align-items: center;
  gap: 12px;
}

.achievement-item.locked {
  opacity: 0.62;
}

.achievement-icon {
  width: 38px;
  height: 38px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--cf-primary);
  background: var(--cf-primary-soft);
}

.achievement-copy {
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.achievement-copy strong {
  overflow-wrap: anywhere;
}

.achievement-copy span {
  color: var(--cf-text-secondary);
  font-size: 12px;
  line-height: 1.4;
  overflow-wrap: anywhere;
}

.mini-metric {
  color: var(--cf-text-secondary);
}

.mini-metric .n-icon {
  flex: 0 0 auto;
  width: 42px;
  height: 42px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--cf-primary);
  background: var(--cf-primary-soft);
}

.mini-metric div {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.mini-metric strong {
  color: var(--cf-text-primary);
}

.empty-block {
  padding: 36px 0;
}

.compact-empty {
  padding: 22px 0 10px;
}

@media (max-width: 980px) {
  .profile-hero,
  .profile-grid {
    grid-template-columns: 1fr;
  }

  .signal-panel {
    padding-left: 0;
    padding-top: 24px;
    border-left: 0;
    border-top: 1px solid var(--cf-border);
  }
}

@media (max-width: 640px) {
  .user-page {
    padding: 22px 14px 36px;
  }

  .profile-hero,
  .activity-panel,
  .about-panel,
  .achievement-panel,
  .calendar-panel {
    padding: 20px;
  }

  .hero-main {
    flex-direction: column;
    align-items: flex-start;
  }

  .avatar-frame {
    width: 88px;
    height: 88px;
    flex-basis: 88px;
  }

  .avatar-frame span {
    font-size: 34px;
  }

  .identity-block h1 {
    font-size: 34px;
  }

  .stats-grid {
    grid-template-columns: 1fr 1fr;
  }

  .section-heading,
  .post-item-top {
    flex-direction: column;
    align-items: flex-start;
  }

  .achievement-item {
    grid-template-columns: 38px minmax(0, 1fr);
  }

  .achievement-item .n-tag {
    grid-column: 2;
    justify-self: start;
  }
}
</style>
