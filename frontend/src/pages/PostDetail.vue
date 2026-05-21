<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { NButton, NCard, NEmpty, NIcon, NInput, NModal, NSelect, NSpin, NTag, useMessage } from 'naive-ui';
import { acceptAnswer, getQaInfo } from '@/api/qa';
import { aiChat, aiModerate, aiRecommendTags, aiSummarize } from '@/api/ai';
import { deleteComment, createComment, getComments, toggleCommentReaction, updateComment } from '@/api/comments';
import { deletePost, getPostById, toggleReaction } from '@/api/posts';
import { createReport } from '@/api/report';
import { useAuthStore } from '@/stores/auth';
import { useWebSocket } from '@/composables/useWebSocket';
import MentionText from '@/components/MentionText.vue';
import type { CommentVO, PostVO } from '@/types/post';
import type { QaQuestionVO } from '@/types/qa';
import { ArrowBackOutline, ChatbubblesOutline, CreateOutline, HeartOutline, LinkOutline, MegaphoneOutline, PricetagOutline, ShieldCheckmarkOutline, TrashOutline, PersonOutline, SendOutline, SparklesOutline } from '@vicons/ionicons5';

const route = useRoute();
const router = useRouter();
const message = useMessage();
const authStore = useAuthStore();

const post = ref<PostVO | null>(null);
const qa = ref<QaQuestionVO | null>(null);
const comments = ref<CommentVO[]>([]);
const loading = ref(true);
const commentText = ref('');
const replyTo = ref<{ id: number; nickname: string } | null>(null);
const submitting = ref(false);
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
const isPostAuthor = computed(() => currentUserId.value === post.value?.authorId);

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
          `我已阅读这篇帖子，先给出一版快速分析：`,
          '',
          `1. 核心摘要：${summary.summary || '暂未提取到明确摘要。'}`,
          `2. 内容风险：${formatRiskText(moderate.riskLevel, moderate.riskReason)}`,
          `3. 推荐标签：${(tags.tags || []).length ? tags.tags.join('、') : '暂无明显标签'}`,
          `4. 互动建议：可以继续追问“这篇帖子适合怎么回复”“有哪些争议点”“帮我提炼成笔记”。`,
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
  try {
    const id = Number(route.params.id);
    post.value = await getPostById(id);
    comments.value = await getComments(id, undefined, 20, post.value?.type === 'QA');
    if (post.value?.type === 'QA') {
      qa.value = await getQaInfo(id);
    }
  } catch {
    post.value = null;
  } finally {
    loading.value = false;
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

function handleReply(comment: CommentVO) {
  replyTo.value = { id: comment.id, nickname: comment.author?.nickname || '匿名' };
  commentText.value = '';
}

function cancelReply() {
  replyTo.value = null;
  commentText.value = '';
}

async function submitComment() {
  if (!post.value) return;
  // 纯空白（空格/回车）时给出明确提示，但允许包含换行的有效内容
  if (!commentText.value.trim()) {
    message.warning('评论内容不能为空，请输入有效内容');
    return;
  }
  submitting.value = true;
  try {
    await createComment({
      postId: post.value.id,
      parentId: replyTo.value?.id,
      replyToId: replyTo.value?.id,
      content: commentText.value,
    });
    message.success('评论成功');
    commentText.value = '';
    replyTo.value = null;
    comments.value = await getComments(post.value.id, undefined, 20, post.value?.type === 'QA');
    if (post.value) post.value.commentCount += 1;
  } catch {
    message.error('评论失败');
  } finally {
    submitting.value = false;
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
    '你是 CampusForum 的帖子分析助手。请只围绕用户正在查看的帖子进行分析，回答要简洁、结构清晰、可执行。',
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
    <div class="page-head cf-surface">
      <button
        class="back-btn"
        @click="router.back()"
      >
        <n-icon size="18"><ArrowBackOutline /></n-icon>
      </button>
      <div>
        <span class="cf-pill">Post Detail</span>
        <h1 class="cf-section-title">
          帖子详情
        </h1>
        <p class="cf-section-subtitle">
          统一为更清晰的内容阅读页，同时保留评论、采纳、举报与删除能力。
        </p>
      </div>
    </div>

    <div v-if="loading" class="loading-wrap">
      <n-spin size="large" />
    </div>

    <template v-else-if="post">
      <div class="content-grid">
        <section class="main-column">
          <article class="post-card cf-card">
            <div class="post-top">
              <button
                class="author-link"
                @click="goUser(post.authorId)"
              >
                <div class="avatar-badge">
                  {{ post.author?.nickname?.charAt(0) || '?' }}
                </div>
                <div>
                  <div class="author-name">
                    {{ post.author?.nickname || '匿名用户' }}
                  </div>
                  <div class="post-meta">
                    <span>{{ formatTime(post.createdAt) }}</span>
                    <span>·</span>
                    <span>阅读 {{ post.viewCount }}</span>
                  </div>
                </div>
              </button>

              <div class="top-actions">
                <n-tag v-if="post.isEssence === 1" round type="warning" size="small">精华</n-tag>
                <n-tag v-if="post.type === 'QA'" round type="info" size="small">问答</n-tag>
                <button class="icon-btn" @click="openReport('POST', post.id)">
                  <n-icon size="16"><ShieldCheckmarkOutline /></n-icon>
                </button>
                <button class="icon-btn" @click="router.push('/posts/new?quote=' + post.id)">
                  <n-icon size="16"><LinkOutline /></n-icon>
                </button>
                <button
                  v-if="isPostAuthor"
                  class="icon-btn"
                  @click="router.push(`/posts/${post.id}/edit`)"
                  title="编辑"
                >
                  <n-icon size="16"><CreateOutline /></n-icon>
                </button>
                <button
                  v-if="isPostAuthor"
                  class="icon-btn danger"
                  @click="handleDeletePost"
                >
                  <n-icon size="16"><TrashOutline /></n-icon>
                </button>
              </div>
            </div>

            <h2 v-if="post.title" class="post-title">
              {{ post.title }}
            </h2>

            <div class="post-content">
              <MentionText :text="post.content" />
            </div>

            <div v-if="post.topics?.length" class="topic-row">
              <span v-for="topic in post.topics" :key="topic" class="topic-tag">
                <n-icon size="14"><PricetagOutline /></n-icon>
                {{ topic }}
              </span>
            </div>

            <div v-if="post.type === 'QA' && qa" class="qa-strip">
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
                class="action-btn"
                :class="{ active: post.liked }"
                @click="handleLike"
              >
                <n-icon size="16"><HeartOutline /></n-icon>
                {{ post.likeCount }}
              </button>
              <button class="action-btn">
                <n-icon size="16"><ChatbubblesOutline /></n-icon>
                {{ post.commentCount }}
              </button>
              <button class="action-btn" @click="openReport('POST', post.id)">
                <n-icon size="16"><MegaphoneOutline /></n-icon>
                举报
              </button>
              <button class="action-btn ai-action" @click="openAiAnalysis">
                <n-icon size="16"><SparklesOutline /></n-icon>
                AI 分析
              </button>
            </div>
          </article>

          <section class="comments-card cf-card">
            <div class="section-title-row">
              <h3>评论</h3>
              <span>{{ post.commentCount }} 条互动</span>
            </div>

            <div class="comment-editor">
              <div v-if="replyTo" class="reply-banner">
                正在回复 @{{ replyTo.nickname }}
                <button class="text-link" @click="cancelReply">取消</button>
              </div>
              <n-input
                v-model:value="commentText"
                type="textarea"
                class="cf-textarea"
                placeholder="写下你的想法，或者补充你的观点"
                :autosize="{ minRows: 4, maxRows: 10 }"
              />
              <div class="editor-actions">
                <button class="cf-secondary-btn" @click="cancelReply">清空</button>
                <button class="cf-primary-btn" :disabled="submitting" @click="submitComment">
                  发布评论
                </button>
              </div>
            </div>

            <div v-if="comments.length === 0" class="empty-comment">
              <n-empty description="还没有评论，来抢首评吧" />
            </div>

            <div v-else class="comment-list">
              <article v-for="comment in comments" :key="comment.id" class="comment-item">
                <div class="comment-line" />
                <div class="comment-body">
                  <div class="comment-header">
                    <button class="author-link small" @click="goUser(comment.authorId)">
                      <div class="comment-avatar">{{ formatAuthorName(comment).charAt(0) }}</div>
                      <div>
                        <strong>{{ formatAuthorName(comment) }}</strong>
                        <span>{{ formatTime(comment.createdAt) }}</span>
                      </div>
                    </button>
                    <div class="comment-actions">
                      <button class="text-link" @click="handleReply(comment)">回复</button>
                      <button class="text-link" @click="handleCommentLike(comment)">
                        点赞 {{ comment.likeCount || 0 }}
                      </button>
                      <button class="text-link danger" @click="openReport('COMMENT', comment.id)">举报</button>
                      <button
                        v-if="currentUserId === comment.authorId"
                        class="text-link"
                        @click="startEditComment(comment)"
                      >编辑</button>
                      <button
                        v-if="currentUserId === comment.authorId"
                        class="text-link danger"
                        @click="handleDeleteComment(comment.id)"
                      >删除</button>
                      <button
                        v-if="isPostAuthor && post.type === 'QA' && !qa?.isSolved"
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
                      <div class="editor-actions" style="margin-top: 8px;">
                        <button class="cf-secondary-btn" @click="cancelEditComment">取消</button>
                        <button class="cf-primary-btn" @click="submitEditComment(comment.id)">保存</button>
                      </div>
                    </template>
                    <MentionText v-else :text="comment.content" />
                  </div>
                </div>
              </article>
            </div>
          </section>
        </section>

        <aside class="side-column">
          <div class="side-panel cf-surface">
            <h3>帖子信息</h3>
            <div class="info-row"><span>作者</span><strong>{{ post.author?.nickname || '匿名用户' }}</strong></div>
            <div class="info-row"><span>发布时间</span><strong>{{ formatTime(post.createdAt) }}</strong></div>
            <div class="info-row"><span>评论数</span><strong>{{ post.commentCount }}</strong></div>
            <div class="info-row"><span>点赞数</span><strong>{{ post.likeCount }}</strong></div>
          </div>
          <div v-if="post.type === 'QA' && qa" class="side-panel cf-surface accent-panel">
            <h3>问答状态</h3>
            <p>该问题适合继续补充实战经验、案例或者参考资料。</p>
            <n-tag :type="qa.isSolved ? 'success' : 'info'" round>
              {{ qa.isSolved ? '问题已解决' : '仍在等待答案' }}
            </n-tag>
          </div>
        </aside>
      </div>
    </template>

    <div v-else class="loading-wrap">
      <n-empty description="帖子不存在或已被删除" />
    </div>

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
  </div>
</template>

<style scoped lang="scss">
.detail-page {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.page-head {
  padding: 22px;
  display: flex;
  align-items: flex-start;
  gap: 16px;
}

.back-btn,
.icon-btn {
  width: 40px;
  height: 40px;
  border-radius: 12px;
  border: 1px solid var(--cf-border);
  background: var(--cf-bg-elevated);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  color: var(--cf-text-secondary);
}

.content-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 300px;
  gap: 18px;
}

.main-column,
.side-column {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.post-card,
.comments-card,
.side-panel {
  padding: 22px;
}

.post-top {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  align-items: flex-start;
}

.author-link {
  display: inline-flex;
  align-items: center;
  gap: 12px;
  background: transparent;
  border: none;
  padding: 0;
  cursor: pointer;
  text-align: left;
}

.author-link.small .comment-avatar {
  width: 36px;
  height: 36px;
  font-size: 14px;
}

.avatar-badge,
.comment-avatar {
  width: 44px;
  height: 44px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--cf-primary-soft);
  color: var(--cf-primary);
  font-weight: 700;
  flex-shrink: 0;
}

.author-name {
  font-weight: 700;
}

.post-meta {
  display: flex;
  gap: 8px;
  color: var(--cf-text-muted);
  font-size: 13px;
  margin-top: 4px;
}

.top-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.icon-btn.danger {
  color: var(--cf-danger);
}

.post-title {
  margin: 18px 0 12px;
  font-family: var(--cf-font-heading);
  font-size: 28px;
  line-height: 1.25;
}

.post-content {
  color: var(--cf-text-secondary);
  line-height: 1.9;
}

.topic-row {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 16px;
}

.topic-tag {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 6px 12px;
  border-radius: 999px;
  background: var(--cf-bg-soft);
  color: var(--cf-primary);
  font-size: 13px;
  font-weight: 700;
}

.qa-strip {
  margin-top: 16px;
  padding: 16px;
  border-radius: 18px;
  background: linear-gradient(180deg, var(--cf-primary-soft), var(--cf-bg-glass));
  border: 1px solid color-mix(in srgb, var(--cf-primary) 22%, var(--cf-border));
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: center;

  strong {
    display: block;
    margin-bottom: 4px;
  }

  span {
    color: var(--cf-text-muted);
    font-size: 13px;
  }
}

.post-actions {
  display: flex;
  gap: 10px;
  margin-top: 18px;
  padding-top: 16px;
  border-top: 1px solid var(--cf-border);
}

.action-btn {
  border: 1px solid var(--cf-border);
  background: var(--cf-bg-elevated);
  color: var(--cf-text-secondary);
  border-radius: 12px;
  padding: 10px 14px;
  display: inline-flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
}

.action-btn.active,
.action-btn.ai-action {
  background: var(--cf-primary-soft);
  color: var(--cf-primary);
}

.section-title-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  margin-bottom: 16px;

  h3 {
    margin: 0;
    font-family: var(--cf-font-heading);
    font-size: 24px;
  }

  span {
    color: var(--cf-text-muted);
    font-size: 13px;
  }
}

.comment-editor {
  display: flex;
  flex-direction: column;
  gap: 14px;
  margin-bottom: 22px;
}

.reply-banner {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  padding: 12px 14px;
  border-radius: 14px;
  background: var(--cf-bg-soft);
  color: var(--cf-text-secondary);
}

.editor-actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}

.empty-comment,
.loading-wrap {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 120px;
}

.comment-list {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.comment-item {
  display: grid;
  grid-template-columns: 14px minmax(0, 1fr);
  gap: 14px;
}

.comment-line {
  width: 2px;
  border-radius: 999px;
  background: var(--cf-border);
  margin-left: 6px;
}

.comment-body {
  padding-bottom: 18px;
  border-bottom: 1px solid var(--cf-border);
}

.comment-header {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: flex-start;
}

.comment-header strong {
  display: block;
}

.comment-header span {
  color: var(--cf-text-muted);
  font-size: 13px;
}

.comment-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  justify-content: flex-end;
}

.comment-content {
  margin-top: 12px;
  color: var(--cf-text-secondary);
  line-height: 1.85;
}

.text-link {
  border: none;
  background: transparent;
  color: var(--cf-primary);
  cursor: pointer;
  padding: 0;
  font-size: 13px;

  &.danger {
    color: var(--cf-danger);
  }

  &.strong {
    font-weight: 700;
  }
}

.side-panel h3 {
  margin: 0 0 14px;
  font-family: var(--cf-font-heading);
  font-size: 22px;
}

.info-row {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  padding: 10px 0;
  color: var(--cf-text-secondary);
  border-bottom: 1px solid var(--cf-border);
}

.info-row:last-child {
  border-bottom: none;
}

.info-row strong {
  color: var(--cf-text-primary);
}

.accent-panel {
  background: linear-gradient(180deg, var(--cf-primary-soft), var(--cf-bg-glass));
  border-color: color-mix(in srgb, var(--cf-primary) 22%, var(--cf-border));
}

.form-block {
  display: flex;
  flex-direction: column;
  gap: 10px;

  label {
    font-size: 14px;
    font-weight: 700;
  }
}

.modal-actions {
  display: flex;
  gap: 10px;
  justify-content: flex-end;
}

.ai-dialog {
  display: flex;
  flex-direction: column;
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

  strong {
    display: block;
    color: var(--cf-text-primary);
  }

  span {
    display: block;
    margin-top: 4px;
    color: var(--cf-text-muted);
    font-size: 13px;
  }
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
  box-shadow: inset 0 1px 0 color-mix(in srgb, #ffffff 30%, transparent);
  font-family: var(--cf-font-heading);
  font-size: 13px;
  font-weight: 800;
  letter-spacing: 0;
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
  font-weight: 700;
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

  .ai-message-role {
    grid-column: 2;
    grid-row: 1;
    background: color-mix(in srgb, var(--cf-primary) 16%, var(--cf-bg-elevated));
    color: var(--cf-primary);
  }

  pre {
    grid-column: 1;
    grid-row: 1;
    background: color-mix(in srgb, var(--cf-primary) 10%, var(--cf-bg-elevated));
  }
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

  span {
    width: 7px;
    height: 7px;
    border-radius: 50%;
    background: var(--cf-primary);
    animation: aiTyping 0.9s ease-in-out infinite;
  }

  span:nth-child(2) {
    animation-delay: 0.12s;
  }

  span:nth-child(3) {
    animation-delay: 0.24s;
  }
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
  .content-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 720px) {
  .post-actions {
    flex-wrap: wrap;
  }

  .ai-input-row {
    grid-template-columns: 1fr;
  }

  .ai-send-btn {
    width: 100%;
  }
}
</style>
