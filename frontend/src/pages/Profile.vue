<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import { NEmpty, NIcon, NModal, NSpin, useMessage } from 'naive-ui';
import {
  AddOutline,
  ArrowBackOutline,
  BulbOutline,
  CalendarNumberOutline,
  CalendarOutline,
  ChatboxOutline,
  CheckmarkCircleOutline,
  CopyOutline,
  DocumentTextOutline,
  GitNetworkOutline,
  HeartOutline,
  LockClosedOutline,
  MicOutline,
  PencilOutline,
  PeopleOutline,
  PersonAddOutline,
  RibbonOutline,
  SaveOutline,
  SettingsOutline,
  ShareSocialOutline,
  SparklesOutline,
  ThumbsUpOutline,
  TrophyOutline,
  WalletOutline,
} from '@vicons/ionicons5';
import { changePassword } from '@/api/auth';
import { getUserAchievements } from '@/api/achievement';
import { getChallenges } from '@/api/checkin';
import { getFollowCounts, getUserFollowers, getUserFollowing } from '@/api/follows';
import { getBalance, getPointsLogs } from '@/api/points';
import { getPosts } from '@/api/posts';
import { getMyProfile, updateProfile, uploadProfileAsset } from '@/api/users';
import { getPasswordStrength, validateConfirmPassword, validateNickname, validatePassword } from '@/utils/authValidation';
import { copyTextToClipboard } from '@/utils/clipboard';
import type { AchievementVO } from '@/types/achievement';
import type { CheckinChallengeVO } from '@/types/checkin';
import type { PointsLogVO } from '@/types/points';
import type { PostVO } from '@/types/post';
import type { UserVO } from '@/types/user';

const router = useRouter();
const message = useMessage();

const user = ref<UserVO | null>(null);
const posts = ref<PostVO[]>([]);
const achievements = ref<AchievementVO[]>([]);
const challenges = ref<CheckinChallengeVO[]>([]);
const followCounts = ref({ followers: 0, following: 0 });
const loading = ref(true);
const saving = ref(false);
const assetUploading = ref<'avatar' | 'cover' | null>(null);
const editing = ref(false);
const activeTab = ref('动态');
const followsVisible = ref(false);
const followsLoading = ref(false);
const followsTab = ref<'followers' | 'following'>('following');
const followUsers = ref<UserVO[]>([]);
const likesVisible = ref(false);
const pointsVisible = ref(false);
const pointsLoading = ref(false);
const pointsBalance = ref<number | null>(null);
const pointLogs = ref<PointsLogVO[]>([]);
const avatarInputRef = ref<HTMLInputElement | null>(null);
const coverInputRef = ref<HTMLInputElement | null>(null);

const form = ref({
  nickname: '',
  avatarUrl: '',
  profileCoverUrl: '',
  bio: '',
  college: '',
  major: '',
  grade: '',
});
const profileNicknameState = ref({ active: false, touched: false, error: '', shaking: false });
const passwordForm = ref({
  oldPassword: '',
  newPassword: '',
  confirmPassword: '',
});
const passwordSaving = ref(false);
const passwordState = ref({
  oldPassword: { active: false, touched: false, error: '', shaking: false },
  newPassword: { active: false, touched: false, error: '', shaking: false },
  confirmPassword: { active: false, touched: false, error: '', shaking: false },
});

const tabs = ['动态', '帖子', '打卡', '成就'];
const pointTypeLabels: Record<string, string> = {
  LOGIN: '每日登录',
  POST: '发表帖子',
  LIKED: '收到点赞',
  ACCEPTED: '回答被采纳',
  CHECKIN: '每日打卡',
  BOUNTY: '悬赏支出',
};
const achievementIconMap = {
  初来乍到: TrophyOutline,
  笔耕不辍: PencilOutline,
  畅所欲言: MicOutline,
  社交达人: GitNetworkOutline,
  广受好评: ThumbsUpOutline,
  周打卡王: CalendarNumberOutline,
  一鸣惊人: SparklesOutline,
} as const;
const achievementFallbackIcons = [TrophyOutline, PencilOutline, MicOutline, GitNetworkOutline, BulbOutline, CalendarNumberOutline, SparklesOutline, RibbonOutline];
const profileInitials = computed(() => {
  const source = (user.value?.nickname || user.value?.email || '').trim();
  if (!source) return 'A123';
  const normalized = source.replace(/\s+/g, '');
  const ascii = normalized.match(/[A-Za-z0-9]+/g)?.join('') || '';
  if (ascii) return ascii.slice(0, 4).toUpperCase();
  return Array.from(normalized).slice(0, 2).join('');
});
const avatarText = computed(() => profileInitials.value);
const profilePasswordStrength = computed(() => getPasswordStrength(passwordForm.value.newPassword));
const profileTitle = computed(() => {
  if (!user.value) return '校园资料待同步';
  return [user.value.college, user.value.major, user.value.grade].filter(Boolean).join(' · ') || '正在完善学习档案';
});
const likeCount = computed(() => posts.value.reduce((sum, post) => sum + post.likeCount, 0));
type AchievementGridItem = {
  key: string;
  achievement: AchievementVO | null;
  icon: unknown;
  locked: boolean;
};
const profileStats = computed(() => [
  {
    key: 'following',
    label: '关注',
    value: formatCompactNumber(followCounts.value.following),
    icon: PersonAddOutline,
    action: () => goFollows('following'),
  },
  {
    key: 'followers',
    label: '粉丝',
    value: formatCompactNumber(followCounts.value.followers),
    icon: PeopleOutline,
    action: () => goFollows('followers'),
  },
  {
    key: 'likes',
    label: '获赞',
    value: formatCompactNumber(likeCount.value),
    icon: ThumbsUpOutline,
    action: openLikes,
  },
  {
    key: 'points',
    label: '积分',
    value: formatCompactNumber(user.value?.points),
    icon: WalletOutline,
    action: openPoints,
  },
]);
const likedPosts = computed(() =>
  [...posts.value]
    .filter((post) => post.likeCount > 0)
    .sort((a, b) => b.likeCount - a.likeCount || new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime()),
);
const awardedAchievements = computed(() => achievements.value.filter((item) => item.awarded));
const achievementGridItems = computed<AchievementGridItem[]>(() => {
  const items: AchievementGridItem[] = achievements.value.slice(0, 8).map((achievement, index) => ({
    key: `achievement-${achievement.id}`,
    achievement,
    icon: achievementIcon(achievement, index),
    locked: !achievement.awarded,
  }));
  while (items.length < 8) {
    items.push({
      key: `empty-${items.length}`,
      achievement: null,
      icon: AddOutline,
      locked: true,
    });
  }
  return items;
});
const profileChallenges = computed(() =>
  challenges.value
    .filter((item) => item.isMember || item.creatorId === user.value?.id)
    .sort((a, b) => (b.myConsecutiveDays || 0) - (a.myConsecutiveDays || 0) || new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime()),
);
const checkinStreak = computed(() => Math.max(0, ...profileChallenges.value.map((item) => item.myConsecutiveDays || 0)));
const feedPosts = computed(() => (activeTab.value === '动态' ? posts.value.slice(0, 6) : posts.value));
const profileLink = computed(() => {
  if (!user.value?.id) return '';
  return `${window.location.origin}/users/${user.value.id}`;
});

function achievementIcon(achievement: AchievementVO, index = 0) {
  return achievementIconMap[achievement.name as keyof typeof achievementIconMap] || achievementFallbackIcons[index % achievementFallbackIcons.length] || RibbonOutline;
}

async function loadProfile() {
  loading.value = true;
  try {
    const profile = await getMyProfile();
    user.value = profile;
    syncForm(profile);
    const [counts, postList, achievementList, challengeList] = await Promise.all([
      getFollowCounts(profile.id).catch(() => ({ followers: 0, following: 0 })),
      getPosts({ scope: 'SQUARE', authorId: profile.id, sort: 'latest', limit: 20 }).catch(() => []),
      getUserAchievements(profile.id).catch(() => []),
      getChallenges({ limit: 50 }).catch(() => []),
    ]);
    followCounts.value = counts;
    posts.value = postList;
    achievements.value = achievementList;
    challenges.value = challengeList;
  } catch (error) {
    user.value = null;
    message.error(error instanceof Error ? error.message : '个人主页加载失败');
  } finally {
    loading.value = false;
  }
}

function syncForm(profile = user.value) {
  if (!profile) return;
  form.value = {
    nickname: profile.nickname || '',
    avatarUrl: profile.avatarUrl || '',
    profileCoverUrl: profile.profileCoverUrl || '',
    bio: profile.bio || '',
    college: profile.college || '',
    major: profile.major || '',
    grade: profile.grade || '',
  };
  profileNicknameState.value = { active: false, touched: false, error: '', shaking: false };
}

type ProfilePasswordField = keyof typeof passwordState.value;

function shakeState(state: { shaking: boolean }) {
  state.shaking = false;
  window.setTimeout(() => {
    state.shaking = true;
    window.setTimeout(() => {
      state.shaking = false;
    }, 520);
  }, 0);
}

function validateProfileNickname() {
  profileNicknameState.value.error = validateNickname(form.value.nickname);
}

function focusProfileNickname() {
  profileNicknameState.value.active = true;
  validateProfileNickname();
}

function blurProfileNickname() {
  const state = profileNicknameState.value;
  state.active = false;
  state.touched = true;
  validateProfileNickname();
  if (state.error) {
    shakeState(state);
  }
}

function validatePasswordField(field: ProfilePasswordField) {
  const validators: Record<ProfilePasswordField, () => string> = {
    oldPassword: () => (passwordForm.value.oldPassword ? '' : '请输入当前密码'),
    newPassword: () => validatePassword(passwordForm.value.newPassword),
    confirmPassword: () => validateConfirmPassword(passwordForm.value.confirmPassword, passwordForm.value.newPassword),
  };
  passwordState.value[field].error = validators[field]();
  if (field === 'newPassword' && passwordForm.value.confirmPassword) {
    passwordState.value.confirmPassword.error = validateConfirmPassword(passwordForm.value.confirmPassword, passwordForm.value.newPassword);
  }
}

function focusPasswordField(field: ProfilePasswordField) {
  passwordState.value[field].active = true;
  validatePasswordField(field);
}

function blurPasswordField(field: ProfilePasswordField) {
  const state = passwordState.value[field];
  state.active = false;
  state.touched = true;
  validatePasswordField(field);
  if (state.error) {
    shakeState(state);
  }
}

