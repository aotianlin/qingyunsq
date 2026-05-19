<script setup lang="ts">
import { computed, ref } from 'vue';
import { useMessage } from 'naive-ui';
import { CopyOutline, DocumentTextOutline, LinkOutline, RefreshOutline, SearchOutline, ShieldCheckmarkOutline } from '@vicons/ionicons5';
import { getPostById } from '@/api/posts';
import type { PostVO } from '@/types/post';

const message = useMessage();

const menus = [
  { id: 'summary', label: '智能摘要', desc: '面向长文、课件与帖子内容的快速结构化提炼', icon: DocumentTextOutline },
  { id: 'content', label: '内容检测', desc: '帮助识别论述完整度、逻辑清晰度与表达风险', icon: ShieldCheckmarkOutline },
  { id: 'qa', label: 'AI 问答', desc: '针对课程概念、帖子内容与学习困惑进行追问', icon: SearchOutline },
];

const activeMenu = ref('summary');
const inputLink = ref('');
const summaryResult = ref('');
const summaryLoading = ref(false);
const targetPost = ref<PostVO | null>(null);

const activeMenuMeta = computed(() => menus.find((item) => item.id === activeMenu.value) || menus[0]);

async function generateSummary() {
  const link = inputLink.value.trim();
  if (!link) {
    message.warning('请输入帖子链接或 ID');
    return;
  }

  let postId: number | null = null;
  const match = link.match(/\/posts\/(\d+)/);

  if (match?.[1]) {
    postId = Number(match[1]);
  } else if (/^\d+$/.test(link)) {
    postId = Number(link);
  }

  if (!postId) {
    message.error('无法识别的帖子链接，请确保包含 /posts/{id}');
    return;
  }

  summaryLoading.value = true;
  summaryResult.value = '';
  targetPost.value = null;

  try {
    const post = await getPostById(postId);
    targetPost.value = post;

    window.setTimeout(() => {
      const title = post.title || '无标题帖子';
      const topics = post.topics?.length ? `涉及主题：${post.topics.join('、')}。` : '当前帖子未设置话题标签。';
      summaryResult.value = `1. 主题概览：这篇内容围绕“${title}”展开，核心在于分享作者的经验、观点或问题背景。\n2. 关键信息：${topics}\n3. 阅读建议：优先关注正文中最能支撑结论的案例、数据与讨论回复，再结合评论区判断社区共识与争议点。\n4. AI 结论：该内容适合进一步沉淀为笔记、问答或资源索引。`;
      summaryLoading.value = false;
      message.success('摘要生成成功');
    }, 1200);
  } catch {
    summaryLoading.value = false;
    message.error('获取帖子失败，请检查链接是否有效');
  }
}

async function copySummary() {
  if (!summaryResult.value) return;
  await navigator.clipboard.writeText(summaryResult.value);
  message.success('已复制到剪贴板');
}
</script>

