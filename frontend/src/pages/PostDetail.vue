<script setup lang="ts">
import { ref, onMounted } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { NCard, NButton, NInput, NTag, NSpace, NSpin, NModal, NSelect, useMessage } from 'naive-ui';
import { getPostById, deletePost, toggleReaction } from '@/api/posts';
import { createComment, getComments, deleteComment } from '@/api/comments';
import { getQaInfo, acceptAnswer } from '@/api/qa';
import { createReport } from '@/api/report';
import { renderMentions } from '@/utils/mention';
import { useAuthStore } from '@/stores/auth';
import type { PostVO, CommentVO } from '@/types/post';
import type { QaQuestionVO } from '@/types/qa';

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

// Report state
const reportModalShow = ref(false);
const reportTargetId = ref<number>(0);
const reportTargetType = ref('POST');
const reportReason = ref('SPAM');
const reportDesc = ref('');
const reportSubmitting = ref(false);
const reportReasons = [
  { label: '垃圾广告', value: 'SPAM' },
  { label: '违规内容', value: 'ILLEGAL' },
  { label: '人身攻击', value: 'ABUSE' },
  { label: '色情低俗', value: 'PORN' },
  { label: '虚假信息', value: 'FAKE' },
  { label: '其他', value: 'OTHER' },
];

function openReport(targetType: string, targetId: number) {
  reportTargetType.value = targetType;
  reportTargetId.value = targetId;
  reportReason.value = 'SPAM';
  reportDesc.value = '';
  reportModalShow.value = true;
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
  }
  reportSubmitting.value = false;
}

const currentUserId = authStore.user?.id;
const isQaPost = () => post.value?.type === 'QA';
const isPostAuthor = () => currentUserId === post.value?.authorId;

async function loadPost() {
  loading.value = true;
  try {
    const id = Number(route.params.id);
    post.value = await getPostById(id);
    const isQa = post.value?.type === 'QA';
    comments.value = await getComments(id, undefined, 20, isQa);
    if (isQa) {
      qa.value = await getQaInfo(id);
    }
  } catch {
    post.value = null;
  }
  loading.value = false;
}

async function handleAccept(commentId: number) {
  if (!post.value) return;
  acceptingId.value = commentId;
  try {
    qa.value = await acceptAnswer(post.value.id, commentId);
    message.success('已采纳该回答');
  } catch (e: any) {
    message.error(e.message || '采纳失败');
  }
  acceptingId.value = null;
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
  if (!post.value || !commentText.value.trim()) return;
  submitting.value = true;
  try {
    const data = {
      postId: post.value.id,
      parentId: replyTo.value?.id,
      replyToId: replyTo.value?.id,
      content: commentText.value,
    };
    await createComment(data);
    message.success('评论成功');
    commentText.value = '';
    replyTo.value = null;
    // 重新加载评论
    comments.value = await getComments(post.value.id, undefined, 20, isQaPost());
    if (post.value) post.value.commentCount++;
  } catch {
    message.error('评论失败');
  }
  submitting.value = false;
}

async function handleCommentLike(comment: CommentVO) {
  try {
    const liked = await toggleReaction(comment.id, 'LIKE', 'COMMENT');
    if (comment.likeCount == null) comment.likeCount = 0;
    comment.likeCount += liked ? 1 : -1;
  } catch {
    message.error('操作失败');
  }
}

async function handleDeleteComment(commentId: number) {
  try {
    await deleteComment(commentId);
    message.success('已删除');
    if (post.value) {
      comments.value = await getComments(post.value.id, undefined, 20, isQaPost());
      post.value.commentCount--;
    }
  } catch {
    message.error('删除失败');
  }
}

onMounted(loadPost);
</script>

