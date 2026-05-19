<script setup lang="ts">
import { ref } from 'vue';
import { useRouter } from 'vue-router';
import { NIcon, NInput, useMessage } from 'naive-ui';
import { ArrowForwardOutline, IdCardOutline, LockClosedOutline, MailOutline, PersonOutline, SchoolOutline } from '@vicons/ionicons5';
import { register } from '@/api/auth';

const router = useRouter();
const message = useMessage();

const email = ref('');
const password = ref('');
const confirmPassword = ref('');
const studentNo = ref('');
const nickname = ref('');
const loading = ref(false);

const benefits = [
  '在广场记录课程心得与校园见闻',
  '加入学习圈，参与专题讨论与资料共享',
  '开启打卡、积分、成就和 AI 学习辅助能力',
];

async function handleRegister() {
  if (!email.value || !password.value || !nickname.value) {
    message.warning('请填写必填项');
    return;
  }
  if (password.value !== confirmPassword.value) {
    message.warning('两次密码不一致');
    return;
  }
  if (password.value.length < 6) {
    message.warning('密码至少 6 位');
    return;
  }

  loading.value = true;
  try {
    await register({
      email: email.value.trim(),
      password: password.value,
      studentNo: studentNo.value.trim() || undefined,
      nickname: nickname.value.trim(),
    });
    message.success('注册成功，请登录');
    router.push('/login');
  } catch (err: unknown) {
    message.error(err instanceof Error ? err.message : '注册失败');
  } finally {
    loading.value = false;
  }
}
</script>

<template>
  <div class="auth-page register-page">
    <div class="auth-shell">
      <section class="auth-panel cf-surface">
        <div class="panel-head">
          <h2>创建账号</h2>
          <p>填写基础信息，加入你的校园学习社区。</p>
        </div>

        <div class="form-grid two-col">
          <div class="form-block">
            <label>昵称</label>
            <n-input
              v-model:value="nickname"
              size="large"
              placeholder="例如：数据结构补完计划"
            >
              <template #prefix>
                <n-icon><PersonOutline /></n-icon>
              </template>
            </n-input>
          </div>
          <div class="form-block">
            <label>学号</label>
            <n-input
              v-model:value="studentNo"
              size="large"
              placeholder="选填"
            >
              <template #prefix>
                <n-icon><IdCardOutline /></n-icon>
              </template>
            </n-input>
          </div>
        </div>

        <div class="form-grid">
          <div class="form-block">
            <label>邮箱</label>
            <n-input
              v-model:value="email"
              size="large"
              placeholder="name@college.edu"
            >
              <template #prefix>
                <n-icon><MailOutline /></n-icon>
              </template>
            </n-input>
          </div>

          <div class="form-block">
            <label>密码</label>
            <n-input
              v-model:value="password"
              type="password"
              size="large"
              placeholder="至少 6 位"
              show-password-on="click"
            >
              <template #prefix>
                <n-icon><LockClosedOutline /></n-icon>
              </template>
            </n-input>
          </div>

          <div class="form-block">
            <label>确认密码</label>
            <n-input
              v-model:value="confirmPassword"
              type="password"
              size="large"
              placeholder="再次输入密码"
              show-password-on="click"
            >
              <template #prefix>
                <n-icon><LockClosedOutline /></n-icon>
              </template>
            </n-input>
          </div>
        </div>

        <button
          class="cf-primary-btn submit-btn"
          :disabled="loading"
          @click="handleRegister"
        >
          <n-icon size="16">
            <ArrowForwardOutline />
          </n-icon>
          {{ loading ? '提交中...' : '完成注册' }}
        </button>

        <div class="footer-note">
          已经有账号？
          <button
            class="text-link strong"
            @click="router.push('/login')"
          >
            去登录
          </button>
        </div>
      </section>

      <section class="register-visual cf-card">
        <div class="register-visual-inner">
          <span class="cf-pill">Join Campus</span>
          <h1>把你的学习轨迹，放进更好的社区里</h1>
          <p>
            通过统一而克制的页面风格，把注册流程也纳入同一套校园产品体验中，让首次进入平台更清晰、更可信。
          </p>

          <div class="benefit-list">
            <div
              v-for="item in benefits"
              :key="item"
              class="benefit-item"
            >
              <div class="benefit-icon">
                <n-icon size="16"><SchoolOutline /></n-icon>
              </div>
              <span>{{ item }}</span>
            </div>
          </div>

          <div class="preview-card">
            <h3>注册后你可以立即开始</h3>
            <div class="preview-grid">
              <div>
                <strong>浏览广场</strong>
                <span>查看校园热门讨论与观点</span>
              </div>
              <div>
                <strong>加入学习圈</strong>
                <span>进入课程、竞赛、科研学习圈</span>
              </div>
              <div>
                <strong>使用 AI</strong>
                <span>自动提炼帖子核心要点与结构</span>
              </div>
              <div>
                <strong>积累成长</strong>
                <span>通过打卡与积分形成长期正反馈</span>
              </div>
            </div>
          </div>
        </div>
      </section>
    </div>
  </div>
