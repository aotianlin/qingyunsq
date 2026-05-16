<script setup lang="ts">
import { ref } from 'vue';
import { 
  DocumentTextOutline,
  ShieldCheckmarkOutline,
  AlertCircleOutline,
  CheckmarkCircleOutline,
  ChatbubbleEllipsesOutline,
  SettingsOutline,
  CopyOutline,
  RefreshOutline
} from '@vicons/ionicons5';
import aiRobotImg from '@/assets/images/ai_robot.png';

const menus = [
  { label: '智能摘要', icon: DocumentTextOutline, active: true },
  { label: '内容检测', icon: ShieldCheckmarkOutline, active: false },
  { label: '敏感词检测', icon: AlertCircleOutline, active: false },
  { label: '打卡相关性检查', icon: CheckmarkCircleOutline, active: false },
  { label: 'AI 问答', icon: ChatbubbleEllipsesOutline, active: false },
  { label: '配置管理', icon: SettingsOutline, active: false },
];

const bottomCards = [
  { title: '内容检测', desc: '检测内容是否包含违规、色情等信息', icon: ShieldCheckmarkOutline, color: '#38bdf8' },
  { title: '敏感词检测', desc: '检测文本中的敏感词汇', icon: AlertCircleOutline, color: '#ef4444' },
  { title: '打卡相关性检查', desc: '检查打卡内容与目标的关联性', icon: CheckmarkCircleOutline, color: '#f59e0b' },
  { title: 'AI 问答', desc: '基于知识库的智能问答', icon: ChatbubbleEllipsesOutline, color: '#c084fc' },
];

const inputText = ref('');
</script>

<template>
  <div class="ai-layout">
    <header class="top-header">
      <h2>AI 助手</h2>
    </header>

    <div class="main-container">
      <aside class="sidebar glass-card">
        <div v-for="m in menus" :key="m.label" 
             class="menu-item" :class="{ active: m.active }">
          {{ m.label }}
        </div>
      </aside>

      <main class="content-area">
        <div class="content-header">
          <h3>智能摘要</h3>
          <p>使用 AI 自动生成帖子或文章的摘要，提炼核心内容</p>
        </div>

        <div class="interaction-area">
          <div class="input-section glass-card">
            <textarea v-model="inputText" placeholder="请输入需要摘要的内容..."></textarea>
            <div class="input-footer">
              <span class="count">{{ inputText.length }} / 5000</span>
              <button class="neon-btn">生成摘要</button>
            </div>
          </div>
          <div class="robot-illustration">
            <img :src="aiRobotImg" alt="AI Robot" class="floating-robot" />
          </div>
        </div>

        <div class="examples-section">
          <h4>示例场景</h4>
          <div class="example-card glass-card">
            <div class="ex-content">
              <h5>原文标题：操作系统进程调度算法详解</h5>
              <div class="summary-box">
                <span class="label">摘要：</span>
                <p>文章详细分析了计算机系统中常见的进程调度算法，包括 FCFS、SJF、优先级调度、轮转调度等，并对比了各自的优缺点及适用场景。通过具体实例分析了调度算法在操作系统中的作用与意义。</p>
              </div>
              <div class="tags">
                <span class="tag">操作系统</span>
                <span class="tag">调度算法</span>
                <span class="tag">内核</span>
              </div>
            </div>
            <div class="ex-actions">
              <span class="action"><n-icon><CopyOutline/></n-icon> 复制</span>
              <span class="action"><n-icon><RefreshOutline/></n-icon> 重新生成</span>
            </div>
          </div>
        </div>

        <div class="bottom-cards">
          <div v-for="card in bottomCards" :key="card.title" class="glass-card feature-card">
            <div class="icon-wrap" :style="{ backgroundColor: card.color + '20', color: card.color }">
              <n-icon size="24"><component :is="card.icon" /></n-icon>
            </div>
            <div class="info">
              <h5>{{ card.title }}</h5>
              <p>{{ card.desc }}</p>
            </div>
          </div>
        </div>
      </main>
    </div>
  </div>
</template>

<style scoped lang="scss">
.ai-layout {
  height: 100vh;
  display: flex;
  flex-direction: column;
  background: var(--cf-bg-base);
  color: var(--cf-text-primary);
  overflow: hidden;
}

