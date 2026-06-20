<script setup lang="ts">
import { computed, nextTick, onMounted, ref, watch } from 'vue';
import type { Component } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { NIcon, useMessage } from 'naive-ui';
import {
  AddOutline,
  ArrowForwardOutline,
  AttachOutline,
  BarChartOutline,
  BookOutline,
  BookmarkOutline,
  BriefcaseOutline,
  ChatbubbleEllipsesOutline,
  CheckmarkCircleOutline,
  ChevronForwardOutline,
  CloudUploadOutline,
  CodeSlashOutline,
  CopyOutline,
  DocumentTextOutline,
  EarthOutline,
  EyeOutline,
  FolderOutline,
  HelpCircleOutline,
  HomeOutline,
  LibraryOutline,
  NotificationsOutline,
  PaperPlaneOutline,
  RefreshOutline,
  SearchOutline,
  SendOutline,
  SettingsOutline,
  ShareSocialOutline,
  SparklesOutline,
  TimeOutline,
  TrashOutline,
} from '@vicons/ionicons5';
import { aiChat, aiRagChat } from '@/api/ai';
import { copyTextToClipboard } from '@/utils/clipboard';
import type { AiCitation } from '@/types/ai';

type ChatRole = 'user' | 'assistant';
type AiPageMode = 'discover' | 'workspace' | 'wiki';
type KnowledgeCategory = '产品文档' | '技术文档' | '学习资料' | '公司制度' | '市场与销售';

type ChatMessage = {
  role: ChatRole;
  content: string;
  time: string;
  citations?: AiCitation[];
};

type Conversation = {
  id: string;
  title: string;
  updatedAt: number;
  favorite: boolean;
  messages: ChatMessage[];
  knowledgeBaseId?: string;
};

type AiModel = {
  id: string;
  name: string;
  provider: string;
};

type KnowledgeBase = {
  id: string;
  name: string;
  desc: string;
  category: KnowledgeCategory;
  type: string;
  docs: number;
  vectors: number;
  updatedAt: string;
  owner: '我创建的' | '共享给我的';
  favorite: boolean;
  color: string;
};

type ShellNavItem = {
  key: string;
  label: string;
  icon: Component;
  path?: string;
  active: boolean;
  action?: () => void;
};

type RecommendationCard = {
  id: string;
  title: string;
  meta: string;
  views: string;
  icon: Component;
  color: string;
  prompt: string;
};

type TopicItem = {
  id: string;
  title: string;
  desc: string;
  stats: string;
  author: string;
  icon: Component;
  color: string;
};

type KnowledgeDocumentItem = {
  id: string;
  title: string;
  kind: '笔记' | '模板' | 'PDF' | '文件夹';
  meta: string;
  icon: Component;
};

const CHAT_STORAGE_KEY = 'campus-ai-conversations';
const MODEL_KEY = 'campus-ai-model';
const KNOWLEDGE_STORAGE_KEY = 'campus-ai-knowledge-bases';

const route = useRoute();
const router = useRouter();
const message = useMessage();

const pageSearch = ref('');
const draft = ref('');
const loading = ref(false);
const webSearchEnabled = ref(true);
const conversations = ref<Conversation[]>([]);
const currentConversationId = ref('');
const attachedContexts = ref<{ name: string; content: string }[]>([]);
const chatStreamRef = ref<HTMLElement | null>(null);
const fileInputRef = ref<HTMLInputElement | null>(null);
const selectedDocumentId = ref('readme');
const recommendationOffset = ref(0);
const selectedModel = ref('deepseek-v4-flash');
const createKnowledgeVisible = ref(false);
const importKnowledgeVisible = ref(false);
const knowledgeDraft = ref({ name: '', category: '学习资料' as KnowledgeCategory, desc: '' });
const importDraft = ref({ targetId: '', files: '', tags: '' });

const modelOptions: AiModel[] = [
  { id: 'deepseek-v4-flash', name: 'DeepSeek V4 Flash', provider: 'DeepSeek' },
  { id: 'deepseek-v4-pro', name: 'DeepSeek V4 Pro', provider: 'DeepSeek' },
  { id: 'mimo-v2.5', name: 'MiMo 2.5', provider: 'MiMo' },
];

const defaultKnowledgeBases: KnowledgeBase[] = [
  {
    id: 'mathhub-calculus',
    name: '微积分（高等数学/数学分析）每日一题',
    desc: '系统收集的高等微积分问题，每日更新，用于严格的数学训练。',
    category: '学习资料',
    type: '数学题库',
    docs: 2247,
    vectors: 58333,
    updatedAt: '6/5',
    owner: '我创建的',
    favorite: true,
    color: '#007a52',
  },
  {
    id: 'marketing-q3',
    name: '第三季度营销策略',
    desc: '第三季度的综合分析、竞争对手研究和活动规划文档。',
    category: '市场与销售',
    type: '营销资料',
    docs: 186,
    vectors: 43890,
    updatedAt: '刚刚',
    owner: '我创建的',
    favorite: false,
    color: '#10b981',
  },
  {
    id: 'frontend-engineering',
    name: '前端工程中心',
    desc: '架构指南、组件库和代码审查标准。',
    category: '技术文档',
    type: '工程文档',
    docs: 96,
    vectors: 29041,
    updatedAt: '2小时前',
    owner: '我创建的',
    favorite: false,
    color: '#2563eb',
  },
  {
    id: 'product-help',
    name: '产品帮助文档',
    desc: '包含产品使用说明、功能介绍、常见问题等。',
    category: '产品文档',
    type: '产品文档',
    docs: 156,
    vectors: 458642,
    updatedAt: '5/20',
    owner: '共享给我的',
    favorite: true,
    color: '#16a34a',
  },
];

const knowledgeBases = ref<KnowledgeBase[]>([]);

const featuredCards: RecommendationCard[] = [
  {
    id: 'compute-era',
    title: '算力时代光纤架构与产业重塑',
    meta: '报告',
    views: '4.9k',
    icon: BarChartOutline,
    color: '#eaf2ff',
    prompt: '请总结算力时代光纤架构与产业重塑的关键趋势。',
  },
  {
    id: 'chip-storage',
    title: '存储芯片进入超级周期',
    meta: '演示',
    views: '6.4k',
    icon: LibraryOutline,
    color: '#ccefe8',
    prompt: '请解释存储芯片进入超级周期的产业原因。',
  },
  {
    id: 'low-altitude',
    title: '低空经济解锁新万亿市场',
    meta: '文章',
    views: '5.1k',
    icon: DocumentTextOutline,
    color: '#f1f3f5',
    prompt: '请梳理低空经济的主要商业模式和风险。',
  },
  {
    id: 'ai-career',
    title: 'AI 新职业生存指南',
    meta: '指南',
    views: '11k',
    icon: BriefcaseOutline,
    color: '#ebe7fb',
    prompt: '请给我一份 AI 新职业学习路线和能力清单。',
  },
];

const topicTabs = ['热门话题', '科技', '教育', '职场', '金融', '行业', '健康', '法律', '人文'];
const activeTopicTab = ref(topicTabs[0]);
const topicItems: TopicItem[] = [
  {
    id: 'ai-smart',
    title: '人工智能 +',
    desc: 'AGI、ASI、SSI趋势。AI将如何影响学习与工作...',
    stats: '1004 订阅 · 92 项',
    author: '@AI模型观察',
    icon: SparklesOutline,
    color: '#052e2b',
  },
  {
    id: 'geo',
    title: '高中地理题库',
    desc: '高考综合复习资源。',
    stats: '123 订阅 · 604 项',
    author: '@GeoLab',
    icon: EarthOutline,
    color: '#0f3d2e',
  },
  {
    id: 'agent',
    title: 'AI 智能体开发',
    desc: '智能助手、Agentic AI 及结构化工作流...',
    stats: '3.1k 订阅 · 2480 项',
    author: '@Huan',
    icon: SparklesOutline,
    color: '#eaf2ff',
  },
  {
    id: 'paper',
    title: 'SCI 论文写作指南',
    desc: '论文、选题、基金申请和投稿技巧。',
    stats: '7087 订阅 · 818 项',
    author: '@Doctor',
    icon: DocumentTextOutline,
    color: '#3b9cff',
  },
  {
    id: 'stock',
    title: 'A股证券（每日）',
    desc: '市场新闻、公告和研报。',
    stats: '1.1k 订阅 · 38798 项',
    author: '@腾讯自选股',
    icon: BarChartOutline,
    color: '#f2f2f2',
  },
  {
    id: 'trader',
    title: '交易大师模型',
    desc: '你来市场不是为了参与，而是为了建立纪律...',
    stats: '9861 订阅 · 97 项',
    author: '@Bull Lab',
    icon: BriefcaseOutline,
    color: '#101010',
  },
];

