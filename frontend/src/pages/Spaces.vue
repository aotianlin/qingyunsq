<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue';
import { useRouter } from 'vue-router';
import { NIcon, NInput, NModal, NSelect, NSpin, useMessage } from 'naive-ui';
import {
  AddOutline,
  BookmarkOutline,
  BonfireOutline,
  ChatbubblesOutline,
  ChevronForwardOutline,
  CodeSlashOutline,
  CubeOutline,
  EllipsisHorizontalOutline,
  FlameOutline,
  LeafOutline,
  MedalOutline,
  PeopleOutline,
  SchoolOutline,
  StarOutline,
  TimeOutline,
  TrophyOutline,
} from '@vicons/ionicons5';
import { createSpace, getSpaces, joinSpace } from '@/api/spaces';
import type { SpaceVO } from '@/types/space';

type DiscoverTab = 'recommend' | 'tech' | 'exam' | 'design' | 'language' | 'career';

const router = useRouter();
const message = useMessage();
const spaces = ref<SpaceVO[]>([]);
const loading = ref(false);
const activeTab = ref<DiscoverTab>('recommend');
const activeLeftNav = ref('discover');
const activitySort = ref<'reply' | 'publish'>('reply');
const stripOffset = ref(0);
const topicOffset = ref(0);
const likedActivities = ref(new Set<string>());
const starredActivities = ref(new Set<string>());
const followedUsers = ref(new Set<string>());
const createVisible = ref(false);
const createSubmitting = ref(false);
const joiningId = ref<number | null>(null);
const createName = ref('');
const createDescription = ref('');
const createCategory = ref('');
const createVisibility = ref('PUBLIC');

const discoverTabs: Array<{ key: DiscoverTab; label: string; category?: string }> = [
  { key: 'recommend', label: '推荐' },
  { key: 'tech', label: '编程技术', category: 'MAJOR' },
  { key: 'exam', label: '考研考证', category: 'CLASS' },
  { key: 'design', label: '设计创意', category: 'CLUB' },
  { key: 'language', label: '语言学习', category: 'INTEREST' },
  { key: 'career', label: '职业发展', category: 'CAREER' },
];

const fallbackSpaces: SpaceVO[] = [
  {
    id: -1,
    ownerId: 0,
    owner: null,
    name: '前端开发学习圈',
    description: 'React、Vue、工程化和前端面试经验交流。',
    category: 'MAJOR',
    visibility: 'PUBLIC',
    memberCount: 12800,
    postCount: 986,
    status: 1,
    isMember: false,
    memberRole: null,
    createdAt: '',
  },
  {
    id: -2,
    ownerId: 0,
    owner: null,
    name: '考研互助小组',
    description: '一起制定复习计划，分享资料和每日打卡。',
    category: 'CLASS',
    visibility: 'PUBLIC',
    memberCount: 8600,
    postCount: 732,
    status: 1,
    isMember: true,
    memberRole: 'MEMBER',
    createdAt: '',
  },
  {
    id: -3,
    ownerId: 0,
    owner: null,
    name: '设计交流圈',
    description: '作品点评、灵感收集和设计工具交流。',
    category: 'CLUB',
    visibility: 'PUBLIC',
    memberCount: 6300,
    postCount: 509,
    status: 1,
    isMember: false,
    memberRole: null,
    createdAt: '',
  },
  {
    id: -4,
    ownerId: 0,
    owner: null,
    name: '数据结构与算法',
    description: '刷题路线、算法笔记和面试题复盘。',
    category: 'INTEREST',
    visibility: 'PUBLIC',
    memberCount: 5700,
    postCount: 421,
    status: 1,
    isMember: true,
    memberRole: 'MEMBER',
    createdAt: '',
  },
  {
    id: -5,
    ownerId: 0,
    owner: null,
    name: '英语学习打卡营',
    description: '听说读写一起练，每天进步一点点。',
    category: 'LANGUAGE',
    visibility: 'PUBLIC',
    memberCount: 7200,
    postCount: 638,
    status: 1,
    isMember: false,
    memberRole: null,
    createdAt: '',
  },
];

const activityPosts = [
  {
    author: '程序员小明',
    level: 5,
    circle: '前端开发学习圈',
    time: '2 小时前',
    title: '分享一个超好用的 React 状态管理库：Zustand',
    tag: '经验分享',
    excerpt: 'Zustand 是一个轻量、快速的状态管理库，API 简洁，TypeScript 友好，使用体验非常不错。',
    stats: [128, 36, 89],
    image: 'ZUSTAND',
  },
  {
    author: '考研上岸鹅',
    level: 4,
    circle: '考研互助小组',
    time: '3 小时前',
    title: '25 考研数学复习规划（基础阶段）',
    tag: '学习规划',
    excerpt: '基础阶段是整个考研复习的关键，一定要打好基础，整理了一份详细的复习计划表，供大家参考。',
    stats: [96, 24, 56],
    image: 'PLAN',
  },
];