<template>
  <div class="detail-page">
    <template v-if="loading">
      <div class="loading"><NSpin /></div>
    </template>

    <template v-else-if="post">
      <NCard class="post-content">
        <div class="post-header">
          <div class="author-info">
            <div class="avatar">{{ post.author?.nickname?.charAt(0) || '?' }}</div>
            <div>
              <div class="author-name">{{ post.author?.nickname || '匿名' }}</div>
              <div class="post-time">{{ new Date(post.createdAt).toLocaleString() }}</div>
            </div>
          </div>
          <NSpace>
            <NTag v-if="post.isEssence === 1" type="warning" size="small">精华</NTag>
            <NButton
              size="small"
              type="warning"
              @click="openReport('POST', post.id)"
            >
              举报
            </NButton>
            <NButton
              size="small"
              type="info"
              @click="router.push('/posts/new?quote=' + post.id)"
            >
              引用
            </NButton>
            <NButton
              v-if="currentUserId === post.authorId"
              size="small"
              type="error"
              @click="handleDeletePost"
            >
              删除
            </NButton>
          </NSpace>
        </div>

        <h2 v-if="post.title" class="post-title">{{ post.title }}</h2>
        <p class="post-body" v-html="renderMentions(post.content)"></p>

        <NSpace v-if="post.topics && post.topics.length" class="topics">
          <NTag v-for="t in post.topics" :key="t" size="small">{{ t }}</NTag>
        </NSpace>

        <!-- QA 信息 -->
        <div v-if="isQaPost() && qa" class="qa-info">
          <NTag type="warning" size="small">悬赏 {{ qa.bountyPoints }} 积分</NTag>
          <NTag v-if="qa.isSolved" type="success" size="small">已解决</NTag>
          <NTag v-else type="info" size="small">待解决</NTag>
        </div>

        <div class="post-stats">
          <NButton
            :type="post.liked ? 'primary' : 'default'"
            size="small"
            @click="handleLike"
          >
            {{ post.liked ? '已赞' : '点赞' }} ({{ post.likeCount }})
          </NButton>
          <span>{{ post.viewCount }} 浏览</span>
          <span>{{ post.commentCount }} 评论</span>
        </div>
      </NCard>

      <!-- 评论区 -->
      <NCard class="comments-section">
        <h3>评论 ({{ post.commentCount }})</h3>

        <!-- 发表评论 -->
        <div class="comment-form">
          <div v-if="replyTo" class="reply-hint">
            回复 @{{ replyTo.nickname }}
            <NButton size="tiny" text @click="cancelReply">取消</NButton>
          </div>
          <NInput
            v-model:value="commentText"
            type="textarea"
            placeholder="写评论..."
            :autosize="{ minRows: 2, maxRows: 4 }"
          />
          <NButton
            type="primary"
            size="small"
            :loading="submitting"
            :disabled="!commentText.trim()"
            class="submit-btn"
            @click="submitComment"
          >
            发表
          </NButton>
        </div>

        <!-- 评论列表 -->
        <div v-for="c in comments" :key="c.id" class="comment-item">
          <div class="comment-avatar">{{ c.author?.nickname?.charAt(0) || '?' }}</div>
          <div class="comment-body">
            <div class="comment-header">
              <span class="comment-author">{{ c.author?.nickname || '匿名' }}</span>
              <span class="comment-time">{{ new Date(c.createdAt).toLocaleDateString() }}</span>
            </div>
            <p class="comment-text" v-html="renderMentions(c.content)"></p>
            <div class="comment-actions">
              <NButton size="tiny" text @click="handleCommentLike(c)">
                👍 {{ c.likeCount || 0 }}
              </NButton>
              <NButton size="tiny" text @click="handleReply(c)">回复</NButton>
              <NButton size="tiny" text type="warning" @click="openReport('COMMENT', c.id)">举报</NButton>
              <NButton
                v-if="isQaPost() && isPostAuthor() && !qa?.isSolved"
                size="tiny"
                text
                type="success"
                :loading="acceptingId === c.id"
                @click="handleAccept(c.id)"
              >
                采纳
              </NButton>
              <NButton
                v-if="currentUserId === c.authorId"
                size="tiny"
                text
                type="error"
                @click="handleDeleteComment(c.id)"
              >
                删除
              </NButton>
            </div>
            <NTag v-if="qa?.acceptedCommentId === c.id" type="success" size="tiny">已采纳</NTag>

            <!-- 子评论 -->
            <div v-if="c.replies && c.replies.length" class="replies">
              <div v-for="r in c.replies" :key="r.id" class="reply-item">
                <span class="reply-author">{{ r.author?.nickname || '匿名' }}</span>
                <span class="reply-text" v-html="renderMentions(r.content)"></span>
                <span class="reply-time">{{ new Date(r.createdAt).toLocaleDateString() }}</span>
              </div>
            </div>
          </div>
        </div>

        <div v-if="comments.length === 0" class="no-comments">
          <p>暂无评论</p>
        </div>
      </NCard>
    </template>

    <!-- Report Modal -->
    <NModal v-model:show="reportModalShow" title="举报">
      <div style="padding: 16px; width: 400px;">
        <NSelect
          v-model:value="reportReason"
          :options="reportReasons"
          style="margin-bottom: 12px;"
          placeholder="选择举报原因"
        />
        <NInput
          v-model:value="reportDesc"
          type="textarea"
          placeholder="补充说明（可选）"
          :autosize="{ minRows: 3, maxRows: 6 }"
        />
        <NSpace style="margin-top: 16px; justify-content: flex-end;">
          <NButton @click="reportModalShow = false">取消</NButton>
          <NButton type="primary" :loading="reportSubmitting" @click="submitReport">提交</NButton>
        </NSpace>
      </div>
    </NModal>
  </div>