const knowledgeDocuments: KnowledgeDocumentItem[] = [
  { id: 'readme', title: 'README｜介绍与指南', kind: '笔记', meta: '6/5 更新', icon: DocumentTextOutline },
  { id: 'latex', title: 'LaTeX 数学笔记提示词模板', kind: '模板', meta: '4/19 更新', icon: CodeSlashOutline },
  { id: 'pdf', title: '高等数学同步辅导.pdf', kind: 'PDF', meta: '6/5', icon: DocumentTextOutline },
  { id: 'vector', title: '8. 向量代数与空间解析几何', kind: '文件夹', meta: '30 项 · 昨天', icon: FolderOutline },
  { id: 'differential', title: '7. 微分方程', kind: '文件夹', meta: '54 项 · 昨天', icon: FolderOutline },
];

const selectedModelInfo = computed(() => modelOptions.find((model) => model.id === selectedModel.value) ?? modelOptions[0]);
const currentConversation = computed(() => conversations.value.find((item) => item.id === currentConversationId.value));
const messages = computed({
  get: () => currentConversation.value?.messages ?? [],
  set: (value: ChatMessage[]) => {
    if (currentConversation.value) {
      currentConversation.value.messages = value;
      currentConversation.value.updatedAt = Date.now();
    }
  },
});
const selectedKnowledgeId = computed(() => {
  const id = route.params.id;
  return typeof id === 'string' ? id : knowledgeBases.value[0]?.id || '';
});
const selectedKnowledge = computed(() => knowledgeBases.value.find((item) => item.id === selectedKnowledgeId.value) ?? knowledgeBases.value[0]);
const pageMode = computed<AiPageMode>(() => {
  if (route.name === 'ai-wiki-detail' || route.path.startsWith('/ai/wikis/')) return 'wiki';
  if (route.name === 'ai-wikis' || route.path === '/ai/wikis') return 'workspace';
  return 'discover';
});
const contextText = computed(() => attachedContexts.value.map((item) => `【${item.name}】\n${item.content}`).join('\n\n'));
const enabledAbilities = computed(() => (webSearchEnabled.value ? ['web-search'] : []));
const totalDocs = computed(() => knowledgeBases.value.reduce((sum, item) => sum + item.docs, 0));
const totalVectors = computed(() => knowledgeBases.value.reduce((sum, item) => sum + item.vectors, 0));
const recentConversations = computed(() => [...conversations.value].sort((a, b) => b.updatedAt - a.updatedAt).slice(0, 4));
const rotatedFeaturedCards = computed(() =>
  Array.from({ length: featuredCards.length }, (_, index) => featuredCards[(recommendationOffset.value + index) % featuredCards.length]),
);
const shellNav = computed<ShellNavItem[]>(() => [
  { key: 'knowledge', label: '知识库', icon: LibraryOutline, path: '/ai/wikis', active: pageMode.value === 'workspace' || pageMode.value === 'wiki' },
  { key: 'discover', label: '发现', icon: SparklesOutline, path: '/ai/discover', active: pageMode.value === 'discover' },
  { key: 'history', label: '历史', icon: TimeOutline, active: false, action: () => startNewChat() },
  { key: 'workbench', label: '工作台', icon: BriefcaseOutline, path: '/ai/wikis', active: pageMode.value === 'workspace' },
  { key: 'community', label: '社区', icon: ChatbubbleEllipsesOutline, path: '/square', active: false },
]);

watch(
  () => route.query.q,
  (value) => {
    pageSearch.value = typeof value === 'string' ? value : '';
  },
  { immediate: true },
);

watch(
  conversations,
  (value) => localStorage.setItem(CHAT_STORAGE_KEY, JSON.stringify(value.slice(0, 30))),
  { deep: true },
);
watch(selectedModel, (value) => localStorage.setItem(MODEL_KEY, value));
watch(knowledgeBases, (value) => localStorage.setItem(KNOWLEDGE_STORAGE_KEY, JSON.stringify(value)), { deep: true });

onMounted(() => {
  loadLocalState();
  void scrollToBottom();
});

function nowLabel() {
  return new Date().toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' });
}

function formatCompact(value: number) {
  if (value >= 10000) return `${(value / 10000).toFixed(value >= 100000 ? 0 : 1)}w`;
  return value.toLocaleString('zh-CN');
}

function normalizeTitle(question: string) {
  const compact = question.replace(/\s+/g, ' ').trim();
  return compact.length > 24 ? `${compact.slice(0, 24)}...` : compact || '新的对话';
}

function createConversation(title = '新的对话', knowledgeBaseId?: string): Conversation {
  return {
    id: `chat-${Date.now()}-${Math.random().toString(16).slice(2)}`,
    title,
    updatedAt: Date.now(),
    favorite: false,
    knowledgeBaseId,
    messages: [
      {
        role: 'assistant',
        content: knowledgeBaseId
          ? '我已进入知识库问答模式，可以基于选定知识库回答问题或创建任务。'
          : '你好，我是青云阁 AI 助手。你可以直接提问，也可以从发现页选择一个主题开始。',
        time: nowLabel(),
      },
    ],
  };
}

function loadLocalState() {
  const storedModel = localStorage.getItem(MODEL_KEY);
  if (storedModel && modelOptions.some((item) => item.id === storedModel)) selectedModel.value = storedModel;

  try {
    const parsed = JSON.parse(localStorage.getItem(CHAT_STORAGE_KEY) || '[]') as Conversation[];
    conversations.value = parsed.length ? parsed : [createConversation('如何高效准备期末考试？')];
  } catch {
    conversations.value = [createConversation()];
  }
  currentConversationId.value = conversations.value[0]?.id || '';

  try {
    const storedKnowledge = JSON.parse(localStorage.getItem(KNOWLEDGE_STORAGE_KEY) || '[]') as KnowledgeBase[];
    knowledgeBases.value = storedKnowledge.length ? storedKnowledge : defaultKnowledgeBases.map((item) => ({ ...item }));
  } catch {
    knowledgeBases.value = defaultKnowledgeBases.map((item) => ({ ...item }));
  }
  importDraft.value.targetId = selectedKnowledge.value?.id || knowledgeBases.value[0]?.id || '';
}

async function scrollToBottom() {
  await nextTick();
  if (chatStreamRef.value) chatStreamRef.value.scrollTop = chatStreamRef.value.scrollHeight;
}

function navigateShell(item: ShellNavItem) {
  if (item.action) {
    item.action();
    return;
  }
  if (item.path) void router.push(item.path);
}

function goWorkspace() {
  void router.push('/ai/wikis');
}

function startNewChat() {
  const chat = createConversation();
  conversations.value.unshift(chat);
  currentConversationId.value = chat.id;
  attachedContexts.value = [];
  draft.value = '';
  void router.push('/ai');
  void scrollToBottom();
}

function openConversation(id: string) {
  currentConversationId.value = id;
  const conversation = conversations.value.find((item) => item.id === id);
  if (conversation?.knowledgeBaseId) {
    void router.push(`/ai/wikis/${conversation.knowledgeBaseId}`);
  } else {
    void router.push('/ai');
  }
  void scrollToBottom();
}

function askFromPrompt(prompt: string, knowledgeBaseId?: string) {
  draft.value = prompt;
  if (knowledgeBaseId) ensureKnowledgeConversation(knowledgeBaseId);
  void sendQuestion(knowledgeBaseId);
}