function validatePasswordForm() {
  (Object.keys(passwordState.value) as ProfilePasswordField[]).forEach((field) => {
    passwordState.value[field].touched = true;
    validatePasswordField(field);
  });
  return (Object.keys(passwordState.value) as ProfilePasswordField[]).every((field) => !passwordState.value[field].error);
}

function resetPasswordForm() {
  passwordForm.value = {
    oldPassword: '',
    newPassword: '',
    confirmPassword: '',
  };
  passwordState.value = {
    oldPassword: { active: false, touched: false, error: '', shaking: false },
    newPassword: { active: false, touched: false, error: '', shaking: false },
    confirmPassword: { active: false, touched: false, error: '', shaking: false },
  };
}

function cancelProfileEdit() {
  syncForm();
  resetPasswordForm();
  editing.value = false;
}

async function saveProfile() {
  if (!user.value || saving.value) return;
  profileNicknameState.value.touched = true;
  validateProfileNickname();
  if (profileNicknameState.value.error) {
    shakeState(profileNicknameState.value);
    message.warning('请按提示修正昵称');
    return;
  }
  saving.value = true;
  try {
    user.value = await updateProfile({
      nickname: form.value.nickname.trim(),
      avatarUrl: form.value.avatarUrl.trim() || undefined,
      profileCoverUrl: form.value.profileCoverUrl.trim() || undefined,
      bio: form.value.bio.trim() || undefined,
      college: form.value.college.trim() || undefined,
      major: form.value.major.trim() || undefined,
      grade: form.value.grade.trim() || undefined,
    });
    syncForm();
    editing.value = false;
    message.success('个人资料已保存');
  } catch (error) {
    message.error(error instanceof Error ? error.message : '保存失败');
  } finally {
    saving.value = false;
  }
}

async function savePassword() {
  if (passwordSaving.value) return;
  if (!validatePasswordForm()) {
    message.warning('请按提示修正密码');
    return;
  }
  passwordSaving.value = true;
  try {
    await changePassword(passwordForm.value.oldPassword, passwordForm.value.newPassword);
    resetPasswordForm();
    message.success('密码已更新，请牢记新密码');
  } catch (error) {
    resetPasswordForm();
    message.error(error instanceof Error ? error.message : '密码更新失败');
  } finally {
    passwordSaving.value = false;
  }
}

function openAssetPicker(target: 'avatar' | 'cover') {
  if (assetUploading.value) return;
  if (target === 'avatar') {
    avatarInputRef.value?.click();
  } else {
    coverInputRef.value?.click();
  }
}

async function handleAssetChange(event: Event, target: 'avatar' | 'cover') {
  const input = event.target as HTMLInputElement;
  const file = input.files?.[0];
  input.value = '';
  if (!file || assetUploading.value) return;
  if (!file.type.startsWith('image/')) {
    message.warning('请选择图片文件');
    return;
  }
  if (file.size > 5 * 1024 * 1024) {
    message.warning('图片不能超过 5MB');
    return;
  }

  assetUploading.value = target;
  try {
    const asset = await uploadProfileAsset(file);
    if (target === 'avatar') {
      form.value.avatarUrl = asset.url;
    } else {
      form.value.profileCoverUrl = asset.url;
    }
    message.success(target === 'avatar' ? '头像已上传' : '主页封面已上传');
  } catch (error) {
    message.error(error instanceof Error ? error.message : '图片上传失败');
  } finally {
    assetUploading.value = null;
  }
}

function tabCount(tab: string) {
  if (tab === '帖子') return posts.value.length;
  if (tab === '打卡') return profileChallenges.value.length;
  if (tab === '成就') return achievements.value.length;
  return posts.value.length + profileChallenges.value.length;
}

async function goFollows(tab: 'followers' | 'following') {
  if (!user.value) return;
  followsTab.value = tab;
  followsVisible.value = true;
  followsLoading.value = true;
  try {
    followUsers.value =
      tab === 'followers'
        ? await getUserFollowers(user.value.id, undefined, 30)
        : await getUserFollowing(user.value.id, undefined, 30);
  } catch {
    followUsers.value = [];
    message.error('关注列表加载失败');
  } finally {
    followsLoading.value = false;
  }
}

function openLikes() {
  likesVisible.value = true;
}

async function openPoints() {
  const userId = user.value?.id;
  if (!userId) return;
  pointsVisible.value = true;
  pointsLoading.value = true;
  try {
    const [balance, logs] = await Promise.all([
      getBalance(userId).catch(() => user.value?.points ?? 0),
      getPointsLogs(userId, undefined, 30).catch(() => []),
    ]);
    pointsBalance.value = balance;
    pointLogs.value = logs;
  } catch {
    pointsBalance.value = user.value?.points ?? 0;
    pointLogs.value = [];
    message.error('积分明细加载失败');
  } finally {
    pointsLoading.value = false;
  }
}

function goPost(postId: number) {
  router.push(`/posts/${postId}`);
}

function goChallenge(challengeId: number) {
  router.push(`/checkin/${challengeId}`);
}

function goCreatePost() {
  router.push('/posts/new');
}

function goCheckin() {
  router.push('/checkin');
}

async function copyProfileLink() {
  if (!profileLink.value) return;
  if (await copyTextToClipboard(profileLink.value)) {
    message.success('主页链接已复制');
  } else {
    message.warning(`复制失败，请手动复制：${profileLink.value}`);
  }
}

