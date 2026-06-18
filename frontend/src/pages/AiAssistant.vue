<script setup lang="ts">
import { computed, nextTick, onMounted, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { useMessage } from 'naive-ui';
import {
  AddOutline,
  AlbumsOutline,
  ArrowBackOutline,
  ChatbubbleEllipsesOutline,
  ChevronDownOutline,
  ChevronForwardOutline,
  CompassOutline,
  DocumentTextOutline,
  EyeOutline,
  FolderOpenOutline,
  GridOutline,
  LinkOutline,
  ListOutline,
  PaperPlaneOutline,
  RefreshOutline,
  SearchOutline,
  SparklesOutline,
  TimeOutline,
} from '@vicons/ionicons5';
import { aiRagChat } from '@/api/ai';
import type { AiCitation } from '@/types/ai';

type XiaoqingMessage = {
  role: 'user' | 'assistant';
  content: string;
  citations?: AiCitation[];
};

type WikiFile = {
  id: string;
  title: string;
  type: 'word' | 'pdf' | 'note' | 'folder';
  meta: string;
  cover: string;
};

type KnowledgeBase = {
  id: string;
  title: string;
  owner: string;
  description: string;
  avatar: string;
  subscribers: string;
  contents: string;
  category: string;
  certified?: boolean;
  files: WikiFile[];
  prompts: string[];
};

type ExampleCard = {
  id: string;
  title: string;
  action: string;
  views: string;
  tint: string;
};

const route = useRoute();
const router = useRouter();
const message = useMessage();
const homeQuestion = ref('');
const wikiQuestion = ref('');
const discoverKeyword = ref('');
const activeCategory = ref('热门');
const chatMessages = ref<XiaoqingMessage[]>([
  { role: 'assistant', content: '我是小青，可以基于站内内容和知识库线索帮你整理问题、拆解概念或生成学习任务。' },
]);
const chatLoading = ref(false);
const bottomInputRef = ref<HTMLTextAreaElement | null>(null);

const featuredExamples: ExampleCard[] = [
  { id: 'report-ai', title: '算力倒逼光纤重构与产业重塑', action: '生成报告', views: '3997', tint: '#edf4ff' },
  { id: 'ppt-chip', title: '存储芯片迎超级周期', action: '生成PPT', views: '5843', tint: '#f8f2ea' },
  { id: 'answer-economy', title: '空域改革落地，解锁低空经济万亿新空间', action: '生成播客', views: '4935', tint: '#f4f0ff' },
  { id: 'career-ai', title: 'AI新职业落地就业市场择业指南', action: '生成PPT', views: '1.1万', tint: '#f9f2e8' },
  { id: 'silver-economy', title: '刚需催生睡眠经济蓬勃崛起', action: '生成播客', views: '7522', tint: '#f4f0ff' },
];

const knowledgeBases: KnowledgeBase[] = [
  {
    id: 'math-daily',
    title: '微积分(高等数学/数学分析)每日一题',
    owner: 'MathHub',
    description: '系统性收入精选的微积分每日一题，覆盖极限、导数、积分与级数。',
    avatar: '∫',
    subscribers: '1783',
    contents: '2171',
    category: '教育',
    certified: true,
    files: [
      { id: 'readme', title: 'README | 知识库介绍与使用指南', type: 'note', meta: '笔记 | 6/5更新', cover: '📗' },
      { id: 'latex', title: 'LaTeX数学笔记生成Prompt模板', type: 'note', meta: '笔记 | 4/19更新', cover: '🧮' },
      { id: 'sync', title: '高等数学同步辅导与复习提高.pdf', type: 'pdf', meta: 'PDF | 6/5', cover: '📘' },
      { id: 'derivative', title: '7.微分方程', type: 'folder', meta: '7项 | 01:19更新', cover: '📁' },
      { id: 'series', title: '6.无穷级数', type: 'folder', meta: '82项 | 01:11更新', cover: '📁' },
      { id: 'latex-notes', title: 'LaTeX笔记总结', type: 'folder', meta: '65项 | 昨天更新', cover: '📁' },
    ],
    prompts: [
      '总结利用罗尔定理、拉格朗日中值定理的证明题。',
      '请根据知识库收集的习题，归纳求解极限的方法。',
      '请总结不定积分的求解方法。',
    ],
  },
  {
    id: 'history-gaokao',
    title: '高考历史高中历史',
    owner: '历史老师定哥',
    description: '高中历史高频知识点、26年教龄经验与答题模板整理。',
    avatar: '史',
    subscribers: '9469',
    contents: '53791',
    category: '教育',
    certified: true,
    files: [],
    prompts: ['帮我梳理中国近代史时间线。', '总结材料题常见设问方式。'],
  },
  {
    id: 'energy-ai',
    title: '程永平 人工智能与能源',
    owner: '程永平',
    description: 'AI与能源、碳中和、算力协同和电力调度资料合集。',
    avatar: 'AI',
    subscribers: '3146',
    contents: '1841',
    category: '科技',
    certified: true,
    files: [],
    prompts: ['解释 AI 与能源调度的关系。', '整理碳中和背景下的技术路线。'],
  },
  {
    id: 'public-law',
    title: '公益法律援助智库',
    owner: '律法团队',
    description: '法律检索、案例分析和常见民事问题处理思路。',
    avatar: '法',
    subscribers: '1.9万',
    contents: '11966',
    category: '法律',
    certified: true,
    files: [],
    prompts: ['帮我整理劳动合同争议要点。', '生成一份法律咨询提纲。'],
  },
  {
    id: 'daoism',
    title: '道家大全-思想·养生·商业·传统文化',
    owner: '成在思维',
    description: '道德经、经典文献、传统文化应用与思想摘录。',
    avatar: '道',
    subscribers: '3362',
    contents: '566',
    category: '人文',
    files: [],
    prompts: ['解释道德经中无为的含义。'],
  },
  {
    id: 'parenting',
    title: '亲子教育',
    owner: '尘埃',
    description: '幼小衔接、小学1-6年级语数英科学资料整理。',
    avatar: '花',
    subscribers: '3228',
    contents: '3505',
    category: '教育',
    files: [],
    prompts: ['制定一份小学生阅读计划。'],
  },
  {
    id: 'stock-docs',
    title: '股票资料',
    owner: '一生何求',
    description: '缠论为主的投资资料、学习笔记和复盘样例。',
    avatar: '股',
    subscribers: '3321',
    contents: '32',
    category: '财经',
    files: [],
    prompts: ['解释趋势与背驰的基础概念。'],
  },
  {
    id: 'paper-writing',
    title: 'SCI论文与国自然课题科研写作',
    owner: 'Doctor阿楠',
    description: '论文选题、标书写作、投稿经验与研究设计资料库。',
    avatar: 'SCI',
    subscribers: '7005',
    contents: '817',
    category: '科技',
    certified: true,
    files: [],
    prompts: ['帮我拆解论文引言结构。'],
  },
];

const categories = ['热门', '科技', '教育', '职场', '财经', '产业', '健康', '法律', '人文', '生活'];

const currentView = computed<'home' | 'wikis' | 'discover' | 'detail'>(() => {
  if (route.name === 'ai-wikis') return 'wikis';
  if (route.name === 'ai-discover') return 'discover';
  if (route.name === 'ai-wiki-detail') return 'detail';
  return 'home';
});

const selectedWiki = computed(() => {
  const id = String(route.params.id || 'math-daily');
  return knowledgeBases.find((item) => item.id === id) || knowledgeBases[0];
});

const discoverItems = computed(() => {
  const keyword = discoverKeyword.value.trim().toLowerCase();
  return knowledgeBases.filter((item) => {
    const matchedCategory = activeCategory.value === '热门' || item.category === activeCategory.value;
    const matchedKeyword = !keyword || `${item.title}${item.description}${item.owner}`.toLowerCase().includes(keyword);
    return matchedCategory && matchedKeyword;
  });
});

const featuredWikis = computed(() => knowledgeBases.slice(0, 4));

const activeInputPlaceholder = computed(() => {
  if (currentView.value === 'detail' || currentView.value === 'wikis') return '基于知识库提问';
  return '有问题尽管问';
});

function navigateTo(path: string) {
  router.push(path);
}

function isActivePath(path: string) {
  return route.path === path;
}

function startNewChat() {
  homeQuestion.value = '';
  wikiQuestion.value = '';
  chatMessages.value = [
    { role: 'assistant', content: '新的对话已经准备好。你可以直接提问，也可以先进入某个知识库再基于资料追问。' },
  ];
  router.push('/ai');
}

function applyRoutePreset() {
  const presetQuestion = route.query.q;
  if (typeof presetQuestion === 'string' && presetQuestion.trim()) {
    homeQuestion.value = presetQuestion.trim();
  }

  const postId = route.query.postId;
  if (typeof postId === 'string' && postId.trim()) {
    homeQuestion.value = `请基于帖子 #${postId} 帮我整理重点，并给出可以继续追问的问题。`;
  }

  // 旧 summary/content 模式已下线，这里统一回落为普通问答，避免历史链接空白或报错。
  if (route.query.mode === 'summary' || route.query.mode === 'content') {
    homeQuestion.value ||= '请帮我整理这条内容的核心信息。';
  }
}

async function sendQuestion(source: 'home' | 'wiki' = 'home', prompt?: string) {
  const question = (prompt || (source === 'wiki' ? wikiQuestion.value : homeQuestion.value)).trim();
  if (!question || chatLoading.value) return;

  if (source === 'wiki') {
    wikiQuestion.value = '';
  } else {
    homeQuestion.value = '';
  }

  chatMessages.value.push({ role: 'user', content: question });
  chatLoading.value = true;

  try {
    const history = chatMessages.value.slice(-8).map((item) => ({
      role: item.role,
      content: item.content,
    }));
    const wiki = currentView.value === 'detail' ? selectedWiki.value : null;
    const context = wiki
      ? `当前知识库：${wiki.title}\n简介：${wiki.description}\n目录：${wiki.files.map((file) => file.title).join('、')}`
      : undefined;
    const result = await aiRagChat(history, context);
    chatMessages.value.push({
      role: 'assistant',
      content: result.reply || '小青暂时没有生成有效回答，请换一种问法再试。',
      citations: result.citations || [],
    });
  } catch {
    chatMessages.value.push({ role: 'assistant', content: '这次请求失败了，请稍后再试。' });
    message.error('小青回答失败');
  } finally {
    chatLoading.value = false;
    await nextTick();
    bottomInputRef.value?.focus();
  }
}

function askPrompt(prompt: string) {
  wikiQuestion.value = prompt;
  void sendQuestion('wiki');
}

function refreshDiscover() {
  const currentIndex = categories.indexOf(activeCategory.value);
  activeCategory.value = categories[(currentIndex + 1) % categories.length];
}

onMounted(() => {
  applyRoutePreset();
});

watch(
  () => [route.name, route.query.mode, route.query.q, route.query.postId, route.params.id],
  () => applyRoutePreset(),
);
</script>

<template>
  <div class="qing-page">
    <aside class="qing-sidebar">
      <div class="qing-brand" @click="navigateTo('/ai')">
        <span class="brand-mark">小</span>
        <strong>小青</strong>
      </div>

      <button class="new-chat-btn" @click="startNewChat">
        <n-icon size="16"><AddOutline /></n-icon>
        新对话
      </button>

      <nav class="side-nav">
        <button
          :class="{ active: isActivePath('/ai/wikis') || currentView === 'detail' }"
          @click="navigateTo('/ai/wikis')"
        >
          <n-icon size="16"><AlbumsOutline /></n-icon>
          <span>知识库</span>
          <n-icon class="nav-arrow" size="15"><ChevronForwardOutline /></n-icon>
        </button>
        <button
          :class="{ active: isActivePath('/ai/discover') }"
          @click="navigateTo('/ai/discover')"
        >
          <n-icon size="16"><CompassOutline /></n-icon>
          <span>发现广场</span>
          <n-icon class="nav-arrow" size="15"><ChevronForwardOutline /></n-icon>
        </button>
        <button>
          <n-icon size="16"><TimeOutline /></n-icon>
          <span>问答历史</span>
        </button>
      </nav>

      <div class="history-empty">暂无历史会话</div>
      <button class="about-btn">
        <n-icon size="16"><SparklesOutline /></n-icon>
        关于小青
        <n-icon class="nav-arrow" size="15"><ChevronForwardOutline /></n-icon>
      </button>
    </aside>

    <main class="qing-main" :class="`view-${currentView}`">
      <section v-if="currentView === 'home'" class="home-view">
        <div class="top-actions">
          <button class="desktop-btn">
            <n-icon size="16"><GridOutline /></n-icon>
            打开电脑版
            <n-icon size="15"><ChevronDownOutline /></n-icon>
          </button>
        </div>

        <div class="home-center">
          <div class="qing-logo">
            <span>小青</span>
            <small>knowledge</small>
          </div>

          <div class="ask-box large">
            <textarea
              v-model="homeQuestion"
              :placeholder="activeInputPlaceholder"
              @keydown.enter.exact.prevent="sendQuestion('home')"
            />
            <div class="ask-toolbar">
              <button>
                <n-icon size="15"><ChatbubbleEllipsesOutline /></n-icon>
                对话模式
                <n-icon size="13"><ChevronDownOutline /></n-icon>
              </button>
              <button>
                <n-icon size="15"><SparklesOutline /></n-icon>
                DS 快速
                <n-icon size="13"><ChevronDownOutline /></n-icon>
              </button>
              <span />
              <button class="icon-only" title="附加链接">
                <n-icon size="18"><LinkOutline /></n-icon>
              </button>
              <button
                class="send-btn"
                :disabled="chatLoading || !homeQuestion.trim()"
                @click="sendQuestion('home')"
              >
                <n-icon size="18"><PaperPlaneOutline /></n-icon>
              </button>
            </div>
          </div>
        </div>

        <section class="examples-section">
          <div class="section-head">
            <strong>精选示例</strong>
            <button @click="navigateTo('/ai/discover')">探索更多</button>
          </div>
          <div class="example-grid">
            <article
              v-for="item in featuredExamples"
              :key="item.id"
              class="example-card"
            >
              <div class="example-cover" :style="{ background: item.tint }">
                <DocumentTextOutline />
              </div>
              <strong>{{ item.title }}</strong>
              <footer>
                <span>{{ item.action }}</span>
                <span><n-icon size="13"><EyeOutline /></n-icon>{{ item.views }}</span>
              </footer>
            </article>
          </div>
        </section>
      </section>

      <section v-else-if="currentView === 'wikis'" class="wikis-view">
        <div class="sub-sidebar">
          <div class="mini-tools">
            <button><n-icon size="16"><ArrowBackOutline /></n-icon></button>
            <button><n-icon size="17"><SearchOutline /></n-icon></button>
          </div>
          <div class="wiki-group">
            <button class="group-head">
              <span>个人知识库</span>
              <n-icon size="17"><AddOutline /></n-icon>
            </button>
            <button class="wiki-item active" @click="navigateTo('/ai/wikis/math-daily')">
              <span class="file-mark">▣</span>
              哈哈的知识库
            </button>
          </div>
          <div class="wiki-group">
            <button class="group-head">
              <span>共享知识库</span>
              <n-icon size="17"><AddOutline /></n-icon>
            </button>
          </div>
          <div class="wiki-group">
            <button class="group-head">
              <span>订阅知识库</span>
              <n-icon size="17"><AddOutline /></n-icon>
            </button>
          </div>
          <button class="discover-more" @click="navigateTo('/ai/discover')">去发现更多知识库</button>
          <div class="storage-text">已使用8.52MB/50GB</div>
        </div>

        <div class="wiki-library">
          <header class="library-head">
            <div>
              <button @click="router.back()"><n-icon size="17"><ArrowBackOutline /></n-icon></button>
              <button><n-icon size="17"><ChevronForwardOutline /></n-icon></button>
              <strong>哈哈的知识库</strong>
            </div>
            <div>
              <button><n-icon size="17"><SearchOutline /></n-icon></button>
              <button><n-icon size="17"><ListOutline /></n-icon></button>
              <button><n-icon size="17"><AddOutline /></n-icon></button>
            </div>
          </header>

          <button class="document-card" @click="navigateTo('/ai/wikis/math-daily')">
            <div class="doc-preview">DOC</div>
            <strong>ima知识库使用指南.docx</strong>
            <footer>
              <span>W WORD</span>
              <span>11:58</span>
            </footer>
          </button>

          <div class="bottom-ask">
            <div class="ask-box compact">
              <textarea
                ref="bottomInputRef"
                v-model="wikiQuestion"
                placeholder="基于知识库提问"
                @keydown.enter.exact.prevent="sendQuestion('wiki')"
              />
              <div class="ask-toolbar">
                <button>
                  <n-icon size="15"><ChatbubbleEllipsesOutline /></n-icon>
                  对话模式
                  <n-icon size="13"><ChevronDownOutline /></n-icon>
                </button>
                <button>
                  DS 快速
                  <n-icon size="13"><ChevronDownOutline /></n-icon>
                </button>
                <span />
                <button
                  class="send-btn"
                  :disabled="chatLoading || !wikiQuestion.trim()"
                  @click="sendQuestion('wiki')"
                >
                  <n-icon size="18"><PaperPlaneOutline /></n-icon>
                </button>
              </div>
            </div>
            <small>内容由AI生成仅供参考</small>
          </div>
        </div>
      </section>

      <section v-else-if="currentView === 'discover'" class="discover-view">
        <header class="discover-head">
          <div class="qing-brand compact-brand">
            <span class="brand-mark">小</span>
            <strong>小青</strong>
          </div>
          <nav>
            <strong>发现</strong>
            <button @click="navigateTo('/ai/wikis')">知识库</button>
          </nav>
        </header>

        <div class="discover-content">
          <label class="search-strip">
            <n-icon size="18"><SearchOutline /></n-icon>
            <input v-model="discoverKeyword" placeholder="搜索知识库" />
          </label>

          <div class="section-head">
            <strong>精选</strong>
            <button @click="refreshDiscover">
              <n-icon size="14"><RefreshOutline /></n-icon>
              换一换
            </button>
          </div>
          <div class="wiki-card-grid featured">
            <article
              v-for="wiki in featuredWikis"
              :key="wiki.id"
              class="wiki-card"
              @click="navigateTo(`/ai/wikis/${wiki.id}`)"
            >
              <div class="wiki-avatar">{{ wiki.avatar }}</div>
              <div>
                <strong>{{ wiki.title }}</strong>
                <p>{{ wiki.description }}</p>
                <span>{{ wiki.subscribers }}人已订阅 | {{ wiki.contents }}个内容 | @{{ wiki.owner }}</span>
              </div>
            </article>
          </div>

          <div class="category-tabs">
            <button
              v-for="item in categories"
              :key="item"
              :class="{ active: activeCategory === item }"
              @click="activeCategory = item"
            >
              {{ item }}
            </button>
          </div>

          <div class="wiki-card-grid">
            <article
              v-for="wiki in discoverItems"
              :key="wiki.id"
              class="wiki-card"
              @click="navigateTo(`/ai/wikis/${wiki.id}`)"
            >
              <div class="wiki-avatar">{{ wiki.avatar }}</div>
              <div>
                <strong>{{ wiki.title }}</strong>
                <p>{{ wiki.description }}</p>
                <span>{{ wiki.subscribers }}人已订阅 | {{ wiki.contents }}个内容 | @{{ wiki.owner }}</span>
              </div>
            </article>
          </div>
        </div>
      </section>

      <section v-else class="detail-view">
        <aside class="detail-panel">
          <div class="detail-tools">
            <button @click="navigateTo('/ai/discover')"><n-icon size="16"><ArrowBackOutline /></n-icon></button>
            <button><n-icon size="16"><SparklesOutline /></n-icon></button>
          </div>
          <div class="wiki-profile">
            <div class="detail-avatar">{{ selectedWiki.avatar }}</div>
            <h2>{{ selectedWiki.title }}</h2>
            <span>@{{ selectedWiki.owner }} <small v-if="selectedWiki.certified">◆</small></span>
            <p>{{ selectedWiki.description }}</p>
            <div class="profile-stats">
              <strong>{{ selectedWiki.subscribers }}</strong>订阅
              <strong>{{ selectedWiki.contents }}</strong>浏览和问答
              <button>订阅</button>
            </div>
          </div>
          <div class="file-list">
            <strong>内容({{ selectedWiki.contents }})</strong>
            <button
              v-for="file in selectedWiki.files"
              :key="file.id"
              :class="{ active: file.id === 'readme' }"
            >
              <span>{{ file.cover }}</span>
              <div>
                <b>{{ file.title }}</b>
                <small>{{ file.meta }}</small>
              </div>
            </button>
          </div>
        </aside>

        <main class="chat-workspace">
          <div class="empty-task">
            <div class="soft-icon">
              <n-icon size="25"><ChatbubbleEllipsesOutline /></n-icon>
            </div>
            <p>基于知识库问答或创建任务</p>
            <button
              v-for="prompt in selectedWiki.prompts"
              :key="prompt"
              @click="askPrompt(prompt)"
            >
              {{ prompt }}
              <n-icon size="16"><ChevronForwardOutline /></n-icon>
            </button>
          </div>

          <div v-if="chatMessages.length > 1" class="chat-thread">
            <article
              v-for="(item, index) in chatMessages"
              :key="`${item.role}-${index}`"
              :class="item.role"
            >
              {{ item.content }}
            </article>
          </div>

          <div class="bottom-ask detail-ask">
            <div class="ask-box compact">
              <textarea
                ref="bottomInputRef"
                v-model="wikiQuestion"
                placeholder="基于知识库提问"
                @keydown.enter.exact.prevent="sendQuestion('wiki')"
              />
              <div class="ask-toolbar">
                <button>
                  <n-icon size="15"><ChatbubbleEllipsesOutline /></n-icon>
                  对话模式
                  <n-icon size="13"><ChevronDownOutline /></n-icon>
                </button>
                <button>
                  DS 快速
                  <n-icon size="13"><ChevronDownOutline /></n-icon>
                </button>
                <span />
                <button
                  class="send-btn"
                  :disabled="chatLoading || !wikiQuestion.trim()"
                  @click="sendQuestion('wiki')"
                >
                  <n-icon size="18"><PaperPlaneOutline /></n-icon>
                </button>
              </div>
            </div>
            <small>内容由AI生成仅供参考</small>
          </div>
        </main>
      </section>
    </main>
  </div>
</template>

<style scoped lang="scss">
.qing-page {
  min-height: calc(100vh - var(--cf-header-height) - 48px);
  display: grid;
  grid-template-columns: 240px minmax(0, 1fr);
  margin: -24px;
  background: #fff;
  color: #202124;
}

.qing-sidebar {
  min-height: calc(100vh - var(--cf-header-height));
  background: #f3f3f3;
  border-right: 1px solid #ebebeb;
  padding: 26px 10px 12px;
  display: flex;
  flex-direction: column;
}

.qing-brand {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  margin: 0 4px 28px;
  cursor: pointer;
  color: #1f1f1f;
}

.brand-mark {
  width: 26px;
  height: 26px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 8px;
  background: linear-gradient(135deg, #d9ffe6, #111);
  color: #fff;
  font-weight: 900;
}

.new-chat-btn,
.side-nav button,
.about-btn,
.group-head,
.wiki-item {
  border: 0;
  background: transparent;
  color: inherit;
  cursor: pointer;
}

.new-chat-btn {
  height: 40px;
  border-radius: 8px;
  background: #fff;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  font-weight: 700;
  margin-bottom: 28px;
}

.side-nav {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.side-nav button,
.about-btn {
  min-height: 42px;
  border-radius: 8px;
  padding: 0 10px;
  display: grid;
  grid-template-columns: 20px 1fr 18px;
  align-items: center;
  gap: 6px;
  text-align: left;
  color: #232323;
}

.side-nav button.active,
.side-nav button:hover,
.about-btn:hover {
  background: #e8f5ee;
}

.nav-arrow {
  justify-self: end;
}

.history-empty {
  margin: 18px 0 0 34px;
  color: #999;
  font-size: 13px;
}

.about-btn {
  margin-top: auto;
  color: #727272;
}

.qing-main {
  min-width: 0;
  position: relative;
  background: #fff;
}

.top-actions {
  position: absolute;
  top: 20px;
  right: 22px;
}

.desktop-btn {
  height: 34px;
  padding: 0 14px;
  border: 0;
  border-radius: 8px;
  background: #222;
  color: #fff;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  cursor: pointer;
}

.home-view {
  min-height: calc(100vh - var(--cf-header-height));
  display: flex;
  flex-direction: column;
}

.home-center {
  flex: 1;
  min-height: 560px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 80px 24px 20px;
}

.qing-logo {
  text-align: center;
  margin-bottom: 46px;
}

.qing-logo span {
  display: block;
  font-size: 52px;
  line-height: 0.9;
  font-weight: 900;
  letter-spacing: 0;
}

.qing-logo small {
  display: block;
  margin-top: 10px;
  letter-spacing: 0.55em;
  color: #333;
  font-size: 12px;
}

.ask-box {
  border: 1px solid #d9d9d9;
  border-radius: 22px;
  background: #fff;
  box-shadow: 0 14px 32px rgba(26, 26, 26, 0.08);
  padding: 12px;
}

.ask-box.large {
  width: min(960px, calc(100vw - 360px));
  min-height: 122px;
}

.ask-box.compact {
  width: min(760px, calc(100vw - 380px));
}

.ask-box textarea {
  width: 100%;
  min-height: 48px;
  border: 0;
  outline: 0;
  resize: none;
  color: #1f1f1f;
  padding: 6px 2px;
  font-size: 16px;
  background: transparent;
}

.ask-box textarea::placeholder {
  color: #c3c3c3;
}

.ask-toolbar {
  display: grid;
  grid-template-columns: auto auto minmax(20px, 1fr) auto auto;
  gap: 8px;
  align-items: center;
}

.ask-toolbar button {
  min-height: 34px;
  border: 1px solid #ececec;
  border-radius: 999px;
  background: #fff;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  padding: 0 10px;
  cursor: pointer;
}

.ask-toolbar .icon-only,
.ask-toolbar .send-btn {
  width: 34px;
  padding: 0;
}

.ask-toolbar .send-btn {
  border: 0;
  color: #fff;
  background: #c8c8c8;
}

.ask-toolbar .send-btn:not(:disabled) {
  background: #5fcb91;
}

.examples-section {
  width: min(1210px, calc(100vw - 340px));
  margin: 0 auto 34px;
}

.section-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 18px;
}

.section-head button {
  border: 0;
  background: transparent;
  color: #68717d;
  display: inline-flex;
  align-items: center;
  gap: 5px;
  cursor: pointer;
}

.example-grid {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 16px;
}

.example-card {
  border: 1px solid #f0f0f0;
  border-radius: 10px;
  background: #fff;
  overflow: hidden;
}

.example-cover {
  height: 118px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #91a0b4;
}

.example-cover svg {
  width: 50px;
  height: 50px;
}

.example-card strong {
  display: block;
  padding: 12px 12px 8px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  font-size: 14px;
}

.example-card footer {
  display: flex;
  justify-content: space-between;
  padding: 0 12px 12px;
  color: #a5aab3;
  font-size: 12px;
}

.wikis-view,
.detail-view {
  display: grid;
  grid-template-columns: 240px minmax(0, 1fr);
  min-height: calc(100vh - var(--cf-header-height));
}

.sub-sidebar,
.detail-panel {
  border-right: 1px solid #e7e7e7;
  padding: 10px 4px 20px;
  display: flex;
  flex-direction: column;
}

.mini-tools,
.library-head,
.detail-tools {
  display: flex;
  justify-content: space-between;
  gap: 8px;
  align-items: center;
}

.mini-tools button,
.library-head button,
.detail-tools button {
  width: 30px;
  height: 30px;
  border: 0;
  border-radius: 8px;
  background: transparent;
  cursor: pointer;
}

.wiki-group {
  padding-top: 14px;
  border-bottom: 1px solid #ebebeb;
}

.group-head {
  width: 100%;
  display: flex;
  justify-content: space-between;
  padding: 10px 12px;
  color: #59616c;
}

.wiki-item {
  width: 100%;
  min-height: 34px;
  border-radius: 7px;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 0 12px;
  text-align: left;
}

.wiki-item.active {
  background: #e4f2eb;
}

.file-mark {
  color: #69d7bd;
}

.discover-more {
  margin: 14px 0 0;
  height: 52px;
  border: 0;
  border-radius: 8px;
  background: #f7f7f7;
  color: #47b880;
  text-align: left;
  padding: 0 14px;
  cursor: pointer;
}

.storage-text {
  margin: auto auto 0;
  color: #a7acb7;
  font-size: 12px;
}

.wiki-library {
  position: relative;
  padding: 14px 40px 160px;
}

.library-head div {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

.document-card {
  width: 146px;
  margin-top: 24px;
  border: 1px solid #e7e7e7;
  border-radius: 8px;
  background: #fff;
  padding: 10px;
  text-align: left;
  cursor: pointer;
}

.doc-preview {
  height: 74px;
  border-radius: 6px;
  background: #f8fafb;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #99a0aa;
  font-weight: 800;
  margin-bottom: 8px;
}

.document-card strong {
  display: block;
  font-size: 13px;
  line-height: 1.55;
  word-break: break-all;
}

.document-card footer {
  display: flex;
  justify-content: space-between;
  margin-top: 8px;
  color: #adb3bd;
  font-size: 12px;
}

.bottom-ask {
  position: absolute;
  left: 50%;
  bottom: 28px;
  transform: translateX(-50%);
  text-align: center;
}

.bottom-ask small {
  display: block;
  margin-top: 8px;
  color: #c7c7c7;
  font-size: 12px;
}

.discover-view {
  min-height: calc(100vh - var(--cf-header-height));
}

.discover-head {
  height: 106px;
  border-bottom: 1px solid #ededed;
  display: flex;
  align-items: center;
  padding: 0 32px;
  gap: min(25vw, 450px);
}

.compact-brand {
  margin: 0;
}

.discover-head nav {
  display: flex;
  align-items: center;
  gap: 22px;
}

.discover-head nav strong {
  font-size: 22px;
}

.discover-head nav button {
  border: 0;
  background: transparent;
  font-weight: 700;
  cursor: pointer;
}

.discover-content {
  width: min(768px, calc(100vw - 360px));
  margin: 32px auto 60px;
}

.search-strip {
  height: 34px;
  border-radius: 8px;
  background: #f7f7f7;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 0 12px;
  margin-bottom: 24px;
  color: #b0b4bb;
}

.search-strip input {
  flex: 1;
  min-width: 0;
  border: 0;
  outline: 0;
  background: transparent;
}

.wiki-card-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
  margin-bottom: 24px;
}

.wiki-card {
  min-height: 92px;
  border: 1px solid #eeeeee;
  border-radius: 14px;
  background: #fff;
  padding: 16px;
  display: grid;
  grid-template-columns: 56px minmax(0, 1fr);
  gap: 12px;
  cursor: pointer;
}

.wiki-card:hover {
  border-color: #cfe8da;
  box-shadow: 0 12px 32px rgba(39, 130, 86, 0.09);
}

.wiki-avatar {
  width: 56px;
  height: 56px;
  border-radius: 10px;
  background: #eff7f3;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 900;
}

.wiki-card strong,
.wiki-card p,
.wiki-card span {
  display: block;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.wiki-card p {
  margin: 6px 0;
  color: #6c7380;
  font-size: 12px;
}

.wiki-card span {
  color: #87909e;
  font-size: 12px;
}

.category-tabs {
  display: flex;
  flex-wrap: wrap;
  gap: 18px;
  margin-bottom: 18px;
  color: #7a818b;
}

.category-tabs button {
  border: 0;
  background: transparent;
  cursor: pointer;
  color: inherit;
}

.category-tabs button.active {
  color: #1f1f1f;
  font-weight: 800;
}

.detail-panel {
  padding: 14px 12px;
  overflow-y: auto;
}

.wiki-profile {
  padding: 24px 0 14px;
  border-bottom: 1px solid #ededed;
}

.detail-avatar {
  width: 88px;
  height: 88px;
  border-radius: 8px;
  background: #eef4f1;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 900;
  margin-bottom: 12px;
}

.wiki-profile h2 {
  margin: 0 0 8px;
  font-size: 18px;
  line-height: 1.45;
}

.wiki-profile span {
  color: #637083;
  font-size: 13px;
}

.wiki-profile small {
  color: #f1b400;
}

.wiki-profile p {
  color: #4f5661;
  font-size: 13px;
  line-height: 1.65;
}

.profile-stats {
  display: flex;
  align-items: center;
  gap: 7px;
  font-size: 12px;
}

.profile-stats button {
  margin-left: auto;
  border: 0;
  border-radius: 6px;
  background: #242424;
  color: #fff;
  padding: 6px 12px;
  cursor: pointer;
}

.file-list {
  padding-top: 16px;
}

.file-list > strong {
  display: block;
  margin-bottom: 10px;
}

.file-list button {
  width: 100%;
  min-height: 54px;
  border: 0;
  border-radius: 7px;
  background: transparent;
  display: grid;
  grid-template-columns: 34px minmax(0, 1fr);
  gap: 8px;
  align-items: center;
  text-align: left;
  cursor: pointer;
}

.file-list button.active,
.file-list button:hover {
  background: #f0f0f0;
}

.file-list b,
.file-list small {
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.file-list small {
  color: #98a0aa;
  margin-top: 4px;
}

.chat-workspace {
  min-width: 0;
  position: relative;
  min-height: calc(100vh - var(--cf-header-height));
}

.empty-task {
  width: min(420px, 80vw);
  position: absolute;
  left: 50%;
  top: 42%;
  transform: translate(-50%, -50%);
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
  color: #757b85;
}

.soft-icon {
  width: 32px;
  height: 32px;
  border-radius: 6px;
  background: #e4f3ec;
  color: #9bb9a8;
  display: flex;
  align-items: center;
  justify-content: center;
}

.empty-task button {
  width: 100%;
  min-height: 36px;
  border: 0;
  border-radius: 8px;
  background: #f7f7f7;
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 0 12px;
  color: #5c626b;
  cursor: pointer;
}

.chat-thread {
  width: min(720px, calc(100vw - 620px));
  margin: 60px auto 180px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.chat-thread article {
  max-width: 82%;
  padding: 12px 14px;
  border-radius: 14px;
  background: #f6f7f8;
  line-height: 1.8;
  white-space: pre-wrap;
}

.chat-thread article.user {
  align-self: flex-end;
  color: #fff;
  background: #1f1f1f;
}

@media (max-width: 1180px) {
  .example-grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }

  .discover-content,
  .examples-section,
  .ask-box.large {
    width: calc(100vw - 320px);
  }
}

@media (max-width: 900px) {
  .qing-page,
  .wikis-view,
  .detail-view {
    grid-template-columns: 1fr;
    margin: -16px;
  }

  .qing-sidebar,
  .sub-sidebar,
  .detail-panel {
    min-height: auto;
  }

  .qing-sidebar {
    position: sticky;
    top: var(--cf-header-height);
    z-index: 2;
  }

  .ask-box.large,
  .ask-box.compact,
  .examples-section,
  .discover-content,
  .chat-thread {
    width: calc(100vw - 48px);
  }

  .example-grid,
  .wiki-card-grid {
    grid-template-columns: 1fr;
  }

  .bottom-ask {
    position: sticky;
    left: auto;
    bottom: 14px;
    transform: none;
    margin: 24px auto;
    width: calc(100vw - 48px);
  }
}
</style>
