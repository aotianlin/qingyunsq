<script setup lang="ts">
import { computed, nextTick, ref } from 'vue';
import { useMessage } from 'naive-ui';
import {
  AddOutline,
  AttachOutline,
  ChatbubbleEllipsesOutline,
  ChevronForwardOutline,
  CodeSlashOutline,
  CopyOutline,
  DocumentTextOutline,
  EarthOutline,
  EllipsisHorizontalOutline,
  ImageOutline,
  RefreshOutline,
  SendOutline,
  SparklesOutline,
  StarOutline,
  ThumbsDownOutline,
  ThumbsUpOutline,
  TimeOutline,
} from '@vicons/ionicons5';
import { aiRagChat } from '@/api/ai';
import { copyTextToClipboard } from '@/utils/clipboard';
import type { AiCitation } from '@/types/ai';

type ChatMessage = {
  role: 'user' | 'assistant';
  content: string;
  time: string;
  citations?: AiCitation[];
};

const message = useMessage();
const draft = ref('');
const loading = ref(false);
const chatStreamRef = ref<HTMLElement | null>(null);
const selectedModel = ref('GPT-4o');
const activeLeftNav = ref<'chat' | 'favorite' | 'history'>('chat');
const quickQuestionOffset = ref(0);

const upgradeProVisible = ref(false);
const upgradeOption = ref('monthly');
const checkoutSimulating = ref(false);

const favoriteConversations = ref([
  { title: '已收藏：如何学好数据结构与算法？' },
  { title: '已收藏：期末复习与时间规划技巧' },
  { title: '已收藏：考研英语高频词汇精选' }
]);
const conversations = [
  { title: '如何高效准备期末考试？', active: true },
  { title: '推荐一些学习资源' },
  { title: 'React 和 Vue 的区别' },
  { title: 'Python 装饰器的应用场景' },
  { title: '如何制定每日学习计划' },
  { title: '操作系统的进程和线程区别' },
  { title: '深度学习入门建议' },
];

const quickQuestionPool = ['如何制定学习计划？', '有哪些高效的记忆方法？', '如何克服考试焦虑？', '帮我整理复习清单', '论文开题怎么准备？', '如何提高课堂笔记质量？'];
const quickQuestions = computed(() => Array.from({ length: 3 }, (_, index) => quickQuestionPool[(quickQuestionOffset.value + index) % quickQuestionPool.length]));

const modelOptions = [
  { name: 'GPT-4o', desc: '最智能的模型，适合大多数任务', active: true, tag: '推荐', color: '#00bfa8' },
  { name: 'Claude 3.5 Sonnet', desc: '擅长分析和深度推理', color: '#f97316' },
  { name: 'Gemini 1.5 Pro', desc: '擅长处理长文本和复杂推理', color: '#4f7cff' },
];

const abilities = ref([
  { label: '联网搜索', icon: EarthOutline, enabled: true },
  { label: '代码解释器', icon: CodeSlashOutline, enabled: true },
  { label: '文档分析', icon: DocumentTextOutline, enabled: false },
  { label: '图像生成', icon: ImageOutline, enabled: false },
]);

const stats = [
  { value: 12, label: '今日对话' },
  { value: 156, label: '本月对话' },
  { value: '98%', label: '满意度' },
];

const messages = ref<ChatMessage[]>([
  {
    role: 'user',
    time: '10:30',
    content: '如何高效准备期末考试？',
  },
  {
    role: 'assistant',
    time: '10:30',
    content:
      '高效准备期末考试需要科学的规划和方法，以下是一些实用的建议：\n\n1. 制定学习计划\n   · 根据考试时间和科目数量，制定详细的学习计划\n   · 将大目标分解为小任务，合理分配每天的学习时间\n\n2. 掌握学习方法\n   · 主动学习：通过总结、提问、讨论等方式加深理解\n   · 间隔重复：定期复习已学内容，强化记忆\n\n3. 善用学习资源\n   · 整理课堂笔记和教材重点\n   · 利用历年真题和模拟题进行练习\n\n4. 保持良好状态\n   · 保证充足的睡眠和适当的运动\n   · 合理安排休息时间，避免过度疲劳',
  },
]);