function formatTime(value?: string) {
  if (!value) return '刚刚';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return '刚刚';
  return date.toLocaleString('zh-CN', { month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' });
}

function formatDate(value?: string | null) {
  if (!value) return '暂无记录';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return '暂无记录';
  return date.toLocaleDateString('zh-CN', { year: 'numeric', month: '2-digit', day: '2-digit' });
}

function formatReadableDate(value?: string | null) {
  if (!value) return '暂无记录';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return '暂无记录';
  return date.toLocaleDateString('zh-CN', { year: 'numeric', month: 'long', day: 'numeric' });
}

function formatCompactNumber(value?: number | null) {
  const count = value ?? 0;
  if (count >= 10000) return `${(count / 10000).toFixed(1)}万`;
  if (count >= 1000) return `${(count / 1000).toFixed(1)}k`;
  return String(count);
}

function pointTypeLabel(type: string) {
  return pointTypeLabels[type] || type || '积分变动';
}

function pointReferenceText(reference?: string | null) {
  return reference?.trim() || '系统结算';
}

function postTitle(post: PostVO) {
  return post.title || '无标题动态';
}

function postPreview(content: string) {
  const normalized = content.replace(/\s+/g, ' ').trim();
  return normalized.length > 140 ? `${normalized.slice(0, 140)}...` : normalized;
}

onMounted(loadProfile);
</script>

<template>
  <div class="profile-layout">
    <header class="top-header">
      <button
        class="header-left"
        type="button"
        @click="router.back()"
      >
        <n-icon size="20">
          <ArrowBackOutline />
        </n-icon>
        <span>个人主页</span>
      </button>
      <div class="header-actions">
        <button
          title="复制主页链接"
          type="button"
          @click="copyProfileLink"
        >
          <n-icon size="20">
            <CopyOutline />
          </n-icon>
        </button>
        <button
          title="编辑资料"
          type="button"
          @click="editing = !editing"
        >
          <n-icon size="20">
            <SettingsOutline />
          </n-icon>
        </button>
      </div>
    </header>

    <div class="scroll-content">
      <div
        v-if="loading"
        class="profile-loading glass-card"
      >
        <n-spin size="large" />
        <span>正在加载个人主页</span>
      </div>

      <div
        v-else-if="user"
        class="profile-container"
      >
        <section class="cover-section glass-card">
          <button
            class="cover-edit-hit"
            type="button"
            title="修改主页封面"
            @click="editing = true"
          >
            <span class="cover-orb cover-orb-primary" />
            <span class="cover-orb cover-orb-secondary" />
            <span class="cover-line" />
          </button>
          <div class="profile-main-info">
            <button
              class="avatar-wrapper"
              type="button"
              title="修改头像"
              @click="editing = true"
            >
              <div class="avatar">
                <img
                  v-if="user.avatarUrl"
                  :src="user.avatarUrl"
                  :alt="user.nickname"
                />
                <span v-else>{{ avatarText }}</span>
              </div>
            </button>
            <div class="user-details">
              <div class="name-row">
                <h2>{{ user.nickname }}</h2>
                <span class="level-tag">{{ user.role || 'USER' }}</span>
              </div>
              <p class="title">
                {{ profileTitle }}
              </p>
              <button
                class="bio-edit"
                type="button"
                title="修改个人简介"
                @click="editing = true"
              >
                {{ user.bio || '还没有写下个人简介。' }}
              </button>

              <div class="stats-row">
                <button
                  v-for="stat in profileStats"
                  :key="stat.key"
                  class="stat"
                  type="button"
                  @click="stat.action"
                >
                  <n-icon class="stat-icon">
                    <component :is="stat.icon" />
                  </n-icon>
                  <span class="stat-copy">
                    <span class="val">{{ stat.value }}</span>
                    <span class="label">{{ stat.label }}</span>
                  </span>
                </button>

                <button
                  class="outline-btn edit-btn"
                  type="button"
                  @click="editing = !editing"
                >
                  {{ editing ? '收起编辑' : '编辑资料' }}
                </button>
              </div>
            </div>
          </div>
        </section>
        <section
          v-if="editing"
          class="edit-panel glass-card"
        >
          <div class="edit-preview">
            <img
              v-if="form.profileCoverUrl"
              :src="form.profileCoverUrl"
              class="cover-preview-image"
              alt="个人主页封面预览"
            />
            <div
              v-else
              class="cover-preview-placeholder"
              aria-hidden="true"
            >
              <span class="preview-orb preview-orb-primary" />
              <span class="preview-orb preview-orb-secondary" />
              <span class="preview-line" />
            </div>
            <button
              class="edit-cover-btn"
              type="button"
              :disabled="Boolean(assetUploading)"
              @click="openAssetPicker('cover')"
            >
              {{ assetUploading === 'cover' ? '上传中...' : '更换封面' }}
            </button>
            <button
              class="avatar preview-avatar"
              type="button"
              :disabled="Boolean(assetUploading)"
              @click="openAssetPicker('avatar')"
            >
              <img
                v-if="form.avatarUrl"
                :src="form.avatarUrl"
                alt="头像预览"
              />
              <span v-else>{{ avatarText }}</span>
              <small>{{ assetUploading === 'avatar' ? '上传中' : '更换头像' }}</small>
            </button>
          </div>
          <input
            ref="avatarInputRef"
            class="asset-input"
            type="file"
            accept="image/png,image/jpeg,image/gif,image/webp"
            @change="handleAssetChange($event, 'avatar')"
          />
          <input
            ref="coverInputRef"
            class="asset-input"
            type="file"
            accept="image/png,image/jpeg,image/gif,image/webp"
            @change="handleAssetChange($event, 'cover')"
          />
          <label
            class="validated-field"
            :class="{ invalid: profileNicknameState.touched && profileNicknameState.error, shake: profileNicknameState.shaking }"
          >
            <span>昵称</span>
            <input
              v-model="form.nickname"
              maxlength="12"
              @focus="focusProfileNickname"
              @blur="blurProfileNickname"
              @input="validateProfileNickname"
            />
            <small
              v-if="profileNicknameState.touched && profileNicknameState.error"
              class="field-hint error"
            >
              {{ profileNicknameState.error }}
            </small>
          </label>
          <div class="asset-field">
            <span>头像</span>
            <button
              type="button"
              :disabled="Boolean(assetUploading)"
              @click="openAssetPicker('avatar')"
            >
              {{ form.avatarUrl ? '重新选择头像' : '选择头像图片' }}
            </button>
          </div>
          <div class="asset-field wide">
            <span>主页封面</span>
            <button
              type="button"
              :disabled="Boolean(assetUploading)"
              @click="openAssetPicker('cover')"
            >
              {{ form.profileCoverUrl ? '重新选择封面' : '选择封面图片' }}
            </button>
          </div>
          <label>
            <span>学院</span>
            <input v-model="form.college" />
          </label>
          <label>
            <span>专业</span>
            <input v-model="form.major" />
          </label>
          <label>
            <span>年级</span>
            <input v-model="form.grade" />
          </label>
          <label class="wide">
            <span>个人简介</span>
            <textarea
              v-model="form.bio"
              maxlength="500"
            />
          </label>
          <div class="password-panel wide">
            <div class="password-panel-head">
              <strong>账号安全</strong>
              <span>修改密码后，下一次登录请使用新密码。</span>
            </div>
            <div class="password-grid">
              <label
                class="validated-field"
                :class="{ invalid: passwordState.oldPassword.touched && passwordState.oldPassword.error, shake: passwordState.oldPassword.shaking }"
              >
                <span>当前密码</span>
                <input
                  v-model="passwordForm.oldPassword"
                  type="password"
                  autocomplete="current-password"
                  @focus="focusPasswordField('oldPassword')"
                  @blur="blurPasswordField('oldPassword')"
                  @input="validatePasswordField('oldPassword')"
                />
                <small
                  v-if="passwordState.oldPassword.touched && passwordState.oldPassword.error"
                  class="field-hint error"
                >
                  {{ passwordState.oldPassword.error }}
                </small>
              </label>
              <label
                class="validated-field"
                :class="{ invalid: passwordState.newPassword.touched && passwordState.newPassword.error, shake: passwordState.newPassword.shaking }"
              >
                <span>新密码</span>
                <input
                  v-model="passwordForm.newPassword"
                  type="password"
                  maxlength="32"
                  autocomplete="new-password"
                  placeholder="8-32 位新密码"
                  @focus="focusPasswordField('newPassword')"
                  @blur="blurPasswordField('newPassword')"
                  @input="validatePasswordField('newPassword')"
                />
                <div
                  v-if="passwordState.newPassword.active || passwordForm.newPassword"
                  class="password-strength"
                  :class="profilePasswordStrength.strength"
                >
                  <div class="strength-bar">
                    <span />
                  </div>
                  <small>密码强度：{{ profilePasswordStrength.label }}，{{ profilePasswordStrength.hint }}</small>
                </div>
                <small
                  v-if="passwordState.newPassword.touched && passwordState.newPassword.error"
                  class="field-hint error"
                >
                  {{ passwordState.newPassword.error }}
                </small>
              </label>
              <label
                class="validated-field"
                :class="{ invalid: passwordState.confirmPassword.touched && passwordState.confirmPassword.error, shake: passwordState.confirmPassword.shaking }"
              >
                <span>确认新密码</span>
                <input
                  v-model="passwordForm.confirmPassword"
                  type="password"
                  maxlength="32"
                  autocomplete="new-password"
                  @focus="focusPasswordField('confirmPassword')"
                  @blur="blurPasswordField('confirmPassword')"
                  @input="validatePasswordField('confirmPassword')"
                />
                <small
                  v-if="passwordState.confirmPassword.touched && passwordState.confirmPassword.error"
                  class="field-hint error"
                >
                  {{ passwordState.confirmPassword.error }}
                </small>
              </label>
              <button
                class="outline-btn password-save-btn"
                type="button"
                :disabled="passwordSaving"
                @click="savePassword"
              >
                {{ passwordSaving ? '更新中...' : '更新密码' }}
              </button>
            </div>
          </div>
          <div class="edit-actions">
            <button
              class="outline-btn"
              type="button"
              @click="cancelProfileEdit"
            >
              取消
            </button>
            <button
              class="primary-btn"
              type="button"
              :disabled="saving"
              @click="saveProfile"
            >
              <n-icon><SaveOutline /></n-icon>
              {{ saving ? '保存中...' : '保存资料' }}
            </button>
          </div>
        </section>

        <div class="content-section">
          <main class="main-left">
            <nav
              class="tabs glass-card"
              :style="{ '--active-tab-index': tabs.indexOf(activeTab), '--tab-count': tabs.length }"
            >
              <button
                v-for="tab in tabs"
                :key="tab"
                type="button"
                class="tab"
                :class="{ active: activeTab === tab }"
                @click="activeTab = tab"
              >
                  <span>{{ tab }}</span>
                  <small>{{ tabCount(tab) }}</small>
                </button>
            </nav>

            <div class="feed-list">
              <template v-if="activeTab === '动态' || activeTab === '帖子'">
                <article
                  v-for="post in feedPosts"
                  :key="post.id"
                  class="feed-item glass-card"
                  @click="goPost(post.id)"
                >
                  <div class="feed-header">
                    <div class="avatar-mini">
                      {{ avatarText }}
                    </div>
                    <div class="info">
                      <span class="name">{{ user.nickname }}</span>
                      <span class="time">{{ formatTime(post.createdAt) }}</span>
                    </div>
                    <n-icon class="more">
                      <DocumentTextOutline />
                    </n-icon>
                  </div>
                  <div class="feed-content">
                    <strong>{{ postTitle(post) }}</strong>
                    <p>{{ postPreview(post.content) }}</p>
                  </div>
                  <div class="feed-actions">
                    <span class="action"><n-icon><ThumbsUpOutline /></n-icon> {{ post.likeCount }}</span>
                    <span class="action"><n-icon><ChatboxOutline /></n-icon> {{ post.commentCount }}</span>
                    <span class="action"><n-icon><ShareSocialOutline /></n-icon> 查看详情</span>
                  </div>
                </article>
                <div
                  v-if="feedPosts.length === 0"
                  class="empty-card glass-card"
                >
                  <n-empty description="还没有发布过帖子" />
                  <button
                    class="primary-btn"
                    type="button"
                    @click="goCreatePost"
                  >
                    发布第一篇帖子
                  </button>
                </div>
              </template>

              <template v-else-if="activeTab === '打卡'">
                <article
                  v-for="challenge in profileChallenges"
                  :key="challenge.id"
                  class="feed-item checkin-item glass-card"
                  @click="goChallenge(challenge.id)"
                >
                  <div class="feed-header">
                    <div class="avatar-mini">
                      <n-icon><CheckmarkCircleOutline /></n-icon>
                    </div>
                    <div class="info">
                      <span class="name">{{ challenge.name }}</span>
                      <span class="time">{{ challenge.startDate }} ~ {{ challenge.endDate }}</span>
                    </div>
                  </div>
                  <div class="feed-content">
                    <p>{{ challenge.description || '这个打卡挑战还没有简介。' }}</p>
                  </div>
                  <div class="feed-actions">
                    <span class="action">累计 {{ challenge.myTotalDays || 0 }} 天</span>
                    <span class="action">连续 {{ challenge.myConsecutiveDays || 0 }} 天</span>
                    <span class="action">参与 {{ challenge.memberCount }} 人</span>
                  </div>
                </article>
                <div
                  v-if="profileChallenges.length === 0"
                  class="empty-card glass-card"
                >
                  <n-empty description="还没有参与打卡挑战" />
                  <button
                    class="primary-btn"
                    type="button"
                    @click="goCheckin"
                  >
                    去打卡广场
                  </button>
                </div>
              </template>

              <template v-else>
                <article
                  v-for="achievement in achievements"
                  :key="achievement.id"
                  class="feed-item achievement-row glass-card"
                  :class="{ locked: !achievement.awarded }"
                >
                  <div class="avatar-mini">
                    <n-icon><component :is="achievementIcon(achievement)" /></n-icon>
                  </div>
                  <div class="achievement-copy">
                    <strong>{{ achievement.name }}</strong>
                    <p>{{ achievement.description }}</p>
                  </div>
                  <span class="achievement-status">
                    <n-icon>
                      <CheckmarkCircleOutline v-if="achievement.awarded" />
                      <LockClosedOutline v-else />
                    </n-icon>
                    {{ achievement.awarded ? '已解锁' : '待解锁' }}
                  </span>
                </article>
                <div
                  v-if="achievements.length === 0"
                  class="empty-card glass-card"
                >
                  <n-empty description="暂无成就数据" />
                </div>
              </template>
            </div>
          </main>

          <aside class="sidebar-right">
            <section class="glass-card widget">
              <div class="widget-header">
                <h3>个人成就</h3>
                <button
                  type="button"
                  @click="activeTab = '成就'"
                >
                  {{ awardedAchievements.length }}/{{ achievements.length || 0 }}
                </button>
              </div>
              <div
                class="badges"
              >
                <button
                  v-for="item in achievementGridItems"
                  :key="item.key"
                  type="button"
                  class="badge neon-glow"
                  :class="{ locked: item.locked, empty: !item.achievement }"
                  :title="item.achievement?.name || '待解锁成就'"
                  @click="activeTab = '成就'"
                >
                  <n-icon>
                    <component :is="item.icon" />
                  </n-icon>
                </button>
              </div>
            </section>

            <section class="glass-card widget">
              <div class="widget-header">
                <h3>近期打卡</h3>
                <button
                  type="button"
                  @click="activeTab = '打卡'"
                >
                  连续 {{ checkinStreak }} 天
                </button>
              </div>
              <div
                v-if="profileChallenges.length"
                class="checkin-list"
              >
                <button
                  v-for="challenge in profileChallenges.slice(0, 3)"
                  :key="challenge.id"
                  type="button"
                  @click="goChallenge(challenge.id)"
                >
                  <strong>{{ challenge.name }}</strong>
                  <span>累计 {{ challenge.myTotalDays || 0 }} 天 · 连续 {{ challenge.myConsecutiveDays || 0 }} 天</span>
                </button>
              </div>
              <p
                v-else
                class="widget-empty"
              >
                暂无打卡记录
              </p>
            </section>

            <section class="glass-card widget">
              <div class="mini-metric">
                <n-icon><CalendarOutline /></n-icon>
                <div>
                  <span>加入时间</span>
                  <strong>{{ formatReadableDate(user.createdAt) }}</strong>
                </div>
              </div>
            </section>
          </aside>
        </div>
      </div>

      <div
        v-else
        class="profile-loading glass-card"
      >
        <n-empty description="个人主页加载失败" />
      </div>
    </div>

    <NModal
      v-model:show="followsVisible"
      preset="card"
      :title="followsTab === 'followers' ? '粉丝' : '关注'"
      class="profile-modal"
      transform-origin="center"
      :style="{ width: 'min(92vw, calc(var(--cf-content-padding) * 12))' }"
    >
      <div class="follow-switch">
        <button
          type="button"
          :class="{ active: followsTab === 'following' }"
          @click="goFollows('following')"
        >
          关注
        </button>
        <button
          type="button"
          :class="{ active: followsTab === 'followers' }"
          @click="goFollows('followers')"
        >
          粉丝
        </button>
      </div>
      <div class="follow-list">
        <article
          v-if="followsLoading"
          class="follow-empty"
        >
          列表加载中...
        </article>
        <article
          v-else-if="followUsers.length === 0"
          class="follow-empty"
        >
          {{ followsTab === 'followers' ? '暂无粉丝' : '暂无关注' }}
        </article>
        <article
          v-for="followUser in followUsers"
          v-else
          :key="followUser.id"
          class="follow-user"
        >
          <img
            v-if="followUser.avatarUrl"
            :src="followUser.avatarUrl"
            :alt="followUser.nickname"
          />
          <span v-else>{{ followUser.nickname.charAt(0).toUpperCase() }}</span>
          <div>
            <strong>{{ followUser.nickname }}</strong>
            <small>{{ [followUser.college, followUser.major].filter(Boolean).join(' · ') || followUser.bio || '校园学习者' }}</small>
          </div>
        </article>
      </div>
    </NModal>

    <NModal
      v-model:show="likesVisible"
      preset="card"
      title="获赞明细"
      class="profile-modal"
      transform-origin="center"
      :style="{ width: 'min(92vw, calc(var(--cf-content-padding) * 14))' }"
    >
      <div class="stat-summary">
        <span>累计获赞</span>
        <strong>{{ formatCompactNumber(likeCount) }}</strong>
        <p>统计当前个人主页帖子获得的点赞数。</p>
      </div>
      <div class="stat-list">
        <article
          v-if="likedPosts.length === 0"
          class="stat-empty"
        >
          <strong>暂无获赞记录</strong>
          <p>发布帖子并获得点赞后，会在这里展示明细。</p>
        </article>
        <button
          v-for="post in likedPosts"
          v-else
          :key="post.id"
          class="stat-detail-item"
          type="button"
          @click="likesVisible = false; goPost(post.id)"
        >
          <span class="stat-item-copy">
            <strong>{{ postTitle(post) }}</strong>
            <small>{{ formatTime(post.createdAt) }} · {{ post.commentCount }} 评论</small>
          </span>
          <span class="stat-item-value">{{ formatCompactNumber(post.likeCount) }} 赞</span>
        </button>
      </div>
    </NModal>

    <NModal
      v-model:show="pointsVisible"
      preset="card"
      title="积分明细"
      class="profile-modal"
      transform-origin="center"
      :style="{ width: 'min(92vw, calc(var(--cf-content-padding) * 15))' }"
    >
      <div class="stat-summary points">
        <span>当前可用积分</span>
        <strong>{{ formatCompactNumber(pointsBalance ?? user?.points ?? 0) }}</strong>
        <p>来自登录、发帖、被点赞、打卡等积分记录。</p>
      </div>
      <div class="stat-list">
        <article
          v-if="pointsLoading"
          class="stat-empty"
        >
          积分明细加载中...
        </article>
        <article
          v-else-if="pointLogs.length === 0"
          class="stat-empty"
        >
          <strong>暂无积分记录</strong>
          <p>参与社区互动后，积分变化会同步到这里。</p>
        </article>
        <article
          v-for="entry in pointLogs"
          v-else
          :key="entry.id"
          class="points-detail-item"
        >
          <span class="stat-item-copy">
            <strong>{{ pointTypeLabel(entry.type) }}</strong>
            <small>{{ pointReferenceText(entry.reference) }} · {{ formatTime(entry.createdAt) }}</small>
          </span>
          <span
            class="stat-item-value"
            :class="{ negative: entry.amount < 0 }"
          >
            {{ entry.amount > 0 ? '+' : '' }}{{ entry.amount }}
          </span>
        </article>
      </div>
    </NModal>
  </div>
</template>

<style scoped lang="scss">
.profile-layout {
  position: relative;
  display: flex;
  flex-direction: column;
  min-height: calc(100vh - var(--cf-header-height));
  background:
    radial-gradient(circle at 16% 8%, color-mix(in srgb, var(--cf-primary) 16%, transparent), transparent 34%),
    radial-gradient(circle at 84% 10%, color-mix(in srgb, var(--cf-secondary) 13%, transparent), transparent 36%),
    linear-gradient(135deg, color-mix(in srgb, var(--cf-primary) 8%, var(--cf-bg-base)), color-mix(in srgb, var(--cf-accent-warm) 6%, var(--cf-bg-base)) 52%, var(--cf-bg-base)),
    var(--cf-bg-base);
  background-size:
    auto,
    auto,
    auto;
  color: var(--cf-text-primary);
  overflow: visible;

  &::before {
    content: '';
    position: absolute;
    inset: 0;
    pointer-events: none;
    background: color-mix(in srgb, var(--cf-bg-glass-soft) 72%, transparent);
    backdrop-filter: blur(calc(var(--cf-backdrop-blur) * 0.6));
    -webkit-backdrop-filter: blur(calc(var(--cf-backdrop-blur) * 0.6));
  }
}

.top-header {
  position: relative;
  z-index: 1;
  min-height: calc(var(--cf-header-height) * 0.72);
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 clamp(var(--cf-radius-lg), 3vw, var(--cf-content-padding));
  border: 0;
  border-radius: var(--cf-radius-xl);
  background: var(--cf-card-bg);
  box-shadow: var(--cf-card-shadow);
  backdrop-filter: blur(var(--cf-backdrop-blur));
  -webkit-backdrop-filter: blur(var(--cf-backdrop-blur));
  margin-bottom: var(--cf-radius-lg);
  z-index: 10;

  .header-left,
  .header-actions button {
    border: 0;
    background: var(--cf-bg-readable);
    color: var(--cf-text-primary);
    cursor: pointer;
    display: inline-flex;
    align-items: center;
    justify-content: center;
    transition: transform var(--cf-motion-duration-standard) var(--cf-motion-ease), border-color var(--cf-motion-duration-standard) var(--cf-motion-ease), box-shadow var(--cf-motion-duration-standard) var(--cf-motion-ease);

    &:hover {
      transform: var(--cf-card-transform);
      border-color: var(--cf-border-strong);
      box-shadow: var(--cf-shadow-soft);
    }
  }

  .header-left {
    gap: var(--cf-radius-sm);
    padding: calc(var(--cf-radius-sm) * 0.9) var(--cf-radius-md);
    border-radius: var(--cf-radius-pill);
    font-weight: 800;
  }

  .header-actions {
    display: flex;
    gap: var(--cf-radius-sm);

    button {
      inline-size: calc(var(--cf-radius-xl) * 2);
      block-size: calc(var(--cf-radius-xl) * 2);
      border-radius: var(--cf-radius-md);
    }
  }
}

.scroll-content {
  position: relative;
  z-index: 1;
  flex: 0 0 auto;
  overflow: visible;
  padding: var(--cf-radius-sm) 0 var(--cf-content-padding);
}

.profile-container {
  width: 100%;
  margin: 0 auto;
  padding-bottom: calc(var(--cf-content-padding) * 1.8);
}

.profile-loading {
  width: min(100%, calc(var(--cf-content-padding) * 27));
  min-height: calc(var(--cf-content-padding) * 11);
  margin: calc(var(--cf-content-padding) * 1.25) auto;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: var(--cf-radius-md);
  color: var(--cf-text-secondary);
}

.glass-card {
  border: var(--cf-hairline) solid var(--cf-border-glass);
  border-radius: var(--cf-radius-xl);
  background:
    linear-gradient(180deg, var(--cf-surface-highlight), transparent 58%),
    color-mix(in srgb, var(--cf-card-bg) 92%, transparent);
  box-shadow: var(--cf-card-shadow);
  backdrop-filter: blur(var(--cf-backdrop-blur));
  -webkit-backdrop-filter: blur(var(--cf-backdrop-blur));
}

.cover-section {
  position: relative;
  overflow: visible;
  min-height: clamp(calc(var(--cf-content-padding) * 7.4), 30vw, calc(var(--cf-content-padding) * 10.2));
  border-radius: calc(var(--cf-radius-xl) + var(--cf-radius-md));
  background:
    linear-gradient(180deg, var(--cf-surface-highlight), transparent 38%),
    color-mix(in srgb, var(--cf-card-bg) 92%, transparent);
  box-shadow: var(--cf-shadow-card-hover);

  .cover-edit-hit {
    position: absolute;
    inset: 0 0 auto;
    block-size: clamp(calc(var(--cf-content-padding) * 4), 18vw, calc(var(--cf-content-padding) * 5.6));
    padding: 0;
    border: 0;
    border-radius: inherit inherit 0 0;
    background:
      radial-gradient(circle at 18% 24%, color-mix(in srgb, var(--cf-primary) 18%, transparent), transparent 42%),
      radial-gradient(circle at 72% 18%, color-mix(in srgb, var(--cf-secondary) 12%, transparent), transparent 46%),
      linear-gradient(135deg, var(--cf-surface-highlight), transparent 62%),
      var(--cf-bg-readable);
    cursor: pointer;
    z-index: 0;
    overflow: hidden;
  }

  .cover-orb,
  .cover-line {
    position: absolute;
    pointer-events: none;
  }

  .cover-orb {
    inline-size: min(36%, calc(var(--cf-content-padding) * 8));
    aspect-ratio: 1;
    border-radius: var(--cf-radius-pill);
    filter: blur(var(--cf-backdrop-blur));
    opacity: var(--cf-announcement-body-opacity);
  }

  .cover-orb-primary {
    inset: auto 12% -28% auto;
    background: radial-gradient(circle, var(--cf-primary-soft), transparent 64%);
  }

  .cover-orb-secondary {
    inset: -34% auto auto 8%;
    background: radial-gradient(circle, var(--cf-secondary-soft), transparent 66%);
  }

  .cover-line {
    inset: auto 8% 18%;
    block-size: var(--cf-radius-sm);
    border-radius: var(--cf-radius-pill);
    background: linear-gradient(90deg, transparent, var(--cf-bg-glass-strong), transparent);
    filter: blur(calc(var(--cf-backdrop-blur) / 2));
  }

  &::after {
    content: '';
    position: absolute;
    inset: clamp(calc(var(--cf-content-padding) * 4), 18vw, calc(var(--cf-content-padding) * 5.6)) 0 0;
    border-radius: 0 0 calc(var(--cf-radius-xl) + var(--cf-radius-md)) calc(var(--cf-radius-xl) + var(--cf-radius-md));
    background: color-mix(in srgb, var(--cf-card-bg) 95%, transparent);
    pointer-events: none;
    box-shadow: inset 0 calc(var(--cf-hairline) * 2) 0 var(--cf-surface-highlight);
  }

  .profile-main-info {
    position: absolute;
    z-index: 1;
    inset: clamp(calc(var(--cf-content-padding) * 2.8), 16vw, calc(var(--cf-content-padding) * 4.3))
      clamp(var(--cf-radius-lg), 4vw, calc(var(--cf-content-padding) * 1.5)) auto;
    display: flex;
    align-items: flex-start;
    gap: clamp(var(--cf-radius-xl), 3vw, calc(var(--cf-content-padding) * 1.15));
  }

  .avatar-wrapper {
    padding: 0;
    border: 0;
    background: transparent;
    cursor: pointer;
    flex: 0 0 auto;
  }

  .avatar {
    inline-size: clamp(calc(var(--cf-content-padding) * 2.7), 10vw, calc(var(--cf-content-padding) * 4));
    block-size: clamp(calc(var(--cf-content-padding) * 2.7), 10vw, calc(var(--cf-content-padding) * 4));
    border-radius: 50%;
    overflow: hidden;
    border: calc(var(--cf-hairline) * 4) solid color-mix(in srgb, var(--cf-bg-readable) 92%, transparent);
    background:
      radial-gradient(circle at 34% 24%, var(--cf-surface-highlight), transparent 32%),
      linear-gradient(145deg, color-mix(in srgb, var(--cf-primary) 78%, var(--cf-secondary)), var(--cf-primary));
    box-shadow:
      inset 0 var(--cf-hairline) 0 var(--cf-surface-highlight),
      var(--cf-shadow-float);
    color: var(--cf-text-inverse);
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: clamp(2rem, 5vw, 3.5rem);
    font-family: var(--cf-font-heading);
    font-weight: 700;
    letter-spacing: 0;

    img {
      width: 100%;
      height: 100%;
      object-fit: cover;
    }
  }
}

.user-details {
  min-width: 0;
  flex: 1;
  padding-top: calc(var(--cf-radius-sm) / 2);
  max-width: min(100%, calc(var(--cf-content-padding) * 25));
}

.name-row {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: var(--cf-radius-sm);
  margin-bottom: calc(var(--cf-radius-sm) / 1.2);

  h2 {
    margin: 0;
    font-family: var(--cf-font-heading);
    font-size: clamp(1.7rem, 3vw, 2.35rem);
    font-weight: 750;
    line-height: 1.15;
    letter-spacing: 0;
  }
}

.level-tag {
  border: 0;
  border-radius: var(--cf-radius-pill);
  background: var(--cf-primary-soft);
  color: var(--cf-primary);
  padding: calc(var(--cf-radius-sm) / 2) var(--cf-radius-sm);
  font-size: 0.75rem;
  font-weight: 700;
}

.title,
.bio {
  margin: 0;
  color: var(--cf-text-secondary);
  font-weight: 400;
}

.bio,
.bio-edit {
  margin-top: var(--cf-radius-sm);
  line-height: 1.75;
}

.bio-edit {
  display: block;
  width: 100%;
  border: 0;
  border-radius: 0;
  background: transparent;
  color: var(--cf-text-secondary);
  cursor: pointer;
  padding: 0;
  text-align: left;
  font-weight: 400;
  transition: color var(--cf-motion-duration-standard) var(--cf-motion-ease), border-color var(--cf-motion-duration-standard) var(--cf-motion-ease), background var(--cf-motion-duration-standard) var(--cf-motion-ease);

  &:hover {
    color: var(--cf-text-primary);
    background: transparent;
  }
}

.stats-row {
  --stat-divider: color-mix(in srgb, var(--cf-text-primary) 9%, transparent);
  display: flex;
  align-items: center;
  flex-wrap: nowrap;
  gap: 0;
  width: fit-content;
  max-width: 100%;
  margin-top: clamp(var(--cf-radius-md), 2vw, var(--cf-radius-xl));
  padding: calc(var(--cf-radius-sm) * 0.62);
  border-radius: var(--cf-radius-pill);
  background:
    linear-gradient(180deg, var(--cf-surface-highlight), transparent),
    color-mix(in srgb, var(--cf-bg-glass-soft) 86%, transparent);
  box-shadow: inset 0 var(--cf-hairline) 0 var(--cf-surface-highlight), var(--cf-shadow-card);
  backdrop-filter: blur(var(--cf-backdrop-blur));
  -webkit-backdrop-filter: blur(var(--cf-backdrop-blur));
}

.stat {
  position: relative;
  min-width: calc(var(--cf-content-padding) * 2.55);
  border: 0;
  border-radius: var(--cf-radius-pill);
  background: transparent;
  color: inherit;
  display: flex;
  flex-direction: row;
  align-items: center;
  gap: calc(var(--cf-radius-sm) * 0.85);
  padding: calc(var(--cf-radius-sm) * 0.72) var(--cf-radius-md);
  text-align: left;
  font: inherit;
  transition: transform var(--cf-motion-duration-standard) var(--cf-motion-ease), background-color var(--cf-motion-duration-standard) var(--cf-motion-ease), color var(--cf-motion-duration-standard) var(--cf-motion-ease);

  &button,
  &[type='button'] {
    cursor: pointer;
  }

  & + .stat::before {
    content: '';
    position: absolute;
    inset-block: 28%;
    inset-inline-start: 0;
    inline-size: var(--cf-hairline);
    border-radius: var(--cf-radius-pill);
    background: var(--stat-divider);
  }

  .label {
    color: var(--cf-text-muted);
    font-size: 0.72rem;
    font-weight: 500;
    line-height: 1.15;
  }

  .val {
    color: var(--cf-text-primary);
    font-size: clamp(1rem, 1.6vw, 1.28rem);
    font-weight: 700;
    line-height: 1;
  }

  .stat-icon {
    color: var(--cf-primary);
    flex: 0 0 auto;
    font-size: 1.05rem;
    opacity: 0.9;
  }

  .stat-copy {
    display: flex;
    flex-direction: column;
    gap: calc(var(--cf-radius-sm) / 3);
    min-width: 0;
  }

  &:hover {
    background: var(--cf-primary-soft);
    color: var(--cf-primary);
    transform: var(--cf-card-transform);
  }

  &:hover .val {
    color: var(--cf-primary);
  }
}

.outline-btn,
.primary-btn {
  border: var(--cf-hairline) solid var(--cf-border-glass);
  border-radius: var(--cf-radius-md);
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: var(--cf-radius-sm);
  min-height: calc(var(--cf-radius-xl) * 2);
  padding: var(--cf-radius-sm) var(--cf-radius-lg);
  font-weight: 900;
  transition: transform var(--cf-motion-duration-standard) var(--cf-motion-ease), border-color var(--cf-motion-duration-standard) var(--cf-motion-ease), box-shadow var(--cf-motion-duration-standard) var(--cf-motion-ease), opacity var(--cf-motion-duration-standard) var(--cf-motion-ease), background-color var(--cf-motion-duration-standard) var(--cf-motion-ease);

  &:hover:not(:disabled) {
    transform: var(--cf-card-transform);
    border-color: var(--cf-border-strong);
    box-shadow: var(--cf-shadow-soft);
  }

  &:disabled {
    cursor: wait;
    opacity: 0.6;
  }
}

.outline-btn {
  background: var(--cf-bg-glass);
  color: var(--cf-text-primary);
}

.primary-btn {
  background: var(--cf-primary);
  color: var(--cf-text-inverse);
}

.edit-btn {
  margin-left: auto;
  white-space: nowrap;
  border-radius: var(--cf-radius-pill);
  border-color: color-mix(in srgb, var(--cf-text-primary) 7%, transparent);
  background: color-mix(in srgb, var(--cf-bg-glass) 66%, transparent);
  color: var(--cf-text-secondary);
  box-shadow: inset 0 var(--cf-hairline) 0 var(--cf-surface-highlight), var(--cf-shadow-soft);
  font-weight: 650;

  &:hover:not(:disabled) {
    background: color-mix(in srgb, var(--cf-primary-soft) 32%, var(--cf-bg-glass-strong));
    color: var(--cf-primary);
  }
}

.edit-panel {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--cf-radius-lg);
  margin-top: var(--cf-radius-lg);
  padding: clamp(var(--cf-radius-lg), 3vw, var(--cf-content-padding));

  .edit-preview {
    position: relative;
    grid-column: 1 / -1;
    overflow: hidden;
    min-height: calc(var(--cf-content-padding) * 5.6);
    border: 0;
    border-radius: var(--cf-radius-lg);
    background: var(--cf-bg-glass);

    .cover-preview-image,
    .cover-preview-placeholder {
      position: absolute;
      inset: 0;
      width: 100%;
      height: 100%;
    }

    .cover-preview-image {
      object-fit: cover;
      opacity: 0.9;
    }

    .cover-preview-placeholder {
      background:
        linear-gradient(135deg, var(--cf-primary-soft), transparent 58%),
        linear-gradient(315deg, var(--cf-secondary-soft), transparent 62%),
        var(--cf-bg-readable);
      overflow: hidden;
    }

    .preview-orb,
    .preview-line {
      position: absolute;
      pointer-events: none;
    }

    .preview-orb {
      inline-size: min(34%, calc(var(--cf-content-padding) * 7));
      aspect-ratio: 1;
      border-radius: var(--cf-radius-pill);
      filter: blur(var(--cf-backdrop-blur));
    }

    .preview-orb-primary {
      inset: auto 12% -26% auto;
      background: radial-gradient(circle, var(--cf-primary-soft), transparent 64%);
    }

    .preview-orb-secondary {
      inset: -30% auto auto 10%;
      background: radial-gradient(circle, var(--cf-secondary-soft), transparent 66%);
    }

    .preview-line {
      inset: auto 10% 22%;
      block-size: var(--cf-radius-sm);
      border-radius: var(--cf-radius-pill);
      background: linear-gradient(90deg, transparent, var(--cf-bg-glass-strong), transparent);
      filter: blur(calc(var(--cf-backdrop-blur) / 2));
    }

    &::after {
      content: '';
      position: absolute;
      inset: 0;
      background:
        linear-gradient(180deg, transparent 20%, color-mix(in srgb, var(--cf-bg-base) 48%, transparent)),
        radial-gradient(circle at 18% 22%, color-mix(in srgb, var(--cf-primary) 16%, transparent), transparent 34%),
        radial-gradient(circle at 82% 24%, color-mix(in srgb, var(--cf-secondary) 14%, transparent), transparent 32%);
    }
  }

  .preview-avatar {
    position: absolute;
    left: var(--cf-radius-lg);
    bottom: var(--cf-radius-lg);
    z-index: 1;
    width: calc(var(--cf-content-padding) * 2.6);
    height: calc(var(--cf-content-padding) * 2.6);
    border: calc(var(--cf-hairline) * 3) solid color-mix(in srgb, var(--cf-bg-readable) 88%, transparent);
    box-shadow: var(--cf-shadow-float);
    padding: 0;
    cursor: pointer;

    small {
      position: absolute;
      inset: auto 0 0;
      background: color-mix(in srgb, var(--cf-text-primary) 54%, transparent);
      color: var(--cf-text-inverse);
      font-size: 0.7rem;
      line-height: var(--cf-content-padding);
      text-align: center;
    }
  }

  .edit-cover-btn,
  .asset-field button {
    border: 0;
    border-radius: var(--cf-radius-md);
    background: var(--cf-bg-glass);
    color: var(--cf-primary);
    cursor: pointer;
    font-weight: 900;
    transition: transform var(--cf-motion-duration-standard) var(--cf-motion-ease), border-color var(--cf-motion-duration-standard) var(--cf-motion-ease), box-shadow var(--cf-motion-duration-standard) var(--cf-motion-ease), opacity var(--cf-motion-duration-standard) var(--cf-motion-ease);

    &:hover:not(:disabled) {
      transform: var(--cf-card-transform);
      border-color: var(--cf-border-strong);
      box-shadow: var(--cf-shadow-soft);
    }

    &:disabled {
      cursor: wait;
      opacity: 0.62;
    }
  }

  .edit-cover-btn {
    position: absolute;
    right: var(--cf-radius-lg);
    bottom: var(--cf-radius-lg);
    z-index: 1;
    padding: var(--cf-radius-sm) var(--cf-radius-md);
  }

  .asset-input {
    display: none;
  }

  .asset-field {
    display: flex;
    flex-direction: column;
    gap: var(--cf-radius-sm);

    &.wide {
      grid-column: 1 / -1;
    }

    button {
      min-height: calc(var(--cf-radius-xl) * 2.1);
      padding: var(--cf-radius-sm) var(--cf-radius-md);
      text-align: left;
    }
  }

  label {
    display: flex;
    flex-direction: column;
    gap: var(--cf-radius-sm);

    &.wide {
      grid-column: 1 / -1;
    }
  }

  span {
    color: var(--cf-text-secondary);
    font-size: 0.82rem;
    font-weight: 800;
  }

  input,
  textarea {
    width: 100%;
    border: 0;
    border-radius: var(--cf-radius-md);
    background: var(--cf-bg-readable);
    color: var(--cf-text-primary);
    outline: none;
    padding: var(--cf-radius-sm) var(--cf-radius-md);

    &:focus {
      border-color: var(--cf-border-strong);
      box-shadow: 0 0 0 calc(var(--cf-hairline) * 4) color-mix(in srgb, var(--cf-primary) 10%, transparent);
    }
  }

  textarea {
    min-height: calc(var(--cf-content-padding) * 3);
    resize: vertical;
  }

  .validated-field.invalid {
    input {
      border-color: var(--cf-danger);
      box-shadow: 0 0 0 calc(var(--cf-hairline) * 4) color-mix(in srgb, var(--cf-danger) 12%, transparent);
    }
  }

  .validated-field.shake {
    animation: field-shake var(--cf-motion-duration-emphasis) var(--cf-motion-ease);
  }
}

.field-hint {
  min-height: calc(var(--cf-radius-sm) * 2.25);
  font-size: 0.75rem;
  line-height: 1.5;

  &.error {
    color: var(--cf-danger);
  }
}

.password-panel {
  grid-column: 1 / -1;
  display: grid;
  gap: var(--cf-radius-lg);
  padding: var(--cf-radius-lg);
  border: 0;
  border-radius: var(--cf-radius-lg);
  background: var(--cf-bg-glass-soft);
}

.password-panel-head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: var(--cf-radius-md);

  strong {
    font-size: 0.95rem;
  }

  span {
    color: var(--cf-text-muted);
    font-size: 0.75rem;
  }
}