const hotTopics = [
  ['期末复习计划', '12.3k 讨论'],
  ['每日一题', '8.7k 讨论'],
  ['学习方法分享', '6.1k 讨论'],
  ['代码优化技巧', '5.3k 讨论'],
  ['考研经验贴', '4.8k 讨论'],
];

const recommendedUsers = [
  ['程序员小明', '前端开发学习圈'],
  ['设计师奶茶', '设计交流圈'],
  ['算法小能手', '数据结构与算法'],
];

const categoryOptions = discoverTabs
  .filter((item) => item.category)
  .map((item) => ({ label: item.label, value: item.category as string }));

const visibilityOptions = [
  { value: 'PUBLIC', label: '公开（任何人可加入）' },
  { value: 'REVIEW', label: '审核（需管理员审核）' },
];

const visibleSpaces = computed(() => (spaces.value.length ? spaces.value : fallbackSpaces));
const featuredSpaces = computed(() => {
  const list = visibleSpaces.value;
  if (list.length <= 5) return list;
  return [...list.slice(stripOffset.value), ...list.slice(0, stripOffset.value)].slice(0, 5);
});
const joinedSpaces = computed(() => visibleSpaces.value.filter((item) => item.isMember).slice(0, 5));
const rankingSpaces = computed(() =>
  [...visibleSpaces.value].sort((a, b) => b.memberCount - a.memberCount).slice(0, 5),
);
const shownHotTopics = computed(() => [...hotTopics.slice(topicOffset.value), ...hotTopics.slice(0, topicOffset.value)].slice(0, 5));

async function loadSpaces() {
  loading.value = true;
  try {
    const category = discoverTabs.find((item) => item.key === activeTab.value)?.category;
    spaces.value = await getSpaces({ category, limit: 20 });
  } catch {
    spaces.value = [];
  } finally {
    loading.value = false;
  }
}

function switchTab(tab: DiscoverTab) {
  if (activeTab.value === tab) return;
  activeTab.value = tab;
  void loadSpaces();
}

function selectLeftNav(key: string) {
  activeLeftNav.value = key;
  if (key === 'discover') {
    document.querySelector('.discover-section')?.scrollIntoView({ behavior: 'smooth', block: 'start' });
    return;
  }
  if (key === 'activity') {
    document.querySelector('.activity-section')?.scrollIntoView({ behavior: 'smooth', block: 'start' });
    return;
  }
  message.info('已切换学习圈视图');
}

function showAllCategories() {
  activeTab.value = 'recommend';
  stripOffset.value = 0;
  void loadSpaces();
}

function nextStrip() {
  if (visibleSpaces.value.length <= 5) {
    message.info('已展示全部推荐学习圈');
    return;
  }
  stripOffset.value = (stripOffset.value + 1) % visibleSpaces.value.length;
}

function switchActivitySort(sort: 'reply' | 'publish') {
  activitySort.value = sort;
  message.info(sort === 'reply' ? '已按最新回复排序' : '已按最新发布排序');
}

function openActivityPost(title: string) {
  router.push({ path: '/square', query: { q: title } });
}

function toggleActivityLike(title: string) {
  const next = new Set(likedActivities.value);
  next.has(title) ? next.delete(title) : next.add(title);
  likedActivities.value = next;
}

function toggleActivityStar(title: string) {
  const next = new Set(starredActivities.value);
  next.has(title) ? next.delete(title) : next.add(title);
  starredActivities.value = next;
}

function openTopic(topic: string) {
  router.push({ path: '/square', query: { topic } });
}

function rotateTopics() {
  topicOffset.value = (topicOffset.value + 1) % hotTopics.length;
}

function showAllRanking() {
  activeLeftNav.value = 'discover';
  document.querySelector('.ranking-card')?.scrollIntoView({ behavior: 'smooth', block: 'start' });
  message.info('已展示学习圈排行榜');
}

function showAllUsers() {
  document.querySelector('.users-card')?.scrollIntoView({ behavior: 'smooth', block: 'start' });
  message.info('已展开推荐用户区域');
}

function toggleFollow(name: string) {
  const next = new Set(followedUsers.value);
  next.has(name) ? next.delete(name) : next.add(name);
  followedUsers.value = next;
}