function ensureKnowledgeConversation(knowledgeBaseId: string) {
  const existing = conversations.value.find((item) => item.knowledgeBaseId === knowledgeBaseId);
  if (existing) {
    currentConversationId.value = existing.id;
    return;
  }
  const chat = createConversation(`基于 ${selectedKnowledge.value?.name || '知识库'} 提问`, knowledgeBaseId);
  conversations.value.unshift(chat);
  currentConversationId.value = chat.id;
}

async function sendQuestion(knowledgeBaseId?: string) {
  const question = draft.value.trim();
  if (!question || loading.value) return;

  draft.value = '';
  if (knowledgeBaseId) ensureKnowledgeConversation(knowledgeBaseId);
  if (!currentConversation.value) {
    const chat = createConversation('新的对话', knowledgeBaseId);
    conversations.value.unshift(chat);
    currentConversationId.value = chat.id;
  }
  if (currentConversation.value?.title === '新的对话') currentConversation.value.title = normalizeTitle(question);

  messages.value = [...messages.value, { role: 'user', content: question, time: nowLabel() }];
  loading.value = true;
  await scrollToBottom();

  try {
    const history = messages.value.slice(-10).map((item) => ({ role: item.role, content: item.content }));
    const knowledgeContext = knowledgeBaseId && selectedKnowledge.value
      ? `当前知识库：${selectedKnowledge.value.name}\n${selectedKnowledge.value.desc}\n\n${contextText.value}`
      : contextText.value;
    const result = webSearchEnabled.value || knowledgeBaseId
      ? await aiRagChat(history, knowledgeContext, selectedModel.value, enabledAbilities.value)
      : await aiChat(history, knowledgeContext, selectedModel.value, enabledAbilities.value);

    messages.value = [
      ...messages.value,
      {
        role: 'assistant',
        content: result.reply || '没有生成有效回复，请换一种问法再试。',
        time: nowLabel(),
        citations: result.citations || [],
      },
    ];
  } catch (error) {
    const reason = error instanceof Error ? error.message : '请求失败';
    messages.value = [
      ...messages.value,
      {
        role: 'assistant',
        content: `这次问答请求失败：${reason}`,
        time: nowLabel(),
      },
    ];
  } finally {
    loading.value = false;
    await scrollToBottom();
  }
}

function askKnowledgeSuggestion(question: string) {
  const id = selectedKnowledge.value?.id;
  if (!id) return;
  draft.value = question;
  void sendQuestion(id);
}

function refreshRecommendations() {
  recommendationOffset.value = (recommendationOffset.value + 1) % featuredCards.length;
}

function searchWithinAi() {
  const query = pageSearch.value.trim();
  if (!query) return;
  draft.value = query;
  if (pageMode.value === 'wiki' && selectedKnowledge.value) {
    void sendQuestion(selectedKnowledge.value.id);
  } else {
    void sendQuestion();
  }
}

function triggerAttachmentPicker() {
  fileInputRef.value?.click();
}

async function handleFileChange(event: Event) {
  const input = event.target as HTMLInputElement;
  const file = input.files?.[0];
  input.value = '';
  if (!file) return;

  const supported = /\.(txt|md|markdown|json|csv|log|ts|tsx|js|jsx|vue|java|py|sql|yml|yaml)$/i.test(file.name);
  if (!supported) {
    message.warning('当前只支持读取文本类文件：txt、md、json、csv、代码文件等');
    return;
  }
  if (file.size > 300 * 1024) {
    message.warning('文件超过 300KB，请先精简后再上传分析');
    return;
  }

  const content = await file.text();
  attachedContexts.value.push({ name: file.name, content: content.slice(0, 12000) });
  draft.value = draft.value || `请分析我上传的文件《${file.name}》。`;
  message.success(`已读取 ${file.name}，发送时会作为上下文`);
}

function removeAttachment(name: string) {
  attachedContexts.value = attachedContexts.value.filter((item) => item.name !== name);
}

function toggleWebSearch() {
  webSearchEnabled.value = !webSearchEnabled.value;
  message.info(webSearchEnabled.value ? '已开启联网/站内检索增强' : '已关闭检索增强');
}

async function copyAssistantAnswer(content: string) {
  if (await copyTextToClipboard(content)) message.success('已复制');
  else message.warning('复制失败，请手动选择内容复制');
}

function retryAssistantAnswer(index: number) {
  const previousUser = [...messages.value.slice(0, index)].reverse().find((item) => item.role === 'user');
  if (!previousUser || loading.value) {
    message.warning('没有可重试的问题');
    return;
  }
  messages.value = messages.value.filter((_, itemIndex) => itemIndex !== index);
  draft.value = previousUser.content;
  void sendQuestion(currentConversation.value?.knowledgeBaseId);
}

function continueAnswer() {
  if (loading.value) return;
  draft.value = '请继续展开上一条回答，并补充更具体的步骤。';
  void sendQuestion(currentConversation.value?.knowledgeBaseId);
}

function markAssistantFeedback(helpful: boolean) {
  message.success(helpful ? '已记录：这条回答有帮助' : '已记录：会减少类似回答方式');
}

function toggleFavorite(id = currentConversationId.value) {
  const target = conversations.value.find((item) => item.id === id);
  if (!target) return;
  target.favorite = !target.favorite;
  message.success(target.favorite ? '已加入收藏' : '已取消收藏');
}

function createKnowledgeBase() {
  if (!knowledgeDraft.value.name.trim()) {
    message.warning('请填写知识库名称');
    return;
  }
  const item: KnowledgeBase = {
    id: `kb-${Date.now()}`,
    name: knowledgeDraft.value.name.trim(),
    desc: knowledgeDraft.value.desc.trim() || '新的知识库，等待导入文档',
    category: knowledgeDraft.value.category,
    type: knowledgeDraft.value.category,
    docs: 0,
    vectors: 0,
    updatedAt: '刚刚',
    owner: '我创建的',
    favorite: false,
    color: '#10b981',
  };
  knowledgeBases.value = [item, ...knowledgeBases.value];
  knowledgeDraft.value = { name: '', category: '学习资料', desc: '' };
  importDraft.value.targetId = item.id;
  createKnowledgeVisible.value = false;
  message.success('知识库已创建');
  void router.push('/ai/wikis');
}

function importKnowledgeDocs() {
  const target = knowledgeBases.value.find((item) => item.id === importDraft.value.targetId);
  if (!target) {
    message.warning('请选择目标知识库');
    return;
  }
  const fileCount = Math.max(1, importDraft.value.files.split('\n').filter(Boolean).length || 1);
  target.docs += fileCount;
  target.vectors += fileCount * 8600;
  target.updatedAt = '刚刚';
  importKnowledgeVisible.value = false;
  importDraft.value = { targetId: target.id, files: '', tags: '' };
  message.success(`已导入 ${fileCount} 份文档到 ${target.name}`);
}

function openKnowledge(item: KnowledgeBase) {
  void router.push(`/ai/wikis/${item.id}`);
}

function toggleKnowledgeFavorite(item: KnowledgeBase) {
  item.favorite = !item.favorite;
  message.success(item.favorite ? '已收藏知识库' : '已取消收藏');
}
</script>