.password-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: var(--cf-radius-md);
  align-items: start;
}

.password-save-btn {
  grid-column: 1 / -1;
  justify-self: flex-end;
  min-height: calc(var(--cf-radius-xl) * 2.1);
  align-self: end;
}

.password-strength {
  display: grid;
  gap: calc(var(--cf-radius-sm) / 1.2);

  small {
    color: var(--cf-text-muted);
    font-size: 0.75rem;
    line-height: 1.5;
  }

  &.weak {
    --strength-color: var(--cf-danger);
    --strength-width: 33%;
  }

  &.medium {
    --strength-color: var(--cf-warning);
    --strength-width: 66%;
  }

  &.strong {
    --strength-color: var(--cf-primary);
    --strength-width: 100%;
  }

  &.empty {
    --strength-color: var(--cf-border-strong);
    --strength-width: 0%;
  }
}

.strength-bar {
  height: calc(var(--cf-radius-sm) * 0.75);
  overflow: hidden;
  border-radius: var(--cf-radius-pill);
  background: var(--cf-bg-glass-soft);

  span {
    display: block;
    width: var(--strength-width);
    height: 100%;
    border-radius: inherit;
    background: var(--strength-color);
    transition: width var(--cf-motion-duration-fast) var(--cf-motion-ease), background-color var(--cf-motion-duration-fast) var(--cf-motion-ease);
  }
}

