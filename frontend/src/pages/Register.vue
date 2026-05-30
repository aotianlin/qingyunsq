<script setup lang="ts">
import { computed, onBeforeUnmount, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { NIcon, NInput, useMessage } from 'naive-ui';
import { ArrowForwardOutline, IdCardOutline, LockClosedOutline, MailOutline, PersonOutline, SchoolOutline, ShieldCheckmarkOutline } from '@vicons/ionicons5';
import { register, sendEmailCode } from '@/api/auth';
import {
  getPasswordStrength,
  validateConfirmPassword,
  validateEmail,
  validateNickname,
  validatePassword,
  validateStudentNo,
} from '@/utils/authValidation';

const router = useRouter();
const route = useRoute();
const message = useMessage();

// 支持从登录页"未注册邮箱"流转过来时通过 URL query 预填邮箱
const prefilledEmail = typeof route.query.email === 'string' ? route.query.email.trim() : '';

const email = ref(prefilledEmail);
const password = ref('');
const confirmPassword = ref('');
const studentNo = ref('');
const nickname = ref('');
const emailCode = ref('');
const loading = ref(false);
const codeLoading = ref(false);
const codeCountdown = ref(0);
let codeTimer: number | undefined;
const fieldState = ref({
  nickname: { active: false, touched: false, error: '', shaking: false },
  studentNo: { active: false, touched: false, error: '', shaking: false },
  email: { active: false, touched: false, error: '', shaking: false },
  password: { active: false, touched: false, error: '', shaking: false },
  confirmPassword: { active: false, touched: false, error: '', shaking: false },
});

// 预填的邮箱立即触发一次校验，便于用户及时看到格式错误
if (prefilledEmail) {
  fieldState.value.email.touched = true;
  fieldState.value.email.error = validateEmail(prefilledEmail);
}
const passwordStrength = computed(() => getPasswordStrength(password.value));
const canSubmit = computed(() =>
  Boolean(
    email.value.trim() &&
      password.value &&
      confirmPassword.value &&
      nickname.value.trim() &&
      emailCode.value.trim() &&
      !Object.values(fieldState.value).some((item) => item.error),
  ),
);

const benefits = [
  '在广场记录课程心得与校园见闻',
  '加入学习圈，参与专题讨论与资料共享',
  '开启打卡、积分、成就和 AI 学习辅助能力',
];

type RegisterField = keyof typeof fieldState.value;

function runFieldValidation(field: RegisterField) {
  const validators: Record<RegisterField, () => string> = {
    nickname: () => validateNickname(nickname.value),
    studentNo: () => validateStudentNo(studentNo.value),
    email: () => validateEmail(email.value),
    password: () => validatePassword(password.value),
    confirmPassword: () => validateConfirmPassword(confirmPassword.value, password.value),
  };
  fieldState.value[field].error = validators[field]();
  if (field === 'password' && confirmPassword.value) {
    fieldState.value.confirmPassword.error = validateConfirmPassword(confirmPassword.value, password.value);
  }
}

function focusField(field: RegisterField) {
  fieldState.value[field].active = true;
  runFieldValidation(field);
}

function blurField(field: RegisterField) {
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
  (Object.keys(fieldState.value) as RegisterField[]).forEach((field) => {
    fieldState.value[field].touched = true;
    runFieldValidation(field);
  });
  return (Object.keys(fieldState.value) as RegisterField[]).every((field) => !fieldState.value[field].error);
}

function resetRegisterForm() {
  email.value = '';
  password.value = '';
  confirmPassword.value = '';
  studentNo.value = '';
  nickname.value = '';
  emailCode.value = '';
  fieldState.value = {
    nickname: { active: false, touched: false, error: '', shaking: false },
    studentNo: { active: false, touched: false, error: '', shaking: false },
    email: { active: false, touched: false, error: '', shaking: false },
    password: { active: false, touched: false, error: '', shaking: false },
    confirmPassword: { active: false, touched: false, error: '', shaking: false },
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
  // 发送前确保邮箱格式正确
  fieldState.value.email.touched = true;
  runFieldValidation('email');
  if (fieldState.value.email.error) {
    message.warning('请先填写有效的邮箱');
    return;
  }
  codeLoading.value = true;
  try {
    await sendEmailCode(email.value.trim(), 'REGISTER');
    message.success('验证码已发送，请查收邮箱');
    startCodeCountdown();
  } catch (err: unknown) {
    message.error(err instanceof Error ? err.message : '验证码发送失败');
  } finally {
    codeLoading.value = false;
  }
}

async function handleRegister() {
  if (!validateForm()) {
    message.warning('请按提示修正注册信息');
    return;
  }
  if (!emailCode.value.trim()) {
    message.warning('请填写邮箱验证码');
    return;
  }

  // 防止重复提交
  if (loading.value) return;
  loading.value = true;
  try {
    await register({
      email: email.value.trim(),
      password: password.value,
      emailCode: emailCode.value.trim(),
      studentNo: studentNo.value.trim() || undefined,
      nickname: nickname.value.trim(),
    });
    message.success('注册成功，请登录');
    router.push('/login');
  } catch (err: unknown) {
    // 解析后端返回的具体验证错误信息（如"密码长度需 8-64 位"）
    const errMsg = err instanceof Error ? err.message : '注册失败';
    // 后端 BindException 返回格式为 "field: message; field: message"
    // 提取中文部分展示给用户
    const fieldErrors = errMsg.split(';').map((s: string) => {
      const parts = s.trim().split(':');
      return parts.length > 1 ? parts.slice(1).join(':').trim() : s.trim();
    }).filter(Boolean);
    if (fieldErrors.length > 0 && fieldErrors[0] !== '注册失败') {
      message.error(fieldErrors.join('；'));
    } else {
      message.error(errMsg);
    }
    resetRegisterForm();
  } finally {
    loading.value = false;
  }
}

onBeforeUnmount(() => {
  window.clearInterval(codeTimer);
});
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
          <div
            class="form-block"
            :class="{ invalid: fieldState.nickname.touched && fieldState.nickname.error, shake: fieldState.nickname.shaking }"
          >
            <label>昵称</label>
            <n-input
              v-model:value="nickname"
              size="large"
              placeholder="例如：数据结构补完计划"
              maxlength="64"
              show-count
              :status="fieldState.nickname.touched && fieldState.nickname.error ? 'error' : undefined"
              @focus="focusField('nickname')"
              @blur="blurField('nickname')"
              @input="runFieldValidation('nickname')"
            >
              <template #prefix>
                <n-icon><PersonOutline /></n-icon>
              </template>
            </n-input>
            <small
              v-if="fieldState.nickname.touched && fieldState.nickname.error"
              class="field-hint error"
            >
              {{ fieldState.nickname.error }}
            </small>
          </div>
          <div
            class="form-block"
            :class="{ invalid: fieldState.studentNo.touched && fieldState.studentNo.error, shake: fieldState.studentNo.shaking }"
          >
            <label>学号</label>
            <n-input
              v-model:value="studentNo"
              size="large"
              placeholder="选填，填写则需为 12 位数字"
              maxlength="12"
              :allow-input="(value: string) => /^\d*$/.test(value)"
              :status="fieldState.studentNo.touched && fieldState.studentNo.error ? 'error' : undefined"
              @focus="focusField('studentNo')"
              @blur="blurField('studentNo')"
              @input="runFieldValidation('studentNo')"
            >
              <template #prefix>
                <n-icon><IdCardOutline /></n-icon>
              </template>
            </n-input>
            <small
              v-if="fieldState.studentNo.touched && fieldState.studentNo.error"
              class="field-hint error"
            >
              {{ fieldState.studentNo.error }}
            </small>
          </div>
        </div>

        <div class="form-grid">
          <div
            class="form-block"
            :class="{ invalid: fieldState.email.touched && fieldState.email.error, shake: fieldState.email.shaking }"
          >
            <label>邮箱</label>
            <n-input
              v-model:value="email"
              size="large"
              placeholder="name@college.edu"
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

          <div
            class="form-block"
            :class="{ invalid: fieldState.password.touched && fieldState.password.error, shake: fieldState.password.shaking }"
          >
            <label>密码</label>
            <n-input
              v-model:value="password"
              type="password"
              size="large"
              placeholder="8-64 位，需包含字母和数字"
              show-password-on="click"
              maxlength="64"
              :status="fieldState.password.touched && fieldState.password.error ? 'error' : undefined"
              @focus="focusField('password')"
              @blur="blurField('password')"
              @input="runFieldValidation('password')"
            >
              <template #prefix>
                <n-icon><LockClosedOutline /></n-icon>
              </template>
            </n-input>
            <div
              v-if="fieldState.password.active || password"
              class="password-strength"
              :class="passwordStrength.strength"
            >
              <div class="strength-bar">
                <span />
              </div>
              <small>密码强度：{{ passwordStrength.label }}，{{ passwordStrength.hint }}</small>
            </div>
            <small
              v-if="fieldState.password.touched && fieldState.password.error"
              class="field-hint error"
            >
              {{ fieldState.password.error }}
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
              placeholder="再次输入密码"
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

          <div class="form-block">
            <label>邮箱验证码</label>
            <div class="code-input-row">
              <n-input
                v-model:value="emailCode"
                size="large"
                placeholder="输入 6 位验证码"
                maxlength="6"
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
        </div>

        <button
          class="cf-primary-btn submit-btn"
          :disabled="loading || !canSubmit"
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

.footer-note {
  margin-top: 18px;
  text-align: center;
  color: var(--cf-text-secondary);
  font-size: 14px;
}

.field-error {
  color: #ef4444;
  font-size: 12px;
  margin-top: -4px;
}

.password-strength {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: -4px;

  span {
    font-size: 12px;
    font-weight: 600;
  }
}

.strength-bar {
  flex: 1;
  height: 4px;
  border-radius: 2px;
  background: var(--cf-border);
  overflow: hidden;
}

.strength-fill {
  height: 100%;
  border-radius: 2px;
  transition: width 0.3s, background 0.3s;
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

  .code-input-row {
    grid-template-columns: 1fr;
  }
}
</style>
