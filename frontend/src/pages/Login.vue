<script setup lang="ts">
import { computed, onBeforeUnmount, ref } from 'vue';
import { useRouter } from 'vue-router';
import { NIcon, NInput, NTabPane, NTabs, useMessage } from 'naive-ui';
import { ArrowForwardOutline, LockClosedOutline, MailOutline, ShieldCheckmarkOutline, SparklesOutline } from '@vicons/ionicons5';
import { login, loginWithEmailCode, sendEmailCode } from '@/api/auth';
import { useAuthStore } from '@/stores/auth';

const router = useRouter();
const message = useMessage();
const authStore = useAuthStore();

const email = ref('');
const password = ref('');
const emailCode = ref('');
const loginMode = ref<'password' | 'code'>('password');
const loading = ref(false);
const codeLoading = ref(false);
const codeCountdown = ref(0);
let codeTimer: number | undefined;

const featureList = [
  '加入多校学习圈，追踪热门课程讨论',
  '查看广场动态、资源分享与打卡挑战',
  '使用 AI 助手快速总结帖子与学习内容',
];

const canSubmit = computed(() => {
  if (!email.value.trim()) return false;
  return loginMode.value === 'password' ? password.value.trim() : emailCode.value.trim();
});

function startCodeCountdown() {
  codeCountdown.value = 60;
  window.clearInterval(codeTimer);
  codeTimer = window.setInterval(() => {
    codeCountdown.value -= 1;
    if (codeCountdown.value <= 0) {
      window.clearInterval(codeTimer);
      codeTimer = undefined;
    }
  }, 1000);
}

async function handleSendCode() {
  if (!email.value.trim()) {
    message.warning('请先填写邮箱');
    return;
  }
  codeLoading.value = true;
  try {
    await sendEmailCode(email.value.trim(), 'LOGIN');
    message.success('验证码已发送，请查收邮箱');
    startCodeCountdown();
  } catch (err: unknown) {
    message.error(err instanceof Error ? err.message : '验证码发送失败');
  } finally {
    codeLoading.value = false;
  }
}

async function handleLogin() {
  if (!canSubmit.value) {
    message.warning(loginMode.value === 'password' ? '请填写邮箱和密码' : '请填写邮箱和验证码');
    return;
  }

  loading.value = true;
  try {
    const res = loginMode.value === 'password'
      ? await login({ email: email.value.trim(), password: password.value })
      : await loginWithEmailCode({ email: email.value.trim(), emailCode: emailCode.value.trim() });
    authStore.setToken(res.token);
    authStore.setUser(res.user);
    if (res.tenantId && res.tenantCode) {
      authStore.setTenant(res.tenantId, res.tenantCode);
    }
    message.success('登录成功');
    router.push('/square');
  } catch {
    message.error(loginMode.value === 'password' ? '邮箱或密码错误' : '邮箱或验证码错误');
  } finally {
    loading.value = false;
  }
}

onBeforeUnmount(() => {
  window.clearInterval(codeTimer);
});
</script>

<template>
  <div class="auth-page">
    <div class="auth-shell">
      <section class="auth-visual cf-card">
        <div class="auth-visual-inner">
          <span class="cf-pill">Campus Access</span>
          <h1>欢迎回到 CampusForum</h1>
          <p>
            进入一个更安静、更高效的校园学习社区，在熟悉的空间里继续追踪讨论、管理资源、沉淀成长记录。
          </p>

          <div class="feature-list">
            <div
              v-for="item in featureList"
              :key="item"
              class="feature-item"
            >
              <div class="feature-icon">
                <n-icon size="16">
                  <SparklesOutline />
                </n-icon>
              </div>
              <span>{{ item }}</span>
            </div>
          </div>

          <div class="visual-metrics">
            <div>
              <strong>56k+</strong>
              <span>活跃学生</span>
            </div>
            <div>
              <strong>12k+</strong>
              <span>学习圈</span>
            </div>
            <div>
              <strong>8.9k</strong>
              <span>今日打卡</span>
            </div>
          </div>
        </div>
      </section>

      <section class="auth-panel cf-surface">
        <div class="panel-head">
          <h2>登录账号</h2>
          <p>使用你的校园账号进入社区。</p>
        </div>

        <n-tabs
          v-model:value="loginMode"
          type="segment"
          animated
          class="login-tabs"
        >
          <n-tab-pane
            name="password"
            tab="密码登录"
          />
          <n-tab-pane
            name="code"
            tab="验证码登录"
          />
        </n-tabs>

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

        <div
          v-if="loginMode === 'password'"
          class="form-block"
        >
          <div class="label-row">
            <label>密码</label>
            <button
              class="text-link"
              @click="router.push('/forgot-password')"
            >
              忘记密码？
            </button>
          </div>
          <n-input
            v-model:value="password"
            type="password"
            size="large"
            placeholder="请输入密码"
            show-password-on="click"
          >
            <template #prefix>
              <n-icon><LockClosedOutline /></n-icon>
            </template>
          </n-input>
        </div>

        <div
          v-else
          class="form-block"
        >
          <label>邮箱验证码</label>
          <div class="code-input-row">
            <n-input
              v-model:value="emailCode"
              size="large"
              placeholder="输入 6 位验证码"
            >
              <template #prefix>
                <n-icon><ShieldCheckmarkOutline /></n-icon>
              </template>
            </n-input>
            <button
              class="cf-secondary-btn code-btn"
              :disabled="codeLoading || codeCountdown > 0"
              @click="handleSendCode"
            >
              {{ codeCountdown > 0 ? `${codeCountdown}s` : codeLoading ? '发送中...' : '获取验证码' }}
            </button>
          </div>
        </div>

        <button
          class="cf-primary-btn submit-btn"
          :disabled="loading"
          @click="handleLogin"
        >
          <n-icon size="16">
            <ArrowForwardOutline />
          </n-icon>
          {{ loading ? '登录中...' : '进入社区' }}
        </button>

        <div class="footer-note">
          还没有账号？
          <button
            class="text-link strong"
            @click="router.push('/register')"
          >
            立即注册
          </button>
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
  grid-template-columns: minmax(0, 1fr) 420px;
  gap: 22px;
}