@keyframes field-shake {
  0%, 100% {
    transform: translateX(0);
  }
  20%, 60% {
    transform: translateX(calc(var(--cf-radius-sm) / -2));
  }
  40%, 80% {
    transform: translateX(calc(var(--cf-radius-sm) / 2));
  }
}

.follow-switch {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--cf-radius-sm);
  margin-bottom: var(--cf-radius-lg);

  button {
    border: 0;
    border-radius: var(--cf-radius-md);
    background: var(--cf-bg-glass);
    color: var(--cf-text-secondary);
    cursor: pointer;
    padding: var(--cf-radius-sm) var(--cf-radius-md);
    font-weight: 900;

    &.active {
      border-color: color-mix(in srgb, var(--cf-primary) 36%, transparent);
      background: var(--cf-primary-soft);
      color: var(--cf-primary);
    }
  }
}

.follow-list {
  display: flex;
  flex-direction: column;
  gap: var(--cf-radius-sm);
}

.stat-summary {
  border: 0;
  border-radius: var(--cf-radius-lg);
  background:
    linear-gradient(135deg, color-mix(in srgb, var(--cf-primary) 14%, transparent), transparent 68%),
    var(--cf-bg-glass);
  padding: var(--cf-radius-lg);
  margin-bottom: var(--cf-radius-md);

  &.points {
    background:
      linear-gradient(135deg, color-mix(in srgb, var(--cf-warning) 18%, transparent), transparent 68%),
      var(--cf-bg-glass);
  }

  span {
    color: var(--cf-text-secondary);
    display: block;
    font-size: 0.75rem;
    margin-bottom: calc(var(--cf-radius-sm) / 1.2);
  }

  strong {
    color: var(--cf-text-primary);
    display: block;
    font-size: clamp(2rem, 5vw, 2.6rem);
    line-height: 1;
  }

  p {
    color: var(--cf-text-muted);
    font-size: 0.82rem;
    line-height: 1.55;
    margin: var(--cf-radius-sm) 0 0;
  }
}