<template>
  <div class="ai-page">
    <aside class="tool-sidebar cf-surface">
      <div class="sidebar-intro">
        <span class="cf-pill">AI Workspace</span>
        <h2>学习增强工具</h2>
        <p>延续 Stitch 提供的蓝白信息工作台风格，让 AI 功能更像一个安静、可信赖的学习控制台。</p>
      </div>

      <button
        v-for="item in menus"
        :key="item.id"
        class="tool-item"
        :class="{ active: activeMenu === item.id }"
        @click="activeMenu = item.id"
      >
        <div class="tool-icon">
          <n-icon size="18">
            <component :is="item.icon" />
          </n-icon>
        </div>
        <div>
          <strong>{{ item.label }}</strong>
          <span>{{ item.desc }}</span>
        </div>
      </button>
    </aside>

    <main class="workspace-column">
      <section class="workspace-header cf-surface">
        <div>
          <span class="cf-pill">{{ activeMenuMeta.label }}</span>
          <h1 class="cf-section-title">
            {{ activeMenuMeta.label }}
          </h1>
          <p class="cf-section-subtitle">
            {{ activeMenuMeta.desc }}
          </p>
        </div>
      </section>

      <section
        v-if="activeMenu === 'summary'"
        class="summary-grid"
      >
        <div class="input-panel cf-surface">
          <div class="panel-head">
            <h3>输入内容</h3>
            <p>支持粘贴系统内帖子链接，或直接输入帖子 ID。</p>
          </div>
          <div class="input-box">
            <div class="input-label">
              <n-icon size="16">
                <LinkOutline />
              </n-icon>
              帖子链接 / 帖子 ID
            </div>
            <input
              v-model="inputLink"
              class="cf-input"
              type="text"
              placeholder="例如：https://.../posts/123 或直接输入 123"
              @keyup.enter="generateSummary"
            />
          </div>
          <div class="action-row">
            <button
              class="cf-primary-btn"
              :disabled="summaryLoading"
              @click="generateSummary"
            >
              {{ summaryLoading ? 'AI 分析中...' : '开始提炼' }}
            </button>
          </div>
          <div class="hint-list">
            <div>· 优先提炼长文中最重要的观点、论据与结论。</div>
            <div>· 可将输出结果用于复习提纲、讨论要点或知识卡片。</div>
          </div>
        </div>

        <div class="result-panel cf-surface">
          <div class="panel-head with-actions">
            <div>
              <h3>分析结果</h3>
              <p>结构化摘要将以便于二次整理的形式展示。</p>
            </div>
            <div class="result-actions">
              <button
                class="result-icon-btn"
                :disabled="!summaryResult || summaryLoading"
                @click="copySummary"
              >
                <n-icon size="16"><CopyOutline /></n-icon>
              </button>
              <button
                class="result-icon-btn"
                :disabled="summaryLoading"
                @click="generateSummary"
              >
                <n-icon size="16"><RefreshOutline /></n-icon>
              </button>
            </div>
          </div>

          <div
            v-if="summaryLoading"
            class="loading-box"
          >
            <div class="pulse-dot" />
            <span>正在阅读帖子内容并提取核心脉络…</span>
          </div>

          <div
            v-else-if="summaryResult"
            class="result-content"
          >
            <div class="result-meta">
              <span>标题：{{ targetPost?.title || '无标题' }}</span>
              <span v-if="targetPost?.topics?.length">标签：{{ targetPost.topics.join(' / ') }}</span>
            </div>
            <pre>{{ summaryResult }}</pre>
          </div>

          <div
            v-else
            class="placeholder-box"
          >
            <div class="placeholder-icon">
              <n-icon size="20"><DocumentTextOutline /></n-icon>
            </div>
            <strong>等待生成摘要</strong>
            <p>输入帖子链接后，AI 会在这里输出结构化结论、重点和阅读建议。</p>
          </div>
        </div>
      </section>

      <section
        v-else
        class="coming-panel cf-surface"
      >
        <div class="placeholder-icon large">
          <n-icon size="26">
            <component :is="activeMenuMeta.icon" />
          </n-icon>
        </div>
        <h3>{{ activeMenuMeta.label }}</h3>
        <p>该模块将在后续页面替换中继续补齐，届时会沿用当前工作台风格统一设计交互。</p>
      </section>
    </main>
  </div>
</template>

<style scoped lang="scss">
.ai-page {
  display: grid;
  grid-template-columns: 280px minmax(0, 1fr);
  gap: 18px;
  perspective: 1200px;
}

.tool-sidebar,
.workspace-header,
.input-panel,
.result-panel,
.coming-panel {
  padding: 22px;
}

.sidebar-intro {
  margin-bottom: 18px;

  h2 {
    margin: 14px 0 10px;
    font-family: var(--cf-font-heading);
    font-size: 28px;
  }

  p {
    margin: 0;
    color: var(--cf-text-secondary);
    line-height: 1.7;
  }
}

.tool-item {
  width: 100%;
  border: 1px solid transparent;
  background: transparent;
  border-radius: 18px;
  padding: 16px;
  display: flex;
  align-items: flex-start;
  gap: 12px;
  text-align: left;
  cursor: pointer;
  transition: transform 0.24s var(--cf-motion-ease), background 0.24s ease, border-color 0.24s ease, box-shadow 0.24s ease;

  & + .tool-item {
    margin-top: 10px;
  }

  strong {
    display: block;
    margin-bottom: 4px;
    font-size: 15px;
  }

  span {
    color: var(--cf-text-muted);
    font-size: 13px;
    line-height: 1.6;
  }

  &:hover {
    transform: translate3d(4px, -2px, 0);
    background: var(--cf-bg-glass-soft);
    border-color: var(--cf-border);
    box-shadow: var(--cf-shadow-soft);
  }

  &.active {
    background: linear-gradient(180deg, var(--cf-primary-soft), var(--cf-bg-glass-soft));
    border-color: color-mix(in srgb, var(--cf-primary) 24%, var(--cf-border));
    box-shadow: inset 0 1px 0 var(--cf-surface-highlight), var(--cf-shadow-soft);
  }
}