<template>
  <div class="ima-app" :class="`mode-${pageMode}`">
    <aside class="ima-sidebar">
      <button class="ima-brand" @click="router.push('/ai')">
        <span class="brand-leaf"><SparklesOutline /></span>
        <strong>ima copilot</strong>
      </button>

      <button class="new-chat-btn" @click="startNewChat">
        <NIcon size="23"><AddOutline /></NIcon>
        <span>新对话</span>
      </button>

      <nav class="ima-nav">
        <button
          v-for="item in shellNav"
          :key="item.key"
          :class="{ active: item.active }"
          @click="navigateShell(item)"
        >
          <NIcon size="22"><component :is="item.icon" /></NIcon>
          <span>{{ item.label }}</span>
        </button>
      </nav>

      <div class="sidebar-spacer" />

      <nav class="ima-nav low">
        <button @click="message.info('帮助中心将在后续版本接入')">
          <NIcon size="22"><HelpCircleOutline /></NIcon>
          <span>帮助</span>
        </button>
        <button @click="message.info('设置项将在后续版本接入')">
          <NIcon size="22"><SettingsOutline /></NIcon>
          <span>设置</span>
        </button>
      </nav>
    </aside>

    <section class="ima-main">
      <header class="ima-topbar">
        <label class="top-search" :class="{ compact: pageMode === 'discover' }">
          <NIcon size="24"><SearchOutline /></NIcon>
          <input v-model="pageSearch" :placeholder="pageMode === 'wiki' ? '搜索当前知识库...' : '搜索知识库...'" @keyup.enter="searchWithinAi" />
        </label>
        <div class="top-actions">
          <button title="回到社区" @click="router.push('/square')"><NIcon size="22"><HomeOutline /></NIcon></button>
          <button title="通知" @click="router.push('/notifications')"><NIcon size="22"><NotificationsOutline /></NIcon></button>
          <button title="设置" @click="message.info('设置项将在后续版本接入')"><NIcon size="22"><SettingsOutline /></NIcon></button>
          <span class="user-avatar">青</span>
        </div>
      </header>

      <main v-if="pageMode === 'discover'" class="discover-page" :class="{ chatting: messages.length > 1 || loading }">
        <section v-if="messages.length <= 1 && !loading" class="hero-query">
          <textarea
            v-model="draft"
            placeholder="搜索知识库..."
            @keydown.enter.exact.prevent="sendQuestion()"
          />
          <div class="hero-tools">
            <button :class="{ active: !webSearchEnabled }" @click="toggleWebSearch">
              <NIcon size="20"><ChatbubbleEllipsesOutline /></NIcon>
              对话模式
            </button>
            <button :class="{ active: webSearchEnabled }" @click="toggleWebSearch">
              <NIcon size="20"><EarthOutline /></NIcon>
              联网搜索
            </button>
            <button class="ghost-icon" title="上传文本文件作为上下文" @click="triggerAttachmentPicker">
              <NIcon size="22"><AttachOutline /></NIcon>
            </button>
            <button class="send-round" :disabled="loading || !draft.trim()" title="发送" @click="sendQuestion()">
              <NIcon size="24"><SendOutline /></NIcon>
            </button>
          </div>
        </section>

        <section v-if="messages.length > 1 || loading" class="chat-preview">
          <header>
            <h2>{{ currentConversation?.title || '新的对话' }}</h2>
            <button @click="toggleFavorite()">
              <NIcon size="17"><BookmarkOutline /></NIcon>
              {{ currentConversation?.favorite ? '取消收藏' : '收藏' }}
            </button>
          </header>
          <div ref="chatStreamRef" class="message-stream">
            <article v-for="(item, index) in messages" :key="`${item.role}-${index}`" class="message-row" :class="item.role">
              <div class="message-bubble">
                <strong>{{ item.role === 'assistant' ? '小青' : '我' }}</strong>
                <pre>{{ item.content }}</pre>
                <div v-if="item.citations?.length" class="citation-list">
                  <a v-for="source in item.citations" :key="`${source.type}-${source.id}`" :href="source.url">{{ source.title }}</a>
                </div>
                <footer v-if="item.role === 'assistant'">
                  <button @click="copyAssistantAnswer(item.content)"><NIcon size="15"><CopyOutline /></NIcon>复制</button>
                  <button @click="retryAssistantAnswer(index)"><NIcon size="15"><RefreshOutline /></NIcon>再试一次</button>
                  <button @click="continueAnswer"><NIcon size="15"><SparklesOutline /></NIcon>继续</button>
                  <button @click="markAssistantFeedback(true)"><NIcon size="15"><CheckmarkCircleOutline /></NIcon>有帮助</button>
                </footer>
              </div>
            </article>
            <div v-if="loading" class="loading-line"><span />AI 正在组织回答...</div>
          </div>
        </section>

        <section v-if="messages.length > 1 || loading" class="chat-input-dock">
          <div v-if="attachedContexts.length" class="attachment-list">
            <button v-for="item in attachedContexts" :key="item.name" @click="removeAttachment(item.name)">{{ item.name }} x</button>
          </div>
          <textarea
            v-model="draft"
            placeholder="继续提问..."
            @keydown.enter.exact.prevent="sendQuestion()"
          />
          <div class="hero-tools">
            <button :class="{ active: !webSearchEnabled }" @click="toggleWebSearch">
              <NIcon size="20"><ChatbubbleEllipsesOutline /></NIcon>
              对话模式
            </button>
            <button :class="{ active: webSearchEnabled }" @click="toggleWebSearch">
              <NIcon size="20"><EarthOutline /></NIcon>
              联网搜索
            </button>
            <button class="ghost-icon" title="上传文本文件作为上下文" @click="triggerAttachmentPicker">
              <NIcon size="22"><AttachOutline /></NIcon>
            </button>
            <button class="send-round" :disabled="loading || !draft.trim()" title="发送" @click="sendQuestion()">
              <NIcon size="24"><SendOutline /></NIcon>
            </button>
          </div>
        </section>

        <section class="featured-section">
          <div class="section-heading">
            <div>
              <h1>精选推荐</h1>
              <p>为您精心挑选的见解</p>
            </div>
            <button @click="refreshRecommendations"><NIcon size="17"><RefreshOutline /></NIcon>换一换</button>
          </div>
          <div class="featured-grid">
            <article v-for="card in rotatedFeaturedCards" :key="card.id" class="feature-card" @click="askFromPrompt(card.prompt)">
              <div class="feature-visual" :style="{ background: card.color }">
                <NIcon size="48"><component :is="card.icon" /></NIcon>
              </div>
              <h3>{{ card.title }}</h3>
              <footer>
                <span><NIcon size="15"><DocumentTextOutline /></NIcon>{{ card.meta }}</span>
                <span><NIcon size="15"><EyeOutline /></NIcon>{{ card.views }}</span>
              </footer>
            </article>
          </div>
        </section>

        <section class="topic-section">
          <div class="topic-tabs">
            <button v-for="tab in topicTabs" :key="tab" :class="{ active: activeTopicTab === tab }" @click="activeTopicTab = tab">
              {{ tab }}
            </button>
          </div>
          <div class="topic-grid">
            <article v-for="item in topicItems" :key="item.id" @click="askFromPrompt(`请介绍「${item.title}」并给出学习建议。`)">
              <span class="topic-icon" :style="{ background: item.color }"><NIcon size="28"><component :is="item.icon" /></NIcon></span>
              <div>
                <h3>{{ item.title }}</h3>
                <p>{{ item.desc }}</p>
                <small>{{ item.stats }} · {{ item.author }}</small>
              </div>
            </article>
          </div>
        </section>
      </main>

      <main v-else-if="pageMode === 'workspace'" class="workspace-page">
        <section class="workspace-hero">
          <div>
            <h1>个人工作台</h1>
            <p>管理和编排您的 AI 知识库。</p>
          </div>
          <button class="create-kb-btn" @click="createKnowledgeVisible = true">
            <NIcon size="22"><AddOutline /></NIcon>
            创建新知识库
          </button>
        </section>

        <section class="stats-row">
          <article>
            <span><NIcon size="28"><DocumentTextOutline /></NIcon></span>
            <div><p>总文件数</p><strong>{{ totalDocs.toLocaleString('zh-CN') }}</strong></div>
          </article>
          <article>
            <span class="purple"><NIcon size="28"><CloudUploadOutline /></NIcon></span>
            <div><p>已用存储</p><strong>45.2 GB</strong></div>
          </article>
          <article>
            <span class="green"><NIcon size="28"><SparklesOutline /></NIcon></span>
            <div><p>AI 交互次数</p><strong>{{ formatCompact(totalVectors).replace('w', ',000') }}</strong></div>
          </article>
        </section>

        <section class="workspace-preview">
          <div class="browser-bar"><i /><i /><i /><span>ima.copilot/wiki</span></div>
          <div class="preview-content">
            <aside />
            <main>
              <article v-for="item in knowledgeBases.slice(0, 2)" :key="item.id">
                <span :style="{ background: item.color }" />
                <strong>{{ item.name }}</strong>
                <small>{{ item.docs }} 份文档</small>
              </article>
            </main>
            <footer>基于知识库提问...</footer>
          </div>
        </section>

        <section class="workspace-lower">
          <div class="my-kbs">
            <header>
              <h2>我的知识库</h2>
              <button @click="message.info('已展示全部知识库')">查看全部</button>
            </header>
            <div class="kb-card-grid">
              <article v-for="item in knowledgeBases" :key="item.id" @click="openKnowledge(item)">
                <button class="more" title="收藏知识库" @click.stop="toggleKnowledgeFavorite(item)">
                  <NIcon size="18"><BookmarkOutline /></NIcon>
                </button>
                <span class="kb-mark" :style="{ '--kb-color': item.color }"><NIcon size="24"><FolderOutline /></NIcon></span>
                <h3>{{ item.name }}</h3>
                <p>{{ item.desc }}</p>
                <footer>{{ item.docs }} 份文档 · {{ item.updatedAt }}</footer>
              </article>
            </div>
          </div>

          <aside class="recent-panel">
            <h2><NIcon size="22"><TimeOutline /></NIcon>最近历史记录</h2>
            <button v-for="item in recentConversations" :key="item.id" @click="openConversation(item.id)">
              <i />
              <span>{{ new Date(item.updatedAt).toLocaleString('zh-CN', { hour: '2-digit', minute: '2-digit' }) }}</span>
              <strong>查询了「{{ item.title }}」</strong>
              <em>{{ item.messages.at(-1)?.content.slice(0, 18) || '暂无内容' }}...</em>
            </button>
          </aside>
        </section>
      </main>

      <main v-else class="wiki-page">
        <aside class="wiki-library">
          <section class="wiki-summary" v-if="selectedKnowledge">
            <span class="wiki-logo" :style="{ '--kb-color': selectedKnowledge.color }"><NIcon size="38"><LibraryOutline /></NIcon></span>
            <div>
              <h1>{{ selectedKnowledge.name }}</h1>
              <p>MathHub</p>
            </div>
            <p>{{ selectedKnowledge.desc }}</p>
            <footer>
              <strong>1823人订阅</strong>
              <strong>{{ selectedKnowledge.vectors.toLocaleString('zh-CN') }}次浏览/问答</strong>
            </footer>
          </section>

          <section class="doc-list">
            <header>
              <strong>目录（{{ selectedKnowledge?.docs || 0 }}）</strong>
              <NIcon size="18"><SearchOutline /></NIcon>
            </header>
            <button
              v-for="item in knowledgeDocuments"
              :key="item.id"
              :class="{ active: selectedDocumentId === item.id }"
              @click="selectedDocumentId = item.id"
            >
              <NIcon size="22"><component :is="item.icon" /></NIcon>
              <span>
                <strong>{{ item.title }}</strong>
                <small>{{ item.kind }} · {{ item.meta }}</small>
              </span>
            </button>
          </section>
          <footer class="storage-mini">已用 8.52MB / 50GB</footer>
        </aside>

        <section class="wiki-chat">
          <header class="wiki-breadcrumb">
            <span>知识库</span>
            <NIcon size="16"><ChevronForwardOutline /></NIcon>
            <strong>{{ selectedKnowledge?.name }}</strong>
            <div>
              <button title="收藏" @click="selectedKnowledge && toggleKnowledgeFavorite(selectedKnowledge)"><NIcon size="22"><BookmarkOutline /></NIcon></button>
              <button title="分享"><NIcon size="22"><ShareSocialOutline /></NIcon></button>
              <button class="context-btn"><NIcon size="18"><SparklesOutline /></NIcon>总结上下文</button>
            </div>
          </header>

          <div v-if="messages.length <= 1 && !loading" class="wiki-empty">
            <span><NIcon size="42"><SparklesOutline /></NIcon></span>
            <h1>基于此知识库提问或创建任务</h1>
            <p>我可以帮助您总结文档，解决数学问题，或从选定的文件夹中提取特定的概念。</p>
            <button @click="askKnowledgeSuggestion('总结罗尔定理和拉格朗日中值定理的证明。')">
              总结罗尔定理和拉格朗日中值定理的证明。
              <NIcon size="24"><ArrowForwardOutline /></NIcon>
            </button>
            <button @click="askKnowledgeSuggestion('基于收集的练习，对求解极限的方法进行分类。')">
              基于收集的练习，对求解极限的方法进行分类。
              <NIcon size="24"><ArrowForwardOutline /></NIcon>
            </button>
            <button @click="askKnowledgeSuggestion('请总结计算不定积分的技巧。')">
              请总结计算不定积分的技巧。
              <NIcon size="24"><ArrowForwardOutline /></NIcon>
            </button>
          </div>

          <div v-else ref="chatStreamRef" class="wiki-message-stream">
            <article v-for="(item, index) in messages" :key="`${item.role}-${index}`" class="message-row" :class="item.role">
              <div class="message-bubble">
                <strong>{{ item.role === 'assistant' ? '小青' : '我' }}</strong>
                <pre>{{ item.content }}</pre>
                <footer v-if="item.role === 'assistant'">
                  <button @click="copyAssistantAnswer(item.content)"><NIcon size="15"><CopyOutline /></NIcon>复制</button>
                  <button @click="retryAssistantAnswer(index)"><NIcon size="15"><RefreshOutline /></NIcon>再试一次</button>
                </footer>
              </div>
            </article>
            <div v-if="loading" class="loading-line"><span />AI 正在检索知识库...</div>
          </div>

          <section class="wiki-input">
            <div v-if="attachedContexts.length" class="attachment-list">
              <button v-for="item in attachedContexts" :key="item.name" @click="removeAttachment(item.name)">{{ item.name }} x</button>
            </div>
            <div class="wiki-input-tools">
              <button :class="{ active: !webSearchEnabled }" @click="toggleWebSearch"><NIcon size="18"><ChatbubbleEllipsesOutline /></NIcon>聊天模式</button>
              <button :class="{ active: webSearchEnabled }" @click="toggleWebSearch"><NIcon size="18"><SparklesOutline /></NIcon>快速搜索</button>
            </div>
            <div class="wiki-input-row">
              <button title="上传文本文件作为上下文" @click="triggerAttachmentPicker"><NIcon size="23"><AddOutline /></NIcon></button>
              <textarea v-model="draft" placeholder="基于知识库提问..." @keydown.enter.exact.prevent="selectedKnowledge && sendQuestion(selectedKnowledge.id)" />
              <button class="send-round" :disabled="loading || !draft.trim()" @click="selectedKnowledge && sendQuestion(selectedKnowledge.id)">
                <NIcon size="24"><PaperPlaneOutline /></NIcon>
              </button>
            </div>
            <p>内容由AI生成，仅供参考。</p>
          </section>
        </section>
      </main>

      <input ref="fileInputRef" class="file-input" type="file" @change="handleFileChange" />
    </section>

    <div v-if="createKnowledgeVisible || importKnowledgeVisible" class="modal-mask" @click="createKnowledgeVisible = false; importKnowledgeVisible = false">
      <section v-if="createKnowledgeVisible" class="simple-modal" @click.stop>
        <h2>创建知识库</h2>
        <label>名称<input v-model="knowledgeDraft.name" placeholder="例如：产品帮助文档" /></label>
        <label>
          分类
          <select v-model="knowledgeDraft.category">
            <option>产品文档</option>
            <option>技术文档</option>
            <option>学习资料</option>
            <option>公司制度</option>
            <option>市场与销售</option>
          </select>
        </label>
        <label>描述<textarea v-model="knowledgeDraft.desc" placeholder="说明这个知识库覆盖哪些内容" /></label>
        <footer>
          <button @click="createKnowledgeVisible = false">取消</button>
          <button class="primary" @click="createKnowledgeBase">创建</button>
        </footer>
      </section>

      <section v-if="importKnowledgeVisible" class="simple-modal" @click.stop>
        <h2>导入文档</h2>
        <label>
          目标知识库
          <select v-model="importDraft.targetId">
            <option v-for="item in knowledgeBases" :key="item.id" :value="item.id">{{ item.name }}</option>
          </select>
        </label>
        <label>文件名<textarea v-model="importDraft.files" placeholder="每行一个文件名，用于本地模拟导入" /></label>
        <label>标签<input v-model="importDraft.tags" placeholder="例如：高数、期末、重点" /></label>
        <footer>
          <button @click="importKnowledgeVisible = false">取消</button>
          <button class="primary" @click="importKnowledgeDocs">导入</button>
        </footer>
      </section>
    </div>
  </div>
