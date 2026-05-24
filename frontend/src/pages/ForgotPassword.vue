<script setup lang="ts">
import { computed, ref } from 'vue';
import { useRouter } from 'vue-router';
import { NIcon, NInput, NStep, NSteps, useMessage } from 'naive-ui';
import { KeyOutline, LockClosedOutline, MailOutline, RefreshOutline } from '@vicons/ionicons5';
import { forgotPassword, resetPassword } from '@/api/auth';

const router = useRouter();
const message = useMessage();

const step = ref(1);
const email = ref('');
const emailCode = ref('');
const newPassword = ref('');
const confirmPassword = ref('');
const loading = ref(false);

const stepTitle = computed(() => (step.value === 1 ? '验证账号身份' : '设置新密码'));

function getErrorMessage(error: unknown): string {
  const err = error as { response?: { data?: { message?: string } } };
  return err.response?.data?.message || '重置失败';
}

async function handleSendCode() {
  if (!email.value.trim()) {
    message.warning('请输入邮箱');
    return;
  }
  loading.value = true;
  try {
    await forgotPassword(email.value.trim());
    message.success('验证码已发送，请查收邮箱');
    step.value = 2;
  } catch {
    message.error('验证码发送失败');
  } finally {
    loading.value = false;
  }
}

async function handleReset() {
  if (!emailCode.value.trim() || !newPassword.value || !confirmPassword.value) {
    message.warning('请填写验证码和新密码');
    return;
  }
  if (newPassword.value !== confirmPassword.value) {
    message.warning('两次密码不一致');
    return;
  }
  if (newPassword.value.length < 8 || newPassword.value.length > 64) {
    message.warning('密码长度需 8-64 位');
    return;
  }
  if (!/(?=.*[A-Za-z])(?=.*\d)/.test(newPassword.value)) {
    message.warning('密码必须同时包含字母和数字');
    return;
  }
  loading.value = true;
  try {
    // 重置密码：成功后由 auth.ts 内部 helper 自动 logout + 跳转 /login，
    // 这里仅保留 toast 提示，避免与 helper 内的 router.push 重复跳转
    await resetPassword(email.value.trim(), emailCode.value.trim(), newPassword.value);
    message.success('密码重置成功，请重新登录');
  } catch (error) {
    message.error(getErrorMessage(error));
  } finally {
    loading.value = false;
  }
}
</script>

<template>
  <div class="forgot-page">
    <div class="forgot-shell">
      <section class="forgot-visual cf-card">
        <div class="forgot-visual-inner">
          <span class="cf-pill">Account Recovery</span>
          <h1>安全地找回你的校园账号</h1>
          <p>
            采用两步流程完成密码重置：先验证注册邮箱，再使用邮箱验证码设置新密码，整个过程保持与新视觉系统一致。
          </p>

          <div class="visual-steps">
            <div class="visual-step active">
              <strong>01 验证邮箱</strong>
              <span>确认你当前使用的注册邮箱，系统会发送一次性验证码。</span>
            </div>
            <div class="visual-step" :class="{ active: step === 2 }">
              <strong>02 设置新密码</strong>
              <span>输入邮箱验证码与新密码，完成账号恢复。</span>
            </div>
          </div>
        </div>
      </section>

      <section class="forgot-panel cf-surface">
        <div class="panel-head">
          <h2>找回密码</h2>
          <p>{{ stepTitle }}</p>
        </div>

        <NSteps
          :current="step"
          class="stepper"
        >
          <NStep title="验证身份" />
          <NStep title="重置密码" />
        </NSteps>

        <div v-if="step === 1" class="form-area">
          <div class="form-block">
            <label>注册邮箱</label>
            <n-input
              v-model:value="email"
              size="large"
              placeholder="请输入注册时使用的邮箱"
            >
              <template #prefix>
                <n-icon><MailOutline /></n-icon>
              </template>
            </n-input>
          </div>

          <button
            class="cf-primary-btn submit-btn"
            :disabled="loading"
            @click="handleSendCode"
          >
            <n-icon size="16"><RefreshOutline /></n-icon>
            {{ loading ? '提交中...' : '获取验证码' }}
          </button>
        </div>

        <div v-else class="form-area">
          <div class="form-block">
            <label>邮箱验证码</label>
            <n-input
              v-model:value="emailCode"
              size="large"
              placeholder="请输入邮箱验证码"
            >
              <template #prefix>
                <n-icon><KeyOutline /></n-icon>
              </template>
            </n-input>
          </div>

          <div class="form-block">
            <label>新密码</label>
            <n-input
              v-model:value="newPassword"
              type="password"
              size="large"
              placeholder="8-64 位，需包含字母和数字"
              show-password-on="click"
              maxlength="64"
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
              placeholder="请再次输入新密码"
              show-password-on="click"
              maxlength="64"
            >
              <template #prefix>
                <n-icon><LockClosedOutline /></n-icon>
              </template>
            </n-input>
          </div>

          <div class="action-row">
            <button
              class="cf-secondary-btn"
              @click="step = 1"
            >
              返回上一步
            </button>
            <button
              class="cf-primary-btn"
              :disabled="loading"
              @click="handleReset"
            >
              {{ loading ? '重置中...' : '重置密码' }}
            </button>
          </div>
        </div>

        <div class="footer-note">
          想起密码了？
          <button
            class="text-link strong"
            @click="router.push('/login')"
          >
            返回登录
          </button>
        </div>
      </section>
    </div>
  </div>