.stat-list {
  display: flex;
  flex-direction: column;
  gap: var(--cf-radius-sm);
}

.stat-empty,
.stat-detail-item,
.points-detail-item {
  border: 0;
  border-radius: var(--cf-radius-md);
  background: var(--cf-bg-glass);
}

.stat-empty {
  color: var(--cf-text-secondary);
  padding: var(--cf-radius-lg);
  text-align: center;

  strong {
    color: var(--cf-text-primary);
    display: block;
    margin-bottom: calc(var(--cf-radius-sm) / 1.2);
  }

  p {
    margin: 0;
  }
}

.stat-detail-item,
.points-detail-item {
  width: 100%;
  align-items: center;
  display: flex;
  justify-content: space-between;
  gap: var(--cf-radius-md);
  padding: var(--cf-radius-md);
  text-align: left;
}

.stat-detail-item {
  color: inherit;
  cursor: pointer;
  font: inherit;
  transition: transform var(--cf-motion-duration-standard) var(--cf-motion-ease), border-color var(--cf-motion-duration-standard) var(--cf-motion-ease), box-shadow var(--cf-motion-duration-standard) var(--cf-motion-ease);

  &:hover {
    transform: var(--cf-card-transform);
    border-color: var(--cf-border-strong);
    box-shadow: var(--cf-shadow-soft);
  }
}