function goDetail(space: SpaceVO) {
  if (space.id < 0) return;
  router.push(`/spaces/${space.id}`);
}

function goCreate() {
  createVisible.value = true;
}

async function handleJoin(space: SpaceVO) {
  if (space.id < 0) {
    message.success('已加入学习圈');
    return;
  }
  if (space.isMember || joiningId.value) return;
  joiningId.value = space.id;
  try {
    await joinSpace(space.id);
    message.success('加入成功');
    await loadSpaces();
  } catch (error) {
    message.error(error instanceof Error ? error.message : '加入失败');
  } finally {
    joiningId.value = null;
  }
}

function resetCreateForm() {
  createName.value = '';
  createDescription.value = '';
  createCategory.value = '';
  createVisibility.value = 'PUBLIC';
}

function closeCreateModal() {
  if (!createSubmitting.value) createVisible.value = false;
}

async function submitCreateSpace() {
  const name = createName.value.trim();
  if (!name || !createCategory.value) {
    message.warning('请填写学习圈名称和分类');
    return;
  }

  createSubmitting.value = true;
  try {
    const space = await createSpace({
      name,
      description: createDescription.value.trim() || undefined,
      category: createCategory.value,
      visibility: createVisibility.value,
    });
    message.success('学习圈创建成功');
    createVisible.value = false;
    resetCreateForm();
    await loadSpaces();
    router.push(`/spaces/${space.id}`);
  } catch (error) {
    message.error(error instanceof Error ? error.message : '创建失败');
  } finally {
    createSubmitting.value = false;
  }
}

watch(createVisible, (visible) => {
  if (!visible && !createSubmitting.value) resetCreateForm();
});

function iconFor(space: SpaceVO) {
  if (space.category === 'CLASS') return SchoolOutline;
  if (space.category === 'CLUB') return MedalOutline;
  if (space.category === 'INTEREST') return CubeOutline;
  if (space.category === 'LANGUAGE') return ChatbubblesOutline;
  return CodeSlashOutline;
}

function colorClass(space: SpaceVO, index = 0) {
  const palette = ['indigo', 'green', 'coral', 'blue', 'purple'];
  if (space.category === 'CLASS') return 'green';
  if (space.category === 'CLUB') return 'coral';
  if (space.category === 'INTEREST') return 'blue';
  if (space.category === 'LANGUAGE') return 'purple';
  return palette[index % palette.length];
}

function formatCount(value: number) {
  if (value >= 10000) return `${(value / 1000).toFixed(1)}k`;
  if (value >= 1000) return `${(value / 1000).toFixed(1)}k`;
  return String(value);
}

onMounted(loadSpaces);
</script>