</template>

<style lang="scss">
.ima-app {
  --ima-green: #007a52;
  --ima-green-strong: #006342;
  --ima-mint: #18bf8d;
  --ima-text: #0f1714;
  --ima-muted: #65746e;
  --ima-border: #dbe5e0;
  --ima-soft: #f5f7f6;
  min-height: 100vh;
  display: grid;
  grid-template-columns: 280px minmax(0, 1fr);
  background: #f7f8f8;
  color: var(--ima-text);
}

.ima-app button,
.ima-app input,
.ima-app textarea,
.ima-app select {
  font: inherit;
}

.ima-app button {
  border: 0;
  background: transparent;
  color: inherit;
  cursor: pointer;
}

.ima-sidebar {
  min-height: 100vh;
  padding: 28px 16px 24px;
  border-right: 1px solid #cbd8d2;
  background: #fff;
  display: flex;
  flex-direction: column;
  gap: 26px;
}

.ima-brand {
  height: 42px;
  padding: 0 10px;
  display: inline-flex;
  align-items: center;
  gap: 14px;
  color: var(--ima-green);
  font-size: 22px;
  font-weight: 900;
}

.brand-leaf {
  width: 29px;
  height: 29px;
  display: grid;
  place-items: center;
}

.brand-leaf svg {
  width: 100%;
  height: 100%;
}

