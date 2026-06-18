<script setup lang="ts">
import { computed, nextTick, onMounted, ref, watch } from 'vue';
import type { Component } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { NIcon, NModal, useMessage } from 'naive-ui';
import {
  AddOutline,
  AnalyticsOutline,
  AppsOutline,
  ArchiveOutline,
  ArrowForwardOutline,
  AttachOutline,
  BarChartOutline,
  BookOutline,
  BriefcaseOutline,
  BuildOutline,
  CalendarOutline,
  ChatbubbleEllipsesOutline,
  CheckmarkCircleOutline,
  ChevronDownOutline,
  ChevronForwardOutline,
  CloudUploadOutline,
  CodeSlashOutline,
  CopyOutline,
  CreateOutline,
  DocumentAttachOutline,
  DocumentTextOutline,
  DocumentsOutline,
  EarthOutline,
  EllipsisHorizontalOutline,
  ExtensionPuzzleOutline,
  FolderOpenOutline,
  FolderOutline,
  GridOutline,
  ImageOutline,
  LanguageOutline,
  LibraryOutline,
  LinkOutline,
  ListOutline,
  MailOutline,
  MapOutline,
  MegaphoneOutline,
  OpenOutline,
  PeopleOutline,
  PersonOutline,
  PieChartOutline,
  PricetagOutline,
  ReaderOutline,
  RefreshOutline,
  SchoolOutline,
  SearchOutline,
  SendOutline,
  ServerOutline,
  ShareSocialOutline,
  ShieldCheckmarkOutline,
  SparklesOutline,
  StarOutline,
  StatsChartOutline,
  StorefrontOutline,
  TimeOutline,
  TrashOutline,
  TrendingUpOutline,
} from '@vicons/ionicons5';
import { aiChat, aiRagChat } from '@/api/ai';
import { copyTextToClipboard } from '@/utils/clipboard';
import type { AiCitation } from '@/types/ai';

type AiSection = 'chat' | 'agents' | 'plugins' | 'knowledge';
type ChatRole = 'user' | 'assistant';
type AgentCategory = '全部' | '通用助手' | '工作效率' | '学习教育' | '代码编程' | '生活娱乐' | '行业应用';
type PluginCategory = '全部插件' | '热门推荐' | 'AI 能力' | '搜索工具' | '内容创作' | '效率工具' | '数据分析' | '生活服务' | '开发工具' | '企业服务';
type KnowledgeCategory = '全部知识库' | '产品文档' | '技术文档' | '学习资料' | '公司制度' | '市场与销售';

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
};

type AiModel = {
  id: string;
  name: string;
  provider: string;
  desc: string;
  tag?: string;
  color: string;
};

type ToolAction = {
  key: string;
  label: string;
  tip: string;
  icon: Component;
  prompt: string;
};

type Agent = {
  id: string;
  name: string;
  desc: string;
  category: AgentCategory;
  icon: Component;
  color: string;
  users: number;
  rating: number;
  tag: string;
  badge?: string;
  mine?: boolean;
  updatedAt?: string;
};