.tool-icon {
  width: 40px;
  height: 40px;
  border-radius: 14px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--cf-bg-glass-soft);
  border: 1px solid var(--cf-border);
  box-shadow: inset 0 1px 0 var(--cf-surface-highlight);
  color: var(--cf-primary);
}

.workspace-column {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.summary-grid {
  display: grid;
  grid-template-columns: minmax(0, 0.95fr) minmax(0, 1.05fr);
  gap: 18px;
}

.panel-head {
  margin-bottom: 18px;

  h3 {
    margin: 0 0 8px;
    font-family: var(--cf-font-heading);
    font-size: 24px;
  }

  p {
    margin: 0;
    color: var(--cf-text-secondary);
  }
}

.with-actions {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: flex-start;
}

.input-label {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 10px;
  color: var(--cf-text-secondary);
  font-size: 13px;
  font-weight: 700;
}

.action-row {
  margin-top: 16px;
}

.hint-list {
  margin-top: 18px;
  color: var(--cf-text-muted);
  font-size: 13px;
  line-height: 1.8;
}

.result-actions {
  display: flex;
  gap: 8px;
}

.result-icon-btn {
  width: 38px;
  height: 38px;
  border-radius: 12px;
  border: 1px solid var(--cf-border);
  background: var(--cf-bg-glass-soft);
  color: var(--cf-text-secondary);
  cursor: pointer;
  box-shadow: inset 0 1px 0 var(--cf-surface-highlight);
  transition: transform 0.22s var(--cf-motion-ease), background 0.22s ease, color 0.22s ease;
}

.result-icon-btn:hover:not(:disabled) {
  transform: translateY(-2px);
  background: var(--cf-bg-glass);
  color: var(--cf-primary);
}

.loading-box,
.placeholder-box,
.coming-panel {
  min-height: 280px;
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
  text-align: center;
  color: var(--cf-text-secondary);
}

.loading-box {
  gap: 14px;
}

.pulse-dot {
  width: 14px;
  height: 14px;
  border-radius: 50%;
  background: var(--cf-primary);
  box-shadow: 0 0 0 0 color-mix(in srgb, var(--cf-primary) 42%, transparent);
  animation: pulse 1.2s infinite;
}

.result-content {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.result-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  color: var(--cf-text-muted);
  font-size: 13px;
}

pre {
  margin: 0;
  padding: 18px;
  border-radius: 18px;
  background: var(--cf-bg-readable);
  border: 1px solid var(--cf-border);
  box-shadow: inset 0 1px 0 var(--cf-surface-highlight);
  white-space: pre-wrap;
  word-break: break-word;
  color: var(--cf-text-primary);
  line-height: 1.85;
  font-family: var(--cf-font-body);
}

.placeholder-icon {
  width: 48px;
  height: 48px;
  border-radius: 16px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(180deg, var(--cf-primary-soft), var(--cf-bg-glass-soft));
  border: 1px solid color-mix(in srgb, var(--cf-primary) 22%, var(--cf-border));
  box-shadow: var(--cf-shadow-soft);
  color: var(--cf-primary);
  margin-bottom: 12px;

  &.large {
    width: 58px;
    height: 58px;
  }
}

.placeholder-box {
  p {
    max-width: 420px;
    margin: 8px 0 0;
    line-height: 1.8;
  }
}

.coming-panel {
  h3 {
    margin: 6px 0 8px;
    font-family: var(--cf-font-heading);
    font-size: 26px;
    color: var(--cf-text-primary);
  }

  p {
    max-width: 460px;
    margin: 0;
    line-height: 1.8;
  }
}

@keyframes pulse {
  0% {
    transform: scale(1);
    box-shadow: 0 0 0 0 color-mix(in srgb, var(--cf-primary) 42%, transparent);
  }
  70% {
    transform: scale(1.05);
    box-shadow: 0 0 0 14px color-mix(in srgb, var(--cf-primary) 0%, transparent);
  }
  100% {
    transform: scale(1);
    box-shadow: 0 0 0 0 color-mix(in srgb, var(--cf-primary) 0%, transparent);
  }
}

@media (max-width: 1024px) {
  .ai-page,
  .summary-grid {
    grid-template-columns: 1fr;
  }
}
</style>