const historyGroups = computed(() => [
  { title: '今天', items: conversations.slice(0, 3) },
  { title: '昨天', items: conversations.slice(3, 5) },
  { title: '更早', items: conversations.slice(5) },
]);

async function scrollToBottom() {
  await nextTick();
  if (chatStreamRef.value) {
    chatStreamRef.value.scrollTop = chatStreamRef.value.scrollHeight;
  }
}

async function sendQuestion() {
  const question = draft.value.trim();
  if (!question || loading.value) return;

  draft.value = '';
  messages.value.push({ role: 'user', content: question, time: '现在' });
  loading.value = true;
  await scrollToBottom();

  try {
    const history = messages.value.slice(-8).map((item) => ({ role: item.role, content: item.content }));
    const result = await aiRagChat(history);
    messages.value.push({
      role: 'assistant',
      content: result.reply || '暂时没有生成有效回复，请换一种问法再试。',
      time: '现在',
      citations: result.citations || [],
    });
  } catch {
    messages.value.push({
      role: 'assistant',
      content: '这次问答请求失败了，请稍后再试。',
      time: '现在',
    });
  } finally {
    loading.value = false;
    await scrollToBottom();
  }
}

function startNewChat() {
  draft.value = '';
  messages.value = [
    {
      role: 'assistant',
      content: '新的对话已开始。你可以直接输入问题，或从下方快捷问题里选择一个。',
      time: '现在',
    },
  ];
  activeLeftNav.value = 'chat';
  void scrollToBottom();
}

function selectLeftNav(nav: 'chat' | 'favorite' | 'history') {
  activeLeftNav.value = nav;
  if (nav === 'favorite') {
    message.info('已切换到收藏视图，收藏的回答会集中展示在这里');
  } else if (nav === 'history') {
    message.info('已切换到历史记录视图');
  }
}

function openConversation(title: string) {
  messages.value = [
    { role: 'user', content: title, time: '历史' },
    { role: 'assistant', content: `已打开「${title}」这段对话。你可以继续追问，我会接着上下文回答。`, time: '现在' },
  ];
  activeLeftNav.value = 'chat';
  void scrollToBottom();
}

function showAllHistory(title: string) {
  activeLeftNav.value = 'history';
  message.info(`已展开「${title}」的历史对话`);
}

function upgradePro() {
  upgradeProVisible.value = true;
  checkoutSimulating.value = false;
}

async function simulateCheckout() {
  checkoutSimulating.value = true;
  await new Promise((resolve) => setTimeout(resolve, 2000));
  checkoutSimulating.value = false;
  upgradeProVisible.value = false;
  message.success('升级 Pro 会员成功！感谢您的支持。');
}

function retryAssistantAnswer(index: number) {
  const previousUser = [...messages.value.slice(0, index)].reverse().find((item) => item.role === 'user');
  if (!previousUser || loading.value) {
    message.warning('没有可重试的问题');
    return;
  }
  messages.value.splice(index, 1);
  draft.value = previousUser.content;
  void sendQuestion();
}

