<script setup lang="ts">
import { computed, onBeforeUnmount, ref } from 'vue';
import { useRouter } from 'vue-router';
import { NIcon, NInput, NTabPane, NTabs, useMessage } from 'naive-ui';
import { ArrowForwardOutline, LockClosedOutline, MailOutline, ShieldCheckmarkOutline, SparklesOutline } from '@vicons/ionicons5';
import { checkEmailExists, login, loginWithEmailCode, sendEmailCode } from '@/api/auth';
import { useAuthStore } from '@/stores/auth';
import { validateEmail, validatePassword } from '@/utils/authValidation';

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
const fieldState = ref({
  email: { active: false, touched: false, error: '', shaking: false },
  password: { active: false, touched: false, error: '', shaking: false },
});

const featureList = [
  '加入多校学习圈，追踪热门课程讨论',
  '查看广场动态、资源分享与打卡挑战',
  '使用 AI 助手快速总结帖子与学习内容',
];

const canSubmit = computed(() => {
  if (!email.value.trim() || fieldState.value.email.error) return false;
  if (loginMode.value === 'password') {
    return password.value.trim() && !fieldState.value.password.error;
  }
  return emailCode.value.trim();
});

function runFieldValidation(field: 'email' | 'password') {
  fieldState.value[field].error = field === 'email'
    ? validateEmail(email.value)
    : validatePassword(password.value);
}

function focusField(field: 'email' | 'password') {
  fieldState.value[field].active = true;
  runFieldValidation(field);
}

function blurField(field: 'email' | 'password') {
  const state = fieldState.value[field];
  state.active = false;
  state.touched = true;
  runFieldValidation(field);
  if (state.error) {
    state.shaking = false;
    window.setTimeout(() => {
      state.shaking = true;
      window.setTimeout(() => {
        state.shaking = false;
      }, 520);
    }, 0);
  }
}

function validateForm() {
  // 验证码登录模式只需要校验邮箱
  const fields = loginMode.value === 'password' ? (['email', 'password'] as const) : (['email'] as const);
  fields.forEach((field) => {
    fieldState.value[field].touched = true;
    runFieldValidation(field);
  });
  return fields.every((field) => !fieldState.value[field].error);
}

function resetLoginForm() {
  email.value = '';
  password.value = '';
  emailCode.value = '';
  fieldState.value = {
    email: { active: false, touched: false, error: '', shaking: false },
    password: { active: false, touched: false, error: '', shaking: false },
  };
}

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
  // 发送验证码前确保邮箱格式正确
  fieldState.value.email.touched = true;
  runFieldValidation('email');
  if (fieldState.value.email.error) {
    message.warning('请先填写有效的邮箱');
    return;
  }
  const trimmedEmail = email.value.trim();
  codeLoading.value = true;
  try {
    // 防呆校验：未注册邮箱不发验证码，提示后跳转至注册页并预填邮箱
    const exists = await checkEmailExists(trimmedEmail);
    if (!exists) {
      message.warning('该邮箱尚未注册，将跳转到注册页');
      router.push({ path: '/register', query: { email: trimmedEmail } });
      return;
    }
    await sendEmailCode(trimmedEmail, 'LOGIN');
    message.success('验证码已发送，请查收邮箱');
    startCodeCountdown();
  } catch (err: unknown) {
    message.error(err instanceof Error ? err.message : '验证码发送失败');
  } finally {
    codeLoading.value = false;
  }
}

async function handleLogin() {
  if (!validateForm()) {
    message.warning(loginMode.value === 'password' ? '请按提示修正邮箱和密码' : '请填写邮箱和验证码');
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
    // 后端统一返回 INVALID_CREDENTIALS(40101)，前端不区分具体失败原因
    message.error(loginMode.value === 'password' ? '邮箱或密码错误' : '邮箱或验证码错误');
    resetLoginForm();
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

        <div
          class="form-block"
          :class="{ invalid: fieldState.email.touched && fieldState.email.error, shake: fieldState.email.shaking }"
        >
          <label>邮箱</label>
          <n-input
            v-model:value="email"
            size="large"
            placeholder="name@college.edu"
            :status="fieldState.email.touched && fieldState.email.error ? 'error' : undefined"
            @focus="focusField('email')"
            @blur="blurField('email')"
            @input="runFieldValidation('email')"
          >
            <template #prefix>
              <n-icon><MailOutline /></n-icon>
            </template>
          </n-input>
          <small
            v-if="fieldState.email.touched && fieldState.email.error"
            class="field-hint error"
          >
            {{ fieldState.email.error }}
          </small>
        </div>

        <div
          v-if="loginMode === 'password'"
          class="form-block"
          :class="{ invalid: fieldState.password.touched && fieldState.password.error, shake: fieldState.password.shaking }"
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
            :status="fieldState.password.touched && fieldState.password.error ? 'error' : undefined"
            @focus="focusField('password')"
            @blur="blurField('password')"
            @input="runFieldValidation('password')"
          >
            <template #prefix>
              <n-icon><LockClosedOutline /></n-icon>
            </template>
          </n-input>
          <small
            v-if="fieldState.password.touched && fieldState.password.error"
            class="field-hint error"
          >
            {{ fieldState.password.error }}
          </small>
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
          :disabled="loading || !canSubmit"
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

  &.invalid {
    :deep(.n-input) {
      --n-border: 1px solid var(--cf-danger) !important;
      --n-border-hover: 1px solid var(--cf-danger) !important;
      --n-border-focus: 1px solid var(--cf-danger) !important;
      --n-box-shadow-focus: 0 0 0 3px color-mix(in srgb, var(--cf-danger) 18%, transparent) !important;
    }
  }

  &.shake {
    animation: field-shake 0.48s ease;
  }
}

.field-hint {
  min-height: 18px;
  font-size: 12px;
  line-height: 1.5;

  &.error {
    color: var(--cf-danger);
  }
}

@keyframes field-shake {
  0%, 100% {
    transform: translateX(0);
  }
  20%, 60% {
    transform: translateX(-4px);
  }
  40%, 80% {
    transform: translateX(4px);
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
