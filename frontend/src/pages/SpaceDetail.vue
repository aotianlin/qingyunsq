<script setup lang="ts">
import { computed, ref, onMounted } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { NModal, NSpin, NIcon, useMessage } from 'naive-ui';
import {
  PlanetOutline,
  BonfireOutline,
  SparklesOutline,
  LibraryOutline,
  CheckmarkCircleOutline,
  NotificationsOutline,
  StarOutline,
  SearchOutline,
  MenuOutline,
  ShareSocialOutline,
  ThumbsUpOutline,
  ChatboxOutline,
  ArrowBackOutline,
  PeopleOutline,
  SettingsOutline,
  PersonOutline,
  DocumentTextOutline
} from '@vicons/ionicons5';
import { getSpaceById, getSpaceMembers, getSpacePosts } from '@/api/spaces';
import { createPost } from '@/api/posts';
import { getResources, uploadResource } from '@/api/resources';
import { getMyProfile } from '@/api/users';
import type { SpaceVO, SpaceMemberVO } from '@/types/space';
import type { PostVO } from '@/types/post';
import type { ResourceVO } from '@/types/resource';
import type { UserVO } from '@/types/user';
import auroraBg from '@/assets/images/aurora_bg.png';

const route = useRoute();
const router = useRouter();
const message = useMessage();

const space = ref<SpaceVO | null>(null);
const members = ref<SpaceMemberVO[]>([]);
const posts = ref<PostVO[]>([]);
const spaceResources = ref<ResourceVO[]>([]);
const myProfile = ref<UserVO | null>(null);
const loading = ref(true);
const searchKeyword = ref('');
const postSort = ref<'latest' | 'hot' | 'essence'>('latest');
const composeVisible = ref(false);
const composeTitle = ref('');
const composeContent = ref('');
const composeType = ref<'NORMAL' | 'QA'>('NORMAL');
const composeTopicInput = ref('');
const composeTopics = ref<string[]>([]);
const composeSubmitting = ref(false);
const paperSheetRef = ref<HTMLElement | null>(null);
const paperRipple = ref<{ x: number; y: number; key: number } | null>(null);
const writingPen = ref(false);
const writingPenPoint = ref({ x: 520, y: 84 });
const inkStroke = ref<{ char: string; x: number; y: number; key: number } | null>(null);
const actionMenuVisible = ref(false);
const notificationVisible = ref(false);
const noticeVisible = ref(false);
const activeMembersVisible = ref(false);
const uploadVisible = ref(false);
const uploadInputRef = ref<HTMLInputElement | null>(null);
const uploadFile = ref<File | null>(null);
const uploadDescription = ref('');
const uploadCollege = ref('');
const uploadMajor = ref('');
const uploadCourse = ref('');
const uploadDropActive = ref(false);
const uploadSubmitting = ref(false);
const profilePanelVisible = ref(false);
const profileTab = ref('动态');
let writingTimer: number | undefined;

const sidebarMenus = [
  { label: '广场', icon: PlanetOutline, path: '/square' },
  { 
    label: '学习圈', 
    icon: BonfireOutline, 
    path: '/spaces',
    active: true,
    children: ['我的圈子', '我加入的', '圈子广场', '圈子管理']
  },
  { label: '打卡', icon: CheckmarkCircleOutline, path: '/checkin' },
  { label: '积分中心', icon: StarOutline, path: '/points' },
  { label: 'AI 助手', icon: SparklesOutline, path: '/ai' },
];