function continueAnswer() {
  if (loading.value) return;
  draft.value = '请继续展开上一个回答，并补充可执行步骤。';
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

async function openAttachmentPicker() {
  message.loading('正在从本地选择并解析文档...');
  
  const mockFiles = [
    { name: '计算机网络重点考点.docx', summary: '【已关联本地文档 [计算机网络重点考点.docx]：主要包含滑动窗口协议、GBN和SR对比分析，共1200字。】请结合此文档，为我梳理这几类协议的优缺点和适用场景：' },
    { name: 'Vue3状态管理大作业.md', summary: '【已关联本地文档 [Vue3状态管理大作业.md]：主要分析Pinia核心架构与持久化缓存设计，共900字。】请分析此作业的合理性：' },
    { name: 'CET6真听力文本.txt', summary: '【已关联本地文档 [CET6真听力文本.txt]：主要为第3套常考高频句型，共600字。】请整理该文档中的难点单词与长难句：' }
  ];
  
  await new Promise((resolve) => setTimeout(resolve, 1500));
  const selected = mockFiles[Math.floor(Math.random() * mockFiles.length)];
  draft.value = selected.summary + draft.value;
  message.success(`已解析 [${selected.name}] 并合并上下文到输入框！`);
}

function toggleWebSearch() {
  const item = abilities.value.find((ability) => ability.label === '联网搜索');
  if (item) item.enabled = !item.enabled;
  message.info(item?.enabled ? '已开启联网搜索' : '已关闭联网搜索');
}

function openModelSelector() {
  message.info('可在右侧模型列表切换当前模型');
}

function showMoreModels() {
  message.info('更多模型正在接入中');
}

function toggleAbility(label: string) {
  const item = abilities.value.find((ability) => ability.label === label);
  if (!item) return;
  item.enabled = !item.enabled;
}

async function copyAssistantAnswer(content: string) {
  if (await copyTextToClipboard(content)) {
    message.success('已复制');
  } else {
    message.warning('复制失败，请手动选中内容复制');
  }
}
</script>

<template>
  <div class="ai-assistant-page">
    <aside class="ai-left">
      <button class="new-chat-btn" @click="startNewChat">
        <n-icon size="18"><AddOutline /></n-icon>
        <span>新建对话</span>
        <kbd>⌘ K</kbd>
      </button>

      <section class="left-nav-card">
        <button class="left-link" :class="{ active: activeLeftNav === 'chat' }" @click="selectLeftNav('chat')">
          <n-icon size="18"><ChatbubbleEllipsesOutline /></n-icon>
          对话
        </button>
        <button class="left-link" :class="{ active: activeLeftNav === 'favorite' }" @click="selectLeftNav('favorite')">
          <n-icon size="18"><StarOutline /></n-icon>
          收藏
        </button>
        <button class="left-link" :class="{ active: activeLeftNav === 'history' }" @click="selectLeftNav('history')">
          <n-icon size="18"><TimeOutline /></n-icon>
          历史记录
        </button>
      </section>

      <section class="history-card">
        <template v-if="activeLeftNav === 'favorite'">
          <div class="history-group">
            <p>收藏的问答</p>
            <button
              v-for="item in favoriteConversations"
              :key="item.title"
              @click="openConversation(item.title)"
            >
              <span>{{ item.title }}</span>
              <n-icon size="16"><StarOutline /></n-icon>
            </button>
          </div>
        </template>
        <template v-else>
          <div v-for="group in historyGroups" :key="group.title" class="history-group">
            <p>{{ group.title }}</p>
            <button
              v-for="item in group.items"
              :key="item.title"
              :class="{ active: item.active }"
              @click="openConversation(item.title)"
            >
              <span>{{ item.title }}</span>
              <n-icon v-if="item.active" size="16"><EllipsisHorizontalOutline /></n-icon>
            </button>
            <button v-if="group.title !== '今天'" class="history-more" @click="showAllHistory(group.title)">查看全部 ({{ group.title === '昨天' ? 6 : 12 }})</button>
          </div>
        </template>
      </section>

      <section class="upgrade-card">
        <div>
          <h3>升级到 Pro 版</h3>
          <p>解锁更多模型和高级功能</p>
          <button @click="upgradePro">立即升级</button>
        </div>
        <span class="crown-visual" />
      </section>
    </aside>

    <main class="chat-main">
      <header class="chat-title">
        <h1>如何高效准备期末考试？</h1>
      </header>

      <div ref="chatStreamRef" class="chat-stream">
        <article
          v-for="(item, index) in messages"
          :key="`${item.role}-${index}`"
          class="message-row"
          :class="item.role"
        >
          <template v-if="item.role === 'user'">
            <div class="user-bubble">{{ item.content }}</div>
            <span class="message-time">{{ item.time }}</span>
            <span class="user-avatar">哈</span>
          </template>

          <template v-else>
            <span class="ai-avatar"><n-icon size="18"><SparklesOutline /></n-icon></span>
            <div class="assistant-card">
              <header>
                <strong>AI 助手</strong>
                <span>{{ item.time }}</span>
              </header>
              <pre>{{ item.content }}</pre>
              <footer>
                <button @click="copyAssistantAnswer(item.content)">
                  <n-icon size="15"><CopyOutline /></n-icon>
                  复制
                </button>
                <button @click="retryAssistantAnswer(index)">
                  <n-icon size="15"><RefreshOutline /></n-icon>
                  再试一次
                </button>
                <button @click="continueAnswer">
                  <n-icon size="15"><SparklesOutline /></n-icon>
                  继续生成
                </button>
                <button @click="markAssistantFeedback(true)">
                  <n-icon size="15"><ThumbsUpOutline /></n-icon>
                  有帮助
                </button>
                <button @click="markAssistantFeedback(false)">
                  <n-icon size="15"><ThumbsDownOutline /></n-icon>
                  没帮助
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
        <button class="refresh-suggestions" @click="refreshSuggestions"><n-icon size="18"><RefreshOutline /></n-icon></button>
      </div>

      <section class="input-panel">
        <textarea
          v-model="draft"
          placeholder="输入你的问题，Enter 发送，Shift + Enter 换行"
          @keydown.enter.exact.prevent="sendQuestion"
        />
        <div class="input-tools">
          <div>
            <button @click="openAttachmentPicker"><n-icon size="20"><AttachOutline /></n-icon></button>
            <button @click="toggleWebSearch"><n-icon size="20"><EarthOutline /></n-icon></button>
          </div>
          <div class="send-cluster">
            <button class="model-chip" @click="openModelSelector">
              {{ selectedModel }}
              <ChevronForwardOutline class="chevron" />
            </button>
            <button class="send-btn" :disabled="loading || !draft.trim()" @click="sendQuestion">
              <n-icon size="20"><SendOutline /></n-icon>
            </button>
          </div>
        </div>
      </section>
    </main>

    <aside class="ai-right">
      <section class="right-card">
        <div class="panel-title">
          <h2>模型选择</h2>
          <button @click="showMoreModels">查看更多 <n-icon size="12"><ChevronForwardOutline /></n-icon></button>
        </div>
        <button
          v-for="model in modelOptions"
          :key="model.name"
          class="model-row"
          :class="{ active: selectedModel === model.name }"
          @click="selectedModel = model.name"
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
        <h2>能力</h2>
        <div class="ability-list">
          <button v-for="ability in abilities" :key="ability.label" class="ability-row" @click="toggleAbility(ability.label)">
            <span class="ability-label">
              <b class="ability-icon">
                <n-icon size="17"><component :is="ability.icon" /></n-icon>
              </b>
              {{ ability.label }}
            </span>
            <span class="ability-toggle" :class="{ on: ability.enabled }" />
          </button>
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
        <svg class="stats-chart" viewBox="0 0 320 176" role="img" aria-label="对话统计趋势">
          <defs>
            <linearGradient id="statsFill" x1="0" x2="0" y1="0" y2="1">
              <stop offset="0" stop-color="#00bfa8" stop-opacity="0.28" />
              <stop offset="1" stop-color="#00bfa8" stop-opacity="0" />
            </linearGradient>
          </defs>
          <g class="chart-grid">
            <line x1="42" y1="28" x2="306" y2="28" />
            <line x1="42" y1="82" x2="306" y2="82" />
            <line x1="42" y1="136" x2="306" y2="136" />
          </g>
          <g class="chart-axis-labels">
            <text x="16" y="32">100</text>
            <text x="22" y="86">50</text>
            <text x="28" y="140">0</text>
          </g>
          <path class="chart-area" d="M42 116 L86 88 L130 72 L174 92 L218 50 L262 88 L306 70 L306 136 L42 136 Z" />
          <path class="chart-line" d="M42 116 L86 88 L130 72 L174 92 L218 50 L262 88 L306 70" />
          <g class="chart-points">
            <circle cx="42" cy="116" r="4" /><circle cx="86" cy="88" r="4" /><circle cx="130" cy="72" r="4" />
            <circle cx="174" cy="92" r="4" /><circle cx="218" cy="50" r="4" /><circle cx="262" cy="88" r="4" />
            <circle cx="306" cy="70" r="4" />
          </g>
          <g class="chart-date-labels">
            <text x="42" y="164">5/20</text><text x="86" y="164">5/21</text><text x="130" y="164">5/22</text>
            <text x="174" y="164">5/23</text><text x="218" y="164">5/24</text><text x="262" y="164">5/25</text>
            <text x="306" y="164">5/26</text>
          </g>
        </svg>
      </section>
    </aside>

    <!-- Pro Upgrade Modal -->
    <NModal v-model:show="upgradeProVisible" preset="card" class="upload-modal" title="升级到 Pro 会员" :bordered="false" style="width: min(560px, calc(100vw - 32px));">
      <div class="upload-form">
        <p style="color: var(--cf-text-secondary); line-height: 1.6; margin-top: 0;">
          升级 Pro 即可享受更强大的大模型服务，获得长上下文分析、联网搜索、代码沙箱及高级绘图功能。
        </p>
        
        <div style="display: grid; grid-template-columns: 1fr 1fr; gap: 16px; margin: 12px 0;">
          <div 
            style="border: 2px solid; border-radius: 12px; padding: 18px; cursor: pointer; text-align: center; transition: all 0.2s;"
            :style="{ borderColor: upgradeOption === 'monthly' ? 'var(--cf-primary)' : 'var(--cf-border)', background: upgradeOption === 'monthly' ? 'var(--cf-primary-soft)' : 'var(--cf-bg-readable)' }"
            @click="upgradeOption = 'monthly'"
          >
            <strong style="font-size: 16px; display: block; margin-bottom: 4px;">按月订阅</strong>
            <span style="font-size: 24px; font-weight: 800; color: var(--cf-primary);">¥ 19</span>
            <small style="display: block; margin-top: 6px; color: var(--cf-text-muted);">每月自动续期，随时取消</small>
          </div>

          <div 
            style="border: 2px solid; border-radius: 12px; padding: 18px; cursor: pointer; text-align: center; transition: all 0.2s;"
            :style="{ borderColor: upgradeOption === 'yearly' ? 'var(--cf-primary)' : 'var(--cf-border)', background: upgradeOption === 'yearly' ? 'var(--cf-primary-soft)' : 'var(--cf-bg-readable)' }"
            @click="upgradeOption = 'yearly'"
          >
            <strong style="font-size: 16px; display: block; margin-bottom: 4px;">按年订阅</strong>
            <span style="font-size: 24px; font-weight: 800; color: var(--cf-primary);">¥ 159</span>
            <small style="display: block; margin-top: 6px; color: var(--cf-text-muted);">合 ¥13.25/月，省 30%</small>
          </div>
        </div>

        <div style="display: flex; flex-direction: column; align-items: center; padding: 16px; border: 1px dashed var(--cf-border); border-radius: 10px; background: var(--cf-bg-soft); margin: 8px 0;">
          <span style="font-size: 13px; color: var(--cf-text-secondary); margin-bottom: 10px; font-weight: 700;">
            微信/支付宝扫码支付 (模拟沙箱通道)
          </span>
          <img 
            src="https://api.dicebear.com/7.x/identicon/svg?seed=campus-forum-pay" 
            alt="Mock Payment QR Code" 
            style="width: 140px; height: 140px; border-radius: 8px; border: 1px solid var(--cf-border); background: white; padding: 6px;"
          />
        </div>

        <div class="actions">
          <NButton type="primary" :loading="checkoutSimulating" @click="simulateCheckout">我已完成支付</NButton>
          <NButton @click="upgradeProVisible = false">取消</NButton>
        </div>
      </div>
    </NModal>
  </div>
</template>

<style lang="scss">
.ai-assistant-page {
  height: calc(100vh - 112px);
  min-height: 640px;
  display: grid;
  grid-template-columns: 280px minmax(0, 1fr) 360px;
  gap: 28px;
  padding: 8px 0 16px;
  overflow: hidden;
  color: var(--cf-text-primary);
  background: var(--cf-page-bg);
}

.ai-assistant-page button,
.ai-assistant-page textarea {
  font: inherit;
}

.ai-assistant-page button {
  border: 0;
  background: transparent;
  color: inherit;
  cursor: pointer;
}

.ai-left,
.ai-right {
  position: sticky;
  top: 8px;
  align-self: start;
  display: flex;
  flex-direction: column;
  gap: 20px;
  max-height: calc(100vh - 132px);
}

.ai-left {
  padding: 0 0 0 10px;
}

.new-chat-btn,
.left-nav-card,
.history-card,
.upgrade-card,
.assistant-card,
.input-panel,
.right-card {
  background: var(--cf-card-bg);
  border: 0;
  border-radius: 20px;
  box-shadow: var(--cf-card-shadow);
  backdrop-filter: blur(var(--cf-backdrop-blur));
  -webkit-backdrop-filter: blur(var(--cf-backdrop-blur));
}

.new-chat-btn {
  height: 48px;
  padding: 0 16px;
  border-radius: 10px;
  background: var(--cf-gradient-primary);
  color: #fff;
  display: flex;
  align-items: center;
  gap: 10px;
  font-weight: 900;
  box-shadow: var(--cf-shadow-glow);

  span {
    flex: 1;
    text-align: left;
  }

  kbd {
    font: inherit;
    font-size: 12px;
    opacity: 0.9;
  }
}

.left-nav-card,
.history-card,
.upgrade-card {
  padding: 12px;
}

.left-link {
  width: 100%;
  height: 40px;
  padding: 0 12px;
  border-radius: 10px;
  color: var(--cf-text-secondary);
  display: flex;
  align-items: center;
  gap: 10px;
  font-weight: 800;

  &.active {
    color: var(--cf-primary);
    background: color-mix(in srgb, var(--cf-primary) 10%, transparent);
  }
}

.history-card {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
}

.history-group {
  padding: 0 0 14px;

  p {
    margin: 10px 8px 8px;
    color: var(--cf-text-muted);
    font-size: 13px;
  }

  button,
  a {
    width: 100%;
    min-height: 36px;
    padding: 0 10px;
    border-radius: 9px;
    color: var(--cf-text-secondary);
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 8px;
    font-size: 14px;
    text-align: left;
  }

  button.active {
    color: var(--cf-text-primary);
    background: color-mix(in srgb, var(--cf-primary) 9%, transparent);
    border-left: 2px solid var(--cf-primary);
  }

  span {
    min-width: 0;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  a {
    color: var(--cf-text-muted);
  }
}

.upgrade-card {
  min-height: 138px;
  position: relative;
  overflow: hidden;

  h3,
  p {
    margin: 0;
  }

  p {
    margin-top: 8px;
    color: var(--cf-text-muted);
    font-size: 13px;
  }

  button {
    height: 36px;
    margin-top: 18px;
    padding: 0 16px;
    border-radius: 9px;
    border: 1px solid color-mix(in srgb, var(--cf-primary) 42%, transparent);
    color: var(--cf-primary);
    font-weight: 900;
  }
}

.crown-visual {
  position: absolute;
  right: 22px;
  bottom: 16px;
  width: 66px;
  height: 52px;
  background: linear-gradient(135deg, #ffd166, #f59e0b);
  clip-path: polygon(0 84%, 10% 25%, 34% 58%, 50% 0, 66% 58%, 90% 25%, 100% 84%, 88% 100%, 12% 100%);
  opacity: 0.76;
}

.chat-main {
  min-width: 0;
  min-height: 0;
  height: 100%;
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.chat-title {
  min-height: 64px;
  flex: 0 0 auto;
  padding: 26px 0 0;

  h1 {
    margin: 0;
    font-size: 24px;
    line-height: 1.2;
  }
}

.chat-stream {
  flex: 1 1 auto;
  min-height: 0;
  max-height: none;
  overflow-y: auto;
  padding: 0 8px 8px;
  display: flex;
  flex-direction: column;
  gap: 26px;
}

.message-row {
  display: flex;
  align-items: flex-start;
  gap: 12px;

  &.user {
    justify-content: flex-end;
    align-items: center;
  }
}

.user-bubble {
  max-width: min(520px, 60%);
  padding: 14px 22px;
  border-radius: 12px;
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
  background: var(--cf-gradient-primary);
  margin-top: 16px;
}

.assistant-card {
  width: min(780px, 100%);
  padding: 22px 24px;

  header {
    display: flex;
    align-items: center;
    gap: 14px;
    margin-bottom: 14px;

    strong {
      font-size: 16px;
    }

    span {
      color: var(--cf-text-muted);
      font-size: 13px;
    }
  }

  pre {
    margin: 0;
    color: var(--cf-text-secondary);
    font-family: inherit;
    font-size: 15px;
    line-height: 1.9;
    white-space: pre-wrap;
  }

  footer {
    margin-top: 18px;
    display: flex;
    flex-wrap: wrap;
    gap: 8px;

    button {
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
  }
}

.chat-loading {
  display: flex;
  align-items: center;
  gap: 10px;
  color: var(--cf-text-muted);
  padding-left: 54px;

  span {
    width: 10px;
    height: 10px;
    border-radius: 50%;
    background: var(--cf-primary);
    animation: pulse 1.2s infinite;
  }

  p {
    margin: 0;
  }
}

.quick-row {
  flex: 0 0 auto;
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr)) 44px;
  gap: 16px;

  button {
    min-height: 42px;
    padding: 0 18px;
    border-radius: 999px;
    background: var(--cf-bg-glass);
    border: 1px solid var(--cf-border);
    color: var(--cf-text-secondary);
    box-shadow: 0 10px 28px rgba(15, 23, 42, 0.05);
  }
}

.refresh-suggestions {
  display: grid;
  place-items: center;
}

.input-panel {
  min-height: 108px;
  flex: 0 0 auto;
  padding: 14px;

  textarea {
    width: 100%;
    min-height: 42px;
    max-height: 140px;
    border: 0;
    outline: 0;
    resize: vertical;
    background: transparent;
    color: var(--cf-text-primary);
    line-height: 1.6;
  }
}

.input-tools,
.input-tools > div,
.send-cluster {
  display: flex;
  align-items: center;
}

.input-tools {
  justify-content: space-between;

  button {
    width: 36px;
    height: 36px;
    border-radius: 10px;
    color: var(--cf-text-muted);
    display: grid;
    place-items: center;
  }
}

.send-cluster {
  gap: 8px;
}

.model-chip {
  width: auto !important;
  padding: 0 12px !important;
  background: var(--cf-bg-soft) !important;
  color: var(--cf-text-primary) !important;
  font-weight: 800;
  display: inline-flex !important;
  gap: 6px;

  .chevron {
    width: 14px;
    transform: rotate(90deg);
  }
}

.send-btn {
  color: #fff !important;
  background: var(--cf-gradient-primary) !important;
  box-shadow: var(--cf-shadow-glow);

  &:disabled {
    opacity: 0.48;
    cursor: not-allowed;
  }
}

.ai-right {
  padding-right: 10px;
}

.right-card {
  padding: 22px;

  h2 {
    margin: 0;
    font-size: 18px;
  }
}

.panel-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;

  button {
    color: var(--cf-text-muted);
    display: inline-flex;
    align-items: center;
    gap: 4px;
    font-size: 13px;
    font-weight: 800;
  }
}

.model-row {
  width: 100%;
  min-height: 72px;
  padding: 12px;
  border-radius: 12px;
  display: grid;
  grid-template-columns: 34px minmax(0, 1fr) auto;
  gap: 12px;
  align-items: center;
  text-align: left;

  &.active {
    border: 1px solid var(--cf-primary);
    background: color-mix(in srgb, var(--cf-primary) 6%, var(--cf-bg-card));
  }

  strong,
  p {
    margin: 0;
  }

  p {
    margin-top: 4px;
    color: var(--cf-text-muted);
    font-size: 12px;
  }

  em {
    padding: 4px 7px;
    border-radius: 8px;
    background: var(--cf-primary-soft);
    color: var(--cf-primary);
    font-size: 12px;
    font-style: normal;
    font-weight: 900;
  }
}

.model-mark {
  width: 28px;
  height: 28px;
  border-radius: 50%;
  display: grid;
  place-items: center;
  color: var(--model-color);
  background: color-mix(in srgb, var(--model-color) 12%, transparent);

  svg {
    width: 18px;
  }
}

.ability-list {
  margin-top: 18px;
  display: flex;
  flex-direction: column;
  gap: 13px;
}

.ability-row {
  width: 100%;
  min-height: 24px;
  padding: 0;
  display: flex;
  align-items: center;
  justify-content: space-between;
  cursor: pointer;

  .ability-label {
    color: var(--cf-text-secondary);
    display: inline-flex;
    align-items: center;
    gap: 10px;
    font-weight: 750;
  }

  .ability-toggle {
    width: 30px;
    height: 18px;
    border-radius: 999px;
    background: #d8dee7;
    position: relative;

    &::after {
      content: '';
      position: absolute;
      top: 3px;
      left: 3px;
      width: 12px;
      height: 12px;
      border-radius: 50%;
      background: #fff;
      transition: transform 0.2s ease;
    }

    &.on {
      background: var(--cf-primary);

      &::after {
        transform: translateX(12px);
      }
    }
  }
}

.ability-icon {
  width: 20px;
  height: 20px;
  display: inline-grid;
  place-items: center;
  color: #64748b;
  flex: 0 0 auto;

  .n-icon {
    display: grid;
  }
}

.stats-card {
  min-height: 280px;
  overflow: hidden;
}

.stats-grid {
  margin-top: 24px;
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  text-align: center;

  strong,
  span {
    display: block;
  }

  strong {
    color: var(--cf-text-primary);
    font-size: 22px;
    line-height: 1;
  }

  span {
    margin-top: 8px;
    color: var(--cf-text-muted);
    font-size: 12px;
  }
}

.stats-chart {
  width: 100%;
  height: 164px;
  margin-top: 20px;
  overflow: visible;
}

.chart-grid line {
  stroke: rgba(100, 116, 139, 0.14);
  stroke-width: 1;
}

.chart-axis-labels,
.chart-date-labels {
  fill: #64748b;
  font-size: 12px;
}

.chart-date-labels {
  text-anchor: middle;
}

.chart-area {
  fill: url(#statsFill);
}

.chart-line {
  fill: none;
  stroke: var(--cf-primary);
  stroke-linecap: round;
  stroke-linejoin: round;
  stroke-width: 3;
}

.chart-points {
  fill: var(--cf-primary);
  stroke: #ffffff;
  stroke-width: 2;
}

@keyframes pulse {
  0% {
    box-shadow: 0 0 0 0 rgba(52, 208, 188, 0.34);
  }
  70% {
    box-shadow: 0 0 0 9px rgba(52, 208, 188, 0);
  }
  100% {
    box-shadow: 0 0 0 0 rgba(52, 208, 188, 0);
  }
}

@media (max-width: 1180px) {
  .ai-assistant-page {
    grid-template-columns: 240px minmax(0, 1fr);
  }

  .ai-right {
    grid-column: 1 / -1;
    position: static;
    display: grid;
    grid-template-columns: repeat(3, 1fr);
    padding-right: 0;
  }
}

@media (max-width: 840px) {
  .ai-assistant-page {
    height: auto;
    grid-template-columns: 1fr;
    overflow: visible;
  }

  .ai-left,
  .ai-right {
    position: static;
    max-height: none;
    padding: 0;
  }

  .quick-row,
  .ai-right {
    grid-template-columns: 1fr;
  }
}

html[data-theme='dark'] .user-bubble {
  background: color-mix(in srgb, var(--cf-primary) 15%, #050505);
}
html[data-theme='dark'] .ability-toggle {
  background: var(--cf-border) !important;
}
html[data-theme='dark'] .chart-points {
  stroke: var(--cf-bg-card);
}
</style>
