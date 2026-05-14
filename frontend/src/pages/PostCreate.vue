<script setup lang="ts">
import { ref, computed, onMounted } from 'vue';
import { useRouter, useRoute } from 'vue-router';
import { NButton, NInput, NSelect, NInputNumber, NCard, NSpace, NTag, NAlert, useMessage } from 'naive-ui';
import { createPost, getPostById } from '@/api/posts';
import type { PostVO } from '@/types/post';

const router = useRouter();
const route = useRoute();
const message = useMessage();

const title = ref('');
const content = ref('');
const postType = ref('NORMAL');
const bountyPoints = ref(0);
const topicInput = ref('');
const topics = ref<string[]>([]);
const loading = ref(false);
const quotePostId = ref<number | undefined>();
const quotedPost = ref<PostVO | null>(null);

const quoteIdParam = route.query.quote;
if (quoteIdParam) {
  quotePostId.value = Number(quoteIdParam);
}

onMounted(async () => {
  if (quotePostId.value) {
    try {
      quotedPost.value = await getPostById(quotePostId.value);
    } catch {
      message.warning('引用的帖子不存在');
    }
  }
});

const typeOptions = [
  { label: '普通帖子', value: 'NORMAL' },
  { label: '悬赏问答', value: 'QA' },
];

const isQa = computed(() => postType.value === 'QA');

function addTopic() {
  const t = topicInput.value.trim();
  if (t && !topics.value.includes(t)) {
    topics.value.push(t);
    topicInput.value = '';
  }
}

function removeTopic(t: string) {
  topics.value = topics.value.filter(x => x !== t);
}

async function submit() {
  if (!content.value.trim()) {
    message.warning('请输入内容');
    return;
  }
  if (isQa.value && !title.value.trim()) {
    message.warning('悬赏问答需要填写标题');
    return;
  }
  loading.value = true;
  try {
    const post = await createPost({
      title: title.value.trim() || undefined,
      content: content.value,
      topics: topics.value.length > 0 ? topics.value : undefined,
      scope: 'SQUARE',
      type: postType.value,
      bountyPoints: isQa.value ? bountyPoints.value : undefined,
      quotePostId: quotePostId.value,
    });
    message.success('发布成功');
    router.push(`/posts/${post.id}`);
  } catch {
    message.error('发布失败');
  }
  loading.value = false;
}

function cancel() {
  router.back();
}
</script>

<template>
  <div class="create-page">
    <NCard title="发帖">
      <div class="form">
        <NAlert
          v-if="quotedPost"
          type="info"
          title="引用帖子"
          :bordered="false"
        >
          <template #header>
            <div style="display: flex; align-items: center; gap: 8px;">
              引用 @{{ quotedPost.author?.nickname || '匿名' }} 的帖子
              <NButton size="tiny" text type="error" @click="quotedPost = null; quotePostId = undefined;">
                取消引用
              </NButton>
            </div>
          </template>
          <div class="quoted-preview">
            <strong>{{ quotedPost.title || '无标题' }}</strong>
            <p>{{ quotedPost.content.slice(0, 200) }}{{ quotedPost.content.length > 200 ? '...' : '' }}</p>
          </div>
        </NAlert>

        <NSelect v-model:value="postType" :options="typeOptions" />

        <NInput v-model:value="title" placeholder="标题（可选）" class="field" maxlength="255" />

        <div v-if="isQa" class="bounty-row">
          <label>悬赏积分</label>
          <NInputNumber v-model:value="bountyPoints" :min="0" :max="1000" />
        </div>

        <NInput
          v-model:value="content"
          type="textarea"
          placeholder="想说些什么..."
          class="field"
          :autosize="{ minRows: 6, maxRows: 16 }"
          maxlength="20000"
        />

        <div class="topic-row">
          <NInput
            v-model:value="topicInput"
            placeholder="添加话题（回车确认）"
            class="topic-input"
            @keyup.enter="addTopic"
          />
          <NButton size="small" @click="addTopic">添加</NButton>
        </div>
        <NSpace v-if="topics.length > 0" class="topic-list">
          <NTag v-for="t in topics" :key="t" closable @close="removeTopic(t)">{{ t }}</NTag>
        </NSpace>

        <NSpace class="actions">
          <NButton type="primary" :loading="loading" @click="submit">发布</NButton>
          <NButton @click="cancel">取消</NButton>
        </NSpace>
      </div>
    </NCard>
  </div>
</template>

<style scoped>
.create-page {
  max-width: 680px;
  margin: 40px auto;
  padding: 0 16px;
}
.form {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.bounty-row {
  display: flex;
  align-items: center;
  gap: 12px;
}
.bounty-row label {
  font-size: 14px;
  color: #666;
}
.topic-row {
  display: flex;
  gap: 8px;
}
.topic-input {
  flex: 1;
}
.topic-list {
  flex-wrap: wrap;
}
.quoted-preview {
  max-height: 120px;
  overflow: hidden;
  font-size: 13px;
  color: #666;
}
.quoted-preview p {
  margin: 4px 0;
  line-height: 1.5;
  white-space: pre-wrap;
}
.actions {
  margin-top: 8px;
}
</style>