.new-chat-btn {
  height: 52px;
  border-radius: 12px !important;
  background: #18bd87 !important;
  color: #062c21 !important;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12px;
  font-weight: 800;
}

.ima-nav {
  display: grid;
  gap: 8px;
}

.ima-nav button {
  height: 48px;
  padding: 0 18px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  gap: 16px;
  color: #1d3029;
  text-align: left;
  font-weight: 700;
}

.ima-nav button.active,
.ima-nav button:hover {
  background: #18bd87;
  color: #063024;
}

.sidebar-spacer {
  flex: 1;
  border-bottom: 1px solid #e0e7e3;
}

.ima-nav.low {
  gap: 10px;
}

.ima-main {
  min-width: 0;
  display: flex;
  flex-direction: column;
}

.ima-topbar {
  height: 86px;
  padding: 14px 30px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 22px;
}

.top-search {
  width: min(620px, 100%);
  height: 48px;
  padding: 0 18px;
  border: 1px solid #b8c9c0;
  border-radius: 999px;
  background: #fff;
  display: flex;
  align-items: center;
  gap: 12px;
}

.top-search.compact {
  opacity: 0;
  pointer-events: none;
}

.top-search input {
  min-width: 0;
  flex: 1;
  border: 0;
  outline: 0;
  background: transparent;
}

.top-actions {
  display: flex;
  align-items: center;
  gap: 18px;
}

.top-actions button {
  width: 36px;
  height: 36px;
  display: grid;
  place-items: center;
}

.user-avatar {
  width: 42px;
  height: 42px;
  border-radius: 50%;
  background: #101820;
  color: #fff;
  display: grid;
  place-items: center;
  font-weight: 900;
  box-shadow: inset 0 0 0 2px #26343d;
}

.discover-page,
.workspace-page {
  width: min(1180px, calc(100vw - 340px));
  margin: 0 auto;
  padding: 30px 0 72px;
}

.discover-page.chatting {
  width: min(900px, calc(100vw - 340px));
  min-height: calc(100vh - 86px);
  padding-top: 34px;
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.hero-query {
  width: min(900px, 100%);
  margin: 22px auto 58px;
  border: 1px solid #d3dfd9;
  border-radius: 18px;
  background: #fff;
  box-shadow: 0 28px 70px rgba(31, 48, 42, 0.09);
  overflow: hidden;
}

.hero-query textarea {
  width: 100%;
  min-height: 92px;
  padding: 28px 40px;
  border: 0;
  outline: 0;
  resize: none;
  color: var(--ima-text);
  font-size: 22px;
  font-weight: 800;
  background: transparent;
}

.hero-query textarea::placeholder {
  color: #b7c0bc;
}

.hero-tools {
  min-height: 72px;
  padding: 14px 18px;
  border-top: 1px solid #e2ebe6;
  display: flex;
  align-items: center;
  gap: 14px;
}

.hero-tools button,
.wiki-input-tools button {
  height: 38px;
  padding: 0 14px;
  border-radius: 9px;
  display: inline-flex;
  align-items: center;
  gap: 8px;
  color: #1f332c;
  font-weight: 700;
}

.hero-tools button.active,
.wiki-input-tools button.active {
  background: #eef1f0;
}

.hero-tools .ghost-icon {
  margin-left: auto;
  padding: 0;
  width: 40px;
}

.send-round {
  width: 52px !important;
  height: 52px !important;
  padding: 0 !important;
  border-radius: 50% !important;
  background: #16b887 !important;
  color: #053325 !important;
  justify-content: center;
}

.send-round:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}

.chat-preview {
  flex: 1;
  min-height: 0;
  margin-bottom: 0;
  display: flex;
  flex-direction: column;
}

.chat-preview > header,
.section-heading,
.workspace-lower header {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 18px;
}

.chat-preview h2,
.section-heading h1,
.workspace-lower h2 {
  margin: 0;
}

.section-heading p {
  margin: 6px 0 0;
  color: var(--ima-muted);
}

.section-heading button,
.chat-preview header button,
.workspace-lower header button {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  color: var(--ima-green);
  font-weight: 800;
}

.message-stream,
.wiki-message-stream {
  max-height: 440px;
  margin-top: 16px;
  overflow-y: auto;
  display: grid;
  gap: 14px;
}

.discover-page.chatting .message-stream {
  flex: 1;
  max-height: none;
  min-height: 360px;
  padding-right: 8px;
}

.chat-input-dock {
  width: 100%;
  border: 1px solid #d3dfd9;
  border-radius: 18px;
  background: #fff;
  box-shadow: 0 18px 50px rgba(31, 48, 42, 0.08);
  overflow: hidden;
}

.chat-input-dock textarea {
  width: 100%;
  min-height: 70px;
  max-height: 150px;
  padding: 18px 22px;
  border: 0;
  outline: 0;
  resize: vertical;
  background: transparent;
  color: var(--ima-text);
}

.message-row {
  display: flex;
}

.message-row.user {
  justify-content: flex-end;
}

.message-bubble {
  max-width: min(720px, 88%);
  padding: 16px 18px;
  border: 1px solid var(--ima-border);
  border-radius: 16px;
  background: #fff;
}

.message-row.user .message-bubble {
  background: #e7f6f0;
}

.message-bubble strong {
  display: block;
  margin-bottom: 8px;
}

.message-bubble pre {
  margin: 0;
  white-space: pre-wrap;
  word-break: break-word;
  font-family: inherit;
  line-height: 1.7;
}