.top-header {
  height: 60px;
  padding: 0 32px;
  display: flex;
  align-items: center;
  border-bottom: 1px solid var(--cf-border);
  background: rgba(13, 17, 23, 0.8);
  h2 { margin: 0; font-size: 18px; font-weight: 600; }
}

.main-container {
  flex: 1;
  display: flex;
  padding: 32px;
  gap: 32px;
  max-width: 1400px;
  margin: 0 auto;
  width: 100%;
}

.sidebar {
  width: 200px;
  padding: 16px;
  display: flex;
  flex-direction: column;
  gap: 8px;
  height: fit-content;

  .menu-item {
    padding: 12px 16px;
    border-radius: 8px;
    cursor: pointer;
    color: var(--cf-text-secondary);
    font-size: 14px;
    transition: all 0.2s;

    &:hover { background: rgba(255,255,255,0.05); color: white; }
    &.active {
      background: rgba(99,102,241,0.15);
      color: var(--cf-primary);
      border-left: 3px solid var(--cf-primary);
      border-radius: 4px 8px 8px 4px;
    }
  }
}

.content-area {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 32px;
  overflow-y: auto;
  padding-right: 16px;

  .content-header {
    h3 { margin: 0 0 8px; font-size: 24px; color: white; }
    p { margin: 0; color: var(--cf-text-secondary); font-size: 14px; }
  }

  .interaction-area {
    display: flex;
    gap: 32px;
    align-items: stretch;

    .input-section {
      flex: 1;
      display: flex;
      flex-direction: column;
      padding: 24px;

      textarea {
        flex: 1;
        min-height: 150px;
        background: transparent;
        border: none;
        color: white;
        font-size: 15px;
        resize: none;
        outline: none;
        margin-bottom: 16px;
        &::placeholder { color: var(--cf-text-muted); }
      }

      .input-footer {
        display: flex; justify-content: space-between; align-items: center;
        .count { color: var(--cf-text-muted); font-size: 13px; }
      }
    }

    .robot-illustration {
      width: 250px;
      display: flex; align-items: center; justify-content: center;
      .floating-robot {
        width: 100%;
        animation: float 4s ease-in-out infinite;
        filter: drop-shadow(0 0 20px rgba(56,189,248,0.3));
      }
    }
  }

  .examples-section {
    h4 { margin: 0 0 16px; font-size: 16px; color: white; }
    
    .example-card {
      padding: 20px 24px;
      display: flex; flex-direction: column; gap: 16px;

      .ex-content {
        h5 { margin: 0 0 12px; font-size: 15px; color: white; }
        .summary-box {
          display: flex; gap: 8px;
          .label { color: var(--cf-primary); font-weight: bold; flex-shrink: 0; }
          p { margin: 0; color: var(--cf-text-secondary); font-size: 14px; line-height: 1.6; }
        }
        .tags {
          display: flex; gap: 12px; margin-top: 16px;
          .tag { font-size: 12px; padding: 2px 10px; border-radius: 12px; border: 1px solid var(--cf-primary); color: var(--cf-primary); background: rgba(99,102,241,0.1); }
        }
      }

      .ex-actions {
        display: flex; justify-content: flex-end; gap: 24px;
        .action { display: flex; align-items: center; gap: 6px; color: var(--cf-text-secondary); font-size: 13px; cursor: pointer; &:hover { color: white; } }
      }
    }
  }

  .bottom-cards {
    display: grid;
    grid-template-columns: repeat(4, 1fr);
    gap: 20px;

    .feature-card {
      padding: 20px;
      display: flex;
      flex-direction: column;
      gap: 16px;

      .icon-wrap { width: 40px; height: 40px; border-radius: 10px; display: flex; align-items: center; justify-content: center; }
      .info {
        h5 { margin: 0 0 8px; font-size: 15px; color: white; }
        p { margin: 0; color: var(--cf-text-secondary); font-size: 12px; line-height: 1.5; display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden; }
      }
    }
  }
}

@keyframes float {
  0% { transform: translateY(0px); }
  50% { transform: translateY(-15px); }
  100% { transform: translateY(0px); }
}
</style>
