<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { NAlert, NButton, NIcon, NInput, NInputNumber, NSelect, NTag, useMessage } from 'naive-ui';
import { createPost, getPostById } from '@/api/posts';
import type { PostVO } from '@/types/post';
import { ArrowForwardOutline, ChatbubbleEllipsesOutline, CloseOutline, PricetagOutline, SparklesOutline } from '@vicons/ionicons5';

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
const publishScope = ref<'SQUARE' | 'SPACE'>('SQUARE');
const spaceId = ref<number | undefined>();
const spaceName = ref('');

const typeOptions = [
  { label: '普通帖子', value: 'NORMAL' },
  { label: '悬赏问答', value: 'QA' },
];

const isQa = computed(() => postType.value === 'QA');
const contentCount = computed(() => content.value.trim().length);

onMounted(async () => {
  const scopeParam = String(route.query.scope || '').toUpperCase();
  const spaceIdParam = Number(route.query.spaceId);
  if (scopeParam === 'SPACE' && Number.isFinite(spaceIdParam) && spaceIdParam > 0) {
    publishScope.value = 'SPACE';
    spaceId.value = spaceIdParam;
    spaceName.value = String(route.query.spaceName || '当前学习圈');
  }

  const quoteIdParam = route.query.quote;
  if (!quoteIdParam) return;

  quotePostId.value = Number(quoteIdParam);
  try {
    quotedPost.value = await getPostById(quotePostId.value);
  } catch {
    message.warning('引用的帖子不存在');
  }
});

function addTopic() {
  const topic = topicInput.value.trim();
  if (!topic || topics.value.includes(topic)) return;
  topics.value.push(topic);
  topicInput.value = '';
}

function removeTopic(topic: string) {
  topics.value = topics.value.filter((item) => item !== topic);
}

function clearQuote() {
  quotedPost.value = null;
  quotePostId.value = undefined;
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
      topics: topics.value.length ? topics.value : undefined,
      scope: publishScope.value,
      spaceId: publishScope.value === 'SPACE' ? spaceId.value : undefined,
      type: postType.value,
      bountyPoints: isQa.value ? bountyPoints.value : undefined,
      quotePostId: quotePostId.value,
    });
    message.success('发布成功');
    router.push(`/posts/${post.id}`);
  } catch {
    message.error('发布失败');
  } finally {
    loading.value = false;
  }
}
</script>

<template>
  <div class="create-page">
    <section class="editor-head cf-surface">
      <div>
        <span class="cf-pill">Compose</span>
        <h1 class="cf-section-title">
          发布新内容
        </h1>
        <p class="cf-section-subtitle">
          {{ publishScope === 'SPACE' ? `发布到「${spaceName}」` : '适配新的广场风格，将发帖页改造成更清晰的创作工作台，兼容普通帖子、问答与引用发布。' }}
        </p>
      </div>
      <button class="cf-secondary-btn" @click="router.back()">
        返回
      </button>
    </section>

    <div class="editor-grid">
      <section class="main-editor cf-card">
        <div v-if="quotedPost" class="quote-banner">
          <div>
            <strong>正在引用 @{{ quotedPost.author?.nickname || '匿名用户' }} 的帖子</strong>
            <p>{{ quotedPost.title || '无标题' }}</p>
          </div>
          <button class="icon-btn" @click="clearQuote">
            <n-icon size="16"><CloseOutline /></n-icon>
          </button>
        </div>

        <div class="form-block">
          <label>内容类型</label>
          <n-select v-model:value="postType" :options="typeOptions" />
        </div>

        <div class="form-block">
          <label>标题</label>
          <n-input
            v-model:value="title"
            placeholder="普通帖子可选；问答贴建议写清问题背景"
            maxlength="255"
          />
        </div>

        <div v-if="isQa" class="bounty-box">
          <div class="bounty-text">
            <strong>设置悬赏积分</strong>
            <span>更具体的问题与合理悬赏，更容易获得高质量回答。</span>
          </div>
          <n-input-number
            v-model:value="bountyPoints"
            :min="0"
            :max="1000"
          />
        </div>

        <div class="form-block">
          <div class="label-row">
            <label>正文内容</label>
            <span>{{ contentCount }}/20000</span>
          </div>
          <n-input
            v-model:value="content"
            type="textarea"
            :autosize="{ minRows: 10, maxRows: 20 }"
            placeholder="描述你的问题、经验、观察或想法。清晰的结构会让更多人愿意参与讨论。"
            maxlength="20000"
          />
        </div>

        <div class="form-block">
          <label>添加话题</label>
          <div class="topic-input-row">
            <n-input
              v-model:value="topicInput"
              placeholder="输入话题后回车确认"
              @keyup.enter="addTopic"
            />
            <button class="cf-secondary-btn" @click="addTopic">
              添加
            </button>
          </div>
          <div v-if="topics.length" class="topic-list">
            <n-tag
              v-for="topic in topics"
              :key="topic"
              closable
              round
              @close="removeTopic(topic)"
            >
              {{ topic }}
            </n-tag>
          </div>
        </div>

        <div class="editor-actions">
          <button class="cf-secondary-btn" @click="router.back()">
            取消
          </button>
          <button class="cf-primary-btn" :disabled="loading" @click="submit">
            <n-icon size="16"><ArrowForwardOutline /></n-icon>
            {{ loading ? '发布中...' : '立即发布' }}
          </button>
        </div>
      </section>

      <aside class="side-panels">
        <div class="side-panel cf-surface">
          <h3>创作建议</h3>
          <ul>
            <li>先写结论，再补充背景与细节，读者更易快速理解。</li>
            <li>话题标签建议控制在 2~4 个，避免过度分散内容焦点。</li>
            <li>问答贴可提供已尝试方案，提高回复质量。</li>
          </ul>
        </div>

        <div class="side-panel cf-surface accent-panel">
          <h3>当前草稿概览</h3>
          <div class="overview-row"><span>类型</span><strong>{{ isQa ? '悬赏问答' : '普通帖子' }}</strong></div>
          <div class="overview-row"><span>标题</span><strong>{{ title.trim() || '未填写' }}</strong></div>
          <div class="overview-row"><span>字数</span><strong>{{ contentCount }}</strong></div>
          <div class="overview-row"><span>话题数</span><strong>{{ topics.length }}</strong></div>
        </div>
      </aside>
    </div>
  </div>
