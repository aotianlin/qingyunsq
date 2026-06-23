<script setup lang="ts">
import { computed, nextTick, onMounted, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { NEmpty, NIcon, NInput, NModal, NSelect, NSpin, NTag, useMessage } from 'naive-ui';
import { acceptAnswer, getQaInfo } from '@/api/qa';
import { aiChat, aiModerate, aiRecommendTags, aiSummarize, getPostAiCard } from '@/api/ai';
import { deleteComment, createComment, getComments, toggleCommentReaction, updateComment } from '@/api/comments';
import { deletePost, getPostById, toggleReaction } from '@/api/posts';
import { createReport } from '@/api/report';
import { useAuthStore } from '@/stores/auth';
import { useWebSocket } from '@/composables/useWebSocket';
import MentionText from '@/components/MentionText.vue';
import BackToTopButton from '@/components/BackToTopButton.vue';
import type { CommentVO, PostVO } from '@/types/post';
import type { MockArticlePost, PostDetailAuthorSummary, PostDetailRecommendationItem } from '@/types/announce';
import { getPostDetailSidebar, readCachedHotArticle } from '@/api/announce';
import type { QaQuestionVO } from '@/types/qa';
import {
  ArrowBackOutline,
  BookmarkOutline,
  ChatbubblesOutline,
  CreateOutline,
  DocumentTextOutline,
  EyeOutline,
  HeartOutline,
  LinkOutline,
  MegaphoneOutline,
  EllipsisHorizontalOutline,
  PricetagOutline,
  SendOutline,
  ShareSocialOutline,
  ShieldCheckmarkOutline,
  SparklesOutline,
  ThumbsUpOutline,
  TrashOutline,
} from '@vicons/ionicons5';

const route = useRoute();
const router = useRouter();
const message = useMessage();
const authStore = useAuthStore();

const post = ref<PostVO | null>(null);
const qa = ref<QaQuestionVO | null>(null);
const comments = ref<CommentVO[]>([]);
const loading = ref(true);
const highlightedCommentId = ref<number | null>(null);
const commentText = ref('');
const submitting = ref(false);
const replyModalShow = ref(false);
const replyText = ref('');
const replyTarget = ref<{ rootId: number; targetId: number; nickname: string } | null>(null);
const replySubmitting = ref(false);
const acceptingId = ref<number | null>(null);
const reportModalShow = ref(false);
const reportTargetId = ref<number>(0);
const reportTargetType = ref<'POST' | 'COMMENT'>('POST');
const reportReason = ref('SPAM');
const reportDesc = ref('');
const reportSubmitting = ref(false);
const editingCommentId = ref<number | null>(null);
const editingCommentText = ref('');
const aiModalShow = ref(false);
const aiLoading = ref(false);
const aiInput = ref('');
const aiAnalyzedPostId = ref<number | null>(null);
const aiMessages = ref<{ role: 'assistant' | 'user'; content: string }[]>([]);
const sidebarAuthor = ref<PostDetailAuthorSummary | null>(null);
const sidebarRecommendations = ref<PostDetailRecommendationItem[]>([]);
const reportReasons = [
  { label: '垃圾广告', value: 'SPAM' },
  { label: '违规内容', value: 'ILLEGAL' },
  { label: '人身攻击', value: 'ABUSE' },
  { label: '色情低俗', value: 'PORN' },
  { label: '虚假信息', value: 'FAKE' },
  { label: '其他', value: 'OTHER' },
];

const currentUserId = computed(() => authStore.user?.id);
const isQaPost = computed(() => post.value?.type === 'QA');
const isMockArticle = computed(() => Boolean((post.value as MockArticlePost | null)?.isMockArticle));
const isPostAuthor = computed(() => !isMockArticle.value && currentUserId.value === post.value?.authorId);
const postTitleText = computed(() => post.value?.title?.trim() || '无标题帖子');
const postAuthorName = computed(() => sidebarAuthor.value?.nickname || post.value?.author?.nickname || '匿名用户');
const authorAvatarUrl = computed(() => sidebarAuthor.value?.avatarUrl || post.value?.author?.avatarUrl || '');
const authorInitial = computed(() => postAuthorName.value.charAt(0).toUpperCase() || '?');
const postTopicsAndTags = computed(() => {
  const topics = post.value?.topics || [];
  const tags = post.value?.tags || [];
  return [
    ...topics.map((value) => ({ key: `topic-${value}`, label: `#${value}` })),
    ...tags.map((value) => ({ key: `tag-${value}`, label: value })),
  ];
});
const postOutline = computed(() => {
  const content = post.value?.content || '';
  const lines = content
    .split(/\r?\n/)
    .map((line) => line.trim())
    .filter(Boolean);
  const headingLines = lines.filter((line) => {
    if (line.length > 42) return false;
    return /^(#{1,3}\s+|[一二三四五六七八九十\d]+[、.．]|（[一二三四五六七八九十\d]+）)/.test(line);
  });
  const source = headingLines.length > 0 ? headingLines : lines.slice(0, 3);
  return source.slice(0, 4).map((line, index) => ({
    id: `outline-${index}`,
    title: line.replace(/^#{1,3}\s+/, ''),
  }));
});
const fallbackAuthorSummary = computed<PostDetailAuthorSummary | null>(() => {
  if (!post.value) return null;
  return {
    authorId: post.value.authorId,
    nickname: post.value.author?.nickname || '匿名用户',
    avatarUrl: post.value.author?.avatarUrl,
    label: isMockArticle.value ? '官方内容' : '校园社区创作者',
    postCount: 1,
    followerCount: 0,
    likeCount: Math.max(post.value.likeCount || 0, 0),
  };
});
const displayAuthorSummary = computed(() => sidebarAuthor.value || fallbackAuthorSummary.value);

// 监听 WebSocket 评论变更事件，自动刷新评论列表
useWebSocket((event) => {
  if (event.type === 'COMMENT_CHANGE' && post.value) {
    // 收到评论变更通知，自动刷新评论列表
    refreshComments();
  }
});

async function refreshComments() {
  if (!post.value) return;
  try {
    comments.value = await getComments(post.value.id, undefined, 20, post.value?.type === 'QA');
    // 同步更新评论数
    const freshPost = await getPostById(post.value.id);
    if (freshPost) post.value.commentCount = freshPost.commentCount;
  } catch {
    // 静默失败，不影响用户体验
  }
}

function goUser(userId?: number | null) {
  if (!userId) return;
  router.push(`/users/${userId}`);
}

function openReport(targetType: 'POST' | 'COMMENT', targetId: number) {
  reportTargetType.value = targetType;
  reportTargetId.value = targetId;
  reportReason.value = 'SPAM';
  reportDesc.value = '';
  reportModalShow.value = true;
}

async function copyPostLink() {
  if (!post.value) return;
  const path = router.resolve(`/posts/${post.value.id}`).href;
  const href = `${window.location.origin}${path}`;
  try {
    await navigator.clipboard.writeText(href);
    message.success('帖子链接已复制');
  } catch {
    message.error('复制失败，请稍后重试');
  }
}

function openAiSummary() {
  if (!post.value) return;
  router.push({ path: '/ai', query: { mode: 'summary', postId: String(post.value.id) }, hash: '#ai-workspace' });
}

async function handleCollect() {
  if (!post.value || isMockArticle.value) return;
  try {
    const collected = await toggleReaction(post.value.id, 'COLLECT');
    post.value.collected = collected;
    message.success(collected ? '已收藏' : '已取消收藏');
  } catch {
    message.error('收藏失败');
  }
}

async function openAiAnalysis() {
  if (!post.value) return;
  aiModalShow.value = true;

  if (aiAnalyzedPostId.value === post.value.id && aiMessages.value.length > 0) {
    return;
  }

  aiAnalyzedPostId.value = post.value.id;
  aiMessages.value = [];
  aiInput.value = '';
  aiLoading.value = true;

  try {
    const [summary, moderate, tags] = await Promise.all([
      aiSummarize(buildPostAiMaterial()),
      aiModerate(buildPostAiMaterial()),
      aiRecommendTags(post.value.title || '无标题帖子', post.value.content),
    ]);
    aiMessages.value = [
      {
        role: 'assistant',
        content: [
          '我已阅读这篇帖子，先给出一版快速分析：',
          '',
          `1. 核心摘要：${summary.summary || '暂未提取到明确摘要。'}`,
          `2. 内容风险：${formatRiskText(moderate.riskLevel, moderate.riskReason)}`,
          `3. 推荐标签：${(tags.tags || []).length ? tags.tags.join('、') : '暂无明显标签'}`,
          '4. 互动建议：可以继续追问“这篇帖子适合怎么回复”“有哪些争议点”“帮我提炼成笔记”。',
        ].join('\n'),
      },
    ];
  } catch {
    aiMessages.value = [
      {
        role: 'assistant',
        content: 'AI 分析暂时失败，可以稍后重试，或直接在下方输入你想追问的问题。',
      },
    ];
    message.error('AI 分析失败');
  } finally {
    aiLoading.value = false;
  }
}

async function sendAiQuestion() {
  if (!post.value || !aiInput.value.trim() || aiLoading.value) return;

  const question = aiInput.value.trim();
  aiMessages.value.push({ role: 'user', content: question });
  aiInput.value = '';
  aiLoading.value = true;

  try {
    const history = aiMessages.value.map((item) => ({ role: item.role, content: item.content }));
    const res = await aiChat(history, buildPostAiContext());
    aiMessages.value.push({
      role: 'assistant',
      content: res.reply || 'AI 暂时没有返回内容，请换个问题再试。',
    });
  } catch {
    message.error('AI 回复失败');
    aiMessages.value.push({
      role: 'assistant',
      content: 'AI 回复失败，请稍后重试。',
    });
  } finally {
    aiLoading.value = false;
  }
}

async function submitReport() {
  reportSubmitting.value = true;
  try {
    await createReport({
      targetType: reportTargetType.value,
      targetId: reportTargetId.value,
      reason: reportReason.value,
      description: reportDesc.value || undefined,
    });
    message.success('举报已提交');
    reportModalShow.value = false;
  } catch {
    message.error('举报失败');
  } finally {
    reportSubmitting.value = false;
  }
}

async function loadPost() {
  loading.value = true;
  post.value = null;
  qa.value = null;
  comments.value = [];
  try {
    const id = Number(route.params.id);
    post.value = await getPostById(id);
  } catch {
    post.value = createFallbackArticlePost(String(route.params.id));
    comments.value = [];
    void loadPostDetailSidebar();
    return;
  } finally {
    loading.value = false;
  }

  try {
    comments.value = await getComments(post.value.id, undefined, 20, post.value.type === 'QA');
  } catch {
    comments.value = [];
    message.error('评论加载失败，请稍后重试');
  }

  // fire-and-forget：触发后台生成/刷新 AI 卡片，结果落库，列表页下次浏览自动取到
  void getPostAiCard(post.value.id).catch(() => {});

  if (post.value.type === 'QA') {
    try {
      qa.value = await getQaInfo(post.value.id);
    } catch {
      qa.value = null;
      message.error('问答信息加载失败，请稍后重试');
    }
  }

  void loadPostDetailSidebar();

  // 处理来自列表「热门评论」跳转的 hash，例如 /posts/12#comment-345
  void scrollToHashComment();
}

async function loadPostDetailSidebar() {
  sidebarAuthor.value = null;
  sidebarRecommendations.value = [];
  if (!post.value || isMockArticle.value) return;
  try {
    const detail = await getPostDetailSidebar({
      postId: post.value.id,
      authorId: post.value.authorId,
      limit: 2,
    });
    sidebarAuthor.value = detail.author;
    sidebarRecommendations.value = detail.recommendations || [];
  } catch {
    sidebarAuthor.value = null;
    sidebarRecommendations.value = [];
  }
}

function createFallbackArticlePost(rawId: string): PostVO & { isMockArticle?: true } {
  const numericId = Number(rawId);
  const cached = readCachedHotArticle(rawId);
  const title = cached?.title || '测试文章';
  const summary = cached?.summary || '这是一篇由前端兜底生成的测试文章，用于保证今日热门点击后的阅读流程保持完整。';

  return {
    id: Number.isFinite(numericId) ? numericId : Date.now(),
    authorId: 0,
    author: {
      id: 0,
      nickname: cached?.source || 'Campus 官方',
      avatarUrl: '',
      email: '',
    },
    scope: 'SQUARE',
    spaceId: null,
    type: 'NORMAL',
    title,
    content: [
      summary,
      '',
      '后端热点详情接口接入前，这里会展示一篇占位排版文章，帮助用户完成从榜单点击到详情阅读的闭环体验。',
      '',
      '接口上线后，真实文章会自动替换这份兜底内容，无需改变当前路由或交互入口。',
    ].join('\n'),
    topics: ['今日热门', '官方公告'],
    tags: ['测试文章'],
    viewCount: 0,
    likeCount: 0,
    commentCount: 0,
    isPinned: 0,
    isEssence: 0,
    status: 1,
    liked: false,
    collected: false,
    createdAt: cached?.publishedAt || new Date().toISOString(),
    updatedAt: cached?.publishedAt || new Date().toISOString(),
    isMockArticle: true,
  };
}

async function scrollToHashComment() {
  const hash = route.hash;
  if (!hash || hash === '#comments') {
    await nextTick();
    document.querySelector('.comments-card')?.scrollIntoView({ behavior: 'smooth', block: 'start' });
    return;
  }
  if (!hash.startsWith('#comment-')) return;
  const id = Number(hash.slice('#comment-'.length));
  if (!Number.isFinite(id) || id <= 0) return;
  // 等 DOM 渲染完成
  await nextTick();
  // 评论可能在第二页：未命中时，简单滚到评论区底部提示用户
  const target = document.getElementById(`comment-${id}`);
  if (target) {
    target.scrollIntoView({ behavior: 'smooth', block: 'center' });
    highlightedCommentId.value = id;
    window.setTimeout(() => {
      if (highlightedCommentId.value === id) highlightedCommentId.value = null;
    }, 2500);
  }
}

async function handleLike() {
  if (!post.value) return;
  try {
    const liked = await toggleReaction(post.value.id, 'LIKE');
    post.value.liked = liked;
    post.value.likeCount += liked ? 1 : -1;
  } catch {
    message.error('操作失败');
  }
}

async function handleDeletePost() {
  if (!post.value) return;
  try {
    await deletePost(post.value.id);
    message.success('已删除');
    router.replace('/square');
  } catch {
    message.error('删除失败');
  }
}

function handleReply(comment: CommentVO, rootComment: CommentVO) {
  const nickname = formatAuthorName(comment);
  replyTarget.value = {
    rootId: rootComment.id,
    targetId: comment.id,
    nickname,
  };
  replyText.value = `@${nickname} `;
  replyModalShow.value = true;
}

function cancelReply() {
  if (replySubmitting.value) return;
  replyModalShow.value = false;
  replyTarget.value = null;
  replyText.value = '';
}

function clearCommentEditor() {
  commentText.value = '';
}

async function submitComment() {
  if (!post.value) return;
  // 纯空白（空格/回车）时给出明确提示，但允许包含换行的有效内容
  if (!commentText.value.trim()) {
    message.warning('评论内容不能为空，请输入有效内容');
    return;
  }
  // 评论字数限制
  if (commentText.value.length > 2000) {
    message.warning(`评论内容过长（${commentText.value.length}/2000），请精简后再发布`);
    return;
  }
  submitting.value = true;
  try {
    await createComment({
      postId: post.value.id,
      content: commentText.value.trim(),
    });
    message.success('评论成功');
    commentText.value = '';
    comments.value = await getComments(post.value.id, undefined, 20, post.value?.type === 'QA');
    if (post.value) post.value.commentCount += 1;
  } catch (e) {
    message.error(e instanceof Error ? e.message : '评论失败');
  } finally {
    submitting.value = false;
  }
}

function normalizeReplyContent(value: string, nickname: string) {
  const content = value.trim();
  const mention = `@${nickname}`;
  return content.startsWith(mention) ? content : `${mention} ${content}`;
}

async function submitReply() {
  if (!post.value || !replyTarget.value || !replyText.value.trim()) return;
  replySubmitting.value = true;
  try {
    await createComment({
      postId: post.value.id,
      parentId: replyTarget.value.rootId,
      replyToId: replyTarget.value.targetId,
      content: normalizeReplyContent(replyText.value, replyTarget.value.nickname),
    });
    message.success('回复成功');
    replyModalShow.value = false;
    replyTarget.value = null;
    replyText.value = '';
    comments.value = await getComments(post.value.id, undefined, 20, post.value?.type === 'QA');
    if (post.value) post.value.commentCount += 1;
  } catch {
    message.error('回复失败');
  } finally {
    replySubmitting.value = false;
  }
}

async function handleCommentLike(comment: CommentVO) {
  try {
    const liked = await toggleCommentReaction(comment.id, 'LIKE');
    comment.likeCount = (comment.likeCount || 0) + (liked ? 1 : -1);
  } catch {
    message.error('操作失败');
  }
}

function startEditComment(comment: CommentVO) {
  editingCommentId.value = comment.id;
  editingCommentText.value = comment.content;
}

function cancelEditComment() {
  editingCommentId.value = null;
  editingCommentText.value = '';
}

async function submitEditComment(commentId: number) {
  if (!editingCommentText.value.trim()) return;
  try {
    await updateComment(commentId, editingCommentText.value);
    message.success('评论已更新');
    editingCommentId.value = null;
    editingCommentText.value = '';
    if (post.value) {
      comments.value = await getComments(post.value.id, undefined, 20, post.value?.type === 'QA');
    }
  } catch (e) {
    message.error(e instanceof Error ? e.message : '编辑失败');
  }
}

async function handleDeleteComment(commentId: number) {
  try {
    await deleteComment(commentId);
    message.success('已删除');
    if (post.value) {
      comments.value = await getComments(post.value.id, undefined, 20, post.value?.type === 'QA');
      post.value.commentCount -= 1;
    }
  } catch {
    message.error('删除失败');
  }
}

async function handleAccept(commentId: number) {
  if (!post.value) return;
  acceptingId.value = commentId;
  try {
    qa.value = await acceptAnswer(post.value.id, commentId);
    message.success('已采纳该回答');
  } catch (error) {
    message.error(error instanceof Error ? error.message : '采纳失败');
  } finally {
    acceptingId.value = null;
  }
}

function formatTime(value?: string) {
  if (!value) return '刚刚';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return '刚刚';
  return date.toLocaleString('zh-CN', { month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' });
}

function formatAuthorName(comment: CommentVO) {
  return comment.author?.nickname || '匿名用户';
}

function goRecommendation(item: PostDetailRecommendationItem) {
  if (item.postId) {
    router.push(`/posts/${item.postId}`);
    return;
  }
  if (item.targetUrl) {
    router.push(item.targetUrl);
  }
}

function buildPostAiMaterial() {
  if (!post.value) return '';
  return [
    `标题：${post.value.title || '无标题帖子'}`,
    `类型：${post.value.type === 'QA' ? '问答帖' : '普通帖子'}`,
    `作者：${post.value.author?.nickname || '匿名用户'}`,
    `话题：${post.value.topics?.length ? post.value.topics.join('、') : '无'}`,
    `正文：${post.value.content}`,
  ].join('\n');
}

function buildPostAiContext() {
  return [
    '你是 CampusForum 的帖子分析助手。请只围绕用户正在查看的帖子进行分析，回答要简洁、清晰、可执行。',
    '如果用户要求回复建议，请给出适合校园社区语境的自然表达。',
    '',
    buildPostAiMaterial(),
  ].join('\n');
}

function formatRiskText(level?: number, reason?: string) {
  if (level === 2) return reason ? `高风险，${reason}` : '高风险，建议谨慎处理。';
  if (level === 1) return reason ? `需留意，${reason}` : '需留意，建议进一步核对。';
  return '未发现明显风险。';
}

onMounted(loadPost);
</script>

<template>
  <div class="detail-page">
    <div class="detail-ambient ambient-one" aria-hidden="true" />
    <div class="detail-ambient ambient-two" aria-hidden="true" />

    <nav class="detail-navbar">
      <button class="nav-back" type="button" @click="router.back()">
        <span>
          <n-icon size="18"><ArrowBackOutline /></n-icon>
        </span>
        返回列表
      </button>

      <div class="nav-actions">
        <button type="button" title="复制链接" @click="copyPostLink">
          <n-icon size="18"><ShareSocialOutline /></n-icon>
        </button>
        <button
          type="button"
          title="收藏"
          :class="{ active: post?.collected }"
          :disabled="!post || isMockArticle"
          @click="handleCollect"
        >
          <n-icon size="18"><BookmarkOutline /></n-icon>
        </button>
        <button v-if="post" type="button" title="举报" @click="openReport('POST', post.id)">
          <n-icon size="18"><EllipsisHorizontalOutline /></n-icon>
        </button>
      </div>
    </nav>

    <div v-if="loading" class="loading-wrap">
      <n-spin size="large" />
    </div>

    <template v-else-if="post">
      <main class="reading-shell">
        <section class="main-column">
          <article class="post-card">
            <div class="post-aura" aria-hidden="true" />

            <header class="post-hero">
              <div class="post-kind-row">
                <span v-if="post.isEssence === 1" class="status-pill warm">精华</span>
                <span v-if="isQaPost" class="status-pill info">问答</span>
                <span v-if="isMockArticle" class="status-pill">测试文章</span>
              </div>

              <h1>{{ postTitleText }}</h1>

              <button
                class="author-link hero-author"
                type="button"
                @click="goUser(post.authorId)"
              >
                <img v-if="authorAvatarUrl" :src="authorAvatarUrl" alt="作者头像" />
                <span v-else class="avatar-badge">{{ authorInitial }}</span>
                <div>
                  <div class="author-name">
                    {{ postAuthorName }}
                  </div>
                  <div class="post-meta">
                    <span>{{ formatTime(post.createdAt) }}</span>
                    <span>
                      <n-icon size="13"><EyeOutline /></n-icon>
                      {{ post.viewCount }} 阅读
                    </span>
                  </div>
                </div>
              </button>
            </header>

            <div class="post-content reading-prose">
              <MentionText :text="post.content" />
            </div>

            <div v-if="postTopicsAndTags.length" class="topic-row">
              <span v-for="item in postTopicsAndTags" :key="item.key" class="topic-tag">
                <n-icon size="14"><PricetagOutline /></n-icon>
                {{ item.label }}
              </span>
            </div>

            <div v-if="isQaPost && qa" class="qa-strip">
              <div>
                <strong>悬赏 {{ qa.bountyPoints }} 积分</strong>
                <span>{{ qa.isSolved ? '已解决' : '等待高质量回答' }}</span>
              </div>
              <n-tag :type="qa.isSolved ? 'success' : 'info'" round>
                {{ qa.isSolved ? '已采纳' : '待解决' }}
              </n-tag>
            </div>

            <div class="post-actions">
              <button
                class="action-btn primary-action"
                :class="{ active: post.liked }"
                @click="handleLike"
              >
                <n-icon size="18"><ThumbsUpOutline /></n-icon>
                {{ post.liked ? '已赞' : '赞同' }} {{ post.likeCount }}
              </button>
              <button class="action-btn" @click="scrollToHashComment">
                <n-icon size="18"><ChatbubblesOutline /></n-icon>
                评论 {{ post.commentCount }}
              </button>
              <button class="action-btn" :class="{ active: post.collected }" :disabled="isMockArticle" @click="handleCollect">
                <n-icon size="18"><HeartOutline /></n-icon>
                {{ post.collected ? '已收藏' : '收藏' }}
              </button>
              <button class="action-btn" @click="openReport('POST', post.id)">
                <n-icon size="18"><MegaphoneOutline /></n-icon>
                举报
              </button>
              <button class="action-btn ai-action" @click="openAiSummary">
                <n-icon size="18"><DocumentTextOutline /></n-icon>
                AI 摘要
              </button>
              <button class="action-btn ai-action" @click="openAiAnalysis">
                <n-icon size="18"><SparklesOutline /></n-icon>
                AI 分析
              </button>
              <button class="icon-btn" type="button" title="引用发帖" @click="router.push('/posts/new?quote=' + post.id)">
                <n-icon size="17"><LinkOutline /></n-icon>
              </button>
              <button
                v-if="isPostAuthor"
                class="icon-btn"
                type="button"
                title="编辑"
                @click="router.push(`/posts/${post.id}/edit`)"
              >
                <n-icon size="17"><CreateOutline /></n-icon>
              </button>
              <button
                v-if="isPostAuthor"
                class="icon-btn danger"
                type="button"
                title="删除"
                @click="handleDeletePost"
              >
                <n-icon size="17"><TrashOutline /></n-icon>
              </button>
            </div>
          </article>
        </section>

        <aside class="side-column">
          <div class="side-panel author-panel">
            <h3>关于作者</h3>
            <button class="side-author" type="button" @click="goUser(displayAuthorSummary?.authorId)">
              <img v-if="displayAuthorSummary?.avatarUrl" :src="displayAuthorSummary.avatarUrl" alt="作者头像" />
              <span v-else class="avatar-badge">{{ authorInitial }}</span>
              <span>
                <strong>{{ displayAuthorSummary?.nickname || postAuthorName }}</strong>
                <small>{{ displayAuthorSummary?.label || displayAuthorSummary?.bio || '校园社区创作者' }}</small>
              </span>
            </button>
            <div class="author-stats">
              <div><strong>{{ displayAuthorSummary?.postCount || 1 }}</strong><span>文章</span></div>
              <div><strong>{{ displayAuthorSummary?.followerCount || 0 }}</strong><span>关注者</span></div>
              <div><strong>{{ displayAuthorSummary?.likeCount || post.likeCount }}</strong><span>获赞</span></div>
            </div>
            <button class="side-ghost-btn" type="button" @click="goUser(displayAuthorSummary?.authorId)">
              进入个人主页
            </button>
          </div>

          <div class="side-panel">
            <h3>
              文章大纲
              <span>导航</span>
            </h3>
            <ul v-if="postOutline.length" class="outline-list">
              <li v-for="(item, index) in postOutline" :key="item.id" :class="{ active: index === 0 }">
                <span />
                {{ item.title }}
              </li>
            </ul>
            <p v-else class="side-empty">正文较短，暂无大纲。</p>
          </div>

          <div class="side-panel">
            <h3>更多好文</h3>
            <div v-if="sidebarRecommendations.length" class="recommend-list">
              <button
                v-for="item in sidebarRecommendations"
                :key="item.id"
                type="button"
                @click="goRecommendation(item)"
              >
                <strong>{{ item.title }}</strong>
                <span>{{ formatTime(item.publishedAt) }} · {{ item.viewCount }} 阅读</span>
              </button>
            </div>
            <p v-else class="side-empty">相关推荐接口接入后会展示同作者或相关帖子。</p>
          </div>

          <div v-if="isQaPost && qa" class="side-panel accent-panel">
            <h3>问答状态</h3>
            <p>该问题适合继续补充实战经验、案例或者参考资料。</p>
            <n-tag :type="qa.isSolved ? 'success' : 'info'" round>
              {{ qa.isSolved ? '问题已解决' : '仍在等待答案' }}
            </n-tag>
          </div>
        </aside>
      </main>

      <section class="comments-card">
        <div class="section-title-row">
          <h2>
            共 {{ post.commentCount }} 条评论
            <span>最新互动</span>
          </h2>
        </div>

        <div class="comment-editor">
          <div class="comment-avatar self-avatar">
            {{ authStore.user?.nickname?.charAt(0)?.toUpperCase() || '我' }}
          </div>
          <div class="editor-shell">
            <n-input
              v-model:value="commentText"
              type="textarea"
              class="cf-textarea"
              placeholder="写下你的想法，或者补充你的观点（最多 2000 字）"
              :autosize="{ minRows: 4, maxRows: 10 }"
              maxlength="2000"
            />
            <div class="editor-actions">
              <span class="comment-char-count" :class="{ over: commentText.length > 2000 }">
                {{ commentText.length }}/2000
              </span>
              <button class="cf-secondary-btn" @click="clearCommentEditor">清空</button>
              <button class="cf-primary-btn" :disabled="submitting || commentText.length > 2000" @click="submitComment">
                <n-icon size="15"><SendOutline /></n-icon>
                发布评论
              </button>
            </div>
          </div>
        </div>

        <div v-if="comments.length === 0" class="empty-comment">
          <n-empty description="还没有评论，来抢首评吧" />
        </div>

        <div v-else class="comment-list">
          <article
            v-for="comment in comments"
            :id="`comment-${comment.id}`"
            :key="comment.id"
            class="comment-item"
            :class="{ highlighted: highlightedCommentId === comment.id }"
          >
            <button class="comment-avatar" type="button" @click="goUser(comment.authorId)">
              {{ formatAuthorName(comment).charAt(0).toUpperCase() }}
            </button>
            <div class="comment-body">
              <div class="comment-header">
                <div>
                  <strong>{{ formatAuthorName(comment) }}</strong>
                  <span v-if="comment.authorId === post.authorId" class="author-badge">作者</span>
                  <span>{{ formatTime(comment.createdAt) }}</span>
                </div>
                <div class="comment-actions">
                  <button class="text-link" @click="handleReply(comment, comment)">回复</button>
                  <button class="text-link" @click="handleCommentLike(comment)">点赞 {{ comment.likeCount || 0 }}</button>
                  <button class="text-link danger" @click="openReport('COMMENT', comment.id)">举报</button>
                  <button v-if="currentUserId === comment.authorId" class="text-link" @click="startEditComment(comment)">编辑</button>
                  <button v-if="currentUserId === comment.authorId" class="text-link danger" @click="handleDeleteComment(comment.id)">删除</button>
                  <button
                    v-if="isPostAuthor && isQaPost && !qa?.isSolved"
                    class="text-link strong"
                    :disabled="acceptingId === comment.id"
                    @click="handleAccept(comment.id)"
                  >采纳</button>
                </div>
              </div>

              <div class="comment-content">
                <template v-if="editingCommentId === comment.id">
                  <n-input
                    v-model:value="editingCommentText"
                    type="textarea"
                    :autosize="{ minRows: 2, maxRows: 6 }"
                  />
                  <div class="editor-actions compact-actions">
                    <button class="cf-secondary-btn" @click="cancelEditComment">取消</button>
                    <button class="cf-primary-btn" @click="submitEditComment(comment.id)">保存</button>
                  </div>
                </template>
                <MentionText v-else :text="comment.content" />
              </div>

              <section v-if="comment.replies?.length" class="reply-thread">
                <div class="reply-thread-head">
                  <span>{{ comment.replies.length }} 条回复</span>
                </div>
                <article v-for="reply in comment.replies" :key="reply.id" class="reply-item">
                  <button class="comment-avatar reply-avatar" type="button" @click="goUser(reply.authorId)">
                    {{ formatAuthorName(reply).charAt(0).toUpperCase() }}
                  </button>
                  <div class="reply-body">
                    <div>
                      <strong>{{ formatAuthorName(reply) }}</strong>
                      <span>{{ formatTime(reply.createdAt) }}</span>
                    </div>
                    <MentionText :text="reply.content" />
                    <div class="reply-actions">
                      <button class="text-link" @click="handleReply(reply, comment)">回复</button>
                      <button class="text-link" @click="handleCommentLike(reply)">点赞 {{ reply.likeCount || 0 }}</button>
                      <button class="text-link danger" @click="openReport('COMMENT', reply.id)">举报</button>
                      <button
                        v-if="currentUserId === reply.authorId"
                        class="text-link danger"
                        @click="handleDeleteComment(reply.id)"
                      >删除</button>
                    </div>
                  </div>
                </article>
              </section>
            </div>
          </article>
        </div>
      </section>
    </template>

    <div v-else class="loading-wrap">
      <n-empty description="帖子不存在或已被删除" />
    </div>

    <n-modal
      v-model:show="replyModalShow"
      preset="card"
      class="reply-modal"
      :title="replyTarget ? `回复 @${replyTarget.nickname}` : '回复评论'"
      style="width: min(560px, calc(100vw - 32px));"
    >
      <div class="reply-form">
        <n-input
          v-model:value="replyText"
          type="textarea"
          class="cf-textarea"
          placeholder="写下你的回复"
          :autosize="{ minRows: 4, maxRows: 8 }"
        />
      </div>
      <template #action>
        <div class="modal-actions">
          <button class="cf-secondary-btn" :disabled="replySubmitting" @click="cancelReply">取消</button>
          <button
            class="cf-primary-btn"
            :disabled="replySubmitting || !replyText.trim()"
            @click="submitReply"
          >
            发布回复
          </button>
        </div>
      </template>
    </n-modal>

    <n-modal v-model:show="reportModalShow" preset="card" title="举报内容" style="width: 520px;">
      <div class="report-form">
        <div class="form-block">
          <label>举报对象</label>
          <div>{{ reportTargetType }} #{{ reportTargetId }}</div>
        </div>
        <div class="form-block">
          <label>举报原因</label>
          <n-select v-model:value="reportReason" :options="reportReasons" />
        </div>
        <div class="form-block">
          <label>补充说明</label>
          <n-input v-model:value="reportDesc" type="textarea" :autosize="{ minRows: 4, maxRows: 8 }" placeholder="选填" />
        </div>
      </div>
      <template #action>
        <div class="modal-actions">
          <button class="cf-secondary-btn" @click="reportModalShow = false">取消</button>
          <button class="cf-primary-btn" :disabled="reportSubmitting" @click="submitReport">提交举报</button>
        </div>
      </template>
    </n-modal>

    <n-modal
      v-model:show="aiModalShow"
      preset="card"
      title="AI 帖子分析"
      class="ai-analysis-modal"
      style="width: min(760px, calc(100vw - 32px));"
    >
      <div class="ai-dialog">
        <div class="ai-dialog-head">
          <div class="ai-icon">
            AI
          </div>
          <div>
            <strong>{{ post?.title || '无标题帖子' }}</strong>
            <span>基于当前帖子正文、话题与基础互动数据进行分析</span>
          </div>
        </div>

        <div class="ai-message-list">
          <div
            v-for="(item, index) in aiMessages"
            :key="`${item.role}-${index}`"
            class="ai-message"
            :class="item.role"
          >
            <div class="ai-message-role">
              {{ item.role === 'user' ? '我' : 'AI' }}
            </div>
            <pre>{{ item.content }}</pre>
          </div>

          <div
            v-if="aiLoading"
            class="ai-message assistant loading"
          >
            <div class="ai-message-role">AI</div>
            <div class="ai-typing">
              <span />
              <span />
              <span />
            </div>
          </div>
        </div>

        <div class="ai-input-row">
          <n-input
            v-model:value="aiInput"
            type="textarea"
            class="cf-textarea"
            placeholder="继续追问：这篇帖子适合怎么回复？有哪些关键观点？"
            :autosize="{ minRows: 2, maxRows: 5 }"
            @keydown.enter.exact.prevent="sendAiQuestion"
          />
          <button
            class="cf-primary-btn ai-send-btn"
            :disabled="aiLoading || !aiInput.trim()"
            @click="sendAiQuestion"
          >
            <n-icon size="16"><SendOutline /></n-icon>
            发送
          </button>
        </div>
      </div>
    </n-modal>

    <BackToTopButton />
  </div>
</template>

<style scoped lang="scss">
.detail-page {
  position: relative;
  min-height: 100vh;
  overflow: hidden;
  padding: 0 0 56px;
  background: #f8fbfa;
  color: var(--cf-text-primary);
}

.detail-ambient {
  position: fixed;
  z-index: 0;
  pointer-events: none;
  border-radius: 50%;
  filter: blur(140px);
}

.ambient-one {
  top: -10%;
  left: -10%;
  width: 50%;
  height: 50%;
  background: rgba(0, 196, 167, 0.06);
}

.ambient-two {
  top: 30%;
  right: -10%;
  width: 40%;
  height: 60%;
  background: rgba(59, 130, 246, 0.04);
  filter: blur(150px);
}

.detail-navbar {
  position: sticky;
  top: 0;
  z-index: 20;
  max-width: 1440px;
  height: 64px;
  margin: 0 auto;
  padding: 0 32px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.5);
  background: color-mix(in srgb, var(--cf-bg-elevated) 76%, transparent);
  backdrop-filter: blur(18px);
  display: flex;
  align-items: center;
  justify-content: space-between;
  box-shadow: 0 2px 10px rgba(15, 23, 42, 0.01);
}

.nav-back,
.nav-actions button,
.icon-btn,
.action-btn,
.side-ghost-btn,
.text-link,
.side-author,
.recommend-list button,
.comment-avatar {
  font: inherit;
}

.nav-back,
.nav-actions button,
.icon-btn {
  border: none;
  background: transparent;
  color: var(--cf-text-muted);
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  transition: background 0.2s ease, color 0.2s ease, transform 0.2s ease;
}

.nav-back {
  gap: 8px;
  font-size: 14px;
  font-weight: 700;
}

.nav-back span,
.nav-actions button,
.icon-btn {
  width: 36px;
  height: 36px;
  border-radius: 999px;
}

.nav-back:hover,
.nav-actions button:hover,
.nav-actions button.active,
.icon-btn:hover {
  color: var(--cf-primary);
}

.nav-back:hover span,
.nav-actions button:hover,
.nav-actions button.active,
.icon-btn:hover {
  background: var(--cf-primary-soft);
}

.nav-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.loading-wrap {
  position: relative;
  z-index: 2;
  min-height: 280px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.reading-shell {
  position: relative;
  z-index: 1;
  max-width: 1200px;
  margin: 0 auto;
  padding: 48px 24px 0;
  display: grid;
  grid-template-columns: minmax(0, 720px) 320px;
  gap: 48px;
  align-items: start;
  justify-content: center;
}

.main-column,
.side-column {
  min-width: 0;
}

.post-card,
.comments-card,
.side-panel {
  border: 1px solid rgba(255, 255, 255, 0.6);
  background: color-mix(in srgb, var(--cf-bg-readable) 88%, transparent);
  backdrop-filter: blur(20px);
  box-shadow: 0 8px 40px rgba(15, 23, 42, 0.025);
}

.post-card {
  position: relative;
  overflow: hidden;
  border-radius: 32px;
  padding: clamp(32px, 4vw, 56px);
}

.post-aura {
  position: absolute;
  inset: 0 0 auto;
  height: 200px;
  background: linear-gradient(180deg, color-mix(in srgb, var(--cf-primary) 10%, transparent), transparent);
  pointer-events: none;
}

.post-hero,
.post-content,
.topic-row,
.qa-strip,
.post-actions {
  position: relative;
  z-index: 1;
}

.post-kind-row {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 18px;
}

.status-pill {
  display: inline-flex;
  align-items: center;
  min-height: 26px;
  padding: 0 11px;
  border-radius: 999px;
  border: 1px solid var(--cf-border);
  background: var(--cf-bg-soft);
  color: var(--cf-text-muted);
  font-size: 12px;
  font-weight: 800;
}

.status-pill.warm {
  background: color-mix(in srgb, var(--cf-warning) 14%, var(--cf-bg-elevated));
  color: #b7791f;
}

.status-pill.info {
  background: var(--cf-primary-soft);
  color: var(--cf-primary);
}

.post-hero h1 {
  max-width: 14ch;
  margin: 0 0 32px;
  color: var(--cf-text-primary);
  font-family: var(--cf-font-heading);
  font-size: clamp(2rem, 4vw, 2.5rem);
  line-height: 1.2;
  letter-spacing: 0;
}

.author-link,
.side-author {
  border: none;
  background: transparent;
  color: inherit;
  cursor: pointer;
  text-align: left;
}

.hero-author {
  width: 100%;
  display: flex;
  align-items: center;
  justify-content: flex-start;
  gap: 16px;
  margin-bottom: 48px;
  padding-left: 16px;
  padding-top: 6px;
  padding-bottom: 6px;
  border-left: 4px solid var(--cf-primary);
}

.hero-author > img,
.side-author img,
.avatar-badge,
.comment-avatar {
  width: 48px;
  height: 48px;
  border-radius: 50%;
  object-fit: cover;
  flex: 0 0 auto;
  border: 2px solid rgba(255, 255, 255, 0.86);
  box-shadow: 0 3px 12px rgba(15, 23, 42, 0.06);
}

.hero-author > img,
.hero-author > .avatar-badge {
  width: 44px;
  height: 44px;
}

.hero-author > .avatar-badge {
  border: 1px solid #ccfbf1;
  background: #e6fcf8;
  color: #00c4a7;
  box-shadow: 0 1px 3px rgba(15, 23, 42, 0.08);
  backdrop-filter: blur(4px);
  font-size: 18px;
  font-weight: 800;
}

.hero-author > div {
  min-width: 0;
  display: flex;
  flex-direction: column;
  justify-content: center;
}

.avatar-badge,
.comment-avatar {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  background: var(--cf-primary-soft);
  color: var(--cf-primary);
  font-weight: 900;
}

.author-name {
  color: var(--cf-text-primary);
  font-weight: 800;
  font-size: 15px;
  line-height: 1.35;
}

.post-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  margin-top: 2px;
  color: var(--cf-text-muted);
  font-size: 13px;
}

.post-meta span {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.post-meta span:first-child {
  color: #64748b;
  font-weight: 600;
}

.reading-prose {
  margin-top: 0;
  color: var(--cf-text-secondary);
  font-size: 17px;
  line-height: 1.9;
}

.reading-prose :deep(p),
.reading-prose :deep(div) {
  white-space: pre-wrap;
}

.topic-row {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 28px;
}

.topic-tag {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 7px 12px;
  border-radius: 999px;
  background: var(--cf-bg-soft);
  color: var(--cf-primary);
  font-size: 13px;
  font-weight: 800;
}

.qa-strip {
  margin-top: 24px;
  padding: 16px 18px;
  border-radius: 18px;
  border: 1px solid color-mix(in srgb, var(--cf-primary) 22%, var(--cf-border));
  background: linear-gradient(180deg, var(--cf-primary-soft), var(--cf-bg-glass));
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.qa-strip strong,
.qa-strip span {
  display: block;
}

.qa-strip span {
  margin-top: 4px;
  color: var(--cf-text-muted);
  font-size: 13px;
}

.post-actions {
  margin-top: 56px;
  padding-top: 28px;
  border-top: 1px solid var(--cf-border);
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 12px;
}

.action-btn {
  min-height: 44px;
  border: 1px solid transparent;
  border-radius: 999px;
  background: var(--cf-bg-soft);
  color: var(--cf-text-secondary);
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 0 18px;
  font-weight: 800;
  transition: background 0.2s ease, color 0.2s ease, transform 0.2s ease;
}

.action-btn:hover,
.action-btn.active,
.action-btn.ai-action {
  background: var(--cf-primary-soft);
  color: var(--cf-primary);
}

.action-btn.primary-action {
  background: var(--cf-primary-soft);
  color: var(--cf-primary);
}

.action-btn:hover {
  transform: translate3d(0, -1px, 0);
}

.icon-btn.danger {
  color: var(--cf-danger);
}

.side-column {
  position: sticky;
  top: 88px;
  display: flex;
  flex-direction: column;
  gap: 24px;
}

.side-panel {
  border-radius: 24px;
  padding: 24px;
}

.side-panel h3 {
  margin: 0 0 18px;
  color: var(--cf-text-muted);
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  font-size: 13px;
  font-weight: 900;
  letter-spacing: 0.04em;
  text-transform: uppercase;
}

.side-panel h3 span {
  border-radius: 999px;
  background: var(--cf-bg-soft);
  padding: 3px 8px;
  color: var(--cf-text-muted);
  font-size: 10px;
  letter-spacing: 0;
}

.side-author {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 0;
}

.side-author strong,
.side-author small {
  display: block;
}

.side-author strong {
  color: var(--cf-text-primary);
  font-weight: 900;
}

.side-author small {
  margin-top: 4px;
  color: var(--cf-text-muted);
  font-size: 12px;
}

.author-stats {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 10px;
  margin: 20px 0;
}

.author-stats div {
  text-align: center;
}

.author-stats strong,
.author-stats span {
  display: block;
}

.author-stats strong {
  color: var(--cf-text-primary);
  font-weight: 900;
}

.author-stats span {
  margin-top: 2px;
  color: var(--cf-text-muted);
  font-size: 11px;
}

.side-ghost-btn {
  width: 100%;
  min-height: 38px;
  border: 1px solid var(--cf-border);
  border-radius: 12px;
  background: var(--cf-bg-soft);
  color: var(--cf-text-secondary);
  cursor: pointer;
  font-weight: 800;
}

.outline-list {
  list-style: none;
  display: flex;
  flex-direction: column;
  gap: 13px;
  margin: 0;
  padding: 0;
}

.outline-list li {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  color: var(--cf-text-muted);
  cursor: default;
  font-size: 14px;
  line-height: 1.45;
}

.outline-list li span {
  width: 6px;
  height: 6px;
  margin-top: 7px;
  border-radius: 999px;
  background: var(--cf-border-strong);
  flex: 0 0 auto;
}

.outline-list li.active {
  color: var(--cf-primary);
  font-weight: 800;
}

.recommend-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.recommend-list button {
  border: none;
  background: transparent;
  color: inherit;
  cursor: pointer;
  padding: 0;
  text-align: left;
}

.recommend-list strong,
.recommend-list span {
  display: block;
}

.recommend-list strong {
  color: var(--cf-text-primary);
  font-size: 14px;
  line-height: 1.45;
  transition: color 0.2s ease;
}

.recommend-list button:hover strong {
  color: var(--cf-primary);
}

.recommend-list span,
.side-empty {
  color: var(--cf-text-muted);
  font-size: 12px;
  line-height: 1.6;
}

.side-empty {
  margin: 0;
}

.accent-panel {
  background: linear-gradient(180deg, var(--cf-primary-soft), var(--cf-bg-glass));
  border-color: color-mix(in srgb, var(--cf-primary) 22%, var(--cf-border));
}

.accent-panel p {
  margin: 0 0 14px;
  color: var(--cf-text-secondary);
  line-height: 1.7;
}

.comments-card {
  position: relative;
  z-index: 1;
  max-width: 720px;
  margin: 56px auto 0;
  padding: 0 24px;
  border: none;
  background: transparent;
  box-shadow: none;
  backdrop-filter: none;
}

.section-title-row h2 {
  margin: 0 0 28px;
  color: var(--cf-text-primary);
  display: flex;
  align-items: center;
  gap: 10px;
  font-family: var(--cf-font-heading);
  font-size: 22px;
}

.section-title-row h2 span {
  border-radius: 999px;
  background: var(--cf-bg-soft);
  color: var(--cf-text-muted);
  padding: 3px 9px;
  font-size: 13px;
  font-weight: 500;
}

.comment-editor {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  margin-bottom: 36px;
  border: 1px solid rgba(255, 255, 255, 0.9);
  border-radius: 24px;
  background: color-mix(in srgb, var(--cf-bg-readable) 92%, transparent);
  backdrop-filter: blur(22px);
  box-shadow: 0 8px 30px rgba(15, 23, 42, 0.04);
  padding: 12px;
  transition: border 0.2s ease, box-shadow 0.2s ease;
}

.comment-editor:focus-within {
  border-color: color-mix(in srgb, var(--cf-primary) 28%, transparent);
  box-shadow: 0 8px 40px rgba(20, 184, 166, 0.1);
}

.self-avatar {
  margin-top: 4px;
  width: 42px;
  height: 42px;
  font-size: 13px;
}

.editor-shell {
  min-width: 0;
  flex: 1;
}

.editor-actions,
.modal-actions,
.reply-actions,
.comment-actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: flex-end;
  gap: 10px;
}

.editor-actions {
  padding: 10px 4px 0;
}

.compact-actions {
  margin-top: 10px;
}

.comment-char-count {
  margin-right: auto;
  color: var(--cf-text-muted);
  font-size: 13px;
}

.comment-char-count.over {
  color: #ef4444;
  font-weight: 700;
}

.empty-comment {
  min-height: 140px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.comment-list {
  display: flex;
  flex-direction: column;
  gap: 24px;
}

.comment-item {
  display: grid;
  grid-template-columns: 42px minmax(0, 1fr);
  gap: 16px;
  border-radius: 16px;
  transition: background 0.4s ease, box-shadow 0.4s ease;
}

.comment-item.highlighted {
  background: color-mix(in srgb, #f59e0b 12%, transparent);
  box-shadow: 0 0 0 2px color-mix(in srgb, #f59e0b 30%, transparent);
}

.comment-item > .comment-avatar {
  width: 42px;
  height: 42px;
  border: none;
  cursor: pointer;
}

.comment-body {
  min-width: 0;
  padding-bottom: 24px;
  border-bottom: 1px solid var(--cf-border);
}

.comment-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.comment-header strong,
.reply-body strong {
  color: var(--cf-text-primary);
  font-size: 14px;
}

.comment-header span,
.reply-body span {
  margin-left: 8px;
  color: var(--cf-text-muted);
  font-size: 12px;
}

.author-badge {
  display: inline-flex;
  align-items: center;
  min-height: 18px;
  padding: 0 6px;
  border-radius: 999px;
  background: var(--cf-primary-soft);
  color: var(--cf-primary) !important;
  font-size: 11px !important;
  font-weight: 800;
}

.comment-content,
.reply-body {
  color: var(--cf-text-secondary);
  line-height: 1.8;
}

.comment-content {
  margin-top: 10px;
}

.reply-thread {
  margin-top: 16px;
  padding: 14px;
  border: 1px solid var(--cf-border);
  border-radius: 16px;
  background: color-mix(in srgb, var(--cf-bg-soft) 72%, transparent);
}

.reply-thread-head {
  margin-bottom: 12px;
  color: var(--cf-text-muted);
  font-size: 13px;
  font-weight: 800;
}

.reply-item {
  display: grid;
  grid-template-columns: 30px minmax(0, 1fr);
  gap: 10px;
  padding: 12px 0;
  border-top: 1px solid var(--cf-border);
}

.reply-item:first-of-type {
  border-top: none;
  padding-top: 0;
}

.reply-item:last-child {
  padding-bottom: 0;
}

.reply-avatar {
  width: 30px !important;
  height: 30px !important;
  border: none;
  font-size: 12px;
}

.reply-actions {
  justify-content: flex-start;
  margin-top: 8px;
}

.text-link {
  border: none;
  background: transparent;
  color: var(--cf-primary);
  cursor: pointer;
  padding: 0;
  font-size: 13px;
}

.text-link.danger,
.icon-btn.danger {
  color: var(--cf-danger);
}

.text-link.strong {
  font-weight: 900;
}

.form-block,
.reply-form,
.ai-dialog {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.form-block label {
  font-size: 14px;
  font-weight: 800;
}

.ai-dialog {
  gap: 16px;
}

.ai-dialog-head {
  display: flex;
  gap: 12px;
  align-items: center;
  padding: 14px;
  border-radius: 16px;
  background: var(--cf-bg-soft);
  border: 1px solid var(--cf-border);
}

.ai-dialog-head strong,
.ai-dialog-head span {
  display: block;
}

.ai-dialog-head span {
  margin-top: 4px;
  color: var(--cf-text-muted);
  font-size: 13px;
}

.ai-icon {
  width: 40px;
  height: 40px;
  border-radius: 12px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  color: var(--cf-text-primary);
  background: color-mix(in srgb, var(--cf-bg-elevated) 82%, var(--cf-primary) 18%);
  border: 1px solid color-mix(in srgb, var(--cf-primary) 28%, var(--cf-border));
  font-family: var(--cf-font-heading);
  font-size: 13px;
  font-weight: 900;
}

.ai-message-list {
  max-height: min(52vh, 520px);
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding-right: 4px;
}

.ai-message {
  display: grid;
  grid-template-columns: 42px minmax(0, 1fr);
  gap: 10px;
  align-items: flex-start;
}

.ai-message-role {
  height: 30px;
  min-width: 34px;
  padding: 0 10px;
  border-radius: 999px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  background: color-mix(in srgb, var(--cf-bg-elevated) 88%, var(--cf-primary) 12%);
  color: var(--cf-text-secondary);
  border: 1px solid color-mix(in srgb, var(--cf-primary) 18%, var(--cf-border));
  font-size: 12px;
  font-weight: 800;
}

.ai-message pre {
  margin: 0;
  white-space: pre-wrap;
  word-break: break-word;
  padding: 13px 14px;
  border-radius: 16px;
  border: 1px solid var(--cf-border);
  background: var(--cf-bg-elevated);
  color: var(--cf-text-secondary);
  line-height: 1.75;
  font-family: var(--cf-font-body);
}

.ai-message.user {
  grid-template-columns: minmax(0, 1fr) 42px;
}

.ai-message.user .ai-message-role {
  grid-column: 2;
  grid-row: 1;
  background: color-mix(in srgb, var(--cf-primary) 16%, var(--cf-bg-elevated));
  color: var(--cf-primary);
}

.ai-message.user pre {
  grid-column: 1;
  grid-row: 1;
  background: color-mix(in srgb, var(--cf-primary) 10%, var(--cf-bg-elevated));
}

.ai-typing {
  width: fit-content;
  min-height: 42px;
  padding: 13px 14px;
  border-radius: 16px;
  border: 1px solid var(--cf-border);
  background: var(--cf-bg-elevated);
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.ai-typing span {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: var(--cf-primary);
  animation: aiTyping 0.9s ease-in-out infinite;
}

.ai-typing span:nth-child(2) {
  animation-delay: 0.12s;
}

.ai-typing span:nth-child(3) {
  animation-delay: 0.24s;
}

.ai-input-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 10px;
  align-items: stretch;
  padding-top: 14px;
  border-top: 1px solid var(--cf-border);
}

.ai-send-btn {
  align-self: flex-end;
  min-height: 42px;
}

@keyframes aiTyping {
  0%,
  100% {
    opacity: 0.35;
    transform: translateY(0);
  }

  50% {
    opacity: 1;
    transform: translateY(-3px);
  }
}

@media (max-width: 1100px) {
  .reading-shell {
    grid-template-columns: minmax(0, 720px);
  }

  .side-column {
    position: static;
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 720px) {
  .detail-navbar {
    padding: 0 16px;
  }

  .reading-shell {
    padding: 28px 16px 0;
  }

  .post-card {
    border-radius: 24px;
    padding: 28px 22px;
  }

  .post-hero h1 {
    max-width: none;
  }

  .hero-author {
    align-items: flex-start;
  }

  .side-column {
    grid-template-columns: 1fr;
  }

  .comments-card {
    margin-top: 40px;
    padding: 0 16px;
  }

  .comment-editor,
  .comment-item {
    grid-template-columns: 1fr;
  }

  .comment-editor {
    display: block;
  }

  .self-avatar {
    display: none;
  }

  .comment-header {
    flex-direction: column;
  }

  .ai-input-row {
    grid-template-columns: 1fr;
  }

  .ai-send-btn {
    width: 100%;
  }
}
</style>