</template>

<style scoped lang="scss">
.auth-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 32px;
}

.auth-shell {
  width: min(1120px, 100%);
  display: grid;
  grid-template-columns: 460px minmax(0, 1fr);
  gap: 22px;
}

.auth-panel,
.register-visual {
  min-height: 720px;
}

.auth-panel {
  padding: 32px;
  display: flex;
  flex-direction: column;
  justify-content: center;
}

.panel-head {
  margin-bottom: 24px;

  h2 {
    margin: 0 0 8px;
    font-family: var(--cf-font-heading);
    font-size: 32px;
  }

  p {
    margin: 0;
    color: var(--cf-text-secondary);
  }
}

.form-grid {
  display: grid;
  grid-template-columns: 1fr;
  gap: 18px;

  &.two-col {
    grid-template-columns: repeat(2, minmax(0, 1fr));
    margin-bottom: 18px;
  }
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

.submit-btn {
  width: 100%;
  margin-top: 24px;
  min-height: 48px;
}

.footer-note {
  margin-top: 18px;
  text-align: center;
  color: var(--cf-text-secondary);
  font-size: 14px;
}

.text-link {
  border: none;
  background: transparent;
  color: var(--cf-primary);
  cursor: pointer;
  padding: 0;
  font-size: 14px;

  &.strong {
    font-weight: 700;
  }
}

.register-visual {
  padding: 28px;
  background: linear-gradient(180deg, var(--cf-bg-glass-soft), var(--cf-bg-glass));
  box-shadow: var(--cf-shadow-float);
}

.register-visual-inner {
  height: 100%;
  border-radius: 24px;
  padding: 32px;
  display: flex;
  flex-direction: column;
  background: linear-gradient(180deg, var(--cf-bg-glass-soft), color-mix(in srgb, var(--cf-bg-glass) 68%, transparent));
  border: 1px solid var(--cf-border-glass);
  box-shadow: inset 0 1px 0 var(--cf-surface-highlight), var(--cf-shadow-card);
  backdrop-filter: blur(16px) saturate(132%);
  -webkit-backdrop-filter: blur(16px) saturate(132%);

  h1 {
    margin: 18px 0 12px;
    font-family: var(--cf-font-heading);
    font-size: clamp(40px, 4vw, 56px);
    line-height: 1.05;
    letter-spacing: 0;
  }

  p {
    margin: 0;
    max-width: 560px;
    color: var(--cf-text-secondary);
    line-height: 1.85;
    font-size: 17px;
  }
}

.benefit-list {
  display: flex;
  flex-direction: column;
  gap: 14px;
  margin: 28px 0 24px;
}

.benefit-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 14px 16px;
  border-radius: 18px;
  background: var(--cf-bg-glass-soft);
  border: 1px solid var(--cf-border-glass);
  box-shadow: inset 0 1px 0 var(--cf-surface-highlight);
  backdrop-filter: blur(14px) saturate(128%);
  -webkit-backdrop-filter: blur(14px) saturate(128%);
  color: var(--cf-text-secondary);
}

.benefit-icon {
  width: 36px;
  height: 36px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--cf-primary-soft);
  color: var(--cf-primary);
}

.preview-card {
  margin-top: auto;
  padding: 22px;
  border-radius: 22px;
  background: var(--cf-bg-glass-soft);
  border: 1px solid var(--cf-border-glass);
  box-shadow: inset 0 1px 0 var(--cf-surface-highlight), var(--cf-shadow-soft);
  backdrop-filter: blur(14px) saturate(126%);
  -webkit-backdrop-filter: blur(14px) saturate(126%);

  h3 {
    margin: 0 0 16px;
    font-family: var(--cf-font-heading);
    font-size: 24px;
  }
}

.preview-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;

  div {
    padding: 14px;
    border-radius: 16px;
    background: var(--cf-bg-readable);
    border: 1px solid var(--cf-border);
  }

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

:deep(.n-input) {
  --n-border-radius: 14px !important;
}

@media (max-width: 960px) {
  .auth-page {
    padding: 16px;
  }

  .auth-shell {
    grid-template-columns: 1fr;
  }

  .auth-panel,
  .register-visual {
    min-height: auto;
  }
}

@media (max-width: 640px) {
  .auth-panel,
  .register-visual {
    padding: 20px;
  }

  .register-visual-inner {
    padding: 22px;
  }

  .form-grid.two-col,
  .preview-grid {
    grid-template-columns: 1fr;
  }
}
</style>