</template>

<style scoped lang="scss">
.create-page {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.editor-head {
  padding: 22px;
  display: flex;
  justify-content: space-between;
  gap: 16px;
  align-items: flex-end;
}

.editor-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 300px;
  gap: 18px;
  align-items: start;
}

.main-editor,
.side-panel {
  padding: 22px;
}

.main-editor {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.quote-banner {
  display: flex;
  justify-content: space-between;
  gap: 14px;
  padding: 16px;
  border-radius: 18px;
  background: linear-gradient(180deg, var(--cf-primary-soft), var(--cf-bg-glass));
  border: 1px solid color-mix(in srgb, var(--cf-primary) 22%, var(--cf-border));

  strong {
    display: block;
    margin-bottom: 6px;
  }

  p {
    margin: 0;
    color: var(--cf-text-secondary);
  }
}

.icon-btn {
  width: 38px;
  height: 38px;
  border-radius: 12px;
  border: 1px solid var(--cf-border);
  background: var(--cf-bg-elevated);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
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

.label-row {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: center;

  span {
    color: var(--cf-text-muted);
    font-size: 13px;
  }
}

.bounty-box {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  align-items: center;
  padding: 18px;
  border-radius: 18px;
  background: var(--cf-bg-soft);
}

.bounty-text {
  strong {
    display: block;
    margin-bottom: 6px;
  }

  span {
    color: var(--cf-text-muted);
    font-size: 13px;
    line-height: 1.7;
  }
}

.topic-input-row {
  display: flex;
  gap: 10px;
}

.topic-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.editor-actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  padding-top: 8px;
}

.side-panels {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.side-panel h3 {
  margin: 0 0 14px;
  font-family: var(--cf-font-heading);
  font-size: 22px;
}

.side-panel ul {
  margin: 0;
  padding-left: 18px;
  color: var(--cf-text-secondary);
  line-height: 1.8;
}

.accent-panel {
  background: linear-gradient(180deg, var(--cf-primary-soft), var(--cf-bg-glass));
  border-color: color-mix(in srgb, var(--cf-primary) 22%, var(--cf-border));
}

.overview-row {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  padding: 10px 0;
  color: var(--cf-text-secondary);
  border-bottom: 1px solid var(--cf-border);
}

.overview-row:last-child {
  border-bottom: none;
}

.overview-row strong {
  color: var(--cf-text-primary);
}

@media (max-width: 1100px) {
  .editor-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 720px) {
  .editor-head,
  .bounty-box,
  .topic-input-row,
  .editor-actions {
    flex-direction: column;
    align-items: stretch;
  }
}
</style>
