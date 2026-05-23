<script setup lang="ts">
import { computed, ref } from 'vue';
import { useRouter } from 'vue-router';
import { NIcon, NInput, NStep, NSteps, useMessage } from 'naive-ui';
import { KeyOutline, LockClosedOutline, MailOutline, RefreshOutline } from '@vicons/ionicons5';
import { forgotPassword, resetPassword } from '@/api/auth';
import { getPasswordStrength, validateConfirmPassword, validateEmail, validatePassword } from '@/utils/authValidation';

const router = useRouter();
const message = useMessage();

const step = ref(1);
const email = ref('');
const emailCode = ref('');
const newPassword = ref('');
const confirmPassword = ref('');
const loading = ref(false);
const fieldState = ref({
  email: { active: false, touched: false, error: '', shaking: false },
  newPassword: { active: false, touched: false, error: '', shaking: false },
  confirmPassword: { active: false, touched: false, error: '', shaking: false },
});

const stepTitle = computed(() => (step.value === 1 ? '验证账号身份' : '设置新密码'));
const passwordStrength = computed(() => getPasswordStrength(newPassword.value));

function getErrorMessage(error: unknown): string {
  const err = error as { response?: { data?: { message?: string } } };
  return err.response?.data?.message || '重置失败';
}

type ForgotField = keyof typeof fieldState.value;

function runFieldValidation(field: ForgotField) {
  const validators: Record<ForgotField, () => string> = {
    email: () => validateEmail(email.value),
    newPassword: () => validatePassword(newPassword.value),
    confirmPassword: () => validateConfirmPassword(confirmPassword.value, newPassword.value),
  };
  fieldState.value[field].error = validators[field]();
  if (field === 'newPassword' && confirmPassword.value) {
    fieldState.value.confirmPassword.error = validateConfirmPassword(confirmPassword.value, newPassword.value);
  }
}

function focusField(field: ForgotField) {
  fieldState.value[field].active = true;
  runFieldValidation(field);
}

function blurField(field: ForgotField) {
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

function validateFields(fields: ForgotField[]) {
  fields.forEach((field) => {
    fieldState.value[field].touched = true;
    runFieldValidation(field);
  });
  return fields.every((field) => !fieldState.value[field].error);
}

function resetPasswordFields() {
  newPassword.value = '';
  confirmPassword.value = '';
  fieldState.value.newPassword = { active: false, touched: false, error: '', shaking: false };
  fieldState.value.confirmPassword = { active: false, touched: false, error: '', shaking: false };
}

async function handleSendCode() {
  if (!validateFields(['email'])) {
    message.warning('请按提示修正邮箱');
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
  if (!validateFields(['newPassword', 'confirmPassword'])) {
    message.warning('请按提示修正新密码');
    return;
  }
  loading.value = true;
  try {
    await resetPassword(email.value.trim(), emailCode.value.trim(), newPassword.value);
    message.success('密码重置成功，请重新登录');
    router.push('/login');
  } catch (error) {
    message.error(getErrorMessage(error));
    resetPasswordFields();
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
          <div
            class="form-block"
            :class="{ invalid: fieldState.email.touched && fieldState.email.error, shake: fieldState.email.shaking }"
          >
            <label>注册邮箱</label>
            <n-input
              v-model:value="email"
              size="large"
              placeholder="请输入注册时使用的邮箱"
              maxlength="128"
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

          <div
            class="form-block"
            :class="{ invalid: fieldState.newPassword.touched && fieldState.newPassword.error, shake: fieldState.newPassword.shaking }"
          >
            <label>新密码</label>
            <n-input
              v-model:value="newPassword"
              type="password"
              size="large"
              placeholder="8-64 位，需包含字母和数字"
              show-password-on="click"
              maxlength="64"
              :status="fieldState.newPassword.touched && fieldState.newPassword.error ? 'error' : undefined"
              @focus="focusField('newPassword')"
              @blur="blurField('newPassword')"
              @input="runFieldValidation('newPassword')"
            >
              <template #prefix>
                <n-icon><LockClosedOutline /></n-icon>
              </template>
            </n-input>
            <div
              v-if="fieldState.newPassword.active || newPassword"
              class="password-strength"
              :class="passwordStrength.strength"
            >
              <div class="strength-bar">
                <span />
              </div>
              <small>密码强度：{{ passwordStrength.label }}，{{ passwordStrength.hint }}</small>
            </div>
            <small
              v-if="fieldState.newPassword.touched && fieldState.newPassword.error"
              class="field-hint error"
            >
              {{ fieldState.newPassword.error }}
            </small>
          </div>

          <div
            class="form-block"
            :class="{ invalid: fieldState.confirmPassword.touched && fieldState.confirmPassword.error, shake: fieldState.confirmPassword.shaking }"
          >
            <label>确认密码</label>
            <n-input
              v-model:value="confirmPassword"
              type="password"
              size="large"
              placeholder="请再次输入新密码"
              show-password-on="click"
              maxlength="64"
              :status="fieldState.confirmPassword.touched && fieldState.confirmPassword.error ? 'error' : undefined"
              @focus="focusField('confirmPassword')"
              @blur="blurField('confirmPassword')"
              @input="runFieldValidation('confirmPassword')"
            >
              <template #prefix>
                <n-icon><LockClosedOutline /></n-icon>
              </template>
            </n-input>
            <small
              v-if="fieldState.confirmPassword.touched && fieldState.confirmPassword.error"
              class="field-hint error"
            >
              {{ fieldState.confirmPassword.error }}
            </small>
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

.password-strength {
  display: grid;
  gap: 6px;

  small {
    color: var(--cf-text-muted);
    font-size: 12px;
    line-height: 1.5;
  }

  &.weak {
    --strength-color: var(--cf-danger);
    --strength-width: 33%;
  }

  &.medium {
    --strength-color: #d97706;
    --strength-width: 66%;
  }

  &.strong {
    --strength-color: var(--cf-primary);
    --strength-width: 100%;
  }

  &.empty {
    --strength-color: var(--cf-border-strong);
    --strength-width: 0%;
  }
}

.strength-bar {
  height: 6px;
  overflow: hidden;
  border-radius: 999px;
  background: var(--cf-bg-glass-soft);

  span {
    display: block;
    width: var(--strength-width);
    height: 100%;
    border-radius: inherit;
    background: var(--strength-color);
    transition: width 0.2s ease, background-color 0.2s ease;
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