.stat-item-copy {
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: calc(var(--cf-radius-sm) / 2);

  strong,
  small {
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  strong {
    color: var(--cf-text-primary);
    font-size: 0.9rem;
  }

  small {
    color: var(--cf-text-muted);
    font-size: 0.75rem;
  }
}

.stat-item-value {
  flex: 0 0 auto;
  border-radius: var(--cf-radius-pill);
  background: color-mix(in srgb, var(--cf-primary) 12%, transparent);
  color: var(--cf-primary);
  font-size: 0.75rem;
  font-weight: 900;
  padding: calc(var(--cf-radius-sm) / 1.6) var(--cf-radius-sm);

  &.negative {
    background: color-mix(in srgb, var(--cf-danger) 12%, transparent);
    color: var(--cf-danger);
  }
}

.follow-empty,
.follow-user {
  border: 0;
  border-radius: var(--cf-radius-md);
  background: var(--cf-bg-glass);
}

.follow-empty {
  padding: var(--cf-radius-lg);
  color: var(--cf-text-secondary);
  text-align: center;
}

.follow-user {
  display: flex;
  align-items: center;
  gap: var(--cf-radius-sm);
  padding: var(--cf-radius-sm) var(--cf-radius-md);

  img,
  > span {
    width: calc(var(--cf-radius-lg) * 2.1);
    height: calc(var(--cf-radius-lg) * 2.1);
    border-radius: 50%;
    flex: 0 0 auto;
  }

  > span {
    background: var(--cf-gradient-primary);
    color: var(--cf-text-inverse);
    display: inline-flex;
    align-items: center;
    justify-content: center;
    font-weight: 900;
  }

  div {
    min-width: 0;
    display: flex;
    flex-direction: column;
    gap: calc(var(--cf-radius-sm) / 4);
  }

  strong {
    color: var(--cf-text-primary);
  }

  small {
    color: var(--cf-text-muted);
  }
}

:global(.profile-modal.n-card) {
  background:
    linear-gradient(180deg, color-mix(in srgb, var(--cf-bg-base) 16%, transparent), transparent 54%),
    color-mix(in srgb, var(--cf-bg-base) 72%, transparent);
  border: 0;
  box-shadow: var(--cf-shadow-float);
  backdrop-filter: blur(var(--cf-backdrop-blur));
  -webkit-backdrop-filter: blur(var(--cf-backdrop-blur));
}

.edit-actions {
  grid-column: 1 / -1;
  display: flex;
  justify-content: flex-end;
  gap: var(--cf-radius-sm);
}

.content-section {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(calc(var(--cf-content-padding) * 8.4), 0.3fr);
  gap: clamp(var(--cf-radius-xl), 3.2vw, calc(var(--cf-content-padding) * 1.2));
  margin-top: clamp(var(--cf-radius-lg), 3vw, var(--cf-content-padding));
}

.tabs {
  --tab-slot-size: calc(100% / var(--tab-count));
  display: flex;
  position: relative;
  gap: 0;
  padding: calc(var(--cf-radius-sm) / 2);
  margin-bottom: var(--cf-radius-lg);
  background:
    linear-gradient(180deg, var(--cf-surface-highlight), transparent),
    color-mix(in srgb, var(--cf-bg-glass-soft) 82%, transparent);
  border-radius: var(--cf-radius-pill);
  box-shadow: inset 0 var(--cf-hairline) 0 var(--cf-surface-highlight), var(--cf-shadow-soft);
  overflow-x: auto;
  scrollbar-width: none;
  backdrop-filter: blur(var(--cf-backdrop-blur));
  -webkit-backdrop-filter: blur(var(--cf-backdrop-blur));

  &::before {
    content: '';
    position: absolute;
    inset-block: calc(var(--cf-radius-sm) / 2);
    inset-inline-start: calc(var(--cf-radius-sm) / 2);
    inline-size: calc(var(--tab-slot-size) - var(--cf-radius-sm));
    border-radius: var(--cf-radius-pill);
    background: var(--cf-bg-readable);
    box-shadow: var(--cf-shadow-soft), inset 0 var(--cf-hairline) 0 var(--cf-surface-highlight);
    transform: translateX(calc(var(--active-tab-index) * 100%));
    transition: transform var(--cf-motion-duration-standard) var(--cf-motion-ease), box-shadow var(--cf-motion-duration-standard) var(--cf-motion-ease);
  }

  &::-webkit-scrollbar {
    display: none;
  }

  .tab {
    position: relative;
    z-index: 1;
    flex: 1 0 var(--tab-slot-size);
    border: none;
    border-radius: var(--cf-radius-pill);
    background: transparent;
    color: var(--cf-text-muted);
    cursor: pointer;
    display: inline-flex;
    align-items: center;
    justify-content: center;
    gap: calc(var(--cf-radius-sm) / 1.2);
    min-height: calc(var(--cf-radius-xl) * 2.2);
    padding: 0 var(--cf-radius-md);
    font-weight: 550;
    white-space: nowrap;
    transition: color var(--cf-motion-duration-standard) var(--cf-motion-ease), transform var(--cf-motion-duration-standard) var(--cf-motion-ease);

    small {
      border-radius: var(--cf-radius-pill);
      background: color-mix(in srgb, var(--cf-bg-muted) 76%, transparent);
      padding: calc(var(--cf-radius-sm) / 4) calc(var(--cf-radius-sm) * 0.8);
      color: var(--cf-text-muted);
      font-size: 0.7rem;
      font-weight: 600;
    }

    &.active {
      background: transparent;
      color: var(--cf-primary);
      box-shadow: none;
      font-weight: 750;
    }

    &.active small {
      background: var(--cf-primary-soft);
      color: var(--cf-primary);
    }

    &:hover {
      color: var(--cf-primary);
      transform: translateY(calc(var(--cf-radius-sm) / -6));
    }
  }
}

.feed-list {
  display: flex;
  flex-direction: column;
  gap: clamp(var(--cf-radius-lg), 2vw, var(--cf-radius-xl));
}

.feed-item {
  padding: clamp(var(--cf-radius-lg), 2.6vw, var(--cf-content-padding));
  cursor: pointer;
  transition: transform var(--cf-motion-duration-standard) var(--cf-motion-ease), border-color var(--cf-motion-duration-standard) var(--cf-motion-ease), box-shadow var(--cf-motion-duration-standard) var(--cf-motion-ease);

  &:hover {
    transform: var(--cf-card-transform);
    box-shadow: var(--cf-shadow-card-hover);
  }
}

.feed-header,
.feed-actions,
.achievement-row {
  display: flex;
  align-items: center;
  gap: var(--cf-radius-md);
}

.avatar-mini {
  width: calc(var(--cf-radius-xl) * 2.1);
  height: calc(var(--cf-radius-xl) * 2.1);
  border-radius: 50%;
  background:
    radial-gradient(circle at 34% 22%, var(--cf-surface-highlight), transparent 34%),
    color-mix(in srgb, var(--cf-primary) 16%, var(--cf-bg-glass));
  color: var(--cf-text-inverse);
  display: flex;
  align-items: center;
  justify-content: center;
  flex: 0 0 auto;
  font-weight: 900;
}

.info {
  display: flex;
  flex-direction: column;
  gap: calc(var(--cf-radius-sm) / 2.5);
  min-width: 0;

  .name {
    color: var(--cf-text-primary);
    font-weight: 900;
  }

  .time {
    color: var(--cf-text-muted);
    font-size: 0.75rem;
  }
}

.more {
  margin-left: auto;
  color: var(--cf-text-secondary);
}

.feed-content {
  margin-top: var(--cf-radius-lg);

  strong {
    color: var(--cf-text-primary);
    font-family: var(--cf-font-heading);
    font-size: 1.05rem;
  }

  p {
    margin: var(--cf-radius-sm) 0 0;
    color: var(--cf-text-secondary);
    line-height: 1.8;
  }
}

.feed-actions {
  flex-wrap: wrap;
  margin-top: var(--cf-radius-lg);
  color: var(--cf-text-secondary);
  gap: var(--cf-radius-md);

  .action {
    display: inline-flex;
    align-items: center;
    gap: calc(var(--cf-radius-sm) / 1.2);
    color: var(--cf-text-muted);
    font-size: 0.82rem;
  }
}

.checkin-item .avatar-mini {
  border-radius: var(--cf-radius-lg);
}

.achievement-row {
  cursor: default;
  display: grid;
  grid-template-columns: calc(var(--cf-radius-xl) * 2.35) minmax(0, 1fr) auto;
  align-items: center;
  min-height: calc(var(--cf-content-padding) * 3.05);
  overflow: hidden;
  background:
    radial-gradient(circle at 8% 18%, color-mix(in srgb, var(--cf-primary) 8%, transparent), transparent 36%),
    linear-gradient(180deg, var(--cf-surface-highlight), transparent 54%),
    color-mix(in srgb, var(--cf-card-bg) 96%, transparent);
  box-shadow: var(--cf-shadow-card), 0 calc(var(--cf-radius-sm) * 1.4) calc(var(--cf-radius-xl) * 1.6) color-mix(in srgb, var(--cf-primary) 7%, transparent);
  isolation: isolate;

  &:hover {
    transform: var(--cf-card-transform);
    box-shadow: var(--cf-shadow-card-hover), 0 calc(var(--cf-radius-sm) * 1.8) calc(var(--cf-radius-xl) * 2) color-mix(in srgb, var(--cf-primary) 9%, transparent);
  }

  &.locked {
    background:
      linear-gradient(180deg, var(--cf-surface-highlight), transparent),
      color-mix(in srgb, var(--cf-bg-glass-soft) 88%, transparent);
    box-shadow: var(--cf-shadow-card);
    filter: saturate(0.55);
    opacity: 0.78;
  }

  .avatar-mini {
    width: calc(var(--cf-radius-xl) * 2.35);
    height: calc(var(--cf-radius-xl) * 2.35);
    background:
      radial-gradient(circle at 34% 22%, var(--cf-surface-highlight), transparent 34%),
      color-mix(in srgb, var(--cf-primary) 15%, var(--cf-bg-glass));
    color: var(--cf-primary);
    box-shadow:
      inset 0 var(--cf-hairline) 0 var(--cf-surface-highlight),
      0 calc(var(--cf-radius-sm) * 1.15) calc(var(--cf-radius-lg) * 1.2) color-mix(in srgb, var(--cf-primary) 13%, transparent);
  }

  &.locked .avatar-mini,
  &.locked .achievement-copy {
    color: var(--cf-text-muted);
    opacity: 0.72;
  }

  p {
    margin: calc(var(--cf-radius-sm) / 2) 0 0;
    color: var(--cf-text-secondary);
    line-height: 1.45;
    font-weight: 400;
  }

  .achievement-copy {
    min-width: 0;

    strong {
      font-weight: 700;
      letter-spacing: 0;
    }
  }

  .achievement-status {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    gap: calc(var(--cf-radius-sm) / 2);
    border-radius: var(--cf-radius-pill);
    background: color-mix(in srgb, var(--cf-primary-soft) 78%, var(--cf-bg-glass));
    color: var(--cf-primary);
    font-size: 0.75rem;
    font-weight: 650;
    padding: calc(var(--cf-radius-sm) / 1.5) var(--cf-radius-sm);
    white-space: nowrap;
  }

  &.locked .achievement-status {
    background: color-mix(in srgb, var(--cf-bg-glass-soft) 82%, transparent);
    color: var(--cf-text-muted);
  }
}

.empty-card {
  padding: clamp(var(--cf-content-padding), 5vw, calc(var(--cf-content-padding) * 1.8)) var(--cf-radius-lg);
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--cf-radius-lg);
}