.auth-visual,
.auth-panel {
  min-height: 680px;
}

.auth-visual {
  padding: 28px;
  background: var(--cf-bg-glass);
  box-shadow: var(--cf-shadow-float);
}

.auth-visual-inner {
  height: 100%;
  border-radius: 24px;
  padding: 32px;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  background: var(--cf-bg-glass);
  border: 1px solid var(--cf-border-glass);
  box-shadow: inset 0 1px 0 var(--cf-surface-highlight), var(--cf-shadow-card);
  backdrop-filter: none;
  -webkit-backdrop-filter: none;

  h1 {
    margin: 18px 0 12px;
    font-family: var(--cf-font-heading);
    font-size: clamp(40px, 4vw, 58px);
    line-height: 1.04;
    letter-spacing: 0;
  }

  p {
    max-width: 560px;
    margin: 0;
    color: var(--cf-text-secondary);
    line-height: 1.85;
    font-size: 17px;
  }
}

.feature-list {
  display: flex;
  flex-direction: column;
  gap: 14px;
  margin: 28px 0 auto;
}

.feature-item {
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

.feature-icon {
  width: 36px;
  height: 36px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--cf-primary-soft);
  color: var(--cf-primary);
}

.visual-metrics {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
  margin-top: 26px;

  div {
    padding: 18px;
    border-radius: 18px;
    background: var(--cf-bg-glass-soft);
    border: 1px solid var(--cf-border-glass);
    box-shadow: inset 0 1px 0 var(--cf-surface-highlight);
    backdrop-filter: blur(14px) saturate(128%);
    -webkit-backdrop-filter: blur(14px) saturate(128%);
  }

  strong {
    display: block;
    font-family: var(--cf-font-heading);
    font-size: 24px;
    margin-bottom: 6px;
  }

  span {
    color: var(--cf-text-muted);
    font-size: 13px;
  }
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

.login-tabs {
  margin-bottom: 18px;
}

.form-block {
  display: flex;
  flex-direction: column;
  gap: 10px;

  & + .form-block {
    margin-top: 18px;
  }

  label {
    font-size: 14px;
    font-weight: 700;
    color: var(--cf-text-primary);
  }
}

.label-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
}

.code-input-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 118px;
  gap: 10px;
  align-items: center;
}

.code-btn {
  min-height: 40px;
  padding: 0 12px;
  white-space: nowrap;
}

.submit-btn {
  width: 100%;
  margin-top: 24px;
  min-height: 48px;
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

.footer-note {
  margin-top: 18px;
  text-align: center;
  color: var(--cf-text-secondary);
  font-size: 14px;
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

  .auth-visual,
  .auth-panel {
    min-height: auto;
  }
}

@media (max-width: 640px) {
  .auth-panel,
  .auth-visual {
    padding: 20px;
  }

  .auth-visual-inner {
    padding: 22px;
  }

  .visual-metrics {
    grid-template-columns: 1fr;
  }

  .code-input-row {
    grid-template-columns: 1fr;
  }
}
</style>