const tabs = ['首页', '帖子', '精华', '文件', '成员', '打卡', '设置'];
const activeTab = ref('帖子');
const profileTabs = ['动态', '帖子', '回复', '收藏', '打卡', '成就'];
const uploadAccept = '.pdf,.doc,.docx,.ppt,.pptx,.xls,.xlsx,.zip,.rar,.7z,.jpg,.jpeg,.png,.gif,.webp';
const maxUploadSize = 50 * 1024 * 1024;
const activeMembers = [
  { id: 1, nickname: '代码骑士', avatarUrl: '', role: '今日高频回复' },
  { id: 2, nickname: '算法小能手', avatarUrl: '', role: '资料贡献者' },
  { id: 3, nickname: '系统设计同学', avatarUrl: '', role: '讨论活跃' },
  { id: 4, nickname: 'Java 学习者', avatarUrl: '', role: '连续打卡' },
  { id: 5, nickname: '操作系统读书会', avatarUrl: '', role: '热门发帖' },
];
const sortedPosts = computed(() => {
  const list = [...posts.value];
  if (postSort.value === 'hot') {
    return list.sort((a, b) => b.likeCount + b.commentCount - (a.likeCount + a.commentCount));
  }
  if (postSort.value === 'essence') {
    const essencePosts = list.filter((item) => item.isEssence === 1);
    return (essencePosts.length ? essencePosts : list).sort((a, b) => b.likeCount - a.likeCount);
  }
  return list.sort((a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime());
});
const activeMemberList = computed(() => {
  if (members.value.length > 0) {
    return members.value.map((member) => ({
      id: member.userId,
      nickname: member.user?.nickname || '未知用户',
      avatarUrl: member.user?.avatarUrl || '',
      role: member.role === 'OWNER' ? '圈主' : member.role === 'ADMIN' ? '管理员' : '成员',
    }));
  }
  return activeMembers;
});
const profileDisplay = computed(() => {
  const user = myProfile.value;
  return {
    nickname: user?.nickname || '校园学习者',
    avatarUrl: user?.avatarUrl || '',
    initial: (user?.nickname || '学').charAt(0).toUpperCase(),
    title: [user?.college, user?.major, user?.grade].filter(Boolean).join(' · ') || '正在完善学习档案',
    bio: user?.bio || '还没有写下个人简介。',
    points: user?.points ?? 0,
    role: user?.role || 'USER',
    email: user?.email || '未绑定邮箱',
  };
});

async function loadSpace() {
  loading.value = true;
  try {
    const id = Number(route.params.id);
    space.value = await getSpaceById(id);
    members.value = await getSpaceMembers(id, undefined, 50);
    posts.value = await getSpacePosts(id, undefined, 10);
    try {
      myProfile.value = await getMyProfile();
    } catch {
      myProfile.value = null;
    }
    try {
      spaceResources.value = await getResources({ spaceId: id, limit: 30 });
    } catch {
      spaceResources.value = [];
    }
  } catch {
    spaceResources.value = [];
    space.value = {
      id: 1,
      ownerId: 1,
      owner: null,
      name: '计算机科学与技术',
      description: 'CS学习交流圈',
      category: 'MAJOR',
      visibility: 'PUBLIC',
      memberCount: 2341,
      postCount: 8712,
      status: 1,
      isMember: false,
      memberRole: null,
      createdAt: new Date().toISOString(),
    };
  }
  loading.value = false;
}

function openCompose() {
  if (!space.value) return;
  composeVisible.value = true;
  actionMenuVisible.value = false;
}

function resetCompose() {
  composeTitle.value = '';
  composeContent.value = '';
  composeType.value = 'NORMAL';
  composeTopicInput.value = '';
  composeTopics.value = [];
}

function closeCompose() {
  composeVisible.value = false;
  paperRipple.value = null;
  inkStroke.value = null;
  writingPen.value = false;
}

function addComposeTopic() {
  const topic = composeTopicInput.value.trim();
  if (!topic || composeTopics.value.includes(topic)) return;
  composeTopics.value.push(topic);
  composeTopicInput.value = '';
}

function removeComposeTopic(topic: string) {
  composeTopics.value = composeTopics.value.filter((item) => item !== topic);
}

function createPaperRipple(event: PointerEvent) {
  const target = event.target as HTMLElement;
  if (target.closest('button')) return;
  const rect = (event.currentTarget as HTMLElement).getBoundingClientRect();
  const key = Date.now();
  paperRipple.value = {
    x: event.clientX - rect.left,
    y: event.clientY - rect.top,
    key,
  };
  window.setTimeout(() => {
    if (paperRipple.value?.key === key) {
      paperRipple.value = null;
    }
  }, 720);
}

function handlePaperInput(event: Event) {
  const target = event.target as HTMLInputElement | HTMLTextAreaElement;
  const sheet = paperSheetRef.value;
  if (!sheet) return;

  const inputEvent = event as InputEvent;
  const value = target.value || '';
  const typedChar = inputEvent.data || Array.from(value).at(-1) || '墨';
  const sheetRect = sheet.getBoundingClientRect();
  const targetRect = target.getBoundingClientRect();
  const style = window.getComputedStyle(target);
  const fontSize = Number.parseFloat(style.fontSize) || 16;
  const lineHeight = Number.parseFloat(style.lineHeight) || fontSize * 1.8;
  const caretIndex = target.selectionStart ?? value.length;
  const beforeCaret = value.slice(0, caretIndex);
  const logicalLines = beforeCaret.split('\n');
  const lastLine = logicalLines.at(-1) || '';
  const usableWidth = Math.max(120, target.clientWidth - 24);
  const charsPerLine = Math.max(1, Math.floor(usableWidth / (fontSize * 0.92)));
  const wrappedLineCount = logicalLines.slice(0, -1).reduce((sum, line) => sum + Math.max(1, Math.ceil(line.length / charsPerLine)), 0);
  const currentWrapLine = Math.floor(lastLine.length / charsPerLine);
  const currentColumn = lastLine.length % charsPerLine;
  const x = targetRect.left - sheetRect.left + 8 + Math.min(usableWidth - 10, currentColumn * fontSize * 0.92);
  const y = targetRect.top - sheetRect.top + lineHeight * (wrappedLineCount + currentWrapLine + 0.68);

  writingPenPoint.value = {
    x: Math.min(sheet.clientWidth - 80, Math.max(78, x + 28)),
    y: Math.min(sheet.clientHeight - 128, Math.max(18, y - 86)),
  };
  inkStroke.value = {
    char: typedChar,
    x: Math.min(sheet.clientWidth - 52, Math.max(52, x)),
    y: Math.min(sheet.clientHeight - 40, Math.max(32, y - fontSize * 1.2)),
    key: Date.now(),
  };
  writingPen.value = true;

  if (writingTimer) {
    window.clearTimeout(writingTimer);
  }
  writingTimer = window.setTimeout(() => {
    writingPen.value = false;
  }, 520);
}

async function submitCompose() {
  if (!space.value || composeSubmitting.value) return;
  if (!composeContent.value.trim()) {
    message.warning('先写一点内容再发布');
    return;
  }
  if (composeType.value === 'QA' && !composeTitle.value.trim()) {
    message.warning('问答帖需要一个清晰标题');
    return;
  }

  composeSubmitting.value = true;
  try {
    const post = await createPost({
      scope: 'SPACE',
      spaceId: space.value.id,
      type: composeType.value,
      title: composeTitle.value.trim() || undefined,
      content: composeContent.value.trim(),
      topics: composeTopics.value.length ? composeTopics.value : undefined,
    });
    posts.value = [post, ...posts.value];
    space.value.postCount += 1;
    message.success('已发布到当前学习圈');
    closeCompose();
    resetCompose();
  } catch {
    message.error('发布失败');
  } finally {
    composeSubmitting.value = false;
  }
}

function goCreatePost() {
  openCompose();
}

function goUploadResource() {
  if (!space.value) return;
  uploadVisible.value = true;
  actionMenuVisible.value = false;
}

function resetUploadForm() {
  uploadFile.value = null;
  uploadDescription.value = '';
  uploadCollege.value = '';
  uploadMajor.value = '';
  uploadCourse.value = '';
  uploadDropActive.value = false;
  if (uploadInputRef.value) {
    uploadInputRef.value.value = '';
  }
}

function closeUploadResource() {
  if (uploadSubmitting.value) return;
  uploadVisible.value = false;
  resetUploadForm();
}

function openUploadFilePicker() {
  uploadInputRef.value?.click();
}

function applyUploadFile(file?: File | null) {
  if (!file) return;
  if (file.size > maxUploadSize) {
    message.warning('文件不能超过 50MB');
    return;
  }
  uploadFile.value = file;
}

function handleUploadFileChange(event: Event) {
  const target = event.target as HTMLInputElement;
  applyUploadFile(target.files?.[0]);
}

function handleUploadDrop(event: DragEvent) {
  uploadDropActive.value = false;
  applyUploadFile(event.dataTransfer?.files?.[0]);
}

async function submitSpaceResource() {
  if (!space.value || uploadSubmitting.value) return;
  if (!uploadFile.value) {
    message.warning('请先选择一个文件');
    return;
  }

  uploadSubmitting.value = true;
  try {
    const resource = await uploadResource(uploadFile.value, {
      visibility: 'SPACE',
      spaceId: space.value.id,
      college: uploadCollege.value.trim() || undefined,
      major: uploadMajor.value.trim() || undefined,
      course: uploadCourse.value.trim() || undefined,
      description: uploadDescription.value.trim() || undefined,
    });
    spaceResources.value = [resource, ...spaceResources.value.filter((item) => item.id !== resource.id)];
    activeTab.value = '文件';
    message.success('已上传到当前学习圈');
    uploadVisible.value = false;
    resetUploadForm();
  } catch {
    message.error('上传失败');
  } finally {
    uploadSubmitting.value = false;
  }
}

function goNotifications() {
  notificationVisible.value = true;
}

function goProfile() {
  profilePanelVisible.value = true;
  actionMenuVisible.value = false;
}

function goUser(userId?: number | null) {
  if (!userId) {
    message.warning('暂时没有该成员的主页信息');
    return;
  }
  router.push(`/users/${userId}`);
}

function goPost(postId?: number) {
  if (!postId) return;
  router.push(`/posts/${postId}`);
}

function goResource(resourceId: number) {
  router.push(`/resources/${resourceId}`);
}

function openActionMenu() {
  actionMenuVisible.value = true;
}

function openNoticeList() {
  actionMenuVisible.value = false;
  noticeVisible.value = true;
}

function openActiveMembers() {
  actionMenuVisible.value = false;
  activeMembersVisible.value = true;
}

function handleSearch() {
  const keyword = searchKeyword.value.trim();
  if (!keyword) return;
  router.push({ path: '/search', query: { q: keyword, type: 'POST', spaceId: String(route.params.id) } });
}

function formatTime(value?: string) {
  if (!value) return '刚刚';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return '刚刚';
  return date.toLocaleString('zh-CN', { month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' });
}

function postTitle(post: PostVO) {
  return post.title || '无标题帖子';
}

function postPreview(content: string) {
  const normalized = content.replace(/\s+/g, ' ').trim();
  return normalized.length > 130 ? `${normalized.slice(0, 130)}...` : normalized;
}

function memberInitial(member: SpaceMemberVO) {
  return member.user?.nickname?.charAt(0)?.toUpperCase() || 'U';
}

function formatFileSize(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / 1024 / 1024).toFixed(1)} MB`;
}

async function sharePost(postId: number) {
  const url = `${window.location.origin}/posts/${postId}`;
  try {
    await navigator.clipboard.writeText(url);
    message.success('帖子链接已复制');
  } catch {
    message.info(url);
  }
}

onMounted(loadSpace);
</script>

<template>
  <div class="layout-container">
    <div class="main-wrapper">
      <!-- Top Header -->
      <header class="top-header">
        <div class="header-left" @click="router.push('/spaces')">
          <n-icon size="20">
            <ArrowBackOutline />
          </n-icon>
          <span>返回学习圈广场</span>
        </div>
        <div class="search-bar">
          <n-icon
            size="18"
            color="#8b949e"
          >
            <SearchOutline />
          </n-icon>
          <input
            v-model="searchKeyword"
            type="text"
            placeholder="搜索圈内内容"
            @keyup.enter="handleSearch"
          />
        </div>
        <div class="header-actions">
          <button
            class="header-action-btn"
            title="通知"
            @click="goNotifications"
          >
            <n-icon size="22">
              <NotificationsOutline />
            </n-icon>
          </button>
          <button
            class="header-action-btn"
            title="页面菜单"
            @click="openActionMenu"
          >
            <n-icon size="22">
              <MenuOutline />
            </n-icon>
          </button>
          <button
            class="avatar profile-entry"
            title="个人主页"
            @click="goProfile"
          >
            <n-icon size="17">
              <PersonOutline />
            </n-icon>
          </button>
        </div>
      </header>

      <Transition name="profile-panel">
        <section
          v-if="profilePanelVisible"
          class="embedded-profile"
        >
          <header class="embedded-profile__top">
            <button
              class="embedded-profile__back"
              @click="profilePanelVisible = false"
            >
              <n-icon size="20"><ArrowBackOutline /></n-icon>
              返回学习圈
            </button>
            <div class="embedded-profile__actions">
              <button title="资料">
                <n-icon size="18"><DocumentTextOutline /></n-icon>
              </button>
              <button title="设置">
                <n-icon size="18"><SettingsOutline /></n-icon>
              </button>
            </div>
          </header>

          <div class="embedded-profile__scroll">
            <div class="embedded-profile__container">
              <section class="profile-cover-card">
                <img
                  :src="auroraBg"
                  alt="个人主页封面"
                />
                <div class="profile-cover-card__shade" />
                <div class="profile-identity">
                  <div class="profile-avatar-large">
                    <img
                      v-if="profileDisplay.avatarUrl"
                      :src="profileDisplay.avatarUrl"
                      :alt="profileDisplay.nickname"
                    />
                    <span v-else>{{ profileDisplay.initial }}</span>
                  </div>
                  <div class="profile-copy">
                    <div class="profile-name-row">
                      <h2>{{ profileDisplay.nickname }}</h2>
                      <span>{{ profileDisplay.role }}</span>
                    </div>
                    <p>{{ profileDisplay.title }}</p>
                    <small>{{ profileDisplay.bio }}</small>
                  </div>
                </div>
              </section>

              <div class="profile-metrics">
                <article>
                  <span>关注</span>
                  <strong>128</strong>
                </article>
                <article>
                  <span>粉丝</span>
                  <strong>1,234</strong>
                </article>
                <article>
                  <span>获赞</span>
                  <strong>8,912</strong>
                </article>
                <article>
                  <span>积分</span>
                  <strong>{{ profileDisplay.points }}</strong>
                </article>
              </div>

              <div class="profile-content-grid">
                <main class="profile-feed">
                  <nav class="profile-tabs">
                    <button
                      v-for="tab in profileTabs"
                      :key="tab"
                      :class="{ active: profileTab === tab }"
                      @click="profileTab = tab"
                    >
                      {{ tab }}
                    </button>
                  </nav>

                  <article class="profile-feed-card glass-card">
                    <div class="profile-feed-card__head">
                      <div class="profile-avatar-small">
                        {{ profileDisplay.initial }}
                      </div>
                      <div>
                        <strong>{{ profileDisplay.nickname }}</strong>
                        <span>刚刚查看了 {{ space?.name || '当前学习圈' }}</span>
                      </div>
                      <n-icon class="profile-feed-card__more">
                        <MenuOutline />
                      </n-icon>
                    </div>
                    <p>
                      {{ profileTab }}内容会在这里汇总展示。当前先保留在学习圈内查看，不打断正在浏览的圈子页面。
                    </p>
                    <div class="profile-attachment">
                      <n-icon size="22"><DocumentTextOutline /></n-icon>
                      <div>
                        <strong>{{ space?.name || '学习圈' }}资料动态</strong>
                        <span>{{ spaceResources.length }} 份圈内文件</span>
                      </div>
                    </div>
                    <div class="profile-feed-card__actions">
                      <span><n-icon><ThumbsUpOutline /></n-icon> 24</span>
                      <span><n-icon><ChatboxOutline /></n-icon> 36</span>
                      <span><n-icon><ShareSocialOutline /></n-icon> 分享</span>
                    </div>
                  </article>
                </main>

                <aside class="profile-side">
                  <section class="profile-widget glass-card">
                    <div class="profile-widget__header">
                      <h3>个人成就</h3>
                      <span>查看全部</span>
                    </div>
                    <div class="profile-badges">
                      <i>学</i>
                      <i>研</i>
                      <i>答</i>
                      <i>勤</i>
                    </div>
                  </section>

                  <section class="profile-widget glass-card">
                    <div class="profile-widget__header">
                      <h3>近期打卡</h3>
                      <span>连续 21 天</span>
                    </div>
                    <div class="profile-calendar">
                      <span
                        v-for="i in 21"
                        :key="i"
                        :class="{ active: i % 5 !== 0 }"
                      />
                    </div>
                  </section>
                </aside>
              </div>
            </div>
          </div>
        </section>
      </Transition>

      <NModal
        v-model:show="actionMenuVisible"
        preset="card"
        title="圈子快捷操作"
        class="space-modal compact-modal"
        transform-origin="center"
        :style="{ width: '320px' }"
      >
        <div class="quick-actions">
          <button @click="openCompose">
            <n-icon><SparklesOutline /></n-icon>
            发布圈内帖子
          </button>
          <button @click="goUploadResource">
            <n-icon><DocumentTextOutline /></n-icon>
            上传圈内文件
          </button>
          <button @click="openActiveMembers">
            <n-icon><PeopleOutline /></n-icon>
            查看活跃成员
          </button>
          <button @click="activeTab = '设置'; actionMenuVisible = false">
            <n-icon><SettingsOutline /></n-icon>
            圈子设置
          </button>
        </div>
      </NModal>

      <NModal
        v-model:show="notificationVisible"
        preset="card"
        title="通知"
        class="space-modal tiny-modal"
        transform-origin="center"
        :style="{ width: '300px' }"
      >
        <div class="notice-modal-list">
          <article class="notice-modal-item">
            <strong>暂无新的圈内通知</strong>
            <p>评论、回复与成员动态会汇总在这里。</p>
            <button
              class="mini-link-btn"
              @click="router.push('/notifications')"
            >
              打开通知中心
            </button>
          </article>
        </div>
      </NModal>

      <NModal
        v-model:show="noticeVisible"
        preset="card"
        title="圈子公告"
        class="space-modal compact-modal"
        transform-origin="center"
        :style="{ width: '320px' }"
      >
        <div class="notice-modal-list">
          <article class="notice-modal-item">
            <strong>欢迎加入本学习圈</strong>
            <p>请遵守发帖规范，友善交流，共同进步！</p>
            <span>2024-05-01</span>
          </article>
          <article class="notice-modal-item">
            <strong>暂无更多公告</strong>
            <p>新的圈子公告会在这里集中展示。</p>
            <span>持续更新</span>
          </article>
        </div>
      </NModal>

      <NModal
        v-model:show="activeMembersVisible"
        preset="card"
        title="活跃成员"
        class="space-modal compact-modal"
        transform-origin="center"
        :style="{ width: '330px' }"
      >
        <div class="active-member-list">
          <button
            v-for="member in activeMemberList"
            :key="member.id"
            class="active-member-item"
            @click="goUser(member.id)"
          >
            <img
              v-if="member.avatarUrl"
              :src="member.avatarUrl"
              :alt="member.nickname"
            />
            <span v-else class="member-avatar-fallback">{{ member.nickname.charAt(0).toUpperCase() }}</span>
            <span class="member-copy">
              <strong>{{ member.nickname }}</strong>
              <small>{{ member.role }}</small>
            </span>
          </button>
        </div>
      </NModal>

      <NModal
        v-model:show="uploadVisible"
        :show-icon="false"
        :mask-closable="!uploadSubmitting"
        :auto-focus="false"
        preset="card"
        class="upload-modal"
        transform-origin="center"
        :style="{ width: 'min(92vw, 540px)' }"
      >
        <section class="upload-panel">
          <div class="upload-panel__glow" />
          <header class="upload-panel__header">
            <div>
              <span class="upload-eyebrow">{{ space?.name || '当前学习圈' }}</span>
              <h2>圈内资料夹</h2>
            </div>
            <button
              type="button"
              class="upload-close"
              :disabled="uploadSubmitting"
              @click="closeUploadResource"
            >
              ×
            </button>
          </header>

          <input
            ref="uploadInputRef"
            class="upload-native-input"
            type="file"
            :accept="uploadAccept"
            @change="handleUploadFileChange"
          />
          <button
            type="button"
            class="upload-dropzone"
            :class="{ active: uploadDropActive, selected: uploadFile }"
            @click="openUploadFilePicker"
            @dragenter.prevent="uploadDropActive = true"
            @dragover.prevent="uploadDropActive = true"
            @dragleave.prevent="uploadDropActive = false"
            @drop.prevent="handleUploadDrop"
          >
            <span class="upload-folder">
              <n-icon size="28"><DocumentTextOutline /></n-icon>
            </span>
            <span class="upload-drop-copy">
              <strong>{{ uploadFile ? uploadFile.name : '选择或拖入一份资料' }}</strong>
              <small>{{ uploadFile ? formatFileSize(uploadFile.size) : 'PDF、Office、压缩包与常见图片，最大 50MB' }}</small>
            </span>
          </button>

          <div class="upload-form-grid">
            <label class="upload-field">
              <span>学院</span>
              <input
                v-model="uploadCollege"
                maxlength="64"
                placeholder="计算机学院"
              />
            </label>
            <label class="upload-field">
              <span>专业</span>
              <input
                v-model="uploadMajor"
                maxlength="64"
                placeholder="软件工程"
              />
            </label>
            <label class="upload-field wide">
              <span>课程</span>
              <input
                v-model="uploadCourse"
                maxlength="128"
                placeholder="Java 程序设计"
              />
            </label>
            <label class="upload-field wide">
              <span>备注</span>
              <textarea
                v-model="uploadDescription"
                maxlength="500"
                placeholder="这份资料适合谁、覆盖哪些重点..."
              />
            </label>
          </div>

          <footer class="upload-actions">
            <button
              type="button"
              class="upload-secondary"
              :disabled="uploadSubmitting"
              @click="closeUploadResource"
            >
              收起
            </button>
            <button
              type="button"
              class="upload-primary"
              :disabled="uploadSubmitting"
              @click="submitSpaceResource"
            >
              {{ uploadSubmitting ? '上传中...' : '上传到圈子' }}
            </button>
          </footer>
        </section>
      </NModal>

      <NModal
        v-model:show="composeVisible"
        :show-icon="false"
        :mask-closable="false"
        :auto-focus="false"
        :block-scroll="true"
        preset="card"
        class="compose-modal"
        transform-origin="center"
        :style="{ width: 'min(92vw, 640px)' }"
      >
        <div class="paper-stage">
          <div class="pen-illustration resting-pen" aria-hidden="true">
            <span class="pen-cap" />
            <span class="pen-body" />
            <span class="pen-tip" />
          </div>
          <section
            ref="paperSheetRef"
            class="paper-sheet"
            :class="{ 'is-rippling': paperRipple }"
            @pointerdown="createPaperRipple"
          >
            <div
              class="pen-illustration writing-pen"
              :class="{ 'is-writing': writingPen }"
              :style="{ left: `${writingPenPoint.x}px`, top: `${writingPenPoint.y}px` }"
              aria-hidden="true"
            >
              <span class="pen-cap" />
              <span class="pen-body" />
              <span class="pen-tip" />
            </div>
            <span
              v-if="paperRipple"
              :key="paperRipple.key"
              class="paper-ripple"
              :style="{ left: `${paperRipple.x}px`, top: `${paperRipple.y}px` }"
            />
            <span
              v-if="inkStroke"
              :key="inkStroke.key"
              class="ink-stroke"
              :style="{ left: `${inkStroke.x}px`, top: `${inkStroke.y}px` }"
            >
              {{ inkStroke.char }}
            </span>
            <div class="paper-header">
              <div>
                <span class="handwritten-label">A fresh page for {{ space?.name || 'this circle' }}</span>
                <h2>写给这个学习圈</h2>
              </div>
              <button
                type="button"
                class="paper-close"
                @pointerdown.stop.prevent="closeCompose"
                @click.stop.prevent="closeCompose"
              >
                ×
              </button>
            </div>

            <div class="paper-mode">
              <button
                :class="{ active: composeType === 'NORMAL' }"
                @click="composeType = 'NORMAL'"
              >
                随笔
              </button>
              <button
                :class="{ active: composeType === 'QA' }"
                @click="composeType = 'QA'"
              >
                问答
              </button>
            </div>

            <input
              v-model="composeTitle"
              class="paper-title-input"
              placeholder="给这页纸写个标题"
              @input="handlePaperInput"
            />
            <textarea
              v-model="composeContent"
              class="paper-content-input"
              placeholder="把问题、想法或学习记录写在这里..."
              @input="handlePaperInput"
            />

            <div class="paper-topics">
              <input
                v-model="composeTopicInput"
                placeholder="添加话题后回车"
                @input="handlePaperInput"
                @keyup.enter="addComposeTopic"
              />
              <button @click="addComposeTopic">
                写上
              </button>
            </div>
            <div
              v-if="composeTopics.length"
              class="paper-topic-list"
            >
              <button
                v-for="topic in composeTopics"
                :key="topic"
                @click="removeComposeTopic(topic)"
              >
                #{{ topic }}
              </button>
            </div>

            <div class="paper-actions">
              <button
                class="paper-cancel"
                @click="closeCompose"
              >
                收起纸页
              </button>
              <button
                class="paper-submit"
                :disabled="composeSubmitting"
                @click="submitCompose"
              >
                {{ composeSubmitting ? '墨迹风干中...' : '发布到圈子' }}
              </button>
            </div>
          </section>
        </div>
      </NModal>

      <div class="content-scroll">
        <template v-if="loading">
          <div class="loading">
            <n-spin size="large" />
          </div>
        </template>
        <template v-else-if="space">
          <div class="space-layout">
            <!-- Center Column -->
            <div class="center-col">
              <!-- Space Banner -->
              <div class="space-banner glass-card">
                <div class="banner-bg" />
                <div class="banner-content">
                  <div class="space-icon">
                    <n-icon
                      size="36"
                      color="white"
                    >
                      <LibraryOutline />
                    </n-icon>
                  </div>
                  <div class="space-info">
                    <div class="title-row">
                      <h2>{{ space.name || '计算机科学与技术' }}</h2>
                      <span class="tag">公开圈子</span>
                    </div>
                    <p class="desc">
                      {{ space.description || 'CS学习交流圈' }}
                    </p>
                    <div class="stats">
                      成员 {{ space.memberCount || '2,341' }} <span class="dot">·</span> 
                      帖子 {{ space.postCount || '8,712' }} <span class="dot">·</span> 
                      今日活跃 342
                    </div>
                  </div>
                  <button
                    class="neon-btn header-btn"
                    @click="goCreatePost"
                  >
                    + 发布帖子
                  </button>
                </div>
              </div>

              <!-- Tabs -->
              <div class="space-tabs">
                <div
                  v-for="tab in tabs"
                  :key="tab" 
                  class="tab-item" 
                  :class="{ active: activeTab === tab }"
                  @click="activeTab = tab"
                >
                  {{ tab }}
                </div>
              </div>

              <!-- Filter -->
              <div v-if="activeTab === '帖子'" class="post-filters">
                <span
                  :class="{ active: postSort === 'latest' }"
                  @click="postSort = 'latest'"
                >
                  最新
                </span>
                <span
                  :class="{ active: postSort === 'hot' }"
                  @click="postSort = 'hot'"
                >
                  热门
                </span>
                <span
                  :class="{ active: postSort === 'essence' }"
                  @click="postSort = 'essence'"
                >
                  精华
                </span>
              </div>

              <!-- Post List -->
              <div v-if="activeTab === '帖子'" class="post-list">
                <div
                  v-for="post in sortedPosts"
                  :key="post.id"
                  class="post-item glass-card"
                  @click="goPost(post.id)"
                >
                  <div class="post-author">
                    <button
                      class="avatar author-avatar"
                      @click.stop="goUser(post.authorId)"
                    >
                      {{ post.author?.nickname?.charAt(0)?.toUpperCase() || 'U' }}
                    </button>
                    <div class="author-info">
                      <span
                        class="name"
                        @click.stop="goUser(post.authorId)"
                      >
                        {{ post.author?.nickname || '匿名用户' }}
                      </span>
                      <span class="time">{{ formatTime(post.createdAt) }}</span>
                    </div>
                    <n-icon class="more-icon">
                      <MenuOutline />
                    </n-icon>
                  </div>
                  <h3 class="post-title">
                    {{ postTitle(post) }}
                    <span
                      v-if="post.type === 'QA'"
                      class="tag blue"
                    >
                      求助
                    </span>
                    <span
                      v-if="post.isEssence === 1"
                      class="tag purple"
                    >
                      精华
                    </span>
                  </h3>
                  <p class="post-preview">
                    {{ postPreview(post.content) }}
                  </p>
                  <div class="post-actions">
                    <span class="action"><n-icon><ThumbsUpOutline /></n-icon> {{ post.likeCount }}</span>
                    <span class="action"><n-icon><ChatboxOutline /></n-icon> {{ post.commentCount }}</span>
                    <span
                      class="action right"
                      @click.stop="sharePost(post.id)"
                    >
                      <n-icon><ShareSocialOutline /></n-icon> 分享
                    </span>
                  </div>
                </div>

                <div
                  v-if="sortedPosts.length === 0"
                  class="empty-inline glass-card"
                >
                  <p>圈内暂时没有帖子。</p>
                  <button
                    class="neon-btn"
                    @click="goCreatePost"
                  >
                    发布第一篇帖子
                  </button>
                </div>
              </div>

              <!-- Members Tab -->
              <div v-if="activeTab === '成员'" class="members-view">
                <div class="member-header">
                  <h3>圈子成员</h3>
                  <div class="search-member">
                    <input type="text" placeholder="搜索成员昵称" />
                  </div>
                </div>
                <div class="member-grid">
                  <div
                    v-for="m in members"
                    :key="m.userId"
                    class="member-card glass-card"
                    @click="goUser(m.userId)"
                  >
                    <button
                      class="avatar"
                      @click.stop="goUser(m.userId)"
                    >
                      {{ memberInitial(m) }}
                    </button>
                    <div class="info">
                      <span class="name">{{ m.user?.nickname || '未知用户' }}</span>
                      <span class="role">{{ m.role === 'OWNER' ? '圈主' : (m.role === 'ADMIN' ? '管理员' : '成员') }}</span>
                    </div>
                  </div>
                  <!-- 成员为空时展示占位成员卡片，避免成员区视觉上完全塌陷。 -->
                  <template v-if="members.length === 0">
                    <div
                      v-for="i in 5"
                      :key="i"
                      class="member-card glass-card"
                    >
                      <div
                        class="avatar"
                        style="background: var(--cf-gradient-primary);"
                      >
                        U
                      </div>
                      <div class="info">
                        <span class="name">学习者 {{ i }}</span>
                        <span class="role">成员</span>
                      </div>
                    </div>
                  </template>
                </div>
              </div>

              <!-- Files Tab -->
              <div v-if="activeTab === '文件'" class="files-view glass-card">
                <div
                  v-if="spaceResources.length === 0"
                  class="empty-state"
                >
                  <n-icon size="48" color="rgba(255,255,255,0.2)"><DocumentTextOutline /></n-icon>
                  <p>暂无文件，快来分享第一份资料吧~</p>
                  <button
                    class="neon-btn"
                    @click="goUploadResource"
                  >
                    上传文件
                  </button>
                </div>
                <div
                  v-else
                  class="resource-list"
                >
                  <div class="resource-list-header">
                    <div>
                      <h3>圈内文件</h3>
                      <span>{{ spaceResources.length }} 份资料已收纳</span>
                    </div>
                    <button
                      class="neon-btn"
                      @click="goUploadResource"
                    >
                      上传文件
                    </button>
                  </div>
                  <article
                    v-for="resource in spaceResources"
                    :key="resource.id"
                    class="resource-card"
                    @click="goResource(resource.id)"
                  >
                    <div class="resource-icon">
                      <n-icon size="22"><DocumentTextOutline /></n-icon>
                    </div>
                    <div class="resource-info">
                      <strong>{{ resource.fileName }}</strong>
                      <span>
                        {{ formatFileSize(resource.fileSize) }}
                        <template v-if="resource.fileType"> · {{ resource.fileType.toUpperCase() }}</template>
                        <template v-if="resource.uploader?.nickname"> · {{ resource.uploader.nickname }}</template>
                      </span>
                      <p v-if="resource.description">
                        {{ resource.description }}
                      </p>
                    </div>
                  </article>
                </div>
              </div>

              <!-- Other Tabs Fallback -->
              <div v-if="!['帖子', '成员', '文件'].includes(activeTab)" class="files-view glass-card">
                <div class="empty-state">
                  <p>该功能模块建设中...</p>
                </div>
              </div>
            </div>

            <!-- Right Column -->
            <div class="right-col">
              <!-- Notice Card -->
              <div class="widget-card glass-card">
                <div class="widget-header">
                  <h3>圈子公告</h3>
                  <span
                    class="more"
                    @click="openNoticeList"
                  >
                    更多 >
                  </span>
                </div>
                <div class="notice-content">
                  <p>欢迎加入本学习圈！请遵守发帖规范，友善交流，共同进步！</p>
                  <div class="date">
                    2024-05-01
                  </div>
                </div>
              </div>

              <!-- Data Card -->
              <div class="widget-card glass-card">
                <div class="widget-header">
                  <h3>圈子数据</h3>
                </div>
                <div class="data-content">
                  <div class="data-row">
                    <span class="label">活跃趋势 (7日)</span>
                    <span class="value">342 <span class="up">+12.5%</span></span>
                  </div>
                  <!-- CSS Simulated Chart -->
                  <div class="mock-chart">
                    <svg
                      viewBox="0 0 100 30"
                      class="chart-svg"
                    >
                      <path
                        d="M0,20 L20,10 L40,25 L60,5 L80,15 L100,5"
                        fill="none"
                        stroke="#6366f1"
                        stroke-width="2"
                      />
                      <circle
                        cx="0"
                        cy="20"
                        r="2"
                        fill="#6366f1"
                      />
                      <circle
                        cx="20"
                        cy="10"
                        r="2"
                        fill="#6366f1"
                      />
                      <circle
                        cx="40"
                        cy="25"
                        r="2"
                        fill="#6366f1"
                      />
                      <circle
                        cx="60"
                        cy="5"
                        r="2"
                        fill="#6366f1"
                      />
                      <circle
                        cx="80"
                        cy="15"
                        r="2"
                        fill="#6366f1"
                      />
                      <circle
                        cx="100"
                        cy="5"
                        r="2"
                        fill="#6366f1"
                      />
                    </svg>
                  </div>
                  <div class="data-row mt">
                    <span class="label">活跃成员</span>
                    <button
                      class="members-avatars"
                      @click="openActiveMembers"
                    >
                      <div
                        class="avatar"
                        style="background:#ef4444"
                      />
                      <div
                        class="avatar"
                        style="background:#3b82f6"
                      />
                      <div
                        class="avatar"
                        style="background:#10b981"
                      />
                      <span class="count">> 2,341</span>
                    </button>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </template>
      </div>
    </div>
  </div>
</template>

<style scoped lang="scss">
.layout-container {
  display: flex;
  height: 100vh;
  background-color: transparent;
  color: var(--cf-text-primary);
  overflow: hidden;
}

.main-wrapper {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  max-width: 1400px;
  margin: 0 auto;
  width: 100%;
}

.top-header {
  height: 64px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 32px;
  border-bottom: 1px solid var(--cf-border-glass);
  background:
    linear-gradient(180deg, var(--cf-surface-highlight), transparent 72%),
    linear-gradient(90deg, var(--cf-bg-glass-strong), var(--cf-bg-glass-soft));
  backdrop-filter: blur(var(--cf-backdrop-blur)) saturate(136%);
  -webkit-backdrop-filter: blur(var(--cf-backdrop-blur)) saturate(136%);
  box-shadow: 0 20px 70px color-mix(in srgb, var(--cf-text-primary) 9%, transparent), 0 -1px 0
    color-mix(in srgb, #ffffff 36%, transparent) inset;
  position: sticky;
  top: 0;
  z-index: 10;

  .header-left {
    display: flex;
    align-items: center;
    gap: 8px;
    cursor: pointer;
    font-weight: 500;
    color: var(--cf-text-primary);
    transition: color 0.22s ease, transform 0.24s var(--cf-motion-ease);
    &:hover {
      color: var(--cf-primary);
      transform: translate3d(-2px, -1px, 0);
    }
  }

  .search-bar {
    display: flex;
    align-items: center;
    background: var(--cf-bg-glass);
    border: 1px solid var(--cf-border-glass);
    border-radius: 20px;
    padding: 8px 16px;
    width: 300px;
    gap: 8px;
    box-shadow: 0 14px 34px color-mix(in srgb, var(--cf-text-primary) 7%, transparent);
    transition: border-color 0.22s ease, box-shadow 0.22s ease, background 0.22s ease;

    &:focus-within {
      background: var(--cf-bg-readable);
      border-color: var(--cf-border-strong);
      box-shadow: 0 0 0 4px color-mix(in srgb, var(--cf-primary) 12%, transparent), var(--cf-shadow-soft);
    }

    input {
      background: transparent;
      border: none;
      color: var(--cf-text-primary);
      outline: none;
      width: 100%;
      &::placeholder { color: var(--cf-text-muted); }
    }
  }

  .header-actions {
    display: flex;
    align-items: center;
    gap: 14px;
    color: var(--cf-text-secondary);

    .header-action-btn,
    .avatar {
      transition: transform 0.24s var(--cf-motion-ease), color 0.22s ease, box-shadow 0.24s var(--cf-motion-ease),
        border-color 0.22s ease;
    }

    .header-action-btn {
      width: 40px;
      height: 40px;
      padding: 8px;
      border: 1px solid var(--cf-border-glass);
      border-radius: 12px;
      background: var(--cf-bg-glass);
      box-shadow: 0 12px 30px color-mix(in srgb, var(--cf-text-primary) 7%, transparent);
      cursor: pointer;
      display: inline-flex;
      align-items: center;
      justify-content: center;
      color: var(--cf-text-secondary);

      &:hover {
        color: var(--cf-primary);
        border-color: var(--cf-border-strong);
        box-shadow: var(--cf-shadow-soft);
        transform: translate3d(0, -2px, 0);
      }
    }
    
    .avatar {
      width: 32px;
      height: 32px;
      border-radius: 50%;
      background: linear-gradient(135deg, var(--cf-secondary), var(--cf-primary));
      border: 1px solid color-mix(in srgb, #ffffff 54%, transparent);
      box-shadow: 0 12px 28px color-mix(in srgb, var(--cf-secondary) 22%, transparent);
      color: var(--cf-text-inverse);
      cursor: pointer;
      display: inline-flex;
      align-items: center;
      justify-content: center;

      &:hover {
        transform: translate3d(0, -2px, 0);
        box-shadow: var(--cf-shadow-soft);
      }
    }
  }
}

.content-scroll {
  flex: 1;
  overflow-y: auto;
  padding: 32px;
  scroll-behavior: smooth;
}

.embedded-profile {
  position: absolute;
  inset: 0;
  z-index: 28;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  color: var(--cf-text-primary);
  background:
    linear-gradient(180deg, color-mix(in srgb, var(--cf-bg-readable) 92%, transparent), color-mix(in srgb, var(--cf-bg-base) 96%, transparent)),
    var(--cf-bg-base);
  backdrop-filter: blur(calc(var(--cf-backdrop-blur) + 4px)) saturate(142%);
  -webkit-backdrop-filter: blur(calc(var(--cf-backdrop-blur) + 4px)) saturate(142%);
}

.profile-panel-enter-active,
.profile-panel-leave-active {
  transition: opacity 0.28s ease, transform 0.32s var(--cf-motion-ease);
}

.profile-panel-enter-from,
.profile-panel-leave-to {
  opacity: 0;
  transform: translate3d(0, 18px, 0) scale(0.985);
}

.embedded-profile__top {
  height: 64px;
  flex: 0 0 auto;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 0 32px;
  border-bottom: 1px solid var(--cf-border-glass);
  background:
    linear-gradient(180deg, var(--cf-surface-highlight), transparent 70%),
    var(--cf-bg-glass-strong);
  box-shadow: 0 18px 58px color-mix(in srgb, var(--cf-text-primary) 8%, transparent);
}

.embedded-profile__back,
.embedded-profile__actions button {
  border: 1px solid var(--cf-border-glass);
  background: var(--cf-bg-glass);
  color: var(--cf-text-primary);
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  transition: transform 0.22s var(--cf-motion-ease), border-color 0.22s ease, box-shadow 0.22s ease;

  &:hover {
    transform: translate3d(0, -1px, 0);
    border-color: var(--cf-border-strong);
    box-shadow: var(--cf-shadow-soft);
  }
}

.embedded-profile__back {
  gap: 8px;
  padding: 10px 14px;
  border-radius: 999px;
  font-weight: 800;
}

.embedded-profile__actions {
  display: flex;
  gap: 10px;

  button {
    width: 40px;
    height: 40px;
    border-radius: 13px;
  }
}

.embedded-profile__scroll {
  flex: 1;
  overflow-y: auto;
  padding: 32px;
}

.embedded-profile__container {
  width: min(100%, 1100px);
  margin: 0 auto 48px;
}

.profile-cover-card {
  position: relative;
  overflow: hidden;
  min-height: 300px;
  border: 1px solid var(--cf-border-glass);
  border-radius: 22px;
  background: var(--cf-bg-glass);
  box-shadow: var(--cf-shadow-card);

  > img {
    position: absolute;
    inset: 0;
    width: 100%;
    height: 100%;
    object-fit: cover;
    opacity: 0.88;
    filter: saturate(112%);
  }
}

.profile-cover-card__shade {
  position: absolute;
  inset: 0;
  background:
    linear-gradient(180deg, transparent 18%, color-mix(in srgb, var(--cf-bg-base) 38%, transparent) 66%, var(--cf-bg-readable)),
    radial-gradient(circle at 18% 24%, color-mix(in srgb, var(--cf-primary) 22%, transparent), transparent 34%),
    radial-gradient(circle at 82% 28%, color-mix(in srgb, var(--cf-secondary) 18%, transparent), transparent 32%);
}

.profile-identity {
  position: absolute;
  left: 34px;
  right: 34px;
  bottom: 28px;
  display: flex;
  align-items: flex-end;
  gap: 24px;
}

.profile-avatar-large {
  width: 122px;
  height: 122px;
  border-radius: 50%;
  overflow: hidden;
  flex: 0 0 auto;
  border: 4px solid color-mix(in srgb, var(--cf-bg-readable) 92%, transparent);
  background: var(--cf-gradient-primary);
  color: var(--cf-text-inverse);
  box-shadow: 0 20px 48px color-mix(in srgb, var(--cf-text-primary) 22%, transparent);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 48px;
  font-weight: 900;

  img {
    width: 100%;
    height: 100%;
    object-fit: cover;
  }
}

.profile-copy {
  min-width: 0;

  p,
  small {
    margin: 0;
    color: var(--cf-text-secondary);
  }

  small {
    display: block;
    margin-top: 8px;
    font-size: 14px;
    line-height: 1.5;
  }
}

.profile-name-row {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px;
  margin-bottom: 8px;

  h2 {
    margin: 0;
    color: var(--cf-text-primary);
    font-size: 30px;
    line-height: 1.15;
  }

  span {
    border: 1px solid color-mix(in srgb, var(--cf-warning) 48%, transparent);
    border-radius: 999px;
    background: color-mix(in srgb, var(--cf-warning) 13%, transparent);
    color: var(--cf-warning);
    padding: 4px 10px;
    font-size: 12px;
    font-weight: 900;
  }
}

.profile-metrics {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 14px;
  margin: 18px 0 24px;

  article {
    border: 1px solid var(--cf-border-glass);
    border-radius: 16px;
    background:
      linear-gradient(180deg, var(--cf-surface-highlight), transparent 58%),
      var(--cf-bg-glass);
    box-shadow: 0 14px 36px color-mix(in srgb, var(--cf-text-primary) 6%, transparent);
    padding: 16px;
    display: flex;
    flex-direction: column;
    gap: 6px;
  }

  span {
    color: var(--cf-text-secondary);
    font-size: 13px;
  }

  strong {
    color: var(--cf-text-primary);
    font-size: 22px;
  }
}

.profile-content-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 300px;
  gap: 24px;
}

.profile-tabs {
  display: flex;
  gap: 8px;
  padding: 8px;
  margin-bottom: 16px;
  border: 1px solid var(--cf-border-glass);
  border-radius: 16px;
  background: var(--cf-bg-glass-soft);
  box-shadow: 0 14px 34px color-mix(in srgb, var(--cf-text-primary) 5%, transparent);

  button {
    border: none;
    border-radius: 12px;
    background: transparent;
    color: var(--cf-text-secondary);
    cursor: pointer;
    padding: 9px 13px;
    font-weight: 800;
    transition: color 0.22s ease, background 0.22s ease, transform 0.22s var(--cf-motion-ease);

    &:hover {
      color: var(--cf-text-primary);
      transform: translate3d(0, -1px, 0);
    }

    &.active {
      background: var(--cf-primary);
      color: var(--cf-text-inverse);
      box-shadow: var(--cf-shadow-glow);
    }
  }
}

.profile-feed-card,
.profile-widget {
  padding: 20px;
  background:
    linear-gradient(180deg, var(--cf-surface-highlight), transparent 58%),
    var(--cf-bg-glass);
  border: 1px solid var(--cf-border-glass);
  box-shadow: var(--cf-shadow-card);
}

.profile-feed-card__head {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 16px;

  > div:not(.profile-avatar-small) {
    display: flex;
    flex-direction: column;
    gap: 3px;
    min-width: 0;
  }

  strong {
    color: var(--cf-text-primary);
  }

  span {
    color: var(--cf-text-muted);
    font-size: 12px;
  }
}

.profile-avatar-small {
  width: 40px;
  height: 40px;
  border-radius: 50%;
  flex: 0 0 auto;
  background: var(--cf-gradient-primary);
  color: var(--cf-text-inverse);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-weight: 900;
}

.profile-feed-card__more {
  margin-left: auto;
  color: var(--cf-text-secondary);
}

.profile-feed-card p {
  color: var(--cf-text-secondary);
  line-height: 1.65;
  margin: 0 0 16px;
}

.profile-attachment {
  display: flex;
  gap: 12px;
  align-items: center;
  border: 1px solid var(--cf-border-glass);
  border-radius: 14px;
  background: var(--cf-bg-glass-soft);
  padding: 14px;
  color: var(--cf-primary);

  div {
    display: flex;
    flex-direction: column;
    gap: 3px;
  }

  strong {
    color: var(--cf-text-primary);
  }

  span {
    color: var(--cf-text-muted);
    font-size: 12px;
  }
}

.profile-feed-card__actions {
  display: flex;
  gap: 26px;
  margin-top: 16px;
  color: var(--cf-text-secondary);

  span {
    display: inline-flex;
    align-items: center;
    gap: 6px;
    cursor: pointer;
    transition: color 0.22s ease, transform 0.22s var(--cf-motion-ease);

    &:hover {
      color: var(--cf-primary);
      transform: translate3d(0, -1px, 0);
    }

    &:last-child {
      margin-left: auto;
    }
  }
}

.profile-side {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.profile-widget__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  margin-bottom: 16px;

  h3 {
    margin: 0;
    color: var(--cf-text-primary);
    font-size: 16px;
  }

  span {
    color: var(--cf-primary);
    font-size: 12px;
    font-weight: 800;
  }
}

.profile-badges {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 10px;

  i {
    aspect-ratio: 1;
    border: 1px solid color-mix(in srgb, var(--cf-primary) 24%, transparent);
    border-radius: 12px;
    background: color-mix(in srgb, var(--cf-primary) 11%, var(--cf-bg-glass));
    color: var(--cf-primary);
    display: inline-flex;
    align-items: center;
    justify-content: center;
    font-style: normal;
    font-weight: 900;
    box-shadow: inset 0 0 18px color-mix(in srgb, var(--cf-primary) 8%, transparent);
  }
}

.profile-calendar {
  display: grid;
  grid-template-columns: repeat(7, minmax(0, 1fr));
  gap: 7px;

  span {
    aspect-ratio: 1;
    border-radius: 5px;
    background: color-mix(in srgb, var(--cf-text-primary) 7%, transparent);

    &.active {
      background: var(--cf-primary);
      box-shadow: 0 0 14px color-mix(in srgb, var(--cf-primary) 26%, transparent);
    }
  }
}

.space-layout {
  display: flex;
  gap: 32px;
  max-width: 1200px;
  margin: 0 auto;

  .center-col {
    flex: 1;
    min-width: 0;

    .space-banner {
      position: relative;
      padding: 32px;
      overflow: hidden;
      margin-bottom: 24px;
      background:
        linear-gradient(135deg, color-mix(in srgb, var(--cf-secondary) 16%, transparent), transparent 58%),
        linear-gradient(315deg, color-mix(in srgb, var(--cf-primary) 14%, transparent), transparent 52%),
        var(--cf-bg-glass);
      border: 1px solid var(--cf-border-glass);
      box-shadow: var(--cf-shadow-card);
      transition: transform 0.3s var(--cf-motion-ease), box-shadow 0.3s var(--cf-motion-ease), border-color 0.3s ease;

      &:hover {
        transform: translate3d(0, -4px, 0);
        border-color: var(--cf-border-strong);
        box-shadow: var(--cf-shadow-card-hover);
      }

      .banner-bg {
        position: absolute;
        top: 0; left: 0; right: 0; bottom: 0;
        background:
          radial-gradient(circle at 12% 16%, color-mix(in srgb, var(--cf-primary) 22%, transparent), transparent 28%),
          radial-gradient(circle at 78% 62%, color-mix(in srgb, var(--cf-secondary) 18%, transparent), transparent 30%);
        z-index: 0;
        opacity: 0.72;
        pointer-events: none;
      }

      .banner-content {
        position: relative;
        z-index: 1;
        display: flex;
        align-items: center;
        gap: 24px;

        .space-icon {
          width: 80px; height: 80px;
          border-radius: 16px;
          background: var(--cf-primary);
          display: flex;
          align-items: center;
          justify-content: center;
          box-shadow: var(--cf-shadow-glow);
        }

        .space-info {
          flex: 1;
          .title-row {
            display: flex;
            align-items: center;
            gap: 12px;
            h2 { margin: 0; font-size: 24px; color: var(--cf-text-primary); }
            .tag {
              font-size: 12px;
              padding: 3px 9px;
              border-radius: 7px;
              border: 1px solid color-mix(in srgb, var(--cf-warning) 58%, transparent);
              background: color-mix(in srgb, var(--cf-warning) 12%, transparent);
              color: var(--cf-warning);
            }
          }
          .desc { color: var(--cf-text-secondary); margin: 8px 0; font-size: 14px; }
          .stats { color: var(--cf-text-muted); font-size: 13px; .dot { margin: 0 8px; } }
        }

        .header-btn { padding: 10px 24px; }
      }
    }

    .space-tabs {
      display: flex;
      gap: 12px;
      border-bottom: 1px solid var(--cf-border-glass);
      margin-bottom: 24px;
      background: var(--cf-bg-glass-soft);
      border-radius: 16px 16px 0 0;
      padding: 0 10px;
      box-shadow: 0 14px 38px color-mix(in srgb, var(--cf-text-primary) 5%, transparent);

      .tab-item {
        padding: 13px 10px;
        color: var(--cf-text-secondary);
        cursor: pointer;
        position: relative;
        transition: color 0.22s ease, transform 0.24s var(--cf-motion-ease);

        &:hover {
          color: var(--cf-text-primary);
          transform: translate3d(0, -1px, 0);
        }

        &.active {
          color: var(--cf-text-primary);
          &::after {
            content: '';
            position: absolute;
            bottom: -1px; left: 10px; width: calc(100% - 20px); height: 2px;
            background: var(--cf-primary);
            box-shadow: 0 -2px 10px color-mix(in srgb, var(--cf-primary) 52%, transparent);
          }
        }
      }
    }

    .post-filters {
      display: flex;
      gap: 16px;
      margin-bottom: 20px;
      span {
        padding: 7px 14px; border-radius: 16px; background: var(--cf-bg-glass);
        border: 1px solid transparent;
        color: var(--cf-text-secondary); font-size: 14px; cursor: pointer;
        transition: transform 0.22s var(--cf-motion-ease), box-shadow 0.22s ease, border-color 0.22s ease, background 0.22s ease;
        &:hover {
          border-color: var(--cf-border-glass);
          color: var(--cf-text-primary);
          transform: translate3d(0, -1px, 0);
        }
        &.active {
          background: var(--cf-primary);
          color: var(--cf-text-inverse);
          box-shadow: var(--cf-shadow-glow);
        }
      }
    }

    .post-list {
      display: flex;
      flex-direction: column;
      gap: 16px;

      .post-item {
        padding: 24px;
        background:
          linear-gradient(180deg, var(--cf-surface-highlight), transparent 54%),
          var(--cf-bg-glass);
        border: 1px solid var(--cf-border-glass);
        border-radius: 16px;
        box-shadow: var(--cf-shadow-card);
        cursor: pointer;
        transition: transform 0.3s var(--cf-motion-ease), box-shadow 0.3s var(--cf-motion-ease), border-color 0.3s ease;

        &:hover {
          transform: translate3d(0, -5px, 0);
          border-color: var(--cf-border-strong);
          box-shadow: var(--cf-shadow-card-hover);
        }
        
        .post-author {
          display: flex;
          align-items: center;
          gap: 12px;
          margin-bottom: 16px;
          
          .avatar {
            width: 36px;
            height: 36px;
            border-radius: 50%;
            border: none;
            background: var(--cf-gradient-primary);
            color: var(--cf-text-inverse);
            cursor: pointer;
            display: inline-flex;
            align-items: center;
            justify-content: center;
            font-weight: 800;
            box-shadow: 0 10px 22px color-mix(in srgb, var(--cf-text-primary) 10%, transparent);
          }
          .admin-avatar { background: #38bdf8; }
          .default-avatar { background: #10b981; }

          .author-info {
            display: flex;
            flex-direction: column;
            .name { font-size: 14px; color: var(--cf-text-primary); font-weight: 500; cursor: pointer; }
            .time { font-size: 12px; color: var(--cf-text-muted); }
          }
          .more-icon { margin-left: auto; color: var(--cf-text-secondary); cursor: pointer; }
        }

        .post-title {
          margin: 0 0 12px;
          font-size: 18px;
          color: var(--cf-text-primary);
          display: flex;
          align-items: center;
          gap: 12px;

          .tag {
            font-size: 12px; padding: 2px 8px; border-radius: 4px; font-weight: normal;
            &.blue { background: rgba(56,189,248,0.1); color: #38bdf8; border: 1px solid rgba(56,189,248,0.3); }
            &.purple { background: rgba(192,132,252,0.1); color: #c084fc; border: 1px solid rgba(192,132,252,0.3); }
          }
        }

        .post-preview {
          color: var(--cf-text-secondary);
          font-size: 15px;
          line-height: 1.6;
          margin-bottom: 20px;
        }

        .post-actions {
          display: flex;
          gap: 24px;
          .action {
            display: flex; align-items: center; gap: 6px; color: var(--cf-text-secondary); font-size: 14px; cursor: pointer;
            transition: color 0.22s ease, transform 0.22s var(--cf-motion-ease);
            &:hover {
              color: var(--cf-primary);
              transform: translate3d(0, -1px, 0);
            }
            &.right { margin-left: auto; }
          }
        }
      }
    }
  }

  .right-col {
    width: 320px;
    display: flex;
    flex-direction: column;
    gap: 24px;

    .widget-card {
      padding: 24px;
      background:
        linear-gradient(180deg, var(--cf-surface-highlight), transparent 54%),
        var(--cf-bg-glass);
      border: 1px solid var(--cf-border-glass);
      border-radius: 16px;
      box-shadow: var(--cf-shadow-card);
      transition: transform 0.3s var(--cf-motion-ease), box-shadow 0.3s var(--cf-motion-ease), border-color 0.3s ease;

      &:hover {
        transform: translate3d(0, -4px, 0);
        border-color: var(--cf-border-strong);
        box-shadow: var(--cf-shadow-card-hover);
      }

      .widget-header {
        display: flex;
        justify-content: space-between;
        align-items: center;
        margin-bottom: 16px;
        h3 { margin: 0; font-size: 16px; color: var(--cf-text-primary); }
        .more {
          font-size: 13px;
          color: var(--cf-text-secondary);
          cursor: pointer;
          transition: color 0.22s ease;
          &:hover { color: var(--cf-primary); }
        }
      }

      .notice-content {
        p { margin: 0 0 12px; color: var(--cf-text-secondary); font-size: 14px; line-height: 1.6; }
        .date { font-size: 12px; color: var(--cf-text-muted); }
      }

      .data-content {
        .data-row {
          display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px;
          .label { font-size: 14px; color: var(--cf-text-secondary); }
          .value { font-size: 18px; font-weight: bold; color: var(--cf-text-primary); }
          .up { color: #10b981; font-size: 12px; margin-left: 8px; font-weight: normal; }
          
          &.mt { margin-top: 24px; margin-bottom: 0; }
        }

        .mock-chart {
          height: 60px;
          width: 100%;
          .chart-svg { width: 100%; height: 100%; overflow: visible; }
        }

        .members-avatars {
          display: flex; align-items: center;
          border: none;
          background: transparent;
          padding: 0;
          cursor: pointer;
          transition: transform 0.22s var(--cf-motion-ease);

          &:hover {
            transform: translate3d(0, -1px, 0);
          }

          .avatar {
            width: 28px;
            height: 28px;
            border-radius: 50%;
            border: 2px solid color-mix(in srgb, var(--cf-bg-base) 72%, transparent);
            margin-left: -8px;
            box-shadow: 0 8px 18px color-mix(in srgb, var(--cf-text-primary) 10%, transparent);
            &:first-child { margin-left: 0; }
          }
          .count { font-size: 13px; color: var(--cf-text-secondary); margin-left: 8px; }
        }
      }
    }
  }
}

.members-view {
  .member-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 24px;
    h3 { margin: 0; color: var(--cf-text-primary); font-size: 18px; }
    .search-member {
      background: rgba(255, 255, 255, 0.05);
      border-radius: 20px;
      padding: 6px 16px;
      input {
        background: transparent;
        border: none;
        color: var(--cf-text-primary);
        outline: none;
        font-size: 14px;
        &::placeholder { color: var(--cf-text-muted); }
      }
    }
  }

  .member-grid {
    display: grid;
    grid-template-columns: repeat(auto-fill, minmax(240px, 1fr));
    gap: 16px;

    .member-card {
      display: flex;
      align-items: center;
      gap: 12px;
      padding: 16px;
      background: var(--cf-bg-elevated);
      border: 1px solid var(--cf-border);
      border-radius: 12px;
      cursor: pointer;
      transition: transform 0.24s var(--cf-motion-ease), box-shadow 0.24s var(--cf-motion-ease), border-color 0.22s ease;

      &:hover {
        transform: translate3d(0, -3px, 0);
        border-color: var(--cf-border-strong);
        box-shadow: var(--cf-shadow-card-hover);
      }

      .avatar {
        width: 48px;
        height: 48px;
        border-radius: 50%;
        background: #6366f1;
        border: none;
        display: flex;
        align-items: center;
        justify-content: center;
        color: white;
        font-size: 18px;
        font-weight: bold;
        cursor: pointer;
      }

      .info {
        display: flex;
        flex-direction: column;
        .name { color: var(--cf-text-primary); font-size: 15px; font-weight: 500; }
        .role { color: var(--cf-text-secondary); font-size: 12px; margin-top: 4px; }
      }
    }
  }
}

.files-view {
  padding: 42px;
  .empty-state {
    min-height: 220px;
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    p { margin: 16px 0 24px; color: var(--cf-text-secondary); }
    button { padding: 8px 24px; }
  }

  .resource-list {
    display: flex;
    flex-direction: column;
    gap: 14px;
  }

  .resource-list-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 16px;
    margin-bottom: 4px;

    h3 {
      margin: 0 0 4px;
      color: var(--cf-text-primary);
      font-size: 18px;
    }

    span {
      color: var(--cf-text-muted);
      font-size: 13px;
    }
  }

  .resource-card {
    display: flex;
    align-items: flex-start;
    gap: 14px;
    padding: 15px;
    border: 1px solid var(--cf-border-glass);
    border-radius: 14px;
    background:
      linear-gradient(180deg, var(--cf-surface-highlight), transparent 64%),
      var(--cf-bg-glass);
    box-shadow: 0 14px 36px color-mix(in srgb, var(--cf-text-primary) 7%, transparent);
    cursor: pointer;
    transition: transform 0.24s var(--cf-motion-ease), border-color 0.22s ease, box-shadow 0.24s ease;

    &:hover {
      transform: translate3d(0, -3px, 0);
      border-color: var(--cf-border-strong);
      box-shadow: var(--cf-shadow-card-hover);
    }
  }

  .resource-icon {
    width: 42px;
    height: 42px;
    border-radius: 12px;
    display: inline-flex;
    align-items: center;
    justify-content: center;
    flex: 0 0 auto;
    color: var(--cf-primary);
    background: color-mix(in srgb, var(--cf-primary) 13%, var(--cf-bg-glass));
    border: 1px solid color-mix(in srgb, var(--cf-primary) 22%, transparent);
  }

  .resource-info {
    min-width: 0;
    display: flex;
    flex-direction: column;
    gap: 5px;

    strong {
      color: var(--cf-text-primary);
      font-size: 15px;
      overflow-wrap: anywhere;
    }

    span,
    p {
      color: var(--cf-text-secondary);
      font-size: 13px;
      line-height: 1.45;
    }

    p {
      margin: 2px 0 0;
    }
  }
}

.empty-inline {
  padding: 30px;
  text-align: center;

  p {
    margin: 0 0 16px;
    color: var(--cf-text-secondary);
  }
}

.quick-actions,
.notice-modal-list,
.active-member-list {
  display: flex;
  flex-direction: column;
  gap: 9px;
}

.quick-actions button,
.active-member-item {
  width: 100%;
  border: 1px solid var(--cf-border-glass);
  border-radius: 12px;
  background: var(--cf-bg-glass);
  color: var(--cf-text-primary);
  cursor: pointer;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  text-align: left;
  font-size: 13px;
  transition: transform 0.24s var(--cf-motion-ease), border-color 0.22s ease, box-shadow 0.24s ease;

  &:hover {
    transform: translate3d(0, -1px, 0);
    border-color: var(--cf-border-strong);
    box-shadow: var(--cf-shadow-soft);
  }
}

.notice-modal-item {
  border: 1px solid var(--cf-border-glass);
  border-radius: 12px;
  background: var(--cf-bg-glass);
  padding: 12px;

  strong {
    display: block;
    margin-bottom: 6px;
    font-size: 14px;
  }

  p {
    margin: 0 0 8px;
    color: var(--cf-text-secondary);
    font-size: 13px;
    line-height: 1.55;
  }

  span {
    color: var(--cf-text-muted);
    font-size: 12px;
  }
}

.active-member-item img,
.member-avatar-fallback {
  width: 34px;
  height: 34px;
  border-radius: 50%;
  flex: 0 0 auto;
}

.member-avatar-fallback {
  background: var(--cf-gradient-primary);
  color: var(--cf-text-inverse);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-weight: 800;
}

.member-copy {
  display: flex;
  flex-direction: column;
  gap: 2px;

  small {
    color: var(--cf-text-muted);
    font-size: 12px;
  }
}

.mini-link-btn {
  border: 1px solid color-mix(in srgb, var(--cf-primary) 34%, transparent);
  border-radius: 10px;
  background: var(--cf-primary-soft);
  color: var(--cf-primary);
  cursor: pointer;
  padding: 8px 10px;
  font-size: 12px;
  font-weight: 700;
}

:global(.upload-modal.n-card) {
  width: min(92vw, 540px);
  max-width: 540px;
  overflow: hidden;
  background:
    linear-gradient(180deg, var(--cf-surface-highlight), transparent 58%),
    var(--cf-bg-readable);
  border: 1px solid var(--cf-border-glass);
  border-radius: 20px;
  box-shadow:
    0 30px 84px color-mix(in srgb, var(--cf-text-primary) 20%, transparent),
    0 10px 30px color-mix(in srgb, var(--cf-primary) 10%, transparent);
  backdrop-filter: blur(calc(var(--cf-backdrop-blur) + 6px)) saturate(150%);
  -webkit-backdrop-filter: blur(calc(var(--cf-backdrop-blur) + 6px)) saturate(150%);
}

:global(.upload-modal .n-card-header) {
  display: none;
}

:global(.upload-modal .n-card__content) {
  padding: 0;
}

.upload-panel {
  position: relative;
  overflow: hidden;
  padding: 24px;
  color: var(--cf-text-primary);
}

.upload-panel__glow {
  position: absolute;
  inset: -80px -80px auto auto;
  width: 210px;
  height: 210px;
  border-radius: 50%;
  background: radial-gradient(circle, color-mix(in srgb, var(--cf-primary) 18%, transparent), transparent 68%);
  pointer-events: none;
}

.upload-panel__header {
  position: relative;
  z-index: 1;
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 18px;
  margin-bottom: 18px;

  h2 {
    margin: 3px 0 0;
    font-size: 22px;
    line-height: 1.2;
  }
}

.upload-eyebrow {
  color: var(--cf-text-muted);
  font-size: 12px;
  font-weight: 800;
}

.upload-close {
  width: 38px;
  height: 38px;
  border: 1px solid var(--cf-border-glass);
  border-radius: 50%;
  background: var(--cf-bg-glass);
  color: var(--cf-text-secondary);
  cursor: pointer;
  font-size: 22px;
  line-height: 1;
  transition: transform 0.22s var(--cf-motion-ease), border-color 0.22s ease, color 0.22s ease;

  &:hover:not(:disabled) {
    color: var(--cf-primary);
    border-color: var(--cf-border-strong);
    transform: translate3d(0, -1px, 0);
  }

  &:disabled {
    cursor: wait;
    opacity: 0.55;
  }
}

.upload-native-input {
  display: none;
}

.upload-dropzone {
  position: relative;
  z-index: 1;
  width: 100%;
  min-height: 112px;
  border: 1px dashed color-mix(in srgb, var(--cf-primary) 42%, var(--cf-border-glass));
  border-radius: 18px;
  background:
    linear-gradient(135deg, color-mix(in srgb, var(--cf-primary) 9%, transparent), transparent 54%),
    color-mix(in srgb, var(--cf-bg-glass) 78%, transparent);
  color: var(--cf-text-primary);
  cursor: pointer;
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 18px;
  text-align: left;
  box-shadow:
    0 18px 44px color-mix(in srgb, var(--cf-text-primary) 8%, transparent),
    0 1px 0 color-mix(in srgb, #ffffff 46%, transparent) inset;
  transition: transform 0.26s var(--cf-motion-ease), border-color 0.22s ease, box-shadow 0.26s ease, background 0.22s ease;

  &.active,
  &:hover {
    transform: translate3d(0, -3px, 0);
    border-color: color-mix(in srgb, var(--cf-primary) 68%, transparent);
    box-shadow:
      0 24px 62px color-mix(in srgb, var(--cf-primary) 14%, transparent),
      0 10px 24px color-mix(in srgb, var(--cf-text-primary) 7%, transparent);
  }

  &.selected {
    border-style: solid;
    background:
      linear-gradient(135deg, color-mix(in srgb, var(--cf-secondary) 12%, transparent), transparent 52%),
      color-mix(in srgb, var(--cf-bg-glass) 86%, transparent);
  }
}

.upload-folder {
  width: 58px;
  height: 58px;
  border-radius: 16px;
  background: color-mix(in srgb, var(--cf-primary) 16%, var(--cf-bg-readable));
  color: var(--cf-primary);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex: 0 0 auto;
  border: 1px solid color-mix(in srgb, var(--cf-primary) 22%, transparent);
  box-shadow: 0 16px 34px color-mix(in srgb, var(--cf-primary) 12%, transparent);
}

.upload-drop-copy {
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 6px;

  strong {
    font-size: 15px;
    overflow-wrap: anywhere;
  }

  small {
    color: var(--cf-text-secondary);
    font-size: 12px;
    line-height: 1.45;
  }
}

.upload-form-grid {
  position: relative;
  z-index: 1;
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
  margin-top: 16px;
}

.upload-field {
  display: flex;
  flex-direction: column;
  gap: 6px;

  &.wide {
    grid-column: 1 / -1;
  }

  span {
    color: var(--cf-text-secondary);
    font-size: 12px;
    font-weight: 800;
  }

  input,
  textarea {
    width: 100%;
    border: 1px solid var(--cf-border-glass);
    border-radius: 12px;
    outline: none;
    background: var(--cf-bg-glass);
    color: var(--cf-text-primary);
    padding: 10px 12px;
    font: inherit;
    transition: border-color 0.22s ease, box-shadow 0.22s ease, background 0.22s ease;

    &:focus {
      border-color: var(--cf-border-strong);
      background: var(--cf-bg-readable);
      box-shadow: 0 0 0 4px color-mix(in srgb, var(--cf-primary) 12%, transparent);
    }

    &::placeholder {
      color: var(--cf-text-muted);
    }
  }

  textarea {
    min-height: 78px;
    resize: vertical;
  }
}

.upload-actions {
  position: relative;
  z-index: 1;
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  margin-top: 18px;
}

.upload-secondary,
.upload-primary {
  border-radius: 999px;
  cursor: pointer;
  padding: 10px 17px;
  font-weight: 900;
  transition: transform 0.22s var(--cf-motion-ease), box-shadow 0.22s ease, opacity 0.22s ease;

  &:hover:not(:disabled) {
    transform: translate3d(0, -1px, 0);
  }

  &:disabled {
    cursor: wait;
    opacity: 0.64;
  }
}

.upload-secondary {
  border: 1px solid var(--cf-border-glass);
  background: var(--cf-bg-glass);
  color: var(--cf-text-secondary);
}

.upload-primary {
  border: 1px solid color-mix(in srgb, var(--cf-primary) 56%, transparent);
  background: var(--cf-primary);
  color: var(--cf-text-inverse);
  box-shadow: 0 16px 36px color-mix(in srgb, var(--cf-primary) 24%, transparent);
}

:global(.space-modal.n-card) {
  width: 320px;
  max-width: calc(100vw - 36px);
  background:
    linear-gradient(180deg, var(--cf-surface-highlight), transparent 54%),
    var(--cf-bg-readable);
  border: 1px solid var(--cf-border-glass);
  border-radius: 16px;
  box-shadow: 0 24px 72px color-mix(in srgb, var(--cf-text-primary) 18%, transparent), 0 10px 28px
    color-mix(in srgb, var(--cf-text-primary) 8%, transparent);
  backdrop-filter: blur(var(--cf-backdrop-blur)) saturate(136%);
  -webkit-backdrop-filter: blur(var(--cf-backdrop-blur)) saturate(136%);
}

:global(.space-modal.tiny-modal.n-card) {
  width: 300px;
}

:global(.space-modal .n-card-header) {
  padding: 14px 14px 6px;
}

:global(.space-modal .n-card-header__main) {
  font-size: 15px;
  font-weight: 800;
}

:global(.space-modal .n-card__content) {
  padding: 8px 14px 14px;
}

:global(.compose-modal.n-card) {
  width: min(92vw, 640px);
  max-width: 640px;
  background: transparent;
  border: none;
  box-shadow: none;
}

:global(.compose-modal .n-card-header) {
  display: none;
}

:global(.compose-modal .n-card__content) {
  padding: 0;
}

.paper-stage {
  --paper-hand-font: 'AR PL UKai CN', 'KaiTi', 'Kaiti SC', 'STKaiti', 'LXGW WenKai', 'FZKai-Z03', 'Microsoft YaHei',
    cursive;
  --pen-cursor: url("data:image/svg+xml,%3Csvg width='32' height='32' viewBox='0 0 32 32' xmlns='http://www.w3.org/2000/svg'%3E%3Cg transform='rotate(-38 16 16)'%3E%3Crect x='13' y='4' width='6' height='18' rx='2' fill='%2307111f'/%3E%3Cpath d='M13 21h6l-3 7z' fill='%2300d8bf'/%3E%3Ccircle cx='16' cy='7' r='1.2' fill='white'/%3E%3C/g%3E%3C/svg%3E") 4 28,
    auto;
  position: relative;
  padding: 18px 28px 24px;
  cursor: var(--pen-cursor);
}

.paper-stage *,
.paper-stage input,
.paper-stage textarea,
.paper-stage button {
  cursor: var(--pen-cursor) !important;
}

.paper-sheet {
  position: relative;
  overflow: hidden;
  min-height: 460px;
  max-height: none;
  padding: 34px 40px 28px;
  border-radius: 9px 18px 14px 9px;
  color: #182033;
  background:
    linear-gradient(90deg, rgba(255, 126, 126, 0.22) 0 1px, transparent 1px 100%) 42px 0 / 1px 100% no-repeat,
    repeating-linear-gradient(180deg, transparent 0 31px, rgba(55, 83, 120, 0.13) 32px),
    linear-gradient(135deg, rgba(255, 255, 255, 0.92), rgba(255, 253, 244, 0.74)),
    #fffaf0;
  border: 1px solid rgba(255, 255, 255, 0.72);
  box-shadow:
    0 26px 72px rgba(17, 24, 39, 0.22),
    0 8px 22px rgba(17, 24, 39, 0.12),
    12px 12px 0 rgba(255, 255, 255, 0.34) inset,
    -18px -20px 38px rgba(171, 145, 88, 0.08) inset;
  transform: rotate(-0.35deg);
  isolation: isolate;
  transform-origin: center;

  &::before {
    content: '';
    position: absolute;
    inset: 0;
    z-index: -1;
    background:
      radial-gradient(circle at 12% 12%, rgba(0, 216, 191, 0.08), transparent 26%),
      radial-gradient(circle at 86% 18%, rgba(123, 97, 255, 0.08), transparent 24%);
    pointer-events: none;
  }

  &::after {
    content: '';
    position: absolute;
    right: 0;
    bottom: 0;
    width: 96px;
    height: 96px;
    background: linear-gradient(135deg, transparent 48%, rgba(215, 196, 142, 0.36) 49%, rgba(255, 247, 219, 0.88) 72%);
    filter: drop-shadow(-8px -8px 18px rgba(90, 75, 48, 0.12));
    pointer-events: none;
  }
}

.paper-sheet.is-rippling {
  animation: paperPress 0.58s cubic-bezier(0.18, 0.82, 0.28, 1) both;
}

.paper-ripple {
  position: absolute;
  z-index: 0;
  width: 18px;
  height: 18px;
  border-radius: 50%;
  background:
    radial-gradient(circle, rgba(255, 255, 255, 0.5) 0 18%, rgba(255, 255, 255, 0.14) 28%, transparent 66%),
    radial-gradient(circle, rgba(97, 82, 54, 0.08) 0 16%, transparent 60%);
  box-shadow:
    0 0 0 1px rgba(104, 90, 62, 0.05),
    0 6px 14px rgba(72, 60, 40, 0.07) inset,
    0 -6px 14px rgba(255, 255, 255, 0.38) inset;
  mix-blend-mode: multiply;
  transform: translate(-50%, -50%);
  animation: paperRipple 0.56s cubic-bezier(0.16, 0.84, 0.28, 1) both;
  pointer-events: none;
}

.paper-header,
.paper-mode,
.paper-title-input,
.paper-content-input,
.paper-topics,
.paper-topic-list,
.paper-actions {
  position: relative;
  z-index: 1;
}

.paper-header {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  align-items: flex-start;
  margin-bottom: 16px;

  h2 {
    margin: 4px 0 0;
    font-size: 29px;
    line-height: 1;
    font-family: var(--paper-hand-font);
    font-weight: 800;
  }
}

.handwritten-label {
  color: rgba(24, 32, 51, 0.62);
  font-family: var(--paper-hand-font);
  font-size: 13px;
}

.paper-close {
  position: relative;
  width: 42px;
  height: 42px;
  margin: -8px -8px 0 0;
  border: 1px solid rgba(24, 32, 51, 0.16);
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.38);
  color: rgba(24, 32, 51, 0.7);
  font-size: 22px;
  line-height: 1;
  z-index: 4;
  touch-action: manipulation;

  &::before {
    content: '';
    position: absolute;
    inset: -8px;
    border-radius: 50%;
  }
}

.paper-mode {
  display: inline-flex;
  gap: 8px;
  padding: 5px;
  border: 1px solid rgba(24, 32, 51, 0.12);
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.35);
  margin-bottom: 14px;

  button {
    border: none;
    border-radius: 999px;
    background: transparent;
    color: rgba(24, 32, 51, 0.68);
    cursor: pointer;
    padding: 7px 14px;
    font-weight: 800;

    &.active {
      background: rgba(0, 216, 191, 0.18);
      color: #08796f;
    }
  }
}

.paper-title-input,
.paper-content-input,
.paper-topics input {
  width: 100%;
  border: none;
  outline: none;
  background: transparent;
  color: #182033;
  font-family: var(--paper-hand-font);
  letter-spacing: 0.02em;
}

.paper-title-input {
  display: block;
  font-size: 23px;
  font-weight: 800;
  margin-bottom: 10px;
  padding: 7px 0;
}

.paper-content-input {
  min-height: 178px;
  resize: none;
  font-size: 17px;
  line-height: 32px;
}

.paper-title-input::placeholder,
.paper-content-input::placeholder,
.paper-topics input::placeholder {
  color: rgba(24, 32, 51, 0.38);
}

.paper-topics {
  display: flex;
  gap: 10px;
  align-items: center;
  margin-top: 14px;
  padding-top: 10px;
  border-top: 1px dashed rgba(24, 32, 51, 0.18);

  button {
    flex: 0 0 auto;
    border: 1px solid rgba(0, 216, 191, 0.38);
    border-radius: 999px;
    background: rgba(0, 216, 191, 0.13);
    color: #08796f;
    cursor: pointer;
    padding: 8px 14px;
    font-weight: 800;
  }
}

.paper-topic-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 12px;

  button {
    border: 1px solid rgba(24, 32, 51, 0.14);
    border-radius: 999px;
    background: rgba(255, 255, 255, 0.42);
    color: rgba(24, 32, 51, 0.74);
    cursor: pointer;
    padding: 5px 10px;
    font-size: 12px;
  }
}

.paper-actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  margin-top: 16px;
}

.paper-cancel,
.paper-submit {
  border-radius: 999px;
  cursor: pointer;
  padding: 11px 18px;
  font-weight: 900;
}

.paper-cancel {
  border: 1px solid rgba(24, 32, 51, 0.16);
  background: rgba(255, 255, 255, 0.36);
  color: rgba(24, 32, 51, 0.72);
}

.paper-submit {
  border: 1px solid rgba(0, 216, 191, 0.5);
  background: #00d8bf;
  color: #041311;
  box-shadow: 0 14px 34px rgba(0, 216, 191, 0.22);

  &:disabled {
    cursor: wait;
    opacity: 0.7;
  }
}

.pen-illustration {
  position: absolute;
  z-index: 2;
  width: 22px;
  height: 150px;
  transform: rotate(34deg);
  filter: drop-shadow(0 18px 20px rgba(17, 24, 39, 0.22));
  pointer-events: none;
}

.resting-pen {
  right: 12px;
  top: 4px;
}

.writing-pen {
  z-index: 3;
  opacity: 0;
  transform: translate3d(0, 0, 0) rotate(34deg);
  transition: left 0.12s ease-out, top 0.12s ease-out, opacity 0.12s ease-out;
}

.writing-pen.is-writing {
  opacity: 1;
  animation: penWriting 0.26s steps(2, end) infinite;
}

.pen-cap,
.pen-body,
.pen-tip {
  position: absolute;
  left: 50%;
  transform: translateX(-50%);
}

.ink-stroke {
  position: absolute;
  z-index: 2;
  color: rgba(24, 32, 51, 0.78);
  font-family: var(--paper-hand-font);
  font-size: 22px;
  font-weight: 800;
  pointer-events: none;
  transform-origin: left bottom;
  animation: inkWrite 0.5s ease-out both;
  filter: blur(0.1px);
}

.pen-cap {
  top: 0;
  width: 15px;
  height: 23px;
  border-radius: 10px 10px 4px 4px;
  background: #101827;
}

.pen-body {
  top: 20px;
  width: 17px;
  height: 92px;
  border-radius: 10px;
  background: linear-gradient(90deg, #121827, #334155 44%, #101827);
}

.pen-tip {
  top: 110px;
  width: 0;
  height: 0;
  border-left: 9px solid transparent;
  border-right: 9px solid transparent;
  border-top: 28px solid #00d8bf;
}

@keyframes paperRipple {
  0% {
    opacity: 0;
    transform: translate(-50%, -50%) scale(0.25);
    filter: blur(0);
  }
  18% {
    opacity: 0.46;
  }
  100% {
    opacity: 0;
    transform: translate(-50%, -50%) scale(8);
    filter: blur(1.2px);
  }
}

@keyframes paperPress {
  0% {
    transform: rotate(-0.35deg) translate3d(0, 0, 0) scale(1);
    box-shadow:
      0 26px 72px rgba(17, 24, 39, 0.22),
      0 8px 22px rgba(17, 24, 39, 0.12),
      12px 12px 0 rgba(255, 255, 255, 0.34) inset,
      -18px -20px 38px rgba(171, 145, 88, 0.08) inset;
  }
  28% {
    transform: rotate(-0.35deg) translate3d(0, 2px, 0) scale(0.997);
    box-shadow:
      0 18px 54px rgba(17, 24, 39, 0.19),
      0 5px 16px rgba(17, 24, 39, 0.1),
      6px 7px 14px rgba(99, 77, 45, 0.07) inset,
      -10px -12px 26px rgba(255, 255, 255, 0.28) inset;
  }
  100% {
    transform: rotate(-0.35deg) translate3d(0, 0, 0) scale(1);
    box-shadow:
      0 26px 72px rgba(17, 24, 39, 0.22),
      0 8px 22px rgba(17, 24, 39, 0.12),
      12px 12px 0 rgba(255, 255, 255, 0.34) inset,
      -18px -20px 38px rgba(171, 145, 88, 0.08) inset;
  }
}

@keyframes penWriting {
  0% {
    transform: translate3d(0, 0, 0) rotate(31deg);
  }
  50% {
    transform: translate3d(3px, 4px, 0) rotate(37deg);
  }
  100% {
    transform: translate3d(1px, -1px, 0) rotate(33deg);
  }
}

@keyframes inkWrite {
  0% {
    opacity: 0;
    clip-path: inset(0 100% 0 0);
    transform: translate3d(-2px, 2px, 0) scale(0.96) rotate(-1deg);
  }
  45% {
    opacity: 0.92;
    clip-path: inset(0 35% 0 0);
  }
  100% {
    opacity: 0;
    clip-path: inset(0 0 0 0);
    transform: translate3d(0, 0, 0) scale(1) rotate(0deg);
  }
}

@media (max-width: 720px) {
  .embedded-profile__top,
  .embedded-profile__scroll {
    padding-left: 16px;
    padding-right: 16px;
  }

  .profile-cover-card {
    min-height: 360px;
  }

  .profile-identity {
    left: 20px;
    right: 20px;
    bottom: 22px;
    align-items: flex-start;
    flex-direction: column;
    gap: 14px;
  }

  .profile-avatar-large {
    width: 94px;
    height: 94px;
    font-size: 38px;
  }

  .profile-name-row h2 {
    font-size: 25px;
  }

  .profile-metrics {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .profile-content-grid {
    grid-template-columns: 1fr;
  }

  .profile-tabs {
    overflow-x: auto;

    button {
      flex: 0 0 auto;
    }
  }

  .profile-feed-card__actions {
    gap: 14px;
    flex-wrap: wrap;

    span:last-child {
      margin-left: 0;
    }
  }

  .paper-stage {
    padding: 12px 4px;
  }

  .paper-sheet {
    min-height: 470px;
    padding: 28px 22px 24px;
  }

  .paper-header h2 {
    font-size: 27px;
  }

  .pen-illustration {
    display: none;
  }
}
</style>