type Plugin = {
  id: string;
  name: string;
  desc: string;
  category: PluginCategory;
  icon: Component;
  color: string;
  uses: number;
  rating: number;
  official?: boolean;
  trend?: number;
  listedAt?: string;
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

const CHAT_STORAGE_KEY = 'campus-ai-conversations';
const MODEL_KEY = 'campus-ai-model';
const AGENT_STORAGE_KEY = 'campus-ai-custom-agents';
const AGENT_FAVORITE_KEY = 'campus-ai-agent-favorites';
const PLUGIN_INSTALL_KEY = 'campus-ai-installed-plugins';
const KNOWLEDGE_STORAGE_KEY = 'campus-ai-knowledge-bases';

const route = useRoute();
const router = useRouter();
const message = useMessage();

const sectionList: AiSection[] = ['chat', 'agents', 'plugins', 'knowledge'];
const activeSection = computed<AiSection>(() => {
  const section = route.query.section;
  return typeof section === 'string' && sectionList.includes(section as AiSection)
    ? (section as AiSection)
    : 'chat';
});

const pageSearch = ref('');
watch(
  () => route.query.q,
  (value) => {
    pageSearch.value = typeof value === 'string' ? value : '';
  },
  { immediate: true },
);

const draft = ref('');
const loading = ref(false);
const webSearchEnabled = ref(true);
const chatStreamRef = ref<HTMLElement | null>(null);
const fileInputRef = ref<HTMLInputElement | null>(null);
const activeLeftNav = ref<'chat' | 'favorite' | 'history'>('chat');
const currentConversationId = ref('');
const attachedContexts = ref<{ name: string; content: string }[]>([]);

const modelOptions: AiModel[] = [
  {
    id: 'deepseek-v4-flash',
    name: 'DeepSeek V4 Flash',
    provider: 'DeepSeek',
    desc: '响应速度优先，适合日常问答、摘要和学习规划',
    tag: '推荐',
    color: '#00bfa8',
  },
  {
    id: 'deepseek-v4-pro',
    name: 'DeepSeek V4 Pro',
    provider: 'DeepSeek',
    desc: '推理能力更强，适合复杂分析和长链路拆解',
    color: '#2563eb',
  },
  {
    id: 'mimo-v2.5',
    name: 'MiMo 2.5',
    provider: 'MiMo',
    desc: '通用中文模型，适合学习场景和轻量创作',
    color: '#f97316',
  },
  {
    id: 'mimo-v2.5-pro',
    name: 'MiMo 2.5 Pro',
    provider: 'MiMo',
    desc: '进阶版本，适合深度解释、代码和文档分析',
    color: '#8b5cf6',
  },
];

const selectedModel = ref(modelOptions[0].id);
const conversations = ref<Conversation[]>([]);

const toolActions: ToolAction[] = [
  {
    key: 'code',
    label: '代码解释',
    tip: '生成代码解释提示词',
    icon: CodeSlashOutline,
    prompt: '请解释下面这段代码的作用、关键流程、潜在问题，并给出可改进建议：\n\n',
  },
  {
    key: 'doc',
    label: '文档分析',
    tip: '分析已上传或粘贴的文档内容',
    icon: DocumentTextOutline,
    prompt: '请分析这份文档，提炼主题、核心结论、风险点和下一步行动：\n\n',
  },
  {
    key: 'summary',
    label: '快速总结',
    tip: '整理成简洁摘要',
    icon: SparklesOutline,
    prompt: '请把下面内容总结为 5 条以内的要点，并保留可执行建议：\n\n',
  },
  {
    key: 'translate',
    label: '中英互译',
    tip: '翻译并保留术语',
    icon: LanguageOutline,
    prompt: '请翻译下面内容，保留专业术语，并在必要处补充简短解释：\n\n',
  },
  {
    key: 'imagePrompt',
    label: '图片提示词',
    tip: '生成可用于绘图模型的提示词',
    icon: ImageOutline,
    prompt: '请根据我的描述生成一段高质量图像生成提示词，包含主体、场景、风格、光线、构图和负面提示词：\n\n',
  },
];

const quickQuestionPool = [
  '如何制定期末复习计划？',
  '有哪些高效的记忆方法？',
  '如何克服考试焦虑？',
  '帮我整理一份今日学习清单',
  '论文开题应该怎么准备？',
  '如何提高课堂笔记质量？',
];
const quickQuestionOffset = ref(0);
const quickQuestions = computed(() =>
  Array.from({ length: 3 }, (_, index) => quickQuestionPool[(quickQuestionOffset.value + index) % quickQuestionPool.length]),
);

const baseAgents: Agent[] = [
  {
    id: 'writer',
    name: '文案写作大师',
    desc: '擅长各类文案创作，包括营销文案、产品介绍、邮件等',
    category: '通用助手',
    icon: DocumentTextOutline,
    color: '#18c7a7',
    users: 12500,
    rating: 4.9,
    tag: '写作',
    badge: '精选',
  },
  {
    id: 'code',
    name: '代码助手',
    desc: '编程问题解答、代码生成与优化，支持多种编程语言',
    category: '代码编程',
    icon: CodeSlashOutline,
    color: '#845ef7',
    users: 9800,
    rating: 4.8,
    tag: '代码编程',
  },
  {
    id: 'analyst',
    name: '数据分析师',
    desc: '数据清洗、分析、可视化，提供专业的数据洞察',
    category: '工作效率',
    icon: AnalyticsOutline,
    color: '#ff7a3d',
    users: 8600,
    rating: 4.8,
    tag: '工作效率',
  },
  {
    id: 'study',
    name: '学习小助手',
    desc: '解答学习问题，提供知识讲解和学习建议',
    category: '学习教育',
    icon: SchoolOutline,
    color: '#3b82f6',
    users: 7200,
    rating: 4.9,
    tag: '学习教育',
  },
  {
    id: 'translator',
    name: '翻译专家',
    desc: '专业多语言翻译助手，支持多种语言互译，提供地道准确的翻译结果',
    category: '通用助手',
    icon: EarthOutline,
    color: '#3b82f6',
    users: 6300,
    rating: 4.7,
    tag: '翻译',
  },
  {
    id: 'travel',
    name: '旅行规划师',
    desc: '根据你的需求定制个性化旅行计划，包括行程安排、景点推荐、美食指南等',
    category: '生活娱乐',
    icon: MapOutline,
    color: '#f43f75',
    users: 5100,
    rating: 4.6,
    tag: '生活娱乐',
  },
  {
    id: 'ppt',
    name: 'PPT 生成助手',
    desc: '根据主题快速生成专业的 PPT 大纲和内容，支持多种模板和风格',
    category: '工作效率',
    icon: ReaderOutline,
    color: '#12b886',
    users: 4800,
    rating: 4.8,
    tag: '工作效率',
  },
  {
    id: 'meeting',
    name: '会议纪要助手',
    desc: '整理会议记录，提炼关键结论、待办事项和负责人',
    category: '工作效率',
    icon: CalendarOutline,
    color: '#fb923c',
    users: 3900,
    rating: 4.7,
    tag: '会议',
  },
];

const agentCategories: { name: AgentCategory; icon: Component }[] = [
  { name: '全部', icon: AppsOutline },
  { name: '通用助手', icon: StarOutline },
  { name: '工作效率', icon: BriefcaseOutline },
  { name: '学习教育', icon: BookOutline },
  { name: '代码编程', icon: CodeSlashOutline },
  { name: '生活娱乐', icon: CalendarOutline },
  { name: '行业应用', icon: StorefrontOutline },
];
const agentSubTabs = [
  { key: 'recommend', label: '推荐' },
  { key: 'latest', label: '最新' },
  { key: 'popular', label: '最受欢迎' },
  { key: 'rating', label: '高评分' },
] as const;
const activeAgentCategory = ref<AgentCategory>('全部');
const activeAgentTab = ref<(typeof agentSubTabs)[number]['key']>('recommend');
const customAgents = ref<Agent[]>([]);
const favoriteAgentIds = ref<string[]>([]);
const agentCreateVisible = ref(false);
const agentDetailVisible = ref(false);
const selectedAgent = ref<Agent | null>(null);
const agentDraft = ref({
  name: '',
  category: '通用助手' as AgentCategory,
  desc: '',
  abilities: '',
});

const plugins: Plugin[] = [
  {
    id: 'weather',
    name: '天气查询',
    desc: '获取实时天气信息和未来多天天气预报',
    category: '生活服务',
    icon: CloudUploadOutline,
    color: '#38bdf8',
    uses: 12400,
    rating: 4.8,
    official: true,
    trend: 3,
  },
  {
    id: 'translate',
    name: '智能翻译',
    desc: '支持多种语言互译，精准快速，支持文本和文档翻译',
    category: 'AI 能力',
    icon: LanguageOutline,
    color: '#8b5cf6',
    uses: 9800,
    rating: 4.7,
    official: true,
    trend: 8,
  },
  {
    id: 'excel',
    name: 'Excel 处理',
    desc: '读取、导入和处理 Excel 文件，支持公式计算',
    category: '效率工具',
    icon: DocumentsOutline,
    color: '#10b981',
    uses: 8200,
    rating: 4.7,
    official: true,
    trend: -1,
  },
  {
    id: 'pdf',
    name: 'PDF 解析',
    desc: '提取 PDF 文档内容，支持表格、图片和文本解析',
    category: '效率工具',
    icon: DocumentAttachOutline,
    color: '#f56565',
    uses: 7100,
    rating: 4.8,
    official: true,
    trend: 5,
  },
  {
    id: 'web-search',
    name: '联网搜索',
    desc: '实时搜索互联网信息，获取最新资讯和资料',
    category: '搜索工具',
    icon: SearchOutline,
    color: '#3b82f6',
    uses: 6500,
    rating: 4.8,
    official: true,
    trend: 12,
  },
  {
    id: 'baidu',
    name: '必应搜索',
    desc: '使用必应搜索引擎获取准确的网页结果',
    category: '搜索工具',
    icon: SearchOutline,
    color: '#2f9eff',
    uses: 5600,
    rating: 4.8,
  },
  {
    id: 'image-generate',
    name: '图片生成',
    desc: '根据描述生成高质量图片',
    category: 'AI 能力',
    icon: ImageOutline,
    color: '#a78bfa',
    uses: 4200,
    rating: 4.7,
  },
  {
    id: 'code-explain',
    name: '代码解释器',
    desc: '运行和解释代码，支持多种编程语言',
    category: '开发工具',
    icon: CodeSlashOutline,
    color: '#1e3a8a',
    uses: 3800,
    rating: 4.9,
  },
  {
    id: 'news',
    name: '新闻聚合',
    desc: '聚合最新新闻资讯，支持自定义订阅',
    category: '内容创作',
    icon: MegaphoneOutline,
    color: '#14b8a6',
    uses: 3200,
    rating: 4.6,
  },
  {
    id: 'mindmap',
    name: '思维导图',
    desc: '根据内容自动生成思维导图',
    category: '效率工具',
    icon: ShareSocialOutline,
    color: '#4f7cff',
    uses: 2900,
    rating: 4.7,
  },
  {
    id: 'map',
    name: '地图查询',
    desc: '查询地点信息、路线规划和周边设施',
    category: '生活服务',
    icon: MapOutline,
    color: '#22c55e',
    uses: 2400,
    rating: 4.5,
  },
  {
    id: 'database',
    name: '数据库查询',
    desc: '连接数据库，执行查询和数据分析',
    category: '数据分析',
    icon: ServerOutline,
    color: '#0f766e',
    uses: 2100,
    rating: 4.8,
  },
  {
    id: 'email',
    name: '邮件发送',
    desc: '发送邮件，支持附件和模板',
    category: '效率工具',
    icon: MailOutline,
    color: '#7c3aed',
    uses: 1900,
    rating: 4.4,
  },
];

const pluginCategories: { name: PluginCategory; icon: Component; count: number }[] = [
  { name: '全部插件', icon: AppsOutline, count: 128 },
  { name: '热门推荐', icon: StarOutline, count: 12 },
  { name: 'AI 能力', icon: SparklesOutline, count: 18 },
  { name: '搜索工具', icon: SearchOutline, count: 16 },
  { name: '内容创作', icon: CreateOutline, count: 21 },
  { name: '效率工具', icon: BriefcaseOutline, count: 19 },
  { name: '数据分析', icon: StatsChartOutline, count: 14 },
  { name: '生活服务', icon: CalendarOutline, count: 12 },
  { name: '开发工具', icon: CodeSlashOutline, count: 8 },
  { name: '企业服务', icon: ShieldCheckmarkOutline, count: 8 },
];
const pluginTabs = [
  { key: 'all', label: '全部' },
  { key: 'official', label: '官方插件' },
  { key: 'featured', label: '精选插件' },
  { key: 'latest', label: '最新上架' },
] as const;
const activePluginCategory = ref<PluginCategory>('全部插件');
const activePluginTab = ref<(typeof pluginTabs)[number]['key']>('all');
const pluginSort = ref('综合排序');
const installedPluginIds = ref<string[]>([]);
const pluginDetailVisible = ref(false);
const developerVisible = ref(false);
const pluginDocVisible = ref(false);
const selectedPlugin = ref<Plugin | null>(null);

const knowledgeCategories: { name: KnowledgeCategory; icon: Component }[] = [
  { name: '全部知识库', icon: FolderOpenOutline },
  { name: '产品文档', icon: FolderOutline },
  { name: '技术文档', icon: FolderOutline },
  { name: '学习资料', icon: FolderOutline },
  { name: '公司制度', icon: FolderOutline },
  { name: '市场与销售', icon: FolderOutline },
];
const knowledgeTabs = [
  { key: 'all', label: '全部' },
  { key: 'mine', label: '我创建的' },
  { key: 'shared', label: '共享给我的' },
  { key: 'favorite', label: '我收藏的' },
] as const;
const defaultKnowledgeBases: KnowledgeBase[] = [
  {
    id: 'product-help',
    name: '产品帮助文档',
    desc: '包含产品使用说明、功能介绍、常见问题等',
    category: '产品文档',
    type: '产品文档',
    docs: 156,
    vectors: 458642,
    updatedAt: '2024-05-20 14:30',
    owner: '我创建的',
    favorite: true,
    color: '#a3e635',
  },
  {
    id: 'tech-manual',
    name: '技术开发手册',
    desc: '开发规范、API 文档、技术方案等',
    category: '技术文档',
    type: '技术文档',
    docs: 82,
    vectors: 256721,
    updatedAt: '2024-05-18 09:15',
    owner: '我创建的',
    favorite: false,
    color: '#60a5fa',
  },
  {
    id: 'company-policy',
    name: '公司制度与流程',
    desc: '公司规章制度、业务流程、管理规范',
    category: '公司制度',
    type: '公司制度',
    docs: 65,
    vectors: 125843,
    updatedAt: '2024-05-17 16:45',
    owner: '共享给我的',
    favorite: false,
    color: '#fb7185',
  },
  {
    id: 'marketing',
    name: '市场营销资料',
    desc: '市场分析、竞品资料、营销方案等',
    category: '市场与销售',
    type: '市场资料',
    docs: 132,
    vectors: 389512,
    updatedAt: '2024-05-15 11:20',
    owner: '我创建的',
    favorite: false,
    color: '#34d399',
  },
  {
    id: 'training',
    name: '新员工培训资料',
    desc: '入职培训、岗位知识、学习资料等',
    category: '学习资料',
    type: '学习资料',
    docs: 98,
    vectors: 245678,
    updatedAt: '2024-05-14 10:05',
    owner: '共享给我的',
    favorite: false,
    color: '#60a5fa',
  },
  {
    id: 'customer-service',
    name: '客户服务知识库',
    desc: '客服话术、问题解决方案、服务流程',
    category: '产品文档',
    type: '服务支持',
    docs: 75,
    vectors: 198341,
    updatedAt: '2024-05-12 15:30',
    owner: '我创建的',
    favorite: false,
    color: '#d946ef',
  },
];

const knowledgeBases = ref<KnowledgeBase[]>([]);
const activeKnowledgeCategory = ref<KnowledgeCategory>('全部知识库');
const activeKnowledgeTab = ref<(typeof knowledgeTabs)[number]['key']>('all');
const knowledgeView = ref<'grid' | 'list'>('list');
const knowledgeTypeFilter = ref('全部类型');
const knowledgeSort = ref('最近更新');
const selectedKnowledgeIds = ref<string[]>([]);
const knowledgeCreateVisible = ref(false);
const knowledgeImportVisible = ref(false);
const knowledgeDetailVisible = ref(false);
const selectedKnowledge = ref<KnowledgeBase | null>(null);
const knowledgeDraft = ref({
  name: '',
  category: '产品文档' as KnowledgeCategory,
  desc: '',
});
const importDraft = ref({
  targetId: 'product-help',
  files: '',
  tags: '',
});

const currentConversation = computed(() =>
  conversations.value.find((item) => item.id === currentConversationId.value) ?? conversations.value[0],
);
const messages = computed({
  get: () => currentConversation.value?.messages ?? [],
  set: (value: ChatMessage[]) => {
    if (!currentConversation.value) return;
    currentConversation.value.messages = value;
    currentConversation.value.updatedAt = Date.now();
  },
});
const visibleConversations = computed(() => {
  if (activeLeftNav.value === 'favorite') return conversations.value.filter((item) => item.favorite);
  return [...conversations.value].sort((a, b) => b.updatedAt - a.updatedAt);
});
const selectedModelInfo = computed(() => modelOptions.find((item) => item.id === selectedModel.value) ?? modelOptions[0]);
const chatTitle = computed(() => currentConversation.value?.title || '新的对话');
const enabledAbilities = computed(() => (webSearchEnabled.value ? ['web-search'] : []));
const contextText = computed(() => {
  if (attachedContexts.value.length === 0) return '';
  return attachedContexts.value.map((item) => `【用户上传文件：${item.name}】\n${item.content}`).join('\n\n');
});
const stats = computed(() => [
  { value: conversations.value.length, label: '本地对话' },
  { value: conversations.value.filter((item) => item.favorite).length, label: '收藏' },
  { value: messages.value.filter((item) => item.role === 'assistant').length, label: '本轮回复' },
]);
const abilityStatus = computed(() => [
  {
    label: '站内检索',
    desc: webSearchEnabled.value ? '发送时会检索帖子、资源和学习圈' : '已关闭，仅进行普通对话',
    icon: EarthOutline,
    status: webSearchEnabled.value ? '已开启' : '已关闭',
  },
  {
    label: '代码解释',
    desc: '输入区工具可生成代码解释提示词',
    icon: CodeSlashOutline,
    status: '可用',
  },
  {
    label: '文档分析',
    desc: '支持上传 txt、md、json、csv 等文本文件',
    icon: DocumentTextOutline,
    status: attachedContexts.value.length ? `${attachedContexts.value.length} 个文件` : '待上传',
  },
]);

const allAgents = computed(() => [...customAgents.value, ...baseAgents]);
const filteredAgents = computed(() => {
  const keyword = pageSearch.value.trim().toLowerCase();
  let list = allAgents.value.filter((item) => {
    const inCategory = activeAgentCategory.value === '全部' || item.category === activeAgentCategory.value;
    const inKeyword = !keyword || `${item.name} ${item.desc} ${item.tag}`.toLowerCase().includes(keyword);
    return inCategory && inKeyword;
  });
  if (activeAgentTab.value === 'popular') list = [...list].sort((a, b) => b.users - a.users);
  if (activeAgentTab.value === 'rating') list = [...list].sort((a, b) => b.rating - a.rating);
  if (activeAgentTab.value === 'latest') list = [...list].sort((a, b) => Number(Boolean(b.mine)) - Number(Boolean(a.mine)));
  return list;
});
const featuredAgents = computed(() => allAgents.value.slice(0, 4));
const hotAgents = computed(() => filteredAgents.value.slice(4, 10));
const myAgents = computed(() => allAgents.value.filter((item) => item.mine || favoriteAgentIds.value.includes(item.id)).slice(0, 3));

const filteredPlugins = computed(() => {
  const keyword = pageSearch.value.trim().toLowerCase();
  let list = plugins.filter((item) => {
    const inCategory = activePluginCategory.value === '全部插件' || item.category === activePluginCategory.value;
    const inKeyword = !keyword || `${item.name} ${item.desc} ${item.category}`.toLowerCase().includes(keyword);
    const inTab =
      activePluginTab.value === 'all' ||
      (activePluginTab.value === 'official' && item.official) ||
      (activePluginTab.value === 'featured' && item.rating >= 4.8) ||
      (activePluginTab.value === 'latest' && Boolean(item.listedAt));
    return inCategory && inKeyword && inTab;
  });
  if (pluginSort.value === '评分最高') list = [...list].sort((a, b) => b.rating - a.rating);
  if (pluginSort.value === '使用最多') list = [...list].sort((a, b) => b.uses - a.uses);
  return list;
});
const recommendedPlugins = computed(() => plugins.slice(0, 5));
const pluginRanking = computed(() => [...plugins].sort((a, b) => b.uses - a.uses).slice(0, 5));
const latestPlugins = computed(() => [
  { name: '视频总结', category: 'AI 能力', time: '2 天前上架', icon: SparklesOutline, color: '#0f766e' },
  { name: '网页摘要', category: '效率工具', time: '3 天前上架', icon: DocumentTextOutline, color: '#64748b' },
  { name: '表格识别', category: 'AI 能力', time: '5 天前上架', icon: GridOutline, color: '#22c55e' },
  { name: '日程管理', category: '效率工具', time: '1 周前上架', icon: CalendarOutline, color: '#3b82f6' },
  { name: '知识图谱', category: '数据分析', time: '1 周前上架', icon: ShareSocialOutline, color: '#6366f1' },
]);

const filteredKnowledgeBases = computed(() => {
  const keyword = pageSearch.value.trim().toLowerCase();
  let list = knowledgeBases.value.filter((item) => {
    const inCategory = activeKnowledgeCategory.value === '全部知识库' || item.category === activeKnowledgeCategory.value;
    const inKeyword = !keyword || `${item.name} ${item.desc} ${item.type}`.toLowerCase().includes(keyword);
    const inType = knowledgeTypeFilter.value === '全部类型' || item.type === knowledgeTypeFilter.value;
    const inTab =
      activeKnowledgeTab.value === 'all' ||
      (activeKnowledgeTab.value === 'mine' && item.owner === '我创建的') ||
      (activeKnowledgeTab.value === 'shared' && item.owner === '共享给我的') ||
      (activeKnowledgeTab.value === 'favorite' && item.favorite);
    return inCategory && inKeyword && inType && inTab;
  });
  if (knowledgeSort.value === '文档最多') list = [...list].sort((a, b) => b.docs - a.docs);
  if (knowledgeSort.value === '向量最多') list = [...list].sort((a, b) => b.vectors - a.vectors);
  return list;
});
const knowledgeStats = computed(() => {
  const docs = knowledgeBases.value.reduce((sum, item) => sum + item.docs, 0);
  const vectors = knowledgeBases.value.reduce((sum, item) => sum + item.vectors, 0);
  return [
    { label: '知识库总数', value: knowledgeBases.value.length, delta: '+4', icon: LibraryOutline, color: '#18c7a7' },
    { label: '文档总数', value: docs, delta: '+86', icon: DocumentsOutline, color: '#f59e0b' },
    { label: '向量总数', value: vectors, delta: '+245,721', icon: LinkOutline, color: '#ff6b4a' },
  ];
});
const recentKnowledge = computed(() => knowledgeBases.value.slice(0, 4));
const selectedKnowledgeCount = computed(() => selectedKnowledgeIds.value.length);
const allKnowledgeSelected = computed(() =>
  filteredKnowledgeBases.value.length > 0 &&
  filteredKnowledgeBases.value.every((item) => selectedKnowledgeIds.value.includes(item.id)),
);

function nowLabel() {
  return new Date().toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' });
}

function formatCompact(value: number) {
  if (value >= 10000) return `${(value / 10000).toFixed(value >= 100000 ? 0 : 1)}w`;
  return value.toLocaleString('zh-CN');
}

function createConversation(title = '新的对话'): Conversation {
  const id = `chat-${Date.now()}-${Math.random().toString(16).slice(2)}`;
  return {
    id,
    title,
    updatedAt: Date.now(),
    favorite: false,
    messages: [
      {
        role: 'assistant',
        content: '你好，我是小青，青云阁的 AI 助手。你可以直接提问，也可以先选择下方工具生成更清晰的提示词。',
        time: nowLabel(),
      },
    ],
  };
}

function loadLocalState() {
  const storedModel = localStorage.getItem(MODEL_KEY);
  if (storedModel && modelOptions.some((item) => item.id === storedModel)) selectedModel.value = storedModel;

  try {
    const raw = localStorage.getItem(CHAT_STORAGE_KEY);
    const parsed = raw ? (JSON.parse(raw) as Conversation[]) : [];
    conversations.value = parsed.length ? parsed : [createConversation('如何高效准备期末考试？')];
  } catch {
    conversations.value = [createConversation()];
  }
  currentConversationId.value = conversations.value[0].id;

  try {
    customAgents.value = JSON.parse(localStorage.getItem(AGENT_STORAGE_KEY) || '[]');
  } catch {
    customAgents.value = [];
  }
  try {
    favoriteAgentIds.value = JSON.parse(localStorage.getItem(AGENT_FAVORITE_KEY) || '[]');
  } catch {
    favoriteAgentIds.value = [];
  }
  try {
    installedPluginIds.value = JSON.parse(localStorage.getItem(PLUGIN_INSTALL_KEY) || '["weather","translate"]');
  } catch {
    installedPluginIds.value = ['weather', 'translate'];
  }
  try {
    const storedKnowledge = JSON.parse(localStorage.getItem(KNOWLEDGE_STORAGE_KEY) || '[]') as KnowledgeBase[];
    knowledgeBases.value = storedKnowledge.length ? storedKnowledge : defaultKnowledgeBases.map((item) => ({ ...item }));
  } catch {
    knowledgeBases.value = defaultKnowledgeBases.map((item) => ({ ...item }));
  }
}

watch(
  conversations,
  (value) => localStorage.setItem(CHAT_STORAGE_KEY, JSON.stringify(value.slice(0, 30))),
  { deep: true },
);
watch(selectedModel, (value) => localStorage.setItem(MODEL_KEY, value));
watch(customAgents, (value) => localStorage.setItem(AGENT_STORAGE_KEY, JSON.stringify(value)), { deep: true });
watch(favoriteAgentIds, (value) => localStorage.setItem(AGENT_FAVORITE_KEY, JSON.stringify(value)), { deep: true });
watch(installedPluginIds, (value) => localStorage.setItem(PLUGIN_INSTALL_KEY, JSON.stringify(value)), { deep: true });
watch(knowledgeBases, (value) => localStorage.setItem(KNOWLEDGE_STORAGE_KEY, JSON.stringify(value)), { deep: true });

async function scrollToBottom() {
  await nextTick();
  if (chatStreamRef.value) chatStreamRef.value.scrollTop = chatStreamRef.value.scrollHeight;
}

function normalizeTitle(question: string) {
  const compact = question.replace(/\s+/g, ' ').trim();
  return compact.length > 24 ? `${compact.slice(0, 24)}...` : compact || '新的对话';
}

async function sendQuestion() {
  const question = draft.value.trim();
  if (!question || loading.value) return;

  draft.value = '';
  if (!currentConversation.value) {
    const chat = createConversation();
    conversations.value.unshift(chat);
    currentConversationId.value = chat.id;
  }
  if (currentConversation.value.title === '新的对话') currentConversation.value.title = normalizeTitle(question);

  messages.value = [...messages.value, { role: 'user', content: question, time: nowLabel() }];
  loading.value = true;
  await scrollToBottom();

  try {
    const history = messages.value.slice(-10).map((item) => ({ role: item.role, content: item.content }));
    const result = webSearchEnabled.value
      ? await aiRagChat(history, contextText.value, selectedModel.value, enabledAbilities.value)
      : await aiChat(history, contextText.value, selectedModel.value, enabledAbilities.value);

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

function startNewChat() {
  const chat = createConversation();
  conversations.value.unshift(chat);
  currentConversationId.value = chat.id;
  activeLeftNav.value = 'chat';
  attachedContexts.value = [];
  draft.value = '';
  void router.push({ path: '/ai', query: { section: 'chat' } });
  void scrollToBottom();
}

function openConversation(id: string) {
  currentConversationId.value = id;
  activeLeftNav.value = 'chat';
  void router.push({ path: '/ai', query: { section: 'chat' } });
  void scrollToBottom();
}

function toggleFavorite(id = currentConversationId.value) {
  const target = conversations.value.find((item) => item.id === id);
  if (!target) return;
  target.favorite = !target.favorite;
  message.success(target.favorite ? '已加入收藏' : '已取消收藏');
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
  void sendQuestion();
}

function continueAnswer() {
  if (loading.value) return;
  draft.value = '请继续展开上一条回答，并补充更具体的步骤。';
  void sendQuestion();
}

function markAssistantFeedback(helpful: boolean) {
  message.success(helpful ? '已记录：这条回答有帮助' : '已记录：会减少类似回答方式');
}

function askQuickQuestion(question: string) {
  draft.value = question;
  void sendQuestion();
}

function refreshSuggestions() {
  quickQuestionOffset.value = (quickQuestionOffset.value + 3) % quickQuestionPool.length;
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
  message.info(webSearchEnabled.value ? '已开启站内检索增强' : '已关闭站内检索增强');
}

function applyToolPrompt(tool: ToolAction) {
  draft.value = draft.value.trim() ? `${tool.prompt}${draft.value.trim()}` : tool.prompt;
}

function selectModel(modelId: string) {
  selectedModel.value = modelId;
  message.success(`已切换到 ${selectedModelInfo.value.name}`);
}

function searchWithinAi() {
  void router.push({ path: '/ai', query: { ...route.query, q: pageSearch.value.trim(), focus: String(Date.now()) } });
}

function clearSearch() {
  pageSearch.value = '';
  const nextQuery = { ...route.query };
  delete nextQuery.q;
  void router.push({ path: '/ai', query: nextQuery });
}

function openAgentDetail(agent: Agent) {
  selectedAgent.value = agent;
  agentDetailVisible.value = true;
}

function useAgent(agent: Agent) {
  selectedAgent.value = agent;
  draft.value = `请以「${agent.name}」的能力帮助我：`;
  void router.push({ path: '/ai', query: { section: 'chat', agent: agent.id } });
  message.success(`已切换到 ${agent.name}`);
}

function toggleAgentFavorite(agent: Agent) {
  if (favoriteAgentIds.value.includes(agent.id)) {
    favoriteAgentIds.value = favoriteAgentIds.value.filter((id) => id !== agent.id);
    message.success('已取消收藏');
  } else {
    favoriteAgentIds.value = [...favoriteAgentIds.value, agent.id];
    message.success('已收藏智能体');
  }
}

function createAgent() {
  if (!agentDraft.value.name.trim()) {
    message.warning('请填写智能体名称');
    return;
  }
  const agent: Agent = {
    id: `custom-agent-${Date.now()}`,
    name: agentDraft.value.name.trim(),
    desc: agentDraft.value.desc.trim() || '自定义智能体，等待补充能力说明',
    category: agentDraft.value.category,
    icon: SparklesOutline,
    color: '#00bfa8',
    users: 1,
    rating: 5,
    tag: agentDraft.value.abilities.trim() || agentDraft.value.category,
    mine: true,
    updatedAt: '刚刚',
  };
  customAgents.value = [agent, ...customAgents.value];
  agentCreateVisible.value = false;
  agentDraft.value = { name: '', category: '通用助手', desc: '', abilities: '' };
  activeAgentTab.value = 'latest';
  message.success('智能体已创建到本地草稿');
}

function openPluginDetail(plugin: Plugin) {
  selectedPlugin.value = plugin;
  pluginDetailVisible.value = true;
}

function togglePluginInstall(plugin: Plugin) {
  if (installedPluginIds.value.includes(plugin.id)) {
    installedPluginIds.value = installedPluginIds.value.filter((id) => id !== plugin.id);
    message.success(`已卸载 ${plugin.name}`);
  } else {
    installedPluginIds.value = [...installedPluginIds.value, plugin.id];
    message.success(`已安装 ${plugin.name}`);
  }
}

function usePlugin(plugin: Plugin) {
  if (!installedPluginIds.value.includes(plugin.id)) togglePluginInstall(plugin);
  draft.value = `请调用「${plugin.name}」插件能力，帮我处理：`;
  void router.push({ path: '/ai', query: { section: 'chat', plugin: plugin.id } });
}

function createKnowledgeBase() {
  if (!knowledgeDraft.value.name.trim()) {
    message.warning('请填写知识库名称');
    return;
  }
  knowledgeBases.value = [
    {
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
      color: '#2dd4bf',
    },
    ...knowledgeBases.value,
  ];
  knowledgeCreateVisible.value = false;
  knowledgeDraft.value = { name: '', category: '产品文档', desc: '' };
  activeKnowledgeTab.value = 'mine';
  message.success('知识库已创建');
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
  knowledgeImportVisible.value = false;
  importDraft.value = { targetId: target.id, files: '', tags: '' };
  message.success(`已导入 ${fileCount} 份文档到 ${target.name}`);
}

function openKnowledgeDetail(item: KnowledgeBase) {
  selectedKnowledge.value = item;
  knowledgeDetailVisible.value = true;
}

function toggleKnowledgeFavorite(item: KnowledgeBase) {
  item.favorite = !item.favorite;
  message.success(item.favorite ? '已收藏知识库' : '已取消收藏');
}

function toggleKnowledgeSelection(id: string) {
  selectedKnowledgeIds.value = selectedKnowledgeIds.value.includes(id)
    ? selectedKnowledgeIds.value.filter((item) => item !== id)
    : [...selectedKnowledgeIds.value, id];
}

function toggleAllKnowledgeSelection() {
  if (allKnowledgeSelected.value) {
    const ids = filteredKnowledgeBases.value.map((item) => item.id);
    selectedKnowledgeIds.value = selectedKnowledgeIds.value.filter((id) => !ids.includes(id));
  } else {
    selectedKnowledgeIds.value = Array.from(new Set([...selectedKnowledgeIds.value, ...filteredKnowledgeBases.value.map((item) => item.id)]));
  }
}

function batchArchiveKnowledge() {
  if (!selectedKnowledgeIds.value.length) {
    message.warning('请先选择知识库');
    return;
  }
  selectedKnowledgeIds.value = [];
  message.success('已加入回收站队列，等待后端接入后执行真实归档');
}

function askKnowledge(item: KnowledgeBase) {
  draft.value = `请基于知识库「${item.name}」回答：`;
  void router.push({ path: '/ai', query: { section: 'chat', knowledgeBaseId: item.id } });
}

onMounted(() => {
  loadLocalState();
  void scrollToBottom();
});
</script>

<template>
  <div class="ai-workbench" :class="`is-${activeSection}`">
    <template v-if="activeSection === 'chat'">
      <aside class="ai-console-sidebar">
        <button class="ai-primary-action" title="新建对话" @click="startNewChat">
          <NIcon size="18"><AddOutline /></NIcon>
          <span>新建对话</span>
        </button>

        <section class="ai-nav-card">
          <button class="ai-side-link" :class="{ active: activeLeftNav === 'chat' }" @click="activeLeftNav = 'chat'">
            <NIcon size="18"><ChatbubbleEllipsesOutline /></NIcon>
            对话
          </button>
          <button class="ai-side-link" :class="{ active: activeLeftNav === 'favorite' }" @click="activeLeftNav = 'favorite'">
            <NIcon size="18"><StarOutline /></NIcon>
            收藏
          </button>
          <button class="ai-side-link" :class="{ active: activeLeftNav === 'history' }" @click="activeLeftNav = 'history'">
            <NIcon size="18"><TimeOutline /></NIcon>
            历史记录
          </button>
        </section>

        <section class="ai-nav-card chat-history-card">
          <div class="section-mini-title">{{ activeLeftNav === 'favorite' ? '收藏的对话' : '最近对话' }}</div>
          <button
            v-for="item in visibleConversations"
            :key="item.id"
            class="history-item"
            :class="{ active: item.id === currentConversationId }"
            @click="openConversation(item.id)"
          >
            <span>{{ item.title }}</span>
            <NIcon v-if="item.favorite" size="15"><StarOutline /></NIcon>
          </button>
          <div v-if="visibleConversations.length === 0" class="empty-tip">暂无记录</div>
        </section>

        <section class="ai-tip-card">
          <strong>当前服务</strong>
          <span>{{ selectedModelInfo.provider }} / {{ selectedModelInfo.name }}</span>
          <button @click="toggleFavorite()">
            <NIcon size="16"><StarOutline /></NIcon>
            {{ currentConversation?.favorite ? '取消收藏本对话' : '收藏本对话' }}
          </button>
        </section>
      </aside>

      <main class="chat-main-panel">
        <header class="chat-title">
          <h1>{{ chatTitle }}</h1>
        </header>

        <div ref="chatStreamRef" class="chat-stream">
          <article v-for="(item, index) in messages" :key="`${item.role}-${index}`" class="message-row" :class="item.role">
            <template v-if="item.role === 'user'">
              <div class="user-bubble">{{ item.content }}</div>
              <span class="message-time">{{ item.time }}</span>
              <span class="user-avatar">我</span>
            </template>

            <template v-else>
              <span class="ai-avatar"><NIcon size="18"><SparklesOutline /></NIcon></span>
              <div class="assistant-card">
                <header>
                  <strong>小青</strong>
                  <span>{{ item.time }}</span>
                </header>
                <pre>{{ item.content }}</pre>
                <div v-if="item.citations?.length" class="citation-list">
                  <strong>参考来源</strong>
                  <a v-for="source in item.citations" :key="`${source.type}-${source.id}`" :href="source.url">
                    {{ source.title }}
                  </a>
                </div>
                <footer>
                  <button title="复制回答" @click="copyAssistantAnswer(item.content)">
                    <NIcon size="15"><CopyOutline /></NIcon>
                    复制
                  </button>
                  <button title="重新生成这条回答" @click="retryAssistantAnswer(index)">
                    <NIcon size="15"><RefreshOutline /></NIcon>
                    再试一次
                  </button>
                  <button title="继续生成" @click="continueAnswer">
                    <NIcon size="15"><SparklesOutline /></NIcon>
                    继续
                  </button>
                  <button title="标记有帮助" @click="markAssistantFeedback(true)">
                    <NIcon size="15"><CheckmarkCircleOutline /></NIcon>
                    有帮助
                  </button>
                </footer>
              </div>
            </template>
          </article>

          <div v-if="loading" class="chat-loading">
            <span />
            <p>AI 正在组织回答...</p>
          </div>
        </div>

        <div class="quick-row">
          <button v-for="question in quickQuestions" :key="question" @click="askQuickQuestion(question)">
            {{ question }}
          </button>
          <button class="icon-only" title="换一批建议" @click="refreshSuggestions">
            <NIcon size="18"><RefreshOutline /></NIcon>
          </button>
        </div>

        <section class="input-panel">
          <div v-if="attachedContexts.length" class="attachment-list">
            <button v-for="item in attachedContexts" :key="item.name" @click="removeAttachment(item.name)">
              {{ item.name }} x
            </button>
          </div>
          <textarea
            v-model="draft"
            placeholder="输入你的问题，Enter 发送，Shift + Enter 换行"
            @keydown.enter.exact.prevent="sendQuestion"
          />
          <div class="input-tools">
            <div class="tool-cluster">
              <button title="上传文本文件作为上下文" :class="{ active: attachedContexts.length > 0 }" @click="triggerAttachmentPicker">
                <NIcon size="20"><AttachOutline /></NIcon>
              </button>
              <button title="站内检索增强" :class="{ active: webSearchEnabled }" @click="toggleWebSearch">
                <NIcon size="20"><EarthOutline /></NIcon>
              </button>
              <button v-for="tool in toolActions" :key="tool.key" :title="tool.tip" @click="applyToolPrompt(tool)">
                <NIcon size="20"><component :is="tool.icon" /></NIcon>
              </button>
            </div>

            <div class="send-cluster">
              <select v-model="selectedModel" class="model-select" aria-label="选择 AI 模型">
                <option v-for="model in modelOptions" :key="model.id" :value="model.id">{{ model.name }}</option>
              </select>
              <button class="send-btn" :disabled="loading || !draft.trim()" title="发送" @click="sendQuestion">
                <NIcon size="20"><SendOutline /></NIcon>
              </button>
            </div>
          </div>
          <input ref="fileInputRef" class="file-input" type="file" @change="handleFileChange" />
        </section>
      </main>

      <aside class="ai-console-right">
        <section class="right-card">
          <div class="panel-title">
            <h2>模型选择</h2>
          </div>
          <button
            v-for="model in modelOptions"
            :key="model.id"
            class="model-row"
            :class="{ active: selectedModel === model.id }"
            @click="selectModel(model.id)"
          >
            <span class="model-mark" :style="{ '--model-color': model.color }"><SparklesOutline /></span>
            <div>
              <strong>{{ model.name }}</strong>
              <p>{{ model.desc }}</p>
            </div>
            <em v-if="model.tag">{{ model.tag }}</em>
          </button>
        </section>

        <section class="right-card">
          <h2>能力状态</h2>
          <div class="ability-list">
            <div v-for="ability in abilityStatus" :key="ability.label" class="ability-row">
              <span class="ability-icon">
                <NIcon size="17"><component :is="ability.icon" /></NIcon>
              </span>
              <div>
                <strong>{{ ability.label }}</strong>
                <p>{{ ability.desc }}</p>
              </div>
              <em>{{ ability.status }}</em>
            </div>
          </div>
        </section>

        <section class="right-card stats-card">
          <h2>对话统计</h2>
          <div class="stats-grid">
            <div v-for="item in stats" :key="item.label">
              <strong>{{ item.value }}</strong>
              <span>{{ item.label }}</span>
            </div>
          </div>
        </section>
      </aside>
    </template>

    <template v-else-if="activeSection === 'agents'">
      <aside class="ai-console-sidebar">
        <button class="ai-primary-action" @click="agentCreateVisible = true">
          <NIcon size="18"><AddOutline /></NIcon>
          <span>创建智能体</span>
        </button>
        <section class="ai-nav-card">
          <button class="ai-side-link active" @click="activeAgentCategory = '全部'">
            <NIcon size="18"><AppsOutline /></NIcon>
            智能体广场
          </button>
          <button class="ai-side-link" @click="activeAgentTab = 'latest'">
            <NIcon size="18"><PersonOutline /></NIcon>
            我的智能体
          </button>
          <button class="ai-side-link" @click="message.success('已筛选使用记录，后端接入后展示真实记录')">
            <NIcon size="18"><TimeOutline /></NIcon>
            使用记录
          </button>
          <button class="ai-side-link" @click="activeAgentTab = 'rating'">
            <NIcon size="18"><StarOutline /></NIcon>
            我的收藏
          </button>
        </section>
        <section class="ai-nav-card">
          <div class="section-mini-title">分类</div>
          <button
            v-for="item in agentCategories"
            :key="item.name"
            class="ai-side-link"
            :class="{ active: activeAgentCategory === item.name }"
            @click="activeAgentCategory = item.name"
          >
            <NIcon size="17"><component :is="item.icon" /></NIcon>
            {{ item.name }}
          </button>
        </section>
        <section class="ai-tip-card illustrated">
          <strong>快速开始</strong>
          <span>创建你的专属智能体，让 AI 帮你完成更多工作</span>
          <button @click="agentCreateVisible = true">
            立即创建
            <NIcon size="15"><ArrowForwardOutline /></NIcon>
          </button>
        </section>
      </aside>

      <main class="ai-section-main">
        <header class="page-heading">
          <h1>智能体广场</h1>
          <p>探索、体验并创建专属于你的 AI 智能体</p>
        </header>

        <section class="agent-hero">
          <div>
            <span>Hi，玲玲</span>
            <h2>今天想让智能体帮你做些什么？</h2>
            <label class="hero-search">
              <NIcon size="18"><SearchOutline /></NIcon>
              <input v-model="pageSearch" placeholder="搜索智能体，或描述你想要的能力" @keyup.enter="searchWithinAi" />
              <button title="搜索智能体" @click="searchWithinAi">
                <NIcon size="18"><ArrowForwardOutline /></NIcon>
              </button>
            </label>
            <div class="hot-tags">
              <span>热门标签：</span>
              <button v-for="tag in ['写作', 'PPT 生成', '数据分析', '代码助手', '翻译', '旅行规划']" :key="tag" @click="pageSearch = tag; searchWithinAi()">
                {{ tag }}
              </button>
              <button v-if="pageSearch" @click="clearSearch">清空</button>
            </div>
          </div>
          <div class="hero-robot" aria-hidden="true">
            <span class="bot-head"><i /><i /></span>
            <span class="bot-ring" />
            <span class="float-card card-a"><TrendingUpOutline /></span>
            <span class="float-card card-b"><ImageOutline /></span>
            <span class="float-card card-c"><DocumentTextOutline /></span>
          </div>
        </section>

        <section class="section-block">
          <div class="block-title">
            <h2>精选推荐</h2>
            <button @click="activeAgentTab = 'popular'">查看全部 <NIcon size="15"><ChevronForwardOutline /></NIcon></button>
          </div>
          <div class="agent-card-grid">
            <article v-for="agent in featuredAgents" :key="agent.id" class="agent-card" @click="openAgentDetail(agent)">
              <span class="agent-icon" :style="{ '--item-color': agent.color }">
                <NIcon size="28"><component :is="agent.icon" /></NIcon>
              </span>
              <div>
                <h3>{{ agent.name }}</h3>
                <p>{{ agent.desc }}</p>
                <em>{{ agent.tag }}</em>
              </div>
              <footer>
                <span><NIcon size="14"><PeopleOutline /></NIcon>{{ formatCompact(agent.users) }}</span>
                <span><NIcon size="14"><StarOutline /></NIcon>{{ agent.rating }}</span>
              </footer>
            </article>
          </div>
        </section>

        <section class="section-block">
          <div class="block-title">
            <h2>热门智能体</h2>
            <div class="inline-tabs">
              <button
                v-for="tab in agentSubTabs"
                :key="tab.key"
                :class="{ active: activeAgentTab === tab.key }"
                @click="activeAgentTab = tab.key"
              >
                {{ tab.label }}
              </button>
            </div>
          </div>
          <div class="agent-list">
            <article v-for="agent in filteredAgents.slice(0, 8)" :key="agent.id" class="agent-list-row">
              <span class="agent-icon small" :style="{ '--item-color': agent.color }">
                <NIcon size="20"><component :is="agent.icon" /></NIcon>
              </span>
              <div>
                <h3>{{ agent.name }}</h3>
                <p>{{ agent.desc }}</p>
              </div>
              <em>{{ agent.tag }}</em>
              <span><NIcon size="14"><PeopleOutline /></NIcon>{{ formatCompact(agent.users) }}</span>
              <span><NIcon size="14"><StarOutline /></NIcon>{{ agent.rating }}</span>
              <button @click="useAgent(agent)">使用</button>
            </article>
          </div>
        </section>
      </main>

      <aside class="ai-console-right">
        <section class="right-card">
          <div class="block-title compact">
            <h2>我的智能体</h2>
            <button @click="activeAgentTab = 'latest'">查看全部 <NIcon size="14"><ChevronForwardOutline /></NIcon></button>
          </div>
          <div class="mini-agent-list">
            <button v-for="agent in myAgents" :key="agent.id" @click="useAgent(agent)">
              <span class="avatar-tile" :style="{ '--item-color': agent.color }"><component :is="agent.icon" /></span>
              <div>
                <strong>{{ agent.name }}</strong>
                <p>{{ agent.desc }}</p>
              </div>
            </button>
          </div>
        </section>
        <section class="right-card">
          <div class="block-title compact">
            <h2>智能体能力</h2>
            <button @click="message.success('已展开能力筛选')">更多能力 <NIcon size="14"><ChevronForwardOutline /></NIcon></button>
          </div>
          <div class="ability-tile-grid">
            <button v-for="ability in [
              { label: '知识问答', icon: BookOutline, desc: '基于知识库回答专业问题' },
              { label: '文档处理', icon: DocumentsOutline, desc: '阅读、分析和处理各类文档' },
              { label: '数据分析', icon: BarChartOutline, desc: '数据洞察、分析和可视化' },
              { label: '内容生成', icon: CreateOutline, desc: '生成高质量文本、报告和创意内容' },
            ]" :key="ability.label" @click="pageSearch = ability.label; searchWithinAi()">
              <NIcon size="20"><component :is="ability.icon" /></NIcon>
              <strong>{{ ability.label }}</strong>
              <span>{{ ability.desc }}</span>
            </button>
          </div>
        </section>
        <section class="right-card guide-card">
          <h2>创建指南</h2>
          <ol>
            <li><span>1</span><strong>选择模型</strong><p>选择合适的模型作为智能体核心</p></li>
            <li><span>2</span><strong>配置能力</strong><p>设置功能和行为方式</p></li>
            <li><span>3</span><strong>添加知识</strong><p>导入知识库或数据</p></li>
            <li><span>4</span><strong>发布使用</strong><p>完成配置后发布</p></li>
          </ol>
          <button @click="agentCreateVisible = true">查看详细教程</button>
        </section>
      </aside>
    </template>

    <template v-else-if="activeSection === 'plugins'">
      <aside class="ai-console-sidebar">
        <section class="ai-nav-card">
          <button class="ai-side-link active" @click="activePluginCategory = '全部插件'">
            <NIcon size="18"><ExtensionPuzzleOutline /></NIcon>
            插件中心
          </button>
          <button class="ai-side-link" @click="activePluginTab = 'official'">
            <NIcon size="18"><ArchiveOutline /></NIcon>
            已安装插件
          </button>
          <button class="ai-side-link" @click="developerVisible = true">
            <NIcon size="18"><BuildOutline /></NIcon>
            开发者中心
          </button>
        </section>
        <section class="ai-nav-card">
          <div class="section-mini-title">插件分类</div>
          <button
            v-for="item in pluginCategories"
            :key="item.name"
            class="ai-side-link with-count"
            :class="{ active: activePluginCategory === item.name }"
            @click="activePluginCategory = item.name"
          >
            <NIcon size="17"><component :is="item.icon" /></NIcon>
            <span>{{ item.name }}</span>
            <em>{{ item.count }}</em>
          </button>
        </section>
        <section class="ai-tip-card illustrated">
          <strong>开发者入驻</strong>
          <span>成为插件开发者，发布你的插件让更多人使用你的能力</span>
          <button @click="developerVisible = true">
            立即入驻
            <NIcon size="15"><ArrowForwardOutline /></NIcon>
          </button>
        </section>
      </aside>

      <main class="ai-section-main">
        <header class="page-heading">
          <h1>插件中心</h1>
          <p>探索和安装插件，扩展 AI 助手的能力边界</p>
        </header>
        <section class="plugin-hero">
          <div>
            <h2>让 AI 更强大，插件让一切成为可能</h2>
            <p>接入丰富的插件能力，为你的智能体供能</p>
            <label class="hero-search">
              <input v-model="pageSearch" placeholder="搜索插件名称或功能，例如：天气、翻译、解析等" @keyup.enter="searchWithinAi" />
              <button title="搜索插件" @click="searchWithinAi"><NIcon size="18"><SearchOutline /></NIcon></button>
            </label>
          </div>
          <div class="plugin-visual" aria-hidden="true">
            <span><ExtensionPuzzleOutline /></span>
            <i />
            <i />
            <i />
          </div>
        </section>

        <section class="section-block">
          <div class="block-title">
            <h2>热门推荐</h2>
            <button @click="activePluginTab = 'featured'">查看全部 <NIcon size="15"><ChevronForwardOutline /></NIcon></button>
          </div>
          <div class="plugin-recommend-grid">
            <article v-for="plugin in recommendedPlugins" :key="plugin.id" class="plugin-card">
              <header>
                <span class="agent-icon small" :style="{ '--item-color': plugin.color }"><NIcon size="21"><component :is="plugin.icon" /></NIcon></span>
                <div>
                  <h3>{{ plugin.name }}</h3>
                  <em v-if="plugin.official">官方</em>
                </div>
              </header>
              <p>{{ plugin.desc }}</p>
              <span>{{ formatCompact(plugin.uses) }} 次使用</span>
              <button @click="togglePluginInstall(plugin)">
                {{ installedPluginIds.includes(plugin.id) ? '卸载' : '安装' }}
              </button>
            </article>
          </div>
        </section>

        <section class="section-block">
          <div class="block-title toolbar-title">
            <h2>全部插件</h2>
            <div class="inline-tabs">
              <button v-for="tab in pluginTabs" :key="tab.key" :class="{ active: activePluginTab === tab.key }" @click="activePluginTab = tab.key">
                {{ tab.label }}
              </button>
            </div>
            <select v-model="pluginSort" class="mini-select">
              <option>综合排序</option>
              <option>评分最高</option>
              <option>使用最多</option>
            </select>
          </div>
          <div class="plugin-grid-list">
            <article v-for="plugin in filteredPlugins" :key="plugin.id" class="plugin-row-card" @click="openPluginDetail(plugin)">
              <span class="agent-icon small" :style="{ '--item-color': plugin.color }"><NIcon size="21"><component :is="plugin.icon" /></NIcon></span>
              <div>
                <h3>{{ plugin.name }} <em>{{ plugin.category }}</em></h3>
                <p>{{ plugin.desc }}</p>
                <footer>
                  <span><NIcon size="14"><StarOutline /></NIcon>{{ plugin.rating }}</span>
                  <span><NIcon size="14"><PeopleOutline /></NIcon>{{ formatCompact(plugin.uses) }}</span>
                </footer>
              </div>
              <button @click.stop="togglePluginInstall(plugin)">{{ installedPluginIds.includes(plugin.id) ? '卸载' : '安装' }}</button>
            </article>
          </div>
        </section>
      </main>

      <aside class="ai-console-right">
        <section class="right-card">
          <div class="block-title compact">
            <h2>插件排行榜</h2>
            <button @click="pluginSort = '使用最多'">查看全部 <NIcon size="14"><ChevronForwardOutline /></NIcon></button>
          </div>
          <div class="ranking-list">
            <button v-for="(plugin, index) in pluginRanking" :key="plugin.id" @click="openPluginDetail(plugin)">
              <em>{{ index + 1 }}</em>
              <span class="avatar-tile" :style="{ '--item-color': plugin.color }"><component :is="plugin.icon" /></span>
              <div><strong>{{ plugin.name }}</strong><p>{{ formatCompact(plugin.uses) }} 次安装</p></div>
              <small :class="{ down: (plugin.trend || 0) < 0 }">{{ (plugin.trend || 0) > 0 ? '+' : '' }}{{ plugin.trend || 0 }}</small>
            </button>
          </div>
        </section>
        <section class="right-card">
          <div class="block-title compact">
            <h2>最新上架</h2>
            <button @click="activePluginTab = 'latest'">查看全部 <NIcon size="14"><ChevronForwardOutline /></NIcon></button>
          </div>
          <div class="latest-list">
            <button v-for="item in latestPlugins" :key="item.name" @click="pageSearch = item.name; searchWithinAi()">
              <span class="avatar-tile" :style="{ '--item-color': item.color }"><component :is="item.icon" /></span>
              <div><strong>{{ item.name }}</strong><p>{{ item.category }}</p></div>
              <small>{{ item.time }}</small>
            </button>
          </div>
        </section>
        <section class="right-card doc-card">
          <h2>插件开发文档</h2>
          <p>了解如何开发和发布插件</p>
          <button @click="pluginDocVisible = true">查看文档 <NIcon size="15"><ArrowForwardOutline /></NIcon></button>
        </section>
      </aside>
    </template>

    <template v-else>
      <aside class="ai-console-sidebar">
        <button class="ai-primary-action" @click="knowledgeCreateVisible = true">
          <NIcon size="18"><AddOutline /></NIcon>
          <span>创建知识库</span>
        </button>
        <section class="ai-nav-card">
          <button class="ai-side-link active" @click="activeKnowledgeCategory = '全部知识库'">
            <NIcon size="18"><LibraryOutline /></NIcon>
            知识库
          </button>
          <button class="ai-side-link" @click="knowledgeImportVisible = true">
            <NIcon size="18"><DocumentsOutline /></NIcon>
            文档管理
          </button>
          <button class="ai-side-link" @click="message.success('问答对管理需要后端保存训练数据，已写入接口文档')">
            <NIcon size="18"><ChatbubbleEllipsesOutline /></NIcon>
            问答对管理
          </button>
          <button class="ai-side-link" @click="message.success('标签管理已切换到分类筛选')">
            <NIcon size="18"><PricetagOutline /></NIcon>
            标签管理
          </button>
          <button class="ai-side-link" @click="batchArchiveKnowledge">
            <NIcon size="18"><TrashOutline /></NIcon>
            回收站
          </button>
        </section>
        <section class="ai-nav-card">
          <div class="section-mini-title with-plus">
            知识库分类
            <button @click="knowledgeCreateVisible = true"><NIcon size="14"><AddOutline /></NIcon></button>
          </div>
          <button
            v-for="item in knowledgeCategories"
            :key="item.name"
            class="ai-side-link"
            :class="{ active: activeKnowledgeCategory === item.name }"
            @click="activeKnowledgeCategory = item.name"
          >
            <NIcon size="17"><component :is="item.icon" /></NIcon>
            {{ item.name }}
          </button>
        </section>
        <section class="ai-tip-card storage-card">
          <strong>存储空间</strong>
          <span class="storage-line"><i /></span>
          <p>已使用 12.45 GB / 50 GB <em>24.9%</em></p>
          <button @click="message.success('升级空间需要后端订单接口，已写入接口文档')">升级空间</button>
        </section>
      </aside>

      <main class="ai-section-main">
        <header class="page-heading">
          <h1>知识库</h1>
          <p>集中管理企业或个人知识，让 AI 更懂你的业务</p>
        </header>

        <section class="knowledge-stats">
          <article v-for="item in knowledgeStats" :key="item.label">
            <span :style="{ '--item-color': item.color }"><NIcon size="22"><component :is="item.icon" /></NIcon></span>
            <p>{{ item.label }}</p>
            <strong>{{ typeof item.value === 'number' ? item.value.toLocaleString('zh-CN') : item.value }}</strong>
            <small>较上周 {{ item.delta }}</small>
          </article>
          <article class="storage-summary">
            <p>存储空间</p>
            <strong>12.45 <small>GB / 50 GB</small></strong>
            <span><i /></span>
            <small>24.9%</small>
          </article>
          <article class="kb-guide">
            <h3>如何更好地使用知识库？</h3>
            <p>了解知识库的最佳实践，提升 AI 回答效果</p>
            <button @click="message.success('已打开知识库使用指南')">查看使用指南 <NIcon size="14"><ArrowForwardOutline /></NIcon></button>
          </article>
        </section>

        <section class="section-block">
          <div class="knowledge-toolbar">
            <div class="inline-tabs">
              <button v-for="tab in knowledgeTabs" :key="tab.key" :class="{ active: activeKnowledgeTab === tab.key }" @click="activeKnowledgeTab = tab.key">
                {{ tab.label }}
              </button>
            </div>
            <label class="table-search">
              <NIcon size="16"><SearchOutline /></NIcon>
              <input v-model="pageSearch" placeholder="搜索知识库" @keyup.enter="searchWithinAi" />
            </label>
            <select v-model="knowledgeTypeFilter" class="mini-select">
              <option>全部类型</option>
              <option v-for="item in Array.from(new Set(knowledgeBases.map((kb) => kb.type)))" :key="item">{{ item }}</option>
            </select>
            <select v-model="knowledgeSort" class="mini-select">
              <option>最近更新</option>
              <option>文档最多</option>
              <option>向量最多</option>
            </select>
            <button class="view-toggle" :class="{ active: knowledgeView === 'grid' }" title="网格视图" @click="knowledgeView = 'grid'"><NIcon size="17"><GridOutline /></NIcon></button>
            <button class="view-toggle" :class="{ active: knowledgeView === 'list' }" title="列表视图" @click="knowledgeView = 'list'"><NIcon size="17"><ListOutline /></NIcon></button>
          </div>

          <div v-if="selectedKnowledgeCount" class="batch-bar">
            已选择 {{ selectedKnowledgeCount }} 项
            <button @click="batchArchiveKnowledge">批量移入回收站</button>
            <button @click="selectedKnowledgeIds = []">取消选择</button>
          </div>

          <div class="knowledge-table" :class="{ grid: knowledgeView === 'grid' }">
            <div v-if="knowledgeView === 'list'" class="knowledge-head">
              <button :class="{ checked: allKnowledgeSelected }" @click="toggleAllKnowledgeSelection" />
              <span>知识库名称</span>
              <span>类型</span>
              <span>文档数</span>
              <span>向量数</span>
              <span>更新时间</span>
              <span>操作</span>
            </div>
            <article v-for="item in filteredKnowledgeBases" :key="item.id" class="knowledge-row">
              <button class="checkbox" :class="{ checked: selectedKnowledgeIds.includes(item.id) }" @click="toggleKnowledgeSelection(item.id)" />
              <div class="kb-name">
                <span :style="{ '--item-color': item.color }"><NIcon size="18"><LibraryOutline /></NIcon></span>
                <div>
                  <h3>{{ item.name }} <NIcon v-if="item.favorite" size="14"><StarOutline /></NIcon></h3>
                  <p>{{ item.desc }}</p>
                </div>
              </div>
              <em>{{ item.type }}</em>
              <span>{{ item.docs }}</span>
              <span>{{ item.vectors.toLocaleString('zh-CN') }}</span>
              <span>{{ item.updatedAt }}</span>
              <div class="row-actions">
                <button title="打开知识库" @click="openKnowledgeDetail(item)"><NIcon size="17"><OpenOutline /></NIcon></button>
                <button title="分享知识库" @click="message.success(`已生成 ${item.name} 的分享链接`)"><NIcon size="17"><ShareSocialOutline /></NIcon></button>
                <button title="更多操作" @click="toggleKnowledgeFavorite(item)"><NIcon size="17"><EllipsisHorizontalOutline /></NIcon></button>
              </div>
            </article>
          </div>
          <footer class="table-footer">
            <span>共 {{ filteredKnowledgeBases.length }} 条</span>
            <div>
              <button @click="message.info('已在第一页')"><NIcon size="15"><ChevronDownOutline /></NIcon></button>
              <button class="active">1</button>
              <button @click="message.info('当前为静态分页预览')">2</button>
              <button @click="message.info('当前为静态分页预览')">3</button>
              <button @click="message.info('当前为静态分页预览')"><NIcon size="15"><ChevronForwardOutline /></NIcon></button>
            </div>
          </footer>
        </section>
      </main>

      <aside class="ai-console-right">
        <section class="right-card">
          <h2>知识库工具</h2>
          <div class="tool-list">
            <button @click="knowledgeImportVisible = true"><NIcon size="20"><CloudUploadOutline /></NIcon><strong>导入文档</strong><span>支持多种格式文档批量导入</span></button>
            <button @click="askKnowledge(knowledgeBases[0])"><NIcon size="20"><ChatbubbleEllipsesOutline /></NIcon><strong>创建问答对</strong><span>手动创建问答对，优化回答效果</span></button>
            <button @click="message.success('标签管理已切换到分类筛选')"><NIcon size="20"><PricetagOutline /></NIcon><strong>标签管理</strong><span>管理知识库标签，便于分类检索</span></button>
            <button @click="message.success('使用统计需要后端埋点接口，已写入接口文档')"><NIcon size="20"><PieChartOutline /></NIcon><strong>使用统计</strong><span>查看知识库使用情况和效果</span></button>
          </div>
        </section>
        <section class="right-card">
          <h2>最近使用</h2>
          <div class="latest-list">
            <button v-for="item in recentKnowledge" :key="item.id" @click="openKnowledgeDetail(item)">
              <span class="avatar-tile" :style="{ '--item-color': item.color }"><LibraryOutline /></span>
              <div><strong>{{ item.name }}</strong></div>
              <small>{{ item.updatedAt.includes('刚刚') ? '刚刚' : '最近' }}</small>
            </button>
          </div>
        </section>
        <section class="right-card kb-advice">
          <h2>知识库使用建议</h2>
          <ul>
            <li>文档格式建议使用 PDF、DOCX、TXT</li>
            <li>单个文档大小不超过 100MB</li>
            <li>定期更新文档，保持知识时效性</li>
          </ul>
          <button @click="message.success('已打开完整建议')">查看完整建议 <NIcon size="14"><ArrowForwardOutline /></NIcon></button>
        </section>
      </aside>
    </template>

    <NModal v-model:show="agentCreateVisible" preset="card" class="ai-modal-card" title="创建智能体">
      <div class="modal-form">
        <label>名称<input v-model="agentDraft.name" placeholder="例如：周报生成器" /></label>
        <label>分类
          <select v-model="agentDraft.category">
            <option v-for="item in agentCategories.map((category) => category.name).filter((name) => name !== '全部')" :key="item">{{ item }}</option>
          </select>
        </label>
        <label>描述<textarea v-model="agentDraft.desc" placeholder="说明它能帮助用户完成什么" /></label>
        <label>能力标签<input v-model="agentDraft.abilities" placeholder="例如：总结、写作、数据分析" /></label>
        <div class="modal-actions">
          <button @click="agentCreateVisible = false">取消</button>
          <button class="primary" @click="createAgent">创建</button>
        </div>
      </div>
    </NModal>

    <NModal v-model:show="agentDetailVisible" preset="card" class="ai-modal-card" :title="selectedAgent?.name || '智能体详情'">
      <div v-if="selectedAgent" class="detail-panel">
        <span class="agent-icon" :style="{ '--item-color': selectedAgent.color }"><NIcon size="30"><component :is="selectedAgent.icon" /></NIcon></span>
        <p>{{ selectedAgent.desc }}</p>
        <div class="detail-metrics">
          <span>使用 {{ formatCompact(selectedAgent.users) }}</span>
          <span>评分 {{ selectedAgent.rating }}</span>
          <span>{{ selectedAgent.category }}</span>
        </div>
        <div class="modal-actions">
          <button @click="toggleAgentFavorite(selectedAgent)">{{ favoriteAgentIds.includes(selectedAgent.id) ? '取消收藏' : '收藏' }}</button>
          <button class="primary" @click="useAgent(selectedAgent); agentDetailVisible = false">立即使用</button>
        </div>
      </div>
    </NModal>

    <NModal v-model:show="pluginDetailVisible" preset="card" class="ai-modal-card" :title="selectedPlugin?.name || '插件详情'">
      <div v-if="selectedPlugin" class="detail-panel">
        <span class="agent-icon" :style="{ '--item-color': selectedPlugin.color }"><NIcon size="30"><component :is="selectedPlugin.icon" /></NIcon></span>
        <p>{{ selectedPlugin.desc }}</p>
        <div class="detail-metrics">
          <span>{{ selectedPlugin.category }}</span>
          <span>{{ formatCompact(selectedPlugin.uses) }} 次使用</span>
          <span>评分 {{ selectedPlugin.rating }}</span>
        </div>
        <div class="modal-actions">
          <button @click="togglePluginInstall(selectedPlugin)">{{ installedPluginIds.includes(selectedPlugin.id) ? '卸载插件' : '安装插件' }}</button>
          <button class="primary" @click="usePlugin(selectedPlugin); pluginDetailVisible = false">在对话中使用</button>
        </div>
      </div>
    </NModal>

    <NModal v-model:show="developerVisible" preset="card" class="ai-modal-card" title="插件开发者入驻">
      <div class="detail-panel">
        <p>前端已提供入驻入口。真实入驻需要后端提供开发者申请、插件包上传、审核和发布接口。</p>
        <div class="modal-actions">
          <button @click="developerVisible = false">稍后</button>
          <button class="primary" @click="pluginDocVisible = true; developerVisible = false">查看开发文档</button>
        </div>
      </div>
    </NModal>

    <NModal v-model:show="pluginDocVisible" preset="card" class="ai-modal-card" title="插件开发文档">
      <div class="detail-panel">
        <p>插件需要声明名称、分类、权限、输入输出 schema、调用端点和安全策略。详细后端接口已写入文档。</p>
        <div class="modal-actions">
          <button class="primary" @click="pluginDocVisible = false">知道了</button>
        </div>
      </div>
    </NModal>

    <NModal v-model:show="knowledgeCreateVisible" preset="card" class="ai-modal-card" title="创建知识库">
      <div class="modal-form">
        <label>名称<input v-model="knowledgeDraft.name" placeholder="例如：产品帮助文档" /></label>
        <label>分类
          <select v-model="knowledgeDraft.category">
            <option v-for="item in knowledgeCategories.map((category) => category.name).filter((name) => name !== '全部知识库')" :key="item">{{ item }}</option>
          </select>
        </label>
        <label>描述<textarea v-model="knowledgeDraft.desc" placeholder="说明这个知识库覆盖哪些内容" /></label>
        <div class="modal-actions">
          <button @click="knowledgeCreateVisible = false">取消</button>
          <button class="primary" @click="createKnowledgeBase">创建</button>
        </div>
      </div>
    </NModal>

    <NModal v-model:show="knowledgeImportVisible" preset="card" class="ai-modal-card" title="导入文档">
      <div class="modal-form">
        <label>目标知识库
          <select v-model="importDraft.targetId">
            <option v-for="item in knowledgeBases" :key="item.id" :value="item.id">{{ item.name }}</option>
          </select>
        </label>
        <label>文件名称<textarea v-model="importDraft.files" placeholder="每行一个文件名。真实上传接口接入后这里会替换为上传组件。" /></label>
        <label>标签<input v-model="importDraft.tags" placeholder="例如：产品、FAQ、流程" /></label>
        <div class="modal-actions">
          <button @click="knowledgeImportVisible = false">取消</button>
          <button class="primary" @click="importKnowledgeDocs">导入</button>
        </div>
      </div>
    </NModal>

    <NModal v-model:show="knowledgeDetailVisible" preset="card" class="ai-modal-card" :title="selectedKnowledge?.name || '知识库详情'">
      <div v-if="selectedKnowledge" class="detail-panel">
        <p>{{ selectedKnowledge.desc }}</p>
        <div class="detail-metrics">
          <span>{{ selectedKnowledge.docs }} 份文档</span>
          <span>{{ selectedKnowledge.vectors.toLocaleString('zh-CN') }} 向量</span>
          <span>{{ selectedKnowledge.updatedAt }}</span>
        </div>
        <div class="modal-actions">
          <button @click="toggleKnowledgeFavorite(selectedKnowledge)">{{ selectedKnowledge.favorite ? '取消收藏' : '收藏' }}</button>
          <button class="primary" @click="askKnowledge(selectedKnowledge); knowledgeDetailVisible = false">基于它提问</button>
        </div>
      </div>
    </NModal>
  </div>
</template>

<style lang="scss">
.ai-workbench {
  min-height: calc(100vh - var(--cf-header-height) - 48px);
  display: grid;
  grid-template-columns: 260px minmax(0, 1fr) 320px;
  gap: 24px;
  color: var(--cf-text-primary);
}

.ai-workbench button,
.ai-workbench input,
.ai-workbench textarea,
.ai-workbench select {
  font: inherit;
}

.ai-workbench button {
  border: 0;
  background: transparent;
  color: inherit;
  cursor: pointer;
}

.ai-console-sidebar,
.ai-console-right {
  display: flex;
  flex-direction: column;
  gap: 18px;
  min-width: 0;
}

.ai-console-sidebar,
.ai-console-right {
  position: sticky;
  top: 96px;
  align-self: start;
  max-height: calc(100vh - 112px);
  overflow-y: auto;
  scrollbar-width: none;
}

.ai-console-sidebar::-webkit-scrollbar,
.ai-console-right::-webkit-scrollbar {
  display: none;
}

.ai-primary-action {
  height: 46px;
  border-radius: 10px;
  background: linear-gradient(135deg, #12cdb8, #08a995) !important;
  color: #fff !important;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  font-weight: 800;
  box-shadow: 0 18px 36px rgba(0, 191, 168, 0.22);
}

.ai-nav-card,
.ai-tip-card,
.right-card,
.assistant-card,
.input-panel,
.agent-card,
.agent-list,
.agent-hero,
.plugin-hero,
.plugin-card,
.plugin-row-card,
.knowledge-stats article,
.knowledge-table,
.modal-form,
.detail-panel {
  background: var(--cf-card-bg);
  border: 1px solid var(--cf-card-border);
  box-shadow: var(--cf-card-shadow);
  backdrop-filter: blur(22px) saturate(140%);
  -webkit-backdrop-filter: blur(22px) saturate(140%);
}

.ai-nav-card,
.ai-tip-card,
.right-card {
  border-radius: 16px;
  padding: 14px;
}

.section-mini-title {
  margin: 4px 8px 10px;
  color: var(--cf-text-muted);
  font-size: 13px;
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.section-mini-title.with-plus button {
  width: 24px;
  height: 24px;
  border-radius: 8px;
  color: var(--cf-primary);
  display: grid;
  place-items: center;
}

.ai-side-link {
  width: 100%;
  min-height: 40px;
  border-radius: 10px;
  padding: 0 10px;
  display: flex;
  align-items: center;
  gap: 10px;
  color: var(--cf-text-secondary);
  font-weight: 700;
  text-align: left;
}

.ai-side-link.active,
.ai-side-link:hover {
  color: var(--cf-primary);
  background: color-mix(in srgb, var(--cf-primary) 10%, transparent);
}

.ai-side-link.with-count em {
  margin-left: auto;
  color: var(--cf-text-muted);
  font-style: normal;
  font-size: 12px;
}

.chat-history-card {
  flex: 1;
}

.history-item {
  width: 100%;
  min-height: 38px;
  padding: 0 10px;
  border-radius: 9px;
  color: var(--cf-text-secondary);
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  text-align: left;
}

.history-item.active {
  color: var(--cf-text-primary);
  background: color-mix(in srgb, var(--cf-primary) 9%, transparent);
  border-left: 2px solid var(--cf-primary);
}

.history-item span {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.empty-tip {
  padding: 16px 8px;
  color: var(--cf-text-muted);
  font-size: 13px;
}

.ai-tip-card {
  display: grid;
  gap: 10px;
}

.ai-tip-card strong {
  font-size: 15px;
}

.ai-tip-card span,
.ai-tip-card p {
  margin: 0;
  color: var(--cf-text-muted);
  font-size: 13px;
  line-height: 1.6;
}

.ai-tip-card button {
  width: max-content;
  min-height: 34px;
  padding: 0 12px;
  border: 1px solid color-mix(in srgb, var(--cf-primary) 24%, var(--cf-border));
  border-radius: 10px;
  color: var(--cf-primary);
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-weight: 800;
}

.illustrated {
  min-height: 172px;
  background:
    radial-gradient(circle at 82% 76%, rgba(0, 216, 191, 0.18), transparent 28%),
    var(--cf-card-bg);
}

.storage-card .storage-line {
  height: 6px;
  border-radius: 999px;
  background: var(--cf-bg-muted);
  overflow: hidden;
}

.storage-card .storage-line i {
  display: block;
  width: 24.9%;
  height: 100%;
  border-radius: inherit;
  background: var(--cf-primary);
}

.storage-card em {
  float: right;
  color: var(--cf-text-muted);
  font-style: normal;
}

.chat-main-panel,
.ai-section-main {
  min-width: 0;
}

.chat-main-panel {
  min-height: 0;
  height: calc(100vh - var(--cf-header-height) - 48px);
  display: flex;
  flex-direction: column;
  gap: 14px;
  overflow: hidden;
}

.chat-title {
  min-height: 42px;
}

.chat-title h1,
.page-heading h1 {
  margin: 0;
  font-size: 28px;
  line-height: 1.2;
  letter-spacing: 0;
}

.page-heading {
  margin: 2px 0 20px;
}

.page-heading p {
  margin: 8px 0 0;
  color: var(--cf-text-secondary);
  font-size: 15px;
}

.chat-stream {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 0 6px 12px;
  display: flex;
  flex-direction: column;
  gap: 22px;
}

.message-row {
  display: flex;
  align-items: flex-start;
  gap: 12px;
}

.message-row.user {
  justify-content: flex-end;
  align-items: center;
}

.user-bubble {
  max-width: min(520px, 62%);
  padding: 13px 20px;
  border-radius: 14px;
  background: color-mix(in srgb, var(--cf-primary) 12%, #ffffff);
  color: var(--cf-text-primary);
  box-shadow: 0 12px 30px rgba(15, 23, 42, 0.06);
}

.message-time {
  color: var(--cf-text-muted);
  font-size: 13px;
}

.user-avatar,
.ai-avatar {
  width: 38px;
  height: 38px;
  border-radius: 50%;
  flex: 0 0 auto;
  display: grid;
  place-items: center;
  font-weight: 900;
}

.user-avatar {
  color: #fff;
  background: linear-gradient(135deg, #f97316, #ef4444);
}

.ai-avatar {
  color: #fff;
  background: linear-gradient(135deg, var(--cf-primary), #00a88f);
  margin-top: 16px;
}

.assistant-card {
  width: min(780px, 100%);
  padding: 22px 24px;
  border-radius: 18px;
}

.assistant-card header {
  display: flex;
  align-items: center;
  gap: 14px;
  margin-bottom: 14px;
}

.assistant-card header span {
  color: var(--cf-text-muted);
  font-size: 13px;
}

.assistant-card pre {
  margin: 0;
  color: var(--cf-text-secondary);
  font-family: inherit;
  font-size: 15px;
  line-height: 1.9;
  white-space: pre-wrap;
}

.assistant-card footer {
  margin-top: 18px;
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.assistant-card footer button {
  height: 32px;
  padding: 0 11px;
  border-radius: 8px;
  border: 1px solid var(--cf-border);
  color: var(--cf-text-secondary);
  display: inline-flex;
  align-items: center;
  gap: 5px;
  font-size: 13px;
}

.citation-list {
  margin-top: 16px;
  padding: 12px;
  border-radius: 10px;
  background: var(--cf-bg-soft);
  display: grid;
  gap: 6px;
}

.citation-list a {
  color: var(--cf-primary);
  font-size: 13px;
}

.chat-loading {
  display: flex;
  align-items: center;
  gap: 10px;
  color: var(--cf-text-muted);
  padding-left: 54px;
}

.chat-loading span {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  background: var(--cf-primary);
  animation: aiPulse 1.2s infinite;
}

.quick-row {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr)) 44px;
  gap: 14px;
}

.quick-row button {
  min-height: 42px;
  padding: 0 18px;
  border-radius: 999px;
  background: var(--cf-bg-glass);
  border: 1px solid var(--cf-border);
  color: var(--cf-text-secondary);
  box-shadow: 0 10px 28px rgba(15, 23, 42, 0.05);
}

.quick-row .icon-only {
  padding: 0;
  display: grid;
  place-items: center;
}

.input-panel {
  min-height: 112px;
  padding: 14px;
  border-radius: 18px;
}

.input-panel textarea {
  width: 100%;
  min-height: 42px;
  max-height: 140px;
  border: 0;
  outline: 0;
  resize: none;
  background: transparent;
  color: var(--cf-text-primary);
  line-height: 1.6;
}

.attachment-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 8px;
}

.attachment-list button {
  height: 28px;
  padding: 0 10px;
  border-radius: 999px;
  background: var(--cf-primary-soft);
  color: var(--cf-primary);
  font-size: 12px;
  font-weight: 800;
}

.input-tools,
.tool-cluster,
.send-cluster {
  display: flex;
  align-items: center;
}

.input-tools {
  justify-content: space-between;
  gap: 12px;
}

.tool-cluster {
  gap: 4px;
  flex-wrap: wrap;
}

.tool-cluster button {
  width: 34px;
  height: 34px;
  border-radius: 10px;
  color: var(--cf-text-muted);
  display: grid;
  place-items: center;
}

.tool-cluster button.active,
.tool-cluster button:hover {
  color: var(--cf-primary);
  background: var(--cf-primary-soft);
}

.send-cluster {
  gap: 8px;
}

.model-select,
.mini-select {
  height: 36px;
  border: 1px solid var(--cf-border);
  border-radius: 10px;
  padding: 0 12px;
  background: var(--cf-bg-soft);
  color: var(--cf-text-primary);
  font-weight: 700;
  outline: none;
}

.model-select {
  max-width: 210px;
}

.send-btn {
  width: 36px;
  height: 36px;
  border-radius: 10px;
  color: #fff !important;
  background: var(--cf-primary) !important;
  display: grid;
  place-items: center;
  box-shadow: 0 12px 28px rgba(0, 191, 168, 0.24);
}

.send-btn:disabled {
  opacity: 0.48;
  cursor: not-allowed;
}

.file-input {
  display: none;
}

.right-card h2 {
  margin: 0;
  font-size: 18px;
}

.panel-title,
.block-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 16px;
}

.block-title h2 {
  margin: 0;
  font-size: 20px;
}

.block-title button {
  color: var(--cf-text-muted);
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 13px;
}

.block-title.compact h2 {
  font-size: 17px;
}

.model-row {
  width: 100%;
  min-height: 74px;
  padding: 12px;
  border-radius: 12px;
  display: grid;
  grid-template-columns: 34px minmax(0, 1fr) auto;
  gap: 12px;
  align-items: center;
  text-align: left;
}

.model-row.active {
  border: 1px solid var(--cf-primary);
  background: color-mix(in srgb, var(--cf-primary) 6%, var(--cf-bg-card));
}

.model-row strong,
.model-row p {
  margin: 0;
}

.model-row p {
  margin-top: 4px;
  color: var(--cf-text-muted);
  font-size: 12px;
  line-height: 1.45;
}

.model-row em {
  padding: 4px 7px;
  border-radius: 8px;
  background: var(--cf-primary-soft);
  color: var(--cf-primary);
  font-size: 12px;
  font-style: normal;
  font-weight: 900;
}

.model-mark {
  width: 28px;
  height: 28px;
  border-radius: 50%;
  display: grid;
  place-items: center;
  color: var(--model-color);
  background: color-mix(in srgb, var(--model-color) 12%, transparent);
}

.ability-list {
  margin-top: 18px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.ability-row {
  min-height: 62px;
  padding: 10px;
  border: 1px solid var(--cf-border);
  border-radius: 12px;
  display: grid;
  grid-template-columns: 28px minmax(0, 1fr) auto;
  align-items: center;
  gap: 10px;
}

.ability-row strong,
.ability-row p {
  margin: 0;
}

.ability-row p {
  margin-top: 3px;
  color: var(--cf-text-muted);
  font-size: 12px;
  line-height: 1.4;
}

.ability-row em {
  font-style: normal;
  color: var(--cf-primary);
  font-size: 12px;
  font-weight: 900;
}

.ability-icon {
  width: 26px;
  height: 26px;
  display: grid;
  place-items: center;
  border-radius: 9px;
  color: var(--cf-primary);
  background: var(--cf-primary-soft);
}

.stats-grid {
  margin-top: 20px;
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  text-align: center;
}

.stats-grid strong,
.stats-grid span {
  display: block;
}

.stats-grid strong {
  font-size: 22px;
  line-height: 1;
}

.stats-grid span {
  margin-top: 8px;
  color: var(--cf-text-muted);
  font-size: 12px;
}

.ai-section-main {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.agent-hero,
.plugin-hero {
  min-height: 218px;
  border-radius: 18px;
  padding: 28px 34px;
  display: grid;
  grid-template-columns: minmax(0, 1fr) 360px;
  gap: 24px;
  overflow: hidden;
  background:
    radial-gradient(circle at 78% 42%, rgba(0, 216, 191, 0.18), transparent 32%),
    linear-gradient(135deg, rgba(235, 255, 252, 0.92), rgba(246, 251, 255, 0.72));
}

.agent-hero span,
.plugin-hero p {
  color: var(--cf-text-secondary);
}

.agent-hero h2,
.plugin-hero h2 {
  margin: 8px 0 22px;
  font-size: 28px;
  line-height: 1.25;
}

.hero-search {
  width: min(620px, 100%);
  min-height: 54px;
  padding: 0 12px 0 18px;
  border-radius: 999px;
  border: 1px solid var(--cf-border);
  background: rgba(255, 255, 255, 0.9);
  display: flex;
  align-items: center;
  gap: 10px;
  box-shadow: 0 18px 40px rgba(15, 23, 42, 0.08);
}

.hero-search input {
  min-width: 0;
  flex: 1;
  border: 0;
  outline: 0;
  background: transparent;
  color: var(--cf-text-primary);
}

.hero-search button {
  width: 38px;
  height: 38px;
  border-radius: 50%;
  color: var(--cf-primary);
  background: var(--cf-primary-soft);
  display: grid;
  place-items: center;
}

.hot-tags {
  margin-top: 18px;
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
}

.hot-tags span {
  font-size: 13px;
}

.hot-tags button {
  height: 28px;
  padding: 0 12px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.74);
  color: var(--cf-text-secondary);
  font-size: 13px;
  font-weight: 700;
}

.hero-robot,
.plugin-visual {
  position: relative;
  display: grid;
  place-items: center;
}

.bot-ring {
  width: 210px;
  height: 210px;
  border-radius: 50%;
  border: 1px solid rgba(0, 191, 168, 0.28);
  background: radial-gradient(circle, rgba(0, 216, 191, 0.12), transparent 60%);
}

.bot-head {
  position: absolute;
  z-index: 2;
  width: 92px;
  height: 72px;
  border-radius: 34px;
  background: #07111f;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 18px;
  box-shadow: 0 18px 45px rgba(0, 191, 168, 0.26);
}

.bot-head i {
  width: 14px;
  height: 24px;
  border-radius: 999px;
  background: var(--cf-primary);
  box-shadow: 0 0 18px var(--cf-primary);
}

.float-card {
  position: absolute;
  width: 66px;
  height: 66px;
  border-radius: 16px;
  background: rgba(255, 255, 255, 0.72);
  color: var(--cf-primary);
  display: grid;
  place-items: center;
  box-shadow: 0 18px 38px rgba(15, 23, 42, 0.08);
}

.float-card svg {
  width: 30px;
  height: 30px;
}

.card-a {
  left: 38px;
  top: 46px;
}

.card-b {
  right: 46px;
  bottom: 46px;
  color: #3b82f6;
}

.card-c {
  left: 62px;
  bottom: 32px;
  color: #94a3b8;
}

.section-block {
  min-width: 0;
}

.agent-card-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 16px;
}

.agent-card {
  min-height: 154px;
  border-radius: 16px;
  padding: 20px;
  display: grid;
  grid-template-columns: 54px minmax(0, 1fr);
  gap: 14px;
  transition: transform 0.2s ease, border-color 0.2s ease;
}

.agent-card:hover,
.plugin-card:hover,
.plugin-row-card:hover,
.knowledge-row:hover {
  transform: translateY(-2px);
  border-color: var(--cf-border-strong);
}

.agent-icon,
.avatar-tile {
  width: 52px;
  height: 52px;
  border-radius: 18px;
  color: var(--item-color);
  background: color-mix(in srgb, var(--item-color) 16%, transparent);
  display: grid;
  place-items: center;
}

.agent-icon.small,
.avatar-tile {
  width: 38px;
  height: 38px;
  border-radius: 12px;
}

.agent-card h3,
.agent-list-row h3,
.plugin-card h3,
.plugin-row-card h3,
.knowledge-row h3 {
  margin: 0;
  font-size: 16px;
}

.agent-card p,
.agent-list-row p,
.plugin-card p,
.plugin-row-card p,
.knowledge-row p {
  margin: 6px 0 0;
  color: var(--cf-text-secondary);
  font-size: 13px;
  line-height: 1.55;
}

.agent-card em,
.agent-list-row em,
.plugin-row-card em,
.knowledge-row em {
  width: max-content;
  margin-top: 10px;
  padding: 4px 8px;
  border-radius: 8px;
  background: var(--cf-primary-soft);
  color: var(--cf-primary);
  font-size: 12px;
  font-style: normal;
  font-weight: 800;
}

.agent-card footer {
  grid-column: 1 / -1;
  display: flex;
  justify-content: flex-end;
  gap: 18px;
  color: var(--cf-text-muted);
  font-size: 13px;
}

.agent-card footer span,
.agent-list-row > span,
.plugin-row-card footer span {
  display: inline-flex;
  align-items: center;
  gap: 5px;
}

.inline-tabs {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.inline-tabs button {
  min-height: 32px;
  padding: 0 14px;
  border-radius: 10px;
  color: var(--cf-text-secondary);
  font-weight: 700;
}

.inline-tabs button.active,
.inline-tabs button:hover {
  color: var(--cf-primary);
  background: var(--cf-primary-soft);
}

.agent-list {
  border-radius: 16px;
  overflow: hidden;
}

.agent-list-row {
  min-height: 70px;
  padding: 14px 18px;
  display: grid;
  grid-template-columns: 42px minmax(0, 1fr) 110px 78px 62px 64px;
  align-items: center;
  gap: 14px;
  border-bottom: 1px solid var(--cf-border);
}

.agent-list-row:last-child {
  border-bottom: 0;
}

.agent-list-row button,
.plugin-card button,
.plugin-row-card > button {
  height: 32px;
  padding: 0 14px;
  border-radius: 10px;
  background: var(--cf-primary-soft);
  color: var(--cf-primary);
  font-weight: 800;
}

.mini-agent-list,
.ranking-list,
.latest-list,
.tool-list {
  display: grid;
  gap: 12px;
}

.mini-agent-list button,
.ranking-list button,
.latest-list button,
.tool-list button {
  width: 100%;
  min-height: 58px;
  display: grid;
  grid-template-columns: 42px minmax(0, 1fr) auto;
  align-items: center;
  gap: 12px;
  text-align: left;
}

.mini-agent-list strong,
.ranking-list strong,
.latest-list strong,
.tool-list strong {
  display: block;
  font-size: 14px;
}

.mini-agent-list p,
.ranking-list p,
.latest-list p,
.tool-list span {
  margin: 3px 0 0;
  color: var(--cf-text-muted);
  font-size: 12px;
  line-height: 1.4;
}

.ability-tile-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.ability-tile-grid button {
  min-height: 104px;
  padding: 14px;
  border-radius: 14px;
  background: var(--cf-bg-soft);
  text-align: left;
  display: grid;
  gap: 6px;
}

.ability-tile-grid button .n-icon {
  color: var(--cf-primary);
}

.ability-tile-grid span {
  color: var(--cf-text-muted);
  font-size: 12px;
  line-height: 1.45;
}

.guide-card ol {
  list-style: none;
  padding: 0;
  margin: 16px 0;
  display: grid;
  gap: 12px;
}

.guide-card li {
  display: grid;
  grid-template-columns: 24px minmax(0, 1fr);
  gap: 10px;
}

.guide-card li span {
  width: 22px;
  height: 22px;
  border-radius: 50%;
  background: var(--cf-primary);
  color: var(--cf-text-inverse);
  display: grid;
  place-items: center;
  font-size: 12px;
  font-weight: 900;
}

.guide-card li p {
  grid-column: 2;
  margin: -4px 0 0;
  color: var(--cf-text-muted);
  font-size: 12px;
}

.guide-card > button,
.doc-card button,
.kb-advice button {
  width: 100%;
  height: 38px;
  border-radius: 10px;
  background: var(--cf-bg-soft);
  color: var(--cf-text-primary);
  font-weight: 800;
}

.plugin-hero {
  grid-template-columns: minmax(0, 1fr) 320px;
  background:
    radial-gradient(circle at 82% 42%, rgba(0, 216, 191, 0.18), transparent 32%),
    linear-gradient(135deg, rgba(229, 255, 251, 0.88), rgba(245, 251, 255, 0.72));
}

.plugin-visual span {
  width: 124px;
  height: 124px;
  border-radius: 28px;
  background: linear-gradient(135deg, #dbeafe, #99f6e4);
  color: #4f46e5;
  display: grid;
  place-items: center;
  box-shadow: 0 22px 50px rgba(0, 191, 168, 0.18);
}

.plugin-visual span svg {
  width: 58px;
  height: 58px;
}

.plugin-visual i {
  position: absolute;
  width: 34px;
  height: 34px;
  border-radius: 10px;
  background: rgba(255, 255, 255, 0.72);
}

.plugin-visual i:nth-child(2) {
  left: 64px;
  top: 34px;
}

.plugin-visual i:nth-child(3) {
  right: 42px;
  top: 62px;
}

.plugin-visual i:nth-child(4) {
  left: 98px;
  bottom: 34px;
}

.plugin-recommend-grid {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 14px;
}

.plugin-card {
  min-height: 172px;
  border-radius: 16px;
  padding: 16px;
  display: grid;
  gap: 10px;
}

.plugin-card header {
  display: flex;
  align-items: center;
  gap: 10px;
}

.plugin-card em {
  padding: 3px 7px;
  border-radius: 7px;
  background: var(--cf-bg-soft);
  color: var(--cf-text-muted);
  font-size: 11px;
  font-style: normal;
}

.plugin-card > span {
  color: var(--cf-text-muted);
  font-size: 13px;
}

.toolbar-title {
  align-items: center;
}

.plugin-grid-list {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 14px;
}

.plugin-row-card {
  min-height: 104px;
  border-radius: 16px;
  padding: 16px;
  display: grid;
  grid-template-columns: 42px minmax(0, 1fr) auto;
  gap: 14px;
  align-items: center;
}

.plugin-row-card h3 em {
  margin-left: 8px;
}

.plugin-row-card footer {
  margin-top: 14px;
  display: flex;
  gap: 18px;
  color: var(--cf-text-muted);
  font-size: 13px;
}

.ranking-list button {
  grid-template-columns: 24px 40px minmax(0, 1fr) 34px;
}

.ranking-list em {
  width: 18px;
  height: 18px;
  border-radius: 50%;
  background: #f59e0b;
  color: #fff;
  display: grid;
  place-items: center;
  font-style: normal;
  font-size: 11px;
  font-weight: 900;
}

.ranking-list small {
  color: #10b981;
  font-weight: 800;
}

.ranking-list small.down {
  color: #ef4444;
}

.latest-list small {
  color: var(--cf-text-muted);
  font-size: 12px;
}

.doc-card,
.kb-advice {
  background:
    radial-gradient(circle at 84% 78%, rgba(99, 102, 241, 0.15), transparent 30%),
    var(--cf-card-bg);
}

.doc-card p,
.kb-advice li {
  color: var(--cf-text-secondary);
  font-size: 13px;
  line-height: 1.65;
}

.knowledge-stats {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr)) 1.15fr 1.45fr;
  gap: 14px;
}

.knowledge-stats article {
  min-height: 148px;
  border-radius: 16px;
  padding: 18px;
  display: grid;
  align-content: center;
  gap: 10px;
}

.knowledge-stats article > span:first-child {
  width: 38px;
  height: 38px;
  border-radius: 12px;
  color: var(--item-color);
  background: color-mix(in srgb, var(--item-color) 14%, transparent);
  display: grid;
  place-items: center;
}

.knowledge-stats p,
.knowledge-stats h3 {
  margin: 0;
}

.knowledge-stats strong {
  font-size: 28px;
  line-height: 1;
}

.knowledge-stats small {
  color: var(--cf-text-muted);
}

.storage-summary span {
  height: 7px;
  border-radius: 999px;
  background: var(--cf-bg-muted);
  overflow: hidden;
}

.storage-summary span i {
  display: block;
  width: 24.9%;
  height: 100%;
  border-radius: inherit;
  background: var(--cf-primary);
}

.kb-guide {
  background:
    radial-gradient(circle at 86% 72%, rgba(0, 216, 191, 0.18), transparent 30%),
    linear-gradient(135deg, rgba(239, 249, 255, 0.88), rgba(234, 255, 251, 0.82)) !important;
}

.kb-guide h3 {
  color: #1d4ed8;
  font-size: 18px;
}

.kb-guide button {
  width: max-content;
  color: var(--cf-primary);
  font-weight: 800;
}

.knowledge-toolbar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px;
  margin-bottom: 14px;
}

.table-search {
  min-width: 210px;
  height: 38px;
  padding: 0 12px;
  border: 1px solid var(--cf-border);
  border-radius: 10px;
  display: flex;
  align-items: center;
  gap: 8px;
  color: var(--cf-text-muted);
  background: var(--cf-card-bg);
}

.table-search input {
  min-width: 0;
  border: 0;
  outline: 0;
  background: transparent;
  color: var(--cf-text-primary);
}

.view-toggle {
  width: 38px;
  height: 38px;
  border-radius: 10px;
  border: 1px solid var(--cf-border) !important;
  display: grid;
  place-items: center;
  color: var(--cf-text-muted) !important;
}

.view-toggle.active {
  color: var(--cf-primary) !important;
  background: var(--cf-primary-soft) !important;
}

.batch-bar {
  min-height: 42px;
  margin-bottom: 12px;
  padding: 8px 12px;
  border-radius: 12px;
  background: var(--cf-primary-soft);
  color: var(--cf-primary);
  display: flex;
  align-items: center;
  gap: 10px;
  font-weight: 800;
}

.batch-bar button {
  height: 28px;
  padding: 0 10px;
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.6);
}

.knowledge-table {
  border-radius: 16px;
  overflow: hidden;
}

.knowledge-head,
.knowledge-row {
  display: grid;
  grid-template-columns: 34px minmax(280px, 1.6fr) 110px 90px 120px 160px 100px;
  align-items: center;
  gap: 12px;
}

.knowledge-head {
  min-height: 46px;
  padding: 0 18px;
  color: var(--cf-text-muted);
  font-size: 13px;
  border-bottom: 1px solid var(--cf-border);
}

.knowledge-row {
  min-height: 76px;
  padding: 14px 18px;
  border-bottom: 1px solid var(--cf-border);
  transition: transform 0.2s ease, border-color 0.2s ease;
}

.knowledge-row:last-child {
  border-bottom: 0;
}

.checkbox,
.knowledge-head button {
  width: 16px;
  height: 16px;
  border: 1px solid var(--cf-border) !important;
  border-radius: 5px;
  background: var(--cf-bg-card) !important;
}

.checkbox.checked,
.knowledge-head button.checked {
  background: var(--cf-primary) !important;
  border-color: var(--cf-primary) !important;
}

.kb-name {
  display: grid;
  grid-template-columns: 38px minmax(0, 1fr);
  gap: 12px;
  align-items: center;
}

.kb-name > span {
  width: 38px;
  height: 38px;
  border-radius: 11px;
  color: var(--item-color);
  background: color-mix(in srgb, var(--item-color) 14%, transparent);
  display: grid;
  place-items: center;
}

.kb-name h3 {
  display: flex;
  align-items: center;
  gap: 6px;
}

.row-actions {
  display: flex;
  gap: 8px;
}

.row-actions button {
  width: 30px;
  height: 30px;
  border-radius: 8px;
  color: var(--cf-text-muted);
}

.row-actions button:hover {
  color: var(--cf-primary);
  background: var(--cf-primary-soft);
}

.knowledge-table.grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
  background: transparent;
  border: 0;
  box-shadow: none;
  backdrop-filter: none;
}

.knowledge-table.grid .knowledge-row {
  grid-template-columns: 20px minmax(0, 1fr);
  grid-template-areas:
    'check name'
    '. type'
    '. actions';
  min-height: 160px;
  border: 1px solid var(--cf-card-border);
  border-radius: 16px;
  background: var(--cf-card-bg);
}

.knowledge-table.grid .checkbox {
  grid-area: check;
}

.knowledge-table.grid .kb-name {
  grid-area: name;
}

.knowledge-table.grid .knowledge-row > em {
  grid-area: type;
}

.knowledge-table.grid .knowledge-row > span {
  display: none;
}

.knowledge-table.grid .row-actions {
  grid-area: actions;
}

.table-footer {
  margin-top: 14px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  color: var(--cf-text-muted);
}

.table-footer div {
  display: flex;
  gap: 8px;
}

.table-footer button {
  min-width: 34px;
  height: 34px;
  border-radius: 9px;
  border: 1px solid var(--cf-border);
  display: grid;
  place-items: center;
}

.table-footer button.active {
  background: var(--cf-primary);
  color: var(--cf-text-inverse);
  border-color: var(--cf-primary);
}

.tool-list button {
  grid-template-columns: 38px minmax(0, 1fr);
  padding: 10px;
  border-radius: 12px;
  background: var(--cf-bg-soft);
}

.tool-list .n-icon {
  grid-row: span 2;
  color: var(--cf-primary);
}

.kb-advice ul {
  padding-left: 18px;
}

.modal-form,
.detail-panel {
  box-shadow: none;
  backdrop-filter: none;
  border-radius: 14px;
  padding: 18px;
}

.modal-form {
  display: grid;
  gap: 14px;
}

.modal-form label {
  display: grid;
  gap: 7px;
  color: var(--cf-text-secondary);
  font-weight: 800;
}

.modal-form input,
.modal-form textarea,
.modal-form select {
  width: 100%;
  border: 1px solid var(--cf-border);
  border-radius: 10px;
  background: var(--cf-bg-soft);
  color: var(--cf-text-primary);
  padding: 10px 12px;
  outline: none;
}

.modal-form textarea {
  min-height: 96px;
  resize: vertical;
}

.modal-actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  margin-top: 8px;
}

.modal-actions button {
  min-width: 86px;
  height: 38px;
  padding: 0 14px;
  border-radius: 10px;
  border: 1px solid var(--cf-border);
  font-weight: 800;
}

.modal-actions .primary {
  border-color: var(--cf-primary);
  background: var(--cf-primary);
  color: var(--cf-text-inverse);
}

.detail-panel {
  display: grid;
  gap: 16px;
}

.detail-panel p {
  margin: 0;
  color: var(--cf-text-secondary);
  line-height: 1.7;
}

.detail-metrics {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.detail-metrics span {
  padding: 6px 10px;
  border-radius: 999px;
  background: var(--cf-bg-soft);
  color: var(--cf-text-secondary);
  font-size: 13px;
  font-weight: 800;
}

:global(.ai-modal-card) {
  width: min(560px, calc(100vw - 32px));
  border-radius: 18px;
}

@keyframes aiPulse {
  0% {
    box-shadow: 0 0 0 0 rgba(0, 191, 168, 0.34);
  }
  70% {
    box-shadow: 0 0 0 9px rgba(0, 191, 168, 0);
  }
  100% {
    box-shadow: 0 0 0 0 rgba(0, 191, 168, 0);
  }
}

html[data-theme='dark'] .agent-hero,
html[data-theme='dark'] .plugin-hero,
html[data-theme='dark'] .kb-guide {
  background:
    radial-gradient(circle at 78% 42%, rgba(0, 245, 212, 0.14), transparent 32%),
    linear-gradient(135deg, rgba(15, 23, 42, 0.86), rgba(8, 13, 26, 0.78)) !important;
}

html[data-theme='dark'] .hero-search,
html[data-theme='dark'] .hot-tags button {
  background: rgba(15, 23, 42, 0.8);
}

html[data-theme='dark'] .user-bubble {
  background: color-mix(in srgb, var(--cf-primary) 15%, #050505);
}

@media (max-width: 1280px) {
  .ai-workbench {
    grid-template-columns: 220px minmax(0, 1fr);
  }

  .ai-console-right {
    grid-column: 1 / -1;
    position: static;
    max-height: none;
    display: grid;
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }

  .agent-card-grid,
  .plugin-recommend-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .knowledge-stats {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 900px) {
  .ai-workbench {
    grid-template-columns: 1fr;
  }

  .ai-console-sidebar,
  .ai-console-right {
    position: static;
    max-height: none;
  }

  .ai-console-right,
  .agent-card-grid,
  .plugin-recommend-grid,
  .plugin-grid-list,
  .knowledge-table.grid {
    grid-template-columns: 1fr;
  }

  .agent-hero,
  .plugin-hero {
    grid-template-columns: 1fr;
  }

  .hero-robot,
  .plugin-visual {
    display: none;
  }

  .agent-list-row,
  .knowledge-head,
  .knowledge-row {
    grid-template-columns: 1fr;
  }

  .knowledge-head {
    display: none;
  }

  .row-actions {
    justify-content: flex-start;
  }

  .quick-row {
    grid-template-columns: 1fr;
  }

  .input-tools {
    align-items: flex-start;
    flex-direction: column;
  }

  .chat-main-panel {
    height: auto;
    min-height: calc(100vh - var(--cf-header-height) - 32px);
  }
}
</style>