.sidebar-right {
  display: flex;
  flex-direction: column;
  gap: clamp(var(--cf-radius-lg), 2vw, var(--cf-radius-xl));
}

.widget {
  position: relative;
  overflow: hidden;
  padding: clamp(var(--cf-radius-lg), 2.4vw, var(--cf-content-padding));
  border-radius: calc(var(--cf-radius-xl) + var(--cf-radius-sm));
  background:
    radial-gradient(circle at 82% 0, color-mix(in srgb, var(--cf-primary) 9%, transparent), transparent 38%),
    linear-gradient(180deg, var(--cf-surface-highlight), transparent 48%),
    color-mix(in srgb, var(--cf-card-bg) 96%, transparent);
  box-shadow: var(--cf-shadow-card);

  &::before {
    content: '';
    position: absolute;
    inset: var(--cf-radius-sm);
    border-radius: calc(var(--cf-radius-xl) + var(--cf-radius-sm));
    box-shadow: inset 0 var(--cf-hairline) 0 var(--cf-surface-highlight);
    pointer-events: none;
  }
}

.widget-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--cf-radius-sm);
  margin-bottom: var(--cf-radius-lg);

  h3 {
    margin: 0;
    font-size: 1rem;
    font-weight: 720;
    letter-spacing: 0;
  }

  button {
    border: none;
    border-radius: var(--cf-radius-pill);
    background: color-mix(in srgb, var(--cf-bg-glass-soft) 84%, transparent);
    color: var(--cf-primary);
    cursor: pointer;
    font-weight: 700;
    padding: calc(var(--cf-radius-sm) / 1.4) var(--cf-radius-sm);
    transition: background-color var(--cf-motion-duration-standard) var(--cf-motion-ease), transform var(--cf-motion-duration-standard) var(--cf-motion-ease);

    &:hover {
      background: var(--cf-primary-soft);
      transform: translateY(calc(var(--cf-radius-sm) / -6));
    }
  }
}

.badges {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: calc(var(--cf-radius-sm) * 1.15);
  padding: calc(var(--cf-radius-sm) * 0.5);
  border-radius: var(--cf-radius-lg);
  background: color-mix(in srgb, var(--cf-bg-glass-soft) 72%, transparent);
  box-shadow: inset 0 var(--cf-hairline) 0 var(--cf-surface-highlight);
}

.badge {
  aspect-ratio: 1;
  border: 0;
  border-radius: calc(var(--cf-radius-md) + var(--cf-radius-sm));
  background:
    radial-gradient(circle at 34% 22%, var(--cf-surface-highlight), transparent 36%),
    linear-gradient(145deg, color-mix(in srgb, var(--cf-primary) 18%, var(--cf-bg-glass)), var(--cf-bg-glass-soft));
  color: var(--cf-primary);
  cursor: pointer;
  font-weight: 900;
  box-shadow:
    inset 0 var(--cf-hairline) 0 var(--cf-surface-highlight),
    inset 0 calc(var(--cf-radius-sm) * -0.5) calc(var(--cf-radius-lg) * 0.9) color-mix(in srgb, var(--cf-text-primary) 5%, transparent),
    var(--cf-shadow-soft);
  transition: transform var(--cf-motion-duration-standard) var(--cf-motion-ease), box-shadow var(--cf-motion-duration-standard) var(--cf-motion-ease), filter var(--cf-motion-duration-standard) var(--cf-motion-ease);

  .n-icon {
    font-size: 1.18rem;
  }

  &:hover {
    transform: var(--cf-card-transform);
    box-shadow:
      inset 0 var(--cf-hairline) 0 var(--cf-surface-highlight),
      var(--cf-shadow-card-hover);
  }

  &.locked {
    opacity: 0.58;
    filter: grayscale(0.72);
    color: var(--cf-text-muted);
    background:
      radial-gradient(circle at 34% 22%, var(--cf-surface-highlight), transparent 36%),
      color-mix(in srgb, var(--cf-bg-glass-soft) 82%, transparent);
  }

  &.empty {
    opacity: 0.46;
    color: var(--cf-text-muted);
  }
}

.checkin-list {
  display: flex;
  flex-direction: column;
  gap: var(--cf-radius-sm);

  button {
    border: 0;
    border-radius: var(--cf-radius-md);
    background:
      linear-gradient(180deg, var(--cf-surface-highlight), transparent),
      color-mix(in srgb, var(--cf-bg-glass-soft) 86%, transparent);
    color: var(--cf-text-primary);
    cursor: pointer;
    display: flex;
    flex-direction: column;
    gap: calc(var(--cf-radius-sm) / 2);
    padding: var(--cf-radius-sm) var(--cf-radius-md);
    text-align: left;
    box-shadow: inset 0 var(--cf-hairline) 0 var(--cf-surface-highlight);
    transition: transform var(--cf-motion-duration-standard) var(--cf-motion-ease), box-shadow var(--cf-motion-duration-standard) var(--cf-motion-ease), background-color var(--cf-motion-duration-standard) var(--cf-motion-ease);

    &:hover {
      transform: var(--cf-card-transform);
      background: var(--cf-bg-readable);
      box-shadow: var(--cf-shadow-soft);
    }
  }

  span {
    color: var(--cf-text-muted);
    font-size: 0.75rem;
  }
}

.widget-empty {
  margin: 0;
  border: 0;
  border-radius: var(--cf-radius-md);
  background: var(--cf-bg-glass-soft);
  color: var(--cf-text-muted);
  padding: var(--cf-radius-lg);
  text-align: center;
}

.mini-metric {
  display: flex;
  align-items: center;
  gap: var(--cf-radius-md);
  color: var(--cf-text-secondary);

  .n-icon {
    width: calc(var(--cf-radius-xl) * 2.1);
    height: calc(var(--cf-radius-xl) * 2.1);
    border-radius: 50%;
    background: color-mix(in srgb, var(--cf-primary) 14%, transparent);
    color: var(--cf-primary);
    display: flex;
    align-items: center;
    justify-content: center;
  }

  div {
    display: flex;
    flex-direction: column;
    gap: calc(var(--cf-radius-sm) / 2);
    align-items: flex-start;
  }

  strong {
    color: var(--cf-text-primary);
    font-weight: 720;
  }
}

@media (prefers-reduced-motion: no-preference) {
  .profile-layout {
    animation: none;
  }
}

@media (prefers-reduced-motion: reduce) {
  .profile-layout,
  .cover-section::after {
    animation: none;
  }
}

@media (max-width: 56.25rem) {
  .content-section {
    grid-template-columns: 1fr;
  }

  .stats-row {
    width: 100%;
    flex-wrap: wrap;
    border-radius: var(--cf-radius-lg);
    gap: calc(var(--cf-radius-sm) / 2);
  }

  .stat {
    flex: 1 1 calc(50% - var(--cf-radius-sm));
    min-width: min(calc(var(--cf-content-padding) * 4), 100%);
    border-radius: var(--cf-radius-md);

    & + .stat::before {
      display: none;
    }
  }

  .cover-section .profile-main-info {
    left: var(--cf-radius-lg);
    right: var(--cf-radius-lg);
    flex-direction: column;
    align-items: flex-start;
  }
}

@media (max-width: 40rem) {
  .scroll-content {
    padding: var(--cf-radius-lg);
  }

  .cover-section {
    display: grid;
    min-height: 0;
    overflow: hidden;
    padding: clamp(calc(var(--cf-content-padding) * 4.1), 34vw, calc(var(--cf-content-padding) * 5.8)) var(--cf-radius-lg)
      var(--cf-radius-lg);
  }

  .cover-section .cover-edit-hit {
    block-size: clamp(calc(var(--cf-content-padding) * 3.4), 32vw, calc(var(--cf-content-padding) * 4.6));
  }

  .cover-section::after {
    inset: clamp(calc(var(--cf-content-padding) * 3.4), 32vw, calc(var(--cf-content-padding) * 4.6)) 0 0;
  }

  .cover-section .profile-main-info {
    position: relative;
    inset: auto;
    flex-direction: column;
    gap: var(--cf-radius-md);
  }

  .cover-section .avatar {
    inline-size: clamp(calc(var(--cf-content-padding) * 2), 22vw, calc(var(--cf-content-padding) * 2.65));
    block-size: clamp(calc(var(--cf-content-padding) * 2), 22vw, calc(var(--cf-content-padding) * 2.65));
    font-size: clamp(1.35rem, 8vw, 2.05rem);
    border-width: calc(var(--cf-hairline) * 3);
  }

  .cover-section .avatar-wrapper {
    margin-top: calc(var(--cf-content-padding) * -1.25);
  }

  .name-row h2 {
    font-size: clamp(1.55rem, 9vw, 2rem);
  }

  .bio-edit {
    max-height: calc(1.75em * 3);
    overflow: hidden;
  }

  .stats-row {
    width: 100%;
    margin-top: var(--cf-radius-md);
  }

  .stat {
    padding: calc(var(--cf-radius-sm) * 0.75);

    .stat-icon {
      font-size: 0.95rem;
    }
  }

  .edit-btn {
    flex: 1 1 100%;
    margin-left: 0;
  }

  .achievement-row {
    grid-template-columns: calc(var(--cf-radius-xl) * 2.15) minmax(0, 1fr);
    align-items: start;

    .achievement-status {
      grid-column: 2;
      justify-self: start;
      margin-top: calc(var(--cf-radius-sm) / 1.5);
    }
  }

  .edit-panel {
    grid-template-columns: 1fr;
  }

  .tabs {
    overflow-x: auto;

    .tab {
      flex-basis: max(var(--tab-slot-size), calc(var(--cf-content-padding) * 3));
    }
  }
}
</style>