<template>
  <div class="spaces-page">
    <aside class="spaces-left">
      <section class="apple-card left-nav-card">
        <button class="left-primary" :class="{ active: activeLeftNav === 'discover' }" @click="selectLeftNav('discover')">
          <span class="round-icon"><n-icon size="17"><LeafOutline /></n-icon></span>
          发现学习圈
        </button>
        <div class="left-divider" />
        <button class="left-link" :class="{ active: activeLeftNav === 'spaces' }" @click="selectLeftNav('spaces')">
          <n-icon size="20"><PeopleOutline /></n-icon>
          学习圈
        </button>
        <button class="left-link" :class="{ active: activeLeftNav === 'activity' }" @click="selectLeftNav('activity')">
          <n-icon size="20"><CubeOutline /></n-icon>
          动态
        </button>
        <button class="left-link" :class="{ active: activeLeftNav === 'favorite' }" @click="selectLeftNav('favorite')">
          <n-icon size="20"><StarOutline /></n-icon>
          收藏
        </button>
        <button class="left-link" :class="{ active: activeLeftNav === 'history' }" @click="selectLeftNav('history')">
          <n-icon size="20"><TimeOutline /></n-icon>
          浏览历史
        </button>
      </section>

      <section class="apple-card joined-card">
        <h3>我加入的学习圈</h3>
        <button
          v-for="(space, index) in joinedSpaces"
          :key="space.id"
          class="joined-item"
          @click="goDetail(space)"
        >
          <span class="space-mini-icon" :class="colorClass(space, index)">
            <n-icon size="18"><component :is="iconFor(space)" /></n-icon>
          </span>
          <span>{{ space.name }}</span>
          <i v-if="index === 0 || index === 2" />
        </button>
      </section>

      <section class="create-card">
        <div>
          <h3>创建学习圈</h3>
          <p>与伙伴一起学习成长</p>
        </div>
        <button @click="goCreate">立即创建</button>
        <div class="stack-illustration">
          <span />
          <span />
          <span><n-icon size="24"><StarOutline /></n-icon></span>
        </div>
      </section>
    </aside>

    <main class="spaces-main">
      <section class="discover-section">
        <header class="section-head">
          <div>
            <h1>发现学习圈</h1>
            <p>找到志同道合的伙伴，一起学习成长</p>
          </div>
        </header>

        <div class="discover-tabs">
          <button
            v-for="tab in discoverTabs"
            :key="tab.key"
            :class="{ active: activeTab === tab.key }"
            @click="switchTab(tab.key)"
          >
            {{ tab.label }}
          </button>
          <button class="tab-select" @click="showAllCategories">全部</button>
        </div>

        <div v-if="loading" class="loading-state">
          <n-spin size="large" />
        </div>

        <div v-else class="space-strip">
          <article
            v-for="(space, index) in featuredSpaces"
            :key="space.id"
            class="discover-card"
            @click="goDetail(space)"
          >
            <div class="discover-cover" :class="colorClass(space, index)">
              <n-icon size="58"><component :is="iconFor(space)" /></n-icon>
            </div>
            <div class="discover-body">
              <h2>{{ space.name }}</h2>
              <p>{{ formatCount(space.memberCount) }} 成员 · {{ space.postCount }} 帖子</p>
              <button @click.stop="handleJoin(space)">
                {{ space.isMember ? '已加入' : joiningId === space.id ? '加入中' : '加入' }}
              </button>
            </div>
          </article>
          <button class="strip-next" @click="nextStrip">
            <n-icon size="22"><ChevronForwardOutline /></n-icon>
          </button>
        </div>
      </section>

      <section class="activity-section">
        <div class="activity-head">
          <h2>热门动态</h2>
          <div>
            <button :class="{ active: activitySort === 'reply' }" @click="switchActivitySort('reply')">最新回复</button>
            <button :class="{ active: activitySort === 'publish' }" @click="switchActivitySort('publish')">最新发布</button>
            <button @click="message.info('已打开动态筛选')"><n-icon size="18"><EllipsisHorizontalOutline /></n-icon></button>
          </div>
        </div>

        <article v-for="post in activityPosts" :key="post.title" class="activity-card" @click="openActivityPost(post.title)">
          <button class="post-more" @click.stop="message.info('已打开动态菜单')"><n-icon size="18"><EllipsisHorizontalOutline /></n-icon></button>
          <header>
            <span class="avatar">{{ post.author.slice(0, 1) }}</span>
            <div>
              <strong>{{ post.author }} <small>LV{{ post.level }}</small></strong>
              <p>{{ post.circle }} · {{ post.time }}</p>
            </div>
          </header>

          <div class="activity-content">
            <div>
              <h3>{{ post.title }}</h3>
              <span class="tag">{{ post.tag }}</span>
              <p>{{ post.excerpt }}</p>
            </div>
            <div class="post-thumb" :class="{ plan: post.image === 'PLAN' }">
              {{ post.image }}
            </div>
          </div>

          <footer>
            <button :class="{ active: likedActivities.has(post.title) }" @click.stop="toggleActivityLike(post.title)"><n-icon size="20"><BonfireOutline /></n-icon>{{ post.stats[0] + (likedActivities.has(post.title) ? 1 : 0) }}</button>
            <button @click.stop="openActivityPost(post.title)"><n-icon size="20"><ChatbubblesOutline /></n-icon>{{ post.stats[1] }}</button>
            <button :class="{ active: starredActivities.has(post.title) }" @click.stop="toggleActivityStar(post.title)"><n-icon size="20"><StarOutline /></n-icon>{{ post.stats[2] + (starredActivities.has(post.title) ? 1 : 0) }}</button>
            <button @click.stop="openActivityPost(post.title)"><n-icon size="20"><ChevronForwardOutline /></n-icon></button>
          </footer>
        </article>
      </section>
    </main>

    <aside class="spaces-right">
      <section class="apple-card ranking-card">
        <div class="side-title">
          <h3>学习圈排行榜</h3>
          <button @click="showAllRanking">查看全部 <n-icon size="12"><ChevronForwardOutline /></n-icon></button>
        </div>
        <div
          v-for="(space, index) in rankingSpaces"
          :key="space.id"
          class="rank-row"
          @click="goDetail(space)"
        >
          <span class="rank-index" :class="{ podium: index < 3 }">{{ index + 1 }}</span>
          <span class="space-mini-icon" :class="colorClass(space, index)">
            <n-icon size="19"><component :is="iconFor(space)" /></n-icon>
          </span>
          <div>
            <strong>{{ space.name }}</strong>
            <p>{{ formatCount(space.memberCount) }} 成员</p>
          </div>
        </div>
      </section>

      <section class="apple-card topics-card">
        <div class="side-title">
          <h3>热门话题</h3>
          <button @click="rotateTopics">换一换 <n-icon size="12"><ChevronForwardOutline /></n-icon></button>
        </div>
        <a v-for="[topic, heat] in shownHotTopics" :key="topic" @click="openTopic(topic)">
          <span><n-icon size="15"><FlameOutline /></n-icon># {{ topic }}</span>
          <strong>{{ heat }}</strong>
        </a>
      </section>

      <section class="apple-card users-card">
        <div class="side-title">
          <h3>推荐用户</h3>
          <button @click="showAllUsers">查看全部 <n-icon size="12"><ChevronForwardOutline /></n-icon></button>
        </div>
        <div v-for="[name, circle] in recommendedUsers" :key="name" class="user-row">
          <span class="user-avatar">{{ name.slice(0, 1) }}</span>
          <div>
            <strong>{{ name }}</strong>
            <p>{{ circle }}</p>
          </div>
          <button :class="{ active: followedUsers.has(name) }" @click="toggleFollow(name)">
            {{ followedUsers.has(name) ? '已关注' : '关注' }}
          </button>
        </div>
      </section>
    </aside>

    <NModal
      v-model:show="createVisible"
      preset="card"
      title="创建学习圈"
      class="space-modal create-space-modal"
      transform-origin="center"
      :closable="!createSubmitting"
      :mask-closable="!createSubmitting"
      :style="{ width: 'min(92vw, 560px)' }"
      @after-leave="resetCreateForm"
    >
      <div class="create-space-form">
        <label class="create-field">
          <span>学习圈名称</span>
          <NInput v-model:value="createName" placeholder="例如：Java 学习小组" maxlength="64" />
        </label>

        <label class="create-field">
          <span>简介</span>
          <NInput
            v-model:value="createDescription"
            type="textarea"
            placeholder="简单介绍一下学习圈..."
            maxlength="255"
            :autosize="{ minRows: 3, maxRows: 5 }"
          />
        </label>

        <label class="create-field">
          <span>分类</span>
          <NSelect v-model:value="createCategory" :options="categoryOptions" placeholder="选择分类" />
        </label>

        <label class="create-field">
          <span>加入方式</span>
          <NSelect v-model:value="createVisibility" :options="visibilityOptions" />
        </label>

        <div class="create-actions">
          <button class="modal-btn secondary" type="button" :disabled="createSubmitting" @click="closeCreateModal">
            取消
          </button>
          <button class="modal-btn primary" type="button" :disabled="createSubmitting" @click="submitCreateSpace">
            {{ createSubmitting ? '创建中...' : '创建' }}
          </button>
        </div>
      </div>
    </NModal>
  </div>