.message-bubble footer,
.citation-list {
  margin-top: 12px;
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.message-bubble footer button,
.citation-list a {
  min-height: 28px;
  padding: 0 9px;
  border-radius: 8px;
  background: var(--ima-soft);
  color: var(--ima-green);
  display: inline-flex;
  align-items: center;
  gap: 4px;
  text-decoration: none;
  font-size: 13px;
  font-weight: 700;
}

.loading-line {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  color: var(--ima-muted);
}

.loading-line span {
  width: 9px;
  height: 9px;
  border-radius: 50%;
  background: var(--ima-green);
  animation: imaPulse 1.2s infinite;
}

.featured-section {
  margin-bottom: 52px;
}

.featured-grid {
  margin-top: 28px;
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 18px;
}

.feature-card {
  min-height: 248px;
  padding: 18px;
  border: 1px solid #dfe7e3;
  border-radius: 14px;
  background: #fff;
  display: grid;
  gap: 14px;
  transition: transform 0.18s ease, box-shadow 0.18s ease;
}

.feature-card:hover,
.kb-card-grid article:hover {
  transform: translateY(-2px);
  box-shadow: 0 18px 40px rgba(31, 48, 42, 0.08);
}

.feature-visual {
  height: 128px;
  border-radius: 9px;
  display: grid;
  place-items: center;
  color: #14b87d;
}

.feature-card h3 {
  margin: 0;
  font-size: 16px;
  line-height: 1.45;
}

.feature-card footer {
  display: flex;
  gap: 14px;
  color: var(--ima-muted);
  font-size: 13px;
}

.feature-card footer span {
  display: inline-flex;
  align-items: center;
  gap: 5px;
}

.topic-tabs {
  border-bottom: 1px solid #d7e1dc;
  display: flex;
  gap: 26px;
}

.topic-tabs button {
  height: 44px;
  border-bottom: 2px solid transparent;
}

.topic-tabs button.active {
  border-color: var(--ima-green);
  color: var(--ima-green);
  font-weight: 800;
}

.topic-grid {
  margin-top: 36px;
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 34px 52px;
}

.topic-grid article {
  min-width: 0;
  display: grid;
  grid-template-columns: 80px minmax(0, 1fr);
  gap: 18px;
  align-items: center;
}

.topic-icon {
  width: 80px;
  height: 80px;
  border-radius: 10px;
  display: grid;
  place-items: center;
  color: #0fbf84;
}

.topic-grid h3,
.topic-grid p {
  margin: 0;
}

.topic-grid p {
  margin-top: 6px;
  color: #2d3935;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.topic-grid small {
  display: block;
  margin-top: 8px;
  color: var(--ima-muted);
}

.workspace-hero {
  margin: 10px 0 46px;
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 24px;
}

.workspace-hero h1 {
  margin: 0;
  font-size: 44px;
}

.workspace-hero p {
  margin: 10px 0 0;
  font-size: 18px;
  color: #24352d;
}

.create-kb-btn {
  height: 56px;
  padding: 0 28px !important;
  border-radius: 12px !important;
  background: var(--ima-green) !important;
  color: #fff !important;
  display: inline-flex;
  align-items: center;
  gap: 10px;
  font-weight: 900;
}

.stats-row {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 18px;
}

.stats-row article {
  min-height: 122px;
  padding: 28px 32px;
  border: 1px solid #dfe7e3;
  border-radius: 16px;
  background: #fff;
  display: flex;
  align-items: center;
  gap: 26px;
}

.stats-row article > span {
  width: 60px;
  height: 60px;
  border-radius: 50%;
  background: #dbeafe;
  color: #0b63b6;
  display: grid;
  place-items: center;
}

.stats-row article > span.purple {
  background: #ebe4ff;
  color: #5b21d6;
}

.stats-row article > span.green {
  background: #cef5e8;
  color: var(--ima-green);
}

.stats-row p,
.stats-row strong {
  margin: 0;
}

.stats-row strong {
  font-size: 30px;
  line-height: 1;
}

.workspace-preview {
  height: 486px;
  margin: 40px 0 38px;
  border: 1px solid #dfe7e3;
  border-radius: 18px;
  background: #fff;
  box-shadow: 0 24px 54px rgba(31, 48, 42, 0.08);
  overflow: hidden;
}

.browser-bar {
  height: 42px;
  padding: 0 18px;
  border-bottom: 1px solid #edf1ef;
  display: flex;
  align-items: center;
  gap: 8px;
  color: #9ca8a2;
  font-size: 12px;
}

.browser-bar i {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #d7dfdb;
}

.preview-content {
  height: calc(100% - 42px);
  display: grid;
  grid-template-columns: 150px minmax(0, 1fr);
  position: relative;
}

.preview-content aside {
  background: linear-gradient(90deg, #f4f7f6, #fff);
  border-right: 1px solid #edf1ef;
}

.preview-content main {
  padding: 48px;
  display: flex;
  gap: 24px;
  opacity: 0.52;
}

.preview-content article {
  width: 150px;
  height: 110px;
  border: 1px solid #dfe7e3;
  border-radius: 12px;
  padding: 14px;
  display: grid;
  gap: 8px;
}

.preview-content article span {
  width: 36px;
  height: 36px;
  border-radius: 10px;
}

.preview-content footer {
  position: absolute;
  left: 46%;
  right: 10%;
  bottom: 22px;
  height: 58px;
  border: 1px solid #dfe7e3;
  border-radius: 16px;
  display: flex;
  align-items: center;
  padding: 0 24px;
  color: #abb6b1;
  background: rgba(255, 255, 255, 0.9);
}

.workspace-lower {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 360px;
  gap: 32px;
  align-items: start;
}

.kb-card-grid {
  margin-top: 20px;
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 18px;
}

.kb-card-grid article {
  position: relative;
  min-height: 178px;
  padding: 24px 24px 18px;
  border: 1px solid #dfe7e3;
  border-radius: 14px;
  background: #fff;
}

.kb-card-grid .more {
  position: absolute;
  right: 18px;
  top: 18px;
}

.kb-mark {
  width: 50px;
  height: 50px;
  border-radius: 10px;
  color: var(--kb-color);
  background: color-mix(in srgb, var(--kb-color) 16%, transparent);
  display: grid;
  place-items: center;
}

.kb-card-grid h3 {
  margin: 18px 0 8px;
}

.kb-card-grid p {
  margin: 0;
  color: #2c3a34;
  line-height: 1.55;
}

.kb-card-grid footer {
  margin-top: 16px;
  color: var(--ima-muted);
  font-size: 13px;
}

.recent-panel {
  padding: 26px;
  border: 1px solid #dfe7e3;
  border-radius: 18px;
  background: #fff;
}

.recent-panel h2 {
  margin: 0 0 22px;
  display: flex;
  align-items: center;
  gap: 10px;
}

.recent-panel button {
  width: 100%;
  min-height: 86px;
  padding-left: 18px;
  border-left: 1px solid #dce5e0;
  display: grid;
  grid-template-columns: 14px minmax(0, 1fr);
  gap: 2px 10px;
  text-align: left;
}

.recent-panel i {
  width: 7px;
  height: 7px;
  margin-left: -22px;
  border-radius: 50%;
  background: #18bd87;
}

.recent-panel span {
  color: #12a977;
  font-size: 13px;
}

.recent-panel strong,
.recent-panel em {
  grid-column: 2;
}

.recent-panel em {
  width: max-content;
  max-width: 100%;
  padding: 6px 10px;
  border-radius: 8px;
  background: #f1f4f3;
  color: var(--ima-muted);
  font-style: normal;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.wiki-page {
  flex: 1;
  min-height: calc(100vh - 86px);
  display: grid;
  grid-template-columns: 425px minmax(0, 1fr);
  border-top: 1px solid #b8c9c0;
}

.wiki-library {
  border-right: 1px solid #b8c9c0;
  background: #fff;
  display: flex;
  flex-direction: column;
}

.wiki-summary {
  padding: 26px 30px 22px;
  border-bottom: 1px solid #dfe7e3;
  display: grid;
  grid-template-columns: 70px minmax(0, 1fr);
  gap: 18px;
}

.wiki-logo {
  width: 70px;
  height: 70px;
  border-radius: 9px;
  color: var(--kb-color);
  background: color-mix(in srgb, var(--kb-color) 12%, #f1f4f3);
  display: grid;
  place-items: center;
}

.wiki-summary h1 {
  margin: 0;
  font-size: 20px;
  line-height: 1.35;
}

.wiki-summary p {
  grid-column: 1 / -1;
  margin: 0;
  line-height: 1.55;
}

.wiki-summary footer {
  grid-column: 1 / -1;
  display: flex;
  gap: 24px;
  font-size: 13px;
}

.doc-list {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
}

.doc-list header {
  height: 60px;
  padding: 0 24px;
  border-bottom: 1px solid #dfe7e3;
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.doc-list button {
  width: calc(100% - 24px);
  min-height: 80px;
  margin: 10px 12px;
  padding: 0 16px;
  border-radius: 10px;
  display: grid;
  grid-template-columns: 28px minmax(0, 1fr);
  align-items: center;
  gap: 14px;
  text-align: left;
}

.doc-list button.active {
  background: #e9ebeb;
  box-shadow: inset 4px 0 0 #10a876;
}

.doc-list small {
  display: block;
  margin-top: 6px;
  color: #0db581;
}

.storage-mini {
  height: 40px;
  border-top: 1px solid #dfe7e3;
  display: grid;
  place-items: center;
  color: var(--ima-muted);
  font-size: 13px;
}

.wiki-chat {
  min-width: 0;
  position: relative;
  display: flex;
  flex-direction: column;
  background: #fff;
}

.wiki-breadcrumb {
  height: 64px;
  padding: 0 32px;
  display: flex;
  align-items: center;
  gap: 12px;
}

.wiki-breadcrumb > div {
  margin-left: auto;
  display: flex;
  align-items: center;
  gap: 18px;
}

.context-btn {
  height: 42px;
  padding: 0 16px !important;
  border: 1px solid #d7e1dc !important;
  border-radius: 9px !important;
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

.wiki-empty {
  width: min(760px, calc(100% - 72px));
  margin: auto;
  transform: translateY(-48px);
  text-align: center;
}

.wiki-empty > span {
  width: 80px;
  height: 80px;
  margin: 0 auto 30px;
  border-radius: 20px;
  background: #cdf7e9;
  color: var(--ima-green);
  display: grid;
  place-items: center;
}

.wiki-empty h1 {
  margin: 0 0 18px;
  font-size: 30px;
}

.wiki-empty p {
  margin: 0 0 42px;
  color: #314139;
}

.wiki-empty button {
  width: 100%;
  min-height: 72px;
  margin-top: 14px;
  padding: 0 22px;
  border: 1px solid #d7e1dc !important;
  border-radius: 13px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  text-align: left;
  font-size: 16px;
}

.wiki-message-stream {
  flex: 1;
  max-height: none;
  padding: 28px 36px 180px;
}

.wiki-input {
  position: sticky;
  bottom: 0;
  width: min(760px, calc(100% - 72px));
  margin: 0 auto 28px;
  border: 1px solid #cfdcd6;
  border-radius: 18px;
  background: #fff;
  box-shadow: 0 16px 40px rgba(31, 48, 42, 0.08);
}

.wiki-input-tools {
  height: 36px;
  padding: 8px 18px 0;
  display: flex;
  gap: 8px;
}

.wiki-input-row {
  min-height: 82px;
  padding: 10px 14px 12px;
  display: grid;
  grid-template-columns: 42px minmax(0, 1fr) 52px;
  align-items: center;
  gap: 12px;
}

.wiki-input-row > button:first-child {
  width: 34px;
  height: 34px;
  border: 1px solid #14372c !important;
  border-radius: 50%;
  display: grid;
  place-items: center;
}

.wiki-input textarea {
  min-height: 46px;
  max-height: 120px;
  border: 0;
  outline: 0;
  resize: vertical;
  background: transparent;
}

.wiki-input > p {
  position: absolute;
  left: 0;
  right: 0;
  bottom: -30px;
  margin: 0;
  color: var(--ima-muted);
  text-align: center;
  font-size: 12px;
}

.attachment-list {
  padding: 10px 14px 0;
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.attachment-list button {
  min-height: 28px;
  padding: 0 10px;
  border-radius: 999px;
  background: #edf4f1;
  color: var(--ima-green);
}

.file-input {
  display: none;
}

.modal-mask {
  position: fixed;
  inset: 0;
  z-index: 100;
  background: rgba(5, 16, 12, 0.38);
  display: grid;
  place-items: center;
  padding: 24px;
}

.simple-modal {
  width: min(520px, 100%);
  padding: 24px;
  border-radius: 18px;
  background: #fff;
  display: grid;
  gap: 16px;
}

.simple-modal h2 {
  margin: 0;
}

.simple-modal label {
  display: grid;
  gap: 7px;
  color: #273a32;
  font-weight: 800;
}

.simple-modal input,
.simple-modal textarea,
.simple-modal select {
  width: 100%;
  border: 1px solid #d7e1dc;
  border-radius: 10px;
  padding: 10px 12px;
  outline: none;
}

.simple-modal textarea {
  min-height: 96px;
  resize: vertical;
}

.simple-modal footer {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}

.simple-modal footer button {
  height: 38px;
  padding: 0 16px;
  border: 1px solid #d7e1dc;
  border-radius: 10px;
  font-weight: 800;
}

.simple-modal footer .primary {
  background: var(--ima-green);
  border-color: var(--ima-green);
  color: #fff;
}

@keyframes imaPulse {
  0% {
    box-shadow: 0 0 0 0 rgba(0, 122, 82, 0.28);
  }
  70% {
    box-shadow: 0 0 0 9px rgba(0, 122, 82, 0);
  }
  100% {
    box-shadow: 0 0 0 0 rgba(0, 122, 82, 0);
  }
}

@media (max-width: 1180px) {
  .ima-app {
    grid-template-columns: 220px minmax(0, 1fr);
  }

  .discover-page,
  .workspace-page {
    width: calc(100vw - 270px);
  }

  .featured-grid,
  .topic-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .workspace-lower,
  .wiki-page {
    grid-template-columns: 1fr;
  }

  .wiki-library {
    display: none;
  }
}

@media (max-width: 780px) {
  .ima-app {
    grid-template-columns: 1fr;
  }

  .ima-sidebar {
    min-height: auto;
    padding: 12px;
    position: sticky;
    top: 0;
    z-index: 20;
    border-right: 0;
    border-bottom: 1px solid #d7e1dc;
  }

  .ima-brand,
  .sidebar-spacer,
  .ima-nav.low {
    display: none;
  }

  .new-chat-btn {
    height: 42px;
  }

  .ima-nav {
    grid-template-columns: repeat(5, minmax(0, 1fr));
    gap: 6px;
  }

  .ima-nav button {
    height: 40px;
    padding: 0 8px;
    justify-content: center;
    gap: 6px;
    font-size: 12px;
  }

  .ima-topbar {
    height: auto;
    padding: 14px;
    align-items: stretch;
    flex-direction: column;
  }

  .top-search.compact {
    opacity: 1;
    pointer-events: auto;
  }

  .top-actions {
    justify-content: flex-end;
  }

  .discover-page,
  .workspace-page {
    width: 100%;
    padding: 16px;
  }

  .hero-query {
    margin-top: 0;
  }

  .hero-query textarea {
    padding: 20px;
    font-size: 18px;
  }

  .hero-tools {
    flex-wrap: wrap;
  }

  .hero-tools .ghost-icon {
    margin-left: 0;
  }

  .featured-grid,
  .topic-grid,
  .stats-row,
  .kb-card-grid {
    grid-template-columns: 1fr;
  }

  .workspace-hero {
    align-items: flex-start;
    flex-direction: column;
  }

  .workspace-hero h1 {
    font-size: 34px;
  }

  .workspace-preview {
    height: 320px;
  }

  .wiki-page {
    min-height: auto;
    border-top: 0;
  }

  .wiki-breadcrumb {
    height: auto;
    padding: 14px;
    flex-wrap: wrap;
  }

  .wiki-breadcrumb > div {
    width: 100%;
    margin-left: 0;
  }

  .wiki-empty,
  .wiki-input {
    width: calc(100% - 28px);
  }

  .wiki-empty {
    transform: none;
    margin: 40px auto 180px;
  }
}
</style>