</template>

<style scoped lang="scss">
.forgot-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 32px;
}

.forgot-shell {
  width: min(1080px, 100%);
  display: grid;
  grid-template-columns: minmax(0, 1fr) 430px;
  gap: 22px;
}

.forgot-visual,
.forgot-panel {
  min-height: 680px;
}

.forgot-visual {
  padding: 28px;
  background: linear-gradient(180deg, var(--cf-bg-glass-soft), var(--cf-bg-glass));
  box-shadow: var(--cf-shadow-float);
}

.forgot-visual-inner {
  height: 100%;
  padding: 32px;
  border-radius: 24px;
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
    line-height: 1.06;
    letter-spacing: 0;
  }

  p {
    margin: 0;
    color: var(--cf-text-secondary);
    line-height: 1.85;
    font-size: 17px;
  }
}

.visual-steps {
  display: flex;
  flex-direction: column;
  gap: 14px;
  margin-top: 28px;
}

.visual-step {
  padding: 18px;
  border-radius: 18px;
  background: var(--cf-bg-glass-soft);
  border: 1px solid var(--cf-border-glass);
  box-shadow: inset 0 1px 0 var(--cf-surface-highlight);
  backdrop-filter: blur(14px) saturate(128%);
  -webkit-backdrop-filter: blur(14px) saturate(128%);

  strong {
    display: block;
    margin-bottom: 6px;
    font-size: 16px;
  }

  span {
    color: var(--cf-text-muted);
    font-size: 14px;
    line-height: 1.75;
  }

  &.active {
    background: linear-gradient(180deg, var(--cf-primary-soft), var(--cf-bg-glass-soft));
    border-color: var(--cf-border-strong);
  }
}

.forgot-panel {
  padding: 32px;
  display: flex;
  flex-direction: column;
  justify-content: center;
}

.panel-head {
  margin-bottom: 22px;

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

.stepper {
  margin-bottom: 26px;
}

.form-area {
  display: flex;
  flex-direction: column;
  gap: 18px;
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
  min-height: 48px;
}

.action-row {
  display: flex;
  gap: 12px;
  margin-top: 8px;

  button {
    flex: 1;
  }
}

.footer-note {
  margin-top: 20px;
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

:deep(.n-input),
:deep(.n-step-indicator) {
  --n-border-radius: 14px !important;
}

@media (max-width: 960px) {
  .forgot-page {
    padding: 16px;
  }

  .forgot-shell {
    grid-template-columns: 1fr;
  }

  .forgot-visual,
  .forgot-panel {
    min-height: auto;
  }
}

@media (max-width: 640px) {
  .forgot-visual,
  .forgot-panel {
    padding: 20px;
  }

  .forgot-visual-inner {
    padding: 22px;
  }

  .action-row {
    flex-direction: column;
  }
}
</style>