</template>

<style scoped lang="scss">
.spaces-page {
  min-height: calc(100vh - 112px);
  display: grid;
  grid-template-columns: 260px minmax(0, 1fr) 340px;
  gap: 28px;
  padding: 8px 0 40px;
  color: var(--cf-text-primary);
  background: var(--cf-page-bg);
}

.spaces-left,
.spaces-right {
  position: sticky;
  top: 8px;
  align-self: start;
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.apple-card,
.discover-card,
.activity-card {
  background: var(--cf-card-bg);
  border: 1px solid var(--cf-card-border);
  border-radius: 20px;
  box-shadow: var(--cf-card-shadow);
  backdrop-filter: blur(24px) saturate(150%);
  -webkit-backdrop-filter: blur(24px) saturate(150%);
}

.left-nav-card,
.joined-card,
.ranking-card,
.topics-card,
.users-card {
  padding: 18px;
}

.left-primary,
.left-link,
.joined-item {
  width: 100%;
  border: 0;
  background: transparent;
  color: var(--cf-text-secondary);
  display: flex;
  align-items: center;
  gap: 12px;
  font-weight: 760;
  cursor: pointer;
}

.left-primary {
  height: 44px;
  padding: 0 8px;
  color: var(--cf-text-primary);
  justify-content: flex-start;
}

.round-icon {
  width: 28px;
  height: 28px;
  border-radius: 50%;
  color: white;
  background: var(--cf-primary);
  display: grid;
  place-items: center;
}

.left-divider {
  height: 1px;
  margin: 18px 0;
  background: var(--cf-border);
}

.left-link {
  min-height: 44px;
  border-radius: 12px;
  padding: 0 8px;
  transition: background 0.2s ease, color 0.2s ease;
}

.left-link:hover,
.left-link.active {
  color: var(--cf-primary);
  background: rgba(0, 216, 191, 0.08);
}

.joined-card h3,
.side-title h3 {
  margin: 0;
  font-size: 16px;
}

.joined-card h3 {
  margin-bottom: 14px;
}

.joined-item {
  min-height: 48px;
  position: relative;
  font-size: 14px;
}

.joined-item i {
  margin-left: auto;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--cf-primary);
}

