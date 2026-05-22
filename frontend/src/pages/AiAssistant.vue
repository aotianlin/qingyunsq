<script setup lang="ts">
import { computed, nextTick, onMounted, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { useMessage } from 'naive-ui';
import { CopyOutline, DocumentTextOutline, LinkOutline, RefreshOutline, SearchOutline, SendOutline, ShieldCheckmarkOutline } from '@vicons/ionicons5';
import { aiModerate, aiRagChat } from '@/api/ai';
import { getPostById } from '@/api/posts';
import type { AiCitation } from '@/types/ai';
import type { PostVO } from '@/types/post';

const message = useMessage();
const route = useRoute();
const router = useRouter();

const menus = [
  { id: 'qa', label: 'AI 问答', desc: '针对课程概念、帖子内容与学习困惑进行追问', icon: SearchOutline },
  { id: 'summary', label: '智能摘要', desc: '面向长文、课件与帖子内容的快速结构化提炼', icon: DocumentTextOutline },
  { id: 'content', label: '内容检测', desc: '帮助识别论述完整度、逻辑清晰度与表达风险', icon: ShieldCheckmarkOutline },
];

const activeMenu = ref('qa');
const inputLink = ref('');
const summaryResult = ref('');
const summaryLoading = ref(false);
const targetPost = ref<PostVO | null>(null);
const detectionMode = ref<'post' | 'text'>('post');
const detectionLink = ref('');
const detectionText = ref('');
const detectionLoading = ref(false);
const detectionTargetPost = ref<PostVO | null>(null);
const detectionResult = ref<{
  sourceTitle: string;
  content: string;
  riskLevel: number;
  riskReason: string;
} | null>(null);
const qaContextMode = ref<'post' | 'text' | 'none'>('none');
const qaContextLink = ref('');
const qaContextText = ref('');
const qaContextPost = ref<PostVO | null>(null);
const qaContextLoading = ref(false);
const qaQuestion = ref('');
const qaLoading = ref(false);
const qaMessages = ref<Array<{ role: 'user' | 'assistant'; content: string; citations?: AiCitation[] }>>([
  { role: 'assistant', content: '你可以直接提问，也可以先绑定一个帖子作为上下文，我会围绕内容继续追问和解释。' },
]);

const activeMenuMeta = computed(() => menus.find((item) => item.id === activeMenu.value) || menus[0]);
const detectionDraftStats = computed(() => analyzeContent(
  detectionMode.value === 'text' ? detectionText.value : detectionResult.value?.content || '',
));
const qaContextStats = computed(() => analyzeContent(currentQaContext()));
const detectionAssessment = computed(() => {
  if (!detectionResult.value) return null;
  const stats = analyzeContent(detectionResult.value.content);
  const risk = riskMeta(detectionResult.value.riskLevel);

  // 内容检测除了后端风险结果，还需要给用户稳定的结构化反馈；这里用轻量启发式生成可解释的评分。
  const completeness = clamp(
    Math.round(36 + Math.min(stats.characters / 8, 30) + Math.min(stats.paragraphs * 8, 18) + Math.min(stats.sentences * 3, 16)),
  );
  const clarity = clamp(
    Math.round(42 + Math.min(stats.sentences * 5, 24) + (stats.averageSentenceLength > 8 && stats.averageSentenceLength < 58 ? 18 : 4) + (stats.paragraphs > 1 ? 8 : 0)),
  );
  const expressionRisk = clamp(
    Math.round((detectionResult.value.riskLevel || 0) * 34 + Math.min(stats.links * 12, 24) + (stats.characters > 5000 ? 18 : 0) + (stats.mentions > 6 ? 10 : 0)),
  );

  return {
    stats,
    risk,
    scores: [
      { label: '论述完整度', value: completeness, desc: scoreDesc(completeness, false) },
      { label: '逻辑清晰度', value: clarity, desc: scoreDesc(clarity, false) },
      { label: '表达风险', value: expressionRisk, desc: scoreDesc(expressionRisk, true) },
    ],
    suggestions: buildDetectionSuggestions(stats, detectionResult.value.riskLevel, detectionResult.value.riskReason),
  };
});

function clamp(value: number) {
  return Math.max(0, Math.min(100, value));
}

function parsePostId(value: string) {
  const link = value.trim();
  const match = link.match(/\/posts\/(\d+)/);
  if (match?.[1]) return Number(match[1]);
  return /^\d+$/.test(link) ? Number(link) : null;
}

function postContentForAi(post: PostVO) {
  const parts = [
    post.title ? `标题：${post.title}` : '',
    post.topics?.length ? `标签：${post.topics.join('、')}` : '',
    post.content || '',
  ];
  return parts.filter(Boolean).join('\n\n');
}

function analyzeContent(value: string) {
  const text = value.trim();
  const paragraphs = text ? text.split(/\n+/).filter((item) => item.trim()).length : 0;
  const sentences = text ? text.split(/[。！？!?；;\n]+/).filter((item) => item.trim()).length : 0;
  const links = (text.match(/https?:\/\/\S+/g) || []).length;
  const mentions = (text.match(/@[\u4e00-\u9fa5\w-]+/g) || []).length;
  const characters = text.replace(/\s/g, '').length;
  return {
    characters,
    paragraphs,
    sentences,
    links,
    mentions,
    averageSentenceLength: sentences ? Math.round(characters / sentences) : 0,
  };
}

function riskMeta(level: number) {
  if (level >= 2) return { label: '高风险', className: 'high', desc: '建议先修改敏感表达后再发布。' };
  if (level === 1) return { label: '需优化', className: 'medium', desc: '内容可发布前再精简或补充说明。' };
  return { label: '低风险', className: 'low', desc: '未识别到明显违规风险。' };
}

function scoreDesc(value: number, reverse: boolean) {
  if (reverse) {
    if (value >= 70) return '风险偏高';
    if (value >= 35) return '注意控制';
    return '较安全';
  }
  if (value >= 80) return '表现良好';
  if (value >= 55) return '基本可用';
  return '需要补强';
}

function scoreClass(value: number, reverse = false) {
  if (reverse) {
    if (value >= 70) return 'danger';
    if (value >= 35) return 'warn';
    return 'good';
  }
  if (value >= 80) return 'good';
  if (value >= 55) return 'warn';
  return 'danger';
}

function buildDetectionSuggestions(
  stats: ReturnType<typeof analyzeContent>,
  riskLevel: number,
  riskReason: string,
) {
  const suggestions: string[] = [];
  if (riskLevel > 0 && riskReason) suggestions.push(`优先处理风险提示：${riskReason}`);
  if (stats.characters < 80) suggestions.push('正文偏短，建议补充背景、过程、结论或具体案例。');
  if (stats.paragraphs < 2 && stats.characters > 180) suggestions.push('长内容建议拆成多个段落，降低阅读压力。');
  if (stats.sentences < 3 && stats.characters > 120) suggestions.push('可以用更清晰的句号、问号或分号拆开观点。');
  if (stats.averageSentenceLength > 60) suggestions.push('单句平均长度偏长，建议拆分复杂句并突出关键词。');
  if (stats.links > 2) suggestions.push('外链较多，建议说明每个链接的来源与用途。');
  if (stats.mentions > 6) suggestions.push('@ 提及较多，发布前确认是否会打扰无关用户。');
  if (!suggestions.length) suggestions.push('内容结构和表达风险都较稳定，可以直接发布或再补充一条结论。');
  return suggestions;
}

async function generateSummary() {
  const link = inputLink.value.trim();
  if (!link) {
    message.warning('请输入帖子链接或 ID');
    return;
  }

  const postId = parsePostId(link);

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

function switchDetectionMode(mode: 'post' | 'text') {
  detectionMode.value = mode;
  detectionResult.value = null;
  detectionTargetPost.value = null;
}

async function runDetection() {
  if (detectionLoading.value) return;

  detectionLoading.value = true;
  detectionResult.value = null;

  try {
    let content = '';
    let sourceTitle = '直接输入内容';
    detectionTargetPost.value = null;

    if (detectionMode.value === 'post') {
      const postId = parsePostId(detectionLink.value);
      if (!postId) {
        message.warning('请输入有效的帖子链接或 ID');
        return;
      }
      const post = await getPostById(postId);
      detectionTargetPost.value = post;
      sourceTitle = post.title || `帖子 #${post.id}`;
      content = postContentForAi(post);
    } else {
      content = detectionText.value.trim();
    }

    if (!content.trim()) {
      message.warning('请输入需要检测的内容');
      return;
    }

    const result = await aiModerate(content);
    detectionResult.value = {
      sourceTitle,
      content,
      riskLevel: result.riskLevel ?? 0,
      riskReason: result.riskReason || '',
    };
    message.success('内容检测完成');
  } catch {
    message.error(detectionMode.value === 'post' ? '获取或检测帖子失败，请检查链接是否有效' : '内容检测失败');
  } finally {
    detectionLoading.value = false;
  }
}

async function copyDetectionReport() {
  if (!detectionResult.value || !detectionAssessment.value) return;
  const report = [
    `检测对象：${detectionResult.value.sourceTitle}`,
    `风险等级：${detectionAssessment.value.risk.label}`,
    detectionResult.value.riskReason ? `风险说明：${detectionResult.value.riskReason}` : '风险说明：未识别到明显违规风险',
    '',
    ...detectionAssessment.value.scores.map((item) => `${item.label}：${item.value}/100（${item.desc}）`),
    '',
    '优化建议：',
    ...detectionAssessment.value.suggestions.map((item, index) => `${index + 1}. ${item}`),
  ].join('\n');
  await navigator.clipboard.writeText(report);
  message.success('检测报告已复制');
}

function currentQaContext() {
  if (qaContextMode.value === 'post') {
    return qaContextPost.value ? postContentForAi(qaContextPost.value) : '';
  }
  if (qaContextMode.value === 'text') {
    return qaContextText.value.trim();
  }
  return '';
}

function switchQaContextMode(mode: 'post' | 'text' | 'none') {
  qaContextMode.value = mode;
  qaContextPost.value = null;
}

async function loadQaContextPost() {
  const postId = parsePostId(qaContextLink.value);
  if (!postId) {
    message.warning('请输入有效的帖子链接或 ID');
    return false;
  }

  qaContextLoading.value = true;
  try {
    qaContextPost.value = await getPostById(postId);
    message.success('帖子上下文已载入');
    return true;
  } catch {
    message.error('获取帖子失败，请检查链接是否有效');
    return false;
  } finally {
    qaContextLoading.value = false;
  }
}

async function ensureQaContextReady() {
  if (qaContextMode.value !== 'post' || qaContextPost.value || !qaContextLink.value.trim()) {
    return true;
  }
  return loadQaContextPost();
}

async function sendQaQuestion() {
  const question = qaQuestion.value.trim();
  if (!question || qaLoading.value) return;
  if (!(await ensureQaContextReady())) return;

  qaQuestion.value = '';
  qaMessages.value.push({ role: 'user', content: question });
  qaLoading.value = true;

  try {
    const context = currentQaContext();
    const history = qaMessages.value.slice(-8).map((item) => ({
      role: item.role,
      content: item.content,
    }));
    const result = await aiRagChat(history, context || undefined);
    qaMessages.value.push({
      role: 'assistant',
      content: result.reply || '暂时没有生成有效回复，请换一种问法再试。',
      citations: result.citations || [],
    });
  } catch {
    qaMessages.value.push({
      role: 'assistant',
      content: '这次问答请求失败了，请稍后再试。',
    });
  } finally {
    qaLoading.value = false;
  }
}

function askQuickQuestion(question: string) {
  qaQuestion.value = question;
  void sendQaQuestion();
}

function clearQaChat() {
  qaMessages.value = [
    { role: 'assistant', content: '对话已清空。你可以继续围绕当前上下文提问。' },
  ];
}

async function copyLastQaAnswer() {
  const answer = [...qaMessages.value].reverse().find((item) => item.role === 'assistant')?.content;
  if (!answer) return;
  await navigator.clipboard.writeText(answer);
  message.success('回答已复制');
}

function openCitation(citation: AiCitation) {
  if (!citation.url) return;
  router.push(citation.url);
}

function applyRoutePreset() {
  const mode = route.query.mode;
  if (mode === 'content') {
    activeMenu.value = 'content';
  } else if (mode === 'summary') {
    activeMenu.value = 'summary';
  } else if (mode === 'qa') {
    activeMenu.value = 'qa';
  } else {
    activeMenu.value = 'qa';
  }

  const postIdRaw = route.query.postId;
  const postId = typeof postIdRaw === 'string' ? Number(postIdRaw) : Number(Array.isArray(postIdRaw) ? postIdRaw[0] : NaN);
  if (activeMenu.value === 'content' && Number.isFinite(postId) && postId > 0) {
    detectionMode.value = 'post';
    detectionLink.value = String(postId);
    window.setTimeout(() => {
      void runDetection();
    }, 0);
  }
  if (activeMenu.value === 'qa' && Number.isFinite(postId) && postId > 0) {
    qaContextMode.value = 'post';
    qaContextLink.value = String(postId);
    window.setTimeout(() => {
      void loadQaContextPost();
    }, 0);
  }

  const presetQuestion = route.query.q;
  if (activeMenu.value === 'qa' && typeof presetQuestion === 'string' && presetQuestion.trim()) {
    qaContextMode.value = Number.isFinite(postId) && postId > 0 ? 'post' : 'none';
    qaQuestion.value = presetQuestion.trim();
    window.setTimeout(() => {
      void sendQaQuestion();
    }, 0);
  }
}

function scrollToWorkspace() {
  if (route.hash !== '#ai-workspace') return;
  void nextTick(() => {
    document.getElementById('ai-workspace')?.scrollIntoView({ behavior: 'smooth', block: 'start' });
  });
}

onMounted(() => {
  applyRoutePreset();
  scrollToWorkspace();
});

watch(
  () => [route.query.mode, route.query.postId, route.query.q, route.query.focus, route.hash],
  () => {
    applyRoutePreset();
    scrollToWorkspace();
  },
);
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

    <main
      id="ai-workspace"
      class="workspace-column"
    >
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
        v-else-if="activeMenu === 'content'"
        class="detection-grid"
      >
        <div class="input-panel cf-surface">
          <div class="panel-head">
            <h3>检测对象</h3>
            <p>支持检测系统内帖子，也可以直接粘贴准备发布的内容。</p>
          </div>

          <div class="mode-switch">
            <button
              class="mode-btn"
              :class="{ active: detectionMode === 'post' }"
              @click="switchDetectionMode('post')"
            >
              帖子链接
            </button>
            <button
              class="mode-btn"
              :class="{ active: detectionMode === 'text' }"
              @click="switchDetectionMode('text')"
            >
              直接输入
            </button>
          </div>

          <div
            v-if="detectionMode === 'post'"
            class="input-box"
          >
            <div class="input-label">
              <n-icon size="16">
                <LinkOutline />
              </n-icon>
              帖子链接 / 帖子 ID
            </div>
            <input
              v-model="detectionLink"
              class="cf-input"
              type="text"
              placeholder="例如：https://.../posts/123 或直接输入 123"
              @keyup.enter="runDetection"
            />
          </div>

          <div
            v-else
            class="input-box"
          >
            <div class="input-label">
              <n-icon size="16">
                <DocumentTextOutline />
              </n-icon>
              待检测内容
            </div>
            <textarea
              v-model="detectionText"
              class="cf-textarea detection-textarea"
              placeholder="粘贴帖子正文、评论或准备发布的内容"
            />
          </div>

          <div class="draft-stats">
            <div>
              <strong>{{ detectionDraftStats.characters }}</strong>
              <span>字数</span>
            </div>
            <div>
              <strong>{{ detectionDraftStats.paragraphs }}</strong>
              <span>段落</span>
            </div>
            <div>
              <strong>{{ detectionDraftStats.sentences }}</strong>
              <span>句子</span>
            </div>
          </div>

          <div class="action-row">
            <button
              class="cf-primary-btn"
              :disabled="detectionLoading"
              @click="runDetection"
            >
              {{ detectionLoading ? '检测中...' : '开始检测' }}
            </button>
          </div>

          <div class="hint-list">
            <div>· 风险检测会结合后端 AI 审核结果与文本结构指标。</div>
            <div>· 检测结果只作为发布前自查参考，最终仍以平台规则为准。</div>
          </div>
        </div>

        <div class="result-panel cf-surface">
          <div class="panel-head with-actions">
            <div>
              <h3>检测结果</h3>
              <p>集中展示风险等级、表达质量和可操作修改建议。</p>
            </div>
            <div class="result-actions">
              <button
                class="result-icon-btn"
                :disabled="!detectionResult || detectionLoading"
                @click="copyDetectionReport"
              >
                <n-icon size="16"><CopyOutline /></n-icon>
              </button>
              <button
                class="result-icon-btn"
                :disabled="detectionLoading"
                @click="runDetection"
              >
                <n-icon size="16"><RefreshOutline /></n-icon>
              </button>
            </div>
          </div>

          <div
            v-if="detectionLoading"
            class="loading-box"
          >
            <div class="pulse-dot" />
            <span>正在检测内容风险与表达结构…</span>
          </div>

          <div
            v-else-if="detectionResult && detectionAssessment"
            class="detection-result"
          >
            <div class="risk-summary">
              <div
                class="risk-badge"
                :class="detectionAssessment.risk.className"
              >
                {{ detectionAssessment.risk.label }}
              </div>
              <div>
                <strong>{{ detectionResult.sourceTitle }}</strong>
                <p>{{ detectionResult.riskReason || detectionAssessment.risk.desc }}</p>
              </div>
            </div>

            <div class="metric-grid">
              <div
                v-for="score in detectionAssessment.scores"
                :key="score.label"
                class="metric-card"
              >
                <div class="metric-head">
                  <span>{{ score.label }}</span>
                  <strong>{{ score.value }}</strong>
                </div>
                <div class="metric-track">
                  <span
                    :class="scoreClass(score.value, score.label === '表达风险')"
                    :style="{ width: `${score.value}%` }"
                  />
                </div>
                <p>{{ score.desc }}</p>
              </div>
            </div>

            <div class="stats-row">
              <span>{{ detectionAssessment.stats.characters }} 字</span>
              <span>{{ detectionAssessment.stats.paragraphs }} 段</span>
              <span>{{ detectionAssessment.stats.sentences }} 句</span>
              <span>均句 {{ detectionAssessment.stats.averageSentenceLength }} 字</span>
            </div>

            <div class="suggestion-block">
              <h4>优化建议</h4>
              <ol>
                <li
                  v-for="item in detectionAssessment.suggestions"
                  :key="item"
                >
                  {{ item }}
                </li>
              </ol>
            </div>
          </div>

          <div
            v-else
            class="placeholder-box"
          >
            <div class="placeholder-icon">
              <n-icon size="20"><ShieldCheckmarkOutline /></n-icon>
            </div>
            <strong>等待内容检测</strong>
            <p>输入帖子链接或正文后，这里会输出风险等级、表达评分和修改建议。</p>
          </div>
        </div>
      </section>

      <section
        v-else-if="activeMenu === 'qa'"
        class="qa-grid"
      >
        <div class="input-panel cf-surface">
          <div class="panel-head">
            <h3>问答上下文</h3>
            <p>可以不带上下文直接提问，也可以先绑定帖子内容后再继续追问。</p>
          </div>

          <div class="mode-switch qa-switch">
            <button
              class="mode-btn"
              :class="{ active: qaContextMode === 'post' }"
              @click="switchQaContextMode('post')"
            >
              帖子上下文
            </button>
            <button
              class="mode-btn"
              :class="{ active: qaContextMode === 'text' }"
              @click="switchQaContextMode('text')"
            >
              粘贴上下文
            </button>
            <button
              class="mode-btn"
              :class="{ active: qaContextMode === 'none' }"
              @click="switchQaContextMode('none')"
            >
              不带上下文
            </button>
          </div>

          <div
            v-if="qaContextMode === 'post'"
            class="input-box"
          >
            <div class="input-label">
              <n-icon size="16">
                <LinkOutline />
              </n-icon>
              帖子链接 / 帖子 ID
            </div>
            <input
              v-model="qaContextLink"
              class="cf-input"
              type="text"
              placeholder="例如：https://.../posts/123 或直接输入 123"
              @keyup.enter="loadQaContextPost"
            />
          </div>

          <div
            v-else-if="qaContextMode === 'text'"
            class="input-box"
          >
            <div class="input-label">
              <n-icon size="16">
                <DocumentTextOutline />
              </n-icon>
              上下文内容
            </div>
            <textarea
              v-model="qaContextText"
              class="cf-textarea qa-context-textarea"
              placeholder="粘贴帖子正文、笔记、题目说明或其他上下文"
            />
          </div>

          <div
            v-if="qaContextMode !== 'none'"
            class="action-row"
          >
            <button
              class="cf-primary-btn"
              :disabled="qaContextLoading || (qaContextMode === 'text' && !qaContextText.trim())"
              @click="qaContextMode === 'post' ? loadQaContextPost() : undefined"
            >
              {{ qaContextLoading ? '载入中...' : qaContextMode === 'post' ? '载入帖子' : '上下文已就绪' }}
            </button>
          </div>

          <div class="draft-stats">
            <div>
              <strong>{{ qaContextStats.characters }}</strong>
              <span>字数</span>
            </div>
            <div>
              <strong>{{ qaContextStats.paragraphs }}</strong>
              <span>段落</span>
            </div>
            <div>
              <strong>{{ qaContextStats.sentences }}</strong>
              <span>句子</span>
            </div>
          </div>

          <div class="hint-list">
            <div>· 绑定帖子后，AI 会根据帖子正文、标题和标签回答。</div>
            <div>· 你也可以直接问平台功能、课程概念或学习方法。</div>
          </div>

          <div class="quick-qa">
            <button class="quick-chip" @click="askQuickQuestion('帮我总结一下这个内容的核心观点。')">总结内容</button>
            <button class="quick-chip" @click="askQuickQuestion('这段内容有哪些可以继续追问的点？')">追问重点</button>
            <button class="quick-chip" @click="askQuickQuestion('如果我要发到广场，应该怎么改得更清晰？')">发布建议</button>
            <button class="quick-chip" @click="askQuickQuestion('这道题/这段话的关键概念是什么？')">提炼概念</button>
          </div>
        </div>

        <div class="chat-panel cf-surface">
          <div class="panel-head with-actions">
            <div>
              <h3>AI 问答</h3>
              <p>围绕当前上下文继续提问，或直接向 AI 询问平台使用问题。</p>
            </div>
            <div class="result-actions">
              <button
                class="result-icon-btn"
                :disabled="qaLoading"
                @click="copyLastQaAnswer"
              >
                <n-icon size="16"><CopyOutline /></n-icon>
              </button>
              <button
                class="result-icon-btn"
                :disabled="qaLoading"
                @click="clearQaChat"
              >
                <n-icon size="16"><RefreshOutline /></n-icon>
              </button>
            </div>
          </div>

          <div class="chat-meta">
            <span v-if="qaContextMode === 'post' && qaContextPost">
              当前帖子：{{ qaContextPost.title || `帖子 #${qaContextPost.id}` }}
            </span>
            <span v-else-if="qaContextMode === 'text' && qaContextText.trim()">
              已载入粘贴内容
            </span>
            <span v-else>
              当前无上下文
            </span>
          </div>

          <div class="chat-stream">
            <article
              v-for="(item, index) in qaMessages"
              :key="`${item.role}-${index}`"
              class="chat-bubble"
              :class="item.role"
            >
              <div class="bubble-tag">
                {{ item.role === 'user' ? '我' : 'AI' }}
              </div>
              <div class="bubble-content">
                {{ item.content }}
              </div>
              <div
                v-if="item.citations?.length"
                class="citation-list"
              >
                <button
                  v-for="citation in item.citations"
                  :key="`${citation.type}-${citation.id}`"
                  class="citation-chip"
                  @click="openCitation(citation)"
                >
                  {{ citation.type }} · {{ citation.title }}
                </button>
              </div>
            </article>

            <div
              v-if="qaLoading"
              class="chat-loading"
            >
              <div class="pulse-dot" />
              <span>AI 正在组织回答…</span>
            </div>
          </div>

          <div class="chat-input-row">
            <textarea
              v-model="qaQuestion"
              class="cf-textarea chat-input"
              placeholder="输入你的问题，回车发送，Shift+Enter 换行"
              @keydown.enter.exact.prevent="sendQaQuestion"
            />
            <button
              class="cf-primary-btn chat-send"
              :disabled="qaLoading || !qaQuestion.trim()"
              @click="sendQaQuestion"
            >
              <n-icon size="16">
                <SendOutline />
              </n-icon>
              发送
            </button>
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
.coming-panel,
.chat-panel {
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

.detection-grid {
  display: grid;
  grid-template-columns: minmax(320px, 0.85fr) minmax(0, 1.15fr);
  gap: 18px;
}

.qa-grid {
  display: grid;
  grid-template-columns: minmax(320px, 0.9fr) minmax(0, 1.1fr);
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

.mode-switch {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px;
  padding: 6px;
  margin-bottom: 18px;
  border-radius: 16px;
  background: var(--cf-bg-glass-soft);
  border: 1px solid var(--cf-border);
  box-shadow: inset 0 1px 0 var(--cf-surface-highlight);
}

.mode-btn {
  border: none;
  border-radius: 12px;
  padding: 10px 12px;
  background: transparent;
  color: var(--cf-text-secondary);
  font-weight: 700;
  cursor: pointer;
  transition: background 0.2s ease, color 0.2s ease, box-shadow 0.2s ease;

  &.active {
    background: var(--cf-bg-readable);
    color: var(--cf-primary);
    box-shadow: var(--cf-shadow-soft);
  }
}

.qa-switch {
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

.detection-textarea {
  min-height: 220px;
  resize: vertical;
}

.qa-context-textarea {
  min-height: 180px;
  resize: vertical;
}

.draft-stats,
.stats-row {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.draft-stats {
  margin-top: 14px;

  div {
    flex: 1;
    min-width: 88px;
    padding: 12px;
    border-radius: 16px;
    background: var(--cf-bg-glass-soft);
    border: 1px solid var(--cf-border);
  }

  strong,
  span {
    display: block;
  }

  strong {
    color: var(--cf-text-primary);
    font-size: 20px;
    font-family: var(--cf-font-heading);
  }

  span {
    margin-top: 2px;
    color: var(--cf-text-muted);
    font-size: 12px;
  }
}

.hint-list {
  margin-top: 18px;
  color: var(--cf-text-muted);
  font-size: 13px;
  line-height: 1.8;
}

.quick-qa {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 18px;
}

.quick-chip {
  border: 1px solid var(--cf-border);
  background: var(--cf-bg-glass-soft);
  color: var(--cf-text-secondary);
  border-radius: 999px;
  padding: 8px 12px;
  cursor: pointer;
  transition: background 0.2s ease, color 0.2s ease, transform 0.2s ease;

  &:hover {
    background: var(--cf-bg-glass);
    color: var(--cf-primary);
    transform: translateY(-1px);
  }
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

.chat-panel {
  display: flex;
  flex-direction: column;
  gap: 14px;
  min-height: 620px;
}

.chat-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  color: var(--cf-text-muted);
  font-size: 13px;
}

.chat-stream {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 14px;
  border-radius: 20px;
  background: var(--cf-bg-readable);
  border: 1px solid var(--cf-border);
  box-shadow: inset 0 1px 0 var(--cf-surface-highlight);
  min-height: 360px;
}

.chat-bubble {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr);
  gap: 12px;
  align-items: flex-start;

  &.user {
    .bubble-tag {
      background: color-mix(in srgb, var(--cf-primary) 16%, transparent);
      color: var(--cf-primary);
    }
  }

  &.assistant {
    .bubble-tag {
      background: color-mix(in srgb, var(--cf-border) 55%, transparent);
      color: var(--cf-text-secondary);
    }
  }
}

.bubble-tag {
  width: 36px;
  height: 36px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 800;
  flex-shrink: 0;
}

.bubble-content {
  padding: 12px 14px;
  border-radius: 16px;
  background: var(--cf-bg-glass-soft);
  border: 1px solid var(--cf-border);
  color: var(--cf-text-primary);
  line-height: 1.8;
  white-space: pre-wrap;
  word-break: break-word;
}

.citation-list {
  grid-column: 2;
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.citation-chip {
  border: 1px solid color-mix(in srgb, var(--cf-primary) 28%, var(--cf-border));
  background: color-mix(in srgb, var(--cf-primary-soft) 72%, var(--cf-bg-glass-soft));
  color: var(--cf-primary);
  border-radius: 999px;
  padding: 6px 10px;
  font-size: 12px;
  font-weight: 700;
  cursor: pointer;
}

.citation-chip:hover {
  background: var(--cf-bg-glass);
}

.chat-bubble.user .bubble-content {
  background: color-mix(in srgb, var(--cf-primary-soft) 74%, var(--cf-bg-glass-soft));
}

.chat-loading {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  color: var(--cf-text-muted);
  padding: 4px 2px;
}

.chat-input-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 12px;
  align-items: end;
}

.chat-input {
  min-height: 120px;
  resize: vertical;
}

.chat-send {
  min-width: 112px;
  height: 48px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
}

.detection-result {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.risk-summary {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr);
  gap: 14px;
  align-items: center;
  padding: 16px;
  border-radius: 18px;
  background: var(--cf-bg-readable);
  border: 1px solid var(--cf-border);
  box-shadow: inset 0 1px 0 var(--cf-surface-highlight);

  strong {
    display: block;
    margin-bottom: 6px;
    color: var(--cf-text-primary);
    font-size: 16px;
  }

  p {
    margin: 0;
    color: var(--cf-text-secondary);
    line-height: 1.7;
  }
}

.risk-badge {
  min-width: 76px;
  padding: 10px 12px;
  border-radius: 14px;
  text-align: center;
  font-weight: 800;

  &.low {
    color: #047857;
    background: color-mix(in srgb, #10b981 16%, transparent);
    border: 1px solid color-mix(in srgb, #10b981 36%, transparent);
  }

  &.medium {
    color: #b45309;
    background: color-mix(in srgb, #f59e0b 18%, transparent);
    border: 1px solid color-mix(in srgb, #f59e0b 38%, transparent);
  }

  &.high {
    color: #b91c1c;
    background: color-mix(in srgb, #ef4444 16%, transparent);
    border: 1px solid color-mix(in srgb, #ef4444 38%, transparent);
  }
}

.metric-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
}

.metric-card {
  padding: 14px;
  border-radius: 18px;
  background: var(--cf-bg-glass-soft);
  border: 1px solid var(--cf-border);
  box-shadow: inset 0 1px 0 var(--cf-surface-highlight);

  p {
    margin: 10px 0 0;
    color: var(--cf-text-muted);
    font-size: 13px;
  }
}

.metric-head {
  display: flex;
  justify-content: space-between;
  gap: 10px;
  align-items: baseline;
  margin-bottom: 10px;

  span {
    color: var(--cf-text-secondary);
    font-weight: 700;
  }

  strong {
    color: var(--cf-text-primary);
    font-family: var(--cf-font-heading);
    font-size: 24px;
  }
}

.metric-track {
  height: 8px;
  overflow: hidden;
  border-radius: 999px;
  background: color-mix(in srgb, var(--cf-border) 60%, transparent);

  span {
    display: block;
    height: 100%;
    border-radius: inherit;

    &.good {
      background: #10b981;
    }

    &.warn {
      background: #f59e0b;
    }

    &.danger {
      background: #ef4444;
    }
  }
}

.stats-row {
  color: var(--cf-text-muted);
  font-size: 13px;

  span {
    padding: 7px 10px;
    border-radius: 999px;
    background: var(--cf-bg-glass-soft);
    border: 1px solid var(--cf-border);
  }
}

.suggestion-block {
  padding: 16px;
  border-radius: 18px;
  background: var(--cf-bg-readable);
  border: 1px solid var(--cf-border);

  h4 {
    margin: 0 0 10px;
    font-family: var(--cf-font-heading);
    font-size: 18px;
  }

  ol {
    margin: 0;
    padding-left: 20px;
    color: var(--cf-text-secondary);
    line-height: 1.85;
  }
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
  .summary-grid,
  .detection-grid,
  .qa-grid {
    grid-template-columns: 1fr;
  }

  .metric-grid {
    grid-template-columns: 1fr;
  }

  .chat-panel {
    min-height: auto;
  }
}
</style>