</template>

<style scoped>
.detail-page {
  max-width: 720px;
  margin: 40px auto;
  padding: 0 16px;
}
.loading {
  text-align: center;
  padding: 80px;
}
.post-content {
  margin-bottom: 20px;
}
.post-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 16px;
}
.author-info {
  display: flex;
  align-items: center;
  gap: 12px;
}
.avatar {
  width: 44px;
  height: 44px;
  border-radius: 50%;
  background: #18a058;
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 18px;
  font-weight: bold;
}
.author-name {
  font-weight: 600;
}
.post-time {
  color: #999;
  font-size: 12px;
}
.post-title {
  margin: 0 0 12px;
}
.post-body {
  line-height: 1.8;
  white-space: pre-wrap;
  margin-bottom: 16px;
}
.post-body :deep(.mention-link),
.comment-text :deep(.mention-link),
.reply-text :deep(.mention-link) {
  color: #18a058;
  text-decoration: none;
  font-weight: 500;
}
.post-body :deep(.mention-link):hover,
.comment-text :deep(.mention-link):hover,
.reply-text :deep(.mention-link):hover {
  text-decoration: underline;
}
.topics {
  margin-bottom: 16px;
}
.qa-info {
  margin-bottom: 16px;
}
.post-stats {
  display: flex;
  align-items: center;
  gap: 16px;
  font-size: 14px;
  color: #666;
}
.comments-section h3 {
  margin: 0 0 16px;
}
.comment-form {
  margin-bottom: 24px;
}
.reply-hint {
  color: #18a058;
  font-size: 13px;
  margin-bottom: 6px;
}
.submit-btn {
  margin-top: 8px;
}
.comment-item {
  display: flex;
  gap: 12px;
  padding: 14px 0;
  border-bottom: 1px solid #f0f0f0;
}
.comment-avatar {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  background: #18a058;
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 14px;
  flex-shrink: 0;
}
.comment-body {
  flex: 1;
}
.comment-header {
  margin-bottom: 4px;
}
.comment-author {
  font-weight: 600;
  font-size: 14px;
  margin-right: 8px;
}
.comment-time {
  color: #999;
  font-size: 12px;
}
.comment-text {
  margin: 4px 0;
  font-size: 14px;
  line-height: 1.6;
}
.comment-actions {
  margin-top: 4px;
}
.replies {
  margin-top: 10px;
  padding: 8px 12px;
  background: #f9f9f9;
  border-radius: 6px;
}
.reply-item {
  padding: 4px 0;
  font-size: 13px;
}
.reply-author {
  font-weight: 600;
  margin-right: 8px;
}
.reply-text {
  color: #333;
}
.reply-time {
  color: #999;
  font-size: 11px;
  margin-left: 8px;
}
.no-comments {
  text-align: center;
  color: #999;
  padding: 24px;
}
</style>