.space-mini-icon {
  width: 32px;
  height: 32px;
  border-radius: 9px;
  color: white;
  display: grid;
  place-items: center;
  flex: 0 0 auto;
}

.indigo { background: linear-gradient(145deg, #15133c, #3d2da7); }
.green { background: linear-gradient(145deg, #23745f, #53b997); }
.coral { background: linear-gradient(145deg, #ff675f, #ffb169); }
.blue { background: linear-gradient(145deg, #2385ed, #52b9ff); }
.purple { background: linear-gradient(145deg, #7b61ff, #c39bff); }

.create-card {
  min-height: 178px;
  position: relative;
  overflow: hidden;
  padding: 22px;
  border-radius: 20px;
  background: linear-gradient(145deg, color-mix(in srgb, var(--cf-primary) 12%, var(--cf-bg-card)), var(--cf-bg-glass-soft));
  border: 1px solid var(--cf-card-border);
  box-shadow: var(--cf-card-shadow);
}

.create-card h3,
.create-card p {
  margin: 0;
}

.create-card p {
  margin-top: 8px;
  color: var(--cf-text-muted);
  font-size: 13px;
}

.create-card button {
  height: 36px;
  margin-top: 32px;
  padding: 0 18px;
  border: 1px solid var(--cf-border-strong);
  border-radius: 10px;
  background: var(--cf-bg-readable);
  color: var(--cf-primary);
  font-weight: 800;
  cursor: pointer;
  transition: all 0.2s ease;
}

.create-card button:hover {
  background: var(--cf-primary-soft);
}

.stack-illustration {
  position: absolute;
  right: 26px;
  bottom: 22px;
  width: 74px;
  height: 74px;
}

.stack-illustration span {
  position: absolute;
  width: 58px;
  height: 32px;
  border-radius: 10px;
  background: #8fc7ff;
  transform: skewY(-12deg);
  box-shadow: 0 8px 18px rgba(67, 146, 255, 0.18);
}

.stack-illustration span:nth-child(1) { left: 8px; bottom: 0; opacity: 0.45; }
.stack-illustration span:nth-child(2) { left: 4px; bottom: 14px; opacity: 0.7; }
.stack-illustration span:nth-child(3) {
  left: 0;
  bottom: 28px;
  color: #ffd23f;
  display: grid;
  place-items: center;
  opacity: 1;
}

.spaces-main {
  min-width: 0;
  border: 1px solid var(--cf-column-border);
  border-radius: 22px;
  background: var(--cf-column-bg);
  box-shadow: var(--cf-column-shadow);
  overflow: hidden;
}

.discover-section,
.activity-section {
  padding: 26px 30px;
}

.discover-section {
  border-bottom: 1px solid rgba(15, 23, 42, 0.06);
}

.section-head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  margin-bottom: 24px;
}

.section-head h1 {
  margin: 0;
  font-size: 24px;
  letter-spacing: 0;
}

.section-head p {
  margin: 8px 0 0;
  color: var(--cf-text-muted);
}

.discover-tabs {
  display: flex;
  align-items: center;
  gap: clamp(20px, 4vw, 58px);
  margin-bottom: 22px;
  overflow-x: auto;
  scrollbar-width: none;
}

.discover-tabs::-webkit-scrollbar {
  display: none;
}

.discover-tabs button {
  height: 42px;
  border: 0;
  border-radius: 12px;
  background: transparent;
  color: var(--cf-text-secondary);
  padding: 0 16px;
  font-weight: 800;
  white-space: nowrap;
  cursor: pointer;
}

.discover-tabs button.active {
  color: var(--cf-primary);
  background: rgba(0, 216, 191, 0.1);
  border: 1px solid rgba(0, 216, 191, 0.18);
}

.discover-tabs .tab-select {
  margin-left: auto;
  border: 1px solid var(--cf-border);
  background: var(--cf-bg-glass);
}

.space-strip {
  position: relative;
  display: grid;
  grid-template-columns: repeat(5, minmax(164px, 1fr));
  gap: 18px;
}

.discover-card {
  overflow: hidden;
  cursor: pointer;
  transition: transform 0.22s ease, box-shadow 0.22s ease;
}

.discover-card:hover {
  transform: translateY(-3px);
  box-shadow: 0 24px 70px rgba(15, 23, 42, 0.1);
}

.discover-cover {
  height: 112px;
  display: grid;
  place-items: center;
  color: rgba(255, 255, 255, 0.88);
}

.discover-body {
  padding: 16px;
}

.discover-body h2 {
  margin: 0 0 8px;
  font-size: 16px;
}

.discover-body p {
  margin: 0 0 14px;
  color: var(--cf-text-muted);
  font-size: 13px;
}

.discover-body button {
  width: 100%;
  height: 34px;
  border: 1px solid rgba(0, 216, 191, 0.22);
  border-radius: 10px;
  background: var(--cf-bg-base);
  color: var(--cf-primary);
  font-weight: 800;
  cursor: pointer;
  transition: background 0.2s ease, border-color 0.2s ease;
}

.discover-body button:hover {
  background: var(--cf-primary-soft);
  border-color: var(--cf-primary);
}

.strip-next {
  position: absolute;
  right: -16px;
  top: 74px;
  width: 38px;
  height: 38px;
  border: 1px solid var(--cf-border);
  border-radius: 50%;
  background: var(--cf-bg-base);
  color: var(--cf-text-secondary);
  box-shadow: var(--cf-shadow-soft);
  display: grid;
  place-items: center;
}

.loading-state {
  min-height: 220px;
  display: grid;
  place-items: center;
}

.activity-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 18px;
}

.activity-head h2 {
  margin: 0;
  font-size: 20px;
}

.activity-head div {
  display: flex;
  gap: 22px;
}

.activity-head button {
  position: relative;
  border: 0;
  background: transparent;
  color: var(--cf-text-secondary);
  font-weight: 800;
  cursor: pointer;
}

.activity-head button.active {
  color: var(--cf-primary);
}

.activity-head button.active::after {
  content: '';
  position: absolute;
  left: 0;
  right: 0;
  bottom: -15px;
  height: 3px;
  border-radius: 999px;
  background: var(--cf-primary);
}

.activity-section {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.activity-card {
  position: relative;
  padding: 22px;
}

.post-more {
  position: absolute;
  top: 20px;
  right: 20px;
  border: 0;
  background: transparent;
  color: var(--cf-text-secondary);
}

.activity-card header {
  display: flex;
  align-items: center;
  gap: 12px;
}

.avatar,
.user-avatar {
  border-radius: 50%;
  display: grid;
  place-items: center;
  color: white;
  background: linear-gradient(145deg, #1f2937, #64748b);
  font-weight: 900;
}

.avatar {
  width: 38px;
  height: 38px;
}

.activity-card header strong {
  display: block;
  font-size: 14px;
}

.activity-card header small {
  padding: 2px 6px;
  border-radius: 999px;
  background: #19b981;
  color: white;
  font-size: 10px;
}

.activity-card header p {
  margin: 4px 0 0;
  color: var(--cf-text-muted);
  font-size: 12px;
}

.activity-content {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 178px;
  gap: 24px;
  align-items: end;
  margin-top: 20px;
}

.activity-content h3 {
  margin: 0 0 12px;
  font-size: 20px;
}

.tag {
  display: inline-flex;
  margin-bottom: 12px;
  padding: 5px 9px;
  border-radius: 8px;
  color: #4c8fff;
  background: rgba(76, 143, 255, 0.12);
  font-size: 12px;
  font-weight: 800;
}

.activity-content p {
  margin: 0;
  color: var(--cf-text-muted);
  font-size: 14px;
  line-height: 1.7;
}

.post-thumb {
  height: 106px;
  border-radius: 12px;
  display: grid;
  place-items: center;
  color: white;
  background: linear-gradient(145deg, #111827, #29313f);
  font-size: 20px;
  font-weight: 900;
  letter-spacing: 0.04em;
}

.post-thumb.plan {
  color: #3b82f6;
  background:
    linear-gradient(90deg, rgba(59, 130, 246, 0.18) 1px, transparent 1px),
    linear-gradient(rgba(59, 130, 246, 0.18) 1px, transparent 1px),
    var(--cf-bg-elevated);
  background-size: 34px 26px;
}

.activity-card footer {
  display: flex;
  gap: 30px;
  margin-top: 18px;
}

.activity-card footer button {
  border: 0;
  background: transparent;
  color: var(--cf-text-muted);
  display: inline-flex;
  align-items: center;
  gap: 7px;
  cursor: pointer;
}

.activity-card footer button:hover,
.activity-card footer button.active {
  color: var(--cf-primary);
}

.side-title {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 18px;
}

.side-title button {
  border: 0;
  background: transparent;
  color: var(--cf-text-muted);
  display: inline-flex;
  align-items: center;
  gap: 4px;
  cursor: pointer;
}

.rank-row {
  min-height: 58px;
  display: grid;
  grid-template-columns: 26px 36px minmax(0, 1fr);
  align-items: center;
  gap: 10px;
  cursor: pointer;
}

.rank-index {
  color: var(--cf-text-secondary);
  font-weight: 900;
}

.rank-index.podium {
  width: 24px;
  height: 24px;
  border-radius: 50%;
  display: grid;
  place-items: center;
  color: white;
  background: linear-gradient(145deg, #ffb629, #ff7d5c);
}

.rank-row strong,
.user-row strong {
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 14px;
}

.rank-row p,
.user-row p {
  margin: 4px 0 0;
  color: var(--cf-text-muted);
  font-size: 12px;
}

.topics-card a {
  min-height: 36px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  color: var(--cf-text-secondary);
  cursor: pointer;
}

.topics-card a span {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  font-weight: 760;
  font-size: 14px;
}

.topics-card a :deep(.n-icon) {
  color: #ff4d4f;
}

.topics-card a strong {
  color: var(--cf-text-muted);
  font-size: 13px;
  font-weight: 650;
}

.user-row {
  min-height: 58px;
  display: grid;
  grid-template-columns: 38px minmax(0, 1fr) 70px;
  align-items: center;
  gap: 10px;
}

.user-avatar {
  width: 34px;
  height: 34px;
  font-size: 13px;
}

.user-row button {
  height: 32px;
  border: 1px solid rgba(0, 216, 191, 0.24);
  border-radius: 999px;
  background: var(--cf-bg-base);
  color: var(--cf-primary);
  font-weight: 800;
  cursor: pointer;
  transition: background 0.2s ease, color 0.2s ease;
}

.user-row button.active {
  background: var(--cf-primary);
  color: var(--cf-text-inverse);
}

.create-space-form {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.create-field {
  display: flex;
  flex-direction: column;
  gap: 7px;
}

.create-field > span {
  color: var(--cf-text-secondary);
  font-size: 13px;
  font-weight: 800;
}

.create-actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  margin-top: 8px;
}

.modal-btn {
  height: 38px;
  border-radius: 10px;
  padding: 0 18px;
  font-weight: 800;
  cursor: pointer;
}

.modal-btn.secondary {
  border: 1px solid var(--cf-border);
  background: var(--cf-bg-base);
  color: var(--cf-text-secondary);
}

.modal-btn.primary {
  border: 0;
  background: var(--cf-primary);
  color: var(--cf-text-inverse);
}

:global(.space-modal.create-space-modal.n-card) {
  border-radius: 18px;
  border: 1px solid var(--cf-border);
}

/* Dark theme adjustments taken care of by global variables */

@media (max-width: 1320px) {
  .spaces-page {
    grid-template-columns: 230px minmax(0, 1fr) 300px;
    gap: 20px;
  }

  .space-strip {
    grid-template-columns: repeat(4, minmax(160px, 1fr));
  }

  .discover-card:nth-of-type(5) {
    display: none;
  }
}

@media (max-width: 1080px) {
  .spaces-page {
    grid-template-columns: 220px minmax(0, 1fr);
  }

  .spaces-right {
    display: none;
  }

  .space-strip {
    grid-template-columns: repeat(3, minmax(160px, 1fr));
  }
}

@media (max-width: 760px) {
  .spaces-page {
    display: flex;
    flex-direction: column;
    min-height: auto;
  }

  .spaces-left,
  .spaces-right {
    position: static;
  }

  .joined-card,
  .create-card {
    display: none;
  }

  .left-nav-card {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .left-divider {
    display: none;
  }

  .discover-section,
  .activity-section {
    padding: 20px;
  }

  .space-strip {
    display: flex;
    overflow-x: auto;
    padding-bottom: 4px;
  }

  .discover-card {
    min-width: 190px;
  }

  .activity-content {
    grid-template-columns: 1fr;
  }

  .post-thumb {
    display: none;
  }
}
</style>
